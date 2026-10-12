package com.example.samplewearmobileapp.models

enum class PpgType {
    PPG_GREEN,
    PPG_IR,
    PPG_RED;

    val mask: Int get() = 1 shl ordinal
}
