package com.ysaito.shogi;
import java.util.ArrayList;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.ysaito.shogi.Board;

public class BoardView extends View implements View.OnTouchListener {
  /**
   * Interface for communicating user moves to the owner of this view.
   * onHumanMove is called when a human player moves a piece, or drops a
   * captured piece.
   */ 
  public interface EventListener {
    void onHumanMove(Player player, Move move);
  }

  public BoardView(Context context, AttributeSet attrs) {
    super(context, attrs);
    mCurrentPlayer = Player.INVALID;
    mBoard = new Board();
    mBoard.setPiece(3, 3, Board.K_KEI);
    mBoard.setPiece(5, 5, Board.K_KEI);
    initializeBitmaps();
    setOnTouchListener(this);
  }

  /**
   *  Called once during object construction.
   */
  public final void initialize(
      EventListener listener,
      ArrayList<Player> humanPlayers) {
    mListener = listener;
    mHumanPlayers = new ArrayList<Player>(humanPlayers);
  }

  /**
   *  Update the state of the board as well as players' turn.
   */
  public final void update(
      GameState gameState,
      Board board,
      Player currentPlayer) {
    mCurrentPlayer = currentPlayer;
    mBoard = new Board(board);
    mBoardInitialized = true;
    invalidate();
  }

  //
  // onTouch processing
  //
  private static final int T_EMPTY_SQUARE = 1;
  private static final int T_PLAYERS_PIECE = 2;
  private static final int S_INVALID = 0;
  private static final int S_PIECE = 1;
  private static final int S_CAPTURED = 2;
  private static final int S_MOVE_DESTINATION = 3;
  
  static private class NearestSquareFinder {
    public NearestSquareFinder(ScreenLayout layout, float sx, float sy) {
      mLayout = layout;
      mSx = sx;
      mSy = sy;
      
      mMinDistance = 9999;
      mPx = mPy = -1;
      mType = S_INVALID;
    }
    
    public void findNearestSquareOnBoard(
        Board  board, Player player, int searchType) {
      int px = mLayout.boardX(mSx);
      int py = mLayout.boardY(mSy);
      Log.d(TAG, String.format("NN: %d %d", px, py));
      for (int i = -1; i <= 1; ++i) {
        for (int j = -1; j <= 1; ++j) {
          int x = px + i;
          int y = py + j;
          if (x >= 0 && x < Board.DIM && y >= 0 && y < Board.DIM) {
            int piece = board.getPiece(x, y);
            switch (searchType) {
              case T_EMPTY_SQUARE: 
                if (piece != 0) continue;
                break;  
              case T_PLAYERS_PIECE:
                if (Board.player(piece) != player) continue;
                break;
            }
            tryScreenPosition(mLayout.screenX(x), mLayout.screenY(y), x, y, S_PIECE);
          }
        }
      }
    }

    private void tryScreenPosition(float sx, float sy, 
        int px, int py, int type) {
      float centerX = sx + mLayout.squareDim() / 2;
      float centerY = sy + mLayout.squareDim() / 2;      
      double distance = Math.hypot(centerX- mSx, centerY - mSy);
      if (distance < mMinDistance && distance < mLayout.squareDim()) {
        mMinDistance = distance;
        mPx = px;
        mPy = py;
        mType = type;
      }
    }
    
    public int nearestType() { return mType; }
    public int nearestX() { return mPx; }
    public int nearestY() { return mPy; }
    
    private ScreenLayout mLayout;
    private float mSx;
    private float mSy;
    
    private double mMinDistance;
    private int mPx;
    private int mPy;
    private int mType;
  }
  
