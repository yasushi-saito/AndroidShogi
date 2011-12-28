// Copyright 2011 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.app.ListActivity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.net.URL;

/**
 * Class for scraping wiki.optus.nu pages and showing them as scrolling lists.
 * 
 * @author saito@google.com (Yaz Saito)
 *
 */
public class OptusViewerActivity extends ListActivity {
  private static final String TAG = "OptusViewer";
  
  private class MyAdapter extends BaseAdapter {
    private LayoutInflater mInflater;
    public MyAdapter(Context context) {
      mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }
    @Override
    public int getCount() {
      return mPlayers.length;
    }
    
    @Override
    public Object getItem(int position) {
      return (position >= mPlayers.length) ? null : mPlayers[position]; 
    }

    @Override
    public long getItemId(int position) {
      return position;
    }
    
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      TextView text;

      if (convertView == null) {
        text = (TextView)mInflater.inflate(android.R.layout.simple_list_item_1, parent, false);
      } else {
        text = (TextView)convertView;
      }
      text.setTextSize(14);
      text.setHorizontallyScrolling(true);

      if (position >= mPlayers.length) {
        text.setText("");
      } else {
        OptusParser.Player player = mPlayers[position];
        text.setText(player.name);
      }
      return text;
    }
  }
  private MyAdapter mAdapter;
  private OptusParser.Player[] mPlayers;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.game_log_list);

    mPlayers = new OptusParser.Player[0];
    mAdapter = new MyAdapter(this);
    
    ListView listView = (ListView)findViewById(android.R.id.list);
    registerForContextMenu(listView);
    
    // Use an existing ListAdapter that will map an array
    // of strings to TextViews
    setListAdapter(mAdapter);
    startListPlayers();
  }
  
  private void startListPlayers() {
    ListPlayersThread thread = new ListPlayersThread();
    thread.execute(0/*not used*/);
  }

  private class ListPlayersThread extends AsyncTask<Integer, OptusParser.Player[], String> {
    @Override
    protected String doInBackground(Integer... unused) {
      // TODO(saito): Auto-generated method stub
      try {
        URL url = new URL(OptusParser.KISI_URL);
        Log.d(TAG, "Start listing");
        publishProgress(OptusParser.listPlayers(url.openStream()));
        Log.d(TAG, "Finish listing");
      } catch (Throwable e) {
        return e.toString();
      }
      return "";
    }
    
    @Override
    protected void onProgressUpdate(OptusParser.Player[]... players_list) {
      for (OptusParser.Player[] p : players_list) {
        mPlayers = p; // TODO: just take the last list
      }
      mAdapter.notifyDataSetChanged();
    }
  }
  
  @Override 
  public void onListItemClick(ListView l, View v, int position, long id) {
    Log.d(TAG, "Click: " + String.valueOf(position));
  }
}
