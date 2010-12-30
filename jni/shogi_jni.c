#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>

#include "shogi.h"
#include "shogi_jni.h"

void CheckFailure(const char* file, int line, const char* msg) {
  __android_log_print(ANDROID_LOG_FATAL, DEBUG_TAG,
		      "Assertion failure: %s:%d: %s", file, line, msg);
  abort();
}

// Copy the board config (piece locations and captured pieces for each player)
// from "ptree" to "board".
static void FillBoard(const char* label,
                      JNIEnv* env,
		      tree_t* ptree, /* source */
		      jobject board /* dest */) {
  jclass boardClass = (*env)->GetObjectClass(env, board);
  jfieldID fid = (*env)->GetFieldID(env, boardClass, "mSquares", "[I");
  jintArray jarray = (jintArray)((*env)->GetObjectField(env, board, fid));

  char msg[1024];
  for (int i = 0; i < 9; ++i) {
    sprintf(msg, "%d: ", i);
    for (int j = 0; j < 9; ++j) {
      char* p = msg + strlen(msg);
      sprintf(p, "%02d ", BOARD[i * 9 + j]);
    }
    LOG_DEBUG("%s: Board: %s", label, msg);
  }
  if (game_status & flag_quit) {
    LOG_DEBUG("%s: quit", label);
  }
  if (game_status & flag_mated) {
    LOG_DEBUG("%s: mated", label);
  }
  if (game_status & flag_resigned) {
    LOG_DEBUG("%s: resigned", label);
  }


  jint tmp[nsquare];
  for (int i = 0; i < nsquare; ++i) {
    tmp[i] = BOARD[i];
  }
  (*env)->SetIntArrayRegion(env,  jarray, 0, nsquare, tmp);
}

static void RunCommand(const char* command) {
  LOG_DEBUG("Run %s", command);
  strcpy(str_cmdline, command);
  CHECK(procedure(&tree) >= 0);
}

static int jni_active = 0;
static int jni_initialized = 0;

void Java_com_ysaito_shogi_BonanzaJNI_Initialize(
    JNIEnv *env,
    jclass unused_bonanza_class,
    jobject board) {
  CHECK(!jni_initialized);
  CHECK(!jni_active);
  jni_initialized = 1;

  LOG_DEBUG("Initializing Bonanza");
  if (ini(&tree) < 0) {
    LOG_FATAL("Failed to initialize Bonanza: %s", str_error);
  } else {
    LOG_DEBUG("Initialized Bonanza successfully");

    // Disable background thinking; we can't send a interrupt Bonanza
    // in the middle of thinking otherwise.
    //
    // TODO(saito) I must be missing something. Enable background
    // thinking.
    RunCommand("ponder off");
    RunCommand("limit time 5 5");
    FillBoard("Init", env, &tree, board);
  }
}

void Java_com_ysaito_shogi_BonanzaJNI_HumanMove(
    JNIEnv *env,
    jclass unused_bonanza_class,
    jint piece,
    jint from_x,
    jint from_y,
    jint to_x,
    jint to_y,
    jobject board) {
  CHECK(jni_initialized);
  CHECK(!jni_active);
  jni_active = 1;
  // The coordinates passed from Java are based on [0,0] at the upper-left
  // corner.  Translate them to the shogi coordinate, with [1,1] at the
  // upper-right corner.
  ++from_y;
  ++to_y;
  to_x = 9 - to_x;
  from_x = 9 - from_x;
  const char* piece_name = astr_table_piece[abs(piece)];
  LOG_DEBUG("HumanMove: %s %d %d %d %d",
            piece_name,
            from_x, from_y, to_x, to_y);

  char buf[1024];
  snprintf(buf, sizeof(buf),
           "%d%d%d%d%s", from_x, from_y, to_x, to_y, piece_name);
  unsigned int move;
  CHECK(interpret_CSA_move(&tree, &move, buf) >= 0);
  CHECK(make_move_root(&tree, move,
                       (flag_history | flag_time | flag_rep
                        | flag_detect_hang
                        | flag_rejections)) >= 0);
  FillBoard("Human", env, &tree, board);
  jni_active = 0;
}

void Java_com_ysaito_shogi_BonanzaJNI_ComputerMove(
    JNIEnv *env,
    jclass unused_bonanza_class,
    jobject board) {
  CHECK(!jni_active);
  jni_active = 1;
  __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "ComputerMove");
  CHECK(com_turn_start(&tree, 0) >= 0);
  FillBoard("Computer", env, &tree, board);
  jni_active = 0;
}
