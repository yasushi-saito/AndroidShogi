package com.ysaito.shogi;
import com.ysaito.shogi.Board;

// JNI interface for Bonanza
public class BonanzaJNI {
	// Initialize the C module. On successful return, @p board will be filled with the initial board configuration.
	static public native void Initialize(Board board);
	
	// Inform that the human player moved @p piece from (from_x,from_y) to (to_x,to_y), and let the computer
	// ponder the next move. On successful return, @p board will store the play by the computer. 
	static public native void HumanMove(int piece, int from_x, int from_y, int to_x, int to_y, Board board);
	
	// Let the computer ponder the next move. On successful return, @p board will store the play by the computer.
	static public native void ComputerMove(Board board);
};

