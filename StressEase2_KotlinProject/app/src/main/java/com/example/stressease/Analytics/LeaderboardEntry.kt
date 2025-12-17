package com.example.stressease.Analytics

data class LeaderboardEntry(
    val id: String,
    var rank: Int = 0,
    val username: String = "User",
    val quizScore: Int = 0,          // 0-100
    val emotionScore: Double = 0.0, // 0.0-1.0
    var compositeScore: Double = 0.0,
    val emoji: String = "🙂",
    val totalLogs: Int = 0,
    val shareWellbeing: Boolean = true,
    val avatarUrl: String? = null,   // optional URL for avatar
    val streakDays: Int = 0         // number of streak days (0 => hidden)
)

