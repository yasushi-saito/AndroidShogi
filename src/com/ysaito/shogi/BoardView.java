package com.ysaito.shogi;
import java.util.ArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.ysaito.shogi.Board;

public class BoardView extends View implements View.OnTouchListener {
  static public final String TAG = "ShogiView";
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
    mBoard.setPiece(3, 3, Piece.KEI);
    mBoard.setPiece(5, 5, Piece.KEI);
    initializeBitmaps(context);
    setOnTouchListener(this);
  }

  /**
   *  Called once during object construction.
   */
  public final void initialize(
      EventListener listener,
      ArrayList<Player> humanPlayers,
      boolean flipScreen) {
    mListener = listener;
    mHumanPlayers = new ArrayList<Player>(humanPlayers);
    mFlipped = flipScreen;
  }

  /**
   * Flip the screen upside down.
   */
  public final void flipScreen() {
    mFlipped = !mFlipped;
    invalidate();
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

    public final void findNearestEmptySquareOnBoard(
        Board board, Player player, int pieceToDrop) { 
      int px = mLayout.boardX(mSx);
      int py = mLayout.boardY(mSy);
      if (Board.type(pieceToDrop) == Piece.FU) {
        // Disallow double pawns
        for (int y = 0; y < Board.DIM; ++y) {
          if (y != py && board.getPiece(px, y) == pieceToDrop) return;
        }
      }
      
      for (int i = -1; i <= 1; ++i) {
        for (int j = -1; j <= 1; ++j) {
          int x = px + i;
          int y = py + j;
          if (x >= 0 && x < Board.DIM && y >= 0 && y < Board.DIM) {
            int piece = board.getPiece(x, y);
            if (piece == 0) {
              tryScreenPosition(mLayout.screenX(x), mLayout.screenY(y), x, y, S_PIECE);
            }
          }
        }
      }
    }

    public final void findNearestPlayersPieceOnBoard(Board board, Player player) { 
      int px = mLayout.boardX(mSx);
      int py = mLayout.boardY(mSy);
      for (int i = -1; i <= 1; ++i) {
        for (int j = -1; j <= 1; ++j) {
          int x = px + i;
          int y = py + j;
          if (x >= 0 && x < Board.DIM && y >= 0 && y < Board.DIM) {
            if (Board.player(board.getPiece(x, y)) == player) {
              tryScreenPosition(mLayout.screenX(x), mLayout.screenY(y), x, y, S_PIECE);
            }
          }
        }
      }
    }

    private final void tryScreenPosition(float sx, float sy, 
        int px, int py, int type) {
      float centerX = sx + mLayout.getSquareDim() / 2;
      float centerY = sy + mLayout.getSquareDim() / 2;      
      double distance = Math.hypot(centerX- mSx, centerY - mSy);
      if (distance < mMinDistance && distance < mLayout.getSquareDim()) {
        mMinDistance = distance;
        mPx = px;
        mPy = py;
        mType = type;
      }
    }

    public final int nearestType() { return mType; }
    public final int nearestX() { return mPx; }
    public final int nearestY() { return mPy; }

    private ScreenLayout mLayout;
    private float mSx;
    private float mSy;

    private double mMinDistance;
    private int mPx;
    private int mPy;
    private int mType;
  }

  public boolean onTouch(View v, MotionEvent event) {
    if (!isHumanPlayer(mCurrentPlayer)) return false;

    ScreenLayout layout = getScreenLayout();
    int action = event.getAction();

    NearestSquareFinder finder = new NearestSquareFinder(layout, event.getX(), event.getY());

    if (action == MotionEvent.ACTION_DOWN) {
      mMoveFrom = null;

      // Start of touch operation
      finder.findNearestPlayersPieceOnBoard(mBoard, mCurrentPlayer);

      ArrayList<CapturedPiece> captured = listCapturedPieces(layout, mCurrentPlayer);
      for (int i = 0; i < captured.size(); ++i) {
        CapturedPiece cp = captured.get(i);
        finder.tryScreenPosition(cp.sx, cp.sy, i, -1, S_CAPTURED);
      }
      if (finder.nearestType() == S_PIECE) {
        mMoveFrom = new PositionOnBoard(finder.nearestX(), finder.nearestY());
      } else if (finder.nearestType() == S_CAPTURED) {
        // Dropping a captured piece
        mMoveFrom = captured.get(finder.nearestX());
      } else {
        return false;
      }
      invalidate();
      return true;
    }

    if (mMoveFrom != null) {
      PositionOnBoard to = null;
      if (mMoveFrom instanceof PositionOnBoard) {
        PositionOnBoard from = (PositionOnBoard)mMoveFrom;
        ArrayList<Board.Position> dests = mBoard.possibleMoveDestinations(from.x, from.y);
        for (Board.Position d: dests) {
          finder.tryScreenPosition(
              layout.screenX(d.x), layout.screenY(d.y),
              d.x, d.y, S_MOVE_DESTINATION);               
        }
        // Allow moving to the origin point to nullify the move.
        finder.tryScreenPosition(
            layout.screenX(from.x), layout.screenY(from.y), from.x, from.y, S_MOVE_DESTINATION);
      } else {
        CapturedPiece p = (CapturedPiece)mMoveFrom;
        finder.findNearestEmptySquareOnBoard(mBoard, mCurrentPlayer, p.piece);
      }
      if (finder.nearestType() != S_INVALID) {
        to = new PositionOnBoard(finder.nearestX(), finder.nearestY());
      }
      // If "to" is different from the currently selected square, redraw
      if ((to == null && mMoveTo != null) ||
          (to != null && !to.equals(mMoveTo))) {
        invalidate();
      }
      mMoveTo = to;
    }
    if (action == MotionEvent.ACTION_UP) {
      if (mMoveTo != null && !mMoveFrom.equals(mMoveTo)) {
        Move move = null;
        if (mMoveFrom instanceof PositionOnBoard) {
          PositionOnBoard from = (PositionOnBoard)mMoveFrom;
          move = new Move(
              mBoard.getPiece(from.x, from.y), 
              from.x, from.y,
              mMoveTo.x, mMoveTo.y);
        } else {
          move = new Move(
              ((CapturedPiece)mMoveFrom).piece,
              -1, -1,
              mMoveTo.x, mMoveTo.y);
          Log.d(TAG, String.format("MOVEMOVE %d", move.piece));
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
    ScreenLayout layout = getScreenLayout();
    int squareDim = layout.getSquareDim();

    drawEmptyBoard(canvas, layout);

    // Draw pieces
    for (int y = 0; y < Board.DIM; ++y) {
      for (int x = 0; x < Board.DIM; ++x) {
        int v = mBoard.getPiece(x, y);
        if (v == 0) continue;
        int alpha = 255;
        
        // A piece that the user is trying to move will be draw with a bit of
        // transparency.
        if (mMoveFrom != null && mMoveFrom instanceof PositionOnBoard) {
          PositionOnBoard p = (PositionOnBoard)mMoveFrom;
          if (x == p.x && y == p.y) alpha = 64;
        }
        drawPiece(canvas, layout, v, layout.screenX(x), layout.screenY(y), alpha);
      }
    }

    drawCapturedPieces(canvas, layout, Player.BLACK);
    drawCapturedPieces(canvas, layout, Player.WHITE);

    if (mMoveFrom != null) {
      if (mMoveFrom instanceof PositionOnBoard) {
        PositionOnBoard from = (PositionOnBoard)mMoveFrom;
        // Draw orange dots in each possible destination
        ArrayList<Board.Position> dests = mBoard.possibleMoveDestinations(from.x, from.y);

        Paint cp = new Paint();
        cp.setColor(0xc0ff8c00);
        cp.setStyle(Style.FILL);
        for (Board.Position dest: dests) {
          float sx = layout.screenX(dest.x);
          float sy = layout.screenY(dest.y);

          sx += squareDim / 2.0f;
          sy += squareDim / 2.0f;
          canvas.drawCircle(sx, sy, 5, cp);
        }
      } else {
        // Dropping a captured piece. Nothing to do
      }
    }
    if (mMoveTo != null) {
      // Move the piece to be moved with 25% transparency.
      int pieceToMove = -1;
      if (mMoveFrom instanceof PositionOnBoard) {
        PositionOnBoard from = (PositionOnBoard)mMoveFrom;        
        pieceToMove = mBoard.getPiece(from.x, from.y);
      } else {
        pieceToMove = ((CapturedPiece)mMoveFrom).piece;                
        if (mCurrentPlayer == Player.WHITE) pieceToMove = -pieceToMove;
      }
      // TODO(saito) draw a big circle around mMoveTo so that people
      // with chubby finger can still see the destination.
      drawPiece(canvas, layout, pieceToMove,
          layout.screenX(mMoveTo.x),
          layout.screenY(mMoveTo.y),
          192);
      
      Paint cp = new Paint();
      float radius = squareDim * 0.9f;
      float cx = layout.screenX(mMoveTo.x) + squareDim / 2.0f;
      float cy = layout.screenY(mMoveTo.y) + squareDim / 2.0f;
      cp.setShader(new RadialGradient(cx, cy, radius, 0xffb8860b, 0x00b8860b, Shader.TileMode.MIRROR));
      canvas.drawCircle(cx, cy, radius, cp);
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
  
  // Bitmap for all the pieces. mWhiteBitmaps[i] is the same as
  // mBlackBitmapsmaps[i], except upside down.
  private BitmapDrawable mBlackBitmaps[];
  private BitmapDrawable mWhiteBitmaps[];

  private boolean mFlipped;        // if true, flip the board upside down.
  private Player mCurrentPlayer;   // Player currently holding the turn
  private Board mBoard;            // Current state of the board
  private boolean mBoardInitialized; 
  private Toast mInitializingToast;

  // Position represents a logical position of a piece. It is either a
  // coordinate on the board (PositionOnBoard), or a captured piece (CapturedPiece).
  private static class Position { 
    @Override public int hashCode() {
      throw new AssertionError("hashCode not implemented");
    }
  }
  
  // Point to a coordinate on the board
  // @invariant (0,0) <= (mX,mY) < (9,9).
  private static class PositionOnBoard extends Position {
    public PositionOnBoard(int xx, int yy) { x = xx; y = yy; }
    @Override public boolean equals(Object o) {
      if (o instanceof PositionOnBoard) {
        PositionOnBoard p = (PositionOnBoard)o;
        return x== p.x && y == p.y;
      }
      return false;
    }
    public final int x, y;
  }
  
  // Point to a captured piece
  private static class CapturedPiece extends Position {
    public CapturedPiece(int p, int i, float x, float y) {
      piece = p; 
      n = i;
      sx = x;
      sy = y;
    }
    public final int piece;     // Piece type, one of Piece.XX. The value is negative if owned by Player.WHITE.
    public final int n;         // Number of pieces owned of type "piece".
    public final float sx, sy;  // Screen location at which this piece should be drawn.
  }

  // A class describing the pixel-level layout of this view.
  private static class ScreenLayout {
    public ScreenLayout(int width, int height, boolean flipped) {
      mWidth = width;
      mHeight = height;
      mFlipped = flipped;

      // TODO(saito) this code doesn't handle a square screen
      int dim;
      int sep = 10; // # pixels between the board and captured pieces.
      if (width < height) {
        // Portrait layout. Captured pieces are shown at the top & bottom of the board
        dim = width;
        mPortrait = true;
        mCapturedWhite= new Rect(0, 0, dim, dim/ 10);
        mCapturedBlack = new Rect(0, dim * 11 / 10 + sep*2, dim, dim * 12 / 10 + sep*2);
        mBoard = new Rect(0, dim/ 10 + sep, dim, dim * 11 / 10 + sep);
      } else {
        // Landscape layout. Captured pieces are shown at the left & right of the board
        mPortrait = false;
        dim = height;
        mCapturedWhite = new Rect(0, 0, dim / 10, dim);
        mCapturedBlack = new Rect(dim * 11 / 10 + sep*2, 0, dim * 12/ 10 + sep*2, dim);
        mBoard = new Rect(dim / 10 + sep, 0, dim * 11 / 10 + sep, dim);
      }
      if (mFlipped) {
        Rect tmp = mCapturedWhite;
        mCapturedWhite = mCapturedBlack;
        mCapturedBlack = tmp;
      }
      mSquareDim = dim / Board.DIM;
    }

    // Get the screen dimension
    public final int getScreenWidth() { return mWidth; }
    public final int getScreenHeight() { return mHeight; }
    public final int getSquareDim() { return mSquareDim; }
    public final boolean getFlipped() { return mFlipped; }
    public final Rect getBoard() { return mBoard; }

    // Convert X screen coord to board position. May return a position
    // outside the range [0,Board.DIM).
    public final int boardX(float sx) {
      return maybeFlip((int)((sx - mBoard.left) / mSquareDim));
    }

    // Convert X screen coord to board position. May return a position
    // outside the range [0,Board.DIM).
    public final int boardY(float sy) {
      return maybeFlip((int)((sy - mBoard.top) / mSquareDim));
    }

    // Convert X board position to the position of the left edge on the screen.
    public final float screenX(int px) {
      px = maybeFlip(px); 	
      return mBoard.left + mBoard.width() * px / Board.DIM;
    }

    // Convert board Y position to the position of the top edge on the screen.
    public final float screenY(int py) {
      py = maybeFlip(py); 	    	
      return mBoard.top + mBoard.height() * py / Board.DIM;
    }

    private final int maybeFlip(int p) {
      if (mFlipped) {
        return 8 - p;
      } else {
        return p;
      }
    }
    // Return the screen location for displaying a captured piece.
    //
    // @p index an integer 0, 1, 2, ... that specifies the position of the
    // piece in captured list.
    private final int capturedScreenX(Player player, int index) {
      Rect r = (player == Player.BLACK ? mCapturedBlack : mCapturedWhite);
      if (mPortrait) {
        return r.left + mSquareDim * index * 4 / 3;
      } else {
        return r.left;
      }
    }

    private final int capturedScreenY(Player player, int index) {
      Rect r = (player == Player.BLACK ? mCapturedBlack : mCapturedWhite);
      if (mPortrait) {
        return r.top;
      } else {
        return r.top + mSquareDim * index * 4 / 3;
      }
    }

    private boolean mPortrait;    // is the screen in portrait mode?
    private int mWidth, mHeight;  // screen pixel size
    private int mSquareDim;       // pixel size of each square in the board
    private boolean mFlipped;     // draw the board upside down
    private Rect mBoard;          // display the board status
    private Rect mCapturedBlack;  // display pieces captured by black player
    private Rect mCapturedWhite;  // display pieces captured by white player
  }
  private ScreenLayout mCachedLayout;

  // If non-NULL, user is trying to move the piece from this square.
  // @invariant mMoveFrom == null || (0,0) <= mMoveFrom < (Board.DIM, Board.DIM)
  private Position mMoveFrom;

  // If non-NULL, user is trying to move the piece to this square.
  // @invariant mMoveTo== null || (0,0) <= mMoveTo < (Board.DIM, Board.DIM)
  private PositionOnBoard mMoveTo;

  private EventListener mListener;
  private ArrayList<Player> mHumanPlayers;

  private final void drawEmptyBoard(Canvas canvas, ScreenLayout layout) {
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

  /**
   * List pieces captured by player.
   */
  private final ArrayList<CapturedPiece> listCapturedPieces(
      ScreenLayout layout,
      Player player) {
    int seq = 0;
    ArrayList<CapturedPiece> pieces = new ArrayList<CapturedPiece>();
    for (Board.CapturedPiece p: mBoard.getCapturedPieces(player)) {
      pieces.add(new CapturedPiece(p.piece, p.n, 
          layout.capturedScreenX(player, seq),
          layout.capturedScreenY(player, seq)));
      ++seq;
    }
    return pieces;
  }


  private final void drawCapturedPieces(
      Canvas canvas, 
      ScreenLayout layout, 
      Player player) {
    ArrayList<CapturedPiece> pieces = listCapturedPieces(layout, player);
    for (int i = 0; i < pieces.size(); ++i) {
      CapturedPiece p = pieces.get(i);
      // int piece = (player == Player.BLACK ? p.piece : -p.piece);
      drawCapturedPiece(canvas, layout, p.piece, p.n, p.sx, p.sy);
    }
  }

  private final void drawPiece(
      Canvas canvas, ScreenLayout layout, 
      int piece,
      float sx, float sy, int alpha) {
    boolean isBlack = (Board.player(piece) == Player.BLACK);
    BitmapDrawable[] bitmaps = (isBlack != mFlipped) ? mBlackBitmaps : mWhiteBitmaps;
    BitmapDrawable b = bitmaps[Board.type(piece)];
    b.setBounds((int)sx, (int)sy, 
        (int)(sx + layout.getSquareDim()), (int)(sy + layout.getSquareDim()));
    b.setAlpha(alpha);
    b.draw(canvas);
  }

  private final void drawCapturedPiece(Canvas canvas, 
      ScreenLayout layout,
      int piece, int n, float sx, float sy) {
    drawPiece(canvas, layout, piece, sx, sy, 255);
    if (n > 1) {
      int fontSize = 14;
      Paint p = new Paint();
      p.setTextSize(fontSize);
      p.setColor(0xffeeeeee);
      p.setTypeface(Typeface.DEFAULT_BOLD);
      canvas.drawText(Integer.toString(n),
          sx + layout.getSquareDim() - fontSize / 4,
          sy + layout.getSquareDim() - fontSize / 2,
          p);
    }
  }

  private boolean isHumanPlayer(Player p) {
    return mHumanPlayers.contains(p);
  }

  // Load bitmaps for pieces. Called once when this view is created.
  private final void initializeBitmaps(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    String prefix = prefs.getString("piece_style", "kinki_simple");

    Resources r = getResources();
    mBlackBitmaps = new BitmapDrawable[Piece.NUM_TYPES];
    mWhiteBitmaps = new BitmapDrawable[Piece.NUM_TYPES];
    String koma_names[] = {
        null,
        "fu", "kyo", "kei", "gin", "kin", "kaku", "hi", "ou",
        "to", "nari_kyo", "nari_kei", "nari_gin", null, "uma", "ryu"
    };

    Matrix flip = new Matrix();
    flip.postRotate(180);

    for (int i = 1; i < Piece.NUM_TYPES; ++i) {
      if (koma_names[i] == null) continue;
      int id = r.getIdentifier(String.format("@com.ysaito.shogi:drawable/%s_%s", prefix, koma_names[i]), null, null);
      Bitmap blackBm = BitmapFactory.decodeResource(r, id);
      mBlackBitmaps[i] = new BitmapDrawable(getResources(), blackBm);
      Bitmap whiteBm = Bitmap.createBitmap(blackBm, 0, 0, blackBm.getWidth(), blackBm.getHeight(), 
          flip, false);
      mWhiteBitmaps[i] = new BitmapDrawable(getResources(), whiteBm);
    }
  }

  private final ScreenLayout getScreenLayout() {
    if (mCachedLayout != null &&
        mCachedLayout.getScreenWidth() == getWidth() &&
        mCachedLayout.getScreenHeight() == getHeight() &&
        mCachedLayout.getFlipped() == mFlipped) {
      // reuse the cached value
    } else {
      mCachedLayout = new ScreenLayout(getWidth(), getHeight(), mFlipped);
    }
    return mCachedLayout;
  }
}
