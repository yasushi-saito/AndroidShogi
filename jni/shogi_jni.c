#include <jni.h>
#include <pthread.h>
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
#define R_RESIGNED -3
#define R_DRAW -4
#define R_INSTANCE_DELETED -5

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static int g_instance_id = 0;
static int g_ini_called = 0;

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
    strcat(msg, "checkmate ");
  }
  if (game_status & flag_drawn) {
    strcat(msg, "draw ");
  }
  if (game_status & flag_resigned) {
    strcat(msg, "resigned ");
  }
  LOG_DEBUG("%s: game: %s", label, msg);
}

static void FillIntField(JNIEnv* env,
                         int value, jclass cls, jobject obj,
                         const char* field) {
  jfieldID fid = (*env)->GetFieldID(env, cls, field, "I");
  (*env)->SetIntField(env, obj, fid, value);
}

static int ExtractIntField(JNIEnv* env,
                        jclass cls, jobject obj,
                        const char* field) {
  jfieldID fid = (*env)->GetFieldID(env, cls, field, "I");
  return (*env)->GetIntField(env, obj, fid);
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

static void SetDifficulty(int difficulty,
                          int total_think_time_secs,
                          int per_turn_think_time_secs) {
  RunCommand("ponder off");

  node_limit   = UINT64_MAX;
  sec_limit    = total_think_time_secs;
  sec_limit_up = per_turn_think_time_secs;

  if (difficulty == 1) {
    depth_limit = 1;
  } else if (difficulty == 2) {
    depth_limit = 2;
  } else if (difficulty == 3) {
    depth_limit = 4;
  } else if (difficulty == 4) {
    depth_limit = 6;
  } else {
    depth_limit  = PLY_MAX;
  }
  LOG_DEBUG("Set difficult: #node=%llu #depth=%d total=%ds, per_turn=%ds",
            node_limit, depth_limit, sec_limit, sec_limit_up);
}

static int GameStatusToReturnCode() {
  CHECK2((game_status & flag_quit) == 0, "status: %x", game_status);
  if (game_status & flag_mated) {
    return R_CHECKMATE;
  }
  if (game_status & flag_drawn) {
    return R_DRAW;
  }
  if (game_status & flag_resigned) {
    return R_RESIGNED;
  }
  return R_OK;
}

jint Java_com_ysaito_shogi_BonanzaJNI_Initialize(
    JNIEnv *env,
    jclass unused_bonanza_class,
    jint difficulty,
    jint total_think_time_secs,
    jint per_turn_think_time_secs,
    jobject board) {
  pthread_mutex_lock(&g_lock);
  int instance_id = ++g_instance_id;

  LOG_DEBUG("Initializing Bonanza: d=%d t=%d p=%d",
            difficulty, total_think_time_secs, per_turn_think_time_secs);
  if (!g_ini_called) {
    if (ini(&tree) < 0) {
      LOG_FATAL("Failed to initialize Bonanza: %s", str_error);
    }
    g_ini_called = 1;
  }

  if (ini_game(&tree, &min_posi_no_handicap, flag_history, NULL, NULL) < 0) {
    LOG_FATAL("Failed to initialize game: %s", str_error);
  } else {
    LOG_DEBUG("Initialized Bonanza successfully");

    // Disable background thinking; we can't send a interrupt Bonanza
    // in the middle of thinking otherwise.
    //
    // TODO(saito) I must be missing something. Enable background
    // thinking.
    SetDifficulty(difficulty,
                  total_think_time_secs,
                  per_turn_think_time_secs);
    FillBoard("Init", env, &tree, board);
  }
  pthread_mutex_unlock(&g_lock);
  return instance_id;
}

jint Java_com_ysaito_shogi_BonanzaJNI_HumanMove(
    JNIEnv *env,
    jclass unused_bonanza_class,
    jint instance_id,
    jstring move_str,
    jobject board) {
  pthread_mutex_lock(&g_lock);
  if (instance_id != g_instance_id) {
    pthread_mutex_unlock(&g_lock);
    return R_INSTANCE_DELETED;
  }

  const char* tmp = (*env)->GetStringUTFChars(env, move_str, NULL);
  CHECK(tmp != NULL);
  int status = R_OK;
  unsigned int move;
  int r = interpret_CSA_move(&tree, &move, tmp);
  if (r < 0) {
    LOG_DEBUG("Failed to parse move: %s: %s", tmp, str_error);
    status = R_ILLEGAL_MOVE;
  } else {
    r = make_move_root(&tree, move,
                       (flag_history | flag_time | flag_rep
                        | flag_detect_hang
                        | flag_rejections));
    if (r < 0) {
      LOG_DEBUG("Failed to make move: %s: %s", tmp, str_error);
      FillBoard("Human", env, &tree, board);
      status = R_ILLEGAL_MOVE;
    } else {
      unsigned int move = last_pv.a[1];
      const char *str_move = str_CSA_move( move );
      LOG_DEBUG("Human: %x %s", move, str_move);
      status = GameStatusToReturnCode();
    }
  }
  (*env)->ReleaseStringUTFChars(env, move_str, tmp);
  FillBoard("Human", env, &tree, board);
  pthread_mutex_unlock(&g_lock);
  return status;
}

jint Java_com_ysaito_shogi_BonanzaJNI_ComputerMove(
    JNIEnv *env,
    jclass unused_bonanza_class,
    jint instance_id,
    jobject board,
    jobject dest_move) {
  int status = R_OK;
  pthread_mutex_lock(&g_lock);
  if (instance_id != g_instance_id) {
    status = R_INSTANCE_DELETED;
  } else {
    __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "ComputerMove");
    CHECK_GE(com_turn_start(&tree, 0), 0);

    unsigned int move = last_pv_save.a[1];
    const char *str_move = str_CSA_move( move );
    LOG_DEBUG("Comp: %x %s", move, str_move);
    FillBoard("Computer", env, &tree, board);

    jclass move_class = (*env)->GetObjectClass(env, dest_move);
    jfieldID fid = (*env)->GetFieldID(env, move_class,
                                      "move", "Ljava/lang/String;");
    (*env)->SetObjectField(env, dest_move, fid,
                           (*env)->NewStringUTF(env, str_move));
    status = GameStatusToReturnCode();
  }
  pthread_mutex_unlock(&g_lock);
  return status;
}

void Java_com_ysaito_shogi_BonanzaJNI_Abort(
    JNIEnv *env,
    jclass unused_bonanza_class) {
  LOG_DEBUG("Aborting the game");
  root_abort = 1;
}
