#include <jni.h>
#include <android/log.h>
#include "bugsnag.h"
#include <cstdlib>
#include <cstring>

bool bugsnag_on_error(void *event) {
    char* error_class = bugsnag_error_get_error_class(event);

    return strcmp(error_class, "SIGABRT") != 0;
}

void aborter(const char *abort_message) {
    bugsnag_notify("NativeCrash", abort_message, BSG_SEVERITY_ERR);
    bugsnag_add_on_error(bugsnag_on_error);
    std::abort();
}

extern "C"
#pragma clang diagnostic push
#pragma ide diagnostic ignored "UnusedParameter"
JNIEXPORT void JNICALL
Java_tk_zwander_lockscreenwidgets_App_setUpAborter(JNIEnv *env, jobject thiz) {
    if (__builtin_available(android 30, *)) {
        __android_log_set_aborter(aborter);
    }
}
#pragma clang diagnostic pop
