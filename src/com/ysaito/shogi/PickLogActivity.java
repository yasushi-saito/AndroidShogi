package com.ysaito.shogi;

import java.io.Serializable;
import java.util.ArrayList;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class PickLogActivity extends ListActivity  {
  private static final String TAG = "PickLog";
  private class MyAdapter extends BaseAdapter {
    private LayoutInflater mInflater;

    public MyAdapter(Context context) {
      mContext = context;
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

      GameLog log = mLogs.get(position);
      text.setText(log.getAttr(GameLog.A_BLACK_PLAYER) + "/" + 
          log.getAttr(GameLog.A_WHITE_PLAYER));
      return text;
    }
    private Context mContext;
  }

  private MyAdapter mAdapter;
  private LogLister mLogLister;
  private final ArrayList<GameLog> mLogs = new ArrayList<GameLog>();

  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.log_picker);

    mAdapter = new MyAdapter(this);
    // Use an existing ListAdapter that will map an array
    // of strings to TextViews
    setListAdapter(mAdapter);
    
    mLogLister = new LogLister(new LogLister.EventListener() {
      public void onNewGameLog(GameLog log) {
        mLogs.add(log);
        mAdapter.notifyDataSetChanged();
      }
      public void onFinish(String error) {
        ;
      }
    }, null);
    mLogLister.start();
  }
  
  @Override 
  public void onListItemClick(ListView l, View v, int position, long id) {
    if (position >= mLogs.size()) {
      Log.d(TAG, "Invalid item click: " + position);
      return;
    }
    GameLog log = mLogs.get(position);
    Intent intent = new Intent(this, ReplayGameActivity.class);
    Serializable ss = (Serializable)log;
    intent.putExtra("gameLog", ss);
    startActivity(intent);
  }
}
