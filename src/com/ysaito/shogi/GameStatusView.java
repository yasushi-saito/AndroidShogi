package com.ysaito.shogi;

import android.content.Context;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Widget for displaying game status, such as elapsed time per player and 
 * last moves.
 * 
 * TODO: show sente-gote image before each player name.
 */
public class GameStatusView extends LinearLayout {
  class Timer {
    public Timer(TextView v) { mView = v; mLastThinkTimeSeconds = -1; }
    public void update(long thinkTimeMs) {
      long t = thinkTimeMs / 1000;  // convent millisecs -> seconds
      if (mLastThinkTimeSeconds != t) {
        mLastThinkTimeSeconds = t;
        long seconds = t % 60;
        long minutes = t / 60;
        mView.setText(String.format("%3d:%02d  ", minutes, seconds));
      }
    }
    public TextView getView() { return mView; }
    
    private final TextView mView;
    private long mLastThinkTimeSeconds;
  }

  private static final String TAG = "ShogiStatus";
  
  private TextView mGameStatus;
  private TextView mMoveHistory;
  private Timer mBlackTime;
  private TextView mBlackStatus;
  private Timer mWhiteTime;
  private TextView mWhiteStatus;
  private String mBlackPlayerName;
  private String mWhitePlayerName;
  
  // List of past moves, in display format.
  private ArrayList<String> mMoveList;
  
  public GameStatusView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }
  public GameStatusView(Context context) {
    super(context);
  }
  
  public final void initialize(
      String blackPlayerName,
      String whitePlayerName) {
    mGameStatus = (TextView)findViewById(R.id.status_game_status);
    mMoveHistory = (TextView)findViewById(R.id.status_move_history);
    mMoveHistory.setHorizontallyScrolling(true);
    mBlackTime = new Timer((TextView)findViewById(R.id.status_black_time));
    mBlackStatus = (TextView)findViewById(R.id.status_black_player_name);
    mWhiteTime = new Timer((TextView)findViewById(R.id.status_white_time));
    mWhiteStatus = (TextView)findViewById(R.id.status_white_player_name);
    mMoveList = new ArrayList<String>();
  
    mBlackPlayerName = "▲" + blackPlayerName;
    mWhitePlayerName = "△" + whitePlayerName;
    mBlackStatus.setText(mBlackPlayerName);
    mWhiteStatus.setText(mWhitePlayerName);    
  }
  
  /**
   * Update the state of the game and redraw widgets.
   * 
   * @param gameState
   * @param lastBoard State of the board before the last move
   * @param board The uptodate state of the board
   * @param moves The list of moves leading up to "board"
   * @param currentPlayer The player to hold the next turn. 
   * @param errorMessage
   */
  public final void update(
      GameState gameState,
      Board lastBoard,
      Board board,
      ArrayList<Move> moves,
      Player currentPlayer,
      String errorMessage) {
    
    if (currentPlayer == Player.WHITE) {
      
      mBlackStatus.setText(mBlackPlayerName);
      SpannableString s = new SpannableString(mWhitePlayerName);
      s.setSpan(new UnderlineSpan(), 0, s.length(), 0);
      mWhiteStatus.setText(s);
    } else {
      SpannableString s = new SpannableString(mBlackPlayerName);
      s.setSpan(new UnderlineSpan(), 0, s.length(), 0);
      mBlackStatus.setText(s);
      mWhiteStatus.setText(mWhitePlayerName);
    }
    
    while (moves.size() > mMoveList.size()) {
      // Generally, moves is just one larger than mMoveList, in which case
      // we can use "lastBoard" to compute the display string of the last move.
      // If moves.size() > mMovesList.size() + 1, then moves other than the last
      // may be inaccurately displayed since "lastBoard" may not correspond to the
      // state before these moves are made.
      Move thisMove = moves.get(mMoveList.size());
      Move prevMove = (mMoveList.size() > 0 ? moves.get(mMoveList.size() - 1) : null);
      mMoveList.add(traditionalMoveNotation(lastBoard, thisMove, prevMove));
    }
    
    // Handle undos
    while (moves.size() < mMoveList.size()) {
      mMoveList.remove(mMoveList.size() - 1);
    }
    
    if (mMoveList.size() > 0) {
      // Display the last six moves. The TextView is right-justified, so if the view isn't wide enough, earlier moves 
      // will be shown truncated.
      int n = Math.min(mMoveList.size(), 6);
      StringBuilder b = new StringBuilder();
      boolean first = true;
      for (int i = mMoveList.size() - n; i < mMoveList.size(); ++i) {
        if (!first) b.append(", ");
        b.append(i + 1).append(":");
        b.append((i % 2 == 0) ? "▲" : "△");
        b.append(mMoveList.get(i));
        first = false;
      }
      mMoveHistory.setText(b.toString());
    }
    String endGameMessage = null;
    if (gameState == GameState.ACTIVE) {
    } else if (gameState == GameState.BLACK_LOST) {
      endGameMessage = getResources().getString(R.string.white_won); 
    } else if (gameState == GameState.WHITE_LOST) {
      endGameMessage = getResources().getString(R.string.black_won);       
    } else if (gameState == GameState.DRAW) {
      endGameMessage = getResources().getString(R.string.draw);             
    } else {
      throw new AssertionError(gameState.toString());
    }
    if (endGameMessage != null) {
      Toast.makeText(getContext(), endGameMessage, Toast.LENGTH_LONG).show();
      mGameStatus.setText(endGameMessage);
    }
  }
  
  public final void updateThinkTimes(long black, long white) {
    mBlackTime.update(black);
    mWhiteTime.update(white);
  }
  
  private final String traditionalMoveNotation(Board board, Move thisMove, Move prevMove) {
    if (Locale.getDefault().getLanguage().equals("ja")) {
      return thisMove.toTraditionalNotation(board, prevMove).toJapaneseString();
    } else {
      return thisMove.toCsaString();
    }
  }
}
