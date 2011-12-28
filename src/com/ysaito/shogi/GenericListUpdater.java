package com.ysaito.shogi;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class GenericListUpdater<T> {
  private static final String TAG = "GenericListUpdater";
  private static final int MAX_CACHE_STALENESS_MS = 7200 * 1000; // 2h
  
  public interface Env<T> {
    String getListLabel(T obj);
    T[] listObjects(InputStream in) throws Throwable;
  };
  
  static private TextView getTextView(
      LayoutInflater inflater, 
      View convertView, 
      ViewGroup parent,
      String message) {
    TextView text;
    if (convertView == null) {
      text = (TextView)inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
      text.setTextSize(18);
      text.setHorizontallyScrolling(true);
    } else {
      text = (TextView)convertView;
    }
    text.setText(message);
    return text;
  }
  
  private class MyAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private T[] mObjects;
    
    public MyAdapter(Context context) { 
      mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
      mObjects = null;
    }
    @Override public int getCount() { return mObjects == null ? 0 : mObjects.length; }
    @Override public Object getItem(int position) { return getObject(position); } 
    @Override public long getItemId(int position) { return position; }
    @Override public View getView(int position, View convertView, ViewGroup parent) {
      return getTextView(
          mInflater, convertView, parent,
          mEnv.getListLabel(getObject(position)));
    }

    public void setObjects(T[] p) {
      mObjects = p;
      notifyDataSetChanged();
    }
    
    public T getObject(int position) {
      if (mObjects == null || position >= mObjects.length) return null;
      return mObjects[position];
    }
  }
  
  private final MyAdapter mAdapter;
  private final String mUrl;
  private final Env<T> mEnv;
  private final ExternalCacheManager mCache;
  private final String mCacheKey;
  
  public GenericListUpdater(Env<T> env, 
      Context context,
      String url,
      ExternalCacheManager cache,
      String cacheKey) {
    mAdapter = new MyAdapter(context);
    mUrl = url;
    mEnv = env; 
    mCache = cache;
    mCacheKey = cacheKey;
  }

  public BaseAdapter adapter() { return mAdapter; }
  public void startListing() {
    ListThread thread = new ListThread();
    thread.execute(0/*not used*/);
  }

  public T getObjectAtPosition(int position) {
    return mAdapter.getObject(position);
  }
  
  private static class CacheEntry<T> implements Serializable {
    // The time the web page was fetched. Used to avoid accessing the page too frequently.
    public long accessTime;
    public T[] objects;
  }
  
  private class ListThread extends AsyncTask<Integer, T[], String> {
    @Override
    protected String doInBackground(Integer... unused) {
      final long now = System.currentTimeMillis(); 
      try {
        CacheEntry<T> cache_entry = (CacheEntry<T>)mCache.read(mCacheKey);
        if (cache_entry != null) {
          Log.d(TAG, "Found cache");
          publishProgress(cache_entry.objects);
        }
        if (cache_entry == null || now - cache_entry.accessTime >= MAX_CACHE_STALENESS_MS) {
          if (cache_entry == null) cache_entry = new CacheEntry();
          URL url = new URL(mUrl);
          Log.d(TAG, "Start reading player list page");
          cache_entry.objects = mEnv.listObjects(url.openStream());
          cache_entry.accessTime = now;
          publishProgress(cache_entry.objects);
          mCache.write(mCacheKey, cache_entry);
        }
      } catch (Throwable e) {
        // TODO: show error on screen
        return e.toString();
      }
      return "";
    }
    
    @Override
    protected void onProgressUpdate(T[]... players_list) {
      for (T[] p : players_list) {
        mAdapter.setObjects(p);  // TODO: just take the last list
      }
    }
  }
}
