package com.ysaito.shogi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

public class GenericListUpdater<T> {
  private static final String TAG = "GenericListUpdater";
  
  public interface Env<T> {
    String getListLabel(T obj);
    int numStreams();
    T[] readNthStream(int index) throws Throwable;
  }
  
  private class MyAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private ArrayList<T> mObjects = new ArrayList<T>();
    private Comparator<T> mSorter;
    
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
      if (mSorter != null) Collections.sort(mObjects, mSorter);
      notifyDataSetChanged();
    }
    
    public T getObject(int position) {
      if (mObjects == null || position >= mObjects.size()) return null;
      return mObjects.get(position);
    }
    
    public void sort(Comparator<T> sorter) {
      mSorter = sorter;
      if (mSorter != null) Collections.sort(mObjects, mSorter);
      notifyDataSetChanged();
    }
  }
  
  private final Context mContext;
  private final MyAdapter mAdapter;
  private final Env<T> mEnv;
  private final ExternalCacheManager mCache;
  private final String mCacheKey;
  private final ProgressBar mProgressBar;
  
  public GenericListUpdater(Env<T> env, 
      Context context,
      String cacheKey,
      ProgressBar progressBar) {
    mContext = context;
    mAdapter = new MyAdapter(context);
    mEnv = env;
    mCache = ExternalCacheManager.getInstance(context.getApplicationContext());
    mCacheKey = cacheKey;
    mProgressBar = progressBar;
    if (mProgressBar != null) mProgressBar.setVisibility(View.INVISIBLE);
  }

  public BaseAdapter adapter() { return mAdapter; }
  
  /**
   * 
   * @param mode one of MAY_READ_FROM_CACHE or FORCE_RELOAD.
   */
  public static final int MAY_READ_FROM_CACHE = 0;
  public static final int FORCE_RELOAD = 1;
  public void startListing(int mode) {
    ListThread thread = new ListThread();
    if (mProgressBar != null) {
      if (mProgressBar != null) mProgressBar.setVisibility(View.VISIBLE);
    }
    thread.execute(mode);
  }

  public void sort(Comparator<T> sorter) {
    mAdapter.sort(sorter);
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
    private ArrayList<T[]> mResults;
    private boolean[] mDone;
    private int mNextIndexToRead;
    private String mError;
    
    public ParallelFetcher() {
      final int numStreams = mEnv.numStreams();
      mNextIndexToRead = 0;
      int numThreads = numStreams;
      if (numThreads >= 6) numThreads = 6;
      mThreads = Executors.newFixedThreadPool(numThreads);
      mResults = new ArrayList<T[]>();
      mDone = new boolean[numStreams];
      while (mResults.size() < numStreams) mResults.add(null);
      for (int i = 0; i < numStreams; ++i) {
        mThreads.submit(new Fetcher(i));
      }
    }
    
    public synchronized String error() { return mError; }
    
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
    
    private synchronized void setError(String e) {
      if (mError == null) mError = e;
    }
    
    private class Fetcher implements Runnable {
      final int mIndex;
      
      public Fetcher(int index) {
        mIndex = index;
      }
      
      public void run() {
        T[] objs = null;
        try {
          try {
            objs = mEnv.readNthStream(mIndex);
          } catch (Throwable e) {
            setError(Util.throwableToString(e));
          }
        } finally {
          yieldResult(mIndex, objs);
        }
      }
    }
  }
  
  /**
   * @param mode either FORCE_RELOAD or MAY_READ_FROM_CACHE
   * 
   * @return an error message, or null on success
   */
  private class ListThread extends AsyncTask<Integer, ListingStatus<T>, String> {
    @Override
    protected String doInBackground(Integer... mode) {
      ParallelFetcher fetcher = null;
      try {
        try {
          ExternalCacheManager.ReadResult r;
          if (mCacheKey == null || mode[0] == FORCE_RELOAD) {
            r = new ExternalCacheManager.ReadResult();
            r.needRefresh = true;
          } else {
            r = mCache.read(mCacheKey);
          }
          final boolean hitCache = (r.obj != null); 
          if (hitCache) {
            Log.d(TAG, "Found cache");
            publishProgress(new ListingStatus<T>((T[])r.obj, false));
          }
          if (r.needRefresh) {
            ArrayList<T> aggregate = new ArrayList<T>();
            T[] objs = null;
            fetcher = new ParallelFetcher();
            
            final int numStreams = mEnv.numStreams();
            for (int i = 0; i < numStreams; ++i) {
              objs = fetcher.next();
              if (objs != null) {
                for (T obj: objs) aggregate.add(obj);
                if (!hitCache) {
                  // Incrementally update the screen as results arrive
                  publishProgress(new ListingStatus<T>(objs, false));
                } else {
                  // If the screet was already filled with a stale cache,
                  // buffer the new contents until it is complete, so that
                  // we don't have delete the screen contents midway.
                } 
              }
            }
            if (fetcher.error() == null) {
              objs = aggregate.toArray(objs);
              if (mCacheKey != null) mCache.write(mCacheKey, objs);
              if (hitCache) {
                publishProgress(new ListingStatus<T>(objs, true));
              }
            }
          }
        } finally {
          if (fetcher != null) fetcher.shutdown();
        }
      } catch (Throwable e) {
        return Util.throwableToString(e);
      }
      if (fetcher != null) {
        return fetcher.error();
      } else {
        // shouldn't happen
        return null;
      }
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
    protected void onPostExecute(String error) {
      if (mProgressBar != null) {
        if (mProgressBar != null) mProgressBar.setVisibility(View.INVISIBLE);
      }
      if (error != null) Util.showErrorDialog(mContext, error);
    }
  }
}
