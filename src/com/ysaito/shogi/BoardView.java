package com.ysaito.shogi;
import java.util.ArrayList;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.ysaito.shogi.Board;

public class BoardView extends View implements View.OnTouchListener {
  // Bitmap for all the pieces. mGoteBitmaps[i] is the same as mSenteBitmaps[i], except upside down.
  Bitmap mSenteBitmaps[];
  Bitmap mGoteBitmaps[];

  Board.GameState mGameState;
  Board.Player mCurrentPlayer;  
  String mErrorMessage;
  
  // Current state of the board
  Board mBoard;   

  public interface EventListener {
    void onHumanMove(Board.Move move);
  }

  // Position represents a logical position of a piece.
  //
  // @invariant (0,0) <= (x,y) < (9,9).
  static class Position {
    // Point to a coordinate on the board
    public Position(int x, int y) { mX = x; mY = y; }

    // Point to a captured piece
    public Position(Board.Player player, int index) {
      mPlayer = player;
      mCapturedIndex = index;
    }
    
    @Override public boolean equals(Object o) {
      if (o instanceof Position) {
        Position p = (Position)o;
        return p.mPlayer == mPlayer &&
               p.mCapturedIndex == mCapturedIndex &&
               p.mX == mX && 
               p.mY == mY;
      }
      return false;
    }

    @Override public int hashCode() { 
      return mX + mY + ((mPlayer != null) ? mPlayer.hashCode() : 0) + mCapturedIndex;
    }
    
    boolean isOnBoard() { return mPlayer == null; }
    
    int getX() {
      if (!isOnBoard()) throw new AssertionError("blah");
      return mX;
    }

    int getY() {
      if (!isOnBoard()) throw new AssertionError("blah");
      return mY;
    }
    
    Board.Player player() { 
      if (isOnBoard()) throw new AssertionError("blah");
      return mPlayer; 
    }
    
    int capturedIndex() { 
      if (isOnBoard()) throw new AssertionError("blah");
      return mCapturedIndex; 
    }
    
    // player and captured_index are set iff the Position describes
    // a captured piece
    Board.Player mPlayer;
    int mCapturedIndex;
    
    // mX and mY are set iff the Position describes a coordinate on the board.
    int mX;
    int mY;
  }

  // A class describing the layout of this view.
  static class ScreenLayout {
    public ScreenLayout(int width, int height) {
      mWidth = width;
      mHeight = height;
      
      // TODO(saito) this code doesn't handle a square screen
      int dim;
      if (width < height) {
        dim = width;
        // Captured pieces are shown at the top & bottom of the board
        mPortrait = true;
        mCapturedWhite= new Rect(0, 0, dim, dim/ 10);
        mCapturedBlack = new Rect(0, dim * 11 / 10, dim, dim * 12 / 10);
        mBoard = new Rect(0, dim/ 10, dim, dim * 11 / 10);
        mStatus = new Rect(0, dim * 12 / 10, width, height);
      } else {
        // Captured pieces are shown at the left & right of the board
        mPortrait = false;
        dim = height;
        mCapturedWhite = new Rect(0, 0, dim / 10, dim);
        mCapturedBlack = new Rect(dim * 11 / 10, 0, dim * 12/ 10, dim);
        mBoard = new Rect(dim / 10, 0, dim * 11 / 10, dim);
        mStatus = new Rect(dim * 12 / 10, 0, width, height);
      }
      mSquareDim = dim / Board.DIM;
    }
  
    // Get the screen dimension
    public int getScreenWidth() { return mWidth; }
    public int getScreenHeight() { return mHeight; }
    public int squareDim() { return mSquareDim; }
    public Rect getBoard() { return mBoard; }

    // Convert screen X value to board position. Return -1 on error.
    public int boardX(int sx) {
      int px = (sx - mBoard.left) / mSquareDim;
      if (px < 0 || px >= Board.DIM) px = -1;
      return px;
    }

    // Convert screen Y value to board position. Return -1 on error.
    public int boardY(int sy) {
      int py = (sy - mBoard.top) / mSquareDim;
      if (py < 0 || py >= Board.DIM) py = -1;
      return py;
    }
    
    // Convert board X position to the position of the left edge of the screen.
    public int screenX(int px) { 
      return mBoard.left + mBoard.width() * px / Board.DIM; 
    }
    
