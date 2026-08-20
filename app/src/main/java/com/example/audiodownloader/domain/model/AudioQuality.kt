package com.example.audiodownloader.domain.model

enum class AudioQuality(val displayName: String, val bitrate: String) {
    LOW("Low", "48"),
    NORMAL("Normal", "128"),
    HIGH("High", "256")
}