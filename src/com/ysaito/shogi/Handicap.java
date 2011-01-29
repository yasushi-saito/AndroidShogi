package com.ysaito.shogi;

public enum Handicap {
  NONE,
  KYO,
  KAKU,
  HI,
  HI_KYO,
  HI_KAKU,
  FOUR,
  SIX;
  
  static Handicap parseInt(int i) {
    switch (i) {
    case 0: return NONE;
    case 1: return KYO;
    case 2: return KAKU;
    case 3: return HI;
    case 4: return HI_KYO;
    case 5: return HI_KAKU;
    case 6: return FOUR;
    case 7: return SIX;
    default: return NONE;
    }
  }
}
