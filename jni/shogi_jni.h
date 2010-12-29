
#ifndef BONANZA_JNI_H
#define BONANZA_JNI_H

#include <android/log.h>  

#define DEBUG_TAG "NDK_Bonanza"

extern void CheckFailure(const char* file, int line, const char* msg);

#define CHECK(expr) if (!(expr)) CheckFailure(__FILE__, __LINE__, #expr);

#endif  // BONANZA_JNI_H
