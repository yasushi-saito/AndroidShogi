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
  private Player mNextPlayer;   // the next player to make a move 
  private GameState mGameState;    // is the game is active or finished?
  private boolean mDestroyed;      // onDestroy called?

  private GameLog mLog;

  // Number of moves made so far. 0 means the beginning of the game.
  private int mNextMove;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.replay_game);
    initializeInstanceState(savedInstanceState);

    mNextMove = 0;
    mBoard = Board.newGame(Handicap.NONE);  // TODO
    mNextPlayer = Player.BLACK;
    
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
    mBoardView.update(mGameState, mBoard, Player.INVALID);
  }

  private void doBeginning() { }
  private void doPrev() { }
  
  private void doNext() { 
    if (mNextMove >= mLog.numMoves()) return;
    Move m = mLog.getMove(mNextMove);
    ++mNextMove;
    
    applyMove(mNextPlayer, m, mBoard);
    mNextPlayer = Player.opponentOf(mNextPlayer);
    mBoardView.update(mGameState, mBoard, Player.INVALID);
  }
  private void doLast() { }
  
  /**
   *  Apply the move "m" by player "p" to the board.
   */
  private static final void applyMove(Player p, Move m, Board b) {
    int oldPiece = Piece.EMPTY;
    if (m.getFromX() < 0) { // dropping?
      b.setPiece(m.getToX(), m.getToY(), m.getPiece());
    } else {
      b.setPiece(m.getFromX(), m.getFromY(), Piece.EMPTY);
      oldPiece = b.getPiece(m.getToX(), m.getToY());
      b.setPiece(m.getToX(), m.getToY(), m.getPiece());
    }
    if (oldPiece != Piece.EMPTY) {
      oldPiece = -oldPiece; // now the piece is owned by the opponent
      ArrayList<Board.CapturedPiece> captured = b.getCapturedPieces(p);
      boolean found = false; 
      for (int i = 0; i < captured.size(); ++i) {
        Board.CapturedPiece c = captured.get(i);
        if (c.piece == oldPiece) {
          Board.CapturedPiece nc = new Board.CapturedPiece(oldPiece, c.n + 1);
          captured.set(i, nc);
          found = true;
          break;
        }
      }
      if (!found) {
        captured.add(new Board.CapturedPiece(oldPiece, 1));
      }
      b.setCapturedPieces(p, captured);
    }
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