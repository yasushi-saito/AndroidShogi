package com.ysaito.shogi;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;

public class StartGameDialog {
  private final Context mContext;
  private final SharedPreferences mPrefs;
  private final AlertDialog mDialog;
  private final DialogInterface.OnClickListener mOnClickStartButton;
  
  private Spinner mPlayerTypes;
  private Spinner mComputerDifficulty;
  private Spinner mHandicap;
  private CheckBox mFlipScreen;
  
  public StartGameDialog(
      Context context, 
      String title,
      DialogInterface.OnClickListener onClick) {
    mContext = context;
    mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    mOnClickStartButton = onClick;
    
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    LinearLayout layout = (LinearLayout)inflater.inflate(R.layout.start_game_dialog, null);
    mPlayerTypes = (Spinner)layout.findViewById(R.id.start_game_player_types);
    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(mContext, R.array.player_types,
        android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    mPlayerTypes.setAdapter(adapter);
    
    mComputerDifficulty = (Spinner)layout.findViewById(R.id.start_game_computer_difficulty);
    adapter = ArrayAdapter.createFromResource(mContext, R.array.computer_levels,
                android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    mComputerDifficulty.setAdapter(adapter);
    
    mHandicap = (Spinner)layout.findViewById(R.id.start_game_handicap);
    adapter = ArrayAdapter.createFromResource(mContext, R.array.handicap_types,
        android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    mHandicap.setAdapter(adapter);
    
    mFlipScreen = (CheckBox)layout.findViewById(R.id.start_game_flip_screen);
    
    loadPreferences();
    
    builder.setMessage(title)
      .setCancelable(true)
      .setPositiveButton(R.string.start_game, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
          savePreferences();
          if (mOnClickStartButton != null) {
            mOnClickStartButton.onClick(dialog, id);
          }
        }
       });
    
    builder.setView(layout);
    mDialog = builder.create();
    loadPreferences();
  }

  private void loadPreferences() {
    mPlayerTypes.setSelection(PlayerTypesToInt(mPrefs.getString("player_types", "0")));
    mComputerDifficulty.setSelection(Integer.parseInt(mPrefs.getString("computer_difficulty", "1")));
    mHandicap.setSelection(Integer.parseInt(mPrefs.getString("handicap", "0")));
    mFlipScreen.setChecked(mPrefs.getBoolean("flip_screen", false));
  }

  public AlertDialog getDialog() { return mDialog; }

  private void savePreferences() {
    boolean changed = false;
    SharedPreferences.Editor editor = mPrefs.edit();
    
    String playerTypes = IntToPlayerTypes(mPlayerTypes.getSelectedItemPosition());
    if (!mPrefs.getString("player_types", "0").equals(playerTypes)) {
      editor.putString("player_types", playerTypes);
      changed = true;
    }

    if (maybeSaveIntPreference(mComputerDifficulty.getSelectedItemPosition(), editor, "computer_difficulty", "1")) {
      changed = true;
    }
  
    if (maybeSaveIntPreference(mHandicap.getSelectedItemPosition(), editor, "handicap", "0")) {
      changed = true;
    }
    
    boolean flipScreen = mFlipScreen.isChecked();
    if (mPrefs.getBoolean("flip_screen", false) != flipScreen) {
      changed = true;
      editor.putBoolean("flip_screen", flipScreen);
    }
    
    if (changed) {
      editor.commit();
    }
  }
  
  private boolean maybeSaveIntPreference(int selectedValue, SharedPreferences.Editor editor, String key, String defaultValue) {
    if (Integer.parseInt(mPrefs.getString(key, defaultValue)) != selectedValue) {
      editor.putString(key, String.valueOf(selectedValue));
      return true;
    } else {
      return false;
    }
  }
  
  private static int PlayerTypesToInt(String s) {
    if (s.equals("HC")) return 0;
    if (s.equals("CH")) return 1;
    if (s.equals("HH")) return 2;    
    if (s.equals("CC")) return 3;
    return 0;
  }
    
  private static String IntToPlayerTypes(int v) {
    switch (v) {
    case 1: return "CH";
    case 2: return "HH";
    case 3: return "CC";
    default: return "HC";
    }
  }
}
