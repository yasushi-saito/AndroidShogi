package com.ysaito.shogi;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ListView;
import android.widget.TextView;

import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
  private OptusParser.Player mPlayer;
  private View mProgressBar;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    boolean supportsCustomTitle = requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
    mPlayer = (OptusParser.Player)getIntent().getExtras().getSerializable("player");
    Log.d(TAG, "PLAIER=" + mPlayer.name);
    setContentView(R.layout.game_log_list);

    if (supportsCustomTitle) {
      getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.title_bar_with_progress);
      TextView titleView = (TextView)findViewById(R.id.title_bar_with_progress_title);
      titleView.setText(mPlayer.name);
      mProgressBar = findViewById(R.id.title_bar_with_progress_progress);
    } else {
      setTitle(mPlayer.name);
    }
    
    mCache = ExternalCacheManager.getInstance(getApplicationContext(), "optus");
    
    String[] urls = new String[mPlayer.hrefs.length];
    for (int i = 0; i < mPlayer.hrefs.length; ++i) {
      urls[i] = OptusParser.LOG_LIST_BASE_URL + mPlayer.hrefs[i];
    }
    mUpdater = new GenericListUpdater<OptusParser.LogRef>(
        new MyEnv(),
        this,
        urls,
        mCache,
        mPlayer.name);
    ListView v = (ListView)findViewById(android.R.id.list);
    registerForContextMenu(findViewById(android.R.id.list));
    setListAdapter(mUpdater.adapter());
    mUpdater.startListing();
  }

  private class MyEnv implements GenericListUpdater.Env<OptusParser.LogRef> {
    // All calls to getListLabel() are from one thread, so share one builder.
    final StringBuilder mBuilder = new StringBuilder();
    
    @Override 
    public String getListLabel(OptusParser.LogRef p) { 
      if (p == null) return "";
      
      mBuilder.setLength(0);
      mBuilder.append(p.date);
      if (!p.blackPlayer.equals(mPlayer.name)) {
        mBuilder.append(": ▲").append(p.blackPlayer);
      }
      if (!p.whitePlayer.equals(mPlayer.name)) {
        mBuilder.append(": △").append(p.whitePlayer);
      }
      mBuilder.append(": ")
      .append(p.tournament)
      .append(": ")
      .append(p.openingMoves);
      return mBuilder.toString();
    }

    @Override
    public OptusParser.LogRef[] listObjects(InputStream in) throws Throwable { 
      return OptusParser.listLogRefs(in); 
    }
    
    @Override public void startProgressAnimation() {
      if (mProgressBar != null) mProgressBar.setVisibility(View.VISIBLE);
    }
    
    @Override public void stopProgressAnimation() {
      if (mProgressBar != null) mProgressBar.setVisibility(View.INVISIBLE);
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
          "Doing Extreme Calculations...", 
          true);
      LogDownloadThread thread = new LogDownloadThread(this);
      thread.execute(log);
    }
  }
  
  private static String arbitraryTextToCacheKey(String k) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      digest.update(k.getBytes());
      return Util.bytesToHexText(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("MessageDigest.NoSuchAlgorithmException: " + e.getMessage());
    }
  }

  /**
   * A thread for downloading a game log from the optus web site. Once the 
   * downloading completes, start an activity to replay the game. 
   */
  private class LogDownloadThread extends AsyncTask<OptusParser.LogRef, String/*notused*/, GameLog> {
    private final Activity mActivity;
    private OptusParser.LogRef mLogRef;
    
    public LogDownloadThread(Activity activity) {
      mActivity = activity;
      mLogRef = null;
    }
        
    @Override
    protected GameLog doInBackground(OptusParser.LogRef... logRefs) {
      // The default href link will show Java applet screen.
      // Replacing display with displaytxt will show the log in KIF-format text.
      mLogRef = logRefs[0];
      String text_href = OptusParser.LOG_LIST_BASE_URL + mLogRef.href.replace("cmds=display", "cmds=displaytxt");
      
      final long now = System.currentTimeMillis();
      final String cacheKey = arbitraryTextToCacheKey(text_href);
      GameLog log = null;
      try {
        ExternalCacheManager.ReadResult r = mCache.read(cacheKey);
        if (r.obj != null) {
          Log.d(TAG, "Found cache");
          log = (GameLog)r.obj;
        }
        if (r.needRefresh) {
          URL url = new URL(text_href);
          Log.d(TAG, "Start reading " + text_href);
          log = GameLog.parseHtml(null/*no local storage*/, url.openStream());
          mCache.write(cacheKey, log);
        }
      } catch (Throwable e) {
        // TODO: show error on screen
        Log.d(TAG, "Failed to download log: " + e.toString());
      }
      return log;
    }
    
    @Override
    protected void onPostExecute(GameLog log) {
      if (mProgressDialog != null) {
        mProgressDialog.dismiss();
        mProgressDialog = null;
      }
      if (log != null) {
        Intent intent = new Intent(mActivity, ReplayGameActivity.class);
        Serializable ss = log;
        intent.putExtra("gameLog", ss);
        mActivity.startActivity(intent);
      }
    }
  }
}
