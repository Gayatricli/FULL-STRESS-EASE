package com.example.stressease.chats

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stressease.Api.ChatRequest
import com.example.stressease.Api.ChatResponse
import com.example.stressease.Api.RetrofitClient
import com.example.stressease.LocalStorageOffline.SharedPreference
import com.example.stressease.LoginMain.MainActivity
import com.example.stressease.SOS.SOS
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.example.stressease.R
// ---------- Colors preserved from your theme ----------
object ChatColors {
    val BackgroundTop = Color(0xFFF5E6D3)
    val BackgroundMid = Color(0xFFE8D8C8)
    val BackgroundMidBlue = Color(0xFFD8E5F0)
    val BackgroundBottom = Color(0xFFB8D8E8)
    val Surface = Color(0xFFFFFBF5)
    val SurfaceBorder = Color(0xFFD0DCE8)
    val Primary = Color(0xFF6B4423)
    val PrimaryLight = Color(0xFF8D6E63)
    val TextPrimary = Color(0xFF5D4037)
    val TextSecondary = Color(0xFF8D6E63)
    val UserBubble = Color(0xFF6B4423)
    val BotBubble = Color(0xFFFFFBF5)
    val BotAvatar = Color(0xFF8BC34A)
    val UserTimestamp = Color(0xFFD4B896)
    val BotTimestamp = Color(0xFF8D6E63)
    val InputBorder = Color(0xFFD4C4B0)
    val SmileyGradientStart = Color(0xFFF4C430)
    val SmileyGradientEnd = Color(0xFFFFA500)
    val ChatFab = Color(0xFF117A65)
}

// ---------- SharedPreferences keys & user-scoped helpers ----------
private const val PREFS_NAME = "AppPrefs"
private const val PREF_CHAT_SESSION_BASE = "chat_session"
private const val PREF_CHAT_HISTORY_BASE = "chat_history"
private const val PREF_AUTH_TOKEN_BASE = "authToken"

private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
private fun chatSessionKeyFor(uid: String) = "${PREF_CHAT_SESSION_BASE}_$uid"
private fun chatHistoryKeyFor(uid: String) = "${PREF_CHAT_HISTORY_BASE}_$uid"
private fun authTokenKeyFor(uid: String) = "${PREF_AUTH_TOKEN_BASE}_$uid"

// persist session id per-user
fun persistSessionIdForUser(context: Context, uid: String, id: String) {
    if (uid.isBlank() || id.isBlank()) return
    prefs(context).edit().putString(chatSessionKeyFor(uid), id).apply()
}

// load session id per-user
fun loadSessionIdForUser(context: Context, uid: String): String? {
    if (uid.isBlank()) return null
    return prefs(context).getString(chatSessionKeyFor(uid), null)
}

fun clearSessionIdForUser(context: Context, uid: String) {
    if (uid.isBlank()) return
    prefs(context).edit().remove(chatSessionKeyFor(uid)).apply()
}

// Helper to read/write auth token per-user (optional)
fun persistAuthTokenForUser(context: Context, uid: String, token: String) {
    if (uid.isBlank() || token.isBlank()) return
    prefs(context).edit().putString(authTokenKeyFor(uid), token).apply()
}

fun loadAuthTokenForUser(context: Context, uid: String): String? {
    if (uid.isBlank()) return null
    return prefs(context).getString(authTokenKeyFor(uid), null)
}

