package com.ysaito.shogi;

import java.util.HashSet;
import java.util.Map;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

/**
 * Widget for game log properties, such as the names of the players, date, and venue.
 */
public class GameLogPropertiesView extends TableLayout {
  private Context mContext;
  
  public GameLogPropertiesView(Context context, AttributeSet attrs) {
    super(context, attrs);
    mContext = context;
  }
  public GameLogPropertiesView(Context context) {
    super(context);
    mContext = context;
  }
  
  public final void initialize(GameLog log) {
    HashSet<String> found = new HashSet<String>();
    final String dateString = getResources().getString(R.string.date);
    addView(addRow(dateString, log.dateString()));
    
    // Show STANDARD_ATTR_NAMES in the fixed order
    for (String attr : GameLog.STANDARD_ATTR_NAMES) {
      String value = log.attr(attr);
      if (value != null) {
        addView(addRow(attr, value));
        found.add(attr);
      }
    }
    
    // Show additional attributes not in STANDARD_ATTR_NAMES
    for (Map.Entry<String, String> e : log.attrs()) {
      if (!found.contains(e.getKey())) {
        addView(addRow(e.getKey(), e.getValue()));
      }
    }
    
    if (log.path() != null) {
      addView(addRow(getResources().getString(R.string.file), log.path().getAbsolutePath()));
    }
    addView(addRow(getResources().getString(R.string.num_plays), 
        String.format("%d", log.numPlays())));
  }
  
  private final TableRow addRow(String attr, String value) {
    TableRow tr = new TableRow(mContext);
    tr.setLayoutParams(new LayoutParams(
        LayoutParams.FILL_PARENT,
        LayoutParams.WRAP_CONTENT));   
    TextView attrView = new TextView(mContext);
    attrView.setText(attr);
    tr.addView(attrView);
    
    TextView valueView = new TextView(mContext);
    valueView.setText(value);
    tr.addView(valueView);
    return tr;
  }
}
