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
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

  public static class UndoToken {
    public enum Op {  // the operation to undo 
      DELETED  // the log was deleted (the undo will restore the log)
    }
    public UndoToken(Op o, GameLog l) { op = o; log = l; }
    public final Op op;
    public final GameLog log;
  }
  
  public interface TrivialListener {
    public void onFinish();
  }
  
  public interface DeleteLogListener {
    public void onFinish(UndoToken undoToken);
  }
  
  public interface ListLogsListener {
    /**
     * Called multiple times to report game logs found.
     */
    public void onNewGameLogs(Collection<GameLog> logs);
    public void onFinish();
  }

  private static String SUMMARY_PATH = "log_summary";

  public enum Mode {
    READ_SDCARD_SUMMARY,
    RESET_SDCARD_SUMMARY,
  }

  private class Work {
    private final Handler mHandler = new Handler();
    
    public Work() { }
    public void post(Runnable r) { mHandler.post(r); }
    public void run() { }  // to be overridden by the subclasses
  }  

  private LogListManager() { mSummary = null; mThread = null; }
  
  /**
   * @param listener Used to report download status to the caller
   * @param externalDir The directory to store the downloaded file.
   * The basename of the file will be the same as the one in the sourceUrl.
   * @param manager The system-wide download manager.
   */
  public synchronized void listLogs(
      ListLogsListener listener, 
      Context context, 
      Mode mode) {
    maybeStartBackgroundThread();
    Work w = new ListLogsWork(listener, context, mode);
    mPendingWork.add(w);
    notify();
  }

  /*
   * Schedule to add a new game "log" to the summary and save it to the sdcard.
   * The file I/Os happen in a separate thread, and this method returns before they are
   * complete.
   */
  public synchronized void addLog(
      Activity activity,
      GameLog log) {
    maybeStartBackgroundThread();
    Work w = new AddLogWork(activity, log);
    mPendingWork.add(w);
    notify();
  }

  public synchronized void saveLogInSdcard(
      TrivialListener listener, 
      Activity activity,
      GameLog log) {
    maybeStartBackgroundThread();
    Work w = new SaveLogInSdcardWork(listener, activity, log);
    mPendingWork.add(w);
    notify();
  }
  
  /**
   * Delete the given log in the background. Show a toast when done.
   */
  public synchronized void deleteLog(
      DeleteLogListener listener,
      Activity activity,
      GameLog log) {
    maybeStartBackgroundThread();
    Work w = new DeleteLogWork(listener, activity, log);
    mPendingWork.add(w);
    notify();
  }
  
  public synchronized void undo(
      TrivialListener listener,
      Activity activity,
      UndoToken undo) {
    maybeStartBackgroundThread();
    Work w = new UndoWork(listener, activity, undo);
    mPendingWork.add(w);
    notify();
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
    private final ListLogsListener mListener;
    private final Context mContext;
    private final Mode mMode;

    // The constructor is called by the UI thread so that mHandler is bound to the UI thread.
    public ListLogsWork(ListLogsListener l, Context c, Mode m) {        
      mListener = l;
      mContext = c;
      mMode = m;
    }
    
    private void publishLogs(Collection<GameLog> logs) {
      final class GameLogReporter implements Runnable {
        Collection<GameLog> l;
        public void run() {
          mListener.onNewGameLogs(l); 
        }
      }

      GameLogReporter r = new GameLogReporter();
      r.l = logs;
      post(r);
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
      scanDirectory(getLogDir(mContext), summary);

      // TODO: don't write the summary if it hasn't changed.
      summary.lastScanTimeMs = scanStartTimeMs;
      writeSummary(mContext, summary);

      final class FinishReporter implements Runnable {
        ListLogsListener listener;
        public void run() { listener.onFinish(); }
      }
      FinishReporter r = new FinishReporter();
      r.listener = mListener;
      post(r);
    }

    private void scanDirectory(File downloadDir, LogList summary) {
      String[] files = downloadDir.list(new FilenameFilter(){
        public boolean accept(File dir, String filename) {
          return isHtml(filename) || isKif(filename);
        }
      });

      if (files == null) return;

      for (String basename: files) {
        File child = new File(downloadDir, basename);
        try {
          InputStream in = null;
          try {
            if (child.lastModified() >= summary.lastScanTimeMs) {
              in = new FileInputStream(child);
              GameLog log = null;
              if (isHtml(basename)) {
                log = GameLog.parseHtml(child, in);
              } else {
                log = GameLog.parseKif(child, in);
              }
              if (log != null) {
                if (summary.logs.put(log.digest(), log) == null) {
                  ArrayList<GameLog> logs = new ArrayList<GameLog>();
                  logs.add(log);
                  publishLogs(logs);
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
    public AddLogWork(Activity a, GameLog l) {
      mActivity = a;
      mLog = l; 
    }
    
    @Override public void run() {
      LogList summary = readSummary(mActivity);
      if (summary == null) summary = new LogList();
      summary.logs.put(mLog.digest(), mLog);
      writeSummary(mActivity, summary);
      showToast(mActivity, mActivity.getResources().getString(R.string.saved_game_log_in_memory));
    }
  }

  private class SaveLogInSdcardWork extends Work {
    private TrivialListener mListener;
    private final Activity mActivity;
    private GameLog mLog;

    final class FinishReporter implements Runnable {
      public void run() { mListener.onFinish(); }
    }
    
    // Called by the UI thread
    public SaveLogInSdcardWork(TrivialListener listener, Activity activity, GameLog l) {
      mListener = listener;
      mActivity = activity;
      mLog = l; 
    }

    @Override public void run() {
      File logFile = new File(getLogDir(mActivity), mLog.digest() + ".kif");
      try {
        saveInSdcard(mLog, logFile);
        
        // Remove the in-memory log from the summary. The sdcard version of the log 
        // will be added back in ListLogs later.
        LogList summary = readSummary(mActivity);
        if (summary != null) {
          summary.logs.remove(mLog.digest());
          writeSummary(mActivity, summary);
        }
        
        showToast(mActivity, String.format(mActivity.getResources().getString(R.string.saved_log_in_sdcard), logFile.getAbsolutePath()));
        post(new FinishReporter());
      } catch (IOException e) {
        String message = "Error saving log: " + e.getMessage();
        showToast(mActivity, message);
        post(new FinishReporter());
      }
    }
    
    public void saveInSdcard(GameLog log, File logFile) throws IOException {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
      FileOutputStream stream = null; 
      try {
        logFile.getParentFile().mkdirs();
        stream = new FileOutputStream(logFile);
        log.toKif(stream, prefs.getString("log_save_format", "kif_dos"));
      } finally {
        if (stream != null) stream.close();
      }
    }
  }
  
  private class UndoWork extends Work {
    private final TrivialListener mListener;
    private final Activity mActivity;
    private final UndoToken mUndo;

    final class FinishReporter implements Runnable {
      public void run() { mListener.onFinish(); }
    }
    
    public UndoWork(TrivialListener l, Activity a, UndoToken u) {
      mListener = l;
      mActivity = a;
      mUndo = u; 
    }
    
    @Override public void run() {
      String error = null;
      File path = mUndo.log.path();
      if (path == null) {
        // The log hasn't been saved to the sdcard.
      } else {
        File trashPath = getTrashPath(mActivity, mUndo.log);
        if (!trashPath.renameTo(path)) {
          error = "Could not restore " + path.getAbsolutePath();
          showToast(mActivity, error);
          post(new FinishReporter());
          return;
        } 
      }
      
      LogList summary = readSummary(mActivity);
      if (summary == null) summary = new LogList();
      summary.logs.put(mUndo.log.digest(), mUndo.log);
      writeSummary(mActivity, summary);
      showToast(mActivity, mActivity.getResources().getString(R.string.restored_log));
      post(new FinishReporter());
    }
  }
  
  private class DeleteLogWork extends Work {
    private final DeleteLogListener mListener;
    private final Activity mActivity;
    private final GameLog mLog;

    final class FinishReporter implements Runnable {
      UndoToken mUndoToken;
      FinishReporter(UndoToken undoToken) { mUndoToken = undoToken; }
      public void run() { mListener.onFinish(mUndoToken); }
    }
    
    // Called by the UI thread
    public DeleteLogWork(DeleteLogListener listener, Activity a, GameLog l) {
      mListener = listener;
      mActivity = a;
      mLog = l; 
    }

    @Override public void run() {
      String error = null;
      File path = mLog.path();
      if (path == null) {
        // The log hasn't been saved to the sdcard.
      } else {
        File trashPath = getTrashPath(mActivity, mLog);
        if (!path.renameTo(trashPath)) {
          trashPath.getParentFile().mkdirs();
          trashPath.delete();
          if (!path.renameTo(trashPath)) {
            error = "Could not delete " + path.getAbsolutePath();
            showToast(mActivity, error);
            post(new FinishReporter(null));
            return;
          }
        }
      }
      
      LogList summary = readSummary(mActivity);
      if (summary == null) summary = new LogList();
      summary.logs.remove(mLog.digest());
      writeSummary(mActivity, summary);
      if (path == null) {
        showToast(mActivity, mActivity.getResources().getString(R.string.deleted_log_in_memory));
      } else {
        showToast(mActivity, String.format(mActivity.getResources().getString(R.string.deleted_log_in_sdcard), path.getAbsolutePath()));
      }
      post(new FinishReporter(new UndoToken(UndoToken.Op.DELETED, mLog)));
    }
  }

  static File getLogDir(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    return new File(prefs.getString("game_log_dir", "/sdcard/ShogiGameLog"));
  }

  static File getTrashPath(Context context, GameLog mLog) {
    return new File(new File(getLogDir(context), "trash"), mLog.digest());
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
