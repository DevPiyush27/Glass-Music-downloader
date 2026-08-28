package com.example.audiodownloader.domain.model

enum class AudioQuality(val displayName: String, val bitrate: String) {
    LOW("Low", "128"),
    NORMAL("Normal", "192"),
    HIGH("High", "320")
}