    // Convert board Y position to the position of the top edge of the screen.
    public int screenY(int py) { 
      return mBoard.top + mBoard.height() * py / Board.DIM; 
    }
    
    // Compute the distance from board position <px, py> to screen location 
    // <event_x, event_y>.
    float screenDistance(int px, int py, float event_x, float event_y) {
      int sx = screenX(px) + mSquareDim / 2;
      int sy = screenY(py) + mSquareDim / 2;
      return Math.abs(sx - event_x) + Math.abs(sy - event_y);
    }

    // Return the position for displaying a captured piec.
    
    // @p player is one of Board.P_{SENTE,GOTE}
    //
    // @p index is an integer 0, 1, 2, ... that specifies the 
    // position of the piece in captured list.
    int capturedScreenX(Board.Player player, int index) {
      Rect r = (player == Board.Player.BLACK ? mCapturedBlack : mCapturedWhite);
      if (mPortrait) {
        return r.left + mSquareDim * index * 4 / 3;
      } else {
        return r.left;
      }
    }
    
    int capturedScreenY(Board.Player player, int index) {
      Rect r = (player == Board.Player.BLACK ? mCapturedBlack : mCapturedWhite);
      if (mPortrait) {
        return r.top;
      } else {
        return r.top + mSquareDim * index * 4 / 3;
      }
    }

    int capturedPieceIndex(Board.Player player, int sx, int sy) {
      Rect r = (player == Board.Player.BLACK ? mCapturedBlack : mCapturedWhite);
      if (sx < r.left|| sx>= r.right) return -1;
      if (sy < r.top || sy >= r.bottom) return -1;
      if (mPortrait) {
        return (sx - r.left) / (mSquareDim * 4 / 3);
      } else {
        return (sy - r.top) / (mSquareDim * 4 / 3);
      }
    }
    
    boolean mPortrait;
    int mWidth, mHeight;  // screen pixel size
    int mSquareDim; // pixel size of each square in the board
    Rect mBoard;   // display the board status
    Rect mCapturedBlack;  // display pieces captured by black player
    Rect mCapturedWhite;  // display pieces captured by white player
    Rect mStatus;  //display arbitrary status messages
  }
  ScreenLayout mCachedLayout;
  
  // If non-NULL, user is trying to move the piece from this square.
  // @invariant mMoveFrom == null || (0,0) <= mMoveFrom < (Board.DIM, Board.DIM)
  Position mMoveFrom; 

  // If non-NULL, user is trying to move the piece to this square.
  // @invariant mMoveTo== null || (0,0) <= mMoveTo < (Board.DIM, Board.DIM)
  Position mMoveTo;

  static final String TAG = "Board";

  EventListener mListener;
  TextView mStatusView;

  public BoardView(Context context, AttributeSet attrs) {
    super(context, attrs);
    mCurrentPlayer = Board.Player.INVALID;
    mGameState = Board.GameState.ACTIVE;
    mBoard = new Board();
    mBoard.setPiece(4, 4, -Board.K_GIN);
    initializeBitmaps();
    setOnTouchListener(this);
  }	

  public final void setEventListener(EventListener listener) { 
    mListener = listener; 
  }

  public final void setStatusView(TextView v) {
    mStatusView = v;
  }
  
  public final Board.GameState gameState() { return mGameState; }
  
  public final void setTurn(Board.Player turn) { mCurrentPlayer = turn; }

