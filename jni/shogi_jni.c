#include <jni.h>  
#include <string.h>  
#include <stdlib.h>
#include <android/log.h>  

#include "shogi.h"
#include "shogi_jni.h"
static tree_t g_tree;

void CheckFailure(const char* file, int line, const char* msg) {
  __android_log_print(ANDROID_LOG_FATAL, DEBUG_TAG, 
		      "Assertion failure: %s:%d: %s", file, line, msg);
  abort();
}

// Copy the board config (piece locations and captured pieces for each player)
// from "ptree" to "board".
static void fillBoard(JNIEnv* env, 
		      tree_t* ptree, /* source */
		      jobject board /* dest */) {
  jclass boardClass = (*env)->GetObjectClass(env, board);
  jfieldID fid2 = (*env)->GetFieldID(env, boardClass, "mCapturedSente", "I");
  __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, 
		      "FID for msente: %d", fid2);
  jfieldID fid = (*env)->GetFieldID(env, boardClass, "mSquares", "[I");
  __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, 
		      "FID for msquares: %d", fid);
  jintArray jarray = (jintArray)((*env)->GetObjectField(env, board, fid));

  jint tmp[nsquare];
  for (int i = 0; i < nsquare; ++i) {
    tmp[i] = BOARD[i];
  }
  (*env)->SetIntArrayRegion(env,  jarray, 0, nsquare, tmp);
}
		      

void Java_com_ysaito_shogi_BonanzaJNI_Initialize(JNIEnv *env,
						 jclass unused_bonanza_class,
						 jobject board) {
  __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "NDK:LC: [%s]", "foohah");
  if (ini(&g_tree) < 0) {
    __android_log_print(ANDROID_LOG_FATAL, DEBUG_TAG, 
			"Failed to initialize Bonanza: %s", str_error);
  } else {
    __android_log_print(ANDROID_LOG_FATAL, DEBUG_TAG, 
			"Initialized Bonanza successfully");

    // Disable background thinking; we can't send a interrupt Bonanza
    // in the middle of thinking otherwise.
    //
    // TODO(saito) I must be missing something. Enable background
    // thinking.
    strcpy(str_cmdline, "ponder off");
    CHECK(procedure(&g_tree) >= 0);

    strcpy(str_cmdline, "limit time 5 5");
    CHECK(procedure(&g_tree) >= 0);

    fillBoard(env, &g_tree, board);
  }
}  

