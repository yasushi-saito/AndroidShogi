package com.ysaito.shogi;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

/**
 * 
 * An asynchronous interface for running Bonanza. 
 * 
 * Each public method in this class, except abort(), is asynchronous. 
 * It starts the request in a separate thread. The method itself returns
 * immediately. When the request completes, the result is communicated
 * via the Handler interface.
 */
public class BonanzaController {
  /**
   *  The result of each asynchronous request. Packed in the "result" part of 
   *  the Message.getData() bundle.
   */
  public static class Result implements java.io.Serializable {
    // The new state of the board
    public Board board;
    
    // The following three fields describe the last move made. 
    // lastMoveCookie is an opaque token summarizing lastMove. It is passed
    // as a parameter to undo() if the caller wants to undo this move.
    //
    // Possible combinations of values are:
    //
    // 1. all the values are null or 0. This happens when the last
    //    operation resulted in an error.
    //
    // 2. lastMove!=null && lastMoveCookie > 0 && undoMoves == 0.
    //    this happens after successful completion of humanMove or 
    //    computerMove.
    //
    // 3. lastMove==null && lastMoveCookie == 0 && undoMoves > 0
    //    this happens after successful completion of undo1 or undo2. 
    public Move lastMove;       // the move made by the request.
    public int lastMoveCookie;  // cookie for lastMove. for future undos.
    public int undoMoves;       // number of moves to be rolled back
    
    // The player that should play the next turn. May be Player.INVALID when the
    // the gameState != ACTIVE.
    public Player nextPlayer; 
    
    public GameState gameState;
    public String errorMessage;
    
    @Override
    public String toString() {
      String s = "state=" + gameState.toString() + 
            " next: " + nextPlayer.toString();
      if (errorMessage != null) s += " error: " + errorMessage;
      return s;
    }
    
    static public Result fromJNI(
        BonanzaJNI.Result jr,
        Player curPlayer) {
      Result r = new Result();
      r.board = jr.board;
      r.lastMove = (jr.move != null) ? Move.fromCsaString(jr.move) : null;
      r.lastMoveCookie = jr.moveCookie;
      r.errorMessage = jr.error;
      
      if (jr.status >= 0) {
        r.nextPlayer = Player.opponentOf(curPlayer);
        r.gameState = GameState.ACTIVE;
      } else {
        switch (jr.status) {
          case BonanzaJNI.R_ILLEGAL_MOVE:
            r.nextPlayer = curPlayer;
            r.gameState = GameState.ACTIVE;
            r.lastMove = null;
            r.lastMoveCookie = -1;
            break;
          case BonanzaJNI.R_CHECKMATE:
            r.nextPlayer = Player.INVALID;
            r.gameState = (curPlayer == Player.BLACK) ?
                GameState.WHITE_LOST : GameState.BLACK_LOST;
            r.errorMessage = "Checkmate";
            break;
          case BonanzaJNI.R_RESIGNED:
            r.nextPlayer = Player.INVALID;
            r.gameState = (curPlayer == Player.BLACK) ?
                GameState.BLACK_LOST : GameState.WHITE_LOST;
            r.errorMessage = "Resigned";
            break;
          case BonanzaJNI.R_DRAW:
            r.nextPlayer = Player.INVALID;
            r.gameState = GameState.DRAW;
            r.errorMessage = "Draw";
            break;
          default:
            throw new AssertionError("Illegal jni_status: " + jr.status);
        }
      }
      return r;
    }
    
    public final void setState(
        int jni_status, 
        BonanzaJNI.Result m,
        Player curPlayer) {
      lastMove = (m.move != null) ? Move.fromCsaString(m.move) : null;
      lastMoveCookie = m.moveCookie;
      
      if (jni_status >= 0) {
        if (curPlayer == Player.WHITE) {
          nextPlayer = Player.BLACK;
        } else if (curPlayer == Player.BLACK) {
          nextPlayer = Player.WHITE;
        } else {
          throw new AssertionError("Invalid player");
        }
        gameState = GameState.ACTIVE;
        errorMessage = null;
      } else {
        switch (jni_status) {
          case BonanzaJNI.R_ILLEGAL_MOVE:
            nextPlayer = curPlayer;
            gameState = GameState.ACTIVE;
            lastMove = null;
            lastMoveCookie = -1;
            errorMessage = "Illegal move";
            break;
          case BonanzaJNI.R_CHECKMATE:
            nextPlayer = Player.INVALID;
            gameState = (curPlayer == Player.BLACK) ?
                GameState.WHITE_LOST : GameState.BLACK_LOST;
            errorMessage = "Checkmate";
            break;
          case BonanzaJNI.R_RESIGNED:
            nextPlayer = Player.INVALID;
            gameState = (curPlayer == Player.BLACK) ?
                GameState.BLACK_LOST : GameState.WHITE_LOST;
            errorMessage = "Resigned";
            break;
          case BonanzaJNI.R_DRAW:
            nextPlayer = Player.INVALID;
            gameState = GameState.DRAW;
            errorMessage = "Draw";
            break;
          default:
            throw new AssertionError("Illegal jni_status: " + jni_status);
        }
      }
    }
  }

  private static final String TAG = "BonanzaController"; 
  private int mComputerDifficulty;
  private Handler mOutputHandler;  // for reporting status to the caller
  private Handler mInputHandler;   // for sending commands to the controller thread 
  private HandlerThread mThread;

  private int mInstanceId;
  
