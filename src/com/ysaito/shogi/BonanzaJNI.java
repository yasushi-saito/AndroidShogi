package com.ysaito.shogi;
import com.ysaito.shogi.Board;

// JNI interface for Bonanza
public class BonanzaJNI {
  //
  // Return values of HumanMove and ComputerMove
  //
  public static final int R_OK = 0;
  // Human made an illegal move.
  public static final int R_ILLEGAL_MOVE = -1;
  // Checkmate. Computer won
  public static final int R_CHECKMATE = -2;
  // Computer lost
  public static final int R_RESIGNED = -3;
  // Sennichite
  public static final int R_DRAW = -4;
  // Another instance of the game started
  public static final int R_INSTANCE_DELETED = -5;
  
  static public final class MoveResult {
    // The description of the move in CSA format.
    String move;
    
    // A cookie used to undo the move in the future. In practice, the value
    // in the result of calling interpret_CSA_move(csaMove).
    int cookie;
  }
  
  /** 
   * Initialize the C module. 
   * 
   * @return An instance ID
   * @param difficulty 1==weak, 5==strong
   * @param board (output)  filled with the initial board configuration.
   */
  static public native int initialize(
      int difficulty,
      int total_think_time_secs,    
      int per_turn_think_time_secs,
      Board board);
  
  /**
   *  Inform Bonanza that the human player made a move.
   *   
   * @param move CSA-format string, such as "7776FU".
   * @param board (output)
   * @param moveResult (output)
   * @return One of R_* constants
   */
  static public native int humanMove(
      int instanceId,
      String move,
      MoveResult moveResult,
      Board board);

  /**
   * Have the computer compute the next move. 

   * @param moveResult (output) store the move made by the computer
   * @param board (output) the state of the board after the move. 
   * @return One of R_* constants
   */
  static public native int computerMove(
      int instanceId,
      MoveResult moveResult,
      Board board);
  
  /**
   * Undo up to two past moves. 
   * 
   * REQUIRES: cookie1 and cookie2 are values of MoveResult.cookie of 
   * past moves. cookie1 must be the last move made in the game (either computer or 
   * human). If cookie2 > 0, it must be the penultimate move in the game.
   *
   * @param board (output)
   * @return Either R_OK or R_INSTANCE_DESTROYED
   */
  static public native int undo(
      int instanceId,
      int cookie1, int cookie2,
      Board board);
  
  // Abort the current game. This method can be called from any thread.
  // If another thread is running HumanMove or ComputerMove, it will see
  // an error. The state of the game will be undefined after this call.
  static public native void abort();
}

