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
import android.widget.ProgressBar;
import android.widget.TextView;

import java.net.URL;
import java.util.Comparator;

/**
 * Class for scraping wiki.optus.nu pages and showing them as scrolling lists.
 * 
 * @author saito@google.com (Yaz Saito)
 *
 */
public class OptusPlayerListActivity extends ListActivity {
  private static final String TAG = "OptusPlayerList";
  private GenericListUpdater<OptusParser.Player> mUpdater;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    boolean supportsCustomTitle = requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
    setContentView(R.layout.game_log_list);
    ProgressBar progressBar = null;
    if (supportsCustomTitle) {
      getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.title_bar_with_progress);
      TextView titleView = (TextView)findViewById(R.id.title_bar_with_progress_title);
      titleView.setText("Player list");
      progressBar = (ProgressBar)findViewById(R.id.title_bar_with_progress_progress);
    } else {
      setTitle("Player list");
    }
    
    mUpdater = new GenericListUpdater<OptusParser.Player>(
        new MyEnv(OptusParser.KISI_URL), this, "@@player_list", progressBar);
    setListAdapter(mUpdater.adapter());
    mUpdater.startListing(GenericListUpdater.MAY_READ_FROM_CACHE);
  }

  @Override 
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.optus_player_list_option_menu, menu);
    return true;
  }
  
  public static final Comparator<OptusParser.Player> BY_LIST_ORDER = new Comparator<OptusParser.Player>() {
    public int compare(OptusParser.Player p1, OptusParser.Player p2) {
      return p1.listOrder - p2.listOrder;
    }
    @Override 
    public boolean equals(Object o) { return o == this; }
  };
  
  public static final Comparator<OptusParser.Player> BY_NUMBER_OF_GAMES = new Comparator<OptusParser.Player>() {
    public int compare(OptusParser.Player p1, OptusParser.Player p2) {
      // Place players with many games first
      return -(p1.numGames - p2.numGames);
    }
    @Override 
    public boolean equals(Object o) { return o == this; }
  };
  
  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_reload:
      mUpdater.startListing(GenericListUpdater.FORCE_RELOAD);
      return true;
    case R.id.menu_sort_by_player:
      mUpdater.sort(BY_LIST_ORDER);
      return true;
    case R.id.menu_sort_by_number_of_games:
      mUpdater.sort(BY_NUMBER_OF_GAMES);
      return true;
    }
    return false;
  }
  
  private class MyEnv implements GenericListUpdater.Env<OptusParser.Player> {
    MyEnv(String url) { mUrl = url; }
    
    @Override 
    public String getListLabel(OptusParser.Player p) { 
      if (p == null) return "";
      
      mBuilder.setLength(0);
      mBuilder.append(p.name)
      .append(" (")
      .append(p.numGames)
      .append("局)");
      return mBuilder.toString();
    }

    @Override
    public int numStreams() { return 1; }
    
    @Override
    public OptusParser.Player[] readNthStream(int index) throws Throwable { 
      URL url = new URL(mUrl);
      return OptusParser.listPlayers(url.openStream()); 
    }
    
    // All calls to getListLabel() are from one thread, so share one builder.
    private final StringBuilder mBuilder = new StringBuilder();
    private final String mUrl;
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