  private static final int C_INIT = 0;
  private static final int C_HUMAN_MOVE = 1;
  private static final int C_COMPUTER_MOVE = 2;
  private static final int C_UNDO = 3;
  private static final int C_DESTROY = 4;
  
  public BonanzaController(Handler handler, int difficulty) {
    mOutputHandler = handler;
    mComputerDifficulty = difficulty;
    mInstanceId = -1;
    mThread = new HandlerThread("BonanzaController");
    mThread.start();
    mInputHandler = new Handler(mThread.getLooper()) {
      @Override
      public void handleMessage(Message msg) {
        int command = msg.getData().getInt("command");
        switch (command) {
          case C_INIT:
            doInit();
            break;
          case C_HUMAN_MOVE:
            doHumanMove(
                (Player)msg.getData().get("player"),
                (Move)msg.getData().get("move"));
            break;  
          case C_COMPUTER_MOVE:
            doComputerMove((Player)msg.getData().get("player"));
            break;
          case C_UNDO:
            doUndo(
                (Player)msg.getData().get("player"),
                msg.getData().getInt("cookie1"),
                msg.getData().getInt("cookie2"));
            break;
          case C_DESTROY:
            doDestroy();
            break; 
          default:
            throw new AssertionError("Invalid command: " + command);
        }
      }
    };
    sendInputMessage(C_INIT, null, null, -1, -1);
  }
  
  /** 
   * Stop the background thread that controls Bonanza. Must be called once before
   * abandonding this object.
   */
  public void destroy() {
    sendInputMessage(C_DESTROY, null, null, -1, -1);
  }

  /** 
   * Tell Bonanza that the human player has made @p move. Bonanza will
   * asynchronously ack through the mOutputHandler.
   *
   * @param player is the player that has made the @p move. It is used only to
   *  report back Result.nextPlayer.
   */   
  public void humanMove(Player player, Move move) {
    sendInputMessage(C_HUMAN_MOVE, player, move, -1, -1);
  }
  
  /** 
   * Ask Bonanza to make a move. Bonanza will asynchronously report its move
   * through the mOutputHandler.
   * 
   * @param player the identity of the computer player. It is used only to 
   * report back Result.nextPlayer.
   */
  public void computerMove(Player player) {
    sendInputMessage(C_COMPUTER_MOVE, player, null, -1, -1);
  }

  /**
   * Undo the last move. 
   * @param player the player who made the last move.
   * @param cookie The last move made in the game.
   */
  public void undo1(Player player, int cookie) {
    sendInputMessage(C_UNDO, player, null, cookie, -1);
  }
  
  /**
   * Undo the last two moves.
   * @param player the player who made the move cookie2.
   * @param cookie1 the last move made in the game.
   * @param cookie2 the penultimate move made in the game.
   */
  public void undo2(Player player, int cookie1, int cookie2) {
    sendInputMessage(C_UNDO, player, null, cookie1, cookie2);
  }
  
  //
  // Implementation details
  //
  private void sendInputMessage(
      int command, 
      Player curPlayer, 
      Move move,
      int cookie1, int cookie2) {
    Message msg = mInputHandler.obtainMessage();
    Bundle b = new Bundle();
    b.putInt("command", command);
    if (move != null) b.putSerializable("move",  move);
    if (curPlayer != null) b.putSerializable("player", curPlayer);
    if (cookie1 >= 0) b.putInt("cookie1", cookie1);
    if (cookie2 >= 0) b.putInt("cookie2", cookie2);    
    msg.setData(b);
    mInputHandler.sendMessage(msg);
  }

  private void sendOutputMessage(Result result) {
    Message msg = mOutputHandler.obtainMessage();
    Bundle b = new Bundle();
    b.putSerializable("result", result);
    msg.setData(b);
    mOutputHandler.sendMessage(msg);
  }

  private void doInit() {
    BonanzaJNI.Result jr = new BonanzaJNI.Result();
    mInstanceId = BonanzaJNI.startGame(mComputerDifficulty, 60, 1, jr);
    
    Result r = new Result();
    r.board = jr.board;
    r.nextPlayer = Player.BLACK;
    r.gameState = GameState.ACTIVE;
    sendOutputMessage(r);
  }

  private void doHumanMove(Player player, Move move) {
    BonanzaJNI.Result jr = new BonanzaJNI.Result();
    BonanzaJNI.humanMove(mInstanceId, move.toCsaString(), jr);
    sendOutputMessage(Result.fromJNI(jr, player));
  }

  private void doComputerMove(Player player) {
    BonanzaJNI.Result jr = new BonanzaJNI.Result();
    BonanzaJNI.computerMove(mInstanceId, jr);
    sendOutputMessage(Result.fromJNI(jr, player));
  }

  private void doUndo(Player player, int cookie1, int cookie2) {
    BonanzaJNI.Result jr = new BonanzaJNI.Result();
    Log.d(TAG, "Undo " + cookie1 + " " + cookie2);
    int iret = BonanzaJNI.undo(mInstanceId, cookie1, cookie2, jr);

    Result r;
    if (cookie2 < 0) {
      r = Result.fromJNI(jr, player);
      r.undoMoves = 1;
    } else {
      r = Result.fromJNI(jr, Player.opponentOf(player));
      r.undoMoves = 2;
    }
    sendOutputMessage(r);
  }   
  
  private void doDestroy() {
    Log.d(TAG, "Destroy");
    mThread.quit();
  }
}