// ---------- ChatCompose (fixed & minimal) ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatFragmentCompose(isNewSession: Boolean = false) {
    val context = LocalContext.current

    // State
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    // rememberSaveable so typed text doesn't vanish
    var inputValue by rememberSaveable { mutableStateOf("") }
    var currentSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Firebase
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    // get user id (may be null before login)
    val uid = auth.currentUser?.uid ?: ""
    val RubyDance = FontFamily(
        Font(R.font.ruby_dance, FontWeight.Normal)
    )

    // Load saved chat or start new (per-user key)
    LaunchedEffect(isNewSession, uid) {
        if (uid.isBlank()) {
            messages = emptyList()
        } else {
            messages = if (isNewSession) {
                SharedPreference.saveChatList(context, chatHistoryKeyFor(uid), mutableListOf())
                emptyList()
            } else {
                SharedPreference.loadChatList(context, chatHistoryKeyFor(uid))
            }
        }
    }

    // Load persisted session id on first composition (per-user)
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            currentSessionId = loadSessionIdForUser(context, uid)
            Log.d("ChatFragment", "Loaded persisted session_id=$currentSessionId for uid=$uid")
        } else {
            currentSessionId = null
            Log.d("ChatFragment", "No user logged in yet (uid blank) on load")
        }
    }

    // Auto-scroll when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // add message & persist locally (per-user)
    fun addMessage(chatMessage: ChatMessage) {
        messages = messages + chatMessage
        if (uid.isNotBlank()) {
            SharedPreference.saveChatList(context, chatHistoryKeyFor(uid), messages.toMutableList())
        } else {
            // fallback (shouldn't normally happen)
            SharedPreference.saveChatList(context, "chat_history", messages.toMutableList())
        }
    }

    // Save to Firestore (unchanged)
    fun saveChatMessage(chatMessage: ChatMessage) {
        val userId = auth.currentUser?.uid ?: return
        val chatData = hashMapOf(
            "message" to chatMessage.message.ifEmpty { chatMessage.text },
            "sender" to if (chatMessage.isUser) "user" else "bot",
            "emotion" to chatMessage.emotion,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("users")
            .document(userId)
            .collection("chats")
            .add(chatData)
            .addOnSuccessListener { Log.d("Firestore", "Saved: ${chatData["sender"]} → ${chatData["message"]}") }
            .addOnFailureListener { e -> Log.e("Firestore", "Failed to save chat", e) }
    }

    // Persist session id locally and update in-memory (per-user)
    fun persistSessionIdLocal(sessionId: String?) {
        val userId = auth.currentUser?.uid ?: ""
        if (sessionId.isNullOrBlank() || userId.isBlank()) {
            Log.d("ChatFragment", "persistSessionIdLocal skipped: sessionId='$sessionId' userId='$userId'")
            return
        }
        currentSessionId = sessionId
        persistSessionIdForUser(context, userId, sessionId)
        Log.d("ChatFragment", "Persisted session_id=$sessionId for uid=$userId")
    }

    // Clear persisted session id (New Conversation)
    fun clearPersistedSession() {
        val userId = auth.currentUser?.uid ?: ""
        currentSessionId = null
        if (userId.isNotBlank()) clearSessionIdForUser(context, userId)
        Log.d("ChatFragment", "Cleared persisted session_id for uid=$userId")
    }

    // Robust sendMessage implementation with explicit request log
    fun sendMessage(userMessage: String) {
        val userId = auth.currentUser?.uid ?: ""
        val token = if (userId.isNotBlank()) loadAuthTokenForUser(context, userId) else null
        // fallback to global token if you used that previously (compatibility)
        val globalToken = prefs(context).getString("authToken", null)
        val finalToken = token ?: globalToken

        Log.d("ChatFragment", "sendMessage invoked. tokenPresent=${!finalToken.isNullOrBlank()} sessionId=$currentSessionId msg='${userMessage.take(60)}' uid='$userId'")

        if (finalToken.isNullOrBlank()) {
            Toast.makeText(context, "No token found. Please log in again.", Toast.LENGTH_SHORT).show()
            Log.w("ChatFragment", "Auth token missing — aborting sendMessage")
            return
        }

        // Build request — ensure session_id is string (empty if null)
        val request = ChatRequest(userMessage, session_id = currentSessionId ?: "")
        // DEBUG: log the exact payload being sent
        Log.d("ChatFragment", "REQUEST → session_id='${request.session_id}', message='${request.message}', uid='$userId'")

        isSending = true

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.api.sendMessage("Bearer $finalToken", request)
                withContext(Dispatchers.Main) {
                    isSending = false
                    Log.d("ChatFragment", "HTTP response code=${response.code()} successful=${response.isSuccessful}")

                    if (!response.isSuccessful) {
                        val err = try { response.errorBody()?.string() } catch (e: Exception) { null }
                        Log.e("ChatFragDebug", "sendMessage failed code=${response.code()} error=$err")
                    }

                    val body = response.body()
                    if (response.isSuccessful && body != null) {
                        // Prefer ai_response.content if present; otherwise fallback to reply field
                        val botReply = body.ai_response?.content?.takeIf { it.isNotBlank() } ?: body.reply ?: "No reply"
                        val botRole = body.ai_response?.role ?: body.emotion ?: "assistant"

                        // persist session_id if returned by server (per-user)
                        body.session_id?.let {
                            persistSessionIdLocal(it)
                        }

                        val botMessage = ChatMessage(
                            text = botReply,
                            isUser = false,
                            emotion = botRole,
                            message = botReply,
                            timestamp = System.currentTimeMillis().toString()
                        )
                        addMessage(botMessage)
                        saveChatMessage(botMessage)
                    } else {
                        // handle 401 explicitly: clear token and redirect to login
                        if (response.code() == 401) {
                            Log.w("ChatFragment", "Unauthorized - clearing token & redirecting to login")
                            // clear per-user token too if stored
                            if (userId.isNotBlank()) prefs(context).edit().remove(authTokenKeyFor(userId)).apply()
                            prefs(context).edit().remove("authToken").apply()
                            Toast.makeText(context, "Session expired. Please login again.", Toast.LENGTH_LONG).show()
                            context.startActivity(Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                            return@withContext
                        }

                        // fallback offline message for UX
                        addMessage(
                            ChatMessage(
                                text = "I’m here for you, even offline 😊",
                                isUser = false,
                                emotion = "assistant",
                                message = "offline",
                                timestamp = System.currentTimeMillis().toString()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSending = false
                    Log.e("ChatFragment", "Network/Server exception: ${e.message}", e)
                    addMessage(
                        ChatMessage(
                            text = "Server error. Try again later.",
                            isUser = false,
                            emotion = "error",
                            message = "server_error",
                            timestamp = System.currentTimeMillis().toString()
                        )
                    )
                }
            }
        }
    }

    // UI - keep exact layout/proportions; add imePadding to keep input visible above keyboard
    Scaffold(
        topBar = {
            ChatHeaderNoNav(
                onBack = {
                    val intent = Intent(context, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                onNewChat = {
                    messages = emptyList()
                    if (uid.isNotBlank()) SharedPreference.saveChatList(context, chatHistoryKeyFor(uid), mutableListOf())
                    else SharedPreference.saveChatList(context, "chat_history", mutableListOf())
                    clearPersistedSession()
                },
                onSOS = { context.startActivity(Intent(context, SOS::class.java)) }
            )
        },
        content = { innerPadding ->
            // --- replace the current top Box + LazyColumn block with this ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding() // keep input above keyboard
                    .padding(innerPadding)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ChatColors.BackgroundTop,
                                ChatColors.BackgroundMid,
                                ChatColors.BackgroundMidBlue,
                                ChatColors.BackgroundBottom
                            )
                        )
                    )
            ) {
                // Use a single weighted container for the whole messages + decorative background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // <-- let this take remaining space
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Decorative content (placed behind messages)
                    Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        Text(text = "😊", fontSize = 200.sp, color = Color.Black.copy(alpha = 0.20f))
                        val decor = listOf(
                            Pair("🌸", Modifier.offset((-120).dp, (-150).dp)),
                            Pair("✨", Modifier.offset(130.dp, (-100).dp)),
                            Pair("☀️", Modifier.offset((-80).dp, 80.dp)),
                            Pair("💫", Modifier.offset(120.dp, 100.dp)),
                            Pair("🍃", Modifier.offset((-140).dp, 150.dp)),
                            Pair("🌈", Modifier.offset(100.dp, (-160).dp))
                        )
                        decor.forEach { (emoji, mod) ->
                            Text(
                                text = emoji,
                                fontSize = 36.sp,
                                color = Color.Black.copy(alpha = 0.18f),
                                modifier = mod
                            )
                        }
                    }

                    // Messages list sits on top of the decorative background
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize(), // fills the weighted Box
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 8.dp) // small bottom padding so last item isn't flush
                    ) {
                        items(messages) { m -> MessageItem(message = m) }
                    }
                }

                // Input stays at the bottom and is visible (no fixed bottom padding required)
                ChatInputSection(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    onSend = {
                        val text = inputValue.trim()
                        if (text.isNotEmpty()) {
                            val userMsg = ChatMessage(
                                text = text,
                                isUser = true,
                                emotion = "neutral",
                                message = text,
                                timestamp = System.currentTimeMillis().toString()
                            )
                            addMessage(userMsg)
                            saveChatMessage(userMsg)
                            inputValue = ""
                            sendMessage(text)
                        }
                    },
                    isSending = isSending,
                    currentSessionId = currentSessionId
                )
            }


        }
    )
}

