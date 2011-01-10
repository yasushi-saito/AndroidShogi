// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import java.util.ArrayList;

import android.util.Log;

/**
 * @author saito@google.com (Yaz Saito)
 *
 *
 * Move represents a move by a human player
 */
public class Move implements java.io.Serializable {
  private final String TAG = "ShogiMove";
  // The piece to move. 
  //
  // The value is negative if player==Player.WHITE.
  public final int piece;

  // The source and destination coordinates. When moving a piece on the board, each value is in range
  // [0, Board.DIM). When dropping a captured piece on the board, fromX = fromY = -1.
  public final int fromX, fromY, toX, toY;
  
  public Move(int p, int fx, int fy, int tx, int ty) {
    piece = p;
    fromX = fx;
    fromY = fy;
    toX = tx;
    toY = ty;
  }

  @Override public String toString() {
    return String.format("%d%d%d%d:%d", fromX, fromY, toX, toY, piece);
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
    int tmp = csa.charAt(0) - '0';
    int fromX, fromY;
    if (tmp > 0) {
      // Moving a piece on board
      fromX = 9 - tmp;
      fromY = csa.charAt(1) - '0' - 1;
    } else {
      // Dropping a captured piece
      fromX = fromY = -1;
    }
    int toX = 9 - (csa.charAt(2) - '0');
    int toY = csa.charAt(3) - '0' - 1;
    
    int piece = Piece.fromCsaName(csa.substring(4));
    
    return new Move(
        piece, fromX, fromY, toX, toY);
  }
  
  // Modifier bits. Used only by TraditionalNotation.modifier.
  public static final int DROP = (1 << 0);
  public static final int PROMOTE = (1 << 1);
  
  // Move direction bits
  public static final int FORWARD = (1 << 2);        
  public static final int BACKWARD = (1 << 3);
  public static final int SIDEWAYS = (1 << 4);

  // Location of this piece relative to other pieces that can move to
  // the same <toX, toY>.
  public static final int LEFT = (1 << 5);
  public static final int RIGHT = (1 << 6);    
  public static final int CENTER = (1 << 7);
  
  public static class TraditionalNotation {
    public TraditionalNotation(int p, int xx, int yy, int m) {
      piece = p;
      x = xx;
      y = yy;
      modifier = m;
    }
    
    @Override public String toString() {
      return String.format("%d <%d,%d>/%x", piece, x, y, modifier);
    }
    
    public final int piece;
    public final int x, y;
    public int modifier;  // bitstring, see below
  }
  
  public final TraditionalNotation toTraditionalNotation(Board board) {
    int modifier = 0;
    int pieceBeforeMove = piece;
    if (isNewlyPromoted(board)) {
      modifier |= PROMOTE;
      pieceBeforeMove = Board.unpromote(piece);
    }
    ArrayList<Board.Position> others = listOtherMoveSources(board);
    if (others.isEmpty()) {
      ;
    } else if (isDroppingCapturedPiece()) {
      modifier |= DROP;
    } else {
      int myMoveDir = moveDirection(fromX, fromY, toX, toY);
      modifier |= myMoveDir;
      
      boolean hasPieceWithSameMoveDir = false;
      int otherMoveDirs = 0;
      for (Board.Position p: others) {
        int dir = moveDirection(p.x, p.y, toX, toY);
        if (dir == myMoveDir) {
          hasPieceWithSameMoveDir = true;
        } else {
          otherMoveDirs |= dir;
        }
      }
      
      if (!hasPieceWithSameMoveDir) {
        // There's no piece that moves in the same direction to reach <tox,toy>.
        // Thus, the modifier can just specify the move direction of my piece to
        // disambiguate it from other legit moves.
      } else {
        // There are pieces that move in the same direction.
        // Determine the location of this piece relative to them.
        //
        // There are only three possibilites --- the piece is to the right,
        // to the left, or in the center of other pieces.
        int relPos = 0;
        for (Board.Position p: others) {
          if (moveDirection(p.x, p.y, toX, toY) == myMoveDir) {
            relPos |= relativePosition(fromX, fromY, p.x, p.y);
          }
        }
        if (relPos == (LEFT | RIGHT)) relPos = CENTER;
        modifier |= relPos;
      }
    }
    return new TraditionalNotation(pieceBeforeMove, 9 - toX, toY + 1, modifier);
  }
  
  private final boolean isDroppingCapturedPiece() { return fromX < 0; }
  
  private final boolean isNewlyPromoted(Board board) {
    if (isDroppingCapturedPiece()) return false; 
    boolean fromPromoted = Board.isPromoted(board.getPiece(fromX, fromY)); 
    boolean toPromoted = Board.isPromoted(piece);
    return toPromoted && !fromPromoted;
  }
  
  private final int moveDirection(int fx, int fy, int tx, int ty) {
    if (fx < 0) return DROP;
    if (fy == ty) return SIDEWAYS;
    if (Board.player(piece) == Player.BLACK) {
      return (ty < fy) ? FORWARD : BACKWARD; 
    } else {
      return (ty < fy) ? BACKWARD : FORWARD;
    }
  }

  private final int relativePosition(int x1, int y1, int x2, int y2) {
    if (Board.player(piece) == Player.BLACK) {
      return(x1 < x2) ? LEFT : RIGHT; 
    } else {
      return(x1 < x2) ? RIGHT : LEFT;
    }
  }
  
  // Find pieces other than the one at <fromX, fromX> 
  // that can move or can be dropped at<toX, toY>. 
  // For captured pieces, Position.x and Position.y
  // are both -1. 
  private final ArrayList<Board.Position> listOtherMoveSources(Board board) {
    ArrayList<Board.Position> list = new ArrayList<Board.Position>();
    for (int x = 0; x < Board.DIM; ++x) {
      for (int y = 0; y < Board.DIM; ++y) {
        if (x == fromX && y == fromY) continue;  // exclude this piece.
        int otherPiece = board.getPiece(x, y);

        // We need disambiguation only when there are two pieces of the same type that
        // can move to the same spot. 
        if (maybeUnpromote(otherPiece) != maybeUnpromote(piece)) continue;

        // If otherPiece can move to <fromX,  fromY>, then we need disambiguation
        for (Board.Position p : board.possibleMoveDestinations(x, y)) {
          if (p.x == toX && p.y == toY) {
            list.add(new Board.Position(x, y));
            break;
          }
        }
      }
    }
    
    if (board.getPiece(toX, toY) == 0 && !isDroppingCapturedPiece()) {
      // The destination is empty now, so we need to check if
      // there's a captured piece that can be dropped to <tox,toy>.
      Player me = Board.player(piece);
      for (Board.CapturedPiece cp: board.getCapturedPieces(me)) {
        boolean dropAllowed = true;
        if (Board.type(cp.piece) == Piece.FU) {
          // Don't allow double pawns
          for (int y = 0; y < Board.DIM; ++y) {
            int piece = board.getPiece(toX, y);
            if (Board.player(piece) == me &&
                Board.type(piece) == Piece.FU) {
              dropAllowed = false;
              break;
            }
          }
        }
        if (dropAllowed && cp.piece == piece) {
          list.add(new Board.Position(-1, -1));
          break;
        }
      }
    }
    return list;
  }
  
  static final int maybeUnpromote(int piece) {
    if (Board.isPromoted(piece)) return Board.unpromote(piece);
    return piece;
  }
}
