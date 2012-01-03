package com.ysaito.shogi;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import java.net.URL;
import java.util.Comparator;

/**
 * Class for scraping wiki.optus.nu pages and showing them as scrolling lists.
 * 
 * @author saito@google.com (Yaz Saito)
 *
 */
public class OptusGameLogListActivity extends GenericListActivity<OptusParser.LogRef> {
  private static final String TAG = "OptusGameLogList";
  private ExternalCacheManager mCache;

  OptusParser.Player mPlayer;
  OptusParser.SearchParameters mSearch;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mCache = ExternalCacheManager.getInstance(getApplicationContext());
    
    final Bundle bundle = getIntent().getExtras();
    final OptusParser.LogRef tmpArray[] = new OptusParser.LogRef[0];
    
    mPlayer = (OptusParser.Player)bundle.getSerializable("player");
    mSearch = (OptusParser.SearchParameters)bundle.getSerializable("search");
    
    String title, cacheKey;
    if (mPlayer != null) {
      title = cacheKey = mPlayer.name;
    } else {
      title = getResources().getString(R.string.query_result);
      cacheKey = mSearch.toString(); /*cache key*/
    }
    
    initialize(
        cacheKey,
        ExternalCacheManager.MAX_STATIC_PAGE_CACHE_STALENESS_MS,
        title, tmpArray);
    startListing(GenericListActivity.MAY_READ_FROM_CACHE);
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
      startListing(GenericListActivity.FORCE_RELOAD);
      return true;
    case R.id.menu_sort_by_date:
      setSorter(BY_DATE);
      return true;
    case R.id.menu_sort_by_tournament:
      setSorter(BY_TOURNAMENT);
      return true;
    case R.id.menu_sort_by_opening_moves:
      setSorter(BY_OPENING_MOVES);
      return true;
    }
    return false;
  }

  public String getListLabel(OptusParser.LogRef l) { 
    if (l == null) return "";
      
    final StringBuilder b = new StringBuilder();
    b.setLength(0);
    b.append(l.date);
    if (mPlayer== null || !l.blackPlayer.equals(mPlayer.name)) {
      b.append(": ▲").append(l.blackPlayer);
    }
    if (mPlayer== null || !l.whitePlayer.equals(mPlayer.name)) {
      b.append(": △").append(l.whitePlayer);
    }
    b.append(": ").append(l.tournament).append(": ").append(l.openingMoves);
    return b.toString();
  }
  
  @Override
  public int numStreams() { 
    if (mPlayer != null) return mPlayer.hrefs.length; 
    return 1;
  }
  
  @Override
  public OptusParser.LogRef[] readNthStream(int index) throws Throwable {
    if (mPlayer != null) {
      return OptusParser.listLogsForPlayer(mPlayer.hrefs[index]);
    } else { 
      return OptusParser.runSearch(mSearch);
    }
  }
  
  private ProgressDialog mProgressDialog = null;
  
  @Override 
  public void onListItemClick(ListView l, View v, int position, long id) {
    OptusParser.LogRef log = getObjectAtPosition(position);
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
        ExternalCacheManager.ReadResult r = mCache.read(
            cacheKey, 
            ExternalCacheManager.MAX_STATIC_PAGE_CACHE_STALENESS_MS);
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
      if (dr.error != null) Util.showErrorDialog(mActivity, dr.error);
      if (dr.log != null) {
        Intent intent = new Intent(mActivity, ReplayGameActivity.class);
        intent.putExtra("gameLog", dr.log);
        mActivity.startActivity(intent);
      }
    }
  }
}
