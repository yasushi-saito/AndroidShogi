package com.ysaito.shogi;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Store the state of a Shogi board.
 */
public class Board implements java.io.Serializable {
  // X and Y dimensions of a board
  public static final int DIM = 9; 


  // mSquares is a 81-entry array. mSquares[X + 9 * Y] stores the piece at coordinate <X, Y>. 
  // <0, 0> is at the upper left corner of the board. 
  // 
  // The sign of the value describes the owner of the piece. A positive (negative) value means that the piece
  // is owned by Player.BLACK (Player.WHITE). The absolute value describes the piece type as defined in the Piece class.
  public int mSquares[];    
  // Encode the set of pieces captured by the BLACK player. Use numCaptured* methods to parse the value. 
  public int mCapturedBlack;
  // Encode the set of pieces captured by the WHITE player. Use numCaptured* methods to parse the value.   
  public int mCapturedWhite;

  public static final boolean isPromoted(int piece) {
    return type(piece) >= 9;
  }

  public static final int promote(int piece) {
    assert !isPromoted(piece);
    if (piece < 0) {
      return piece - 8;
    } else {
      return piece + 8;
    }
  }

  // Given a piece in mSquares[], return the player type.
  public static final Player player(int piece) { 
    if (piece > 0) return Player.BLACK;
    if (piece == 0) return Player.INVALID;
    return Player.WHITE;
  }

  // Given a piece in mSquares[], return its type, i.e., Piece.XXX.
  public static final int type(int piece) { 
    return (piece < 0 ? -piece: piece); 
  }

  // Helper functions to parse the value of mCapturedUp or mCapturedDown.
  public static final int numCapturedFu(int c) { return c & 0x1f; }
  public static final int numCapturedKyo(int c) { return (c >> 5) & 7; }
  public static final int numCapturedKei(int c) { return (c >>  8) & 0x07; }
  public static final int numCapturedGin(int c) { return (c >> 11) & 0x07; }
  public static final int numCapturedKin(int c) { return (c >> 14) & 0x07; }
  public static final int numCapturedKaku(int c) { return (c >> 17) & 3; }
  public static final int numCapturedHi(int c) { return (c >> 19); }

  public Board() {
    mSquares = new int[DIM * DIM];  // initialized to zero
  }

  public Board(Board src) {
    mSquares = Arrays.copyOf(src.mSquares, src.mSquares.length);
    mCapturedBlack = src.mCapturedBlack;
    mCapturedWhite = src.mCapturedWhite;
  }

  public final void setPiece(int x, int y, int piece) {
    mSquares[x + y * DIM] = piece;
  }

  public final int getPiece(int x, int y) {
    return mSquares[x + y * DIM];
  }
  
  public static class MoveDelta {
    public MoveDelta(int x, int y, boolean m) { deltaX = x; deltaY = y; multi = m; }
    
    // horizontal and vertical deltas. The values are for Player.BLACK.
    // For Player.WHITE, the value of "y" must be negated.
    public final int deltaX, deltaY;
    
    // if true, can move to <deltaX*N, deltaY*N> for every positive N.
    public final boolean multi;
  }

  public static class Position {
    public Position(int tx, int ty) { x = tx; y = ty; }
    public final int x, y;
  }
  
  /**
   * Generate the list of board positions that a piece at <fromX, fromY> can
   * move to. It takes other pieces on the board into account, but it may
   * still generate illegal moves -- e.g., this method doesn't check for
   * nifu, sennichite, uchi-fu zume aren't by this method.
   */
  ArrayList<Position> possibleMoveDestinations(int fromX, int fromY) {
    int piece = getPiece(fromX, fromY);
    MoveTargetsLister lister = new MoveTargetsLister(
        this, player(piece), fromX, fromY);
    for (MoveDelta m: possibleMoves(type(piece))) {
      lister.tryMove(m);
    }
    return lister.getTargets();
  }
  
  // Helper class for listing possible positions a piece can move to
  private static final class MoveTargetsLister {
    // <cur_x, cur_y> is the current board position of the piece.
    // Both are in range [0, Board.DIM).
    public MoveTargetsLister(Board board, Player player, int cur_x, int cur_y) {
      mBoard = board;
      mPlayer = player;
      mCurX = cur_x;
      mCurY = cur_y;
    }

    // TODO(saito) disallow double pawn moves.
    
