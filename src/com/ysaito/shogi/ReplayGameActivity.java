package com.ysaito.shogi;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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

  // Game preferences
  private boolean mFlipScreen;

  // State of the game
  private Board mBoard;            // current state of the board
  private ArrayList<Move> mMoves;  // moves made up to mBoard.
  private Player mNextPlayer;   // the next player to make a move 
  private GameState mGameState;    // is the game is active or finished?

  private GameLog mLog;
  
  // Number of moves made so far. 0 means the beginning of the game.
  private int mNextMove;

  private static final int MAX_PROGRESS = 1000; 
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.replay_game);
    initializeInstanceState(savedInstanceState);

    mLog = (GameLog)getIntent().getSerializableExtra("gameLog");
    Assert.isTrue(mLog.numMoves() > 0);
    
    mGameState = GameState.ACTIVE;
    mNextMove = 0;
    mBoard = new Board();
    mMoves = new ArrayList<Move>();
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
    // b = (ImageButton)findViewById(R.id.replay_beginning_button);
    //  b.setOnClickListener(new ImageButton.OnClickListener() {
    //    public void onClick(View v) { replayUpTo(0); }
    //  });
    b = (ImageButton)findViewById(R.id.replay_prev_button);
    b.setOnClickListener(new ImageButton.OnClickListener() {
      public void onClick(View v) { 
        if (mNextMove > 0) replayUpTo(mNextMove - 1);
      }
    });
    b = (ImageButton)findViewById(R.id.replay_next_button);
    b.setOnClickListener(new ImageButton.OnClickListener() {
      public void onClick(View v) {
        if (mNextMove < mLog.numMoves()) {
          replayUpTo(mNextMove + 1);
        }
      }
    });

    mSeekBar = (SeekBar)findViewById(R.id.replay_seek_bar);
    mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
        if (fromTouch) {
          final int maxMoves = mLog.numMoves() - 1;
          int move = 0;
          if (progress >= MAX_PROGRESS) {
            move = maxMoves;
          } else if (progress <= 0) {
            move = 0;
          } else {
            move = (int)(maxMoves * (progress / (float)MAX_PROGRESS));
          }
          replayUpTo(move);
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
  private final void replayUpTo(int numMoves) {
    mBoard.initialize(mLog.handicap());
    mNextPlayer = Player.BLACK;
    mMoves.clear();
    
    Move move = null;
    Board lastBoard = mBoard;
    
    // Compute the state of the game @ numMoves
    for (int i = 0; i < numMoves; ++i) {
      move = mLog.getMove(i);
      if (i == numMoves - 1) {
        lastBoard = new Board(mBoard);
      }
      mBoard.applyMove(mNextPlayer, move);
      mNextPlayer = mNextPlayer.opponent();
      mMoves.add(move);
    }
    mNextMove = numMoves;
    mStatusView.update(mGameState, lastBoard, mBoard, mMoves, mNextPlayer, null);
    mBoardView.update(mGameState, lastBoard, mBoard, 
        Player.INVALID,  // Disallow board manipluation by the user 
        move, false);
    mSeekBar.setProgress((int)((float)MAX_PROGRESS * mNextMove / mLog.numMoves()));
  }
  
  @Override 
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.replay_game_menu, menu);
    
    menu.setGroupEnabled(R.id.menu_save_in_sdcard_group,
        (mLog.getFlag() & GameLog.FLAG_ON_SDCARD) == 0);
    return true;
  }

  private static final int DIALOG_RESUME_GAME = 1;
  private static final int DIALOG_LOG_PROPERTIES = 2;
  private StartGameDialog mStartGameDialog;

  void resumeGame() {            
    Intent intent = new Intent(this, GameActivity.class);
    intent.putExtra("initial_board", mBoard);
    intent.putExtra("moves", mMoves);
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
      if (mStartGameDialog != null) {
        mStartGameDialog.loadPreferences();
      }
      showDialog(DIALOG_RESUME_GAME);
      return true;
    case R.id.menu_save_in_sdcard: {
      GameLogSaver saver = new GameLogSaver(this);
      saver.save(mLog);
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
      mStartGameDialog = new StartGameDialog(this, "Resume Game");
      mStartGameDialog.setOnClickStartButtonHandler(
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
    public void onHumanMove(Player player, Move move) {
	// Replay screen doesn't allow for human move.
    }
  };
}