package com.ysaito.shogi;
import com.ysaito.shogi.Board;

// JNI interface for Bonanza
public class BonanzaJNI {
  //
  // Return values of HumanMove and ComputerMove
  //
  public static final int OK = 0;
  // Human made an illegal move.
  public static final int ILLEGAL_MOVE = -1;
  // Checkmate. Computer won
  public static final int CHECKMATE = -2;
  // Computer lost
  public static final int RESIGNED = -3;
  // Sennichite
  public static final int DRAW = -4;
  // Another instance of the game started
  public static final int INSTANCE_DELETED = -5;
  
  // Initialize the C module. On successful return, 
  // @p board will be filled with the initial board configuration.
  //
  // @p difficulty 1==weak, 5==strong 
  static public native int Initialize(
      int difficulty,
      int total_think_time_secs,    
      int per_turn_think_time_secs,
      Board board);
  
  // Inform that the human player made a move. @p move is a csa-format string,
  // such as "7776FU".
  // On successful return, @p board stores the state of the board after the
  // move.
  static public native int HumanMove(
      int instanceId,
      String move,
      Board board);
  
  static public final class ComputerMoveResult {
    // JNI code can't modify string in place. So instead of passing a "String move"
    // directly to ComputerMove, pass this object and let it set the "move" field
    // anew.
    String move;
  }
  
  // Let the computer ponder the next move. On successful return, 
  // @p board stores the play by the computer, and @p move stores
  // the move made.
  static public native int ComputerMove(
      int instanceId,
      Board board,
      ComputerMoveResult move);
  
  // Abort the current game. This method can be called from any thread.
  // If another thread is running HumanMove or ComputerMove, it will see
  // an error. The state of the game will be undefined after this call.
  static public native void Abort();
}

