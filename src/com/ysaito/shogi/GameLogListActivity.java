package com.ysaito.shogi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Activity that lists available game logs.
 *
 */
public class GameLogListActivity extends ListActivity  {
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
      if (log == null) {
        // this shouldn't happen
        text.setText("");
      } else {
        StringBuilder b = new StringBuilder();
        long date = log.getDate();
        if (date > 0) {
          mTmpCalendar.setTimeInMillis(date);
          b.append(String.format("%04d/%02d/%02d ", 
              mTmpCalendar.get(Calendar.YEAR),
              mTmpCalendar.get(Calendar.MONTH) - Calendar.JANUARY + 1,
              mTmpCalendar.get(Calendar.DAY_OF_MONTH)));
        }
        if (log.path() != null) {
          b.append("[sd] ");
        } 
        String v = log.attr(GameLog.ATTR_BLACK_PLAYER);
        if (v != null) b.append(v);
        b.append("/");
        v = log.attr(GameLog.ATTR_WHITE_PLAYER);
        if (v != null) b.append(v);
        v = log.attr(GameLog.ATTR_TOURNAMENT);
        if (v != null) {
          b.append("/").append(v);
        } else {
          v = log.attr(GameLog.ATTR_TITLE);
          if (v != null) {
            b.append("/").append(v);
          }
        }
        b.append("/").append(log.numPlays());
        b.append(getResources().getString(R.string.plays_suffix));
        text.setTextSize(14);
        text.setText(b.toString());
        text.setHorizontallyScrolling(true);
      }
      return text;
    }
  }

  private MyAdapter mAdapter;

  private Comparator<GameLog> mLogSorter; 
  private ArrayList<GameLog> mLogs;
  private boolean mLogsSorted;
  
  private GameLog getNthLog(int position) {
    if (!mLogsSorted) {
      Collections.sort(mLogs, mLogSorter);
      mLogsSorted = true;
    }
    if (position >= mLogs.size()) return null;
    return mLogs.get(position);
  }
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.game_log_list);

    mLogSorter = GameLog.SORT_BY_DATE;
    mLogs = new ArrayList<GameLog>();
    mAdapter = new MyAdapter(this);
    
    ListView listView = (ListView)findViewById(android.R.id.list);
    registerForContextMenu(listView);
    
    // Use an existing ListAdapter that will map an array
    // of strings to TextViews
    setListAdapter(mAdapter);
    startListLogs(LogListManager.Mode.READ_SDCARD_SUMMARY);
  }
  
  private void startListLogs(LogListManager.Mode mode) {
    mLogs.clear();
    mLogsSorted = false;
    
    LogListManager.getSingletonInstance().listLogs(
        new LogListManager.ListLogsListener() {
          public void onNewGameLogs(Collection<GameLog> logs) {
            if (mLogs.addAll(logs)) {
              mLogsSorted = false;
              mAdapter.notifyDataSetChanged();
            }
          }
          public void onFinish(String error) { 
            mAdapter.notifyDataSetChanged();
          }
        },
        this,
        mode);
  }

  @Override 
  public void onListItemClick(ListView l, View v, int position, long id) {
    replayGame(position);
  }
  
  private void replayGame(int position) {
    GameLog log = getNthLog(position);
    if (log == null) {
      Log.d(TAG, "Invalid item click: " + position);
      return;
    }
    Intent intent = new Intent(this, ReplayGameActivity.class);
    Serializable ss = (Serializable)log;
    intent.putExtra("gameLog", ss);
    startActivity(intent);
  }

  @Override protected Dialog onCreateDialog(int id) {
    switch (id) {
    case DIALOG_LOG_PROPERTIES:
    default:
      return null;
    }
  }
  

  @Override
  public void onCreateContextMenu(
      ContextMenu menu, 
      View v,
      ContextMenuInfo menuInfo) {
    super.onCreateContextMenu(menu, v, menuInfo);
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.game_log_list_context_menu, menu);
    GameLog log = getNthLog(((AdapterView.AdapterContextMenuInfo)menuInfo).position);
    
    if (log.path() != null) {
      menu.findItem(R.id.game_log_list_save_in_sdcard).setEnabled(false);
    }
  }  

  @Override 
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.game_log_list_option_menu, menu);
    return true;
  }

  private void sortLogs(Comparator<GameLog> sorter) {
    if (mLogSorter != sorter) {
      mLogSorter = sorter;
      startListLogs(LogListManager.Mode.READ_SDCARD_SUMMARY);
    }
  }
  
  static final int DIALOG_LOG_PROPERTIES = 1;
  
  @Override
  public boolean onContextItemSelected(MenuItem item) {
    AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    switch (item.getItemId()) {
    case R.id.game_log_list_replay:
      replayGame(info.position);
      return true;
    case R.id.game_log_list_delete_log: {
      GameLog log = getNthLog(info.position);
      if (log != null) { 
        class DoDelete implements DialogInterface.OnClickListener {
          final LogListManager.Listener mListener;
          final GameLogListActivity mActivity;
          final GameLog mLog;
          public DoDelete(GameLogListActivity a, GameLog log) {
            mListener = new LogListManager.Listener() {
              public void onFinish(String error) { 
                startListLogs(LogListManager.Mode.READ_SDCARD_SUMMARY);
              }
            };
            mActivity = a; 
            mLog = log; 
          }
          public void onClick(DialogInterface dialog, int whichButton) { 
            LogListManager.getSingletonInstance().deleteLog(mListener, mActivity, mLog);
          }
        }
        
        String path;
        if (log.path() != null) {
          path = log.path().getAbsolutePath();
        } else {
          path = "in memory";
        }
        new AlertDialog.Builder(this)
          .setTitle("Delete log " + path + "?")
          .setPositiveButton(android.R.string.ok, new DoDelete(this, log))
          .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) { }
          }).create().show();
      }
      return true;
    }
    case R.id.game_log_list_save_in_sdcard: {
      GameLog log = getNthLog(info.position);
      if (log != null) {
        LogListManager.getSingletonInstance().saveLogInSdcard(
            new LogListManager.Listener() {
              public void onFinish(String error) { 
                startListLogs(LogListManager.Mode.READ_SDCARD_SUMMARY);
              }
            },
            this,
            log);
      }
      return true;
    }
    case R.id.game_log_list_properties: {
      GameLog log = getNthLog(info.position);
      if (log != null) {
        final GameLogPropertiesView view = new GameLogPropertiesView(this);
        view.initialize(log);
        new AlertDialog.Builder(this)
        .setTitle(R.string.game_log_properties)
        .setView(view)
        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int whichButton) { }
        }).create().show();
      }
      return true;
    }
    default:
      return super.onContextItemSelected(item);
    }
  }
  
  
  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_reload:
      startListLogs(LogListManager.Mode.RESET_SDCARD_SUMMARY);
      return true;
    case R.id.menu_sort_by_date:
      sortLogs(GameLog.SORT_BY_DATE);
      return true;
    case R.id.menu_sort_by_black_player:
      sortLogs(GameLog.SORT_BY_BLACK_PLAYER);
      return true;
    case R.id.menu_sort_by_white_player:
      sortLogs(GameLog.SORT_BY_WHITE_PLAYER);
      return true;
    default:    
      return super.onOptionsItemSelected(item);
    }
  }
}