  @Override public boolean onTouch(View v, MotionEvent event) {
    ScreenLayout layout = getScreenLayout();
    int squareDim = layout.squareDim();
    int action = event.getAction();

    if (action == MotionEvent.ACTION_DOWN) {
      // Start of touch operation
      int px = layout.boardX((int)event.getX());
      int py = layout.boardY((int)event.getY());
      if (px >= 0 && py >= 0) {
        // Moving a piece on the board
        int piece = mBoard.getPiece(px, py);
        if (Board.player(piece) != mCurrentPlayer) {
          // Tried to move a piece not owned by the player
          return false;
        }
        mMoveFrom = new Position(px, py);
      } else {
        int pindex = layout.capturedPieceIndex(
            mCurrentPlayer, (int)event.getX(), (int)event.getY());
        int piece = getCapturedPiece(mBoard, mCurrentPlayer, pindex);
        if (piece <= 0) return false;
        mMoveFrom = new Position(mCurrentPlayer, pindex);
      }
      invalidate();
      return true;
    }

    if (mMoveFrom != null) {
      Position to = null;
      if (mMoveFrom.isOnBoard()) {
        to = findSnapSquare(layout, mMoveFrom.getX(), mMoveFrom.getY(), 
            event.getX(), event.getY());
      } else {
        int px = layout.boardX((int)event.getX());
        int py = layout.boardY((int)event.getY());
        if (px >= 0 && py >= 0 && mBoard.getPiece(px, py) == 0) {
          to = new Position(px, py); 
        }
      }
      // If "to" is different from the currently selected square, redraw
      if ((to == null && mMoveTo != null) ||
          (to != null && !to.equals(mMoveTo))) {
        invalidate();
      }
      mMoveTo = to;
    }
    if (action == MotionEvent.ACTION_UP) {
      if (mMoveTo != null && mListener != null) {
        Board.Move move = new Board.Move();
        move.player = mCurrentPlayer;
        move.to_x = mMoveTo.getX();
        move.to_y = mMoveTo.getY();
        move.promote = false; // filled by Shogi.jana
        
        if (mMoveFrom.isOnBoard()) {
          move.piece = mBoard.getPiece(mMoveFrom.getX(), mMoveFrom.getY());
          move.from_x = mMoveFrom.getX();
          move.from_y = mMoveFrom.getY();
        } else {
          move.piece = getCapturedPiece(
              mBoard, mCurrentPlayer, mMoveFrom.capturedIndex());
          move.from_x = move.from_y = -1;
        }
        
        mCurrentPlayer = Board.Player.INVALID;
        mListener.onHumanMove(move);
      }
      mMoveTo = null;
      mMoveFrom = null;
      invalidate();
    }
    return true;
  }

  //
  // Screen drawing
  //
  @Override public void onDraw(Canvas canvas) {
    if (mBoard == null) return;
    int DIM = Board.DIM;
    ScreenLayout layout = getScreenLayout();
    Rect boardRect = layout.getBoard();
    int squareDim = layout.squareDim();

    drawEmptyBoard(canvas, layout);

    // Draw pieces
    for (int y = 0; y < Board.DIM; ++y) {
      for (int x = 0; x < Board.DIM; ++x) {
        int v = mBoard.getPiece(x, y);
        if (v == 0) continue; 
        int t = Board.type(v);

        Bitmap bm;
        if (Board.player(v) == Board.Player.WHITE) {
          bm = mGoteBitmaps[t];
        } else {
          bm = mSenteBitmaps[t];
        }
        BitmapDrawable b = new BitmapDrawable(getResources(), bm);

        int sx = layout.screenX(x);
        int sy = layout.screenY(y);
        b.setBounds(sx, sy, sx + squareDim, sy + squareDim);
        b.draw(canvas);
      }
    }

    if (mBoard.mCapturedBlack != 0) {
      drawCapturedPieces(canvas, layout, Board.Player.BLACK, mBoard.mCapturedBlack);
    }
    if (mBoard.mCapturedWhite!= 0) {
      drawCapturedPieces(canvas, layout, Board.Player.WHITE, mBoard.mCapturedWhite);
    }

    if (mMoveFrom != null) {
      Paint p = new Paint();
      p.setColor(0x28000000);
      if (mMoveFrom.isOnBoard()) {
        ArrayList<Position> dests = possibleMoveDestinations(
            mBoard.getPiece(mMoveFrom.getX(), mMoveFrom.getY()),
            mMoveFrom.getX(), mMoveFrom.getY());
        for (Position dest: dests) {
          int sx = layout.screenX(dest.getX());
          int sy = layout.screenY(dest.getY());
          canvas.drawRect(new Rect(sx, sy, sx + squareDim, sy + squareDim), p);
        }
      } else {
        int sx = layout.capturedScreenX(mMoveFrom.player(), mMoveFrom.capturedIndex());
        int sy = layout.capturedScreenY(mMoveFrom.player(), mMoveFrom.capturedIndex());
        canvas.drawRect(new Rect(sx, sy, sx + squareDim, sy + squareDim), p);
      }
    }
    if (mMoveTo != null) {
      Paint p = new Paint();
      p.setColor(0x50000000);
      int sx = layout.screenX(mMoveTo.getX());
      int sy = layout.screenY(mMoveTo.getY());
      canvas.drawRect(new Rect(sx, sy, sx + squareDim, sy + squareDim), p);
    }
    
    if (mStatusView != null) {
      drawStatus();
    }
  }

