package com.ysaito.shogi;

import android.content.Context;
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
 */
public class GameStatusView extends LinearLayout {
  private final String TAG = "ShogiStatus";
  
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
    mBlackTime = new Timer((TextView)findViewById(R.id.status_black_time));
    mBlackStatus = (TextView)findViewById(R.id.status_black_player_name);
    mWhiteTime = new Timer((TextView)findViewById(R.id.status_white_time));
    mWhiteStatus = (TextView)findViewById(R.id.status_white_player_name);
    mMoveList = new ArrayList<String>();
  
    mBlackPlayerName = blackPlayerName;
    mWhitePlayerName = whitePlayerName;
    mBlackStatus.setText(mBlackPlayerName);
    mWhiteStatus.setText(mWhitePlayerName);    
  }
  
  public final void update(
      GameState gameState,
      Board board,
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
    while (moves.size() > mMoveList.size()) {
      // Generally, moves is just one larger than mMoveList, in which case
      // we can use "board" to compute the display string of the last move.
      // If moves.size() > mMovesList.size() + 1, then moves other than the last
      // may be inaccurately displayed since "board" may not correspond to the
      // state before these moves are made.
      Move m = moves.get(mMoveList.size());
      mMoveList.add(traditionalMoveNotation(board, m));
    }
    
    // Handle undos
    while (moves.size() < mMoveList.size()) {
      mMoveList.remove(mMoveList.size() - 1);
    }
    
    if (mMoveList.size() > 0) {
      // Display the last two moves.
      int n = Math.min(mMoveList.size(), 2);
      String s = "";
      for (int i = mMoveList.size() - n; i < mMoveList.size(); ++i) {
        if (s != "") s += ", ";
        s += (i+1) + ":" + mMoveList.get(i);
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
  private final String traditionalMoveNotation(Board board, Move m) {
    if (!Locale.getDefault().getLanguage().equals("ja")) {
      return m.toCsaString();
    }
    Move.TraditionalNotation n = m.toTraditionalNotation(board);
    String s = String.format("%d%s%s%s",
        n.x, Move.japaneseNumbers[n.y], Piece.japaneseNames[n.piece],
        modifiersToJapanese(n.modifier));
    return s;
  }
  
  private static final String modifiersToJapanese(int modifiers) {
    String s = "";
    if ((modifiers & Move.DROP) != 0) s += "打";
    if ((modifiers & Move.PROMOTE) != 0) s += "成";    
    if ((modifiers & Move.FORWARD) != 0) s += "上";        
    if ((modifiers & Move.BACKWARD) != 0) s += "引";            
    if ((modifiers & Move.SIDEWAYS) != 0) s += "寄";          
    if ((modifiers & Move.RIGHT) != 0) s += "右";                    
    if ((modifiers & Move.LEFT) != 0) s += "左";                        
    if ((modifiers & Move.CENTER) != 0) s += "直";                            
    return s;
  }
}
