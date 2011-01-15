// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

/**
 * @author saito@google.com (Yaz Saito)
 *
 *
 * Move represents a move by a player
 */
public class Move implements java.io.Serializable {
  private static final String TAG = "ShogiMove";
  
  // Japanese move display support
  public static final String japaneseNumbers[] = {
    null, "一", "二", "三", "四", "五", "六", "七", "八", "九",    
  };
  
  // The piece to move. 
  //
  // The value is negative if player==Player.WHITE.
  private final int mPiece;

  // The source and destination coordinates. When moving a piece on the board, each value is in range
  // [0, Board.DIM). When dropping a captured piece on the board, fromX = fromY = -1.
  private final int mFromX, mFromY, mToX, mToY;
  
  public Move(int p, int fx, int fy, int tx, int ty) {
    mPiece = p;
    mFromX = fx;
    mFromY = fy;
    mToX = tx;
    mToY = ty;
  }

  public final int getPiece() { return mPiece; }
  public final int getFromX() { return mFromX; }
  public final int getFromY() { return mFromY; }  
  public final int getToX() { return mToX; }
  public final int getToY() { return mToY; }  
  
  @Override public String toString() {
    return String.format("%d%d%d%d:%d", mFromX, mFromY, mToX, mToY, mPiece);
  }
  
  // Return the CSA-format string for this move. 
  public final String toCsaString() {
    // This program uses defines the upper left corner of the board to be <0,0>, whereas CSA defines
    // the upper right corner to be <1, 1>. 
    // Translate the coordinate accordingly.
    int cFromX = 0;
    int cFromY = 0;
    if (mFromX >= 0) {
      // Moving a piece on board
      cFromX = 9 - mFromX;
      cFromY = mFromY + 1;
    } else {
      // Dropping a captured piece
    }
    int cToX = 9 - mToX;
    int cToY = mToY + 1;
    int p = mPiece >= 0 ? mPiece : -mPiece;
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
  
  
  private static final Pattern KIF_MOVE_PATTERN = Pattern.compile("([1-9１-９])(.)([^\\(]+)\\((.)(.)\\)$");
  private static final Pattern KIF_MOVE2_PATTERN = Pattern.compile("同\\s*([^\\(]+)\\((.)(.)\\)$");
  private static final Pattern KIF_DROP_PATTERN = Pattern.compile("([1-9１-９])(.)(.*)$");
  // Parse a KIF-format string. It looks like
  // "８四歩(83)" (move FU at 83 to 84).
  public static final Move fromKifString(Move prevMove, Player player, String kifMove) throws ParseException {
    Matcher m = KIF_MOVE_PATTERN.matcher(kifMove);
    try {
      if (m.matches()) {
        return new Move(japaneseToPiece(player, m.group(3)),
            arabicToXCoord(m.group(4)), arabicToYCoord(m.group(5)),
            arabicToXCoord(m.group(1)), japaneseToYCoord(m.group(2)));
      }
      if (prevMove != null) {
        m = KIF_MOVE2_PATTERN.matcher(kifMove);
        if (m.matches()) {
          return new Move(japaneseToPiece(player, m.group(1)), 
              arabicToXCoord(m.group(2)), arabicToYCoord(m.group(3)),
              prevMove.mToX, prevMove.mToY);
        }
      }
      m = KIF_DROP_PATTERN.matcher(kifMove);
      if (m.matches()) {
        return new Move(japaneseToPiece(player, m.group(3)), 
            -1, -1, arabicToXCoord(m.group(1)), japaneseToYCoord(m.group(2)));
      }
      throw new ParseException("Failed to parse " + kifMove);
    } catch (NumberFormatException e) {
      throw new ParseException("Failed to parse " + kifMove + ": " + e.getMessage()); 
    }
  }

  private static int arabicToXCoord(String s) throws NumberFormatException {
    return 9 - Integer.parseInt(s);
  }

  private static int arabicToYCoord(String s) throws NumberFormatException {
    return Integer.parseInt(s) - 1;
  }

  private static int japaneseToYCoord(String s) throws NumberFormatException {
    for (int i = 0; i < japaneseNumbers.length; ++i) {
      if (s.equals(japaneseNumbers[i])) return i - 1;
    }
    throw new NumberFormatException(s + ": is not a japanese numeric string");
  }

  private static int japaneseToPiece(Player player, String s) throws NumberFormatException {
    final boolean promoted = (s.indexOf("成") >= 0);
    for (int i = 0; i < Piece.TO; ++i) {
      final String pieceName = Piece.japaneseNames[i];
      if (pieceName != null && s.indexOf(pieceName) >= 0) {
        int piece = i;
        if (promoted) piece = Board.promote(piece);
        return (player == Player.BLACK) ? piece : -piece;
      }
    }
    throw new NumberFormatException(s + ": Failed to parse as a japanese Shogi piece name");
  }

  // Modifier bits. Used by TraditionalNotation.modifier.
  
  public static final int DROP = (1 << 0);    // dropping a captured piece
  public static final int PROMOTE = (1 << 1); // newly promoting a piece
  
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
    
    public final int piece; // Piece type. Negative for Player.WHITE. Never promoted.
    public final int x, y;  // destination square
    public int modifier;    // bitstring, see above.
  }
  
