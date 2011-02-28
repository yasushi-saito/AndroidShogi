// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;

/**
 * A task that scans the file system and finds saved game logs
 */
public class LogListManager {
  /**
   * Maximum number of logs that are not saved in the sdcard, but only in the summary file.
   * Exceeding this limit, oldest logs will be deleted.
   */
  private static final int MAX_IN_MEMORY_LOGS = 30;
  
  private static LogListManager mSingleton;
  public static synchronized LogListManager getSingletonInstance() {
    if (mSingleton == null) {
      mSingleton = new LogListManager();
    }
    return mSingleton;
  }

  public interface Listener {
    /**
     * Called when the request finishes
     * @param error null on success. Contains an error message otherwise.
     */
    public void onFinish(String error);
  }
  
  public static final Listener NULL_LISTENER = new Listener() {
    public void onFinish(String error) { }
  };
  
  public interface ListLogsListener extends Listener {
    /**
     * Called multiple times to report game logs found.
     */
    public void onNewGameLogs(Collection<GameLog> logs);
  }

  private static String SUMMARY_PATH = "log_summary";

  public enum Mode {
    READ_SDCARD_SUMMARY,
    RESET_SDCARD_SUMMARY,
  };

  public static class Cancellable {
    private Work mWork;
    public Cancellable(Work w) { mWork = w; }
    public synchronized void cancel() {
      mWork.cancel();
    }
  }
  
  private class Work {
    private final Listener mListener;
    private final Handler mHandler = new Handler();
    private boolean mCancelled;

    public Work(Listener listener) { mListener = listener; }
    public synchronized void cancel() { mCancelled = true; }
    public synchronized boolean isCancelled() { return mCancelled; }
    public Listener getListener() { return mListener; }
    
    public void reportFinish(String error) { 
      final class FinishReporter implements Runnable {
        String error;
        Listener listener;
        public void run() { 
          listener.onFinish(error);
        }
      }
      FinishReporter r = new FinishReporter();
      r.error = error;
      r.listener = mListener;
      mHandler.post(r);
    }
    
    public void run() { }  // to be overridden by the subclasses
  }  

  private LogListManager() { mSummary = null; mThread = null; }
  
  /**
   * @param listener Used to report download status to the caller
   * @param externalDir The directory to store the downloaded file.
   * The basename of the file will be the same as the one in the sourceUrl.
   * @param manager The system-wide download manager.
   */
  public synchronized Cancellable listLogs(
      ListLogsListener listener, 
      Context context, 
      Mode mode) {
    maybeStartBackgroundThread();
    Work w = new ListLogsWork(listener, context, mode);
    mPendingWork.add(w);
    notify();
    return new Cancellable(w);
  }

  /*
   * Schedule to add a new game "log" to the summary and save it to the sdcard.
   * The file I/Os happen in a separate thread, and this method returns before they are
   * complete.
   */
  public synchronized Cancellable addLog(
      Listener listener,
      Activity activity,
      GameLog log) {
    maybeStartBackgroundThread();
    Work w = new AddLogWork(listener, activity, log);
    mPendingWork.add(w);
    notify();
    return new Cancellable(w);
  }

  public synchronized Cancellable saveLogInSdcard(
      Listener listener, 
      Activity activity,
      GameLog log) {
    maybeStartBackgroundThread();
    Work w = new SaveLogInSdcardWork(listener, activity, log);
    mPendingWork.add(w);
    notify();
    return new Cancellable(w);
  }
  
  /**
   * Delete the given log in the background. Show a toast when done.
   */
  public synchronized Cancellable deleteLog(
      Listener listener,
      Activity activity,
      GameLog log) {
    maybeStartBackgroundThread();
    Work w = new DeleteLogWork(listener, activity, log);
    mPendingWork.add(w);
    notify();
    return new Cancellable(w);
  }
  
  // 
  // Implementation details
  //
  private static final String TAG = "ShogiLogLister";
  private Thread mThread;
  private final LinkedList<Work> mPendingWork = new LinkedList<Work>();
  private LogList mSummary;

  private static class LogList implements Serializable {
    public LogList(LogList src) {
      lastScanTimeMs = src.lastScanTimeMs;
      logs = new HashMap<String, GameLog>(src.logs);
    }
    public LogList() {
      lastScanTimeMs = -1;
      logs = new HashMap<String, GameLog>();
    }

    // The last walltime the filesystem was scanned.
    public long lastScanTimeMs;

    // maps gamelog digest -> gamelog
    public final HashMap<String, GameLog> logs;
  }

  private synchronized void maybeStartBackgroundThread() {
    if (mThread == null) {
      mThread = new Thread(
          new Runnable() { public void run() { threadBody(); } },
      "LogLister");
      mThread.start();
    }
  }

