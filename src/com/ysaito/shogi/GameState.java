// Copyright 2010 Google Inc. All Rights Reserved.

package com.ysaito.shogi;

/**
 * @author saito@google.com (Your Name Here)
 *
 */
public enum GameState {
  ACTIVE,     // game ongoing
  // TODO(saito) rename to WHITE_WON and BLACK_WON
  BLACK_LOST,
  WHITE_LOST,
  DRAW,       // sennichite
}