    // If the piece can be moved to <cur_x+dx, cur_y+dy>, add it to
    // mTargets.
    public final void tryMove(MoveDelta m) {
      mSeenOpponentPiece = false;
      int dy = m.deltaY;
      if (mPlayer == Player.WHITE) dy = -dy;
      if (!m.multi) {
        tryMoveTo(mCurX + m.deltaX, mCurY + dy);
      } else {
        int x = mCurX;
        int y = mCurY;
        for (;;) {
          x += m.deltaX;
          y += dy;
          if (!tryMoveTo(x, y)) break;
        }
      }
    }

    // Return the computed list of move targets.
    public final ArrayList<Position> getTargets() { return mTargets; }


    private final boolean tryMoveTo(int x, int y) {
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

    private final ArrayList<Position> mTargets 
      = new ArrayList<Position>();
    private final Board mBoard;
    private final Player mPlayer;
    private final int mCurX;
    private final int mCurY;
    private boolean mSeenOpponentPiece;
  }

  
  public static MoveDelta[] possibleMoves(int piece) {
    switch (piece) {
    case Piece.FU: return mFuMoves;
    case Piece.KYO: return mKyoMoves;
    case Piece.KEI: return mKeiMoves;
    case Piece.GIN: return mGinMoves;
    case Piece.KIN:
    case Piece.TO:
    case Piece.NARI_KYO:
    case Piece.NARI_KEI:
    case Piece.NARI_GIN:        
      return mKinMoves;
    case Piece.KAKU: return mKakuMoves;
    case Piece.UMA: return mUmaMoves;
    case Piece.HI: return mHiMoves;
    case Piece.RYU: return mRyuMoves;
    case Piece.OU: return mOuMoves;
    default:
      throw new AssertionError("Invalid piece " + piece);
    }
  }
  
  private static final MoveDelta[] mFuMoves = { new MoveDelta(0, -1, false) };
  private static final MoveDelta[] mKyoMoves = { new MoveDelta(0, -1, true) };  
  private static final MoveDelta[] mKeiMoves = { 
    new MoveDelta(-1, -2, false),
    new MoveDelta(1, -2, false) };
  private static final MoveDelta[] mGinMoves = {
    new MoveDelta(-1, -1, false),
    new MoveDelta(0, -1, false),
    new MoveDelta(1, -1, false),
    new MoveDelta(-1, 1, false),
    new MoveDelta(1, 1, false) };
  private static final MoveDelta[] mKinMoves = {
    new MoveDelta(-1, -1, false),
    new MoveDelta(0, -1, false),
    new MoveDelta(1, -1, false),
    new MoveDelta(-1, 0, false),
    new MoveDelta(1, 0, false),
    new MoveDelta(0, 1, false) };
  private static final MoveDelta[] mKakuMoves = {
    new MoveDelta(-1, -1, true),
    new MoveDelta(1, 1, true),
    new MoveDelta(1, -1, true),
    new MoveDelta(-1, 1, true),
  };  
  private static final MoveDelta[] mUmaMoves = {
    new MoveDelta(-1, -1, true),
    new MoveDelta(1, 1, true),
    new MoveDelta(1, -1, true),
    new MoveDelta(-1, 1, true),
    new MoveDelta(0, -1, false),
    new MoveDelta(0, 1, false),
    new MoveDelta(1, 0, false),
    new MoveDelta(-1, 0, false),
  };  
  private static final MoveDelta[] mHiMoves = {  
    new MoveDelta(0, -1, true),
    new MoveDelta(0, 1, true),
    new MoveDelta(-1, 0, true),
    new MoveDelta(1, 0, true),
  };
  private static final MoveDelta[] mRyuMoves = {  
    new MoveDelta(0, -1, true),
    new MoveDelta(0, 1, true),
    new MoveDelta(-1, 0, true),
    new MoveDelta(1, 0, true),
    new MoveDelta(-1, -1, false),
    new MoveDelta(-1, 1, false),
    new MoveDelta(1, -1, false),
    new MoveDelta(1, 1, false),
  };
  private static final MoveDelta[] mOuMoves = { 
    new MoveDelta(0, -1, false),
    new MoveDelta(0, 1, false),
    new MoveDelta(1, 0, false),
    new MoveDelta(-1, 0, false),
    new MoveDelta(-1, -1, false),
    new MoveDelta(-1, 1, false),
    new MoveDelta(1, -1, false),
    new MoveDelta(1, 1, false),
  };
  
}
