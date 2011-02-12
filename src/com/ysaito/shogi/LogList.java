// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

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
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author saito@google.com (Your Name Here)
 *
 */
public class LogList {
  public interface EventListener {
    /**
     * Called multiple times to report progress.
     * @param message Download status
     */
    public void onNewGameLogs(GameLog[] logs);

    /**
     *  Called exactly once when download finishes. error==null on success. Else, it contains an 
     * @param error null on success. Contains an error message on error. 
     */
    public void onFinish(String error);  
  };

  private static String SUMMARY_PATH = "log_summary";
  
  public enum Mode {
    // Read the log summary stored in SUMMARY_PATH
    READ_SAVED_SUMMARY,
    
    // Do not read the summary SUMMARY_PATH. Read and parse files
    // in /download and 
    CLEAR_SAVED_SUMMARY,
  };
  
  /**
   * @param listener Used to report download status to the caller
   * @param externalDir The directory to store the downloaded file.
   * The basename of the file will be the same as the one in the sourceUrl.
   * @param manager The system-wide download manager.
   */
  public static void startListing(Context context, EventListener listener, Mode mode) {
    if (mTask != null) {
      mTask.cancel(true);
    }
    mTask = new ListerTask(context, listener, mode);
    mTask.execute();
  }

  // mNewLogs may be accessed by both the UI thread and the lister thread, so
  // mark the method synchronized.
  public static synchronized void addGameLog(GameLog log) {
    if (mNewLogs == null) mNewLogs = new ArrayList<GameLog>();
    mNewLogs.add(log);
  }
  
  private static synchronized ArrayList<GameLog> getAndClearGameLogs() {
    ArrayList<GameLog> logs = mNewLogs;
    mNewLogs = null;
    return logs;
  }
  
  /**
   * Must be called to stop the download thread.
   */
  public static void stopListing() {
    Log.d(TAG, "Destroy");
    if (mTask != null) mTask.cancel(false);
  }

  // 
  // Implementation details
  //
  private static final String TAG = "ShogiLogLister";
  private static ListerTask mTask;

  // The accesses to the following variable must be synchronized.
  private static ArrayList<GameLog> mNewLogs;
    
  private static class LogSummary implements Serializable {
    public LogSummary() {
      lastScanTimeMs = 0;
      logs = new HashMap<String, GameLog>();
    }
    
    public long lastScanTimeMs;
    
    // maps gamelog digest -> gamelog
    public final HashMap<String, GameLog> logs;
  }

  private static class ListerTask extends AsyncTask<Void, GameLog, String> {
    private final Context mContext;
    private final EventListener mListener;
    private final Mode mMode;
    private String mError;
    
    public ListerTask(Context context, EventListener listener, Mode mode) {
      super();
      mContext = context;
      mListener = listener;
      mMode = mode;
    }
    
    @Override protected String doInBackground(Void... unused) {
      LogSummary summary = null;
      if (mMode == Mode.READ_SAVED_SUMMARY) summary = readSummary();
      if (summary == null) {
        summary = new LogSummary();
      } 
      ArrayList<GameLog> newLogs = getAndClearGameLogs();
      if (newLogs != null) {
        for (GameLog log: newLogs) summary.logs.put(log.digest(), log);
      }
      for (GameLog log: summary.logs.values()) {
        publishProgress(log);
      }
      long scanStartTimeMs = System.currentTimeMillis();
      scanHtmlFiles(summary);
      summary.lastScanTimeMs = scanStartTimeMs;
      
      // TODO: don't write the summary if it hasn't changed.
      writeSummary(summary);
      return null; // null means no error
    }

    private LogSummary readSummary() {
      FileInputStream fin = null;
      try {
        try {
          fin = mContext.openFileInput(SUMMARY_PATH);
          ObjectInputStream oin = new ObjectInputStream(fin);
          return (LogSummary)oin.readObject();
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
    
    private void writeSummary(LogSummary summary) {
      FileOutputStream fout = null;
      try {
        try {
          fout = mContext.openFileOutput(SUMMARY_PATH, Context.MODE_PRIVATE);
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
    private void scanHtmlFiles(LogSummary summary) {
      File downloadDir = new File("/sdcard/download");
      String[] files = downloadDir.list(new FilenameFilter(){
        public boolean accept(File dir, String filename) {
          return filename.endsWith(".html") || filename.endsWith(".htm");
        }
      });
      if (files == null || isCancelled()) return;
      for (String f: files) {
        if (isCancelled()) return;
        File child = new File(downloadDir, f);
        try {
          if (true || child.lastModified() >= summary.lastScanTimeMs) {
            Log.d(TAG, "Try: " + child.getAbsolutePath());
            InputStream in = new FileInputStream(child);
            GameLog log = GameLog.parseHtml(in);
            if (log != null) {
              if (summary.logs.put(log.digest(), log) == null) {
                publishProgress(log);
                Log.d(TAG, "ADD: " + log.digest() + "//" + log.attr(GameLog.ATTR_BLACK_PLAYER));
              }
            }
          }
        } catch (IOException e) {
          Log.d(TAG, child.getAbsolutePath() + ": I/O error: " + e.getMessage()); 
        } catch (ParseException e) {
          Log.d(TAG, child.getAbsolutePath() + ": KIF parse: " + e.getMessage());           
        }
      }
    }
    
    @Override public void onProgressUpdate(GameLog... logs) {
      if (isCancelled()) return;
      mListener.onNewGameLogs(logs);
    }

    @Override public void onPostExecute(String status) {
      if (isCancelled()) return;
      mListener.onFinish(status);
    }


    private void setError(String m) {
      Log.d(TAG, "Error: " + m);
      if (mError == null) mError = m;  // take only the first message
    }
  }
}
