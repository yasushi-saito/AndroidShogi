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
    public Board.Player nextPlayer; 
    public Board.GameState gameState;
    public String errorMessage;

    @Override
    public String toString() {
      String s = "state=" + gameState.toString() + 
            " next: " + nextPlayer.toString();
      if (errorMessage != null) s += " error: " + errorMessage;
      return s;
    }
    void setStatus(int jni_status, Board.Player curPlayer) {
      if (jni_status >= 0) {
        if (curPlayer == Board.Player.WHITE) {
          nextPlayer = Board.Player.BLACK;
        } else if (curPlayer == Board.Player.BLACK) {
          nextPlayer = Board.Player.WHITE;
        } else {
          throw new AssertionError("Invalid player");
        }
        gameState = Board.GameState.ACTIVE;
        errorMessage = null;
      } else {
        switch (jni_status) {
          case BonanzaJNI.ILLEGAL_MOVE:
            nextPlayer = curPlayer;
            gameState = Board.GameState.ACTIVE;
            errorMessage = "Illegal move";
            break;
          case BonanzaJNI.CHECKMATE:
            nextPlayer = Board.Player.INVALID;
            gameState = (curPlayer == Board.Player.BLACK) ?
                Board.GameState.WHITE_LOST : Board.GameState.BLACK_LOST;
            errorMessage = "Checkmate";
            break;
          case BonanzaJNI.RESIGNED:
            nextPlayer = Board.Player.INVALID;          
            gameState = (curPlayer == Board.Player.BLACK) ?
                Board.GameState.BLACK_LOST : Board.GameState.WHITE_LOST;
            errorMessage = "Resigned";
            break;
          case BonanzaJNI.DRAW:
            nextPlayer = Board.Player.INVALID;          
            gameState = Board.GameState.DRAW;
            errorMessage = "Draw";
            break;
          default:
            throw new AssertionError("Illegal jni_status: " + jni_status);
        }
      }
    }
  }

  Handler mOutputHandler;

  final Handler mInputHandler;

  HandlerThread mThread;

  public BonanzaController(Handler handler, Board.Player firstTurn) {
    mOutputHandler = handler;
    mThread = new HandlerThread("BonanzaController");
    mThread.start();
    mInputHandler = new Handler(mThread.getLooper()) {
      @Override
      public void handleMessage(Message msg) {
        String command = msg.getData().getString("command");
        if (command == "init") {
          doInit((Board.Player)msg.getData().get("player"));
        } else if (command == "humanMove") {
          doHumanMove(
              (Board.Move)msg.getData().get("move"),
              (Board.Player)msg.getData().get("player"));
        } else if (command == "computerMove") {
          doComputerMove((Board.Player)msg.getData().get("player"));
        } else if (command == "stop") {
          doStop();
        } else {
          Log.e(TAG, "Invalid command: " + command);
        }
      }
    };
    sendInputMessage("init", firstTurn, null);
  }

  // @p player is the player that has made the @p move. It is used only to
  // report back Result.nextPlayer.
  //
  // @invariant player == move.player
  public void humanMove(Board.Player player, Board.Move move) {
    sendInputMessage("humanMove", player, move);
  }

  // @p player is the identity of the computer player. It is used only to 
  // report back Result.nextPlayer.
  public void computerMove(Board.Player player) {
    sendInputMessage("computerMove", player, null);
  }

  public void stop() {
    sendInputMessage("stop", null, null);
  }

  void sendInputMessage(String command, Board.Player curPlayer, Board.Move move) {
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

  static Board.Player nextPlayer(Board.Player curPlayer) {
    if (curPlayer == Board.Player.BLACK) return Board.Player.WHITE;
    if (curPlayer == Board.Player.WHITE) return Board.Player.BLACK;
    throw new AssertionError("Invalid player");
  }

  void doInit(Board.Player firstTurn) {
    Log.d(TAG, "Init");
    Result r = new Result();
    BonanzaJNI.Initialize(1, 60, 1, r.board);
    r.nextPlayer = firstTurn;
    r.gameState = Board.GameState.ACTIVE;
    sendOutputMessage(r);
  }

  void doHumanMove(Board.Move move, Board.Player player) {
    Log.d(TAG, "Human");
    Result r = new Result();
    int iret = BonanzaJNI.HumanMove(move.piece, move.from_x, move.from_y,
          move.to_x, move.to_y, move.promote, r.board);
    r.setStatus(iret, player);
    sendOutputMessage(r);
  }

  void doComputerMove(Board.Player player) {
    Log.d(TAG, "Computer");
    Result r = new Result();
    int iret = BonanzaJNI.ComputerMove(r.board);
    r.setStatus(iret, player);
    sendOutputMessage(r);
  }
  
  void doStop() {
    Log.d(TAG, "Stop");
    mThread.quit();
  }

}
