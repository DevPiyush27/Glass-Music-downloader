package com.example.audiodownloader.domain.model

enum class AudioQuality(val displayName: String, val bitrate: String) {
    HIGH("High Quality (360 kbps)", "360"),
    NORMAL("Normal Quality (256 kbps)", "256"),
    STANDARD("Standard Quality (160 kbps)", "160");

    companion object {
        /**
         * Backward-compatible alias for legacy calls referencing LOW.
         */
        val LOW get() = STANDARD
    }
}