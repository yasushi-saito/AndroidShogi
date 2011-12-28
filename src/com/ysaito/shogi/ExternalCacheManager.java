package com.ysaito.shogi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;

import android.content.Context;
import android.util.Log;

public class ExternalCacheManager {
  private static final String TAG = "ExtenalCacheManager"; 
  private final File mDir;
  private final Context mContext;
  
  // cache key -> last access time
  private HashMap<String, Long> mLastAccessTimes;
  
  // The path where a serialized mLastAccessTimes is saved serialized.
  private static final String SUMMARY_PATH = "external_cache_summary";
  
  // TODO: add background purging
  
  /**
   * 
   * @param id The ID for this cache. It can be any string (it should be usable as a filename). 
   * It should be unique within the application, so that
   * multiple instances of this class can partition the per-application cache space.
   */
  public ExternalCacheManager(Context context, String id) {
    mContext = context;
    mDir = new File(context.getCacheDir(), id);
    mDir.mkdirs();
    Log.d(TAG, "CACHE=" + mDir.getAbsolutePath());
    mLastAccessTimes = new HashMap<String, Long>();
    
    FileInputStream summary_in = null;
    try {
      try {
        summary_in = context.openFileInput(SUMMARY_PATH);
        ObjectInputStream oin = new ObjectInputStream(summary_in);
        mLastAccessTimes = (HashMap<String, Long>)oin.readObject();
      } finally {
        if (summary_in != null) summary_in.close();
      }
    } catch (FileNotFoundException e) {
      // ignore
    } catch (Throwable e) {
      Log.d(TAG, SUMMARY_PATH + ": failed to read summary: " + e.toString());
    }
  }
  
  /**
   * Clear the cache
   */
  public synchronized void clearAll() {
    try {
      for (String fileName : mDir.list()) {
        File f = new File(mDir, fileName);
        f.delete();
      }
      mLastAccessTimes = new HashMap<String, Long>();
      saveSummary();
    } catch (IOException e) {
      Log.d(TAG, SUMMARY_PATH + ": Failed to clear cache: " + e.toString());
    }
  }
  
  /**
   * Write the mapping key -> obj to the cache.
   */
  public synchronized void write(String key, Serializable obj) {
    FileOutputStream data_out = null;
    try {
      try {
        File path = new File(mDir, key);
        data_out = new FileOutputStream(path);
        new ObjectOutputStream(data_out).writeObject(obj);
        mLastAccessTimes.put(key, System.currentTimeMillis());
        saveSummary();
      } finally {
        if (data_out != null) data_out.close();
      }
    } catch (IOException e) {
      Log.d(TAG, "Failed to write: " + e.toString());
    }
  }
  
  /**
   * If object for "key" is in the cache, return it. Else, or in case of an I/O error, return null.
   */
  public synchronized Object read(String key) {
    FileInputStream data_in = null;
    Object obj = null;
    try {
      try {
        File path = new File(mDir, key);
        data_in = new FileInputStream(path);
        obj = new ObjectInputStream(data_in).readObject();
        mLastAccessTimes.put(key, System.currentTimeMillis());
        saveSummary();
      } finally {
        if (data_in != null) data_in.close();
      }
    } catch (FileNotFoundException e) {
      return null;
    } catch (Throwable e) {
      Log.d(TAG, SUMMARY_PATH + ": failed to write: " + e.toString());
    }
    return obj;
  }

  private void saveSummary() throws IOException {
    FileOutputStream out = null;
    try {
      if (mContext ==null) Log.d(TAG, "NULL");
      else Log.d(TAG, "NONNULL:" + SUMMARY_PATH);
      out = mContext.openFileOutput(SUMMARY_PATH, Context.MODE_PRIVATE);
      new ObjectOutputStream(out).writeObject(mLastAccessTimes);
    } finally {
      if (out != null) out.close();
    }
  }

}
