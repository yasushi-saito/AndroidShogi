package com.ysaito.shogi;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

public class GameLog {
  public static final String TAG = "GameLog";
  
  // Common keys for mAttrs.
  public static final String A_TITLE = "title";
  public static final String A_TOURNAMENT = "tourament";
  public static final String A_BLACK_PLAYER = "blackplayer";
  public static final String A_WHITE_PLAYER = "whiteplayer";
  public static final String A_HANDICAP = "handicap";
  
  private final HashMap<String, String> mAttrs;
  private long mDate;  // UTC in millisec
  private final ArrayList<Move> mMoves;

  @Override public String toString() {
    StringBuilder b = new StringBuilder();
    for (Map.Entry<String, String> e: mAttrs.entrySet()) {
      b.append(e.getKey());
      b.append(": ");
      b.append(e.getValue());
    }
    return b.toString();
  }
  
  @Override public int hashCode() {
    throw new AssertionError("hashCode not implemented");
  }

  private GameLog() { 
    mAttrs = new HashMap<String, String>();
    mMoves = new ArrayList<Move>();
  }
  
  public final String getAttr(String key) {
    return mAttrs.get(key);
  }
  
  public final long getDate() { return mDate; }
  
  public final Move getMove(int n) { return mMoves.get(n); }
  public final int numMoves() { return mMoves.size(); }
  
  private static final Pattern DATE_PATTERN = Pattern.compile("開始日時[：:](.*)");
  private static final Pattern MOVE_PATTERN = Pattern.compile("\\s*[0-9]+\\s+(.*)");  
  
  private static final HashMap<String, Pattern> mAttrPatterns;
  static {
    mAttrPatterns = new HashMap<String, Pattern>();
    mAttrPatterns.put(A_TITLE, Pattern.compile("表題[：:](.*)")); 
    mAttrPatterns.put(A_TOURNAMENT, Pattern.compile("棋戦[：:](.*)"));  
    mAttrPatterns.put(A_HANDICAP, Pattern.compile("手合割[：:](.*)"));    
    mAttrPatterns.put(A_BLACK_PLAYER, Pattern.compile("先手[：:](.*)"));    
    mAttrPatterns.put(A_WHITE_PLAYER, Pattern.compile("後手[：:](.*)"));    
  }

  static public GameLog fromKif(InputStream stream) throws ParseException {
    Log.d(TAG, "Start reading");
    GameLog l = new GameLog();
    Scanner scanner = new Scanner(stream);
    Move prevMove = null;
    Player curPlayer = Player.BLACK;
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      boolean matched = false;
      for (Map.Entry<String, Pattern> e: mAttrPatterns.entrySet()) {
        Matcher matcher = e.getValue().matcher(line);  
        if (matcher.matches()) {
          l.mAttrs.put(e.getKey(), matcher.group(1));
          matched = true;
          break;
        }
      }
      if (matched) continue;
      Matcher matcher = DATE_PATTERN.matcher(line);
      if (matcher.matches()) {
        l.mDate = parseDate(matcher.group(1));
        continue;
      }
      if (line.startsWith("手数")) {
        continue;
      }
      matcher = MOVE_PATTERN.matcher(line);
      if (matcher.matches()) {
        String moveString = matcher.group(1);
        if (moveString.startsWith("投了")) {
          continue;
        } else {
          Move m = Move.fromKifString(prevMove, curPlayer, moveString);
          l.mMoves.add(m);
          prevMove = m;
          curPlayer = Player.opponentOf(curPlayer);
        }
      }
    }
    return l;
  }
  
  private static final Pattern JAPANESE_DATE_PATTERN = Pattern.compile("(\\d{4})/(\\d{2})/(\\d{2})\\s*((\\d{2}):(\\d{2}):([\\d.]+))?");
  
  private static long parseDate(String s) throws ParseException {
    Matcher matcher = JAPANESE_DATE_PATTERN.matcher(s);
    if (!matcher.matches()) {
      throw new ParseException(s + ": failed to parse as date");
    }
    
    // TODO(saito) support multiple timezones
    Calendar c = new GregorianCalendar();
    Double secD = 0.0;
    int secI = 0;
    if (matcher.group(4) == null) {
      c.set(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
    } else {
      secD = Double.parseDouble(matcher.group(6));
      secI = secD.intValue();
      c.set(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)),
          Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5)), secI);
    }
    return c.getTimeInMillis() + (long)((secD - secI) * 1000);
  }
}