  public final void setState(
      Board.GameState gameState,
      Board board,
      Board.Player currentPlayer,
      String errorMessage) {
    mGameState = gameState;
    mCurrentPlayer = currentPlayer;
    mBoard = new Board(board);
    mErrorMessage = errorMessage;
    invalidate();
  }
  
  void drawEmptyBoard(Canvas canvas, ScreenLayout layout) {
    Rect boardRect = layout.getBoard();
    Paint p = new Paint();
    p.setColor(0xfff5deb3);
    canvas.drawRect(boardRect, p);
    
    // Draw the gridlines
    p.setColor(0xff000000);
    for (int i = 0; i < Board.DIM; ++i) {
      int sx = layout.screenX(i);
      int sy = layout.screenY(i);
      canvas.drawLine(sx, boardRect.top, sx, boardRect.bottom, p);
      canvas.drawLine(boardRect.left, sy, boardRect.right, sy, p);
    }
  }

  void drawStatus() {
    String status;
    if (mGameState == Board.GameState.ACTIVE) {
      status = "Turn: ";
      if (mCurrentPlayer == Board.Player.BLACK) status += "black"; 
      else if (mCurrentPlayer == Board.Player.WHITE) status += "white"; 
      else status += "none";
    } else if (mGameState == Board.GameState.BLACK_LOST) {
      status = "Black lost";
    } else if (mGameState == Board.GameState.WHITE_LOST) {
      status = "White lost";
    } else if (mGameState == Board.GameState.DRAW) {
      status = "Draw";
    } else {
      throw new AssertionError(mGameState.toString());
    }
    if (mErrorMessage != null) {
      status += ": " + mErrorMessage;
    }
    mStatusView.setText(status);
  }
  
  void drawCapturedPieces(Canvas canvas, ScreenLayout layout,
      Board.Player player, int bits) {
    Bitmap[] bitmaps = (player == Board.Player.BLACK ? mSenteBitmaps : mGoteBitmaps);
    int seq = 0;
    int n = Board.numCapturedFu(bits);
    if (n > 0) {
      drawCapturedPiece(canvas, layout, bitmaps[Board.K_FU], n, player, seq);
      ++seq;
    }
    n = Board.numCapturedKyo(bits);
    if (n > 0) {
      drawCapturedPiece(canvas, layout, bitmaps[Board.K_KYO], n, player, seq);
      ++seq;
    }
    n = Board.numCapturedKei(bits);
    if (n > 0) {
      drawCapturedPiece(canvas, layout, bitmaps[Board.K_KEI], n, player, seq);
      ++seq;
    }
    n = Board.numCapturedGin(bits);
    if (n > 0) {
      drawCapturedPiece(canvas, layout, bitmaps[Board.K_GIN], n, player, seq);
      ++seq;
    }
    n = Board.numCapturedKin(bits);
    if (n > 0) {
      drawCapturedPiece(canvas, layout, bitmaps[Board.K_KIN], n, player, seq);
      ++seq;
    }
    n = Board.numCapturedKaku(bits);
    if (n > 0) {
      drawCapturedPiece(canvas, layout, bitmaps[Board.K_KAKU], n, player, seq);
      ++seq;
    }
    n = Board.numCapturedHi(bits);
    if (n > 0) {
      drawCapturedPiece(canvas, layout, bitmaps[Board.K_HI], n, player, seq);
      ++seq;
    }
  }

