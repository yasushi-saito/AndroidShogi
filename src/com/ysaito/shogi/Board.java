package com.ysaito.shogi;

import java.util.Arrays;

public class Board implements java.io.Serializable {
  // X and Y dimensions of a board
  public static final int DIM = 9; 

  // Total # of squares in a board
  public static final int NUM_SQUARES = DIM * DIM;

  // Encoding of mSquares[]. A piecebelonging to P_UP (resp. P_DOWN) is 
  // positive (resp. negative).
  // The absolute value defines the type of the piece. It is one of the following.
  public static final int K_EMPTY = 0;  // placeholder for an unoccupied square
  public static final int K_FU = 1;
  public static final int K_KYO = 2;
  public static final int K_KEI = 3;
  public static final int K_GIN = 4;
  public static final int K_KIN = 5;
  public static final int K_KAKU = 6;
  public static final int K_HI = 7;
  public static final int K_OU = 8;
  public static final int K_TO = 9;
  public static final int K_NARI_KYO = 10;
  public static final int K_NARI_KEI = 11;
  public static final int K_NARI_GIN = 12;
  public static final int K_UMA = 14;
  public static final int K_RYU = 15;
  public static final int NUM_TYPES = 16;

  // Type of the players. "BLACK" is "sente" in japanese. It starts at the 
  // bottom and moves up. "WHITE" is "gote" in japanese.
  enum Player {
    BLACK, INVALID, WHITE
  }

  public static enum GameState {
    ACTIVE,     // game ongoing
    BLACK_LOST,
    WHITE_LOST,
    DRAW,       // sennichite
  }

  // Struct that represents a move by a human player
  public static class Move implements java.io.Serializable {
    public Player player;  // UP or DOWN

    // The piece to move. The value is negative if player==Player.DOWN.
    public int piece;

    // The source and destination coordinates. Each value is in range [0,DIM).
    public int from_x, from_y, to_x, to_y;

    // If promote==true, promote the piece. 
    //
    // Note: this field is not set by BoardView.
    // The EventListener impl in Shogi class runs a dialog if the move 
    // allows for promotion, the Shogi class will run a dialog 
    // and sets @v promote=true if the user indicates the wish.
    //
    // @invariant !isPromoted(piece) && <to_x,to_y> is in 
    // the opponent's territory.
    public boolean promote;

    @Override public String toString() {
      return ("Piece: " + piece + " [" + from_x + "," + from_y + "]->[" +
          to_x + "," + to_y + "](" + promote + ")"); 
    }
  }

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

  // Public members
  public int mSquares[];      // Contents of the board
  public int mCapturedBlack;     // Pieces captured by Player.BLACK
  public int mCapturedWhite;   // Pieces captured by Player.WHITE

  // Given a piece in mSquares[], return the player type.
  public static final Player player(int piece) { 
    if (piece > 0) return Player.BLACK;
    if (piece == 0) return Player.INVALID;
    return Player.WHITE;
  }

  // Given a piece in mSquares[], return its type, i.e., K_XXX.
  public static final int type(int piece) { 
    return (piece< 0 ? -piece: piece); 
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
    mSquares = new int[NUM_SQUARES];  // initialized to zero
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
}
