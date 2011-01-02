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

/**
 * The main activity that controls game play
 */
public class GameActivity extends Activity {
  private static final String TAG = "Shogi"; 

  private static final int DIALOG_PROMOTE = 1235;
  private static final int DIALOG_CONFIRM_QUIT = 1236;
 
  // Config parameters
  //
  // List of players played by humans. The list size is usually one, when one side is 
  // played by Human and the other side by the computer.
  private ArrayList<Player> mHumanPlayers;
  
  // Number of undos remaining.
  //
  // TODO(saito) This works only when there's only one human player in the game.
  // Make this field per-player attribute.
  private int mUndosRemaining;
  
  // View components
  private AlertDialog mPromoteDialog;
  private BonanzaController mController;
  private BoardView mBoardView;
  private GameStatusView mStatusView;
  private Menu mMenu;
  
  // State of the game
  private Board mBoard;            // current state of the board
  private Player mCurrentPlayer;   // the next player to make a move 
  private GameState mGameState;    // is the game is active or finished?
  private long mBlackThinkTimeMs;  // Cumulative # of think time (millisec)
  private long mBlackThinkStartMs; // -1, or ms since epoch
  private long mWhiteThinkTimeMs;  // Cumulative # of think time (millisec)
  private long mWhiteThinkStartMs; // -1, or ms since epoch
  private boolean mDestroyed;      // onDestroy called?
  
  // History of moves made in the game. Even (resp. odd) entries are 
  // moves by the black (resp. white) player.
  private final ArrayList<Move> mMoves = new ArrayList<Move>();
  private final ArrayList<Integer> mMoveCookies = new ArrayList<Integer>();
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.game);
    mBlackThinkTimeMs = 0;
    mWhiteThinkTimeMs = 0;
    mBlackThinkStartMs = 0;  
    mWhiteThinkStartMs = 0;
    
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
        playerName(player_black, computer_level),
        playerName(player_white, computer_level));
    
    mBoardView = (BoardView)findViewById(R.id.boardview);
    mBoardView.initialize(mViewListener, mHumanPlayers);
    
    mUndosRemaining= Integer.parseInt(prefs.getString("max_undos", "0"));
    mController = new BonanzaController(mEventHandler, computer_level);
    schedulePeriodicTimer();
    // mController will call back via mControllerHandler when Bonanza is 
    // initialized. mControllerHandler will cause mBoardView to start accepting
    // user inputs.
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.game_menu, menu);
    mMenu = menu;
    updateUndoMenu();
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
  
  
  private String playerName(String type, int level) {
    if (type.equals("Human")) return getResources().getString(R.string.human);
    return getResources().getStringArray(R.array.computer_level_names)[level];
  }
  
  @Override
  public void onDestroy() {
    Log.d(TAG, "ShogiActivity destroyed");
    mController.destroy();
    super.onDestroy();
    mDestroyed = true;
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

  private boolean isComputerPlayer(Player p) { 
    return p != Player.INVALID && !isHumanPlayer(p);
  }
  
  private boolean isHumanPlayer(Player p) {
    return mHumanPlayers.contains(p);
  }
  
  // 
  // Periodic status update
  //
  private Runnable mTimerHandler = new Runnable() {
    @Override public void run() { 
      long now = System.currentTimeMillis();
      long totalBlack = mBlackThinkTimeMs;
      long totalWhite = mWhiteThinkTimeMs;
      if (mCurrentPlayer == Player.BLACK) {
        totalBlack += (now - mBlackThinkStartMs); 
      } else if (mCurrentPlayer == Player.WHITE) {
        totalWhite += (now - mWhiteThinkStartMs);
      }
      mStatusView.updateThinkTimes(totalBlack, totalWhite);
      if (!mDestroyed) schedulePeriodicTimer();
    }
  };
  private void setCurrentPlayer(Player p) {
    // Register the time spent during the last move.
    final long now = System.currentTimeMillis();
    if (mCurrentPlayer == Player.BLACK && mBlackThinkStartMs > 0) {
      mBlackThinkTimeMs += (now - mBlackThinkStartMs);
    }
    if (mCurrentPlayer == Player.WHITE && mWhiteThinkStartMs > 0) {
      mWhiteThinkTimeMs += (now - mWhiteThinkStartMs);
    }
    
    // Switch the player, and start its timer.
    mCurrentPlayer = p;
    mBlackThinkStartMs = mWhiteThinkStartMs = 0;
    if (mCurrentPlayer == Player.BLACK) mBlackThinkStartMs = now;
    else if (mCurrentPlayer == Player.WHITE) mWhiteThinkStartMs = now;
  }
  
  private void schedulePeriodicTimer() {
    mEventHandler.postDelayed(mTimerHandler, 1000);
  }
  //
  // Undo
  //
  private void undo() {
    if (!isHumanPlayer(mCurrentPlayer)) {
      Toast.makeText(getBaseContext(), "Computer is thinking", 
          Toast.LENGTH_SHORT).show();
      return;
    } 
    if (mMoveCookies.size() < 2) return;
    int lastMove = mMoveCookies.get(mMoveCookies.size() - 1);
    int penultimateMove = mMoveCookies.get(mMoveCookies.size() - 2);
    mController.undo2(mCurrentPlayer, lastMove, penultimateMove);
    setCurrentPlayer(Player.INVALID);
    --mUndosRemaining;
    updateUndoMenu();
  }

  private void updateUndoMenu() {
    mMenu.setGroupEnabled(R.id.undo_group, (mUndosRemaining > 0));
    MenuItem item = mMenu.getItem(0);
    if (mUndosRemaining <= 0) {
      item.setTitle("Undo (disallowed)");
    } else if (mUndosRemaining >= 100) {
      item.setTitle("Undo");
    } else {
      item.setTitle("Undo (" + mUndosRemaining + " remaining)");
    }
  }  
  
  //
  // Handling results from the Bonanza controller thread
  //
  private final Handler mEventHandler = new Handler() {
    @Override public void handleMessage(Message msg) {
      BonanzaController.Result r = (BonanzaController.Result)(
          msg.getData().get("result"));
      mGameState = r.gameState;
      mBoard = r.board;
      setCurrentPlayer(r.nextPlayer);
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
  private Player mSavedPlayerForPromotion;
  private Move mSavedMoveForPromotion;    
  
  private final BoardView.EventListener mViewListener = new BoardView.EventListener() {
    public void onHumanMove(Player player, Move move) {
      setCurrentPlayer(Player.INVALID);  
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
    b.setTitle(R.string.promote_piece);
    b.setCancelable(true);
    b.setOnCancelListener(
        new DialogInterface.OnCancelListener() {
          public void onCancel(DialogInterface unused) {
            setCurrentPlayer(mSavedPlayerForPromotion);
            mBoardView.update(mGameState, mBoard, mCurrentPlayer);
          }
        });
    b.setItems(
        new CharSequence[] {
            getResources().getString(R.string.promote), 
            getResources().getString(R.string.do_not_promote) },
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
  
  private static final boolean MoveAllowsForPromotion(Player player, Move move) {
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
  private AlertDialog createConfirmQuitDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(R.string.confirm_quit_game);
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