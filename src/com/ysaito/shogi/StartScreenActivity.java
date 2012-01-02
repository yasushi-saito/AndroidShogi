package com.ysaito.shogi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The activity launched when the Shogi application starts
 *
 */
public class StartScreenActivity extends Activity {
  static final String TAG = "ShogiStart";
  static final int DIALOG_NEW_GAME = 1233;
  static final int DIALOG_INSTALL_SHOGI_DATA = 1234;
  static final int DIALOG_START_SHOGI_DATA = 1235;
  static final int DIALOG_FATAL_ERROR = 1236;
  private File mExternalDir;
  private SharedPreferences mPrefs;
  private String mErrorMessage;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.start_screen);
    mExternalDir = getExternalFilesDir(null);
    if (mExternalDir == null) {
      FatalError("Please mount the sdcard on the device");
      return;
    } else if (!hasRequiredFiles(mExternalDir)) {
      if (hasShogiDataApplication()) {
        showDialog(DIALOG_START_SHOGI_DATA);
      } else {
        showDialog(DIALOG_INSTALL_SHOGI_DATA);
      }
      return;
    }
    mPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

    Button newGameButton = (Button)findViewById(R.id.new_game_button);
    newGameButton.setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) { newGame(); }
    });

    Button pickLogButton = (Button)findViewById(R.id.pick_log_button);
    pickLogButton.setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) { 
        startActivity(new Intent(v.getContext(), GameLogListActivity.class));
      }
    });

    Button optusButton = (Button)findViewById(R.id.optus_button);
    optusButton.setOnClickListener(new Button.OnClickListener() {
      public void onClick(View v) { 
        startActivity(new Intent(v.getContext(), OptusPlayerListActivity.class));
      }
    });

    new BonanzaInitializeThread().start();
  }

  private final Intent newShogiDataIntent() {
    Intent intent = new Intent(Intent.ACTION_MAIN);
    intent.setComponent(new ComponentName(
        "com.ysaito.shogidata",
        "com.ysaito.shogidata.AndroidShogiDataActivity"));
    return intent;
  }
  
  private boolean hasShogiDataApplication() {
    List<ResolveInfo> list = getPackageManager().queryIntentActivities(
        newShogiDataIntent(),
        PackageManager.MATCH_DEFAULT_ONLY);  
    return list.size() > 0;  
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
      startActivity(new Intent(this, HelpActivity.class));
      return true;
    case R.id.start_screen_preferences_menu_id:
      startActivity(new Intent(this, ShogiPreferenceActivity.class));
      return true;
    default:    
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
    case DIALOG_NEW_GAME: {
      StartGameDialog d = new StartGameDialog(
          this, 
          getResources().getString(R.string.new_game),
          new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
              newGame2();
            }}
          );
      return d.getDialog();
    }
    case DIALOG_INSTALL_SHOGI_DATA: 
      return newInstallShogiDataDialog();
    case DIALOG_START_SHOGI_DATA: 
      return newStartShogiDataDialog();
    case DIALOG_FATAL_ERROR: 
      return newFatalErrorDialog();
    default:    
      return null;
    }
  }

  @Override protected void onPrepareDialog(int id, Dialog d) {
    if (id == DIALOG_FATAL_ERROR) {
      ((AlertDialog)d).setMessage(mErrorMessage);
    }
  }

  private void FatalError(String message) {
    mErrorMessage = message;
    showDialog(DIALOG_FATAL_ERROR);
  }

  private Dialog newFatalErrorDialog() {
    DialogInterface.OnClickListener cb = new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int id) {  };
    };
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    // The dialog message will be set in onPrepareDialog
    builder.setMessage("???") 
    .setCancelable(false)
    .setPositiveButton("Ok", cb);
    return builder.create();
  }

  static final String mMarketUri = new String("market://details?id=com.ysaito.shogidata");

  private Dialog newStartShogiDataDialog() {
    return new AlertDialog.Builder(this)
    .setMessage(R.string.start_shogi_data)
    .setCancelable(true)
    .setIcon(android.R.drawable.ic_dialog_info)
    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        startActivity(newShogiDataIntent());
      }
    })
    .create();
  }
  
  private Dialog newInstallShogiDataDialog() {
    final TextView message = new TextView(this);
    final SpannableString s = 
        new SpannableString(getText(R.string.shogi_data_download));
    Linkify.addLinks(s, Pattern.compile("market:[a-z/?.=&]+"), mMarketUri);
    message.setText(s);
    message.setMovementMethod(LinkMovementMethod.getInstance());

    return new AlertDialog.Builder(this)
    .setTitle("")
    .setCancelable(true)
    .setIcon(android.R.drawable.ic_dialog_info)
    .setPositiveButton(R.string.visit_marketplace, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Uri marketUri = Uri.parse(mMarketUri);
        Intent intent = new Intent(Intent.ACTION_VIEW).setData(marketUri);
        startActivity(intent);
      }
    })
    .setView(message)
    .create();
  }

  private void newGame() {
    if (!hasRequiredFiles(mExternalDir)) {
      // Note: this shouldn't happen, since the button is disabled if !hasRequiredFiles
      Util.showErrorDialog(
          getBaseContext(),
          "Please download the shogi database files first");
    } else {
      showDialog(DIALOG_NEW_GAME);
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

  private class BonanzaInitializeThread extends Thread {
    @Override public void run() {
      BonanzaJNI.initialize(mExternalDir.getAbsolutePath());
    }
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
