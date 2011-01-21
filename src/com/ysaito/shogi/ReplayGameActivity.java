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

  private final void initializeBoard() {
    mBoard.initialize(Handicap.NONE);  // TODO: support handicap
  }
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.replay_game);
    initializeInstanceState(savedInstanceState);

    mNextMove = 0;
    mBoard = new Board();
    initializeBoard();
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
      public void onClick(View v) { replayUpTo(0); }
    });
    b = (ImageButton)findViewById(R.id.replay_prev_button);
    b.setOnClickListener(new ImageButton.OnClickListener() {
      public void onClick(View v) { 
        if (mNextMove > 0) replayUpTo(mNextMove - 1);
      }
    });
    b = (ImageButton)findViewById(R.id.replay_next_button);
    b.setOnClickListener(new ImageButton.OnClickListener() {
      public void onClick(View v) { 
        if (mNextMove >= mLog.numMoves()) return;
        Move m = mLog.getMove(mNextMove);
        ++mNextMove;
        
        applyMove(mNextPlayer, m, mBoard);
        mNextPlayer = Player.opponentOf(mNextPlayer);
        mBoardView.update(mGameState, mBoard, Player.INVALID);
      }
    });
    b = (ImageButton)findViewById(R.id.replay_last_button);
    b.setOnClickListener(new ImageButton.OnClickListener() {
      public void onClick(View v) {
        replayUpTo(mLog.numMoves() - 1);
      }
    });
    mBoardView.update(mGameState, mBoard, Player.INVALID);
  }

  /**
   *  Play the game up to "numMoves" moves. numMoves==0 will initialize the board, and
   *  numMoves==mLog.numMoves-1 will recreate the final game state.
   */ 
  private final void replayUpTo(int numMoves) {
    initializeBoard();
    mNextPlayer = Player.BLACK;
    for (int i = 0; i < numMoves; ++i) {
      applyMove(mNextPlayer, mLog.getMove(i), mBoard);
      mNextPlayer = Player.opponentOf(mNextPlayer);
    }
    mNextMove = numMoves;
    mBoardView.update(mGameState, mBoard, Player.INVALID);
  }
  
  
  /**
   *  Apply the move "m" by player "p" to the board. 
   *  Does not update the screen; for that, the caller must call mBoardView.update.
   */
  private static final void applyMove(Player p, Move m, Board b) {
    int oldPiece = Piece.EMPTY;
    boolean capturedChanged = false;
    ArrayList<Board.CapturedPiece> captured = b.getCapturedPieces(p);
    
    if (m.getFromX() < 0) { // dropping
      b.setPiece(m.getToX(), m.getToY(), m.getPiece());
      capturedChanged = true;
      for (int i = 0; i < captured.size(); ++i) {
        Board.CapturedPiece c = captured.get(i);
        if (c.piece == m.getPiece()) {
          if (c.n == 1) {
            captured.remove(i);
          } else {
            captured.set(i, new Board.CapturedPiece(c.piece, c.n - 1));
          }
          break;
        }
      }
    } else {
      b.setPiece(m.getFromX(), m.getFromY(), Piece.EMPTY);
      oldPiece = b.getPiece(m.getToX(), m.getToY());
      b.setPiece(m.getToX(), m.getToY(), m.getPiece());
    }
    if (oldPiece != Piece.EMPTY) {
      capturedChanged = true;
      oldPiece = -oldPiece; // now the piece is owned by the opponent
      if (Board.isPromoted(oldPiece)) {
        oldPiece = Board.unpromote(oldPiece);
      }
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
    }
    if (capturedChanged) b.setCapturedPieces(p, captured);
  }
  
  @Override 
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.replay_game_menu, menu);
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