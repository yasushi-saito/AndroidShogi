package com.ysaito.shogi;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;

/**
 * Activity for replaying a saved game
 */
public class ReplayGameActivity extends Activity {
  // View components
  private BoardView mBoardView;
  private GameStatusView mStatusView;
  private SeekBar mSeekBar;

  private Activity mActivity;
  private GameLogListManager mGameLogList;
  
  // Game preferences
  private boolean mFlipScreen;

  // State of the game
  private Board mBoard;            // current state of the board
  private ArrayList<Play> mPlays;  // plays made up to mBoard.
  private Player mNextPlayer;   // the next player to make a move 
  private GameState mGameState;    // is the game is active or finished?

  private GameLog mLog;
  
  // Number of moves made so far. 0 means the beginning of the game.
  private int mNextPlay;

  private static final int MAX_PROGRESS = 1000; 
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mActivity = this;
    mGameLogList = GameLogListManager.getInstance();
    
    setContentView(R.layout.replay_game);
    initializeInstanceState(savedInstanceState);

    mLog = (GameLog)getIntent().getSerializableExtra("gameLog");
    Assert.isTrue(mLog.numPlays() > 0);
    
    mGameState = GameState.ACTIVE;
    mNextPlay = 0;
    mBoard = new Board();
    mPlays = new ArrayList<Play>();
    mBoard.initialize(mLog.handicap());
    mNextPlayer = Player.BLACK;
    
    mStatusView = (GameStatusView)findViewById(R.id.replay_gamestatusview);
    mStatusView.initialize(
        mLog.attr(GameLog.ATTR_BLACK_PLAYER),
        mLog.attr(GameLog.ATTR_WHITE_PLAYER));

    mBoardView = (BoardView)findViewById(R.id.replay_boardview);
    mBoardView.initialize(mViewListener, 
        new ArrayList<Player>(),  // Disallow board manipulation by the user
        mFlipScreen);
    ImageButton b;
    b = (ImageButton)findViewById(R.id.replay_prev_button);
    b.setOnClickListener(new ImageButton.OnClickListener() {
      public void onClick(View v) { 
        if (mNextPlay > 0) replayUpTo(mNextPlay - 1);
      }
    });
    b = (ImageButton)findViewById(R.id.replay_next_button);
    b.setOnClickListener(new ImageButton.OnClickListener() {
      public void onClick(View v) {
        if (mNextPlay < mLog.numPlays()) {
          replayUpTo(mNextPlay + 1);
        }
      }
    });

    mSeekBar = (SeekBar)findViewById(R.id.replay_seek_bar);
    mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
        if (fromTouch) {
          final int maxPlays = mLog.numPlays() - 1;
          int play = 0;
          if (progress >= MAX_PROGRESS) {
            play = maxPlays;
          } else if (progress <= 0) {
            play = 0;
          } else {
            play = (int)(maxPlays * (progress / (float)MAX_PROGRESS));
          }
          replayUpTo(play);
        }
      }
      public void onStartTrackingTouch(SeekBar seekBar) { }
      public void onStopTrackingTouch(SeekBar seekBar) { }
    });
    
    mBoardView.update(
        mGameState, mBoard, mBoard, 
        Player.INVALID, // Disallow board manipulation by the user
        null, false);
  }

  /**
   *  Play the game up to "numMoves" moves. numMoves==0 will initialize the board, and
   *  numMoves==mLog.numMoves-1 will recreate the final game state.
   */ 
  private final void replayUpTo(int numPlays) {
    mBoard.initialize(mLog.handicap());
    mNextPlayer = Player.BLACK;
    mPlays.clear();
    
    Play play = null;
    Board lastBoard = mBoard;
    
    // Compute the state of the game @ numMoves
    for (int i = 0; i < numPlays; ++i) {
      play = mLog.play(i);
      if (i == numPlays - 1) {
        lastBoard = new Board(mBoard);
      }
      mBoard.applyPly(mNextPlayer, play);
      mNextPlayer = mNextPlayer.opponent();
      mPlays.add(play);
    }
    mNextPlay = numPlays;
    mStatusView.update(mGameState, lastBoard, mBoard, mPlays, mNextPlayer, null);
    mBoardView.update(mGameState, lastBoard, mBoard, 
        Player.INVALID,  // Disallow board manipluation by the user 
        play, false);
    mSeekBar.setProgress((int)((float)MAX_PROGRESS * mNextPlay / mLog.numPlays()));
  }
  
  @Override 
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.replay_game_menu, menu);
    if (mLog.path() != null) {
      menu.findItem(R.id.menu_save_in_sdcard).setEnabled(false);
    }
    return true;
  }

  private static final int DIALOG_RESUME_GAME = 1;
  private static final int DIALOG_LOG_PROPERTIES = 2;
  private StartGameDialog mStartGameDialog;

  void resumeGame() {            
    Intent intent = new Intent(this, GameActivity.class);
    intent.putExtra("initial_board", mBoard);
    intent.putExtra("moves", mPlays);
    intent.putExtra("next_player", mNextPlayer);
    intent.putExtra("replaying_saved_game", true);

    Handicap h = mLog.handicap();
    if (h != Handicap.NONE) intent.putExtra("handicap", h);
    startActivity(intent);
  }
  
  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_flip_screen:
      mBoardView.flipScreen();
      return true;
    case R.id.menu_resume:
      // TODO(saito) Disable resumegame when !hasRequiredFiles.
      showDialog(DIALOG_RESUME_GAME);
      return true;
    case R.id.menu_save_in_sdcard: {
      new AsyncTask<GameLog, String, String>() {
        @Override
        protected String doInBackground(GameLog... logs) {
          mGameLogList.saveLogInSdcard(mActivity, logs[0]);
          return null;
        }
      }.execute(mLog);
      return true;
    }
    case R.id.menu_log_properties:
      showDialog(DIALOG_LOG_PROPERTIES);
      return true;
    default:    
      return super.onOptionsItemSelected(item);
    }
  }

  @Override protected Dialog onCreateDialog(int id) {
    switch (id) {
    case DIALOG_RESUME_GAME: {
      mStartGameDialog = new StartGameDialog(
      		this, "Resume Game",
          new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int id) {
             resumeGame();
           }}
          );
      return mStartGameDialog.getDialog();
    }
    case DIALOG_LOG_PROPERTIES:
      final GameLogPropertiesView view = new GameLogPropertiesView(this);
      view.initialize(mLog);
      
      return new AlertDialog.Builder(this)
      .setTitle(R.string.game_log_properties)
      .setView(view)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) { }
      }).create();
    default:
      return null;
    }
  }
  
  private final void initializeInstanceState(Bundle b) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
        getBaseContext());
    mFlipScreen = prefs.getBoolean("flip_screen", false);
  }

  private final BoardView.EventListener mViewListener = new BoardView.EventListener() {
    public void onHumanPlay(Player player, Play play) {
	// Replay screen doesn't allow for human play.
    }
  };
}