  @Override public boolean onTouch(View v, MotionEvent event) {
    if (!isHumanPlayer(mCurrentPlayer)) return false;

    ScreenLayout layout = getScreenLayout();
    int squareDim = layout.squareDim();
    int action = event.getAction();

    NearestSquareFinder finder = new NearestSquareFinder(layout, event.getX(), event.getY());
    
    if (action == MotionEvent.ACTION_DOWN) {
      mMoveFrom =null ;
      
      // Start of touch operation
      finder.findNearestSquareOnBoard(mBoard, mCurrentPlayer, T_PLAYERS_PIECE);
      
      ArrayList<CapturedPiece> captured = listCapturedPieces(layout, mCurrentPlayer);
      for (int i = 0; i < captured.size(); ++i) {
        CapturedPiece cp = captured.get(i);
        finder.tryScreenPosition(cp.sx, cp.sy, i, -1, S_CAPTURED);
      }
      if (finder.nearestType() == S_PIECE) {
        mMoveFrom = new Position(finder.nearestX(), finder.nearestY());
      } else if (finder.nearestType() == S_CAPTURED) {
        // Dropping a captured piece
        mMoveFrom = new Position(captured.get(finder.nearestX()));
      } else {
        return false;
      }
      invalidate();
      return true;
    }

    if (mMoveFrom != null) {
      Position to = null;
      if (mMoveFrom.isOnBoard()) {
        ArrayList<Position> dests = possibleMoveTargets(
            mBoard.getPiece(mMoveFrom.getX(), mMoveFrom.getY()),
            mMoveFrom.getX(), mMoveFrom.getY());
        for (Position d: dests) {
          finder.tryScreenPosition(
              layout.screenX(d.getX()), layout.screenY(d.getY()),
              d.getX(), d.getY(), S_MOVE_DESTINATION);               
        }
        if (finder.nearestType() == S_MOVE_DESTINATION) {
          to = new Position(finder.nearestX(), finder.nearestY());
        }
      } else {
        finder.findNearestSquareOnBoard(mBoard, mCurrentPlayer, T_EMPTY_SQUARE);
        if (finder.nearestType() == S_PIECE) {
          to = new Position(finder.nearestX(), finder.nearestY());
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
        Move move = new Move();
        move.toX = mMoveTo.getX();
        move.toY = mMoveTo.getY();

        if (mMoveFrom.isOnBoard()) {
          move.piece = mBoard.getPiece(mMoveFrom.getX(), mMoveFrom.getY());
          move.fromX = mMoveFrom.getX();
          move.fromY = mMoveFrom.getY();
        } else {
          move.piece = mMoveFrom.capturedPiece().piece;
          move.fromX = move.fromY = -1;
        }
        mListener.onHumanMove(mCurrentPlayer, move);
        mCurrentPlayer = Player.INVALID;
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
        if (Board.player(v) == Player.WHITE) {
          bm = mWhiteBitmaps[t];
        } else {
          bm = mBlackBitmaps[t];
        }
        BitmapDrawable b = new BitmapDrawable(getResources(), bm);

        float sx = layout.screenX(x);
        float sy = layout.screenY(y);
        b.setBounds((int)sx, (int)sy, (int)(sx + squareDim), (int)(sy + squareDim));
        b.draw(canvas);
      }
    }

    if (mBoard.mCapturedBlack != 0) {
      drawCapturedPieces(canvas, layout, Player.BLACK);
    }
    if (mBoard.mCapturedWhite!= 0) {
      drawCapturedPieces(canvas, layout, Player.WHITE);
    }

    if (mMoveFrom != null) {
      Paint p = new Paint();
      //p.setColor(0x28000000);
      p.setColor(0x28ff8c00);
      if (mMoveFrom.isOnBoard()) {
        ArrayList<Position> dests = possibleMoveTargets(
            mBoard.getPiece(mMoveFrom.getX(), mMoveFrom.getY()),
            mMoveFrom.getX(), mMoveFrom.getY());
        
        Paint cp = new Paint();
        cp.setColor(0xc0ff8c00);
        cp.setStyle(Style.FILL);
        for (Position dest: dests) {
          float sx = layout.screenX(dest.getX());
          float sy = layout.screenY(dest.getY());

          sx += squareDim / 2.0f;
          sy += squareDim / 2.0f;
          canvas.drawCircle(sx, sy, 5, cp);
        }
      } else {
        CapturedPiece cp = mMoveFrom.capturedPiece();
        canvas.drawRect(new RectF(cp.sx, cp.sy, cp.sx + squareDim, cp.sy + squareDim), p);
      }
    }
    if (mMoveTo != null) {
      Paint p = new Paint();
      p.setColor(0x50000000);
      float sx = layout.screenX(mMoveTo.getX());
      float sy = layout.screenY(mMoveTo.getY());
      //canvas.drawRect(new Rect(sx, sy, sx + squareDim, sy + squareDim), p);

      Paint cp = new Paint();
      cp.setColor(0xc0ff4500);
      sx += squareDim / 2;
      sy += squareDim / 2;
      canvas.drawCircle(sx, sy, 5, cp);
      
    }
    if (!mBoardInitialized) {
      if (mInitializingToast == null) {
        mInitializingToast = Toast.makeText(getContext(), "Initializing",
            Toast.LENGTH_LONG);
      }
      mInitializingToast.show();
    } else {
      if (mInitializingToast != null) {
        mInitializingToast.cancel();
      }
    }
  }

  //
  // Implementation details
  //

  private static final String TAG = "BoardView";

  // Bitmap for all the pieces. mWhiteBitmaps[i] is the same as
  // mBlackBitmapsmaps[i], except upside down.
  private Bitmap mBlackBitmaps[];
  private Bitmap mWhiteBitmaps[];

  private Player mCurrentPlayer;   // Player currently holding the turn
  private Board mBoard;            // Current state of the board
  private boolean mBoardInitialized; 
  private Toast mInitializingToast;
  
  // Position represents a logical position of a piece.
  //
  // @invariant (0,0) <= (x,y) < (9,9).
  private static class Position {
    // Point to a coordinate on the board
    public Position(int x, int y) { mX = x; mY = y; }

    // Point to a captured piece
    public Position(CapturedPiece p) {
      mCapturedPiece = p;
    }

    @Override public boolean equals(Object o) {
      if (o instanceof Position) {
        Position p = (Position)o;
        return ((p.mCapturedPiece == null && mCapturedPiece == null) ||
                (p.mCapturedPiece != null && p.mCapturedPiece.equals(mCapturedPiece))) &&
               p.mX == mX &&
               p.mY == mY;
      }
      return false;
    }

    @Override public int hashCode() {
      throw new AssertionError("hashCode not implemented");
    }

    boolean isOnBoard() { return mCapturedPiece == null; }

    int getX() {
      if (!isOnBoard()) throw new AssertionError("blah");
      return mX;
    }

    int getY() {
      if (!isOnBoard()) throw new AssertionError("blah");
      return mY;
    }

    CapturedPiece capturedPiece() {
      if (isOnBoard()) throw new AssertionError("blah");
      return mCapturedPiece;
    }

    // Set iff the Position describes a captured piece
    private CapturedPiece mCapturedPiece;

    // mX and mY are set iff the Position describes a coordinate on the board.
    private int mX;
    private int mY;
  }

  // A class describing the pixel-level layout of this view.
  private static class ScreenLayout {
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
      } else {
        // Captured pieces are shown at the left & right of the board
        mPortrait = false;
        dim = height;
        mCapturedWhite = new Rect(0, 0, dim / 10, dim);
        mCapturedBlack = new Rect(dim * 11 / 10, 0, dim * 12/ 10, dim);
        mBoard = new Rect(dim / 10, 0, dim * 11 / 10, dim);
      }
      mSquareDim = dim / Board.DIM;
    }

