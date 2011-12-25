package com.ysaito.shogi.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import com.ysaito.shogi.Downloader;

import android.content.res.AssetManager;
import android.test.AndroidTestCase;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.Log;

public class DownloaderTest extends InstrumentationTestCase { 
  static final String TAG = "DownloaderTest";

  File SRC_DIR;
  File DST_DIR;

  protected void setUp() throws IOException {
    SRC_DIR = new File(getInstrumentation().getContext().getExternalFilesDir(null),
      "DownloaderTestSrc");
    DST_DIR = new File(getInstrumentation().getContext().getExternalFilesDir(null),
      "DownloaderTestDst");
    if (!SRC_DIR.exists()) SRC_DIR.mkdir();
    if (!DST_DIR.exists()) DST_DIR.mkdir();
    CleanDstDir();
    ResetSrcDir();
  }

  void CleanDstDir() throws IOException {  
    String[] children = DST_DIR.list();
    if (children != null) {
      for (String child: children) {
        File f = new File(DST_DIR, child);
        Log.d(TAG, "Deleting " + f.getAbsolutePath());
        f.delete();
      }
    }
  }

  void ResetSrcDir() throws IOException {
    AssetManager assets = getInstrumentation().getContext().getAssets();
    copyFile(assets, SRC_DIR, "testdata.zip.aa");
    copyFile(assets, SRC_DIR, "testdata.zip.ab");
    copyFile(assets, SRC_DIR, "testdata.zip.ac");
    copyFile(assets, SRC_DIR, "testdata.zip.ad");    
    copyFile(assets, SRC_DIR, "testdata.zip.summary");    
  }

  private static void copyFile(AssetManager assets, File dstDir, String basename) throws IOException {
    InputStream input = assets.open(basename);
    FileOutputStream output = new FileOutputStream(new File(dstDir, basename));
    byte[] buf = new byte[1024];
    int len;
    while ((len = input.read(buf)) >= 0) {
      output.write(buf, 0, len);
    }
    input.close();
    output.close();
  }

  private static void copyString(String contents, File dstDir, String basename) throws IOException {
    FileOutputStream output = new FileOutputStream(new File(dstDir, basename));
    output.write(contents.getBytes());
    output.close();
  }
  
  class DownloadState {
    DownloadState() { done = false; error = null; }
    boolean done;
    String error;
  };
  DownloadState mDownloadState;

  String doDownload() throws Throwable {
    mDownloadState = new DownloadState();
    
    // startActivity(_startIntent, null, null);
    runTestOnUiThread(new Runnable() {
      public void run() {
        Downloader.EventListener listener = new Downloader.EventListener() {
          public void onProgressUpdate(String message) {
            Log.d(TAG, "Progress: " + message);
          }
          public void onFinish(String error) {
            Log.d(TAG, "Done: " + error);
            synchronized (mDownloadState) {
              mDownloadState.error = error;
              mDownloadState.done = true;   
              mDownloadState.notify();
            }
          }
        }; 
        Downloader d = new Downloader(listener, DST_DIR);
        String uri = String.format("file://%s/testdata.zip", SRC_DIR.getAbsolutePath());
        Log.d(TAG, "Start download: " + uri);
        d.start(uri);
      }
    });
    
    synchronized (mDownloadState) {
      while (!mDownloadState.done) {
        mDownloadState.wait();
      }
    }
    if (mDownloadState.error != null) return mDownloadState.error;
    InputStream input = new FileInputStream(new File(DST_DIR, "testdata.txt"));
    byte[] buf = new byte[1024];
    int len = input.read(buf);
    input.close();
    return new String(buf, 0, len);
  }
  
  public String listFilesInDstDir() {
    String[] files = DST_DIR.list();
    return TextUtils.join(" ", files);
  }
  
  private void deleteFromSource(String name) {
    File f = new File(SRC_DIR, name);
    f.delete();
  }

  // Test successful download
  public void testOk() throws Throwable {
    assertEquals("foo\nbar\nbaz\n", doDownload());
  }

  public void testMissingSummary() throws Throwable {
    deleteFromSource("testdata.zip.summary");
    assertTrue(doDownload().contains("testdata.zip.summary"));
    assertEquals("", listFilesInDstDir());
    
    // Test download retry 
    ResetSrcDir();
    assertEquals("foo\nbar\nbaz\n", doDownload());
  }
  
  public void testMissingFile() throws Throwable {
    deleteFromSource("testdata.zip.ab");
    
    assertTrue(doDownload().contains("testdata.zip.ab"));
    assertEquals("testdata.zip.aa testdata.zip.summary", listFilesInDstDir());
    
    // Test download retry 
    ResetSrcDir();
    assertEquals("foo\nbar\nbaz\n", doDownload());
  }
  
  public void testCorruptFile() throws Throwable {
    copyString("blah", SRC_DIR, "testdata.zip.ab");
    String result = doDownload();
    Log.d(TAG, "Result " + result);
    assertTrue(result.contains("Wrong SHA-1 checksum"));
    
    // Corruption will clear the dest dir.
    assertEquals("", listFilesInDstDir());
  }
  
  public void testCorruptSummary() throws Throwable {
    copyString("blah", SRC_DIR, "testdata.zip.summary");
    String result = doDownload();
    Log.d(TAG, "Result " + result);
    assertTrue(result.contains("Illegal summary line"));
    
    // Corruption will clear the dest dir.
    assertEquals("", listFilesInDstDir());
  }
}
