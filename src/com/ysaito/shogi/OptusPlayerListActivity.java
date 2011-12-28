// Copyright 2011 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.app.ListActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import java.io.InputStream;

/**
 * Class for scraping wiki.optus.nu pages and showing them as scrolling lists.
 * 
 * @author saito@google.com (Yaz Saito)
 *
 */
public class OptusPlayerListActivity extends ListActivity {
  private static final String TAG = "OptusViewer";
  private ExternalCacheManager mCache;
  private GenericListUpdater<OptusParser.Player> mUpdater;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.game_log_list);
    mCache = new ExternalCacheManager(getApplicationContext(), "optus");
    mUpdater = new GenericListUpdater<OptusParser.Player>(
        new MyEnv(),
        getApplicationContext(),
        OptusParser.KISI_URL,
        mCache,
        "@@player_list");
    registerForContextMenu((ListView)findViewById(android.R.id.list));
    setListAdapter(mUpdater.adapter());
    mUpdater.startListing();
  }

  private class MyEnv implements GenericListUpdater.Env<OptusParser.Player> {
    @Override 
    public String getListLabel(OptusParser.Player p) { return (p == null) ? "" : p.name; }

    @Override
    public OptusParser.Player[] listObjects(InputStream in) throws Throwable { return OptusParser.listPlayers(in); }
  }
  
  @Override 
  public void onListItemClick(ListView l, View v, int position, long id) {
    OptusParser.Player player = mUpdater.getObjectAtPosition(position);
    if (player != null) {
      Log.d(TAG, "Click: " + player.name);
    }
  }
}
