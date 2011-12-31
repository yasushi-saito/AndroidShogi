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
import java.io.StringBufferInputStream;
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
    public Player(int lo, String n, int g, String[] refs) {
      listOrder = lo;
      name = n;
      numGames = g;
      hrefs = refs;
    }

    // The order of appearance in the web site. This is the pronunciation order.
    public final int listOrder;
    
    public final String name;
    
    // Total # of games played by this player.
    public final int numGames;
    
    // List of href links of the games played by this player. 
    public final String[] hrefs;
  }

  // Parsed result of a game log
  @SuppressWarnings("serial") 
  public static class LogRef implements Serializable {
    public String href;
    public String tournament;
    public String blackPlayer;
    public String whitePlayer;
    public String date;
    public String openingMoves;  // 戦型
    public int numPlays;         // 手数
  }
  
  @SuppressWarnings("serial")
  public static class SearchParameters implements Serializable {
    String blackPlayer;
    String whitePlayer;
    // TODO(saito): add more query conditions
  }
  
  public static LogRef[] runQuery(SearchParameters q) {
    HttpClient httpClient = new DefaultHttpClient();
    HttpPost httpPost = new HttpPost("http://wiki.optus.nu/shogi/index.php");
/**
    ------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="cmd"

kif
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="cmds"

query3
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="keyword"


------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="kisen"

½ç°ÌÀï
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="kisi_check"

checked
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="kisi_a"

º´Æ£
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="kisi_b"

Ãæ¸¶
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="senkei"

ÌðÁÒ
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="teai"

Ê¿¼ê
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="y_play1"

2011
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="m_play1"

00
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="d_play1"

00
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="y_play2"

2011
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="m_play2"

12
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="d_play2"

31
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="d_start"


------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="d_end"


------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="filename"


------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="pagex"

0
------WebKitFormBoundaryeXdNwTSeWKW6kYhB
Content-Disposition: form-data; name="pagey"

100
------WebKitFormBoundaryeXdNwTSeWKW6kYhB--
*/
    List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
    nameValuePairs.add(new BasicNameValuePair("cmd", "kif"));
    nameValuePairs.add(new BasicNameValuePair("cmds", "query3"));
    //nameValuePairs.add(new BasicNameValuePair("kisi_check", "checked"));
    //nameValuePairs.add(new BasicNameValuePair("kisi_a", "中原"));
    //nameValuePairs.add(new BasicNameValuePair("kisi_b", "大山"));
    nameValuePairs.add(new BasicNameValuePair("dplay_check", "checked"));
    nameValuePairs.add(new BasicNameValuePair("d_start", "2011-12-01"));
    nameValuePairs.add(new BasicNameValuePair("d_end", "2011-12-31"));    
    nameValuePairs.add(new BasicNameValuePair("pagex", "0"));
    nameValuePairs.add(new BasicNameValuePair("pagey", "500"));    
    
    try {
      httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
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
      e.printStackTrace();
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
      String player_name = player.child(0).text();
      int num_games = Integer.parseInt(player.child(2).text());
      for (Element ref : player.child(3).children()) {
        tmp_refs.add(ref.attr("href"));
      }
      players.add(new Player(
          n,
          player_name, 
          num_games, 
          tmp_refs.toArray(tmpString)));
      ++n;
    }
    return players.toArray(new Player[0]);
  }
  
  public static LogRef[] listLogRefs(String relativeUrl) throws IOException {
    URL url = new URL(LOG_LIST_BASE_URL + relativeUrl);
    ArrayList<LogRef> logs = new ArrayList<LogRef>();
    byte[] contents = Util.streamToBytes(url.openStream());
    Document doc = Jsoup.parse(
        new ByteArrayInputStream(contents),
        Util.detectEncoding(contents, null),
        "http://www.example.com");
    Elements log_list = doc.select("tr:has(td:containsOwn(kid)) ~ tr");
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
