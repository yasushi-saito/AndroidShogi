// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;

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
public class Downloader {
  public static final String[] REQUIRED_FILES = {
    "book.bin", "fv.bin", "hash.bin"
  };
  public static final int DOWNLOADING = 1; 
  public static final int EXTRACTING = 2;
  public static final int SUCCESS = 3;
  public static final int ERROR = 4;

  public interface EventListener {
    /**
     * Called multiple times to report progress.
     * @param message Download status
     */
    public void onProgressUpdate(String message);
    
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
  public Downloader(
      EventListener listener,
      File externalDir) {
    mListener = listener;
    mExternalDir = externalDir;
    mThread = new DownloadThread();
  }

  /**
   * Must be called once to start downloading
   * @param sourceUrl The location of the file.
   */
  public void start(String sourceUrl) {
    mThread.execute(Uri.parse(sourceUrl));
  }

  /**
   * Must be called to stop the download thread.
   */
  public void destroy() {
    Log.d(TAG, "Destroy");
    mThread.cancel(false);
  }

  /**
   * See if all the files required to run Bonanza are present in externalDir.
   */
  public static boolean hasRequiredFiles(File externalDir) {
    for (String basename: REQUIRED_FILES) {
      File file = new File(externalDir, basename);
      if (!file.exists()) {
        Log.d(TAG, file.getAbsolutePath() + " not found");
        return false;
      }
    }
    return true;
  }

  // 
  // Implementation details
  //
  private static final String TAG = "ShogiDownload";
  private EventListener mListener;
  private File mExternalDir;
  private DownloadThread mThread;
  private String mError;

  private class DownloadThread extends AsyncTask<Uri, String, String> {
    // The zip file is split into 4MB chunks (11 total) to allow for easy restarts.
    // The first chunk is shogi-data.zip.aa, the second is shogi-data.zip.ab, so on.
    
    // Number of zip splits in the source site. "shogi-data.zip.aa" .. "shogi-data.zip.ak".
    private static final int NUM_ZIP_SHARDS = 11;
    
    // The source uri without any shard suffix, i.e., "http://foo.bar.com/dir/shogi-data.zip".
    private Uri mSourceUri;
    
    // The destination (unsharded) zip file.
    private File mZipPath;
    
    private boolean mCorruptionFound;
    
    @Override protected String doInBackground(Uri... sourceUri) {
      mError = null;
      mCorruptionFound = false;
      mSourceUri = sourceUri[0];

      List<String> segments = mSourceUri.getPathSegments();
      if (segments.size() == 0) {
        return "No file specified in " + mSourceUri;
      }
      String zipBaseName = segments.get(segments.size() - 1);
      mZipPath = new File(mExternalDir, zipBaseName);
      
      if (!mZipPath.exists()) {
        // Note: if the file exists but is corrupt, extractZipFiles() will
        // return an error, and deleteOldFiles() will delete the file.
        // A retry by the user will start from a clean slate.
        downloadZipShards();
        if (mError == null) {
          concatenateZipShards();
        }
      }
      if (mError == null) {
        extractZipFiles();
      }
      if (mError == null) {
        if (!hasRequiredFiles(mExternalDir)) {
          mError = String.format("Failed to download required files to %s:", mExternalDir);
          for (String s: REQUIRED_FILES) mError += " " + s;
          mCorruptionFound = true;
        }
      }
      
      Log.d(TAG, "Download thread exiting : " + mError);
      if (mCorruptionFound) {
        deleteOldFiles();
      }
      return mError;  // null means success
    }

    @Override public void onProgressUpdate(String... status) {
      for (String s: status) mListener.onProgressUpdate(s);
    }
    
    @Override public void onPostExecute(String status) {
      mListener.onFinish(status);
    }
    
    private void deleteOldFiles() {
      String[] children = mExternalDir.list();
      if (children != null) {
        for (String child: children) {
          File f = new File(mExternalDir, child);
          Log.d(TAG, "Deleting " + f.getAbsolutePath());
          f.delete();
        }
      }
    }

