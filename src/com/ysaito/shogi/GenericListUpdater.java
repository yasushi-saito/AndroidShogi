package com.ysaito.shogi;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.ProgressDialog;
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
  
  public interface Env<T> {
    String getListLabel(T obj);
    T[] listObjects(InputStream in) throws Throwable;
    void startProgressAnimation();
    void stopProgressAnimation();
  }
  
  private class MyAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private ArrayList<T> mObjects = new ArrayList<T>();
    
    public MyAdapter(Context context) { 
      mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE); 
    }
    @Override public int getCount() { return mObjects.size(); }
    @Override public Object getItem(int position) { return getObject(position); } 
    @Override public long getItemId(int position) { return position; }
    @Override public View getView(int position, View convertView, ViewGroup parent) {
      TextView text;
      if (convertView == null) {
        text = (TextView)mInflater.inflate(android.R.layout.simple_list_item_1, parent, false);
        text.setTextSize(18);
      } else {
        text = (TextView)convertView;
      }
      text.setHorizontallyScrolling(false);
      text.setText(mEnv.getListLabel(getObject(position)));
      return text;
    }

    public void setObjects(T[] p) {
      mObjects.clear();
      addObjects(p);
    }
    
    public void addObjects(T[] p) {
      mObjects.ensureCapacity(mObjects.size() + p.length);
      for (T obj : p) mObjects.add(obj);
      notifyDataSetChanged();
    }
    
    public T getObject(int position) {
      if (mObjects == null || position >= mObjects.size()) return null;
      return mObjects.get(position);
    }
  }
  
  private final Context mContext;
  private final MyAdapter mAdapter;
  private final String[] mUrls;
  private final Env<T> mEnv;
  private final ExternalCacheManager mCache;
  private final String mCacheKey;
  
  public GenericListUpdater(Env<T> env, 
      Context context,
      String[] urls,
      ExternalCacheManager cache,
      String cacheKey) {
    mContext = context;
    mAdapter = new MyAdapter(context);
    mUrls = urls;
    mEnv = env; 
    mCache = cache;
    mCacheKey = cacheKey;
  }

  private ProgressDialog mProgressDialog = null;
  public BaseAdapter adapter() { return mAdapter; }
  
  public static final int MAY_READ_FROM_CACHE = 0;
  public static final int FORCE_RELOAD = 1;
  
  /**
   * 
   * @param mode one of MAY_READ_FROM_CACHE or FORCE_RELOAD.
   */
  public void startListing(int mode) {
    ListThread thread = new ListThread();
    mEnv.startProgressAnimation();
    thread.execute(mode);
  }

  public T getObjectAtPosition(int position) {
    return mAdapter.getObject(position);
  }

  private static class ListingStatus<T> {
    public ListingStatus(T[] o, boolean d) {
      objects = o;
      deleteExistingObjects = d;
    }
    
    public final T[] objects;
    public final boolean deleteExistingObjects;
  }
  
  private class ParallelFetcher {
    private ExecutorService mThreads;
    ArrayList<T[]> mResults;
    boolean[] mDone;
    int mNextIndexToRead;
    
    public ParallelFetcher(String[] urls) {
      mNextIndexToRead = 0;
      int numThreads = urls.length;
      if (numThreads >= 6) numThreads = 6;
      mThreads = Executors.newFixedThreadPool(numThreads);
      mResults = new ArrayList<T[]>();
      mDone = new boolean[urls.length];
      while (mResults.size() < urls.length) mResults.add(null);
      for (int i = 0; i < urls.length; ++i) {
        mThreads.submit(new Fetcher(i, urls[i]));
      }
    }

    public void shutdown() {
      mThreads.shutdown();
    }

    public synchronized T[] next() {
      while (!mDone[mNextIndexToRead]) {
        try {
          wait();
        } catch (InterruptedException e) { 
        }
      }
      T[] r = mResults.get(mNextIndexToRead);
      ++mNextIndexToRead;
      Log.d(TAG, "RETURN: " + String.valueOf(mNextIndexToRead) + ": " + String.valueOf(r != null ? r.length : -1));
      return r;
    }
    
    private synchronized void yieldResult(int index, T[] r) {
      Log.d(TAG, "Yield : " + String.valueOf(index) + ": " + String.valueOf(r != null ? r.length : -1));
      mResults.set(index, r);
      mDone[index] = true;
      notify();
    }
    
    private class Fetcher implements Runnable {
      final int mIndex;
      final String mUrl;
      
      public Fetcher(int index, String url) { 
        mIndex = index;
        mUrl = url; 
      }
      
      public void run() {
        T[] objs = null;
        try {
          try {
            URL url = new URL(mUrl);
            Log.d(TAG, "Start reading player list page " + mUrl);
            objs = mEnv.listObjects(url.openStream());
          } catch (Throwable e) {
            Log.d(TAG, "Failed to download " + mUrl + ": " + e.toString());
          }
        } finally {
          yieldResult(mIndex, objs);
        }
      }
    }
  }
  private class ListThread extends AsyncTask<Integer, ListingStatus<T>, String> {
    @Override
    protected String doInBackground(Integer... mode) {
      try {
        ParallelFetcher fetcher = null;
        try {
          ExternalCacheManager.ReadResult r;
          if (mode[0] == FORCE_RELOAD) {
            r = new ExternalCacheManager.ReadResult();
            r.needRefresh = true;
          } else {
            r = mCache.read(mCacheKey);
          }
          if (r.obj != null) {
            Log.d(TAG, "Found cache");
            publishProgress(new ListingStatus<T>((T[])r.obj, false));
          }
          if (r.needRefresh) {
            ArrayList<T> aggregate = new ArrayList<T>();
            T[] objs = null;
            fetcher = new ParallelFetcher(mUrls);
            for (int i = 0; i < mUrls.length; ++i) {
              objs = fetcher.next();
              for (T obj: objs) aggregate.add(obj);
              if (r.obj == null) {
                publishProgress(new ListingStatus<T>(objs, false));
              } else {
                // If the screet was already filled with a stale cache,
                // buffer the new contents until it is complete, so that
                // we don't have delete the screen contents midway.
              } 
            }
            fetcher.shutdown();
            objs = aggregate.toArray(objs);
            mCache.write(mCacheKey, objs);
            if (r.obj != null) {
              publishProgress(new ListingStatus<T>(objs, true));
            }
          }
        } finally {
          if (fetcher != null) fetcher.shutdown();
        }
      } catch (Throwable e) {
        // TODO: show error on screen
        return e.toString();
      }
      return "";
    }
    
    @Override
    protected void onProgressUpdate(ListingStatus<T>... list) {
      for (ListingStatus<T> status : list) {
        if (status.deleteExistingObjects) {
          mAdapter.setObjects(status.objects);
        } else {
          mAdapter.addObjects(status.objects);
       } 
      }
    }
    
    @Override
    protected void onPostExecute(String unused) {
      mEnv.stopProgressAnimation();
    }
  }
}
