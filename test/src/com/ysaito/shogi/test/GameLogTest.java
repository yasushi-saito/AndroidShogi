package com.ysaito.shogi.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.GregorianCalendar;

import com.ysaito.shogi.GameLog;
import com.ysaito.shogi.ParseException;

import android.content.Context;
import android.content.res.Resources;
import android.test.InstrumentationTestCase;
import android.util.Log;

public class GameLogTest extends InstrumentationTestCase {
  static final String TAG = "MoveLogTest";
  private GameLog openFile(int id) throws ParseException, IOException {
    Context context = getInstrumentation().getContext();
    Resources resources = context.getResources();
    InputStream in = resources.openRawResource(id);
    GameLog log = GameLog.fromKif(in);
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
    GameLog log = openFile(R.raw.kifu2);
    assertEquals(log.getAttr(GameLog.A_TITLE), "第60回NHK杯3回戦第6局");
    assertEquals(log.getAttr(GameLog.A_BLACK_PLAYER), "佐藤康光");
    assertEquals(log.getAttr(GameLog.A_WHITE_PLAYER), "久保利明");
    assertEquals(log.getAttr(GameLog.A_HANDICAP), "平手");
    assertEquals(log.getAttr(GameLog.A_TOURNAMENT), "ＮＨＫ杯");
    assertEquals(log.getMove(4).toCsaString(), "2625FU");
    assertEquals(log.getMove(94).toCsaString(), "7765KE");
    assertEquals(log.numMoves(), 157);
 }
  
  public void testLoad3() throws ParseException, IOException {
    GameLog log = openFile(R.raw.kifu3);
  }
  
  public void testLoad4() throws ParseException, IOException {
    GameLog log = openFile(R.raw.kifu4);
  }
}