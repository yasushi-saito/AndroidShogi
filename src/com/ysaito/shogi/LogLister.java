// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

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
    public void onNewGameLog(GameLog log);

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
      EventListener listener,
      File externalDir) {
    mListener = listener;
    mExternalDir = externalDir;
    mTask = new ListerTask();
  }

  /**
   * Must be called once to start downloading
   * @param sourceUrl The location of the file.
   */
  public void start() {
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
  private EventListener mListener;
  private File mExternalDir;
  private ListerTask mTask;
  private String mError;

  private class ListerTask extends AsyncTask<Void, GameLog, String> {
    @Override protected String doInBackground(Void... unused) {
      scanHtmlFiles();
      return null; // null means no error
    }

    private void scanHtmlFiles() {
      File downloadDir = new File("/sdcard/download");
      String[] files = downloadDir.list(new FilenameFilter(){
        public boolean accept(File dir, String filename) {
          return filename.endsWith(".html") || filename.endsWith(".htm");
        }
      });
      if (files == null) return;
      for (String f: files) {
        File child = new File(downloadDir, f);
        try {
          Log.d(TAG, "Try: " + child.getAbsolutePath());
          InputStream in = new FileInputStream(child);
          GameLog log = GameLog.fromKifHtml(in);
          if (log != null) publishProgress(log);
        } catch (IOException e) {
          Log.d(TAG, child.getAbsolutePath() + ": I/O error: " + e.getMessage()); 
        } catch (ParseException e) {
          Log.d(TAG, child.getAbsolutePath() + ": KIF parse: " + e.getMessage());           
        }
      }
    }
    
    @Override public void onProgressUpdate(GameLog... logs) {
      Log.d(TAG, "ADD");
      for (GameLog log: logs) mListener.onNewGameLog(log);
    }

    @Override public void onPostExecute(String status) {
      Log.d(TAG, "DONE");      
      mListener.onFinish(status);
    }


    private void setError(String m) {
      Log.d(TAG, "Error: " + m);
      if (mError == null) mError = m;  // take only the first message
    }
  }
}
