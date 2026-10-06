#include <jni.h>
#include <string>
#include <algorithm>
#include <cctype>
static std::string trim_copy(std::string s) {
    auto not_space=[](unsigned char c){ return !std::isspace(c); };
    s.erase(s.begin(), std::find_if(s.begin(), s.end(), not_space));
    s.erase(std::find_if(s.rbegin(), s.rend(), not_space).base(), s.end());
    return s;
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_scrami_ai_NativeCore_engineInfo(JNIEnv* env, jobject) {
    return env->NewStringUTF("S.AI Native Core - C++17 - llama.cpp backend");
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_scrami_ai_NativeCore_preparePrompt(JNIEnv* env, jobject, jstring input) {
    const char* raw = env->GetStringUTFChars(input, nullptr);
    std::string text = raw ? raw : "";
    env->ReleaseStringUTFChars(input, raw);
    text = trim_copy(text);
    if (text.size() > 12000) text.resize(12000);
    return env->NewStringUTF(text.c_str());
}
