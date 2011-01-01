// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

/**
 * @author saito@google.com (Yaz Saito)
 *
 *
 * Move represents a move by a human player
 */
public class Move implements java.io.Serializable {
  // The piece to move. 
  //
  // - The value is negative if player==Player.DOWN.
  // - If the piece is to be promoted, this field stores the promoted piece type.
  //   For example, if the piece is originally a "fu" and it is to be promoted
  //   to "to" at <to_x,to_y>, @p piece should be K_TO.
  public int piece;

  // The source and destination coordinates. Each value is in range
  // [0, Board.DIM).
  public int fromX, fromY, toX, toY;

  @Override public String toString() {
    return ("Piece: " + piece + " [" + fromX + "," + fromY + "]->[" +
        toX + "," + toY + "]");
  }
}
