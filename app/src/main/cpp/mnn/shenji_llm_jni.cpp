//
// shenji_llm_jni.cpp
// 神迹输入法LLM JNI接口实现
//

#include <jni.h>
#include <android/log.h>
#include <string>
#include "llm_session.h"

#define LOG_TAG "ShenjiLLMJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_shenji_aikeyboard_llm_ShenjiLLMEngine_nativeInit(JNIEnv *env, jobject thiz, jstring config_path, jstring model_dir) {
    try {
        const char* config_path_str = env->GetStringUTFChars(config_path, nullptr);
        const char* model_dir_str = env->GetStringUTFChars(model_dir, nullptr);
        
        LOGI("Native init called with config: %s, model_dir: %s", config_path_str, model_dir_str);
        
        LlmSession* session = new LlmSession();
        bool success = session->init(std::string(config_path_str), std::string(model_dir_str));
        
        env->ReleaseStringUTFChars(config_path, config_path_str);
        env->ReleaseStringUTFChars(model_dir, model_dir_str);
        
        if (success) {
            LOGI("Native init successful, session pointer: %p", session);
            return reinterpret_cast<jlong>(session);
        } else {
            LOGE("Native init failed");
            delete session;
            return 0;
        }
    } catch (const std::exception& e) {
        LOGE("Exception in native init: %s", e.what());
        return 0;
    }
}

JNIEXPORT jstring JNICALL
Java_com_shenji_aikeyboard_llm_ShenjiLLMEngine_nativeGenerate(JNIEnv *env, jobject thiz, jlong session_ptr, jstring input, jint max_tokens) {
    try {
        LlmSession* session = reinterpret_cast<LlmSession*>(session_ptr);
        if (!session) {
            LOGE("Invalid session pointer");
            return env->NewStringUTF("");
        }
        
        const char* input_str = env->GetStringUTFChars(input, nullptr);
        LOGI("Native generate called with input: %s, max_tokens: %d", input_str, max_tokens);
        
        std::string result = session->generate(std::string(input_str), max_tokens);
        
        env->ReleaseStringUTFChars(input, input_str);
        
        LOGI("Native generate result: %s", result.c_str());
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception& e) {
        LOGE("Exception in native generate: %s", e.what());
        return env->NewStringUTF("");
    }
}

JNIEXPORT void JNICALL
Java_com_shenji_aikeyboard_llm_ShenjiLLMEngine_nativeReset(JNIEnv *env, jobject thiz, jlong session_ptr) {
    try {
        LlmSession* session = reinterpret_cast<LlmSession*>(session_ptr);
        if (!session) {
            LOGE("Invalid session pointer for reset");
            return;
        }
        
        LOGI("Native reset called");
        session->reset();
    } catch (const std::exception& e) {
        LOGE("Exception in native reset: %s", e.what());
    }
}

JNIEXPORT void JNICALL
Java_com_shenji_aikeyboard_llm_ShenjiLLMEngine_nativeRelease(JNIEnv *env, jobject thiz, jlong session_ptr) {
    try {
        LlmSession* session = reinterpret_cast<LlmSession*>(session_ptr);
        if (!session) {
            LOGE("Invalid session pointer for release");
            return;
        }
        
        LOGI("Native release called");
        delete session;
    } catch (const std::exception& e) {
        LOGE("Exception in native release: %s", e.what());
    }
}

JNIEXPORT jboolean JNICALL
Java_com_shenji_aikeyboard_llm_ShenjiLLMEngine_nativeIsLoaded(JNIEnv *env, jobject thiz, jlong session_ptr) {
    try {
        LlmSession* session = reinterpret_cast<LlmSession*>(session_ptr);
        if (!session) {
            LOGE("Invalid session pointer for isLoaded");
            return JNI_FALSE;
        }
        
        bool loaded = session->isLoaded();
        LOGI("Native isLoaded called, result: %s", loaded ? "true" : "false");
        return loaded ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        LOGE("Exception in native isLoaded: %s", e.what());
        return JNI_FALSE;
    }
}

JNIEXPORT jstring JNICALL
Java_com_shenji_aikeyboard_llm_ShenjiLLMEngine_nativeGetModelInfo(JNIEnv *env, jobject thiz, jlong session_ptr) {
    try {
        LlmSession* session = reinterpret_cast<LlmSession*>(session_ptr);
        if (!session) {
            LOGE("Invalid session pointer for getModelInfo");
            return env->NewStringUTF("Invalid session");
        }
        
        std::string info = session->getModelInfo();
        LOGI("Native getModelInfo called, result: %s", info.c_str());
        return env->NewStringUTF(info.c_str());
    } catch (const std::exception& e) {
        LOGE("Exception in native getModelInfo: %s", e.what());
        return env->NewStringUTF("Error getting model info");
    }
}

} // extern "C" 