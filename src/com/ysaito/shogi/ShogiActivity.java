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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.ysaito.shogi.BonanzaController;

public class ShogiActivity extends Activity {
  static final String TAG = "Shogi"; 

  static final int DIALOG_PROMOTE = 1235;
  static final int DIALOG_CONFIRM_QUIT = 1236;
 
  AlertDialog mPromoteDialog;
  BonanzaController mController;
  BoardView mBoardView;
  GameStatusView mStatusView;
  
  Board mBoard;
  Player mNextPlayer;
  GameState mGameState;
  
  // History of moves made by both players
  final ArrayList<Move> mMoves = new ArrayList<Move>();
  final ArrayList<Integer> mMoveCookies = new ArrayList<Integer>();
  
  // List of players played by humans. The list size is usually one, when one side is 
  // played by Human and the other side by the computer.
  ArrayList<Player> mHumanPlayers;

  static {  
    System.loadLibrary("bonanza-jni");  
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.game);
    
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
        mHumanPlayers.add(Player.BLACK);
      }
      if (player_white.equals("Human")) {
        mHumanPlayers.add(Player.WHITE);      
      }
    }
    mStatusView = (GameStatusView)findViewById(R.id.gamestatusview);
    mStatusView.initialize(
        PlayerName(player_black, computer_level),
        PlayerName(player_white, computer_level));
    
    mBoardView = (BoardView)findViewById(R.id.boardview);
    mBoardView.initialize(mViewListener, mHumanPlayers);
    
    mController = new BonanzaController(mControllerHandler, computer_level);
    // mController will call back via mControllerHandler when Bonanza is 
    // initialized. mControllerHandler will cause mBoardView to start accepting
    // user inputs.
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.game_menu, menu);
    return true;
  }
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.undo:
        undo();
        return true;
      default:    
        return super.onOptionsItemSelected(item);
    }
  }
  
  
  String PlayerName(String type, int level) {
    if (type.equals("Human")) return type;
    return "Com Lv" + level;
  }
  
  @Override
  public void onDestroy() {
    Log.d(TAG, "ShogiActivity destroyed");
    mController.destroy();
    super.onDestroy();
  }

  @Override
  public void onBackPressed() { 
    if (mGameState == GameState.ACTIVE) {
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
  // Undo
  //
  void undo() {
    if (!isHumanPlayer(mNextPlayer)) {
      Toast.makeText(getBaseContext(), "Computer is thinking", 
          Toast.LENGTH_SHORT).show();
    } else {
      Toast.makeText(getBaseContext(), "Undo",
          Toast.LENGTH_SHORT).show();
    }
    if (mMoveCookies.size() < 2) return;
    int lastMove = mMoveCookies.get(mMoveCookies.size() - 1);
    int penultimateMove = mMoveCookies.get(mMoveCookies.size() - 2);
    mController.undo2(mNextPlayer, lastMove, penultimateMove);
  }
  //
  // Handling results from the Bonanza controller thread
  //
  final Handler mControllerHandler = new Handler() {
    @Override public void handleMessage(Message msg) {
      BonanzaController.Result r = (BonanzaController.Result)(
          msg.getData().get("result"));
      mGameState = r.gameState;
      mBoard = r.board;
      mNextPlayer = r.nextPlayer;
      if (r.lastMove != null) {
        mMoves.add(r.lastMove);
        mMoveCookies.add(r.lastMoveCookie);
      } 
      for (int i = 0; i < r.undoMoves; ++i) {
        assert r.lastMove == null;
        mMoves.remove(mMoves.size() - 1);
        mMoveCookies.remove(mMoveCookies.size() - 1);
      }
      
      mBoardView.update(r.gameState, r.board, r.nextPlayer);
      mStatusView.update(r.gameState, mMoves, r.nextPlayer, r.errorMessage);
      
      if (isComputerPlayer(r.nextPlayer)) {
        mController.computerMove(r.nextPlayer);
      }
    }
  };
  
  //
  // Handling of move requests from BoardView
  //
  
  // state kept during the run of promotion dialog
  Player mSavedPlayerForPromotion;
  Move mSavedMoveForPromotion;    
  
  final BoardView.EventListener mViewListener = new BoardView.EventListener() {
    public void onHumanMove(Player player, Move move) {
      mNextPlayer = Player.INVALID;  
      if (MoveAllowsForPromotion(player, move)) {
        mSavedPlayerForPromotion = player;
        mSavedMoveForPromotion = move;
        showDialog(DIALOG_PROMOTE);
      } else {
        mController.humanMove(player, move);
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
            mNextPlayer = mSavedPlayerForPromotion;
            mBoardView.update(mGameState, mBoard, mNextPlayer);
          }
        });
    b.setItems(
        new CharSequence[] {"Promote", "Do not promote"},
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface d, int item) {
            if (item == 0) {
              mSavedMoveForPromotion.piece = Board.promote(mSavedMoveForPromotion.piece);
            }
            mController.humanMove(mSavedPlayerForPromotion, mSavedMoveForPromotion);
            mSavedMoveForPromotion = null;
          }
        });
    return b.create();
  }
  
  static final boolean MoveAllowsForPromotion(Player player, Move move) {
    if (Board.isPromoted(move.piece)) return false;  // already promoted
    
    final int type = Board.type(move.piece);
    if (type == Board.K_KIN || type == Board.K_OU) return false;
    
    if (move.fromX < 0) return false;  // dropping a captured piece
    if (player == Player.WHITE && move.toY < 6) return false;
    if (player == Player.BLACK && move.toY >= 3) return false;
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