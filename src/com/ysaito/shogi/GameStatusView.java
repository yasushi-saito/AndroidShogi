package com.ysaito.shogi;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Widget for displaying game status, such as elapsed time per player and 
 * last moves.
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
  
  private TextView mGameStatus;
  private TextView mMoveHistory;
  private Timer mBlackTime;
  private TextView mBlackStatus;
  private Timer mWhiteTime;
  private TextView mWhiteStatus;
  private String mBlackPlayerName;
  private String mWhitePlayerName;
  
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
    mBlackTime = new Timer((TextView)findViewById(R.id.status_black_time));
    mBlackStatus = (TextView)findViewById(R.id.status_black_player_name);
    mWhiteTime = new Timer((TextView)findViewById(R.id.status_white_time));
    mWhiteStatus = (TextView)findViewById(R.id.status_white_player_name);
  
    mBlackPlayerName = blackPlayerName;
    mWhitePlayerName = whitePlayerName;
    mBlackStatus.setText(mBlackPlayerName);
    mWhiteStatus.setText(mWhitePlayerName);    
  }
  
  public final void update(
      GameState gameState,
      ArrayList<Move> moves,
      Player currentPlayer,
      String errorMessage) {
    if (currentPlayer == Player.WHITE) {
      mWhiteStatus.setBackgroundColor(0xffeeeeee);
      mWhiteStatus.setTextColor(0xff000000);
    } else {
      mWhiteStatus.setBackgroundColor(0xff000000);
      mWhiteStatus.setTextColor(0xffffffff);
    }
    if (currentPlayer == Player.BLACK) {
      mBlackStatus.setBackgroundColor(0xffeeeeee);
      mBlackStatus.setTextColor(0xff000000);
    } else {
      mBlackStatus.setBackgroundColor(0xff000000);
      mBlackStatus.setTextColor(0xffffffff);
    }
    if (moves.size() > 0) {
      // Display the last two moves.
      int n = Math.min(moves.size(), 2);
      String s = "";
      for (int i = moves.size() - n; i < moves.size(); ++i) {
        Move m = moves.get(i);
        if (s != "") s += ", ";
        s += (i+1) + ":" + m.toCsaString();
      }
      mMoveHistory.setText(s);
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
}
