package com.ysaito.shogi;

import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;

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
  public void startListing() {
    ListThread thread = new ListThread();
    mEnv.startProgressAnimation();
    thread.execute(0/*not used*/);
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
  
  private class ListThread extends AsyncTask<Integer, ListingStatus<T>, String> {
    @Override
    protected String doInBackground(Integer... unused) {
      final long now = System.currentTimeMillis(); 
      try {
        ExternalCacheManager.ReadResult r = mCache.read(mCacheKey);
        if (r.obj != null) {
          Log.d(TAG, "Found cache");
          publishProgress(new ListingStatus<T>((T[])r.obj, false));
        }
        if (r.needRefresh) {
          ArrayList<T> aggregate = new ArrayList<T>();
          T[] objs = null;
          for (int i = 0; i < mUrls.length; ++i) {
            URL url = new URL(mUrls[i]);
            Log.d(TAG, "Start reading player list page " + String.valueOf(i));
            objs = mEnv.listObjects(url.openStream());
            for (T obj: objs) aggregate.add(obj);
            if (r.obj == null) {
              publishProgress(new ListingStatus<T>(objs, false));
            } else {
              // If the screet was already filled with a stale cache,
              // buffer the new contents until it is complete, so that
              // we don't have delete the screen contents midway.
            } 
          }
          objs = aggregate.toArray(objs);
          mCache.write(mCacheKey, objs);
          if (r.obj != null) {
            publishProgress(new ListingStatus<T>(objs, true));
          }
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
