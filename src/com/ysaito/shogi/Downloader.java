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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;
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
 * through the EventListener.
 */
public class Downloader {
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
  }
  
  /**
   * @param listener Used to report download status to the caller
   * @param externalDir The directory to store the downloaded file.
   * The basename of the file will be the same as the one in the sourceUrl.
   */
  public Downloader(
      EventListener listener,
      File externalDir) {
    mListener = listener;
    mExternalDir = externalDir;
    mThread = new DownloadThread();
  }

  static void deleteFilesInDir(File dir) {
    String[] children = dir.list();
    if (children != null) {
      for (String child: children) {
        File f = new File(dir, child);
        if (!f.isDirectory()) {
          Log.d(TAG, "Deleting " + f.getAbsolutePath());
          f.delete();
        }
      }
    }
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

  // 
  // Implementation details
  //
  private static final String TAG = "ShogiDownload";
  private EventListener mListener;
  private File mExternalDir;
  private DownloadThread mThread;
  private String mError;

  private static class Summary {
    public static class File {
      public String suffix;
      public byte[] sha1Digest;
    }
    public ArrayList<File> files;

    public static Summary parseFile(java.io.File input) throws IOException, NumberFormatException {
      Summary s = new Summary();
      s.files = new ArrayList<File>();

      Scanner scanner = new Scanner(input);
      while (scanner.hasNextLine()) {
        String l = scanner.nextLine();
        if (Pattern.matches("\\s*#.*", l)) continue;

        File f = new File();
        Scanner line = new Scanner(l);
        line.useDelimiter(",");
        if (!line.hasNext()) {
          throw new NumberFormatException(l + ": Illegal summary line");
        }
        f.suffix = line.next();
        Log.d(TAG, String.format("SUFFIX=%s", f.suffix));

        if (!line.hasNext()) {
          throw new NumberFormatException(l + ": Illegal summary line");
        }
        f.sha1Digest = parseHexDigest(line.next());
        s.files.add(f);
      }
      return s;
    }
    
    private static byte[] parseHexDigest(String input) throws NumberFormatException {
      Log.d(TAG, String.format("DIGEST=%s", input));
      final int length = input.length();
      if (length % 2 != 0) {
        throw new NumberFormatException(input + ": unparsable digest");
      }
      int n = 0;
      byte[] b = new byte[length / 2];
      
      for (int i = 0; i < length / 2; ++i) {
        String hex = input.substring(i * 2, i * 2 + 2);
        b[n++] = (byte)Integer.parseInt(hex, 16);
      }
      return b;
    }
  }

  private class DownloadThread extends AsyncTask<Uri, String, String> {
    // The zip file is split into 4MB chunks (11 total) to allow for easy restarts.
    // The first chunk is shogi-data.zip.aa, the second is shogi-data.zip.ab, so on.
    
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
        Summary summary = downloadSummary();
        if (mError == null) {
          // Note: if the file exists but is corrupt, extractZipFiles() will
          // return an error, and deleteOldFiles() will delete the file.
          // A retry by the user will start from a clean slate.
          downloadZipShards(summary);
        }
        if (mError == null) {
          checksumZipShards(summary);
        }
        if (mError == null) {
          concatenateZipShards(summary);
        }
      }
      if (mError == null) {
        extractZipFiles();
      }
      Log.d(TAG, "Download thread exiting : " + mError);
      if (mCorruptionFound) {
        deleteFilesInDir(mExternalDir);
      }
      return mError;  // null means success
    }

    @Override public void onProgressUpdate(String... status) {
      Log.d(TAG, "OnProgress");
      for (String s: status) mListener.onProgressUpdate(s);
    }
    
    @Override public void onPostExecute(String status) {
      Log.d(TAG, "OnPost");
      mListener.onFinish(status);
    }
    
    private Summary downloadSummary() {
      File dstPath = new File(mExternalDir, mZipPath.getName() + ".summary");
      if (!downloadFile(mSourceUri.toString() + ".summary", dstPath)) {
        return null;
      }
      try {
        return Summary.parseFile(dstPath);
      } catch (IOException e) {
        mCorruptionFound = true;
        setError("downloadSummary: " + e.getMessage());
        return null;
      } catch (NumberFormatException e) {
        mCorruptionFound = true;
        setError("downloadSummary: " + e.getMessage());
        return null;
      }
    }

    private void downloadZipShards(Summary summary) {
      for (Summary.File file : summary.files) {
        File dstShardPath = new File(mExternalDir, mZipPath.getName() + "." + file.suffix);
        if (!dstShardPath.exists()) {
          if (!downloadFile(mSourceUri.toString() + "." + file.suffix, dstShardPath)) {
            return;
          }
        } else {
          Log.d(TAG, dstShardPath.getName() + " already exists");
        }
      }
    }

    private void checksumZipShards(Summary summary) {
      for (Summary.File file : summary.files) {
        File shardPath = new File(mExternalDir, mZipPath.getName() + "." + file.suffix);
        FileInputStream in = null;
        MessageDigest digester = null;
        try {
          try {
            digester = MessageDigest.getInstance("SHA-1");
            in = new FileInputStream(shardPath);
            int len;
            while ((len = in.read(mBuf)) > 0) {
              digester.update(mBuf, 0, len);
            }
          } finally {
            if (in != null) in.close();
          }
        } catch (java.security.NoSuchAlgorithmException e) {
          Log.e(TAG, "Could not find SHA1 digester");
          return;
        } catch (IOException e) {
          setError(shardPath.getAbsolutePath() + ": " + e.getMessage());
          return;
        }
        byte[] digest = digester.digest();
        if (!MessageDigest.isEqual(digest, file.sha1Digest)) {
          Log.d(TAG, shardPath.getName() + ": Wrong SHA-1 checksum");
          setError(shardPath.getName() + ": Wrong SHA-1 checksum: " + digest.toString());
          mCorruptionFound = true;
        }
      }
    }
    private void concatenateZipShards(Summary summary) {
      FileOutputStream out = null;
      FileInputStream in = null;
      try {
        try {
          out = new FileOutputStream(mZipPath);
          for (Summary.File file : summary.files) {
            File srcShardPath = new File(mExternalDir, mZipPath.getName() + "." + file.suffix);
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
    
    private boolean downloadFile(String sourceUri, File dstPath) {
      InputStream in = null;
      AndroidHttpClient httpclient = null;
      FileOutputStream out = null;
      
      // Download to a tmp file first, then rename to dstPath so that
      // we won't leave partially downloaded file around.
      File tmpPath = new File(mExternalDir, "download-tmp");
      if (tmpPath.exists()) tmpPath.delete();
      
      Log.d(TAG, "Start download: " + sourceUri + "->" + dstPath.getName());
      publishProgress(dstPath.getName() + ": start downloading");
      
      try {
        try {
          if (sourceUri.startsWith("file://")) {
            in = new FileInputStream(sourceUri.substring(7));
          } else {
            httpclient = AndroidHttpClient.newInstance("Mozilla/5.0 (Android)");
            HttpResponse response = httpclient.execute(new HttpGet(sourceUri.toString()));
            in = response.getEntity().getContent();
          }
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
