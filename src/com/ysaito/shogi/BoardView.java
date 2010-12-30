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
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.ysaito.shogi.Board;

public class BoardView extends View implements View.OnTouchListener {
  // Bitmap for all the pieces. mGoteBitmaps[i] is the same as mSenteBitmaps[i], except upside down.
  private Bitmap mSenteBitmaps[];
  private Bitmap mGoteBitmaps[];

  private int mTurn;  // who's allowed to move pieces? One of Board.P_XXX.

  // Current state of the board
  private Board mBoard;   

  private class Position {
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
    Rect r = screenRect();
    int squareDim = r.width() / Board.DIM;
    int action = event.getAction();

    int px = (int)((event.getX() - r.left) / squareDim);
    int py = (int)((event.getY() - r.top) / squareDim);
    if (px < 0 || px >= Board.DIM || py < 0 || py >= Board.DIM) {
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
      Position to = findSnapSquare(r, mMoveFrom.x, mMoveFrom.y, event.getX(), event.getY());
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

    Rect r = screenRect();
    int squareDim = r.width() / Board.DIM;

    // Draw the board
    Paint p = new Paint();
    p.setColor(0xfff5deb3);
    canvas.drawRect(r, p);

    // Draw the gridlines
    p.setColor(0xff000000);
    for (int i = 0; i < DIM; ++i) {
      int sx = screenX(r, i);
      int sy = screenY(r, i);
      canvas.drawLine(sx, r.top, sx, r.bottom, p);
      canvas.drawLine(r.left, sy, r.right, sy, p);
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

        int sx = screenX(r, x);
        int sy = screenY(r, y);
        b.setBounds(sx, sy, sx + squareDim, sy + squareDim);
        b.draw(canvas);
      }
    }

    if (mMoveFrom != null) {
      p.setColor(0x10000000);
      ArrayList<Position> dests = possibleMoveDestinations(
          mBoard.getPiece(mMoveFrom.x, mMoveFrom.y),
          mMoveFrom.x, mMoveFrom.y);
      for (Position dest: dests) {
        int sx = screenX(r, dest.x);
        int sy = screenY(r, dest.y);
        canvas.drawRect(new Rect(sx, sy, sx + squareDim, sy + squareDim), p);
      }
    }
    if (mMoveTo != null) {
      p.setColor(0x40000000);
      int sx = screenX(r, mMoveTo.x);
      int sy = screenY(r, mMoveTo.y);
      canvas.drawRect(new Rect(sx, sy, sx + squareDim, sy + squareDim), p);
    }
  }

  public void setBoard(Board board) {
    mBoard = new Board(board);
    invalidate();
  }

  private void initializeBitmaps() {
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

  class MoveDestinationsState {
    public MoveDestinationsState(int player, int cur_x, int cur_y) {
      mPlayer = player;
      mCurX = cur_x;
      mCurY = cur_y;
    }
    
    public ArrayList<Position> getDests() { return mDests; }
    
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
    
    public void tryMove(int dx, int dy) {
      mSeenOpponentPiece = false;
      canMoveTo(mCurX + dx, mCurY + dy);
    }
    
    boolean canMoveTo(int x, int y) {
      // Don't allowing moving outside the board
      if (x < 0 || x >= Board.DIM || y < 0 || y >= Board.DIM) {
        return false;
      }
      
      int existing = mBoard.getPiece(x, y);
      if (existing != 0) {
        // Can't occupy the same square twice
        if (Board.player(existing) == mPlayer) return false;
        
        // We can't skip over an opponent
        if (mSeenOpponentPiece) return false;
        
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

  private Position findSnapSquare(Rect screenRect, int from_x, int from_y, float cur_sx, float cur_sy) {
    ArrayList<Position> dests = possibleMoveDestinations(mBoard.getPiece(from_x, from_y), from_x, from_y);
    Position nearest = null;
    float min_distance = screenDistance(screenRect, from_x, from_y, cur_sx, cur_sy);

    for (Position dest: dests) {
      float distance = screenDistance(screenRect, dest.x, dest.y, cur_sx, cur_sy);
      if (distance < min_distance) {
        nearest = dest;
        min_distance = distance;
      }
    }
    return nearest;
  }

  private int screenX(Rect r, int x) { return r.left + r.width() * x / Board.DIM; }
  private int screenY(Rect r, int y) { return r.top + r.height() * y / Board.DIM; }

  // Compute the distance from board position <px, py> to screen location <event_x, event_y>. 
  private float screenDistance(Rect r, int px, int py, float event_x, float event_y) {
    int sx = screenX(r, px) + r.width() / 2 / Board.DIM;
    int sy = screenY(r, py) + r.height() / 2 / Board.DIM;
    return Math.abs(sx - event_x) + Math.abs(sy - event_y);
  }

  Rect screenRect() {
    int screenDim = Math.min(getWidth(), getHeight());
    return new Rect(0, 0, screenDim, screenDim);
  }
};
