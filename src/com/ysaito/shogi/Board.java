package com.ysaito.shogi;

import java.util.Arrays;

public class Board implements java.io.Serializable {
	// Width and height of a board
	public static final int DIM = 9; 
	
	// Total # of squares in a board
	public static final int NUM_SQUARES = DIM * DIM;
	
	// Encoding of mSquares[]. A piecebelonging to sente (gote) is positive (negative), respectively.
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
	
	public static final int P_SENTE = 1;
	public static final int P_INVALID = 0;
	public static final int P_GOTE= -1;
	
	// Given a piece in mSquares[], see if it's owned by gote. 
	public static final int player(int pos) { 
		if (pos > 0) return P_SENTE;
		if (pos == 0) return P_INVALID;
		return P_GOTE;
	}
	
	// Given a piece in mSquares, return its type.
	public static final int type(int pos) { return (pos < 0 ? -pos : pos); }
	
	public int mSquares[];      // Contents of the board
	public int mCapturedSente;  // Pieces captured by sente
	public int mCapturedGote;   // Pieces captured by gote
	
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
		mCapturedSente = src.mCapturedSente;
		mCapturedGote = src.mCapturedGote;
	}
	
	public final void setPiece(int x, int y, int piece) {
		mSquares[x + y * DIM] = piece;
	}
	
	public final int getPiece(int x, int y) {
		return mSquares[x + y * DIM];
	}
}
