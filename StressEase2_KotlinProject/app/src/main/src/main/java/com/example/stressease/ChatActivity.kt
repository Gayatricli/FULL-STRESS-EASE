package com.example.stressease

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ChatActivity : AppCompatActivity() {

    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageButton
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        rvChat = findViewById(R.id.rvChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        chatAdapter = ChatAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvChat.adapter = chatAdapter

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                addUserMessage(text)
                etMessage.setText("")
                // TODO: call your API here and add bot response via addBotMessage()
                // simulate bot reply for testing:
                rvChat.postDelayed({
                    addBotMessage("This is a sample bot reply to: " + text)
                }, 700)
            }
        }

        // Restore messages if available
        if (savedInstanceState != null) {
            val saved = savedInstanceState.getStringArrayList("messages")
            saved?.forEach { messages.add(ChatMessage(it, true)) }
            chatAdapter.notifyDataSetChanged()
            rvChat.scrollToPosition(messages.size - 1)
        }
    }

    private fun addUserMessage(text: String) {
        val m = ChatMessage(text, isUser = true)
        messages.add(m)
        chatAdapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
    }

    fun addBotMessage(text: String) {
        val m = ChatMessage(text, isUser = false)
        messages.add(m)
        chatAdapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val list = ArrayList<String>()
        messages.forEach { list.add(it.text) }
        outState.putStringArrayList("messages", list)
    }
}
