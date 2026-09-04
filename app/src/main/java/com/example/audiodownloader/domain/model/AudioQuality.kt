package com.example.audiodownloader.domain.model

enum class AudioQuality(val displayName: String, val bitrate: String) {
    LOW("Low", "low"),
    NORMAL("Normal", "normal"),
    HIGH("High", "high")
}