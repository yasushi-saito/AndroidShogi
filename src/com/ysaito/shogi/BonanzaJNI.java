package com.ysaito.shogi;
import com.ysaito.shogi.Board;

// JNI interface for Bonanza
public class BonanzaJNI {
  // Return values of HumanMove and ComputerMove
  public static final int OK = 0;
  public static final int ILLEGAL_MOVE = -1;
  public static final int CHECKMATE = -2;
  
  // Initialize the C module. On successful return, @p board will be filled with the initial board configuration.
  static public native void Initialize(Board board);
  
  // Inform that the human player moved @p piece from (from_x,from_y) to (to_x,to_y).
  // On successful return, @p board will store the state of the board after the
  // move.
  static public native int HumanMove(int piece, int from_x, int from_y, 
      int to_x, int to_y, boolean promote,
      Board board);
  
  // Let the computer ponder the next move. On successful return, 
  // @p board will store the play by the computer.
  static public native int ComputerMove(Board board);
};

