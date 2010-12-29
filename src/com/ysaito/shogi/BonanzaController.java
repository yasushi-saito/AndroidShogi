package com.ysaito.shogi;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

public class BonanzaController {
	static final String TAG = "BonanzaController"; 
	
	public class Result implements java.io.Serializable {
		public Board board;
		public int next_player;
	};
	
	public class Request implements java.io.Serializable {
		public int piece;
		public int from_x, from_y, to_x, to_y;
	};
	
	Handler mOutputHandler;

	final Handler mInputHandler;
		
	HandlerThread mThread;
	
	public BonanzaController(Handler handler) {
		mOutputHandler = handler;
		mThread = new HandlerThread("BonanzaController");
		mThread.start();
		
		mInputHandler = new Handler(mThread.getLooper()) {
			public void handleMessage(Message msg) {
				String command = msg.getData().getString("command");
				if (command == "init") {
					doInit();
				} else if (command == "humanMove") {
					doHumanMove((Request)(msg.getData().get("arg0")));
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

	public void humanMove(Request request) {
		sendInputMessage("humanMove", request);
	}
	
	public void computerMove() {
		sendInputMessage("computerMove", null);
	}
	
	public void stop() {
		sendInputMessage("stop", null);
	}
	
	void sendInputMessage(String command, Request request) {
		Message msg = mInputHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putString("command", command);
		b.putSerializable("request",  request);
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
		Result r = new Result();
		BonanzaJNI.Initialize(r.board);
		sendOutputMessage(r);
	}
	
	void doHumanMove(Request request) {
		Result r = new Result();
		BonanzaJNI.HumanMove(request.piece, request.from_x, request.from_y, request.to_x, request.to_y, r.board);
		sendOutputMessage(r);
	}
	
	void doComputerMove() {
		Result r = new Result();
		BonanzaJNI.ComputerMove(r.board);
		sendOutputMessage(r);
	}
	void doStop() {
	}

};
