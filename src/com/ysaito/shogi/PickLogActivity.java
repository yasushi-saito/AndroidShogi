package com.ysaito.shogi;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.TreeSet;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class PickLogActivity extends ListActivity  {
  private static final String TAG = "PickLog";
  
  private class MyAdapter extends BaseAdapter {
    private LayoutInflater mInflater;
    private final GregorianCalendar mTmpCalendar = new GregorianCalendar();
    
    public MyAdapter(Context context) {
      mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public int getCount() {
      return mLogs.size();
    }

    public Object getItem(int position) {
      return position;
    }

    public long getItemId(int position) {
      return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
      TextView text;

      if (convertView == null) {
        text = (TextView)mInflater.inflate(android.R.layout.simple_list_item_1, parent, false);
      } else {
        text = (TextView)convertView;
      }

      GameLog log = getNthLog(position);
      
      StringBuilder b = new StringBuilder();
      long date = log.getDate();
      if (date > 0) {
        mTmpCalendar.setTimeInMillis(date);
        b.append(String.format("%04d/%02d/%02d ", 
            mTmpCalendar.get(Calendar.YEAR),
            mTmpCalendar.get(Calendar.MONTH) - Calendar.JANUARY + 1,
            mTmpCalendar.get(Calendar.DAY_OF_MONTH)));
      }
      String v = log.attr(GameLog.A_BLACK_PLAYER);
      if (v != null) b.append(v);
      b.append("/");
      v = log.attr(GameLog.A_WHITE_PLAYER);
      if (v != null) b.append(v);
      v = log.attr(GameLog.A_TOURNAMENT);
      if (v != null) {
        b.append("/").append(v);
      } else {
        v = log.attr(GameLog.A_TITLE);
        if (v != null) {
          b.append("/").append(v);
        }
      }
      text.setTextSize(14);
      text.setText(b.toString());
      text.setHorizontallyScrolling(true);
      return text;
    }
  }

  private MyAdapter mAdapter;

  private final Comparator<GameLog> BY_DATE = new Comparator<GameLog>() {
    public int compare(GameLog g1, GameLog g2) { 
      if (g1.getDate() < g2.getDate()) return -1;
      if (g1.getDate() > g2.getDate()) return 1;      

      // Use player name, then digest as a tiebreaker.
      
      // int x = BY_PLAYERS.compare(g1, g2);
      // if (x != 0) return x;
      return g1.digest().compareTo(g2.digest());
    }
  };

  private final Comparator<GameLog> BY_PLAYER = new Comparator<GameLog>() {
    public int compare(GameLog g1, GameLog g2) { 
      String p1 = getMinPlayer(g1);
      String p2 = getMinPlayer(g2);      
      int x = p1.compareTo(p2);
      if (x != 0) return x;
      
      return BY_DATE.compare(g1, g2);
    }
  };
  
  private static final String getMinPlayer(GameLog g) {
    String black = g.attr(GameLog.A_BLACK_PLAYER);
    if (black == null) black = "";
    String white = g.attr(GameLog.A_WHITE_PLAYER);
    if (white == null) white = "";
    return (black.compareTo(white) <= 0) ? black : white;
  }
  
  private Comparator<GameLog> mLogSorter; 
  private TreeSet<GameLog> mLogs;
  
  private ArrayList<GameLog> mLogsArray;
  private Iterator<GameLog> mLogsIterator;
  private GameLog getNthLog(int position) {
    if (position >= mLogs.size()) return null;

    if (mLogsIterator == null) mLogsIterator = mLogs.iterator();
    while (mLogsArray.size() <= position) {
      mLogsArray.add(mLogsIterator.next());
    }
    return mLogsArray.get(position);
  }
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.log_picker);

    mLogSorter = BY_DATE;
    mLogs = new TreeSet<GameLog>(mLogSorter);
    mLogsArray = new ArrayList<GameLog>();
    mAdapter = new MyAdapter(this);
    // Use an existing ListAdapter that will map an array
    // of strings to TextViews
    setListAdapter(mAdapter);
    startListingLogs(LogList.Mode.READ_SAVED_SUMMARY);
  }
  
  private void startListingLogs(LogList.Mode mode) {
    LogList.startListing(
        this,
        new LogList.EventListener() {
          public void onNewGameLogs(GameLog[] logs) {
            for (GameLog log: logs) mLogs.add(log);
            mLogsIterator = null;
            mLogsArray.clear();
            mAdapter.notifyDataSetChanged();
          }
          public void onFinish(String error) { }
        },
        mode);
  }

  @Override 
  public void onListItemClick(ListView l, View v, int position, long id) {
    if (position >= mLogs.size()) {
      Log.d(TAG, "Invalid item click: " + position);
      return;
    }
    GameLog log = getNthLog(position);
    Intent intent = new Intent(this, ReplayGameActivity.class);
    intent.putExtra("gameLog", log);
    startActivity(intent);
  }
  
  @Override 
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.log_picker_menu, menu);
    return true;
  }

  private void sortLogs(Comparator<GameLog> sorter) {
    if (mLogSorter != sorter) {
        mLogSorter = sorter;
        mLogs = new TreeSet<GameLog>(mLogSorter);
        mLogsIterator = null;
        mLogsArray.clear();
        startListingLogs(LogList.Mode.READ_SAVED_SUMMARY);
    }
  }
  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_reload:
      mLogs.clear();
      startListingLogs(LogList.Mode.CLEAR_SAVED_SUMMARY);
      return true;
    case R.id.menu_sort_by_date:
      sortLogs(BY_DATE);
      return true;
    case R.id.menu_sort_by_player:
      sortLogs(BY_PLAYER);
      return true;
    default:    
      return super.onOptionsItemSelected(item);
    }
  }
}