// ---------- Header ----------
@Composable
fun ChatHeaderNoNav(
    onBack: () -> Unit,
    onNewChat: () -> Unit,
    onSOS: () -> Unit
) {
    val RubyDance = FontFamily(
        Font(R.font.ruby_dance, FontWeight.Normal)
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // BACK BUTTON
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = ChatColors.TextPrimary
                    )
                }

                // NEW CUSTOM CHATBOT NAME
                Column {
                    Text(
                        text = "FeelBetter",
                        fontSize = 20.sp,
                        fontFamily = RubyDance,
                        color = ChatColors.TextPrimary
                    )
                    Text(
                        text = "Your FeelBetter Companion",
                        fontSize = 13.sp,
                        color = ChatColors.TextSecondary
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // NEW CHAT BUTTON
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = BorderStroke(
                        1.dp,
                        ChatColors.TextSecondary.copy(alpha = 0.25f)
                    ),
                    shadowElevation = 2.dp
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        IconButton(
                            onClick = onNewChat,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.PostAdd,
                                contentDescription = "New Chat",
                                tint = ChatColors.TextPrimary
                            )
                        }
                    }
                }

                // SOS BUTTON
                Button(
                    onClick = onSOS,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "SOS",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SOS", fontSize = 14.sp)
                }
            }
        }
    }
}

