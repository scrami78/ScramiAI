package com.scrami.ai

object NativeCore {
    init { System.loadLibrary("sai_core") }
    external fun engineInfo(): String
    external fun preparePrompt(input: String): String
}
