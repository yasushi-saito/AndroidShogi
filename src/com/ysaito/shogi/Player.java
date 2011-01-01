// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

/**
 * @author saito@google.com (Your Name Here)
 *
 */
public enum Player {
  // Type of the players. "BLACK" is "sente" in japanese. It starts at the
  // bottom and moves up. "WHITE" is "gote" in japanese.
  BLACK (1), 
  INVALID (2), 
  WHITE (3);

  Player(int v) { value = v; }
  final int value;
  
  static String toDisplayString() {
    return "VALUE";
  }
}
