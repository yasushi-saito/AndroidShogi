package com.ysaito.shogi;

import java.util.HashSet;
import java.util.Map;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

/**
 * Widget for displaying game status, such as elapsed time per player and 
 * last moves.
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
    for (String attr : GameLog.STANDARD_ATTR_NAMES) {
      String value = log.attr(attr);
      if (value != null) {
        addView(addRow(attr, value));
        found.add(attr);
      }
    }
    
    for (Map.Entry<String, String> e : log.attrs()) {
      if (!found.contains(e.getKey())) {
        addView(addRow(e.getKey(), e.getValue()));
      }
    }
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
