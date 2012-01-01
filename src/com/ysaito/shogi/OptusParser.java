package com.ysaito.shogi;


import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for parsing web pages at http://wiki.optus.nu, aka Kifu database.
 * 
 * @author saito@google.com (Yaz Saito)
 */
public class OptusParser {
  private static final String TAG = "OptusParser";
  public static final String KISI_URL = "http://wiki.optus.nu/shogi/index.php?lan=jp&page=index_kisi";
  public static final String LOG_LIST_BASE_URL = "http://wiki.optus.nu/shogi/";

  // Parsed result of a player
  @SuppressWarnings("serial") 
  public static class Player implements Serializable {
    public String name;
    public int listOrder;   // The order of appearance in the web site (pronunciation order).
    public int numGames;    // Total # of games played by this player.
    public String[] hrefs;  // List of href links of the games played by this player. 
  }

  // Parsed result of a game log
  @SuppressWarnings("serial") 
  public static class LogRef implements Serializable {
    public String href;          // href to the play page
    public String tournament;
    public String blackPlayer;
    public String whitePlayer;
    public String date;
    public String openingMoves;  // 戦型
    public int numPlays;         // 手数
  }
  
  // Search parameters
  @SuppressWarnings("serial")
  public static class SearchParameters implements Serializable {
    public String player1;       // substring matching 
    public String player2;       // substring matching 
    public String startDate;     // YYYY-MM-DD
    public String endDate;       // YYYY-MM-DD
    public String tournament;    // "王位戦", etc
    public String openingMoves;  // "四間飛車", etc

    // Caution: toString() is used as the cache lookup key, so it must
    // an injection.
    public String toString() {
      StringBuilder b = new StringBuilder();
      if (player1 != null) b.append(" player1=").append(player1);
      if (player2 != null) b.append(" player2=").append(player2);
      if (startDate != null) b.append(" startDate=").append(startDate);
      if (endDate != null) b.append(" endDate=").append(endDate);
      if (openingMoves!= null) b.append(" openingMoves=").append(openingMoves);
      if (tournament != null) b.append(" tournament=").append(tournament);
      return b.toString();
    }
  }

  public static LogRef[] runSearch(SearchParameters q) {
    HttpClient httpClient = new DefaultHttpClient();
    HttpPost httpPost = new HttpPost("http://wiki.optus.nu/shogi/index.php");
    List<NameValuePair> p = new ArrayList<NameValuePair>();
    try {
      p.add(new BasicNameValuePair("cmd", "kif"));
      p.add(new BasicNameValuePair("cmds", "query3"));
      if (q.tournament != null) {
        p.add(new BasicNameValuePair("kisen_check", "checked"));
        p.add(new BasicNameValuePair("kisen", q.tournament));
      }
      if (q.player1 != null || q.player2 != null) {
        p.add(new BasicNameValuePair("kisi_check", "checked"));
        if (q.player1 != null) p.add(new BasicNameValuePair("kisi_a", q.player1));
        if (q.player2 != null) p.add(new BasicNameValuePair("kisi_b", q.player2));
      }
      if (q.openingMoves != null) {
        p.add(new BasicNameValuePair("senkei_check", "checked"));
        p.add(new BasicNameValuePair("senkei", q.openingMoves));
      }
      if (q.startDate != null || q.endDate != null) {
        p.add(new BasicNameValuePair("dplay_check", "checked"));
        if (q.startDate != null) p.add(new BasicNameValuePair("d_start", q.startDate));
        if (q.endDate != null) p.add(new BasicNameValuePair("d_end", q.endDate));
      }
      p.add(new BasicNameValuePair("pagex", "0"));
      p.add(new BasicNameValuePair("pagey", "100"));    
    
      // Optus only accepts EUC-JP encoding
      httpPost.setEntity(new UrlEncodedFormEntity(p, "EUC-JP"));
      
      ResponseHandler<String> responseHandler = new BasicResponseHandler();
      String response = httpClient.execute(httpPost, responseHandler);
      Log.d(TAG, "GOT: " + response);
      byte[] bytes = response.getBytes("SHIFT-JIS");
      
      ArrayList<LogRef> logs = new ArrayList<LogRef>();
      Document doc = Jsoup.parse(
          new ByteArrayInputStream(bytes),
          Util.detectEncoding(bytes, null),
          "http://www.example.com");
      Elements log_list = doc.select("tr:has(td:containsOwn(kid)) ~ tr");
      Log.d(TAG, "DOC: " + log_list.html());
      for (Element log : log_list) {
        // (0) ref
        // (1) tournament name
        // (2) black player name
        // (3) white player name
        // (4) date (YYYY-MM-DD)
        // (5) #plays
        LogRef l = new LogRef();
        l.href = log.child(0).child(0).attr("href");
        l.tournament = log.child(1).text();
        l.blackPlayer = log.child(2).text();
        l.whitePlayer = log.child(3).text();
        l.numPlays = Integer.parseInt(log.child(5).text());
        l.openingMoves = "";
        l.date = log.child(4).text();
        logs.add(l);
      }
      return logs.toArray(new LogRef[0]);
    } catch (Throwable e) {
      // TODO Auto-generated catch block
      Log.d(TAG, "Query failed: " + e.toString());
    }
    return null;
  }
  
