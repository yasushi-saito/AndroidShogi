package com.ysaito.shogi;

import java.util.ArrayList;

public class GameLog {
  public String title;
  public String blackPlayer;
  public String whitePlayer;
  public String handicap;
  public long date;  // UTC in millisec
  public ArrayList<Move> moves;
  
  private GameLog() { }
  
  static public GameLog fromKifString(String kif) throws ParseException {
    GameLog l = new GameLog();
    
    return l;
  }
}
