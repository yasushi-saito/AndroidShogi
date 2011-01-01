// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.io.File;

/**
 * @author yasushi.saito@gmail.com 
 *
 */
public class StartScreenActivity extends Activity {
  static final String TAG = "ShogiStart";
  static final int DIALOG_CONFIRM_DOWNLOAD = 1234;
  static final int DIALOG_DOWNLOAD = 1235;
  File mExternalDir;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.start_screen);
    
    if (!installedShogiData()) {
      // Create a dialog and ask the download manager to fetch the file.
      // Block until download completes.
      // showDialog(DOWNLOAD_DIALOG);
    }
    
    mExternalDir = getExternalFilesDir(null);
    Button b = (Button)findViewById(R.id.new_game_button);
    b.setOnClickListener(new Button.OnClickListener() {
      @Override public void onClick(View v) { newGame(); }
    });

    b = (Button)findViewById(R.id.settings_button);
    b.setOnClickListener(new Button.OnClickListener() {
      @Override public void onClick(View v) { settings(); }
    });
    
    b = (Button)findViewById(R.id.download_button);
    b.setOnClickListener(new Button.OnClickListener() {
      @Override public void onClick(View v) { 
        if (BonanzaDownloader.hasRequiredFiles(mExternalDir)) {
          showDialog(DIALOG_CONFIRM_DOWNLOAD);
        } else {
          startDownload();
        }
       }
    });
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
  
  //
  // Data download
  //
  ProgressDialog mDownloadDialog;
  BonanzaDownloader mDownloadController;
  
  AlertDialog newConfirmDownloadDialog() {
    AlertDialog.Builder b = new AlertDialog.Builder(this);
    b.setTitle("Files already downloaded. Download again?");
    b.setCancelable(true);
    b.setPositiveButton("Yes",
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface d, int item) {
            startDownload();
          }
        });
    b.setNegativeButton("No",
        new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface d, int item) {
        d.cancel();
      }
    });
    return b.create();
  }
  
  ProgressDialog newDownloadDialog() {
    ProgressDialog d = new ProgressDialog(this);
    d.setCancelable(true);
    d.setMessage("Downloading shogi-data.zip");
    d.setOnCancelListener(new DialogInterface.OnCancelListener() {
      public void onCancel(DialogInterface unused) {
        mDownloadController.destroy();
      }
    });
    return d;
  }
  
  void startDownload() {
    DownloadManager m = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
    mDownloadController = new BonanzaDownloader(
        mDownloadHandler, mExternalDir, m);
    mDownloadController.start();
    showDialog(DIALOG_DOWNLOAD);
  }
  
  final Handler mDownloadHandler = new Handler() {
    @Override public void handleMessage(Message msg) {
      BonanzaDownloader.Status status = (BonanzaDownloader.Status)msg.getData().get("status");
      if (mDownloadDialog != null && status.message != null) {
        mDownloadDialog.setMessage(status.message);
      }
      if (status.state >= BonanzaDownloader.SUCCESS) {
        if (mDownloadDialog != null) {
          mDownloadDialog.dismiss();
        }
        if (mDownloadController != null) {
          mDownloadController.destroy();
          mDownloadController = null;
        }
      }
    }
  };
  // Download Bonanza data files
  boolean installedShogiData() {
    return false;
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
      case DIALOG_CONFIRM_DOWNLOAD:
        return newConfirmDownloadDialog();
      case DIALOG_DOWNLOAD:
        mDownloadDialog = newDownloadDialog();
        return mDownloadDialog;
      default:    
        return null;
    }
  }
  
  void newGame() {
    startActivity(new Intent(this, ShogiActivity.class));
  }
  
  void settings() {
    startActivity(new Intent(this, ShogiPreferenceActivity.class));
  }
}