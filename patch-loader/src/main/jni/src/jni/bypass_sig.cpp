//
// Created by VIP on 2021/4/25.
//

#include "bypass_sig.h"
#include "elf_util.h"
#include "logging.h"
#include "native_util.h"
#include "patch_loader.h"
#include "utils/hook_helper.hpp"
#include "utils/jni_helper.hpp"

namespace lspd {

    std::string apkPath;
    std::string redirectPath;

    CREATE_HOOK_STUB_ENTRY(
            "__openat",
            int, __openat,
            (int fd, const char* pathname, int flag, int mode), {
                if (pathname == apkPath) {
                    LOGD("redirect openat");
                    return backup(fd, redirectPath.c_str(), flag, mode);
                }
                return backup(fd, pathname, flag, mode);
            });

    LSP_DEF_NATIVE_METHOD(void, SigBypass, enableOpenatHook, jstring origApkPath, jstring cacheApkPath) {
        auto sym_openat = SandHook::ElfImg("libc.so").getSymbAddress<void *>("__openat");
        auto r = HookSymNoHandle(handler, sym_openat, __openat);
        if (!r) {
            LOGE("Hook __openat fail");
            return;
        }
        lsplant::JUTFString str1(env, origApkPath);
        lsplant::JUTFString str2(env, cacheApkPath);
        apkPath = str1.get();
        redirectPath = str2.get();
        LOGD("apkPath %s", apkPath.c_str());
        LOGD("redirectPath %s", redirectPath.c_str());
    }

    static JNINativeMethod gMethods[] = {
            LSP_NATIVE_METHOD(SigBypass, enableOpenatHook, "(Ljava/lang/String;Ljava/lang/String;)V")
    };

    void RegisterBypass(JNIEnv* env) {
        REGISTER_LSP_NATIVE_METHODS(SigBypass);
    }
}
