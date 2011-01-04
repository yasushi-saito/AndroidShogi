// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
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
        t /= 60;
        long minutes = t % 60;
        long hours = t / 60;
        mView.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
      }
    }
    public TextView getView() { return mView; }
    
    private final TextView mView;
    private long mLastThinkTimeSeconds;
  }
  
  private Context mContext;
  private TextView mGameStatus;
  private Timer mBlackTime;
  private TextView mBlackStatus;
  private Timer mWhiteTime;
  private TextView mWhiteStatus;
  private String mBlackPlayerName;
  private String mWhitePlayerName;
  
  private int dpsToPixels(int v) {
    return (int)(mContext.getResources().getDisplayMetrics().density * v);
  }
  
  private TextView newTextView(Context context) {
    TextView v = new TextView(context);
    v.setTextSize(dpsToPixels(14));
    return v;
  }
  
  public GameStatusView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setOrientation(HORIZONTAL);
    mContext = context;
    
    LinearLayout.LayoutParams lStatus = new LinearLayout.LayoutParams(
        LayoutParams.WRAP_CONTENT,
        LayoutParams.WRAP_CONTENT);
    lStatus.gravity = Gravity.TOP| Gravity.FILL_HORIZONTAL;
    lStatus.weight = 1;
    
    mGameStatus = newTextView(context);
    addView(mGameStatus, lStatus);
    
    mBlackTime = new Timer(newTextView(context));
    LinearLayout.LayoutParams lTime = new LinearLayout.LayoutParams(
        dpsToPixels(64),
        LayoutParams.WRAP_CONTENT);
    lTime.gravity = Gravity.TOP;
    addView(mBlackTime.getView(), lTime);

    mBlackStatus = newTextView(context);
    addView(mBlackStatus, lStatus);
    
    mWhiteTime = new Timer(newTextView(context));
    addView(mWhiteTime.getView(), lTime);
    
    mWhiteStatus = newTextView(context);
    addView(mWhiteStatus, lStatus);
  }
  
  public void initialize(
      String blackPlayerName,
      String whitePlayerName) {
    mBlackPlayerName = blackPlayerName;
    mWhitePlayerName = whitePlayerName;
    mBlackStatus.setText(mBlackPlayerName);
    mWhiteStatus.setText(mWhitePlayerName);    
  }
  
  public void update(
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
    String msg = "";
    if (moves.size() > 0) {
      Move m = moves.get(moves.size() - 1);
      msg = m.toCsaString() + " ";
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
      msg += endGameMessage;
    }
    mGameStatus.setText(msg);
  }
  public void updateThinkTimes(long black, long white) {
    mBlackTime.update(black);
    mWhiteTime.update(white);
  }
}
