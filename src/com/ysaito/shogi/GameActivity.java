package com.ysaito.shogi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

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

  // Game preferences
  private int mComputerLevel;      // 0 .. 4
  private String mPlayerTypes;     // "HC", "CH", "HH", "CC"
  private boolean mFlipScreen;

  // State of the game
  private long mStartTimeMs;       // Time the game started (UTC millisec)
  private Board mBoard;            // current state of the board
  private Player mCurrentPlayer;   // the next player to make a move 
  private GameState mGameState;    // is the game is active or finished?
  private long mBlackThinkTimeMs;  // Cumulative # of think time (millisec)
  private long mBlackThinkStartMs; // -1, or ms since epoch =
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
    initializeInstanceState(savedInstanceState);

    mStatusView = (GameStatusView)findViewById(R.id.gamestatusview);
    mStatusView.initialize(
        playerName(mPlayerTypes.charAt(0), mComputerLevel),
        playerName(mPlayerTypes.charAt(1), mComputerLevel));

    mBoardView = (BoardView)findViewById(R.id.boardview);
    mBoardView.initialize(mViewListener, mHumanPlayers, mFlipScreen);
    mController = new BonanzaController(mEventHandler, mComputerLevel);
    
    Board initialBoard = (Board)getIntent().getSerializableExtra("initial_board");
    mController.start(savedInstanceState, initialBoard);

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
  public void onSaveInstanceState(Bundle bundle) {
    saveInstanceState(bundle);
    mController.saveInstanceState(bundle);
  }

  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_undo:
      undo();
      return true;
    case R.id.menu_flip_screen:
      mBoardView.flipScreen();
      return true;
    default:    
      return super.onOptionsItemSelected(item);
    }
  }

  private final String playerName(char type, int level) {
    if (type == 'H') return getResources().getString(R.string.human);
    return getResources().getStringArray(R.array.computer_level_names)[level];
  }

  @Override public void onDestroy() {
    Log.d(TAG, "ShogiActivity destroyed");
    mController.destroy();
    super.onDestroy();
    mDestroyed = true;
  }

  @Override public void onBackPressed() { 
    if (mGameState == GameState.ACTIVE) {
      showDialog(DIALOG_CONFIRM_QUIT);
    } else {
      super.onBackPressed();
    }
  }

  @Override protected Dialog onCreateDialog(int id) {
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

  private final boolean isComputerPlayer(Player p) { 
    return p != Player.INVALID && !isHumanPlayer(p);
  }

  private final boolean isHumanPlayer(Player p) {
    return mHumanPlayers.contains(p);
  }

  private final void saveInstanceState(Bundle b) {
    b.putLong("shogi_undos_remaining", mUndosRemaining);
    b.putLong("shogi_black_think_time_ms", mBlackThinkTimeMs);
    b.putLong("shogi_white_think_time_ms", mWhiteThinkTimeMs);	  
    b.putLong("shogi_black_think_start_ms", mBlackThinkStartMs);	  	  
    b.putLong("shogi_white_think_start_ms", mWhiteThinkStartMs);
    b.putLong("shogi_start_time_ms", mStartTimeMs);
  }
  private final void initializeInstanceState(Bundle b) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
        getBaseContext());
    mUndosRemaining = (int)initializeLong(b, "shogi_undos_remaining", prefs, "max_undos", 0);
    mBlackThinkTimeMs = initializeLong(b, "shogi_black_think_time_ms", null, null, 0);
    mWhiteThinkTimeMs = initializeLong(b, "shogi_white_think_time_ms", null, null, 0);	  
    mBlackThinkStartMs = initializeLong(b, "shogi_black_think_start_ms", null, null, 0);
    mWhiteThinkStartMs = initializeLong(b, "shogi_white_think_start_ms", null, null, 0);
    mStartTimeMs = initializeLong(b, "shogi_start_time_ms", null, null, System.currentTimeMillis());

    mFlipScreen = prefs.getBoolean("flip_screen", false);
    mPlayerTypes = prefs.getString("player_types", "HC");
    mHumanPlayers = new ArrayList<Player>();
    if (mPlayerTypes.charAt(0) == 'H') {
      mHumanPlayers.add(Player.BLACK);
    }
    if (mPlayerTypes.charAt(1) == 'H') {
      mHumanPlayers.add(Player.WHITE);      
    }
    mComputerLevel = Integer.parseInt(prefs.getString("computer_difficulty", "1"));
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
  // Periodic status update
  //
  private final Runnable mTimerHandler = new Runnable() {
    public void run() { 
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
  private final void setCurrentPlayer(Player p) {
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

  private final void schedulePeriodicTimer() {
    mEventHandler.postDelayed(mTimerHandler, 1000);
  }
  //
  // Undo
  //
  private final void undo() {
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

  private final void updateUndoMenu() {
    mMenu.setGroupEnabled(R.id.menu_undo_group, (mUndosRemaining > 0));
    MenuItem item = mMenu.getItem(0);
    if (mUndosRemaining <= 0) {
      item.setTitle(R.string.undo_disallowed);
    } else if (mUndosRemaining >= 100) {
      item.setTitle(getResources().getText(R.string.undo));
    } else {
      item.setTitle(String.format(
          getResources().getString(R.string.undos_remaining),
          new Integer(mUndosRemaining)));
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
        mMoves.add(r.lastMove);
        mMoveCookies.add(r.lastMoveCookie);
      }
      setCurrentPlayer(r.nextPlayer);
      for (int i = 0; i < r.undoMoves; ++i) {
        Assert.isTrue(r.lastMove == null);
        mMoves.remove(mMoves.size() - 1);
        mMoveCookies.remove(mMoveCookies.size() - 1);
      }

      mBoardView.update(r.gameState, r.board, r.nextPlayer);
      mStatusView.update(r.gameState,
          // Note: statusview needs the board state before the move
          // to compute the traditional move notation.
          mBoard,
          mMoves, r.nextPlayer, r.errorMessage);

      mGameState = r.gameState;
      mBoard = r.board;
      if (isComputerPlayer(r.nextPlayer)) {
        mController.computerMove(r.nextPlayer);
      }
      if (mGameState != GameState.ACTIVE && 
          (true || mMoves.size() >= 30) &&
          !mPlayerTypes.equals("CC")) { 
        saveGame();
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

  private void saveGame() {
    TreeMap<String, String> attrs = new TreeMap<String, String>();
    attrs.put(GameLog.A_BLACK_PLAYER, blackPlayerName());
    attrs.put(GameLog.A_WHITE_PLAYER, whitePlayerName());
    Log.d(TAG, "SAVING");
    LogList.addGameLog(GameLog.newLog(mStartTimeMs, attrs, mMoves));
  }
  
  private String blackPlayerName() {
    if (mPlayerTypes.charAt(0) == 'H') {
      return getResources().getText(R.string.human).toString();
    }
    return String.format("Lv%d", mComputerLevel);
  }
  
  private String whitePlayerName() {
    if (mPlayerTypes.charAt(1) == 'H') {
      return getResources().getText(R.string.human).toString();
    }
    return String.format("Lv%d", mComputerLevel);
  }
  
  private final AlertDialog createPromoteDialog() {
    AlertDialog.Builder b = new AlertDialog.Builder(this);
    b.setTitle(R.string.promote_piece);
    b.setCancelable(true);
    b.setOnCancelListener(
        new DialogInterface.OnCancelListener() {
          public void onCancel(DialogInterface unused) {
            if (mSavedMoveForPromotion == null) {
              // Event delivered twice?
            } else {
              setCurrentPlayer(mSavedPlayerForPromotion);
              mBoardView.update(mGameState, mBoard, mCurrentPlayer);
              mSavedMoveForPromotion = null;
              mSavedPlayerForPromotion = null;
            }
          }
        });
    b.setItems(
        new CharSequence[] {
            getResources().getString(R.string.promote), 
            getResources().getString(R.string.do_not_promote) },
            new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface d, int item) {
            if (mSavedMoveForPromotion == null) {
              // A click event delivered twice?
              return;
            }

            if (item == 0) {
              mSavedMoveForPromotion = new Move(
                  Board.promote(mSavedMoveForPromotion.getPiece()), 
                  mSavedMoveForPromotion.getFromX(), mSavedMoveForPromotion.getFromY(),
                  mSavedMoveForPromotion.getToX(), mSavedMoveForPromotion.getToY());
            }
            mController.humanMove(mSavedPlayerForPromotion, mSavedMoveForPromotion);
            mSavedMoveForPromotion = null;
            mSavedPlayerForPromotion = null;
          }
        });
    return b.create();
  }

  private static final boolean MoveAllowsForPromotion(Player player, Move move) {
    if (Board.isPromoted(move.getPiece())) return false;  // already promoted

    final int type = Board.type(move.getPiece());
    if (type == Piece.KIN || type == Piece.OU) return false;

    if (move.getFromX() < 0) return false;  // dropping a captured piece
    if (player == Player.WHITE && move.getFromY() < 6 && move.getToY() < 6) return false;
    if (player == Player.BLACK && move.getFromY() >= 3 && move.getToY() >= 3) return false;
    return true;
  }

  // 
  // Confirm quitting the game ("BACK" button interceptor)
  //
  private final AlertDialog createConfirmQuitDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(R.string.confirm_quit_game);
    builder.setCancelable(false);
    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface d, int id) {
        saveGame();
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