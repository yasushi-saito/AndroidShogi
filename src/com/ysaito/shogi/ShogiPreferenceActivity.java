// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * @author saito@google.com (Your Name Here)
 *
 */

public class ShogiPreferenceActivity extends PreferenceActivity {
  static final String TAG = "ShogiPreference";
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
  }
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.preferences_menu, menu);
    return true;
  }
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    Log.d(TAG, "Options menu");
    switch (item.getItemId()) {
      case R.id.reset_preferences:
        Log.d(TAG, "Reset");
        PreferenceManager.getDefaultSharedPreferences(getBaseContext())
          .edit().clear().commit();
        Toast.makeText(getBaseContext(), "Reset preferences to the default", Toast.LENGTH_SHORT);
        return true;
      default:    
        return super.onOptionsItemSelected(item);
    }
  }
  
}
