package com.ysaito.shogi;

import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

/*
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
*/
public class KifuDatabasePlayerListParser {
  private static final String TAG = "KifuPlayerList";
  
  public static class Player {
    public Player(String n, int g, String[] refs) {
      name = n;
      num_games = g;
      hrefs = refs;
    }
    
    public final String name;
    public final int num_games;
    public final String[] hrefs;
  }
  
  public static Player[] parse(InputStream in) throws IOException {
    ArrayList<Player> players = new ArrayList<Player>();
    ArrayList<String> tmp_refs = new ArrayList<String>();
    String[] tmpString = new String[0];
    
    byte[] contents = Util.streamToBytes(in);
    Document doc = Jsoup.parse(
        new ByteArrayInputStream(contents),
        Util.detectEncoding(contents, null),
        "http://www.example.com");
    Log.d(TAG, "Parsed");
    Elements player_list = doc.select("tr:has(td:containsOwn(名前)) ~ tr");
    
    for (Element player : player_list) {
      // The <td> columns of each line:
      // (0) player name (in kanji)
      // (1) player name (in hirakana)
      // (2) number of game logs
      // (3) list of ref links to the games
      tmp_refs.clear();
      String player_name = player.child(0).text();
      int num_games = Integer.parseInt(player.child(2).text());
      for (Element ref : player.child(3).children()) {
        tmp_refs.add(ref.attr("href"));
      }
      players.add(new Player(
          player_name, 
          num_games, 
          tmp_refs.toArray(tmpString)));
    }
    return players.toArray(new Player[0]);
  }
}
