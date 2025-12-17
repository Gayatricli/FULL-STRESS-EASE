package com.example.stressease.Analytics

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.stressease.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ReportsActivity : AppCompatActivity() {

    private lateinit var tvReportTitle: TextView
    private lateinit var tvReportStats: TextView
    private lateinit var tvReportStatus: TextView

    private lateinit var emotionBarChart: BarChart
    private lateinit var moodPieChart: PieChart
    private lateinit var moodTrendChart: LineChart

    private lateinit var btnBack: Button
    private lateinit var btnViewSummary: Button
    private lateinit var btnNext: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.reports)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initViews()
        setupNavigation()
        prepareChartsDefaults()

        loadReports()
    }

    private fun initViews() {
        tvReportTitle = findViewById(R.id.tvReportTitle)
        tvReportStats = findViewById(R.id.tvReportStats)
        tvReportStatus = findViewById(R.id.tvReportStatus)

        emotionBarChart = findViewById(R.id.emotionBarChart)
        moodPieChart = findViewById(R.id.moodPieChart)
        moodTrendChart = findViewById(R.id.moodTrendChart)

        btnBack = findViewById(R.id.btnBack)
        btnViewSummary = findViewById(R.id.btnViewSummary)
        btnNext = findViewById(R.id.btnNext)
    }

    private fun setupNavigation() {
        btnBack.setOnClickListener { finish() }
        btnViewSummary.setOnClickListener { startActivity(Intent(this, Summary::class.java)) }
        btnNext.setOnClickListener { startActivity(Intent(this, Suggestions::class.java)) }
    }

    private fun prepareChartsDefaults() {
        emotionBarChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            axisRight.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            legend.isEnabled = true
            setFitBars(true)
        }
        moodPieChart.apply {
            description.isEnabled = false
            isRotationEnabled = false
            legend.isEnabled = true
            setUsePercentValues(false)
        }
        moodTrendChart.apply {
            description.isEnabled = false
            axisRight.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            axisLeft.axisMinimum = 0f
            axisLeft.axisMaximum = 3.5f
            legend.isEnabled = true
        }
    }

    // ---- Data model
    data class MoodEntry(val id: String, val mood: String, val timestamp: Long)

    // ----- Robust loadReports (handles both collections and field shapes)
    private fun loadReports() {
        val userId = auth.currentUser?.uid
        if (userId.isNullOrEmpty()) {
            Log.e("REPORTS", "AUTH CHECK FAILED: auth.currentUser is null -> user NOT signed in")
            tvReportStats.text = "Not signed in. Please sign in to load reports."
            showSampleCharts()
            return
        }
        Log.d("REPORTS", "Starting robust loadReports for user=$userId")

        val resultsCollector = mutableListOf<DocumentSnapshot>()
        val errors = mutableListOf<Exception>()
        val completeCounter = AtomicInteger(0)
        val expected = 2

        fun onQueryDone() {
            if (completeCounter.incrementAndGet() < expected) return

            Log.d("REPORTS", "All queries completed. errors=${errors.size} totalDocsCollected=${resultsCollector.size}")

            runOnUiThread {
                tvReportStats.text = "Raw docs found: ${resultsCollector.size} (see Logcat REPORTS)"
            }

            if (resultsCollector.isEmpty()) {
                Log.w("REPORTS", "No documents returned from both queries. Check project, rules, or paths.")
                showSampleCharts()
                return
            }

            val merged = mergeDocuments(resultsCollector)
            Log.d("REPORTS", "Merged entries count=${merged.size}")

            if (merged.isEmpty()) {
                Log.w("REPORTS", "Merged list empty after dedupe - dumping raw docs for debug")
                for (d in resultsCollector) {
                    Log.v("REPORTS:DUMP", "doc id=${d.id} data=${d.data}")
                }
                showSampleCharts()
                return
            }

            updateUI(merged)
            saveSummaryToFirestore(userId, merged)
        }

        // Query A: users/{uid}/moods (do NOT orderBy, createdAt used sometimes)
        db.collection("users")
            .document(userId)
            .collection("moods")
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val snap = task.result
                    Log.d("REPORTS", "Query A success: users/$userId/moods size=${snap?.size() ?: 0}")
                    snap?.documents?.let { docs ->
                        resultsCollector.addAll(docs)
                        docs.forEach { d -> Log.v("REPORTS:QA", "A doc id=${d.id} data=${d.data}") }
                    }
                } else {
                    Log.e("REPORTS", "Query A failed:", task.exception)
                    task.exception?.let { errors.add(it) }
                }
                onQueryDone()
            }

        // Query B: users/{uid}/moodLogs OR top-level collection "user_mood_logs"
        // try collection under user first (moodLogs), then fallback to top-level user_mood_logs if you rely on that.
        db.collection("users")
            .document(userId)
            .collection("moodLogs")
            .get()
            .addOnCompleteListener { t ->
                if (t.isSuccessful) {
                    val snap = t.result
                    Log.d("REPORTS", "Query B success: users/$userId/moodLogs size=${snap?.size() ?: 0}")
                    snap?.documents?.let { docs ->
                        resultsCollector.addAll(docs)
                        docs.forEach { d -> Log.v("REPORTS:QB", "B doc id=${d.id} data=${d.data}") }
                    }
                    onQueryDone()
                } else {
                    Log.w("REPORTS", "Query B (users/.../moodLogs) failed; trying top-level user_mood_logs", t.exception)
                    // fallback: top-level collection
                    db.collection("user_mood_logs")
                        .whereEqualTo("user_id", userId)
                        .get()
                        .addOnCompleteListener { t2 ->
                            if (t2.isSuccessful) {
                                val snap2 = t2.result
                                Log.d("REPORTS", "Query B fallback success: user_mood_logs size=${snap2?.size() ?: 0}")
                                snap2?.documents?.let { docs ->
                                    resultsCollector.addAll(docs)
                                    docs.forEach { d -> Log.v("REPORTS:QB2", "B2 doc id=${d.id} data=${d.data}") }
                                }
                            } else {
                                Log.e("REPORTS", "Query B fallback failed:", t2.exception)
                                t2.exception?.let { errors.add(it) }
                            }
                            onQueryDone()
                        }
                }
            }
    }

    // ----- Extract timestamp robustly and normalize mood strings
    private fun extractTS(doc: DocumentSnapshot): Long? {
        val candidates = listOf("timestamp", "createdAt", "time", "created_at", "created_at_ms")
        for (key in candidates) {
            val value = doc.get(key) ?: continue
            when (value) {
                is Timestamp -> return value.toDate().time
                is Long -> return value
                is Double -> return value.toLong()
                is Number -> return value.toLong()
                is Date -> return value.time
                is Map<*, *> -> {
                    val secondsAny = (value["seconds"] ?: value["_seconds"])
                    if (secondsAny is Number) return secondsAny.toLong() * 1000
                }
            }
        }
        // If nothing found, try document metadata (no reliable timestamp, so return null)
        return null
    }

    private fun normalizeMood(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unknown"
        // Remove emoji and most punctuation: keep letters, numbers and spaces
        val stripped = raw.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
        if (stripped.isEmpty()) return "Unknown"
        val lower = stripped.toLowerCase(Locale.ROOT)

        // Map variations to canonical labels
        return when {
            lower.contains("happy") -> "Happy"
            lower.contains("calm") -> "Calm"
            lower.contains("content") -> "Content"
            lower.contains("neutral") -> "Neutral"
            lower.contains("sad") -> "Sad"
            lower.contains("angry") -> "Angry"
            lower.contains("anxious") || lower.contains("anxiety") -> "Anxious"
            lower.contains("stressed") || lower.contains("stress") -> "Stressed"
            else -> stripped.split("\\s+".toRegex()).first().capitalize(Locale.getDefault())
        }
    }

    // Merge docs into MoodEntry list, dedupe by composite key (mood+timestamp or doc.id)
    private fun mergeDocuments(docs: List<DocumentSnapshot>): List<MoodEntry> {
        val temp = mutableListOf<MoodEntry>()
        for (d in docs) {
            try {
                val id = d.id ?: UUID.randomUUID().toString()
                val rawMood = d.getString("mood") ?: d.getString("emotion") ?: d.getString("feeling") ?: d.getString("moodText")
                val mood = normalizeMood(rawMood)
                val ts = extractTS(d) ?: continue // skip docs without timestamp
                temp.add(MoodEntry(id, mood, ts))
            } catch (ex: Exception) {
                Log.w("REPORTS", "Skipping doc during merge due to: ${ex.message} doc=${d.id}")
            }
        }
        // dedupe by composite
        val map = linkedMapOf<String, MoodEntry>()
        for (e in temp) {
            val key = "${e.mood}_${e.timestamp}"
            if (!map.containsKey(key)) map[key] = e
        }
        val out = map.values.toMutableList()
        out.sortBy { it.timestamp }
        Log.d("REPORTS", "mergeDocuments -> in=${docs.size} out=${out.size}")
        return out
    }

    // ----- UI update and charts -----
    private fun updateUI(list: List<MoodEntry>) {
        runOnUiThread {
            val moods = list.map { it.mood }

            val positive = moods.count { it.equals("Happy", true) || it.equals("Calm", true) || it.equals("Content", true) }
            val negative = moods.count { it.equals("Sad", true) || it.equals("Angry", true) || it.equals("Anxious", true) || it.equals("Stressed", true) }
            val neutral = moods.count { it.equals("Neutral", true) }

            tvReportStats.text = """
                Total Entries: ${moods.size}
                Positive: $positive
                Negative: $negative
                Neutral: $neutral
            """.trimIndent()

            tvReportStatus.text = when {
                positive > negative -> "You're trending positive! ✨"
                negative > positive -> "You've had some tough days 💛"
                else -> "Mixed mood patterns ⚖️"
            }

            showEmotionBarChart(positive, negative, neutral)
            showPieChart(positive, negative, neutral)
            showTrendChart(list)
        }
    }

    private fun showEmotionBarChart(pos: Int, neg: Int, neu: Int) {
        val entries = listOf(
            BarEntry(0f, pos.toFloat()),
            BarEntry(1f, neg.toFloat()),
            BarEntry(2f, neu.toFloat())
        )
        val ds = BarDataSet(entries, "Emotions")
        ds.colors = listOf(Color.parseColor("#10B981"), Color.parseColor("#EF4444"), Color.parseColor("#6B7280"))
        ds.valueTextSize = 14f
        val data = BarData(ds)
        data.barWidth = 0.6f
        emotionBarChart.data = data
        emotionBarChart.xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Positive", "Negative", "Neutral"))
        emotionBarChart.xAxis.setLabelCount(3, true)
        emotionBarChart.setFitBars(true)
        emotionBarChart.axisLeft.axisMinimum = 0f
        emotionBarChart.invalidate()
        emotionBarChart.data.notifyDataChanged()
        emotionBarChart.notifyDataSetChanged()
    }

    private fun showPieChart(pos: Int, neg: Int, neu: Int) {
        val entries = listOf(
            PieEntry(pos.toFloat(), "Positive"),
            PieEntry(neg.toFloat(), "Negative"),
            PieEntry(neu.toFloat(), "Neutral")
        )
        val ds = PieDataSet(entries, "Mood Distribution")
        ds.colors = ColorTemplate.COLORFUL_COLORS.toList()
        ds.valueTextSize = 12f
        ds.sliceSpace = 2f
        val data = PieData(ds)
        moodPieChart.data = data
        moodPieChart.centerText = "Mood"
        moodPieChart.invalidate()
        moodPieChart.data.notifyDataChanged()
        moodPieChart.notifyDataSetChanged()
    }

    private fun showTrendChart(list: List<MoodEntry>) {
        val entries = ArrayList<Entry>()
        val labels = ArrayList<String>()
        val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())

        list.forEachIndexed { index, entry ->
            val value = when (entry.mood) {
                "Happy" -> 3f
                "Calm" -> 2.5f
                "Content" -> 2.75f
                "Neutral" -> 2f
                "Sad" -> 1f
                "Angry" -> 0.5f
                "Anxious" -> 0.75f
                "Stressed" -> 0.6f
                else -> 2f
            }
            entries.add(Entry(index.toFloat(), value))
            labels.add(sdf.format(Date(entry.timestamp)))
        }

        val ds = LineDataSet(entries, "Mood Trend")
        ds.lineWidth = 3f
        ds.circleRadius = 4f
        ds.valueTextSize = 10f
        ds.setDrawValues(false)
        ds.color = Color.parseColor("#2563EB")
        ds.setCircleColor(Color.parseColor("#2563EB"))

        val data = LineData(ds)
        moodTrendChart.data = data

        moodTrendChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        moodTrendChart.xAxis.labelRotationAngle = -45f
        moodTrendChart.xAxis.setLabelCount(labels.size.coerceAtMost(6), true)

        moodTrendChart.invalidate()
        moodTrendChart.data.notifyDataChanged()
        moodTrendChart.notifyDataSetChanged()
    }

    private fun showSampleCharts() {
        val bar = BarDataSet(listOf(
            BarEntry(0f, 3f),
            BarEntry(1f, 1f),
            BarEntry(2f, 2f)
        ), "Sample")
        bar.colors = ColorTemplate.MATERIAL_COLORS.toList()
        emotionBarChart.data = BarData(bar)
        emotionBarChart.invalidate()

        val pie = PieDataSet(listOf(
            PieEntry(3f, "Positive"),
            PieEntry(1f, "Negative"),
            PieEntry(2f, "Neutral")
        ), "Sample")
        moodPieChart.data = PieData(pie)
        moodPieChart.invalidate()

        val line = LineDataSet(listOf(
            Entry(0f, 3f),
            Entry(1f, 2f),
            Entry(2f, 2.5f),
            Entry(3f, 1f)
        ), "Sample")
        moodTrendChart.data = LineData(line)
        moodTrendChart.invalidate()
    }

    // --- Save aggregated summary used by Summary activity
    private fun saveSummaryToFirestore(uid: String, list: List<MoodEntry>) {
        try {
            if (list.isEmpty()) return

            val moodCounts = HashMap<String, Int>()
            for (e in list) {
                val mKey = e.mood.trim()
                moodCounts[mKey] = (moodCounts[mKey] ?: 0) + 1
            }

            val totalMoods = list.size.toLong()
            val mostCommonMood = moodCounts.maxByOrNull { it.value }?.key ?: "None"

            val positive = moodCounts.filterKeys { it.equals("Happy", true) || it.equals("Calm", true) || it.equals("Content", true) }.values.sum()
            val negative = moodCounts.filterKeys { it.equals("Sad", true) || it.equals("Angry", true) || it.equals("Anxious", true) || it.equals("Stressed", true) }.values.sum()
            val neutral = moodCounts.filterKeys { it.equals("Neutral", true) }.values.sum()

            val overallStatus = when {
                positive > negative -> "Positive"
                negative > positive -> "Negative"
                else -> "Mixed"
            }

            val userUpdates = hashMapOf<String, Any>(
                "totalMoods" to totalMoods,
                "mostCommonMood" to mostCommonMood,
                "mostCommonEmotion" to mostCommonMood,
                "overallStatus" to overallStatus,
                "lastUpdated" to Timestamp.now()
            )

            db.collection("users")
                .document(uid)
                .set(userUpdates, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("REPORTS", "Saved summary to users/$uid : $userUpdates")
                }
                .addOnFailureListener {
                    Log.e("REPORTS", "Failed to save summary to user doc", it)
                }

            val last7 = list.takeLast(7).map { it.mood }
            val weekly = hashMapOf<String, Any>(
                "totalMoods" to totalMoods,
                "last7DaysMood" to last7,
                "mostCommonMood" to mostCommonMood,
                "averageQuizScore" to 0.0,
                "generatedAt" to Timestamp.now()
            )
            db.collection("users")
                .document(uid)
                .collection("reports")
                .document("weekly_summary")
                .set(weekly, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("REPORTS", "Saved weekly_summary for $uid")
                }
                .addOnFailureListener {
                    Log.e("REPORTS", "Failed to save weekly_summary", it)
                }

        } catch (ex: Exception) {
            Log.e("REPORTS", "saveSummaryToFirestore failed: ${ex.message}", ex)
        }
    }
}
