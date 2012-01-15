package com.ysaito.shogi;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Comparator;

/**
 * Class for showing wiki.optus.nu statically generated pages for 
 * individual players. 
 * 
 * @author saito@google.com (Yaz Saito)
 *
 */
public class OptusPlayerListActivity extends GenericListActivity<OptusParser.Player> {
  private static final String TAG = "OptusPlayerList";
  private static final int DIALOG_SEARCH = 1234;
  private static final int DIALOG_START_DATE = 1235;  
  private static final int DIALOG_END_DATE = 1236;    
  
  private ListActivity mActivity;  // ==this
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    mActivity = this;
    super.onCreate(savedInstanceState);
    initialize(
        "@@player_list", /*cache key*/
        ExternalCacheManager.MAX_STATIC_PAGE_CACHE_STALENESS_MS,
        getResources().getString(R.string.player_list),
        new OptusParser.Player[0]/*tmp*/);
    startListing(GenericListActivity.MAY_READ_FROM_CACHE);
  }

  @Override 
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.optus_player_list_option_menu, menu);
    return true;
  }
  
  private static final Comparator<OptusParser.Player> BY_LIST_ORDER = new Comparator<OptusParser.Player>() {
    public int compare(OptusParser.Player p1, OptusParser.Player p2) {
      return p1.listOrder - p2.listOrder;
    }
    @Override 
    public boolean equals(Object o) { return o == this; }
  };
  
  private static final Comparator<OptusParser.Player> BY_NUMBER_OF_GAMES = new Comparator<OptusParser.Player>() {
    public int compare(OptusParser.Player p1, OptusParser.Player p2) {
      // Place players with many games first
      return -(p1.numGames - p2.numGames);
    }
    @Override 
    public boolean equals(Object o) { return o == this; }
  };
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_reload:
      startListing(GenericListActivity.FORCE_RELOAD);
      return true;
    case R.id.menu_sort_by_player:
      setSorter(BY_LIST_ORDER);
      return true;
    case R.id.menu_sort_by_number_of_games:
      setSorter(BY_NUMBER_OF_GAMES);
      return true;
    case R.id.menu_search: 
      showDialog(DIALOG_SEARCH);
      return true;
    }
    return false;
  }

  private static class PickedDate {
    public PickedDate(int y, int m, int d) { year = y; month = m; day = d; }
    public final int year, month, day;
  }
  
  PickedDate mStartDate;
  PickedDate mEndDate;
  TextView mStartDateTextView;
  TextView mEndDateTextView;  
  
  private void setSpinnerList(View layout, int spinner_id, int values_id) {
    Spinner spinner = (Spinner)layout.findViewById(spinner_id);
    ArrayAdapter<?> adapter = ArrayAdapter.createFromResource(
        this, values_id, android.R.layout.simple_spinner_item);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spinner.setAdapter(adapter);
  }
  
  private void setDateText(TextView view, PickedDate d) {
    if (d == null) {
      String s = getResources().getString(R.string.unspecified);
      view.setText(s);
    } else {
      view.setText(String.format("%04d-%02d-%02d", d.year, d.month, d.day));
    }
  }

  private final Dialog newStartDateDialog() { 
    final DatePickerDialog.OnDateSetListener listener = new DatePickerDialog.OnDateSetListener() {
      @Override public void onDateSet(DatePicker view, int year, int month, int day) {
        mStartDate = new PickedDate(year, month - Calendar.JANUARY + 1, day);
        setDateText(mStartDateTextView, mStartDate);
      }
    };
    return new DatePickerDialog(this, listener, 2000, 1, 1);
  }
  
  private final Dialog newEndDateDialog() {
    final DatePickerDialog.OnDateSetListener listener = new DatePickerDialog.OnDateSetListener() {
      @Override public void onDateSet(DatePicker view, int year, int month, int day) {
        mEndDate = new PickedDate(year, month - Calendar.JANUARY + 1, day);
        setDateText(mEndDateTextView, mEndDate);
      }
    };
    
    // By default, use today as the upper limit.
    Calendar c = Calendar.getInstance();
    return new DatePickerDialog(this, listener, 
        c.get(Calendar.YEAR), 
        c.get(Calendar.MONTH) - Calendar.JANUARY + 1,
        c.get(Calendar.DAY_OF_MONTH));
  }

  private void setAutoCompleteTextView(View layout, int id, String[] completions) {
    AutoCompleteTextView textView = (AutoCompleteTextView)layout.findViewById(id);
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
        this, 
        android.R.layout.simple_dropdown_item_1line,
        completions);
    textView.setAdapter(adapter);
  }

  private final Dialog newSearchDialog() {
    final Context context = this;
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View layout = (View)inflater.inflate(R.layout.optus_search_dialog, null);

    String[] PLAYERS = new String[numObjects()];
    for (int i = 0; i < PLAYERS.length; ++i) {
      PLAYERS[i] = getObjectAtPosition(i).name;
    }
    setAutoCompleteTextView(layout, R.id.optus_player1, PLAYERS);
    setAutoCompleteTextView(layout, R.id.optus_player2, PLAYERS);    
    setSpinnerList(layout, R.id.optus_tournament, R.array.tournament_values);
    setSpinnerList(layout, R.id.optus_opening_moves, R.array.opening_moves_values);
    
    mStartDateTextView = (TextView)layout.findViewById(R.id.optus_start_date_text);
    setDateText(mStartDateTextView, mStartDate);
    Button startDate = (Button)layout.findViewById(R.id.optus_start_date_button);
    startDate.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showDialog(DIALOG_START_DATE);
      }
    });
    
    mEndDateTextView = (TextView)layout.findViewById(R.id.optus_end_date_text);
    setDateText(mEndDateTextView, mEndDate);
    Button endDate = (Button)layout.findViewById(R.id.optus_end_date_button);
    endDate.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showDialog(DIALOG_END_DATE);
      }
    });
    
    builder.setView(layout);
    builder.setCancelable(true);
    builder.setPositiveButton(R.string.search, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface intf, int id) {
        AlertDialog d = (AlertDialog)intf;
        
          OptusParser.SearchParameters q = new OptusParser.SearchParameters();  
          String player1 = ((EditText)d.findViewById(R.id.optus_player1)).getText().toString().trim();
          if (player1.length() > 0) q.player1 = player1;

          String player2 = ((EditText)d.findViewById(R.id.optus_player2)).getText().toString().trim();
          if (player2.length() > 0) q.player2 = player2;
          
          Spinner spinner = (Spinner)d.findViewById(R.id.optus_opening_moves);
          q.openingMoves = (String)spinner.getSelectedItem();
          if (q.openingMoves.equals(spinner.getItemAtPosition(0))) {
            q.openingMoves = null;
          }
          
          spinner = (Spinner)d.findViewById(R.id.optus_tournament);
          q.tournament = (String)spinner.getSelectedItem();
          if (q.tournament.equals(spinner.getItemAtPosition(0))) {
            q.tournament = null;
          }

          final String unspecifiedDate = getResources().getString(R.string.unspecified);
          String s = mStartDateTextView.getText().toString();
          if (!s.equals(unspecifiedDate)) q.startDate = s;

          s = mEndDateTextView.getText().toString();
          if (!s.equals(unspecifiedDate)) q.endDate = s;
          
          Log.d(TAG, "QUERY=" + q.toString());
          Intent intent = new Intent(mActivity, OptusGameLogListActivity.class);
          intent.putExtra("search", q); 
          startActivity(intent);
        }
       });
      return builder.create();
    
  }
  
  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
    case DIALOG_START_DATE: return newStartDateDialog();
    case DIALOG_END_DATE: return newEndDateDialog();
    case DIALOG_SEARCH: return newSearchDialog();
    }
    return null;
  }
  
  @Override 
  public String getListLabel(OptusParser.Player p) { 
    if (p == null) return "";
    
    mBuilder.setLength(0);
    mBuilder.append(p.name)
    .append(" (")
    .append(p.numGames)
    .append("局)");
    return mBuilder.toString();
  }

  @Override
  public int numStreams() { return 1; }
  
  @Override
  public OptusParser.Player[] readNthStream(int index) throws Throwable { 
    return OptusParser.listPlayers();
  }
  
  // All calls to getListLabel() are from one thread, so share one builder.
  private final StringBuilder mBuilder = new StringBuilder();

  
  @Override 
  public void onListItemClick(ListView l, View v, int position, long id) {
    OptusParser.Player player = getObjectAtPosition(position);
    if (player != null) {
      Log.d(TAG, "Click: " + player.name);
      Intent intent = new Intent(this, OptusGameLogListActivity.class);
      intent.putExtra("player", player);
      startActivity(intent);
    }
  }
}
