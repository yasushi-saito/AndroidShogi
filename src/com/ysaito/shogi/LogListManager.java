// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

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
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Map;

/**
 * A task that scans the file system and finds saved game logs
 */
public class LogListManager {
  private static LogListManager mSingleton;
  public static synchronized LogListManager getSingletonInstance() {
    if (mSingleton == null) {
      mSingleton = new LogListManager();
    }
    return mSingleton;
  }

  public interface EventListener {
    /**
     * Called multiple times to report progress.
     * @param message Download status
     */
    public void onNewGameLog(GameLog log);

    /**
     *  Called exactly once when download finishes. error==null on success. Else, it contains an 
     * @param error null on success. Contains an error message on error. 
     */
    public void onFinish(String error);  
  };

  public interface SaveLogEventListener {
    public void onFinish(String message);
  }
  
  private static String SUMMARY_PATH = "log_summary";

  public enum Mode {
    READ_SDCARD_SUMMARY,
    RESET_SDCARD_SUMMARY,
  };

  public static class Cancellable {
    boolean mCancelled;
    public synchronized void cancel() {
      mCancelled = true;
    }
  }
  
  private static class Work extends Cancellable {
    public synchronized boolean isCancelled() { return mCancelled; }
    public void run() { }  // to be overridden by the subclasses
  }  
  
  /**
   * @param listener Used to report download status to the caller
   * @param externalDir The directory to store the downloaded file.
   * The basename of the file will be the same as the one in the sourceUrl.
   * @param manager The system-wide download manager.
   */
  public synchronized Cancellable startListing(
      Context context, EventListener listener, Mode mode) {
    maybeStartBackgroundThread();
    Work w = new ListLogsWork(context, listener, mode);
    mPendingWork.add(w);
    notify();
    return w;
  }

  // mNewLogs may be accessed by both the UI thread and the lister thread, so
  // mark the method synchronized.
  public synchronized Cancellable addGameLog(Context context, GameLog log) {
    maybeStartBackgroundThread();
    Work w = new AddLogWork(context, log);
    mPendingWork.add(w);
    notify();
    return w;
  }

  public synchronized Cancellable saveGameLogInSdcard(
      Context context, 
      SaveLogEventListener listener, 
      GameLog log) {
    maybeStartBackgroundThread();
    Work w = new SaveLogInSdcardWork(context, listener, log);
    mPendingWork.add(w);
    notify();
    return w;
  }
  // 
  // Implementation details
  //
  private static final String TAG = "ShogiLogLister";
  private Thread mThread;
  private final LinkedList<Work> mPendingWork = new LinkedList<Work>();

  private static class LogList implements Serializable {
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

  private static class ListLogsWork extends Work {
    private final Context mContext;
    private final EventListener mListener;
    private final Mode mMode;
    private final Handler mHandler = new Handler();

    // The constructor is called by the UI thread so that mHandler is bound to the UI thread.
    public ListLogsWork(Context c, EventListener l, Mode m) {
      mContext = c;
      mListener = l;
      mMode = m;
    }
    
    private void publishLog(GameLog log) {
      final class GameLogReporter implements Runnable {
        GameLog log;
        EventListener listener;
        public void run() {
          listener.onNewGameLog(log); 
        }
      }

      GameLogReporter r = new GameLogReporter();
      r.log = log;
      r.listener = mListener;
      mHandler.post(r);
    }

    private void reportFinish(String error) {
      final class FinishReporter implements Runnable {
        String error;
        EventListener listener;
        public void run() { 
          listener.onFinish(error);
        }
      }
      FinishReporter r = new FinishReporter();
      r.listener = mListener;
      mHandler.post(r);
    }
    
    private void removeSdcardSummary(LogList summary) {
      summary.lastScanTimeMs = -1;
      for (Map.Entry<String, GameLog> e : summary.logs.entrySet()) {
        if ((e.getValue().getFlag() & GameLog.FLAG_ON_SDCARD) != 0) {
          summary.logs.remove(e.getKey());
        }
      }
    }

    @Override 
    public void run() {
      LogList summary = readSummary(mContext);
      if (summary == null) {
        summary = new LogList();
      } 
      if (mMode == Mode.RESET_SDCARD_SUMMARY) {
        removeSdcardSummary(summary);
      }

      for (GameLog log: summary.logs.values()) {
        publishLog(log);
      }
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
                  publishLog(log);
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

  private static class AddLogWork extends Work {
    private final Context mContext;
    private final GameLog mLog;

    // Called by the UI thread
    public AddLogWork(Context c, GameLog l) { 
      mContext = c; 
      mLog = l; 
    }
    
    @Override public void run() {
      LogList summary = readSummary(mContext);
      if (summary == null) summary = new LogList();
      summary.logs.put(mLog.digest(), mLog);
      if (!isCancelled()) {
        writeSummary(mContext, summary);
      }
    }
  }

  private static class SaveLogInSdcardWork extends Work {
    private final Context mContext;
    private final SaveLogEventListener mListener;
    private final GameLog mLog;
    private final Handler mHandler = new Handler();

    // Called by the UI thread
    public SaveLogInSdcardWork(Context c, SaveLogEventListener listener, GameLog l) { 
      mContext = c;
      mListener = listener;
      mLog = l; 
    }

    private void reportFinish(String error) {
      final class FinishReporter implements Runnable {
        String error;
        SaveLogEventListener listener;
        public void run() { 
          listener.onFinish(error);
        }
      }
      FinishReporter r = new FinishReporter();
      r.listener = mListener;
      mHandler.post(r);
    }

    @Override public void run() {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      File logDir = new File(prefs.getString("game_log_dir", "/sdcard/ShogiGameLog"));
      File logFile = new File(logDir, mLog.digest() + ".kif");
      try {
        saveInSdcard(mLog, logFile);
        LogList summary = readSummary(mContext);
        if (summary == null) summary = new LogList();
        mLog.setFlag(mLog.getFlag() & GameLog.FLAG_ON_SDCARD);
        summary.logs.put(mLog.digest(), mLog);
        writeSummary(mContext, summary);
        reportFinish("Saved log in " + logFile.getAbsolutePath());
      } catch (IOException e) {
        reportFinish("Error saving log: " + e.getMessage());
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
  
  private static LogList readSummary(Context context) {
    FileInputStream fin = null;
    try {
      try {
        fin = context.openFileInput(SUMMARY_PATH);
        ObjectInputStream oin = new ObjectInputStream(fin);
        return (LogList)oin.readObject();
      } finally {
        if (fin != null) fin.close();
      }
    } catch (FileNotFoundException e) {
      return null;
    } catch (ClassNotFoundException e) {
      Log.d(TAG, SUMMARY_PATH + ": ClassNotFoundException: " + e.getMessage());
      return null;
    } catch (IOException e) {
      Log.d(TAG, SUMMARY_PATH + ": IOException: " + e.getMessage());
      return null;
    }
  }

  private static void writeSummary(Context context, LogList summary) {
    FileOutputStream fout = null;
    try {
      try {
        fout = context.openFileOutput(SUMMARY_PATH, Context.MODE_PRIVATE);
        ObjectOutputStream oout = new ObjectOutputStream(fout);
        oout.writeObject(summary);
        oout.close();
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
