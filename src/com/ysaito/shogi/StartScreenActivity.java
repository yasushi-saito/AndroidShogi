// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

/**
 * @author yasushi.saito@gmail.com 
 *
 */
public class StartScreenActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.start_screen);
  }
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.start_screen_menu, menu);
    return true;
  }
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.new_game:
        newGame();
        return true;
      case R.id.settings:
        settings();
        return true;
      default:    
        return super.onOptionsItemSelected(item);
    }
  }
  
  void newGame() {
    startActivity(new Intent(this, ShogiActivity.class));
  }
  
  void settings() {
    startActivity(new Intent(this, SettingsActivity.class));
  }
}
