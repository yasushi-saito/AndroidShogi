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
import java.util.HashMap;

/**
 * @author saito@google.com (Your Name Here)
 *
 */
public class LogLister {
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

  /**
   * @param listener Used to report download status to the caller
   * @param externalDir The directory to store the downloaded file.
   * The basename of the file will be the same as the one in the sourceUrl.
   * @param manager The system-wide download manager.
   */
  public LogLister(
      Context context,
      EventListener listener) {
    mContext = context;
    mListener = listener;
  }

  private static String SUMMARY_PATH = "log_summary";
  
  public enum Mode {
    // Read the log summary stored in SUMMARY_PATH
    READ_SAVED_SUMMARY,
    
    // Do not read the summary SUMMARY_PATH. Read and parse files
    // in /download and 
    CLEAR_SAVED_SUMMARY,
  };
  
  /**
   * Must be called once to start downloading
   * @param sourceUrl The location of the file.
   */
  public void start(Mode mode) {
    if (mTask != null) {
      mTask.cancel(true);
    }
    mTask = new ListerTask(mode);
    mTask.execute();
  }

  /**
   * Must be called to stop the download thread.
   */
  public void destroy() {
    Log.d(TAG, "Destroy");
    mTask.cancel(false);
  }

  // 
  // Implementation details
  //
  private static final String TAG = "ShogiLogLister";
  private final Context mContext;
  private final EventListener mListener;
  private ListerTask mTask;
  private String mError;

  private static class LogSummary implements Serializable {
    public LogSummary() {
      lastScanTimeMs = 0;
      logs = new HashMap<String, GameLog>();
    }
    
    public long lastScanTimeMs;
    
    // maps gamelog digest -> gamelog
    public final HashMap<String, GameLog> logs;
  }

  private class ListerTask extends AsyncTask<Void, GameLog, String> {
    private final Mode mMode;
    
    public ListerTask(Mode mode) {
      super();
      mMode = mode;
    }
    
    @Override protected String doInBackground(Void... unused) {
      LogSummary summary = null;
      if (mMode == Mode.READ_SAVED_SUMMARY) summary = readSummary();
      if (summary == null) {
        summary = new LogSummary();
      } else {
        for (GameLog log: summary.logs.values()) {
          publishProgress(log);
        }
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
                Log.d(TAG, "ADD: " + log.digest() + "//" + log.attr(GameLog.A_BLACK_PLAYER));
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