  public final TraditionalNotation toTraditionalNotation(Board board) {
    int modifier = 0;
    int pieceBeforeMove = mPiece;
    if (isNewlyPromoted(board)) {
      modifier |= PROMOTE;
      pieceBeforeMove = Board.unpromote(mPiece);
    }
    ArrayList<Board.Position> others = listOtherMoveSources(board);
    if (others.isEmpty()) {
      ;
    } else if (isDroppingCapturedPiece()) {
      modifier |= DROP;
    } else {
      int myMoveDir = moveDirection(board, mFromX, mFromY, mToX, mToY);
      modifier |= myMoveDir;
      
      boolean hasPieceWithSameMoveDir = false;
      int otherMoveDirs = 0;
      for (Board.Position p: others) {
        int dir = moveDirection(board, p.x, p.y, mToX, mToY);
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
          if (moveDirection(board, p.x, p.y, mToX, mToY) == myMoveDir) {
            relPos |= relativePosition(mFromX, mFromY, p.x, p.y);
          }
        }
        if (relPos == (LEFT | RIGHT)) relPos = CENTER;
        modifier |= relPos;
      }
    }
    return new TraditionalNotation(pieceBeforeMove, 9 - mToX, mToY + 1, modifier);
  }
  
  private final boolean isDroppingCapturedPiece() { return mFromX < 0; }
  
  private final boolean isNewlyPromoted(Board board) {
    if (isDroppingCapturedPiece()) return false; 
    boolean fromPromoted = Board.isPromoted(board.getPiece(mFromX, mFromY)); 
    boolean toPromoted = Board.isPromoted(mPiece);
    return toPromoted && !fromPromoted;
  }
  
  private static final int moveDirection(Board b, int fx, int fy, int tx, int ty) {
    if (fx < 0) return DROP;
    if (fy == ty) return SIDEWAYS;
    final int piece = b.getPiece(fx, fy);
    if (Board.player(piece) == Player.BLACK) {
      return (ty < fy) ? FORWARD : BACKWARD; 
    } else {
      return (ty < fy) ? BACKWARD : FORWARD;
    }
  }

  private final int relativePosition(int x1, int y1, int x2, int y2) {
    if (Board.player(mPiece) == Player.BLACK) {
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
        if (x == mFromX && y == mFromY) continue;  // exclude this piece.
        int otherPiece = board.getPiece(x, y);

        // We need disambiguation only when there are two pieces of the same type that
        // can move to the same spot. 
        if (maybeUnpromote(otherPiece) != maybeUnpromote(mPiece)) continue;

        // If otherPiece can move to <fromX,  fromY>, then we need disambiguation
        for (Board.Position p : board.possibleMoveDestinations(x, y)) {
          if (p.x == mToX && p.y == mToY) {
            list.add(new Board.Position(x, y));
            break;
          }
        }
      }
    }
    
    if (board.getPiece(mToX, mToY) == 0 && !isDroppingCapturedPiece()) {
      // The destination is empty now, so we need to check if
      // there's a captured piece that can be dropped to <tox,toy>.
      Player me = Board.player(mPiece);
      for (Board.CapturedPiece cp: board.getCapturedPieces(me)) {
        boolean dropAllowed = true;
        if (Board.type(cp.piece) == Piece.FU) {
          // Don't allow double pawns
          for (int y = 0; y < Board.DIM; ++y) {
            int piece = board.getPiece(mToX, y);
            if (Board.player(piece) == me &&
                Board.type(piece) == Piece.FU) {
              dropAllowed = false;
              break;
            }
          }
        }
        if (dropAllowed && cp.piece == mPiece) {
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
