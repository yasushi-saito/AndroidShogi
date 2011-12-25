package com.ysaito.shogi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
<<<<<<< HEAD
import java.util.ArrayList;
=======
>>>>>>> 6dd1df4c0767403869da41a0d5a257f8ba430372

/**
 * @author yasushi.saito@gmail.com 
 *
 */
public class StartScreenActivity extends Activity {
  static final String TAG = "ShogiStart";
  static final int DIALOG_NEW_GAME = 1233;
  static final int DIALOG_CONFIRM_DOWNLOAD = 1234;
  static final int DIALOG_DOWNLOAD_STATUS = 1235;
  private File mExternalDir;
  private SharedPreferences mPrefs;
  

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.start_screen);

    mPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    mExternalDir = getExternalFilesDir(null);
    
    mNewGameButton = (Button)findViewById(R.id.new_game_button);
    mNewGameButton.setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) { newGame(); }
    });
    
    mPickLogButton = (Button)findViewById(R.id.pick_log_button);
    mPickLogButton.setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) { pickLog(); }
    });

    if (mExternalDir == null) {
      Toast.makeText(
          getBaseContext(),
          "Please mount the sdcard on the device", 
          Toast.LENGTH_LONG).show();
      mNewGameButton.setEnabled(false);
      mPickLogButton.setEnabled(false);
    } else if (!hasRequiredFiles(mExternalDir)) {
      mNewGameButton.setEnabled(false);
      mDownloadConfirmMessage = getResources().getString(R.string.start_download_database);
      showDialog(DIALOG_CONFIRM_DOWNLOAD);
    } else {
      initializeBonanzaInBackground();
    }
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
      case R.id.start_screen_help_menu_id:
          help();
          return true;
      case R.id.start_screen_preferences_menu_id:
        settings();
        return true;
      case R.id.start_screen_download_menu_id:
        if (mExternalDir == null) {
          Toast.makeText(
              getBaseContext(),
              "Please mount the sdcard on the device", 
              Toast.LENGTH_LONG).show();
        } else if (!hasRequiredFiles(mExternalDir)) {
          startDownload();
        } else {
          mDeleteFilesBeforeDownload = true;
          mDownloadConfirmMessage = getResources().getString(R.string.already_downloaded);
          showDialog(DIALOG_CONFIRM_DOWNLOAD);
        }
        return true;
      default:    
        return super.onOptionsItemSelected(item);
    }
  }

  // UI
  private Button mNewGameButton;
  private Button mPickLogButton;
  
  //
  // Data download
  //
  private boolean mDeleteFilesBeforeDownload;
  private String mDownloadConfirmMessage;
  private ProgressDialog mDownloadStatusDialog;
  private Downloader mDownloadController;
  
  private AlertDialog newConfirmDownloadDialog() {
    AlertDialog.Builder b = new AlertDialog.Builder(this);
    // Issue dummy calls to setTitle and setMessage. Otherwise, onPrepareDialog will become a noop.
    b.setTitle("");  
    b.setMessage("");
    b.setCancelable(true);
    b.setPositiveButton(android.R.string.yes,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface d, int item) {
            if (mDeleteFilesBeforeDownload) {
              mDeleteFilesBeforeDownload = false;
              Downloader.deleteFilesInDir(mExternalDir);
              mNewGameButton.setEnabled(false);
            }
            startDownload();
          }
        });
    b.setNegativeButton(android.R.string.no,
        new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface d, int item) {
        mDeleteFilesBeforeDownload = false;
        d.cancel();
      }
    });
    return b.create();
  }
  
  private String dbSourceUrl() {
    String dflt = getResources().getString(R.string.default_download_source_url);
    return mPrefs.getString("download_source_url", dflt);
  }
  private ProgressDialog newDownloadDialog() {
    // TODO(saito) screen rotation will abort downloading.
    ProgressDialog d = new ProgressDialog(this);
    d.setCancelable(true);
    d.setMessage(String.format("Downloading %s", dbSourceUrl()));
    d.setOnCancelListener(new DialogInterface.OnCancelListener() {
      public void onCancel(DialogInterface unused) {
        if (mDownloadController != null) mDownloadController.destroy();
      }
    });
    return d;
  }
  
  private void startDownload() {
    mDownloadController = new Downloader(
        mDownloadHandler, 
        mExternalDir);
    mDownloadController.start(dbSourceUrl());
    showDialog(DIALOG_DOWNLOAD_STATUS);
  }

  private final Downloader.EventListener mDownloadHandler = new Downloader.EventListener() {
    public void onProgressUpdate(String status) {
      if (mDownloadStatusDialog != null) {
        mDownloadStatusDialog.setMessage(status);
      }
    }
    
    public void onFinish(String status) {
      if (status == null) {  // success
        if (!hasRequiredFiles(mExternalDir)) {
          status = String.format("Failed to download required files to %s:", mExternalDir);
          for (String s: REQUIRED_FILES) status += " " + s;
        }
      }
      mDownloadStatusDialog.dismiss();
      if (mDownloadController != null) {
        mDownloadController.destroy();
        mDownloadController = null;
      }
      if (status != null) {
        mDownloadConfirmMessage = getResources().getString(R.string.restart_download_database) + status;
        mDeleteFilesBeforeDownload = true;
        showDialog(DIALOG_CONFIRM_DOWNLOAD);
      } else {
        mNewGameButton.setEnabled(true);
        initializeBonanzaInBackground();
      }
    }
  };
  
  private StartGameDialog mStartGameDialog;
  
  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
    case DIALOG_NEW_GAME: {
      mStartGameDialog = new StartGameDialog(
          this, 
          getResources().getString(R.string.new_game));
      mStartGameDialog.setOnClickStartButtonHandler(
          new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int id) {
             newGame2();
           }}
          );
      return mStartGameDialog.getDialog();
    }
    case DIALOG_CONFIRM_DOWNLOAD:
      return newConfirmDownloadDialog();
    case DIALOG_DOWNLOAD_STATUS:
      mDownloadStatusDialog = newDownloadDialog();
      return mDownloadStatusDialog;
    default:    
      return null;
    }
  }
  
  @Override
  protected void onPrepareDialog(int id, Dialog dialog, Bundle unused) {
    super.onPrepareDialog(id, dialog, unused);
    if (id == DIALOG_CONFIRM_DOWNLOAD) {
      ((AlertDialog)dialog).setMessage(mDownloadConfirmMessage);
    }
  }
  
  private void newGame() {
    if (!hasRequiredFiles(mExternalDir)) {
      // Note: this shouldn't happen, since the button is disabled if !hasRequiredFiles
      Toast.makeText(
          getBaseContext(),
          "Please download the shogi database files first",
          Toast.LENGTH_LONG).show();
    } else {
<<<<<<< HEAD
      Intent intent = new Intent(this, GameActivity.class);
      Board board = new Board();
      Handicap h = Handicap.parseInt(
          Integer.parseInt(mPrefs.getString("handicap", "0")));
      board.initialize(h);
      intent.putExtra("initial_board", board);
      startActivity(intent);
=======
      if (mStartGameDialog != null) {
        mStartGameDialog.loadPreferences();
      }
      showDialog(DIALOG_NEW_GAME);
>>>>>>> 6dd1df4c0767403869da41a0d5a257f8ba430372
    }
  }
  
  private void newGame2() {
    Intent intent = new Intent(this, GameActivity.class);
    Board b = new Board();
    Handicap h = Handicap.parseInt(Integer.parseInt(mPrefs.getString("handicap", "0")));
    b.initialize(h);
    intent.putExtra("initial_board", b);
    intent.putExtra("handicap", h);
    startActivity(intent);
  }

  private void pickLog() {
    startActivity(new Intent(this, GameLogListActivity.class));
  }
  
  private void settings() {
    startActivity(new Intent(this, ShogiPreferenceActivity.class));
  }
  
  private void help() {
    startActivity(new Intent(this, HelpActivity.class));
  }
  
  private class BonanzaInitializeThread extends Thread {
    @Override public void run() {
      BonanzaJNI.initialize(mExternalDir.getAbsolutePath());
    }
  }
  
  private void initializeBonanzaInBackground() {
    if (!hasRequiredFiles(mExternalDir)) {
      Toast.makeText(
          getBaseContext(),
          "Please download the shogi database files first",
          Toast.LENGTH_LONG).show();
      return;
    }
    new BonanzaInitializeThread().start();
  }
  
  /**
   * See if all the files required to run Bonanza are present in externalDir.
   */
  private static final String[] REQUIRED_FILES = {
    "book.bin", "fv.bin", "hash.bin"
  };
  public static boolean hasRequiredFiles(File externalDir) {
    for (String basename: REQUIRED_FILES) {
      File file = new File(externalDir, basename);
      if (!file.exists()) {
        Log.d(TAG, file.getAbsolutePath() + " not found");
        return false;
      }
    }
    return true;
  }
}
