package com.ysaito.shogi.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.Set;

import com.ysaito.shogi.GameLog;
import com.ysaito.shogi.ParseException;

import android.content.Context;
import android.content.res.Resources;
import android.test.InstrumentationTestCase;
import android.util.Log;

public class GameLogTest extends InstrumentationTestCase {
  static final String TAG = "MoveLogTest";
  private GameLog openKifFile(int id) throws ParseException, IOException {
    Resources resources = getInstrumentation().getContext().getResources();
    InputStreamReader in = new InputStreamReader(resources.openRawResource(id));
    GameLog log = GameLog.parseKif(in);
    in.close();
    return log;
  }
  
  private GameLog openHtmlFile(int id) throws ParseException, IOException {
    Resources resources = getInstrumentation().getContext().getResources();
    InputStream in = resources.openRawResource(id);
    GameLog log = GameLog.parseHtml(in);
    in.close();
    return log;
  }
  
  public void testParseDate() throws ParseException {
    GregorianCalendar c = new GregorianCalendar();
    c.setTimeInMillis(GameLog.TEST_parseDate("2011/1/10"));
    assertEquals(c.get(Calendar.YEAR), 2011);
    assertEquals(c.get(Calendar.MONTH), 1);
    assertEquals(c.get(Calendar.DAY_OF_MONTH), 10);
    
    c.setTimeInMillis(GameLog.TEST_parseDate("2011/1/10 11:23"));
    assertEquals(c.get(Calendar.HOUR_OF_DAY), 11);
    assertEquals(c.get(Calendar.MINUTE), 23);
    
    c.setTimeInMillis(GameLog.TEST_parseDate("2011/1/10 11:23:34"));
    assertEquals(c.get(Calendar.HOUR_OF_DAY), 11);
    assertEquals(c.get(Calendar.MINUTE), 23);
    Log.e(TAG, "EEE " + c.get(Calendar.SECOND) + "//" + c.get(Calendar.MILLISECOND));
    assertEquals(c.get(Calendar.SECOND), 34);    
  }
  
  public void testLoad() throws ParseException, IOException {
    Log.d(TAG, "StartTestLoad3");
    GameLog log = openKifFile(R.raw.kifu2);
    assertEquals(log.attr(GameLog.A_TITLE), "第60回NHK杯3回戦第6局");
    assertEquals(log.attr(GameLog.A_BLACK_PLAYER), "佐藤康光");
    assertEquals(log.attr(GameLog.A_WHITE_PLAYER), "久保利明");
    assertEquals(log.attr(GameLog.A_HANDICAP), "平手");
    assertEquals(log.attr(GameLog.A_TOURNAMENT), "ＮＨＫ杯");
    assertEquals(log.move(4).toCsaString(), "2625FU");
    assertEquals(log.move(94).toCsaString(), "7765KE");
    assertEquals(log.numMoves(), 157);
 }
  
  public void testLoad3() throws ParseException, IOException {
    GameLog log = openKifFile(R.raw.kifu3);
  }
  
  public void testLoad4() throws ParseException, IOException {
    GameLog log = openKifFile(R.raw.kifu4);
  }
  
  public void testHtml() throws ParseException, IOException {
    GameLog log = openHtmlFile(R.raw.download1);
    assertEquals(log.attr(GameLog.A_WHITE_PLAYER), "早川俊");
    assertEquals(log.move(125).toCsaString(), "0085KY");
    assertEquals(log.numMoves(), 134);
  }
  
  public void testHtml2() throws ParseException, IOException {
    GameLog log = openHtmlFile(R.raw.download2);
    assertEquals(log.attr(GameLog.A_WHITE_PLAYER), "上野裕和");
    assertEquals(log.attr(GameLog.A_BLACK_PLAYER), "稲葉陽");    
    assertEquals(log.move(64).toCsaString(), "7785KE");
    assertEquals(log.numMoves(), 121);
  }
  
  public void testToKif() throws ParseException, IOException {
    GameLog log = openKifFile(R.raw.kifu4);
    StringWriter w = new StringWriter();
    log.toKif(w);
    Log.d(TAG, "TESTSTART");
    StringReader r = new StringReader(w.toString());
    GameLog log2 = GameLog.parseKif(r);
    assertLogEquals(log, log2);
  }
  
  private void assertLogEquals(GameLog l1, GameLog l2) {
    assertEquals(l1.attrs().size(), l2.attrs().size());
    
    for (Map.Entry<String, String> e: l1.attrs()) {
      assertEquals(l2.attr(e.getKey()), e.getValue());
    }
    
    assertEquals(l1.numMoves(), l2.numMoves());
    for (int i = 0; i < l1.numMoves(); ++i) {
      assertTrue(l1.move(i).equals(l2.move(i)));
    }
  }
}