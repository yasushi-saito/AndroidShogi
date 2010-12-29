package com.ysaito.shogi;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

public class BonanzaController {
  static final String TAG = "BonanzaController"; 

  public class Result implements java.io.Serializable {
    public final Board board = new Board();
    public int next_player;
  };

  Handler mOutputHandler;

  final Handler mInputHandler;

  HandlerThread mThread;

  public BonanzaController(Handler handler) {
    mOutputHandler = handler;
    mThread = new HandlerThread("BonanzaController");
    mThread.start();
    mInputHandler = new Handler(mThread.getLooper()) {
      @Override
      public void handleMessage(Message msg) {
        Log.d(TAG, "Got message");
        String command = msg.getData().getString("command");
        if (command == "init") {
          doInit();
        } else if (command == "humanMove") {
          doHumanMove((Board.Move)(msg.getData().get("move")));
        } else if (command == "computerMove") {
          doComputerMove();
        } else if (command == "stop") {
          doStop();
        } else {
          Log.e(TAG, "Invalid command: " + command);
        }
      }
    };
    sendInputMessage("init", null);
  }

  public void humanMove(Board.Move move) {
    sendInputMessage("humanMove", move);
  }

  public void computerMove() {
    sendInputMessage("computerMove", null);
  }

  public void stop() {
    sendInputMessage("stop", null);
  }

  void sendInputMessage(String command, Board.Move move) {
    Message msg = mInputHandler.obtainMessage();
    Bundle b = new Bundle();
    b.putString("command", command);
    b.putSerializable("move",  move);
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


  void doInit() {
    Log.d(TAG, "Init");
    Result r = new Result();
    BonanzaJNI.Initialize(r.board);
    sendOutputMessage(r);
  }

  void doHumanMove(Board.Move move) {
    Log.d(TAG, "Human");
    Result r = new Result();
    BonanzaJNI.HumanMove(move.piece, move.from_x, move.from_y,
          move.to_x, move.to_y, r.board);
    sendOutputMessage(r);
  }

  void doComputerMove() {
    Log.d(TAG, "Computer");
    Result r = new Result();
    BonanzaJNI.ComputerMove(r.board);
    sendOutputMessage(r);
  }
  
  void doStop() {
    Log.d(TAG, "Stop");
    mThread.quit();
  }

};