    // Get the screen dimension
    public int getScreenWidth() { return mWidth; }
    public int getScreenHeight() { return mHeight; }
    public int squareDim() { return mSquareDim; }
    public Rect getBoard() { return mBoard; }

    // Convert X screen coord to board position. May return a position
    // outside the range [0,Board.DIM).
    public int boardX(float sx) {
      int px = (int)((sx - mBoard.left) / mSquareDim);
      return px;
    }

    // Convert X screen coord to board position. May return a position
    // outside the range [0,Board.DIM).
    public int boardY(float sy) {
      int py = (int)((sy - mBoard.top) / mSquareDim);
      return py;
    }

    // Convert X board position to the position of the left edge on the screen.
    public float screenX(int px) {
      return mBoard.left + mBoard.width() * px / Board.DIM;
    }

    // Convert board Y position to the position of the top edge on the screen.
    public float screenY(int py) {
      return mBoard.top + mBoard.height() * py / Board.DIM;
    }

    // Return the screen location for displaying a captured piece.
    //
    // @p index an integer 0, 1, 2, ... that specifies the position of the
    // piece in captured list.
    private int capturedScreenX(Player player, int index) {
      Rect r = (player == Player.BLACK ? mCapturedBlack : mCapturedWhite);
      if (mPortrait) {
        return r.left + mSquareDim * index * 4 / 3;
      } else {
        return r.left;
      }
    }

