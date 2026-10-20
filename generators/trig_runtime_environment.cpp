// SPDX-License-Identifier: GPL-3.0-or-later
#include <jni.h>
#include <cfenv>
#include <cstdint>
#include <cstdio>
extern "C" JNIEXPORT jstring JNICALL Java_NativeMathEnvironment_environment(JNIEnv* env, jclass)
{
    std::uint64_t fpcr = 0;
#if defined(__aarch64__)
    asm volatile("mrs %0, fpcr" : "=r"(fpcr));
#endif
    char result[120];
    std::snprintf(result, sizeof(result), "{\"fegetround\":%d,\"FE_TONEAREST\":%d,\"fpcr_hex\":\"%llx\"}",
                  std::fegetround(), FE_TONEAREST, static_cast<unsigned long long>(fpcr));
    return env->NewStringUTF(result);
}
