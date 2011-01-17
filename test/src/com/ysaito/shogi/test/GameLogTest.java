package com.ysaito.shogi.test;

import java.io.IOException;
import java.io.InputStream;

import com.ysaito.shogi.GameLog;
import com.ysaito.shogi.ParseException;

import android.content.Context;
import android.content.res.Resources;
import android.test.InstrumentationTestCase;
import android.util.Log;

public class GameLogTest extends InstrumentationTestCase {
  static final String TAG = "MoveLogTest";
  private InputStream openFile(int id) {
    Context context = getInstrumentation().getContext();
    Resources resources = context.getResources();
    return resources.openRawResource(id);
  }
  
  public void testLoad() throws ParseException, IOException {
    Log.d(TAG, "StartTestLoad3");
    InputStream in = openFile(R.raw.kifu2);
    GameLog log = GameLog.fromKif(in);
    in.close();
    assertEquals(log.getAttr(GameLog.A_TITLE), "第60回NHK杯3回戦第6局");
    assertEquals(log.getAttr(GameLog.A_BLACK_PLAYER), "佐藤康光");
    assertEquals(log.getAttr(GameLog.A_WHITE_PLAYER), "久保利明");
    assertEquals(log.getAttr(GameLog.A_HANDICAP), "平手");
    assertEquals(log.getAttr(GameLog.A_TOURNAMENT), "ＮＨＫ杯");
    assertEquals(log.getMove(4).toCsaString(), "2625FU");
    assertEquals(log.getMove(94).toCsaString(), "7765KE");
    assertEquals(log.numMoves(), 157);
 }
}
