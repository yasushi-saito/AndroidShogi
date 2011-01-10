package com.ysaito.shogi;

import java.util.ArrayList;

/**
 * Store the state of a Shogi board.
 */
public class Board implements java.io.Serializable {
  // X and Y dimensions of a board
  public static final int DIM = 9; 

  public static class CapturedPiece {
    public CapturedPiece(int p, int _n) { piece = p; n = _n; }
    public final int piece;  // one of Piece.*
    public final int n;      // number of pieces of the same type
  };
  
  public Board() {
    mSquares = new int[DIM * DIM];  // initialized to zero
    mCapturedBlackList = new ArrayList<CapturedPiece>();
    mCapturedWhiteList = new ArrayList<CapturedPiece>();
  }

  public Board(Board src) {
    mSquares = src.mSquares.clone();
    mCapturedBlack = src.mCapturedBlack;
    mCapturedWhite = src.mCapturedWhite;
    mCapturedBlackList = new ArrayList<CapturedPiece>(src.mCapturedBlack);
    mCapturedWhiteList = new ArrayList<CapturedPiece>(src.mCapturedWhite);
  }

  // getPiece and setPiece will set the piece at coordinate <x, y>. The upper left 
  // corner is <0, 0>. A piece belonging to Player.BLACK will have a positive value of 
  // one of Piece.* (e.g., Piece.FU). A piece belonging to Player.WHITE will have a negative value.
  // (e.g., -Piece.FU).
  public final void setPiece(int x, int y, int piece) {
    mSquares[x + y * DIM] = piece;
  }

  public final int getPiece(int x, int y) {
    return mSquares[x + y * DIM];
  }

  // Given a piece returned by getPiece(), return its type, e.g., Piece.FU, etc.
  public static final int type(int piece) { 
    return (piece < 0 ? -piece: piece); 
  }

  // Given a piece returned by getPieces(), return the player that owns it.
  public static final Player player(int piece) { 
    if (piece > 0) return Player.BLACK;
    if (piece == 0) return Player.INVALID;
    return Player.WHITE;
  }

  // Given a value returned by getPiece(), see if the piece is promoted.
  public static final boolean isPromoted(int piece) {
    return type(piece) >= 9;
  }

  public static final int promote(int piece) {
    Assert.isFalse(isPromoted(piece));
    if (piece < 0) {
      return piece - 8;
    } else {
      return piece + 8;
    }
  }
  
  public static final int unpromote(int piece) {
    Assert.isTrue(isPromoted(piece));
    if (piece < 0) {
      return piece + 8;
    } else {
      return piece - 8;
    }
    
  }

  // Get the list of piece type and its count captured by player p.
  // For Player.WHITE, the values of CapturedPiece.piece will be still
  // positive.
  public final ArrayList<CapturedPiece> getCapturedPieces(Player p) {
    // Note: The JNI code will set mCapturedBlack and mCapturedWhite.
    // getCapturedPiece will translate them to a list.
    if (p == Player.BLACK) {
      if (mLastReadCapturedBlack != mCapturedBlack) {
        mLastReadCapturedBlack = mCapturedBlack;
        mCapturedBlackList = listCapturedPieces(Player.BLACK, mCapturedBlack);
      }
      return mCapturedBlackList;
    } else if (p == Player.WHITE) {
      if (mLastReadCapturedWhite != mCapturedWhite) {
        mLastReadCapturedWhite = mCapturedWhite;
        mCapturedWhiteList = listCapturedPieces(Player.WHITE, mCapturedWhite);
      }
      return mCapturedWhiteList;
    } else {
      throw new AssertionError("Invalid player");
    }
  }
  
  // Absolute position on a Board.
  public static class Position {
    public Position(int tx, int ty) { x = tx; y = ty; }
    public final int x, y;
  }
  
  // A relative move.
  private static class MoveDelta {
    public MoveDelta(int x, int y, boolean m) { deltaX = x; deltaY = y; multi = m; }
    
    // horizontal and vertical deltas. The values are for Player.BLACK.
    // For Player.WHITE, the value of "y" must be negated.
    public final int deltaX, deltaY;
    
    // if true, can move to <deltaX*N, deltaY*N> for every positive N.
    public final boolean multi;
  }

  public void TEST_setCapturedPieces(Player p, ArrayList<Board.CapturedPiece> pieces) {
    if (p == Player.BLACK) {
      mCapturedBlackList = pieces;
      mCapturedBlack = mLastReadCapturedBlack = 0;
    } else {
      mCapturedWhiteList = pieces;
      mCapturedWhite = mLastReadCapturedWhite = 0;
    }
  }
  
  private static final ArrayList<CapturedPiece> listCapturedPieces(
      Player player, int bits) {
    int sign = (player == Player.BLACK ? 1 : -1);
    
    ArrayList<CapturedPiece> pieces = new ArrayList<CapturedPiece>();
    int n = Board.numCapturedFu(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Piece.FU * sign, n));
    }
    n = Board.numCapturedKyo(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Piece.KYO * sign, n));
    }
    n = Board.numCapturedKei(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Piece.KEI * sign, n));
    }
    n = Board.numCapturedGin(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Piece.GIN * sign, n));
    }
    n = Board.numCapturedKin(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Piece.KIN * sign, n));
    }
    n = Board.numCapturedKaku(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Piece.KAKU * sign, n));
    }
    n = Board.numCapturedHi(bits);
    if (n > 0) {
      pieces.add(new CapturedPiece(Piece.HI * sign, n));
    }
    return pieces;
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

  
  private static MoveDelta[] possibleMoves(int piece) {
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
  
  // Helper functions to parse the value of mCapturedBlack or mCapturedWhite.
  private static final int numCapturedFu(int c) { return c & 0x1f; }
  private static final int numCapturedKyo(int c) { return (c >> 5) & 7; }
  private static final int numCapturedKei(int c) { return (c >>  8) & 0x07; }
  private static final int numCapturedGin(int c) { return (c >> 11) & 0x07; }
  private static final int numCapturedKin(int c) { return (c >> 14) & 0x07; }
  private static final int numCapturedKaku(int c) { return (c >> 17) & 3; }
  private static final int numCapturedHi(int c) { return (c >> 19); }

  // mSquares is a 81-entry array. mSquares[X + 9 * Y] stores the piece at coordinate <X, Y>. 
  // <0, 0> is at the upper left corner of the board. 
  // 
  // The sign of the value describes the owner of the piece. A positive (negative) value means that the piece
  // is owned by Player.BLACK (Player.WHITE). The absolute value describes the piece type as defined in the Piece class.
  private int mSquares[];    
  
  // The following two fields are set directly by the JNI C code.
  private int mCapturedBlack;
  private int mCapturedWhite;
  
  // Encode the set of pieces captured by the BLACK player. 
  private ArrayList<CapturedPiece> mCapturedBlackList;
  // Encode the set of pieces captured by the WHITE player. 
  private ArrayList<CapturedPiece> mCapturedWhiteList;

  // the values of mCaptured{Black,White} used when computing the above lists.
  private int mLastReadCapturedBlack;
  private int mLastReadCapturedWhite;

}
