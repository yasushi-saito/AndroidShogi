package com.ysaito.shogi;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
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
public class OptusGameLogListActivity extends ListActivity {
  private static final String TAG = "OptusGameLogList";
  private ExternalCacheManager mCache;
  private GenericListUpdater<OptusParser.LogRef> mUpdater;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    boolean supportsCustomTitle = requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
    mCache = ExternalCacheManager.getInstance(getApplicationContext());
    setContentView(R.layout.game_log_list);
    
    final Bundle bundle = getIntent().getExtras();
    final OptusParser.Player player = (OptusParser.Player)bundle.getSerializable("player");

    if (player != null) {
      setTitle(supportsCustomTitle, player.name);
      mUpdater = new GenericListUpdater<OptusParser.LogRef>(
          new PlayerEnv(player), this, player.name,
          (ProgressBar)findViewById(R.id.title_bar_with_progress_progress)/*could be null*/);
    } else {
      final OptusParser.SearchParameters mSearch = 
          (OptusParser.SearchParameters)bundle.getSerializable("search");
      setTitle(supportsCustomTitle, "query");
      mUpdater = new GenericListUpdater<OptusParser.LogRef>(
          new SearchEnv(mSearch), this, null,
          (ProgressBar)findViewById(R.id.title_bar_with_progress_progress)/*could be null*/);
    }
    setListAdapter(mUpdater.adapter());
    mUpdater.startListing(GenericListUpdater.MAY_READ_FROM_CACHE);
  }

  @Override 
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.optus_game_log_list_option_menu, menu);
    return true;
  }
  
  public static final Comparator<OptusParser.LogRef> BY_DATE = new Comparator<OptusParser.LogRef>() {
    public int compare(OptusParser.LogRef l1, OptusParser.LogRef l2) {
      return l1.date.compareTo(l2.date);
    }
    @Override 
    public boolean equals(Object o) { return o == this; }
  };

  public static final Comparator<OptusParser.LogRef> BY_TOURNAMENT = new Comparator<OptusParser.LogRef>() {
    public int compare(OptusParser.LogRef l1, OptusParser.LogRef l2) {
      return l1.tournament.compareTo(l2.tournament);
    }
    @Override 
    public boolean equals(Object o) { return o == this; }
  };
  
  public static final Comparator<OptusParser.LogRef> BY_OPENING_MOVES = new Comparator<OptusParser.LogRef>() {
    public int compare(OptusParser.LogRef l1, OptusParser.LogRef l2) {
      return l1.openingMoves.compareTo(l2.openingMoves);
    }
    @Override 
    public boolean equals(Object o) { return o == this; }
  };
  
  @Override public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_reload:
      mUpdater.startListing(GenericListUpdater.FORCE_RELOAD);
      return true;
    case R.id.menu_sort_by_date:
      mUpdater.sort(BY_DATE);
      return true;
    case R.id.menu_sort_by_tournament:
      mUpdater.sort(BY_TOURNAMENT);
      return true;
    case R.id.menu_sort_by_opening_moves:
      mUpdater.sort(BY_OPENING_MOVES);
      return true;
    }
    return false;
  }

  private void setTitle(boolean supportsCustomTitle, String title) {
    if (supportsCustomTitle) {
      getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.title_bar_with_progress);
      TextView titleView = (TextView)findViewById(R.id.title_bar_with_progress_title);
      titleView.setText(title);
    } else {
      setTitle(title);
    }
  }

  private static final String logRefToString(OptusParser.LogRef l, String thisPlayerName) {
    if (l == null) return "";
      
    final StringBuilder b = new StringBuilder();
    b.setLength(0);
    b.append(l.date);
    if (thisPlayerName == null || !l.blackPlayer.equals(thisPlayerName)) {
      b.append(": ▲").append(l.blackPlayer);
    }
    if (thisPlayerName == null || !l.whitePlayer.equals(thisPlayerName)) {
      b.append(": △").append(l.whitePlayer);
    }
    b.append(": ").append(l.tournament).append(": ").append(l.openingMoves);
    return b.toString();
  }
  
  /**
   * An environment object for fetching the game logs for particular, pre-compiled player pages.
   */
  private class PlayerEnv implements GenericListUpdater.Env<OptusParser.LogRef> {
    PlayerEnv(OptusParser.Player p) { mPlayer = p; }
    private final OptusParser.Player mPlayer;
    
    @Override 
    public String getListLabel(OptusParser.LogRef l) { 
      return logRefToString(l, mPlayer.name); 
    }

    @Override
    public int numStreams() { return mPlayer.hrefs.length; }
    
    @Override
    public OptusParser.LogRef[] readNthStream(int index) throws Throwable {
      return OptusParser.listLogRefs(mPlayer.hrefs[index]);
    }
  }

  /**
   * An environment object for issuing db query on the optus web page.
   */
  private class SearchEnv implements GenericListUpdater.Env<OptusParser.LogRef> {
    SearchEnv(OptusParser.SearchParameters p) { mSearch = p; }
    private final OptusParser.SearchParameters mSearch;
    
    @Override 
    public String getListLabel(OptusParser.LogRef l) { 
      return logRefToString(l, null);
    }

    @Override
    public int numStreams() { return 1; }
    
    @Override
    public OptusParser.LogRef[] readNthStream(int index) throws Throwable {
      return OptusParser.runQuery(mSearch);
    }
  }
  
  private ProgressDialog mProgressDialog = null;
  
  @Override 
  public void onListItemClick(ListView l, View v, int position, long id) {
    OptusParser.LogRef log = mUpdater.getObjectAtPosition(position);
    if (log!= null) {
      Log.d(TAG, "Click: " + log.tournament + ": ref=" + log.href);
      mProgressDialog = ProgressDialog.show(
          this,
          "",
          getResources().getString(R.string.fetching_game_log),
          true);
      LogDownloadThread thread = new LogDownloadThread(this);
      thread.execute(log);
    }
  }
  
  private static class DownloadResult {
    public GameLog log;
    String error;  // null if ok
  }
  
  /**
   * A thread for downloading a game log from the optus web site. Once the 
   * downloading completes, start an activity to replay the game. 
   */
  private class LogDownloadThread extends AsyncTask<OptusParser.LogRef, String/*notused*/, DownloadResult> {
    private final Activity mActivity;
    private OptusParser.LogRef mLogRef;
    
    public LogDownloadThread(Activity activity) {
      mActivity = activity;
      mLogRef = null;
    }
        
    @Override
    protected DownloadResult doInBackground(OptusParser.LogRef... logRefs) {
      // The default href link will show Java applet screen.
      // Replacing display with displaytxt will show the log in KIF-format text.
      mLogRef = logRefs[0];
      String text_href = OptusParser.LOG_LIST_BASE_URL + mLogRef.href.replace("cmds=display", "cmds=displaytxt");
      
      final String cacheKey = text_href;
      DownloadResult dr = new DownloadResult();
      try {
        ExternalCacheManager.ReadResult r = mCache.read(cacheKey);
        if (r.obj != null) {
          Log.d(TAG, "Found cache");
          dr.log = (GameLog)r.obj;
        }
        if (r.needRefresh) {
          URL url = new URL(text_href);
          Log.d(TAG, "Start reading " + text_href);
          dr.log = GameLog.parseHtml(null/*no local storage*/, url.openStream());
          mCache.write(cacheKey, dr.log);
        }
      } catch (Throwable e) {
        Log.d(TAG, "Failed to download log: " + e.getMessage());
        dr.error = "Failed to download log: " + e.getMessage();
      }
      return dr;
    }
    
    @Override
    protected void onPostExecute(DownloadResult dr) {
      if (mProgressDialog != null) {
        mProgressDialog.dismiss();
        mProgressDialog = null;
      }
      if (dr.error != null) Util.showErrorDialog(getBaseContext(), dr.error);
      if (dr.log != null) {
        Intent intent = new Intent(mActivity, ReplayGameActivity.class);
        intent.putExtra("gameLog", dr.log);
        mActivity.startActivity(intent);
      }
    }
  }
}