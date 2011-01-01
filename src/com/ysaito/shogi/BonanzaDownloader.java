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
  static final String[] REQUIRED_FILES = {
    "book.bin", "fv.bin", "hash.bin"
  };
  static final int DOWNLOADING = 1; 
  static final int EXTRACTING = 2;
  static final int SUCCESS = 3;
  static final int ERROR = 4;
  
  static class Status implements Serializable {
    int state;  // one of the above constants
    String message;
    
    @Override public String toString() {
      return "state=" + state + " message=" + message;
    }
  }
  
  public BonanzaDownloader(
      Handler handler, 
      File externalDir,
      DownloadManager manager) {
    mHandler = handler;
    mExternalDir = externalDir;
    mDownloadManager = manager;
    mThread = new DownloadThread();
    Log.d(TAG, "Start downloading");
  }
  
  // Must be called once to start downloading
  void start() {
    mThread.start();
  }
  
  // Must be called to stop the download thread.
  void destroy() {
    mStopped = true;
  }
  
  // See if all the files required to run Bonanza are present in @p externalDir.
  static boolean hasRequiredFiles(File externalDir) {
    for (String basename: REQUIRED_FILES) {
      File file = new File(externalDir, basename);
      if (!file.exists()) return false;
    }
    return true;
  }
  
  // 
  // Implementation details
  //
  static final String TAG = "ShogiDownload";
  Handler mHandler;
  File mExternalDir;
  DownloadManager mDownloadManager;
  DownloadThread mThread;
  String mError;
  boolean mStopped;
  
  class DownloadThread extends Thread {
    static final String mZipBasename = "shogi-data.zip";
    
    @Override public void run() {
      downloadFile();
      if (mError == null) extractZipFiles();
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
      Log.d(TAG, "Download thread exiting");
    }
    
    void downloadFile() {
      // Arrange to download from XXXX to /sdcard/<app_dir>/shogi-data.zip
      Uri.Builder uriBuilder = new Uri.Builder();
      uriBuilder.scheme("http");
      uriBuilder.authority("www.corp.google.com");
      uriBuilder.path("/~saito/shogi-data.zip");
      // uriBuilder.path("/~saito/foo.zip");
      DownloadManager.Request req = new DownloadManager.Request(uriBuilder.build());

      File dest = new File(mExternalDir, mZipBasename);
      req.setDestinationUri(Uri.fromFile(dest));
      Log.d(TAG, "Start downloading to " + dest.getAbsolutePath());
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
        
        if (mStopped) {
          setError("Cancelled by user");
          mDownloadManager.remove(downloadId);
          return;
        }
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
          return;
        } else if (status == DownloadManager.STATUS_FAILED) {
          if (!reason.equals(Integer.toString(DownloadManager.ERROR_FILE_ALREADY_EXISTS))) {
            // TODO(saito) show more detailed status
            setError("Download failed after " + bytes + " bytes: " + reason);
          }
          return;
        }
        sendMessage(DOWNLOADING, "Downloaded " + bytes + " bytes");
      }
    }

    void extractZipFiles() {
      ZipEntry e = null;
      try {
        File zipPath = new File(mExternalDir, mZipBasename);
        ZipFile zip = new ZipFile(zipPath);
        Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>)zip.entries();
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
    
    void extractZipFile(ZipFile zip, ZipEntry e) throws IOException {
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
  
  void setError(String m) {
    if (mError == null) mError = m;  // take only the first message
  }
  
  void sendMessage(int state, String message) {
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
