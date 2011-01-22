package com.ysaito.shogi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

public class GameLog implements Serializable {
  public static final String TAG = "GameLog";
  
  // Common keys for mAttrs.
  public static final String A_TITLE = "title";
  public static final String A_LOCATION = "location";
  public static final String A_TIME_LIMIT = "timelimit";  
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
    mAttrPatterns.put(A_TIME_LIMIT, Pattern.compile("持ち時間[：:](.*)"));  
    mAttrPatterns.put(A_LOCATION, Pattern.compile("場所[：:](.*)"));  
    mAttrPatterns.put(A_HANDICAP, Pattern.compile("手合割[：:](.*)"));    
    mAttrPatterns.put(A_BLACK_PLAYER, Pattern.compile("先手[：:](.*)"));    
    mAttrPatterns.put(A_WHITE_PLAYER, Pattern.compile("後手[：:](.*)"));    
  }

  private static final Pattern HTML_KIF_START_PATTERN = Pattern.compile(".*>\\s*(開始日時|棋戦|場所|表題|手合割|先手|後手)[:：].*");
  private static final Pattern HTML_KIF_END_PATTERN = Pattern.compile("([^<]*)<.*");

  // Parse an embedded KIF file downloaded from http://wiki.optus.nu/.
  // Such a file can be created by saving a "テキスト表示" link directly to a file.
  //
  // This method assumes that the file is encoded in EUC-JP.
  public static GameLog fromHtmlStream(InputStream stream) throws ParseException {
    Reader in = null;
    try {
      in = new InputStreamReader(stream, "EUC_JP");
    } catch (UnsupportedEncodingException e1) {
      Log.e(TAG, "Failed to parse file (unsupported encoding): " + e1.getMessage());
      return null;
    }
    try {
      BufferedReader reader = new BufferedReader(in);
      String line;
      StringBuilder output = new StringBuilder();
      boolean kifFound = false;
      while ((line = reader.readLine()) != null && !kifFound) {
        Log.d(TAG, "Read: " + line);
        Matcher m = HTML_KIF_START_PATTERN.matcher(line);
        if (m.matches()) {
          output.append(m.group(1));
          output.append('\n');
          while ((line = reader.readLine()) != null) {
            m = HTML_KIF_END_PATTERN.matcher(line);
            if (m.matches()) {
              output.append(m.group(1));
              output.append('\n');
              kifFound = true;
              break;
            } else {
              output.append(line);
              output.append('\n');
            }
          }
          break;
        }
      }
      if (!kifFound) return null;
      return fromKifStream(new StringReader(output.toString()));
    } catch (IOException e2) {
      Log.e(TAG, "Failed to parse file: " + e2.getMessage());      
      return null;
    }
  }
  
  public static GameLog fromKifStream(Readable stream) throws ParseException {
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
      if (line.startsWith("まで")) {
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
      } else {
        Log.e(TAG, line + ": ignoring line");
      }
    }
    return l;
  }
  
  // Exposed for unittesting only.
  public static long TEST_parseDate(String s) throws ParseException {
    return parseDate(s);
  }
  
  // Parse Japanese date string found in KIF file. Support format is:
  //    YYYY/MM/DD [HH[:MM[:SS.SSS]]]
  private static long parseDate(String s) throws ParseException {
    Scanner scanner = new Scanner(s);
    scanner.useDelimiter("[/\\s　]");

    if (!scanner.hasNext()) throw new ParseException(s + ": failed to parse year");
    final int year = Integer.parseInt(scanner.next());
    if (!scanner.hasNext()) throw new ParseException(s + ": failed to parse month");
    final int month = Integer.parseInt(scanner.next());
    if (!scanner.hasNext()) throw new ParseException(s + ": failed to parse day");
    final int day = Integer.parseInt(scanner.next());
    
    int hour = 0;
    int minute = 0;
    int sec = 0;
    
    scanner.useDelimiter("[:\\s　]");    
    if (scanner.hasNext()) hour = Integer.parseInt(scanner.next());
    if (scanner.hasNext()) minute = Integer.parseInt(scanner.next());
    if (scanner.hasNext()) sec = Integer.parseInt(scanner.next());

    // TODO(saito) support multiple timezones
    Calendar c = new GregorianCalendar();
    c.set(year, month, day, hour, minute, sec);
    return c.getTimeInMillis();
  }
}
