// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

/**
 * @author saito@google.com (Your Name Here)
 *
 */
public enum Player {
  // Type of the players. "BLACK" is "sente" in japanese. BLACK makes the first move
  // of the game. "WHITE" is "gote" in japanese.
  INVALID,
  BLACK,
  WHITE;
  
  public final static Player opponentOf(Player p) {
    if (p == BLACK) return WHITE;
    if (p == WHITE) return BLACK;
    throw new AssertionError("Invalid player: " + p.toString());
  }
}
