// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.app.DownloadManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author saito@google.com (Your Name Here)
 *
 * Helper class for downloading large data files (fv.bin etc) from the network.
 * 
 * Downloading runs in a separate thread. The thread will communicate its status
 * through a Handler. Each message sent to the handler is of type Status.
 * 
 * While download or zip extraction is ongoing, the thread will repeatedly sends
 * Messages with state DOWNLOADING or EXTRACTING. If an error happens, or 
 * download+extraction finishes, the thread will send the final Message with state 
 * either SUCCESS or ERROR.
 * 
 */
public class BonanzaDownloader {
  public static final String[] REQUIRED_FILES = {
    "book.bin", "fv.bin", "hash.bin"
  };
  public static final int DOWNLOADING = 1; 
  public static final int EXTRACTING = 2;
  public static final int SUCCESS = 3;
  public static final int ERROR = 4;
  
  /** Status reported by the downloader. It is packed in the "status" value of
   * Message.getData()
   */
  public static class Status implements Serializable {
    int state;  // one of the above constants
    String message;
    
    @Override public String toString() {
      return "state=" + state + " message=" + message;
    }
  }
  
  /**
   * @param handler Used to report download status to the caller
   * @param externalDir The directory to store the downloaded file.
   * The basename of the file will be the same as the one in the sourceUrl.
   * @param sourceUrl The location of the file.
   * @param manager The system-wide download manager.
   */
  public BonanzaDownloader(
      Handler handler, 
      File externalDir,
      String sourceUrl,
      DownloadManager manager) {
    mHandler = handler;
    mExternalDir = externalDir;
    mDownloadManager = manager;
    mThread = new DownloadThread(sourceUrl);
  }
  
  /**
   * Must be called once to start downloading
   */
  public void start() {
    mThread.start();
  }
  
  /**
   * Must be called to stop the download thread.
   */
  public void destroy() {
    Log.d(TAG, "Destroy");
    mDestroyed = true;
  }
  
  /**
   * See if all the files required to run Bonanza are present in externalDir.
   */
  public static boolean hasRequiredFiles(File externalDir) {
    for (String basename: REQUIRED_FILES) {
      File file = new File(externalDir, basename);
      if (!file.exists()) return false;
    }
    return true;
  }
  
  // 
  // Implementation details
  //
  private static final String TAG = "ShogiDownload";
  private Handler mHandler;
  private File mExternalDir;
  private DownloadManager mDownloadManager;
  private DownloadThread mThread;
  private String mError;
  private boolean mDestroyed;
  
  private class DownloadThread extends Thread {
    private Uri mSourceUri;
    private String mZipBaseName;
    
    public DownloadThread(String sourceUri) {
      mSourceUri = Uri.parse(sourceUri);
    }
    
    @Override public void run() {
      List<String> segments = mSourceUri.getPathSegments();
      if (segments.size() == 0) {
        sendMessage(ERROR, "No file specified in " + mSourceUri);
        return;
      }
      mZipBaseName = segments.get(segments.size() - 1);
        
      downloadFile();
      if (mError == null) {
        extractZipFiles();
      }
      if (mError == null) {
        if (!hasRequiredFiles(mExternalDir)) {
          mError = "Failed to download required files to " + mExternalDir 
            + ":";
          for (String s: REQUIRED_FILES) mError += " " + s;
        }
      }
      if (mError == null) {
        sendMessage(SUCCESS, "");
      } else {
        sendMessage(ERROR, mError);
      }
      Log.d(TAG, "Download thread exiting : " + mError);
    }
    
    private void downloadFile() {
      DownloadManager.Request req = new DownloadManager.Request(mSourceUri);

      File dest = new File(mExternalDir, mZipBaseName);
      req.setDestinationUri(Uri.fromFile(dest));
      Log.d(TAG, "Start downloading " + mSourceUri.toString() +
            "->" + dest.getAbsolutePath());
      long downloadId = mDownloadManager.enqueue(req);

      DownloadManager.Query query = new DownloadManager.Query();
      query.setFilterById(downloadId);

      int n = 0;
      for (;;) {
        ++n;
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          Log.e(TAG, "Thread Interrupted");
        }

        int status = -1;
        long bytes = -1;
        String reason;
        if (true) {
          Cursor cursor = mDownloadManager.query(query);
          int idStatus = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
          cursor.moveToFirst();
          status = cursor.getInt(idStatus);

          int idBytes = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
          cursor.moveToFirst();
          bytes = cursor.getLong(idBytes);
          
          int idReason = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
          cursor.moveToFirst();
          reason = cursor.getString(idReason);
          cursor.close();
        }
        
        if (mDestroyed) {
          setError("Cancelled by user");
          mDownloadManager.remove(downloadId);
          return;
        }
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
          return;
        } else if (status == DownloadManager.STATUS_FAILED) {
          if (!reason.equals(Integer.toString(DownloadManager.ERROR_FILE_ALREADY_EXISTS))) {
            // TODO(saito) show more detailed status
            setError("Download of " + mSourceUri.toString() +
                " failed after " + bytes + " bytes: " + reason);
          }
          return;
        }
        sendMessage(DOWNLOADING, "Downloaded " + bytes + " bytes");
      }
    }

    private void extractZipFiles() {
      ZipEntry e = null;
      try {
        File zipPath = new File(mExternalDir, mZipBaseName);
        ZipFile zip = new ZipFile(zipPath);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
          e = entries.nextElement();
          extractZipFile(zip, e);
        }
      } catch (IOException ex) {
        Log.e(TAG, "Exception: " + ex.toString());
        String msg = "Failed to extract file: " + ex.toString(); 
        if (e != null) msg += " for zip: " + e.toString();
        setError(msg);
      }
    }
    
    private void extractZipFile(ZipFile zip, ZipEntry e) throws IOException {
      Log.d(TAG, "Found zip entry:" + e.toString());
      FileOutputStream out = null;
      InputStream in = null;
      sendMessage(EXTRACTING, "Extracting " + e.getName());
      try {
        File outPath = new File(mExternalDir, e.getName());
        out = new FileOutputStream(outPath);
        in = zip.getInputStream(e);
        byte[] buf = new byte[8192];
        int n;
        long cumulative = 0;
        long lastReported = 0;
        while ((n = in.read(buf)) > 0) {
          out.write(buf, 0, n);
          cumulative += n;
          if (cumulative - lastReported >= (1 << 20)) {
            sendMessage(EXTRACTING, e.getName() + ": " + cumulative + " bytes extracted");
            lastReported = cumulative;
          }
        }
      } finally {
        if (in != null) in.close();
        if (out != null) out.close();
      }
    }
  }
  
  private void setError(String m) {
    if (mError == null) mError = m;  // take only the first message
  }
  
  private void sendMessage(int state, String message) {
    Message msg = mHandler.obtainMessage();
    Bundle b = new Bundle();
    Status s = new Status();
    s.state = state;
    s.message = message;
    b.putSerializable("status", s);
    msg.setData(b);
    mHandler.sendMessage(msg);
  }
}