  /**
   * List players in KISI_URL.
   *   
   * @return List of players found in @p in. 
   * @throws IOException
   */
  // TODO(saito) take URL
  public static Player[] listPlayers() throws IOException {
    URL url = new URL(KISI_URL);
    ArrayList<Player> players = new ArrayList<Player>();
    ArrayList<String> tmp_refs = new ArrayList<String>();
    String[] tmpString = new String[0];
    
    byte[] contents = Util.streamToBytes(url.openStream());
    Document doc = Jsoup.parse(
        new ByteArrayInputStream(contents),
        Util.detectEncoding(contents, null),
        "http://www.example.com");
    Elements player_list = doc.select("tr:has(td:containsOwn(名前)) ~ tr");
    
    int n = 0;
    for (Element player : player_list) {
      // The <td> columns of each line:
      // (0) player name (in kanji)
      // (1) player name (in hirakana)
      // (2) number of game logs
      // (3) list of ref links to the games
      tmp_refs.clear();
      Player p = new Player();
      p.listOrder = n++;
      p.name = player.child(0).text();
      p.numGames = Integer.parseInt(player.child(2).text());
      for (Element ref : player.child(3).children()) {
        tmp_refs.add(ref.attr("href"));
      }
      p.hrefs = tmp_refs.toArray(tmpString);
      players.add(p);
    }
    return players.toArray(new Player[0]);
  }
  
  /**
   * List game logs in a player's page
   * 
   */
  public static LogRef[] listLogsForPlayer(String relativeUrl) throws IOException {
    URL url = new URL(LOG_LIST_BASE_URL + relativeUrl);
    byte[] contents = Util.streamToBytes(url.openStream());
    Document doc = Jsoup.parse(
        new ByteArrayInputStream(contents),
        Util.detectEncoding(contents, null),
        "http://www.example.com");
    Elements log_list = doc.select("tr:has(td:containsOwn(kid)) ~ tr");
    ArrayList<LogRef> logs = new ArrayList<LogRef>();
    for (Element log : log_list) {
      // (0) ref
      // (1) tournament name
      // (2) black player name
      // (3) white player name
      // (4) date (YYYY-MM-DD)
      // (5) opening moves
      LogRef l = new LogRef();
      l.href = log.child(0).child(0).attr("href");
      l.tournament = log.child(1).text();
      l.blackPlayer = log.child(2).text();
      l.whitePlayer = log.child(3).text();
      l.date = log.child(4).text();
      l.openingMoves = log.child(5).text();
      logs.add(l);
    }
    return logs.toArray(new LogRef[0]);
  }
}
