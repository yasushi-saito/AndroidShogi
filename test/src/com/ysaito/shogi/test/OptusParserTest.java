// Copyright 2011 Google Inc. All Rights Reserved.

package com.ysaito.shogi.test;

import android.content.res.Resources;
import android.test.InstrumentationTestCase;
import android.util.Log;

import com.ysaito.shogi.OptusParser;
import com.ysaito.shogi.OptusParser.Player;

import java.io.InputStream;
import java.util.ArrayList;

/**
 * @author saito@google.com (Yaz Saito)
 *
 */
public class OptusParserTest extends InstrumentationTestCase {
  static final String TAG = "Test";
  
  private InputStream openRawFile(int resource_id) {
    Resources resources = getInstrumentation().getContext().getResources();
    return resources.openRawResource(resource_id);
  }

  public void testKisiParse() throws Throwable {
    Log.d(TAG, "testParse");
    InputStream in = openRawFile(R.raw.optus_kisi);
    OptusParser.Player[] players = OptusParser.listPlayers(in);
              
    assertEquals("愛達治", players[0].name);
    assertEquals(41, players[0].num_games);
    assertEquals(1, players[0].hrefs.length, 1);
    assertEquals(        
        "index.php?page=53b22a79b9c0959b50e1c8d6df106233",
        players[0].hrefs[0]);
    assertEquals(390, players.length);
    assertEquals("和田印哲", players[389].name);
    assertEquals(16, players[389].num_games);
    assertEquals(1, players[389].hrefs.length, 1);
    assertEquals(
        "index.php?page=4c8c3a08dd738c13bedf2ef9604e09c1",
        players[389].hrefs[0]);

  }
}
