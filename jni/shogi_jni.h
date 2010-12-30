
#ifndef BONANZA_JNI_H
#define BONANZA_JNI_H

#ifdef ANDROID

#include <android/log.h>

#define DEBUG_TAG "NDK_Bonanza"

extern void CheckFailure(const char* file, int line, const char* msg);

#define CHECK(expr) if (!(expr)) CheckFailure(__FILE__, __LINE__, #expr);

#define LOG_DEBUG(...) \
  __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, __VA_ARGS__)

#define LOG_FATAL(...) \
  __android_log_print(ANDROID_LOG_FATAL, DEBUG_TAG, __VA_ARGS__)

#else  // !ANDROID

#define LOG_DEBUG(...) printf(__VA_ARGS__)

#endif // !ANDROID

#endif  // BONANZA_JNI_H