  static int getCapturedPiece(Board board, Board.Player player, int index) {
    int bits = (player == Board.Player.BLACK ? 
                board.mCapturedBlack : board.mCapturedWhite);
    if (Board.numCapturedFu(bits) > 0) --index;
    if (index < 0) return Board.K_FU;
    if (Board.numCapturedKyo(bits) > 0) --index;
    if (index < 0) return Board.K_KYO;
    if (Board.numCapturedKei(bits) > 0) --index;
    if (index < 0) return Board.K_KEI;
    if (Board.numCapturedGin(bits) > 0) --index;
    if (index < 0) return Board.K_GIN;
    if (Board.numCapturedKin(bits) > 0) --index;
    if (index < 0) return Board.K_KIN;
    if (Board.numCapturedKaku(bits) > 0) --index;
    if (index < 0) return Board.K_KAKU;
    if (Board.numCapturedHi(bits) > 0) --index;
    if (index < 0) return Board.K_HI;
    throw new AssertionError("???");
  }
  
  void drawPiece(Canvas canvas, ScreenLayout layout, Bitmap bm, int sx, int sy) {
    BitmapDrawable b = new BitmapDrawable(getResources(), bm);
    b.setBounds(sx, sy, sx + layout.squareDim(), sy + layout.squareDim());
    b.draw(canvas);
  }
  
  void drawCapturedPiece(Canvas canvas, ScreenLayout layout, 
      Bitmap bm, int num_pieces, Board.Player player, int seq) {
    int sx = layout.capturedScreenX(player, seq);
    int sy = layout.capturedScreenY(player, seq);
    drawPiece(canvas, layout, bm, sx, sy);
    if (num_pieces >= 1) {
      int fontSize = 14;
      Paint p = new Paint();
      p.setTextSize(fontSize);
      p.setColor(0xffeeeeee);
      p.setTypeface(Typeface.DEFAULT_BOLD);
      canvas.drawText(Integer.toString(num_pieces),
          sx + layout.squareDim() - fontSize / 4,
          sy + layout.squareDim() - fontSize / 2,
          p);
    }
  }
  
  // Load bitmaps for pieces. Called once when the process starts
  void initializeBitmaps() {
    Resources r = getResources();
    mSenteBitmaps = new Bitmap[Board.NUM_TYPES];
    mGoteBitmaps = new Bitmap[Board.NUM_TYPES];
    String koma_names[] = {
        null,
        "fu", "kyo", "kei", "gin", "kin", "kaku", "hi", "ou",
        "nari_fu", "nari_kyo", "nari_kei", "nari_gin", null, "nari_kaku", "nari_hi"
    };

    Matrix flip = new Matrix();
    flip.postRotate(180);

    for (int i = 1; i < Board.NUM_TYPES; ++i) {
      if (koma_names[i] == null) continue;
      int id = r.getIdentifier("@com.ysaito.shogi:drawable/kinki_" + koma_names[i], null, null);
      mSenteBitmaps[i] = BitmapFactory.decodeResource(r, id);
      mGoteBitmaps[i] = Bitmap.createBitmap(mSenteBitmaps[i], 0, 0, 
          mSenteBitmaps[i].getWidth(), mSenteBitmaps[i].getHeight(),
          flip, false);
    }
    assert 1 == 0;
  }

  // Helper class used to list possible squares a piece can move to
  class MoveDestinationsState {
    public MoveDestinationsState(Board.Player player, int cur_x, int cur_y) {
      mPlayer = player;
      mCurX = cur_x;
      mCurY = cur_y;
    }
    
    public ArrayList<Position> getDests() { return mDests; }

    // If the piece can be moved to <cur_x+dx, cur_y+dy>, add it to
    // mDests.
    public void tryMove(int dx, int dy) {
      mSeenOpponentPiece = false;
      canMoveTo(mCurX + dx, mCurY + dy);
    }
    
    // Perform tryMove for each <dx,dy>, <2*dx,2*dy>, ..., 
    // Stop when reached the first disallowed square.
    public void tryMoveMulti(int dx, int dy) {
      mSeenOpponentPiece = false;
      int x = mCurX;
      int y = mCurY;
      for (;;) {
        x += dx;
        y += dy;
        if (!canMoveTo(x, y)) break;
      }
    }
    
    boolean canMoveTo(int x, int y) {
      // Disallow moving outside the board
      if (x < 0 || x >= Board.DIM || y < 0 || y >= Board.DIM) {
        return false;
      }
      // Disallow skipping over an opponent
      if (mSeenOpponentPiece) return false;
      
      int existing = mBoard.getPiece(x, y);
      if (existing != 0) {
        // Disallow occuping the same square twice
        if (Board.player(existing) == mPlayer) return false;
        mSeenOpponentPiece = true;
        mDests.add(new Position(x, y));
        return true;
      }
      mDests.add(new Position(x, y));
      return true;
    }
    
