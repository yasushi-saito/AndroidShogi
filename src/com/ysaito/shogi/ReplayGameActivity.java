package com.ysaito.shogi;

import java.util.ArrayList;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.ysaito.shogi.BonanzaController;

/**
 * The main activity that controls game play
 */
public class ReplayGameActivity extends Activity {
  private static final String TAG = "Replay"; 

  // View components
  private BonanzaController mController;
  private BoardView mBoardView;
  private GameStatusView mStatusView;
  private Menu mMenu;

  // Game preferences
  private boolean mFlipScreen;

  // State of the game
  private Board mBoard;            // current state of the board
  private Player mCurrentPlayer;   // the next player to make a move 
  private GameState mGameState;    // is the game is active or finished?
  private boolean mDestroyed;      // onDestroy called?

  private GameLog mLog;

  // History of moves made in the game. Even (resp. odd) entries are 
  // moves by the black (resp. white) player.
  private final ArrayList<Move> mMovesDone = new ArrayList<Move>();
  private final ArrayList<Integer> mMoveCookies = new ArrayList<Integer>();

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.replay_game);
    initializeInstanceState(savedInstanceState);

    mLog = (GameLog)getIntent().getSerializableExtra("gameLog");
    mStatusView = (GameStatusView)findViewById(R.id.replay_gamestatusview);
    mStatusView.initialize(mLog.getAttr(GameLog.A_BLACK_PLAYER),
			     mLog.getAttr(GameLog.A_WHITE_PLAYER));

    mBoardView = (BoardView)findViewById(R.id.replay_boardview);
    mBoardView.initialize(mViewListener, 
			  new ArrayList<Player>(),  // don't allow manipulation
			  mFlipScreen);
    mController = new BonanzaController(mEventHandler, 0, 0);
    mController.start(savedInstanceState);
  }

  @Override 
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.game_menu, menu);
    mMenu = menu;
    return true;
  }

  @Override 
  public void onSaveInstanceState(Bundle bundle) {
    saveInstanceState(bundle);
    mController.saveInstanceState(bundle);
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.flip_screen:
      mBoardView.flipScreen();
      return true;
    default:    
      return super.onOptionsItemSelected(item);
    }
  }

  @Override public void onDestroy() {
    Log.d(TAG, "ShogiActivity destroyed");
    mController.destroy();
    super.onDestroy();
    mDestroyed = true;
  }

  private final void saveInstanceState(Bundle b) {
  }

  private final void initializeInstanceState(Bundle b) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
        getBaseContext());
    mFlipScreen = prefs.getBoolean("flip_screen", false);
  }

  private final long initializeLong(Bundle b, String bundle_key, SharedPreferences prefs, String pref_key, long dflt) {
    long v = dflt;
    if (b != null) {
      v = b.getLong(bundle_key, dflt);
      if (v != dflt) return v;
    }
    if (prefs != null) {
      return Integer.parseInt(prefs.getString(pref_key, String.valueOf(dflt)));
    } else {
      return v;
    }
  }

  //
  // Handling results from the Bonanza controller thread
  //
  private final Handler mEventHandler = new Handler() {
    @Override public void handleMessage(Message msg) {
      BonanzaController.Result r = (BonanzaController.Result)(
          msg.getData().get("result"));
      if (r.lastMove != null) {
        mMovesDone.add(r.lastMove);
        mMoveCookies.add(r.lastMoveCookie);
      }
      mCurrentPlayer = r.nextPlayer;
      for (int i = 0; i < r.undoMoves; ++i) {
        Assert.isTrue(r.lastMove == null);
        mMovesDone.remove(mMovesDone.size() - 1);
        mMoveCookies.remove(mMoveCookies.size() - 1);
      }

      mBoardView.update(r.gameState, r.board, Player.INVALID);
      mStatusView.update(r.gameState,
          // Note: statusview needs the board state before the move
          // to compute the traditional move notation.
          mBoard,
          mMovesDone, r.nextPlayer, r.errorMessage);

      mGameState = r.gameState;
      mBoard = r.board;
    }
  };

  private final BoardView.EventListener mViewListener = new BoardView.EventListener() {
    public void onHumanMove(Player player, Move move) {
	// Replay screen doesn't allow for human move.
    }
  };
}