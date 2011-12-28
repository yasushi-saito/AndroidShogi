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
      mObjects = p;
      notifyDataSetChanged();
    }
    
    public T getObject(int position) {
      if (mObjects == null || position >= mObjects.length) return null;
      return mObjects[position];
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
    mProgressDialog = ProgressDialog.show(
        mContext,
        "Please wait...", "Doing Extreme Calculations...", 
        true);
    ListThread thread = new ListThread();
    thread.execute(0/*not used*/);
  }

  public T getObjectAtPosition(int position) {
    return mAdapter.getObject(position);
  }
  
    private class ListThread extends AsyncTask<Integer, T[], String> {
    @Override
    protected String doInBackground(Integer... unused) {
      final long now = System.currentTimeMillis(); 
      try {
        ExternalCacheManager.ReadResult r = mCache.read(mCacheKey);
        if (r.obj != null) {
          Log.d(TAG, "Found cache");
          publishProgress((T[])r.obj);
        }
        if (r.needRefresh) {
          ArrayList<T> aggr = new ArrayList<T>();
          T[] objs = null;
          for (int i = 0; i < mUrls.length; ++i) {
            URL url = new URL(mUrls[i]);
            Log.d(TAG, "Start reading player list page " + String.valueOf(i));
            objs = mEnv.listObjects(url.openStream());
            for (T obj : objs) aggr.add(obj);
          }
          objs = aggr.toArray(objs);
          publishProgress(objs);
          mCache.write(mCacheKey, objs);
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
      if (mProgressDialog != null) {
        mProgressDialog.dismiss();
        
        // Set to null so that the dialog will disappear on the first 
        // call to this method.
        mProgressDialog = null;
      }
    }
  }
}
