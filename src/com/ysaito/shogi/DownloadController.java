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
import java.io.IOException;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author saito@google.com (Your Name Here)
 *
 */
public class DownloadController {
  static final int DOWNLOADING = 1; 
  static final int EXTRACTING = 2;
  static final int SUCCESS = 3;
  static final int ERROR = 4;
  
  static class Status implements Serializable {
    int state;  // one of the above constants
    long bytesDownloaded;
    String message;
    
    @Override public String toString() {
      return "state=" + state + " #bytes=" + bytesDownloaded + " state=" + message;
    }
  }
  
  public DownloadController(
      Handler handler, 
      File externalDir,
      DownloadManager manager) {
    mHandler = handler;
    mExternalDir = externalDir;
    mDownloadManager = manager;
    mThread = new DownloadThread();
    Log.d(TAG, "Start downloading");
  }
  
  void start() {
    mThread.start();
  }

  void destroy() {
    mStopped = true;
  }
  
  static final String TAG = "ShogiDownload";
  Handler mHandler;
  File mExternalDir;
  DownloadManager mDownloadManager;
  DownloadThread mThread;
  boolean mStopped;
  
  class DownloadThread extends Thread {
    static final String mZipBasename = "shogi-data.zip";
    
    @Override public void run() {
      if (downloadFile()) {
        extractFiles();
      }
      Log.d(TAG, "Download thread exiting");
    }
    
    boolean downloadFile() {
      // Arrange to download from XXXX to /sdcard/<app_dir>/shogi-data.zip
      Uri.Builder uriBuilder = new Uri.Builder();
      uriBuilder.scheme("http");
      uriBuilder.authority("www.corp.google.com");
      //uriBuilder.path("/~saito/shogi-data.zip");
      uriBuilder.path("/~saito/foo.zip");
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
          Thread.sleep(3000);
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
          sendMessage(bytes, ERROR, "Cancelled by user");
          mDownloadManager.remove(downloadId);
          return false;
        }
        if (status == DownloadManager.STATUS_FAILED) {
          if (reason.equals(Integer.toString(DownloadManager.ERROR_FILE_ALREADY_EXISTS))) {
            status = DownloadManager.STATUS_SUCCESSFUL;
          } else {
            // TODO(saito) show more detailed status
            sendMessage(bytes, ERROR, "Download failed: " + reason);
            return false;
          }
        }
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
          // TODO(saito) show more detailed status
          sendMessage(bytes, DOWNLOADING, "Download completed: " + reason);
          return true;
        } 
        sendMessage(bytes, DOWNLOADING, reason);
        // continue;
      }
    }

    void extractFiles() {
      try {
        File zipPath = new File(mExternalDir, mZipBasename);
        ZipFile zip = new ZipFile(zipPath);
        ZipEntry e;
        Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>)zip.entries();
        while (entries.hasMoreElements()) {
          e = entries.nextElement();
          Log.d(TAG, "Found zip entry:" + e.toString());
        }
        sendMessage(0, SUCCESS, "Extracted");
      } catch (IOException e) {
        Log.e(TAG, "EXCEPTION: " + e.toString());
        sendMessage(0, ERROR, "Zip extraction failed: " + e.toString());
      }
    }
  }
  
  void sendMessage(long bytes, int state, String message) {
    Message msg = mHandler.obtainMessage();
    Bundle b = new Bundle();
    Status s = new Status();
    s.bytesDownloaded = bytes;
    s.state = state;
    s.message = message;
    b.putSerializable("status", s);
    msg.setData(b);
    mHandler.sendMessage(msg);
  }
}
