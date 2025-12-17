package com.example.stressease.Analytics

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stressease.Api.PredictRequest
import com.example.stressease.Api.RetrofitClient
import com.example.stressease.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Summary : AppCompatActivity() {

    // UI
    private lateinit var tvTotalChats: TextView
    private lateinit var tvTotalMoods: TextView
    private lateinit var tvMostCommonEmotion: TextView
    private lateinit var tvMostCommonMood: TextView
    private lateinit var tvOverallStatus: TextView
    private lateinit var tvStressLevel: TextView
    private lateinit var tvAiBadge: TextView
    private lateinit var btnBack: Button
    private lateinit var btnNext: Button

    // Data
    private var avgMoodScore = 3.0
    private var avgQuizScore = 3.0
    private var chatCount7Days = 0

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.summary)

        bindViews()
        loadFirestoreSummary()

        btnBack.setOnClickListener { finish() }
        btnNext.setOnClickListener {
            startActivity(Intent(this, LeaderBoard::class.java))
        }
    }

    private fun bindViews() {
        tvTotalChats = findViewById(R.id.tvTotalChats)
        tvTotalMoods = findViewById(R.id.tvTotalMoods)
        tvMostCommonEmotion = findViewById(R.id.tvMostCommonEmotion)
        tvMostCommonMood = findViewById(R.id.tvMostCommonMood)
        tvOverallStatus = findViewById(R.id.tvOverallStatus)
        tvStressLevel = findViewById(R.id.tvStressLevel)
        tvAiBadge = findViewById(R.id.tvAiBadge)
        btnBack = findViewById(R.id.btnBack)
        btnNext = findViewById(R.id.btnNext)
    }

    // ---------------- FIRESTORE ----------------

    private fun loadFirestoreSummary() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) return@addOnSuccessListener

                val mood = doc.getString("mostCommonMood") ?: "Neutral"
                val emotion = doc.getString("mostCommonEmotion") ?: "Neutral"

                avgMoodScore = moodToScore(mood)
                avgQuizScore = doc.getDouble("averageQuizScore") ?: 3.0

                tvTotalMoods.text = (doc.getLong("totalMoods") ?: 0).toString()
                tvMostCommonMood.text = mood
                tvMostCommonEmotion.text = emotion
                tvOverallStatus.text = doc.getString("overallStatus") ?: "Mixed Mood"

                val dailyMap = doc.get("dailyChatCounts")
                if (dailyMap is Map<*, *>) {
                    val count = sumLastNDays(dailyMap as Map<String, Any>, 7)
                    finishWithChatCount(uid, count)
                } else {
                    computeChatCount(uid)
                }
            }
    }

    private fun finishWithChatCount(uid: String, count: Int) {
        chatCount7Days = count
        tvTotalChats.text = count.toString()

        updateLeaderboard(uid)
        callAiPrediction(uid)
    }

    // ---------------- CHAT COUNT ----------------

    private fun sumLastNDays(map: Map<String, Any>, days: Int): Int {
        val cal = Calendar.getInstance()
        var sum = 0
        repeat(days) {
            val key = isoDate.format(cal.time)
            sum += (map[key] as? Number)?.toInt() ?: 0
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return sum
    }

    private fun computeChatCount(uid: String) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)

        db.collection("users").document(uid)
            .collection("chats")
            .whereGreaterThanOrEqualTo("timestamp", cal.timeInMillis)
            .get()
            .addOnSuccessListener {
                finishWithChatCount(uid, it.size())
            }
            .addOnFailureListener {
                finishWithChatCount(uid, 0)
            }
    }

    // ---------------- AI ----------------

    private fun callAiPrediction(uid: String) {
        val user = auth.currentUser ?: return

        val backendQuizScore = (avgQuizScore * 12).toInt().coerceIn(12, 60)

        user.getIdToken(true).addOnSuccessListener { token ->
            lifecycleScope.launch {
                try {
                    val response = RetrofitClient.api.predictMentalState(
                        "Bearer ${token.token}",
                        PredictRequest(
                            userId = uid,
                            avgMoodScore = avgMoodScore,
                            chatCount = chatCount7Days,
                            avgQuizScore = backendQuizScore.toDouble()
                        )
                    )

                    if (response.isSuccessful && response.body() != null) {
                        val prediction = response.body()!!.prediction
                        updateStressUI(prediction.label, prediction.confidence)
                    }

                } catch (e: Exception) {
                    Log.e("SUMMARY", "AI error", e)
                }
            }
        }
    }

    private fun updateStressUI(label: String, confidence: Double) {
        tvStressLevel.text = label.replaceFirstChar { it.uppercase() }
        tvAiBadge.text = "AI: ${label.uppercase()} • ${(confidence * 100).toInt()}%"
    }

    // ---------------- LEADERBOARD ----------------

    private fun updateLeaderboard(uid: String) {
        val data = hashMapOf(
            "username" to (auth.currentUser?.displayName ?: "User"),
            "avgMoodScore" to avgMoodScore,
            "avgQuizScore" to avgQuizScore,
            "chatCount" to chatCount7Days,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db.collection("leaderboard").document(uid).set(data)
    }

    // ---------------- UTILS ----------------

    private fun moodToScore(mood: String): Double =
        when (mood.lowercase()) {
            "very happy" -> 5.0
            "happy" -> 4.5
            "calm" -> 4.0
            "relaxed" -> 3.5
            "neutral" -> 3.0
            "anxious" -> 2.5
            "sad" -> 2.0
            "stressed" -> 1.5
            "very stressed" -> 1.0
            else -> 3.0
        }
}
