#include <jni.h>
#include <stdarg.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>

#include "shogi.h"
#include "shogi_jni.h"

// CAUTION: These constants must match the values defined in BonanzaJNI.java
#define R_OK 0
#define R_ILLEGAL_MOVE -1
#define R_CHECKMATE -2

static char* Basename(const char* path, char* buf, int buf_size) {
  const char* r = strrchr(path, '/');
  if (r == NULL) r = path;
  else r++;
  snprintf(buf, buf_size, "%s", r);
  return buf;
}

static void PrintExtraMessage(const char* fmt, va_list ap,
                              char* buf, int buf_size) {
  vsnprintf(buf, buf_size, fmt, ap);
  if (str_error != NULL) {
    strncat(buf, ": ", buf_size - 1);
    strncat(buf, str_error, buf_size - 1);
  }
}

void CheckFailure(const char* file, int line,
                  const char* label, const char* fmt, ...) {
  char msg_buf[256];
  char file_buf[256];
  msg_buf[0] = '\0';
  if (fmt != NULL) {
    va_list ap;
    va_start(ap, fmt);
    PrintExtraMessage(fmt, ap, msg_buf, sizeof(msg_buf));
    va_end(ap);
  }
  __android_log_print(ANDROID_LOG_FATAL, DEBUG_TAG,
		      "Aborted: %s:%d: %s %s",
                      Basename(file, file_buf, sizeof(file_buf)),
                      line, label, msg_buf);
  abort();
}

void CheckIntCmpFailure(const char* file, int line,
                        int a, int b,
                        const char* label,
                        const char* fmt, ...) {
  char msg_buf[256];
  char file_buf[256];
  msg_buf[0] = '\0';
  if (fmt != NULL) {
    va_list ap;
    va_start(ap, fmt);
    PrintExtraMessage(fmt, ap, msg_buf, sizeof(msg_buf));
    va_end(ap);
  }
  __android_log_print(ANDROID_LOG_FATAL, DEBUG_TAG,
		      "Aborted: %s:%d: %s %d<>%d %s",
                      Basename(file, file_buf, sizeof(file_buf)),
                      line, label, a, b, msg_buf);
  abort();
}

static void LogTree(const char* label, tree_t* ptree) {
  char msg[1024];
#if 0  // full board display. this is too verbose, so turn off
  for (int i = 0; i < 9; ++i) {
    sprintf(msg, "%d: ", i);
    for (int j = 0; j < 9; ++j) {
      char* p = msg + strlen(msg);
      sprintf(p, "%02d ", BOARD[i * 9 + j]);
    }
    LOG_DEBUG("%s: Board: %s", label, msg);
  }
#endif
  LOG_DEBUG("%s: captured black %x white %x material %x",
            label,
            ptree->posi.hand_black,
            ptree->posi.hand_white,
            ptree->posi.material);
  msg[0] = '\0';
  if (game_status & flag_quit) {
    strcat(msg, "quit ");
  }
  if (game_status & flag_mated) {
    strcat(msg, "mated ");
  }
  if (game_status & flag_resigned) {
    strcat(msg, "resigned ");
  }
  LOG_DEBUG("%s: game: %s", label, msg);
}

// Copy the board config (piece locations and captured pieces for each player)
// from "ptree" to "board".
static void FillBoard(const char* label,
                      JNIEnv* env,
		      tree_t* ptree, /* source */
		      jobject board /* dest */) {
  LogTree(label, ptree);

  jclass boardClass = (*env)->GetObjectClass(env, board);
  jfieldID fid = (*env)->GetFieldID(env, boardClass, "mSquares", "[I");
  jintArray jarray = (jintArray)((*env)->GetObjectField(env, board, fid));
  jint tmp[nsquare];
  for (int i = 0; i < nsquare; ++i) {
    tmp[i] = BOARD[i];
  }
  (*env)->SetIntArrayRegion(env,  jarray, 0, nsquare, tmp);

  fid = (*env)->GetFieldID(env, boardClass, "mCapturedBlack", "I");
  (*env)->SetIntField(env, board, fid, ptree->posi.hand_black);

  fid = (*env)->GetFieldID(env, boardClass, "mCapturedWhite", "I");
  (*env)->SetIntField(env, board, fid, ptree->posi.hand_white);
}

static void RunCommand(const char* command) {
  LOG_DEBUG("Run %s", command);
  strcpy(str_cmdline, command);
  CHECK_GE(procedure(&tree), 0);
}

static int jni_initialized = 0;

void Java_com_ysaito_shogi_BonanzaJNI_Initialize(
    JNIEnv *env,
    jclass unused_bonanza_class,
    jobject board) {
  CHECK(!jni_initialized);
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

jint Java_com_ysaito_shogi_BonanzaJNI_HumanMove(
    JNIEnv *env,
    jclass unused_bonanza_class,
    jint piece,
    jint from_x,
    jint from_y,
    jint to_x,
    jint to_y,
    jboolean promote,
    jobject board) {
  CHECK(jni_initialized);
  // The coordinates passed from Java are based on [0,0] at the upper-left
  // corner.  Translate them to the shogi coordinate, with [1,1] at the
  // upper-right corner.
  ++to_y;
  to_x = 9 - to_x;

  if (promote) {
    if (piece > 0) {
      piece += 8;
    } else {
      piece -= 8;
    }
  }

  CHECK2(piece != 0 && piece >= -15 && piece <= 15,
         "Piece: %d", piece);
  char buf[1024];
  const char* piece_name = astr_table_piece[abs(piece)];
  LOG_DEBUG("HumanMove: %s %d %d %d %d",
            piece_name,
            from_x, from_y, to_x, to_y);
  if (from_x < 0) {
    // Drop a captured piece
    snprintf(buf, sizeof(buf), "00%d%d%s", to_x, to_y, piece_name);
  } else {
    // Move piece on the board
    ++from_y;
    from_x = 9 - from_x;
    snprintf(buf, sizeof(buf),
             "%d%d%d%d%s", from_x, from_y, to_x, to_y, piece_name);
  }
  unsigned int move;
  int r = interpret_CSA_move(&tree, &move, buf);
  if (r < 0) {
    LOG_DEBUG("Failed to parse move: %s: %s", buf, str_error);
    FillBoard("Human", env, &tree, board);
    return R_ILLEGAL_MOVE;
  }
  r = make_move_root(&tree, move,
                     (flag_history | flag_time | flag_rep
                      | flag_detect_hang
                      | flag_rejections));
  if (r < 0) {
    LOG_DEBUG("Failed to make move: %s: %s", buf, str_error);
    FillBoard("Human", env, &tree, board);
    return R_ILLEGAL_MOVE;
  }

  FillBoard("Human", env, &tree, board);
  return R_OK;
}

jint Java_com_ysaito_shogi_BonanzaJNI_ComputerMove(
    JNIEnv *env,
    jclass unused_bonanza_class,
    jobject board) {
  __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "ComputerMove");
  CHECK_GE(com_turn_start(&tree, 0), 0);
  FillBoard("Computer", env, &tree, board);
  return R_OK;
}
