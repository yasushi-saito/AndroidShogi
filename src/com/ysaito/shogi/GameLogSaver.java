package com.ysaito.shogi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

/**
 * Helper class for saving a GameLog object in SDCard.
 */
public class GameLogSaver {
  private final Context mContext;
  
  public GameLogSaver(Context context) {
    mContext = context;
  }
  
  /**
   * Save "log" in SDCard. The basename will be the digest string of the log.
   * On error, show a toast.
   */
  public void save(GameLog log) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    File logDir = new File(prefs.getString("game_log_dir", "/sdcard/ShogiGameLog"));
    File logFile = new File(logDir, log.digest() + ".kif");
    
    FileWriter out = null;
    try {
      try {
        logDir.mkdirs();
        out = new FileWriter(logFile);
        log.toKif(out);
        Toast toast = Toast.makeText(mContext, 
            "Saved log in " + logFile.getAbsolutePath(),
            Toast.LENGTH_LONG);
        toast.show();
      } finally {
        if (out != null) out.close();
      }
    } catch (IOException e) {
      Toast toast = Toast.makeText(mContext, 
          "Error saving log: " + e.getMessage(),
          Toast.LENGTH_LONG);
      toast.show();
      return;
    }
  }
}