    private void downloadZipShards() {
      for (int shard = 0; shard < NUM_ZIP_SHARDS; ++shard) {
        String suffix = shardToSuffix(shard);
        File dstShardPath = new File(mExternalDir, mZipPath.getName() + suffix);
        if (!dstShardPath.exists()) {
          if (!downloadShard(mSourceUri.toString() + shardToSuffix(shard), dstShardPath)) {
            return;
          }
        } else {
          Log.d(TAG, dstShardPath.getName() + " already exists");
        }
      }
    }

    private void concatenateZipShards() {
      FileOutputStream out = null;
      FileInputStream in = null;
      try {
        try {
          out = new FileOutputStream(mZipPath);
          for (int shard = 0; shard < NUM_ZIP_SHARDS; ++shard) {
            File srcShardPath = new File(mExternalDir, mZipPath.getName() + shardToSuffix(shard));
            in = new FileInputStream(srcShardPath);
            copyStream(srcShardPath.getName() + ": %d bytes copied", in, out, 1<<20);
            FileInputStream tmpIn = in;
            in = null;
            tmpIn.close();
          }
        } finally {
          if (out != null) out.close();
          if (in != null) in.close();
        }
      } catch (IOException e) {
        setError("download " + e.toString());
      }
    }
    
    private String shardToSuffix(int shard) {
      // We assume that shard < 26
      return String.format(".a%c", shard + 'a'); 
    }
    
    private boolean downloadShard(String sourceUri, File dstPath) {
      InputStream in = null;
      FileOutputStream out = null;
      AndroidHttpClient httpclient = null;
      
      // Download to a tmp file first, then rename to dstPath so that
      // we won't leave partially downloaded file around.
      File tmpPath = new File(mExternalDir, "download-tmp");
      if (tmpPath.exists()) tmpPath.delete();
      
      Log.d(TAG, "Start download: " + sourceUri + "->" + dstPath.getName());
      publishProgress(dstPath.getName() + ": start downloading");
      
      try {
        try {
          httpclient = AndroidHttpClient.newInstance("Mozilla/5.0 (Android)");
          HttpResponse response = httpclient.execute(new HttpGet(sourceUri.toString()));
          in = response.getEntity().getContent();
          out = new FileOutputStream(tmpPath);
          copyStream(dstPath.getName() + ": %d bytes downloaded", in, out, 64<<10);

          FileOutputStream tmpStream = out;
          out = null;
          tmpStream.close();
          
          if (dstPath.exists()) dstPath.delete();
          tmpPath.renameTo(dstPath);
        } finally {
          Log.d(TAG, dstPath.getName() + " done");
          if (httpclient != null) httpclient.close();
          if (in != null) in.close();
          if (out != null) out.close();
        }
      } catch (IOException e) {
        setError("download " + e.toString());
        return false;
      }
      return true;
    }

    private void extractZipFiles() {
      ZipEntry e = null;
      try {
        ZipFile zip = new ZipFile(mZipPath);
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
        mCorruptionFound = true;
      }
    }

    private void extractZipFile(ZipFile zip, ZipEntry e) throws IOException {
      Log.d(TAG, "Found zip entry:" + e.toString());
      FileOutputStream out = null;
      InputStream in = null;
      publishProgress("Extracting " + e.getName());
      try {
        File outPath = new File(mExternalDir, e.getName());
        out = new FileOutputStream(outPath);
        in = zip.getInputStream(e);
        copyStream(e.getName() + ": %d bytes extracted", in, out, 1<<20);
      } finally {
        if (in != null) in.close();
        if (out != null) out.close();
      }
    }

    private final byte[] mBuf = new byte[65536];
    
    private void copyStream(String format, InputStream in, OutputStream out, int reportInterval) throws IOException {
      long cumulative = 0;
      long lastReported = 0;
      int n;
      while ((n = in.read(mBuf)) > 0) {
        out.write(mBuf, 0, n);
        cumulative += n;
        if (cumulative - lastReported >= reportInterval) {
          publishProgress(String.format(format, cumulative));
          lastReported = cumulative;
        }
        if (isCancelled()) {
          throw new IOException("Extraction cancelled by user");
        }
      }
    }
  }

  private void setError(String m) {
    Log.d(TAG, "Error: " + m);
    if (mError == null) mError = m;  // take only the first message
  }
}
