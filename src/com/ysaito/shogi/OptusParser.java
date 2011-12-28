package com.ysaito.shogi;


import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * Class for parsing web pages at http://wiki.optus.nu, aka Kifu database.
 * 
 * @author saito@google.com (Yaz Saito)
 */
public class OptusParser {
  private static final String TAG = "OptusParser";
  public static final String KISI_URL = "http://wiki.optus.nu/shogi/index.php?lan=jp&page=index_kisi";
  
  public interface PlayerListener {
    void added(Player[] players);
    void deleted(String[] player_names);
  }
  
  public static class Player implements Serializable {
    public Player(String n, int g, String[] refs) {
      name = n;
      num_games = g;
      hrefs = refs;
    }
    
    public final String name;
    
    // Total # of games played by this player.
    public final int num_games;
    
    // List of href links of the games played by this player. 
    public final String[] hrefs;
  }
  
  /**
   * List players in KISI_URL.
   *   
   * @param in Contents of web page KISI_URL. @p in will be closed by this method.
   * @return List of players found in @p in. 
   * @throws IOException
   */
  public static Player[] listPlayers(
      InputStream in) throws IOException {
    ArrayList<Player> players = new ArrayList<Player>();
    ArrayList<String> tmp_refs = new ArrayList<String>();
    String[] tmpString = new String[0];
    
    byte[] contents = Util.streamToBytes(in);
    Document doc = Jsoup.parse(
        new ByteArrayInputStream(contents),
        Util.detectEncoding(contents, null),
        "http://www.example.com");
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
