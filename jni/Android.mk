LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := bonanza-jni
LOCAL_LDLIBS := -llog
LOCAL_SRC_FILES := \
 shogi_jni.c \
 data.c io.c proce.c utility.c ini.c attack.c book.c makemove.c \
 unmake.c time.c csa.c valid.c bitop.c iterate.c searchr.c search.c \
 quiesrch.c evaluate.c swap.c  hash.c root.c next.c movgenex.c \
 genevasn.c gencap.c gennocap.c gendrop.c mate1ply.c rand.c learn1.c \
 learn2.c evaldiff.c problem.c ponder.c thread.c sckt.c debug.c mate3.c \
 genchk.c

# Compile Options
#
# -DNDEBUG (DEBUG)  builds release (debug) version of Bonanza.
# -DMINIMUM         disables some auxiliary functions that are not necessary to
#                   play a game, e.g., book composition and optimization of
#                   evaluation function.
# -DTLP             enables thread-level parallel search.
# -DMPV             enables multi-PV search.
# -DCSA_LAN         enables bonanza to talk CSA Shogi TCP/IP protcol.
# -DNO_LOGGING      suppresses dumping log files.

LOCAL_CFLAGS := -DMINIMUM -DNDEBUG -std=gnu99 -DNO_LOGGING -DANDROID -DNO_STDOUT

include $(BUILD_SHARED_LIBRARY)

