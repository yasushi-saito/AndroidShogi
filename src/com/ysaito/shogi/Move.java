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
  // - The value is negative if player==Player.WHITE.
  // - If the piece is to be promoted, this field stores the promoted piece type.
  //   For example, if the piece is originally a "fu" and it is to be promoted
  //   to "to" at <to_x,to_y>, piece will store K_TO.
  public int piece;

  // The source and destination coordinates. When moving a piece on the board, each value is in range
  // [0, Board.DIM). When dropping a captured piece on the board, from_X = from_Y = -1.
  public int fromX, fromY, toX, toY;

  @Override public String toString() {
    return toCsaString();
  }
  
  // Return the CSA-format string for this move. 
  public final String toCsaString() {
	// This program uses defines the upper left corner of the board to be <0,0>, whereas CSA defines
    // the upper right corner to be <1, 1>. 
	// Translate the coordinate accordingly.
    int cFromX = 0;
    int cFromY = 0;
    if (fromX >= 0) {
      // Moving a piece on board
      cFromX = 9 - fromX;
      cFromY = fromY + 1;
    } else {
      // Dropping a captured piece
    }
    int cToX = 9 - toX;
    int cToY = toY + 1;
    int p = piece >= 0 ? piece : -piece;
    return String.format("%d%d%d%d%s", 
        cFromX, cFromY, cToX, cToY,
        Piece.csaNames[p]);
  }
  
  public static Move fromCsaString(String csa) {
    Move m = new Move();
    int tmp = csa.charAt(0) - '0';
    
    if (tmp > 0) {
      // Moving a piece on board
      m.fromX = 9 - tmp;
      m.fromY = csa.charAt(1) - '0' - 1;
    } else {
      // Dropping a captured piece
      m.fromX = m.fromY = -1;
    }
    m.toX = 9 - (csa.charAt(2) - '0');
    m.toY = csa.charAt(3) - '0' - 1;
    m.piece = Piece.fromCsaName(csa.substring(4));
    return m;
  }
}
