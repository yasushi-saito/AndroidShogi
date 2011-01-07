/**
 * 
 */
package com.ysaito.shogi.test;

import com.ysaito.shogi.Board;
import com.ysaito.shogi.Move;
import com.ysaito.shogi.Piece;

import android.test.AndroidTestCase;
import android.util.Log;

/**
 * @author saito
 *
 */
public class MoveTest extends AndroidTestCase {
  @Override public void setUp() {
    
  }
  
  public void testToCsaString() {
    Move move = new Move(Piece.FU, 1, 1, 1, 2);
    assertEquals(move.toCsaString(), "8283FU");
  }

  public void testTraditionalNotation() {
    Move m = newMove(Piece.FU, 7, 7, 7, 6); 
    assertEquals(toTraditionalNotation(m, newBoard()), "76FU");
    
    m = newMove(-Piece.FU, 7, 6, 7, 7); 
    assertEquals(toTraditionalNotation(m, newBoard()), "77FU");

    Board b = newBoard(7, 4, Piece.FU);
    m = newMove(Piece.TO, 7, 4, 7, 3); 
    assertEquals(toTraditionalNotation(m, b), "73FU/PROMOTE");
    
    b = newBoard();
    m = newMove(Piece.FU, -1, -1, 7, 3); 
    assertEquals(toTraditionalNotation(m, newBoard()), "73FU");
  }
  
  public void testTraditionalNotation2() {
    Board b = newBoard(7,9, Piece.KYO);
    Move m = newMove(Piece.KYO, -1, -1, 7, 8);
    Log.d("START", "START");
    assertEquals(toTraditionalNotation(m, b), "78KY/DROP");
    m = newMove(Piece.KYO, 7, 9, 7, 8);
    assertEquals(toTraditionalNotation(m, b), "78KY/FORWARD");
    m = newMove(Piece.KYO, 7, 9, 7, 1);
    assertEquals(toTraditionalNotation(m, b), "71KY/PROMOTE");
  }
  
  private String toTraditionalNotation(Move m, Board b) {  
    Move.TraditionalNotation n = m.toTraditionalNotation(b);
    String s = String.format("%d%d%s", n.x, n.y, Piece.csaNames[Board.type(n.piece)]);
    if ((n.modifier & Move.PROMOTE) != 0) {
      s += "/PROMOTE";
    }
    if ((n.modifier & Move.DROP) != 0) {
      s += "/DROP";
    }
    if ((n.modifier & Move.FORWARD) != 0) {
      s += "/FORWARD";
    }
    if ((n.modifier & Move.BACKWARD) != 0) {
      s += "/BACKWARD";
    }
    if ((n.modifier & Move.SIDEWAYS) != 0) {
      s += "/SIDEWAYS";
    }
    return s;
  }
  
  // The arg is a variadic list of <x, y, piece>.
  // <x, y> are in traditional coordinate to simplify testing.
  // That is, the upper-right corner is <1,1>. 
  private Board newBoard(int... values) { 
    Board b = new Board();
    assertEquals(values.length % 3, 0);
    for (int i = 0; i < values.length; i += 3) {
      int x = values[i];
      int y = values[i + 1];
      int piece = values[i + 2];
      b.setPiece(9 - x, y - 1, piece);
    }
    return b;
  }
  
  // <fx, fy> and <tx, ty> are in traditional coordinate to simplify testing.
  // That is, the upper-right corner is <1,1>. 
  private Move newMove(int piece, int fx, int fy, int tx, int ty) {
    if (fx < 0) {  // dropping caputured piece
      return new Move(piece, -1, -1, 9 - tx, ty - 1);
    } else {
      return new Move(piece, 9 - fx, fy - 1, 9 - tx, ty - 1);
    }
  }
}