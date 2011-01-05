package com.ysaito.shogi;

/**
 * Shogi piece types. They are stored in Board.mSquares. 
 * 
 * Caution: These definitions are shared with Bonanza. Don't change!
 */ 
public class Piece {
  public static final int EMPTY = 0;  // placeholder for an unoccupied square
  public static final int FU = 1;
  public static final int KYO = 2;
  public static final int KEI = 3;
  public static final int GIN = 4;
  public static final int KIN = 5;
  public static final int KAKU = 6;
  public static final int HI = 7;
  public static final int OU = 8;
  public static final int TO = 9;
  public static final int NARI_KYO = 10;
  public static final int NARI_KEI = 11;
  public static final int NARI_GIN = 12;
  public static final int UMA = 14;
  public static final int RYU = 15;
  public static final int NUM_TYPES = 16;
  
  // CSA format string for each piece.
  public final static String[] csaNames = {
    null, "FU", "KY", "KE", "GI", "KI", "KA", "HI", "OU", 
          "TO", "NY", "NK", "NG", null, "UM", "RY"
  };
  
  public static int fromCsaName(String s) {
    for (int i = 0; i < csaNames.length; ++i) {
      String n = csaNames[i];
      if (n != null && n.equals(s)) return i;
    }
    throw new AssertionError("Illegal piece name: " + s);
  }
  
  public static class PossibleMove {
    public PossibleMove(int xx, int yy, boolean m) { x = xx; y = yy; multi = m; }
    
    // horizontal and vertical deltas. The values are for Player.BLACK.
    // For Player.WHITE, the value of "y" must be negated.
    public final int x, y;
    
    // if true, can move <x*N, y*N> for every positive N.
    public final boolean multi;
  }
  
  public static PossibleMove[] possibleMoves(int piece) {
    switch (piece) {
    case FU: return mFuMoves;
    case KYO: return mKyoMoves;
    case KEI: return mKeiMoves;
    case GIN: return mGinMoves;
    case KIN:
    case TO:
    case NARI_KYO:
    case NARI_KEI:
    case NARI_GIN:        
      return mKinMoves;
    case KAKU: return mKakuMoves;
    case UMA: return mUmaMoves;
    case HI: return mHiMoves;
    case RYU: return mRyuMoves;
    case OU: return mOuMoves;
    default:
      throw new AssertionError("Invalid piece " + piece);
    }
  }
  
  private static final PossibleMove[] mFuMoves = { new PossibleMove(0, -1, false) };
  private static final PossibleMove[] mKyoMoves = { new PossibleMove(0, -1, true) };  
  private static final PossibleMove[] mKeiMoves = { 
    new PossibleMove(-1, -2, false),
    new PossibleMove(1, -2, false) };
  private static final PossibleMove[] mGinMoves = {
    new PossibleMove(-1, -1, false),
    new PossibleMove(0, -1, false),
    new PossibleMove(1, -1, false),
    new PossibleMove(-1, 1, false),
    new PossibleMove(1, 1, false) };
  private static final PossibleMove[] mKinMoves = {
    new PossibleMove(-1, -1, false),
    new PossibleMove(0, -1, false),
    new PossibleMove(1, -1, false),
    new PossibleMove(-1, 0, false),
    new PossibleMove(1, 0, false),
    new PossibleMove(0, 1, false) };
  private static final PossibleMove[] mKakuMoves = {
    new PossibleMove(-1, -1, true),
    new PossibleMove(1, 1, true),
    new PossibleMove(1, -1, true),
    new PossibleMove(-1, 1, true),
  };  
  private static final PossibleMove[] mUmaMoves = {
    new PossibleMove(-1, -1, true),
    new PossibleMove(1, 1, true),
    new PossibleMove(1, -1, true),
    new PossibleMove(-1, 1, true),
    new PossibleMove(0, -1, false),
    new PossibleMove(0, 1, false),
    new PossibleMove(1, 0, false),
    new PossibleMove(-1, 0, false),
  };  
  private static final PossibleMove[] mHiMoves = {  
    new PossibleMove(0, -1, true),
    new PossibleMove(0, 1, true),
    new PossibleMove(-1, 0, true),
    new PossibleMove(1, 0, true),
  };
  private static final PossibleMove[] mRyuMoves = {  
    new PossibleMove(0, -1, true),
    new PossibleMove(0, 1, true),
    new PossibleMove(-1, 0, true),
    new PossibleMove(1, 0, true),
    new PossibleMove(-1, -1, false),
    new PossibleMove(-1, 1, false),
    new PossibleMove(1, -1, false),
    new PossibleMove(1, 1, false),
  };
  private static final PossibleMove[] mOuMoves = { 
    new PossibleMove(0, -1, false),
    new PossibleMove(0, 1, false),
    new PossibleMove(1, 0, false),
    new PossibleMove(-1, 0, false),
    new PossibleMove(-1, -1, false),
    new PossibleMove(-1, 1, false),
    new PossibleMove(1, -1, false),
    new PossibleMove(1, 1, false),
  };
}
