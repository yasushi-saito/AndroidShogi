package com.ysaito.shogi;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

public class BonanzaController {
  static final String TAG = "BonanzaController"; 

  public static class Result implements java.io.Serializable {
    public final Board board = new Board();
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
    
    final void setStatus(int jni_status, Player curPlayer) {
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
          case BonanzaJNI.ILLEGAL_MOVE:
            nextPlayer = curPlayer;
            gameState = GameState.ACTIVE;
            errorMessage = "Illegal move";
            break;
          case BonanzaJNI.CHECKMATE:
            nextPlayer = Player.INVALID;
            gameState = (curPlayer == Player.BLACK) ?
                GameState.WHITE_LOST : GameState.BLACK_LOST;
            errorMessage = "Checkmate";
            break;
          case BonanzaJNI.RESIGNED:
            nextPlayer = Player.INVALID;          
            gameState = (curPlayer == Player.BLACK) ?
                GameState.BLACK_LOST : GameState.WHITE_LOST;
            errorMessage = "Resigned";
            break;
          case BonanzaJNI.DRAW:
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

  // Config params
  int mComputerDifficulty;
  
  Handler mOutputHandler;  // for reporting status to the caller
  Handler mInputHandler;   // for sending command to the controller thread 
  HandlerThread mThread;

  int mInstanceId;
  
  public BonanzaController(Handler handler, int difficulty) {
    mOutputHandler = handler;
    mComputerDifficulty = difficulty;
    mInstanceId = -1;
    mThread = new HandlerThread("BonanzaController");
    mThread.start();
    mInputHandler = new Handler(mThread.getLooper()) {
      @Override
      public void handleMessage(Message msg) {
        String command = msg.getData().getString("command");
        if (command == "init") {
          doInit();
        } else if (command == "humanMove") {
          doHumanMove(
              (Move)msg.getData().get("move"),
              (Player)msg.getData().get("player"));
        } else if (command == "computerMove") {
          doComputerMove((Player)msg.getData().get("player"));
        } else if (command == "destroy") {
          doDestroy();
        } else {
          throw new AssertionError("Invalid command: " + command);
        }
      }
    };
    sendInputMessage("init", null, null);
  }
  
  // Stop the background thread that controls Bonanza. Must be called once before
  // abandonding this object.
  void destroy() {
    sendInputMessage("destroy", null, null);
  }

  // Tell Bonanza that the human player has made @p move. Bonanza will
  // asynchronously ack through the mOutputHandler.
  //
  // @p player is the player that has made the @p move. It is used only to
  // report back Result.nextPlayer.
  //
  // @invariant player == move.player
  public void humanMove(Player player, Move move) {
    sendInputMessage("humanMove", player, move);
  }
  
  // Ask Bonanza to make a move. Bonanza will asynchronously report its move
  // through the mOutputHandler.
  //
  // @p player is the identity of the computer player. It is used only to 
  // report back Result.nextPlayer.
  public void computerMove(Player player) {
    sendInputMessage("computerMove", player, null);
  }

  //
  // Implementation details
  //
  void sendInputMessage(String command, Player curPlayer, Move move) {
    Message msg = mInputHandler.obtainMessage();
    Bundle b = new Bundle();
    b.putString("command", command);
    b.putSerializable("move",  move);
    b.putSerializable("player", curPlayer);
    msg.setData(b);
    mInputHandler.sendMessage(msg);
  }

  void sendOutputMessage(Result result) {
    Message msg = mOutputHandler.obtainMessage();
    Bundle b = new Bundle();
    b.putSerializable("result", result);
    msg.setData(b);
    mOutputHandler.sendMessage(msg);
  }

  static Player nextPlayer(Player curPlayer) {
    if (curPlayer == Player.BLACK) return Player.WHITE;
    if (curPlayer == Player.WHITE) return Player.BLACK;
    throw new AssertionError("Invalid player");
  }

  void doInit() {
    Log.d(TAG, "Init");
    Result r = new Result();
    mInstanceId =BonanzaJNI.Initialize(mComputerDifficulty, 60, 1, r.board);
    r.nextPlayer = Player.BLACK;
    r.gameState = GameState.ACTIVE;
    sendOutputMessage(r);
  }

  void doHumanMove(Move move, Player player) {
    Log.d(TAG, "Human");
    Result r = new Result();
    int iret = BonanzaJNI.HumanMove(
        mInstanceId,
        move.piece, move.from_x, move.from_y,
        move.to_x, move.to_y, move.promote, r.board);
    r.setStatus(iret, player);
    sendOutputMessage(r);
  }

  void doComputerMove(Player player) {
    Log.d(TAG, "Computer");
    Result r = new Result();
    int iret = BonanzaJNI.ComputerMove(mInstanceId, r.board);
    r.setStatus(iret, player);
    sendOutputMessage(r);
  }
  
  void doDestroy() {
    Log.d(TAG, "Destroy");
    mThread.quit();
  }

}
