// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

/**
 * @author saito@google.com (Your Name Here)
 *
 */

public class SettingsActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.settings);
    
    ArrayAdapter adapter = ArrayAdapter.createFromResource(
        this, R.array.player_types, android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    ((Spinner)findViewById(R.id.player_black)).setAdapter(adapter);
    ((Spinner)findViewById(R.id.player_white)).setAdapter(adapter);
  }

  
}
