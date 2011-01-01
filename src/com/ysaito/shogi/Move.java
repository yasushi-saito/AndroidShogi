// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

/**
 * @author saito@google.com (Yaz Saito)
 *
 *
 * Move represents a move by a human player
 */
public class Move implements java.io.Serializable {
  // Player.UP or Player.DOWN
  public Player player;

  // The piece to move. The value is negative if player==Player.DOWN.
  public int piece;

  // The source and destination coordinates. Each value is in range
  // [0, Board.DIM).
  public int from_x, from_y, to_x, to_y;

  // If promote==true, promote the piece.
  //
  // Note: this field is not set by BoardView.  The EventListener impl in
  // Shogi class runs a dialog if the move allows for promotion, the Shogi
  // class will run a dialog and sets @v promote=true if the user indicates
  // the wish.
  //
  // @invariant if proto==true, then
  //    !Board.isPromoted(piece) && <to_x,to_y> is in opponent's territory.
  public boolean promote;

  @Override public String toString() {
    return ("Piece: " + piece + " [" + from_x + "," + from_y + "]->[" +
        to_x + "," + to_y + "](" + promote + ")");
  }
}