    private int capturedScreenY(Player player, int index) {
      Rect r = (player == Player.BLACK ? mCapturedBlack : mCapturedWhite);
      if (mPortrait) {
        return r.top;
      } else {
        return r.top + mSquareDim * index * 4 / 3;
      }
    }

    boolean mPortrait;    // is the screen in portrait mode?
    int mWidth, mHeight;  // screen pixel size
    int mSquareDim; // pixel size of each square in the board
    Rect mBoard;   // display the board status
    Rect mCapturedBlack;  // display pieces captured by black player
    Rect mCapturedWhite;  // display pieces captured by white player
  }
  private ScreenLayout mCachedLayout;

  // If non-NULL, user is trying to move the piece from this square.
  // @invariant mMoveFrom == null || (0,0) <= mMoveFrom < (Board.DIM, Board.DIM)
  private Position mMoveFrom;

  // If non-NULL, user is trying to move the piece to this square.
  // @invariant mMoveTo== null || (0,0) <= mMoveTo < (Board.DIM, Board.DIM)
  private Position mMoveTo;

  private EventListener mListener;
  private ArrayList<Player> mHumanPlayers;
  
  void drawEmptyBoard(Canvas canvas, ScreenLayout layout) {
    // Fill the board square
    Rect boardRect = layout.getBoard();
    Paint p = new Paint();
    p.setColor(0xfff5deb3);
    canvas.drawRect(boardRect, p);

    // Draw the gridlines
    p.setColor(0xff000000);
    for (int i = 0; i < Board.DIM; ++i) {
      float sx = layout.screenX(i);
      float sy = layout.screenY(i);
      canvas.drawLine(sx, boardRect.top, sx, boardRect.bottom, p);
      canvas.drawLine(boardRect.left, sy, boardRect.right, sy, p);
    }
  }

  private static class CapturedPiece {
    public CapturedPiece(int p, int i, float x, float y) {
      piece = p; 
      n = i;
      sx = x;
      sy = y;
    }
    
    public final boolean equals(CapturedPiece p) {
      if (p == null) return false;
      return piece == p.piece && n == p.n && sx == p.sx && sy == p.sy;
    }
    
    public final int piece;
    public final int n;
    public final float sx, sy;
  }
  
  private ArrayList<CapturedPiece> listCapturedPieces(
      ScreenLayout layout,
      Player player) {
    final int bits = (player == Player.BLACK ?
        mBoard.mCapturedBlack : mBoard.mCapturedWhite);
    
    ArrayList<CapturedPiece> pieces = new ArrayList<CapturedPiece>();
    int seq = 0;
    int n = Board.numCapturedFu(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Board.K_FU, n, 
          layout.capturedScreenX(player, seq),
          layout.capturedScreenY(player, seq)));
      ++seq;
    }
    n = Board.numCapturedKyo(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Board.K_KYO, n, 
          layout.capturedScreenX(player, seq),
          layout.capturedScreenY(player, seq)));
      ++seq;
    }
    n = Board.numCapturedKei(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Board.K_KEI, n, 
          layout.capturedScreenX(player, seq),
          layout.capturedScreenY(player, seq)));
      ++seq;
    }
    n = Board.numCapturedGin(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Board.K_GIN, n, 
          layout.capturedScreenX(player, seq),
          layout.capturedScreenY(player, seq)));
      ++seq;
    }
    n = Board.numCapturedKin(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Board.K_KIN, n, 
          layout.capturedScreenX(player, seq),
          layout.capturedScreenY(player, seq)));
      ++seq;
    }
    n = Board.numCapturedKaku(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Board.K_KAKU, n, 
          layout.capturedScreenX(player, seq),
          layout.capturedScreenY(player, seq)));
      ++seq;
    }
    n = Board.numCapturedHi(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Board.K_HI, n, 
          layout.capturedScreenX(player, seq),
          layout.capturedScreenY(player, seq)));
      ++seq;
    }
    return pieces;
  }
      
  
  private void drawCapturedPieces(Canvas canvas, ScreenLayout layout, Player player) {
    ArrayList<CapturedPiece> pieces = listCapturedPieces(layout, player);
    Bitmap[] bitmaps = (player == Player.BLACK ? mBlackBitmaps : mWhiteBitmaps);
    for (int i = 0; i < pieces.size(); ++i) {
      CapturedPiece p = pieces.get(i);
      drawCapturedPiece(canvas, layout, bitmaps[p.piece], p.n, p.sx, p.sy);
    }
  }

  private void drawPiece(Canvas canvas, ScreenLayout layout, Bitmap bm, float sx, float sy) {
    BitmapDrawable b = new BitmapDrawable(getResources(), bm);
    b.setBounds((int)sx, (int)sy, 
        (int)(sx + layout.squareDim()), (int)(sy + layout.squareDim()));
    b.draw(canvas);
  }

  private void drawCapturedPiece(Canvas canvas, 
      ScreenLayout layout,
      Bitmap bm, int num_pieces, float sx, float sy) {
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

  boolean isHumanPlayer(Player p) {
    return mHumanPlayers.contains(p);
  }

  // Load bitmaps for pieces. Called once when the process starts
  void initializeBitmaps() {
    Resources r = getResources();
    mBlackBitmaps = new Bitmap[Board.NUM_TYPES];
    mWhiteBitmaps = new Bitmap[Board.NUM_TYPES];
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
      mBlackBitmaps[i] = BitmapFactory.decodeResource(r, id);
      mWhiteBitmaps[i] = Bitmap.createBitmap(mBlackBitmaps[i], 0, 0,
          mBlackBitmaps[i].getWidth(), mBlackBitmaps[i].getHeight(),
          flip, false);
    }
    assert 1 == 0;
  }

  // Helper class for listing possible positions a piece can move to
  class MoveTargetsLister {
    // <cur_x, cur_y> is the current board position of the piece.
    // Both are in range [0, Board.DIM).
    public MoveTargetsLister(Player player, int cur_x, int cur_y) {
      mPlayer = player;
      mCurX = cur_x;
      mCurY = cur_y;
    }

    // If the piece can be moved to <cur_x+dx, cur_y+dy>, add it to
    // mTargets.
    public void tryMove(int dx, int dy) {
      mSeenOpponentPiece = false;
      if (mPlayer == Player.WHITE) dy = -dy;
      tryMoveTo(mCurX + dx, mCurY + dy);
    }

    // Perform tryMove for each <dx,dy>, <2*dx,2*dy>, ...,
    // Stop upon reaching the first disallowed square.
    public void tryMoveMulti(int dx, int dy) {
      mSeenOpponentPiece = false;
      if (mPlayer == Player.WHITE) dy = -dy;
      int x = mCurX;
      int y = mCurY;
      for (;;) {
        x += dx;
        y += dy;
        if (!tryMoveTo(x, y)) break;
     }
    }

    // Return the computed list of move targets.
    public ArrayList<Position> getTargets() { return mTargets; }


    boolean tryMoveTo(int x, int y) {
      // Disallow moving outside the board
      if (x < 0 || x >= Board.DIM || y < 0 || y >= Board.DIM) {
        return false;
      }
      // Disallow skipping over an opponent piece
      if (mSeenOpponentPiece) return false;

      // Disallow occuping the same square twice
      int existing = mBoard.getPiece(x, y);
      if (existing != 0) {
        if (Board.player(existing) == mPlayer) return false;
        mSeenOpponentPiece = true;
        mTargets.add(new Position(x, y));
        return true;
      }

      mTargets.add(new Position(x, y));
      return true;
    }

    final ArrayList<Position> mTargets = new ArrayList<Position>();
    Player mPlayer;
    int mCurX;
    int mCurY;
    boolean mSeenOpponentPiece;
  }

  // Generate the list of board positions that a piece at <cur_x, cur_y> can
  // move to. It takes other pieces on the board into account, but it may
  // still generate illegal moves -- e.g., this method doesn't check for
  // nifu, sennichite, uchi-fu zume aren't by this method.
  private ArrayList<Position> possibleMoveTargets(
      int piece, int cur_x, int cur_y) {
    int type = Board.type(piece);
    Player player = Board.player(piece);
    ArrayList<Position> dests = new ArrayList<Position>();
    MoveTargetsLister state = new MoveTargetsLister(player, cur_x, cur_y);

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
    return state.getTargets();
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
