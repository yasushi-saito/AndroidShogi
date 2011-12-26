package com.ysaito.shogi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.Toast;

import java.io.File;
import java.util.regex.Pattern;

/**
 * @author yasushi.saito@gmail.com 
 *
 */
public class StartScreenActivity extends Activity {
  static final String TAG = "ShogiStart";
  static final int DIALOG_NEW_GAME = 1233;
  static final int DIALOG_DATA_DOWNLOAD = 1234;
  static final int DIALOG_FATAL_ERROR = 1235;
  private File mExternalDir;
  private SharedPreferences mPrefs;
  private String mErrorMessage;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mExternalDir = getExternalFilesDir(null);
    if (mExternalDir == null) {
			FatalError("Please mount the sdcard on the device");
			return;
    }
    setContentView(R.layout.start_screen);
    mPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    
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
      showDialog(DIALOG_DATA_DOWNLOAD);
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
      default:    
        return super.onOptionsItemSelected(item);
    }
  }

  // UI
  private Button mNewGameButton;
  private Button mPickLogButton;
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
    case DIALOG_DATA_DOWNLOAD: 
    	return newDataDownloadDialog();
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
  
  private Dialog newDataDownloadDialog() {
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
				// TODO Auto-generated method stub
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
      Toast.makeText(
          getBaseContext(),
          "Please download the shogi database files first",
          Toast.LENGTH_LONG).show();
    } else {
      if (mStartGameDialog != null) {
        mStartGameDialog.loadPreferences();
      }
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