  private synchronized Work getNextWork() {
    while (mPendingWork.isEmpty()) {
      try {
        wait();
      } catch (InterruptedException e) {
      }
    }
    return mPendingWork.removeFirst();
  }

  private void threadBody() {
    for (;;) {
      getNextWork().run();
    }
  }

  private class ListLogsWork extends Work {
    private final Context mContext;
    private final Mode mMode;
    private final Handler mHandler = new Handler();

    // The constructor is called by the UI thread so that mHandler is bound to the UI thread.
    public ListLogsWork(ListLogsListener l, Context c, Mode m) {
      super(l);
      mContext = c;
      mMode = m;
    }
    
    private void publishLogs(Collection<GameLog> logs) {
      final class GameLogReporter implements Runnable {
        Collection<GameLog> logs;
        ListLogsListener listener;
        public void run() {
          listener.onNewGameLogs(logs); 
        }
      }

      GameLogReporter r = new GameLogReporter();
      r.logs = logs;
      r.listener = (ListLogsListener)getListener();
      mHandler.post(r);
    }

    /**
     * Remove from "summary" logs that are in sdcard. 
     * Logs that are not yet saved in sdcard are kept intact.
     */
    private void removeLogsInSdCard(LogList summary) {
      summary.lastScanTimeMs = -1;
      ArrayList<String> to_remove = new ArrayList<String>();
      for (Map.Entry<String, GameLog> e : summary.logs.entrySet()) {
        if (e.getValue().path() != null) {
          to_remove.add(e.getKey());
        }
      }
      for (String key : to_remove) {
        summary.logs.remove(key);
      }
    }

    @Override 
    public void run() {
      LogList summary = readSummary(mContext);
      if (mMode == Mode.RESET_SDCARD_SUMMARY) {
        removeLogsInSdCard(summary);
      }

      publishLogs(summary.logs.values());
      long scanStartTimeMs = System.currentTimeMillis();
      scanDirectory(new File("/sdcard/download"), summary);

      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      scanDirectory(new File(prefs.getString("game_log_dir", "/sdcard/ShogiGameLog")), summary);

      // TODO: don't write the summary if it hasn't changed.
      if (!isCancelled()) {
        summary.lastScanTimeMs = scanStartTimeMs;
        writeSummary(mContext, summary);
      }

      reportFinish(null);
    }

    private void scanDirectory(File downloadDir, LogList summary) {
      String[] files = downloadDir.list(new FilenameFilter(){
        public boolean accept(File dir, String filename) {
          return isHtml(filename) || isKif(filename);
        }
      });

      if (files == null || isCancelled()) return;

      for (String basename: files) {
        if (isCancelled()) return;
        File child = new File(downloadDir, basename);
        InputStream in = null;
        try {
          try {
            if (child.lastModified() >= summary.lastScanTimeMs) {
              Log.d(TAG, "Try: " + child.getAbsolutePath());
              in = new FileInputStream(child);
              GameLog log = null;
              Reader reader = null;
              if (isHtml(basename)) {
                reader = new InputStreamReader(in, "EUC_JP");
                log = GameLog.parseHtml(child, reader);
              } else {
                reader = new InputStreamReader(in);
                log = GameLog.parseKif(child, reader);
              }
              if (log != null) {
                if (summary.logs.put(log.digest(), log) == null) {
                  ArrayList<GameLog> logs = new ArrayList<GameLog>();
                  logs.add(log);
                  publishLogs(logs);
                  Log.d(TAG, "ADD: " + log.digest() + "//" + log.attr(GameLog.ATTR_BLACK_PLAYER));
                }
              }
            }
          } finally {
            if (in != null) in.close();
          }
        } catch (IOException e) {
          Log.d(TAG, child.getAbsolutePath() + ": I/O error: " + e.getMessage()); 
        } catch (ParseException e) {
          Log.d(TAG, child.getAbsolutePath() + ": KIF parse: " + e.getMessage());           
        }
      }
    }
  }

  private class AddLogWork extends Work {
    private final Activity mActivity;
    private final GameLog mLog;

    // Called by the UI thread
    public AddLogWork(Listener listener, Activity a, GameLog l) {
      super(listener);
      mActivity = a;
      mLog = l; 
    }
    
    @Override public void run() {
      LogList summary = readSummary(mActivity);
      if (summary == null) summary = new LogList();
      summary.logs.put(mLog.digest(), mLog);
      if (!isCancelled()) {
        writeSummary(mActivity, summary);
        showToast(mActivity, mActivity.getResources().getString(R.string.saved_game_log_in_memory));
      }
    }
  }

  private class SaveLogInSdcardWork extends Work {
    private final Activity mActivity;
    private GameLog mLog;

    // Called by the UI thread
    public SaveLogInSdcardWork(Listener listener, Activity activity, GameLog l) {
      super(listener);
      mActivity = activity;
      mLog = l; 
    }

