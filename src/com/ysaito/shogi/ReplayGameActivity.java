package com.ysaito.shogi;

import java.util.ArrayList;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;

/**
 * The main activity that controls game play
 */
public class ReplayGameActivity extends Activity {
  private static final String TAG = "Replay"; 

  // View components
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
    ImageButton b = (ImageButton)findViewById(R.id.replay_beginning_button);
    b.setOnClickListener(new ImageButton.OnClickListener() {
      public void onClick(View v) { doBeginning(); }
    });
    b = (ImageButton)findViewById(R.id.replay_prev_button);
    b.setOnClickListener(new ImageButton.OnClickListener() {
      public void onClick(View v) { doPrev(); }
    });
    b = (ImageButton)findViewById(R.id.replay_next_button);
    b.setOnClickListener(new ImageButton.OnClickListener() {
      public void onClick(View v) { doNext(); }
    });
    b = (ImageButton)findViewById(R.id.replay_last_button);
    b.setOnClickListener(new ImageButton.OnClickListener() {
      public void onClick(View v) { doLast(); }
    });
    
  }

  private void doBeginning() { }
  private void doPrev() { }
  private void doNext() { }
  private void doLast() { }
  
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

  private final BoardView.EventListener mViewListener = new BoardView.EventListener() {
    public void onHumanMove(Player player, Move move) {
	// Replay screen doesn't allow for human move.
    }
  };
}