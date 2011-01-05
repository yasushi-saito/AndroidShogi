// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

/**
 * Type of the player. "BLACK" is "sente" in japanese. BLACK makes the first move
 * of the game. "WHITE" is "gote" in japanese.
 */
public enum Player {
  INVALID,
  BLACK,
  WHITE;
  
  public final static Player opponentOf(Player p) {
    if (p == BLACK) return WHITE;
    if (p == WHITE) return BLACK;
    throw new AssertionError("Invalid player: " + p.toString());
  }
}
