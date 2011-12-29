package com.ysaito.shogi;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ListView;
import android.widget.TextView;

import java.io.InputStream;

/**
 * Class for scraping wiki.optus.nu pages and showing them as scrolling lists.
 * 
 * @author saito@google.com (Yaz Saito)
 *
 */
public class OptusPlayerListActivity extends ListActivity {
  private static final String TAG = "OptusPlayerList";
  private ExternalCacheManager mCache;
  private GenericListUpdater<OptusParser.Player> mUpdater;
  private View mProgressBar;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    boolean supportsCustomTitle = requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
    setContentView(R.layout.game_log_list);
    if (supportsCustomTitle) {
      getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.title_bar_with_progress);
      TextView titleView = (TextView)findViewById(R.id.title_bar_with_progress_title);
      titleView.setText("Player list");
      mProgressBar = findViewById(R.id.title_bar_with_progress_progress);
    } else {
      setTitle("Player list");
    }
    
    mCache = ExternalCacheManager.getInstance(getApplicationContext(), "optus");
    
    String[] url = new String[1];
    url[0] = OptusParser.KISI_URL;
    mUpdater = new GenericListUpdater<OptusParser.Player>(
        new MyEnv(),
        this,
        url,
        mCache,
        "@@player_list");
    registerForContextMenu(findViewById(android.R.id.list));
    setListAdapter(mUpdater.adapter());
    mUpdater.startListing(GenericListUpdater.MAY_READ_FROM_CACHE);
  }

  @Override 
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.optus_list_option_menu, menu);
    return true;
  }
  
  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_reload:
      mUpdater.startListing(GenericListUpdater.FORCE_RELOAD);
      return true;
    }
    return false;
  }
  
  private class MyEnv implements GenericListUpdater.Env<OptusParser.Player> {
    // All calls to getListLabel() are from one thread, so share one builder.
    final StringBuilder mBuilder = new StringBuilder();
    
    @Override 
    public String getListLabel(OptusParser.Player p) { 
      if (p == null) return "";
      
      mBuilder.setLength(0);
      mBuilder.append(p.name)
      .append(" (")
      .append(p.num_games)
      .append(")");
      return mBuilder.toString();
    }

    @Override
    public OptusParser.Player[] listObjects(InputStream in) throws Throwable { return OptusParser.listPlayers(in); }
    
    @Override public void startProgressAnimation() {
      if (mProgressBar != null) mProgressBar.setVisibility(View.VISIBLE);
    }
    
    @Override public void stopProgressAnimation() {
      if (mProgressBar != null) mProgressBar.setVisibility(View.INVISIBLE);
    }
  }
  
  @Override 
  public void onListItemClick(ListView l, View v, int position, long id) {
    OptusParser.Player player = mUpdater.getObjectAtPosition(position);
    if (player != null) {
      Log.d(TAG, "Click: " + player.name);
      Intent intent = new Intent(this, OptusGameLogListActivity.class);
      intent.putExtra("player", player);
      startActivity(intent);
    }
  }
}