    @Override public void run() {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
      File logDir = new File(prefs.getString("game_log_dir", "/sdcard/ShogiGameLog"));
      File logFile = new File(logDir, mLog.digest() + ".kif");
      try {
        saveInSdcard(mLog, logFile);
        LogList summary = readSummary(mActivity);
        if (summary == null) summary = new LogList();
        mLog = GameLog.newLog(
            mLog.getDate(),
            mLog.attrs(),
            mLog.plays(),
            logFile);
        summary.logs.put(mLog.digest(), mLog);
        writeSummary(mActivity, summary);
        showToast(mActivity, "Saved log in " + logFile.getAbsolutePath());
        reportFinish(null);
      } catch (IOException e) {
        String message = "Error saving log: " + e.getMessage();
        showToast(mActivity, message);
        reportFinish(message);
      }
    }
    
    public void saveInSdcard(GameLog log, File logFile) throws IOException {
      FileWriter out = null;
      try {
        logFile.getParentFile().mkdirs();
        out = new FileWriter(logFile);
        log.toKif(out);
      } finally {
        if (out != null) out.close();
      }
    }
  }
  
  private class DeleteLogWork extends Work {
    private final Activity mActivity;
    private final GameLog mLog;

    // Called by the UI thread
    public DeleteLogWork(Listener listener, Activity a, GameLog l) {
      super(listener);
      mActivity = a;
      mLog = l; 
    }

    @Override public void run() {
      String error = null;
      File path = mLog.path();
      if (path == null) {
        // The log hasn't been saved to the sdcard.
      } else {
        if (!path.delete()) {
          error = "Could not delete " + path.getAbsolutePath();
          showToast(mActivity, error);
        }
      }
      
      LogList summary = readSummary(mActivity);
      if (summary == null) summary = new LogList();
      summary.logs.remove(mLog.digest());
      writeSummary(mActivity, summary);
      if (path == null) {
        showToast(mActivity, "Deleted log in memory");
      } else {
        showToast(mActivity, "Deleted " + path.getAbsolutePath());
      }
      reportFinish(error);
    }
    
  }

  static private void showToast(Activity activity, String message) {
    class ShowToast implements Runnable {
      private final Activity mActivity;
      private final String mMessage;
      ShowToast(Activity a, String m) {
        mActivity = a;
        mMessage = m;
      }
      public void run() {
        Toast.makeText(mActivity, mMessage, Toast.LENGTH_SHORT).show();
      }
    }
    
    activity.runOnUiThread(new ShowToast(activity, message));
  }
  
  /**
   * 
   * TODO this method assumes that context.openFileInput(path) opens the same file for a given path for any value of "context".
   */
  private LogList readSummary(Context context) {
    if (mSummary != null) return new LogList(mSummary);
    
    mSummary = new LogList();
    FileInputStream fin = null;
    try {
      try {
        fin = context.openFileInput(SUMMARY_PATH);
        ObjectInputStream oin = new ObjectInputStream(fin);
        mSummary = (LogList)oin.readObject();
      } finally {
        if (fin != null) fin.close();
      }
    } catch (FileNotFoundException e) {
      ;
    } catch (ClassNotFoundException e) {
      Log.d(TAG, SUMMARY_PATH + ": ClassNotFoundException: " + e.getMessage());
    } catch (IOException e) {
      Log.d(TAG, SUMMARY_PATH + ": IOException: " + e.getMessage());
    }
    return mSummary;
  }

  private void removeOldInMemoryLogs(LogList summary) {
    ArrayList<GameLog> inmemory_logs = new ArrayList<GameLog>();
    for (GameLog log : summary.logs.values()) {
      if (log.path() == null) inmemory_logs.add(log);
    }
    if (inmemory_logs.size() > MAX_IN_MEMORY_LOGS) {
      Collections.sort(inmemory_logs, GameLog.SORT_BY_DATE);
      for (int i = 0; i < inmemory_logs.size() - MAX_IN_MEMORY_LOGS; ++i) {
        summary.logs.remove(inmemory_logs.get(i).digest());
      }
    }
  }
  private void writeSummary(Context context, LogList summary) {
    removeOldInMemoryLogs(summary);
    FileOutputStream fout = null;
    try {
      try {
        fout = context.openFileOutput(SUMMARY_PATH, Context.MODE_PRIVATE);
        ObjectOutputStream oout = new ObjectOutputStream(fout);
        oout.writeObject(summary);
        oout.close();
        mSummary = new LogList(summary);
      } finally {
        fout.close();
      }
    } catch (FileNotFoundException e) {
      ;
    } catch (IOException e) {
      Log.d(TAG, SUMMARY_PATH + ": IOException: " + e.getMessage());
      ;
    }
  }

  private static boolean isHtml(String basename) {
    return basename.endsWith(".html") || basename.endsWith(".htm");
  }

  private static boolean isKif(String basename) {
    return basename.endsWith(".kif");
  }


}
