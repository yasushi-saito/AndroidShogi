package com.ysaito.shogi;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.TextView;
import com.ysaito.shogi.BonanzaController;

public class ShogiActivity extends Activity {
  static final String TAG = "Shogi"; 
  String mMenu;

  static final int DIALOG_PROMOTE = 1235;
  static final int DIALOG_CONFIRM_QUIT = 1236;
 
  AlertDialog mPromoteDialog;
  BonanzaController mController;
  BoardView mBoardView;
  GameStatusView mStatusView;
  
  // List of players played by humans. The list size is usually one, when one side is 
  // played by Human and the other side by the computer.
  ArrayList<Player> mHumanPlayers;

  static {  
    System.loadLibrary("bonanza-jni");  
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
        getBaseContext());
    String player_black = prefs.getString("player_black", "Human");
    String player_white = prefs.getString("player_white", "Computer");
    int computer_level = Integer.parseInt(
        prefs.getString("computer_difficulty", "1"));
    Log.d(TAG, "onCreate black=" + player_black + " white=" + player_white);

    mHumanPlayers = new ArrayList<Player>();
    // mHumanPlayers.add(Player.BLACK);
    if (true) {
      if (player_black.equals("Human")) {
        Log.d(TAG, "Add BLACK human player");
        mHumanPlayers.add(Player.BLACK);
      }
      if (player_white.equals("Human")) {
        Log.d(TAG, "Add WHITE human player");        
        mHumanPlayers.add(Player.WHITE);      
      }
    }
    mStatusView = (GameStatusView)findViewById(R.id.gamestatusview);
    mStatusView.initialize("FOO", "BAR");
    
    mBoardView = (BoardView)findViewById(R.id.boardview);
    mBoardView.initialize(mViewListener, mStatusView, mHumanPlayers);
    
    mController = new BonanzaController(mControllerHandler, computer_level);
    // mController will call back via mControllerHandler when Bonanza is 
    // initialized. mControllerHandler will cause mBoardView to start accepting
    // user inputs.
  }
  
  @Override
  public void onDestroy() {
    Log.d(TAG, "ShogiActivity destroyed");
    mController.destroy();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() { 
    Log.d(TAG, "Back button");
    if (mBoardView.gameState() == GameState.ACTIVE) {
      showDialog(DIALOG_CONFIRM_QUIT);
    } else {
      super.onBackPressed();
    }
  }
  
  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
      case DIALOG_PROMOTE: 
        mPromoteDialog = createPromoteDialog();
        return mPromoteDialog;
      case DIALOG_CONFIRM_QUIT:
        return createConfirmQuitDialog();
      default:    
        return null;
    }
  }

  boolean isComputerPlayer(Player p) { 
    return p != Player.INVALID && !isHumanPlayer(p);
  }
  
  boolean isHumanPlayer(Player p) {
    return mHumanPlayers.contains(p);
  }
  
  //
  // Handling results from the Bonanza controller thread
  //
  final Handler mControllerHandler = new Handler() {
    @Override public void handleMessage(Message msg) {
      Log.d(TAG, "Got controller callback");
      BonanzaController.Result r = (BonanzaController.Result)(
          msg.getData().get("result"));
      Log.d(TAG, "Controller msg: " + r.toString());
      mBoardView.setState(r.gameState, r.board, r.nextPlayer, 
          r.errorMessage);
      if (isComputerPlayer(r.nextPlayer)) {
        mController.computerMove(r.nextPlayer);
      }
    }
  };
  
  //
  // Handling of move requests from BoardView
  //
  Move mLastMove;  // state kept during the run of promotion dialog
  final BoardView.EventListener mViewListener = new BoardView.EventListener() {
    public void onHumanMove(Move move) {
      if (MoveAllowsForPromotion(move)) {
        mLastMove = move;
        showDialog(DIALOG_PROMOTE);
      } else {
        mController.humanMove(move.player, move);
      }
    }
  };
  
  AlertDialog createPromoteDialog() {
    AlertDialog.Builder b = new AlertDialog.Builder(this);
    b.setTitle("Promote piece?");
    b.setCancelable(true);
    b.setOnCancelListener(
        new DialogInterface.OnCancelListener() {
          public void onCancel(DialogInterface unused) {
          }
        });
    b.setItems(
        new CharSequence[] {"Promote", "Do not promote"},
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface d, int item) {
            mLastMove.promote = false;
            if (item == 0) mLastMove.promote = true;
            mController.humanMove(mLastMove.player, mLastMove);
            mLastMove = null;
          }
        });
    return b.create();
  }
  
  static final boolean MoveAllowsForPromotion(Move move) {
    if (Board.isPromoted(move.piece)) return false;  // already promoted
    if (move.from_x < 0) return false;  // dropping a captured piece
    if (move.player == Player.WHITE && move.to_y < 6) return false;
    if (move.player == Player.BLACK && move.to_y >= 3) return false;
    return true;
  }

  // 
  // Confirm quitting the game ("BACK" button interceptor)
  //
  AlertDialog createConfirmQuitDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage("Do you really want to quit the game?");
    builder.setCancelable(false);
    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface d, int id) {
        finish();
      }
    });
    builder.setNegativeButton("No",  new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface d, int id) {
        // nothing to do
      }
    });
    return builder.create();
  }
  
}