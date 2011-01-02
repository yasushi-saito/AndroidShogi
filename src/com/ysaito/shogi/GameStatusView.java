// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * @author saito@google.com (Your Name Here)
 *
 */
public class GameStatusView extends LinearLayout {
  Context mContext;
  TextView mGameStatus;
  TextView mBlackTime;
  TextView mBlackStatus;
  TextView mWhiteTime;
  TextView mWhiteStatus;
  String mBlackPlayerName;
  String mWhitePlayerName;
  
  int dpsToPixels(int v) {
    return (int)(mContext.getResources().getDisplayMetrics().density * v);
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
    
    mGameStatus = new TextView(context);
    addView(mGameStatus, lStatus);
    
    mBlackTime = new TextView(context);
    LinearLayout.LayoutParams lTime = new LinearLayout.LayoutParams(
        dpsToPixels(64),
        LayoutParams.WRAP_CONTENT);
    lTime.gravity = Gravity.TOP;
    addView(mBlackTime, lTime);

    mBlackStatus = new TextView(context);
    addView(mBlackStatus, lStatus);
    
    mWhiteTime = new TextView(context);
    addView(mWhiteTime, lTime);
    
    mWhiteStatus = new TextView(context);
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
    mBlackStatus.invalidate();
    mWhiteStatus.invalidate();

    String msg = "";
    if (moves.size() > 0) {
      Move m = moves.get(moves.size() - 1);
      msg = m.toCsaString() + " ";
    }
    if (gameState == GameState.ACTIVE) {
    } else if (gameState == GameState.BLACK_LOST) {
      msg += "Black lost";
    } else if (gameState == GameState.WHITE_LOST) {
      msg += "White lost";
    } else if (gameState == GameState.DRAW) {
      msg += "Draw";
    } else {
      throw new AssertionError(gameState.toString());
    }
    mGameStatus.setText(msg);
  }
}