// ---------- Message item + input section ----------
@Composable
fun MessageItem(message: ChatMessage) {
    val isUser = message.isUser
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val formatted = remember(message.timestamp) {
        try { timeFormat.format(Date(message.timestamp.toLong())) } catch (e: Exception) { message.timestamp }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) {
            Box(modifier = Modifier.size(32.dp).background(ChatColors.BotAvatar, shape = CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SmartToy, contentDescription = "Bot", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = if (isUser) ChatColors.UserBubble else ChatColors.BotBubble,
            shadowElevation = if (isUser) 0.dp else 1.dp,
            border = if (!isUser) BorderStroke(1.dp, ChatColors.SurfaceBorder) else null,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(text = message.text, color = if (isUser) Color.White else Color(0xFF4A4A4A), fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = formatted, fontSize = 11.sp, color = if (isUser) ChatColors.UserTimestamp else ChatColors.BotTimestamp)
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.size(32.dp).background(ChatColors.Primary, shape = CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = "User", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputSection(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    currentSessionId: String?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = ChatColors.Surface,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type your message...") },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = ChatColors.TextSecondary,
                        unfocusedIndicatorColor = ChatColors.InputBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    maxLines = 4,
                    singleLine = false
                )
                if (isSending) {
                    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                } else {
                    FloatingActionButton(onClick = onSend, modifier = Modifier.size(44.dp), containerColor = ChatColors.Primary, contentColor = Color.White) {
                        Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            val statusText = when {
                currentSessionId.isNullOrBlank() -> "No active session"
                else -> "Session active"
            }
            Text(text = statusText, fontSize = 11.sp, color = ChatColors.TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(text = "Press Enter to send • Shift + Enter for new line", fontSize = 11.sp, color = ChatColors.TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

