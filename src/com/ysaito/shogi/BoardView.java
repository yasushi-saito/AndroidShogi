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

import com.ysaito.shogi.Board;

public class BoardView extends View implements View.OnTouchListener {
  // Bitmap for all the pieces. mGoteBitmaps[i] is the same as mSenteBitmaps[i], except upside down.
  Bitmap mSenteBitmaps[];
  Bitmap mGoteBitmaps[];

  int mTurn;  // who's allowed to move pieces? One of Board.P_XXX.

  // Current state of the board
  Board mBoard;   

  // Position represents a logical position of a piece.
  //
  // @invariant (0,0) <= (x,y) < (9,9).
  static class Position {
    public Position(int v1, int v2) { x = v1; y = v2; }

    @Override public boolean equals(Object o) {
      if (o instanceof Position) {
        Position p = (Position)o;
        return p.x == x && p.y == y;
      }
      return false;
    }

    @Override public int hashCode() { return x + y; }

    public int x;
    public int y;
  }

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
        mCapturedGote= new Rect(0, 0, dim, dim/ 10);
        mCapturedSente = new Rect(0, dim * 11 / 10, dim, dim * 12 / 10);
        mBoard = new Rect(0, dim/ 10, dim, dim * 11 / 10);
      } else {
        // Captured pieces are shown at the left & right of the board
        mPortrait = false;
        dim = height;
        mCapturedGote = new Rect(0, 0, dim / 10, dim);
        mCapturedSente = new Rect(dim * 11 / 10, 0, dim * 12/ 10, dim);
        mBoard = new Rect(dim / 10, 0, dim * 11 / 10, dim);
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
    int capturedScreenX(int player, int index) {
      Rect r = (player == Board.P_SENTE ? mCapturedSente : mCapturedGote);
      if (mPortrait) {
        return r.left + mSquareDim * index * 4 / 3;
      } else {
        return r.left;
      }
    }
    int capturedScreenY(int player, int index) {
      Rect r = (player == Board.P_SENTE ? mCapturedSente : mCapturedGote);
      if (mPortrait) {
        return r.top;
      } else {
        return r.top + mSquareDim * index * 4 / 3;
      }
    }

    boolean mPortrait;
    int mWidth, mHeight;  // screen pixel size
    int mSquareDim; // pixel size of each square in the board
    Rect mBoard;
    Rect mCapturedSente;
    Rect mCapturedGote;
  };
  ScreenLayout mCachedLayout;
  
  // If non-NULL, user is trying to move the piece from this square.
  // @invariant mMoveFrom == null || (0,0) <= mMoveFrom < (Board.DIM, Board.DIM)
  private Position mMoveFrom; 

  // If non-NULL, user is trying to move the piece to this square.
  // @invariant mMoveTo== null || (0,0) <= mMoveTo < (Board.DIM, Board.DIM)
  private Position mMoveTo;

  private static final String TAG = "Board";

  public interface EventListener {
    void onHumanMove(Board.Move move);
  }

  private EventListener mListener;

  public BoardView(Context context, AttributeSet attrs) {
    super(context, attrs);
    mTurn = Board.P_INVALID;
    mBoard = new Board();
    mBoard.setPiece(4,4, -Board.K_KYO);
    initializeBitmaps();
    setOnTouchListener(this);
  }	

  public void setEventListener(EventListener listener) { 
    mListener = listener; 
  }

  public void setTurn(int turn) { mTurn = turn; }

  public boolean onTouch(View v, MotionEvent event) {
    ScreenLayout layout = getScreenLayout();
    int squareDim = layout.squareDim();
    int action = event.getAction();

    int px = layout.boardX((int)event.getX());
    int py = layout.boardY((int)event.getY());
    if (px < 0 || py < 0) {
      return false;
    }

    if (action == MotionEvent.ACTION_DOWN) {
      // Start of touch operation
      int piece = mBoard.getPiece(px, py);
      if (Board.player(piece) != mTurn) {
        // Tried to move a piece not owned by the player
        return false;
      }
      mMoveFrom = new Position(px, py);
      invalidate();
      return true;
    }

    if (mMoveFrom != null) {
      Position to = findSnapSquare(layout, mMoveFrom.x, mMoveFrom.y, 
          event.getX(), event.getY());
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
        move.piece = mBoard.getPiece(mMoveFrom.x, mMoveFrom.y);
        move.from_x = mMoveFrom.x;
        move.from_y = mMoveFrom.y;
        move.to_x = mMoveTo.x;
        move.to_y = mMoveTo.y;
        move.promote = false; // TODO(saito) fix
        mListener.onHumanMove(move);
      }
      mMoveTo = null;
      mMoveFrom = null;
      invalidate();
    }
    return true;
  }

  @Override
  public void onDraw(Canvas canvas) {
    if (mBoard == null) return;
    int DIM = Board.DIM;

    ScreenLayout layout = getScreenLayout();

    // Draw the board
    Paint p = new Paint();
    p.setColor(0xfff5deb3);
    
    Rect boardRect = layout.getBoard();
    int squareDim = layout.squareDim();
    
    canvas.drawRect(boardRect, p);

    // Draw the gridlines
    p.setColor(0xff000000);
    for (int i = 0; i < DIM; ++i) {
      int sx = layout.screenX(i);
      int sy = layout.screenY(i);
      canvas.drawLine(sx, boardRect.top, sx, boardRect.bottom, p);
      canvas.drawLine(boardRect.left, sy, boardRect.right, sy, p);
    }

    // Draw pieces
    for (int y = 0; y < Board.DIM; ++y) {
      for (int x = 0; x < Board.DIM; ++x) {
        int v = mBoard.getPiece(x, y);
        if (v == 0) continue; 
        int t = Board.type(v);

        Bitmap bm;
        if (Board.player(v) == Board.P_GOTE) {
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

    if (mBoard.mCapturedSente != 0) {
      drawCapturedPieces(canvas, layout, Board.P_SENTE, mBoard.mCapturedSente);
    }
    if (mBoard.mCapturedGote!= 0) {
      drawCapturedPieces(canvas, layout, Board.P_GOTE, mBoard.mCapturedGote);
    }

    if (mMoveFrom != null) {
      p.setColor(0x28000000);
      ArrayList<Position> dests = possibleMoveDestinations(
          mBoard.getPiece(mMoveFrom.x, mMoveFrom.y),
          mMoveFrom.x, mMoveFrom.y);
      for (Position dest: dests) {
        int sx = layout.screenX(dest.x);
        int sy = layout.screenY(dest.y);
        canvas.drawRect(new Rect(sx, sy, sx + squareDim, sy + squareDim), p);
      }
    }
    if (mMoveTo != null) {
      p.setColor(0x50000000);
      int sx = layout.screenX(mMoveTo.x);
      int sy = layout.screenY(mMoveTo.y);
      canvas.drawRect(new Rect(sx, sy, sx + squareDim, sy + squareDim), p);
    }
  }

  public void setBoard(Board board) {
    mBoard = new Board(board);
    mBoard.mCapturedSente = 0x2345;
    mBoard.mCapturedGote = 0x2345;
    invalidate();
  }

  void drawCapturedPieces(Canvas canvas, ScreenLayout layout,
      int player, int bits) {
    Bitmap[] bitmaps = (player == Board.P_SENTE ? mSenteBitmaps : mGoteBitmaps);
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
  
  void drawPiece(Canvas canvas, ScreenLayout layout, Bitmap bm, int sx, int sy) {
    BitmapDrawable b = new BitmapDrawable(getResources(), bm);
    b.setBounds(sx, sy, sx + layout.squareDim(), sy + layout.squareDim());
    b.draw(canvas);
  }
  
  void drawCapturedPiece(Canvas canvas, ScreenLayout layout, 
      Bitmap bm, int num_pieces, int player, int seq) {
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
    public MoveDestinationsState(int player, int cur_x, int cur_y) {
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
    int mPlayer;
    int mCurX;
    int mCurY;
    boolean mSeenOpponentPiece;
  }
  
  private ArrayList<Position> possibleMoveDestinations(int piece, int cur_x, int cur_y) {
    int type = Board.type(piece);
    int player = Board.player(piece);
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

  private Position findSnapSquare(
      ScreenLayout layout,
      int from_x, int from_y, 
      float cur_sx, float cur_sy) {
    ArrayList<Position> dests = possibleMoveDestinations(mBoard.getPiece(from_x, from_y), from_x, from_y);
    Position nearest = null;
    float min_distance = layout.screenDistance(from_x, from_y, cur_sx, cur_sy);

    for (Position dest: dests) {
      float distance = layout.screenDistance(dest.x, dest.y, cur_sx, cur_sy);
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
  
  };
