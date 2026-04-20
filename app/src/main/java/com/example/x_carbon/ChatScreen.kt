package com.example.x_carbon

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var chatMessages by remember { mutableStateOf(listOf<DashboardChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // Initial welcome message
    LaunchedEffect(Unit) {
        if (chatMessages.isEmpty()) {
            chatMessages = listOf(
                DashboardChatMessage(
                    role = "assistant",
                    content = "Hello! I'm your carbon footprint assistant. Ask me about reducing emissions, understanding your score, or sustainable living tips. How can I help?"
                )
            )
        }
    }

    // Auto-scroll to bottom
    LaunchedEffect(chatMessages.size, isLoading) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = CarbonGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Assistant", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = CarbonGreen.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, CarbonGreen.copy(alpha = 0.5f))
                        ) {
                            Text(
                                "Gemini 1.5 Flash",
                                fontSize = 10.sp,
                                color = CarbonGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GlassSurface,
                    scrolledContainerColor = GlassSurface
                ),
                actions = {
                    IconButton(onClick = {
                        chatMessages = listOf(
                            DashboardChatMessage(
                                role = "assistant",
                                content = "Chat cleared. How can I help you today?"
                            )
                        )
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear chat", tint = Color.Gray)
                    }
                }
            )
        },
        containerColor = DeepSpace
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = 80.dp), // Space for input area
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chatMessages) { message ->
                    DashboardChatBubble(message)
                }
                if (isLoading) {
                    item { TypingIndicator() }
                }
            }

            // Input area at bottom
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = GlassSurface,
                shadowElevation = 8.dp,
                tonalElevation = 0.dp,
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask about your carbon footprint...", color = Color.Gray) },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CarbonGreen,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = CarbonGreen
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                val userMessage = DashboardChatMessage("user", inputText)
                                chatMessages = chatMessages + userMessage
                                val question = inputText
                                inputText = ""
                                isLoading = true
                                coroutineScope.launch {
                                    try {
                                        val answer = GeminiService.askQuestion(question, "User is asking about their carbon footprint.")
                                        val assistantMessage = DashboardChatMessage(
                                            "assistant",
                                            answer.ifBlank { "Sorry, I couldn't process that. Please try again." }
                                        )
                                        chatMessages = chatMessages + assistantMessage
                                    } catch (e: Exception) {
                                        val errorMessage = DashboardChatMessage(
                                            "assistant",
                                            "Error: ${e.localizedMessage ?: "Please check your internet connection."}"
                                        )
                                        chatMessages = chatMessages + errorMessage
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        containerColor = CarbonGreen,
                        contentColor = DeepSpace,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardChatBubble(message: DashboardChatMessage) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) CarbonGreen else GlassSurface
    val textColor = if (isUser) DeepSpace else Color.White

    val alpha by animateFloatAsState(targetValue = 1f, animationSpec = tween(300), label = "alpha")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer(alpha = alpha),
        horizontalAlignment = alignment
    ) {
        if (!isUser && !message.isLoading) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp, start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Psychology, null, tint = CarbonGreen, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Assistant", fontSize = 10.sp, color = Color.Gray, letterSpacing = 0.5.sp)
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 12.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 12.dp,
                bottomStart = 12.dp,
                bottomEnd = 12.dp
            ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp),
            shadowElevation = if (isUser) 0.dp else 2.dp,
            border = if (!isUser) BorderStroke(1.dp, BorderColor) else null
        ) {
            Text(
                text = if (message.isLoading) "..." else message.content,
                modifier = Modifier.padding(12.dp),
                color = textColor,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val transition = rememberInfiniteTransition(label = "typing")
        repeat(3) { i ->
            val scale by transition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$i"
            )
            Box(
                Modifier
                    .size(6.dp)
                    .scale(scale)
                    .background(CarbonGreen, CircleShape)
            )
        }
    }
}

// DashboardChatMessage data class
data class DashboardChatMessage(
    val role: String,
    val content: String,
    val isLoading: Boolean = false
)