    final ArrayList<Position> mDests = new ArrayList<Position>();
    Board.Player mPlayer;
    int mCurX;
    int mCurY;
    boolean mSeenOpponentPiece;
  }
  
  // Generate the list of board positions that a piece at <cur_x, cur_y> can
  // move to. It takes other pieces on the board into account, but it may
  // still generate illegal moves -- e.g., this method doesn't check for
  // nifu, sennichite, uchi-fu zume aren't by this method.
  private ArrayList<Position> possibleMoveDestinations(
      int piece, int cur_x, int cur_y) {
    int type = Board.type(piece);
    Board.Player player = Board.player(piece);
    ArrayList<Position> dests = new ArrayList<Position>();
    MoveDestinationsState state = new MoveDestinationsState(player, cur_x, cur_y);
    
    switch (type) {
      case Board.K_FU: 
        state.tryMove(0, -1);
        break;
      case Board.K_KYO:
        state.tryMoveMulti(0, -1);
        break;
      case Board.K_KEI:
        state.tryMove(-1, -2);
        state.tryMove(1, -2);
        break;
      case Board.K_GIN:
        state.tryMove(-1, -1);
        state.tryMove(0, -1);
        state.tryMove(1, -1);
        state.tryMove(-1, 1);
        state.tryMove(1, 1);
        break;
      case Board.K_KIN:
      case Board.K_TO:
      case Board.K_NARI_KYO:
      case Board.K_NARI_KEI:
        state.tryMove(-1, -1);
        state.tryMove(0, -1);
        state.tryMove(1, -1);
        state.tryMove(-1, 0);    			
        state.tryMove(1, 0);
        state.tryMove(0, 1);
        break;
      case Board.K_KAKU:
      case Board.K_UMA:
        state.tryMoveMulti(-1, -1);
        state.tryMoveMulti(1, 1);
        state.tryMoveMulti(1, -1);
        state.tryMoveMulti(-1, 1);
        if (type == Board.K_UMA) {
          state.tryMove(0, -1);
          state.tryMove(0, 1);
          state.tryMove(1, 0);
          state.tryMove(-1, 0);
        }
        break;
      case Board.K_HI:
      case Board.K_RYU:
        state.tryMoveMulti(0, -1);
        state.tryMoveMulti(0, 1);
        state.tryMoveMulti(-1, 0);
        state.tryMoveMulti(1, 0);
        if (type == Board.K_RYU) {
          state.tryMove(-1, -1);
          state.tryMove(-1, 1);
          state.tryMove(1, -1);
          state.tryMove(1, 1);
        }
        break;
      case Board.K_OU:
        state.tryMove(0, -1);
        state.tryMove(0, 1);
        state.tryMove(1, 0);
        state.tryMove(-1, 0);
        state.tryMove(-1, -1);
        state.tryMove(-1, 1);
        state.tryMove(1, -1);
        state.tryMove(1, 1);
        break;
      default:
        Log.wtf(TAG, "Illegal type: " + type);
    }
    return state.getDests();
  }

  Position findSnapSquare(
      ScreenLayout layout,
      int from_x, int from_y, 
      float cur_sx, float cur_sy) {
    ArrayList<Position> dests = possibleMoveDestinations(mBoard.getPiece(from_x, from_y), from_x, from_y);
    Position nearest = null;
    float min_distance = layout.screenDistance(from_x, from_y, cur_sx, cur_sy);

    for (Position dest: dests) {
      float distance = layout.screenDistance(dest.getX(), dest.getY(), cur_sx, cur_sy);
      if (distance < min_distance) {
        nearest = dest;
        min_distance = distance;
      }
    }
    return nearest;
  }

  ScreenLayout getScreenLayout() {
    if (mCachedLayout != null &&
        mCachedLayout.getScreenWidth() == getWidth() &&
        mCachedLayout.getScreenHeight() == getHeight()) {
      // reuse the cached value
    } else {
      mCachedLayout = new ScreenLayout(getWidth(), getHeight());
    }
    return mCachedLayout;
  }
  
}
