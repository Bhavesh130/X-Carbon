package com.example.x_carbon

import android.util.Log
import kotlin.text.RegexOption

object LocalChatbotService {

    private const val TAG = "LocalChatbotService"

    // Intent → answer mapping (regex patterns)
    private val intentMap = listOf(
        IntentPattern(
            patterns = listOf(
                Regex("carbon|footprint|emission|co2", RegexOption.IGNORE_CASE),
                Regex("what is.*carbon.*footprint", RegexOption.IGNORE_CASE)
            ),
            answer = "Your carbon footprint is the total greenhouse gases (CO₂e) emitted by your activities. In this app, we measure the emissions from your device usage, network data, and even the manufacturing of your phone."
        ),
        IntentPattern(
            patterns = listOf(
                Regex("reduce|lower|decrease", RegexOption.IGNORE_CASE),
                Regex("how to reduce", RegexOption.IGNORE_CASE),
                Regex("tips", RegexOption.IGNORE_CASE)
            ),
            answer = "Here are effective ways to reduce your digital carbon footprint:\n• Use Wi‑Fi instead of mobile data\n• Lower screen brightness\n• Close unused apps to reduce CPU load\n• Enable dark mode (saves up to 20% on OLED screens)\n• Avoid unnecessary background data sync"
        ),
        IntentPattern(
            patterns = listOf(
                Regex("diet|food|eat|meat|plant", RegexOption.IGNORE_CASE),
                Regex("what should I eat", RegexOption.IGNORE_CASE)
            ),
            answer = "Diet has a big impact on your carbon footprint. Plant‑based diets produce up to 50% less CO₂ than meat‑heavy diets. Try meat‑free Mondays or replace beef with chicken or plant proteins."
        ),
        IntentPattern(
            patterns = listOf(
                Regex("transport|car|drive|vehicle", RegexOption.IGNORE_CASE),
                Regex("public transport", RegexOption.IGNORE_CASE)
            ),
            answer = "Transportation is a major source of emissions. Consider: carpooling, public transit, electric vehicles, or biking for short trips. Reducing your driving by 10% can save ~300 kg CO₂/year."
        ),
        IntentPattern(
            patterns = listOf(
                Regex("energy|electric|power|grid", RegexOption.IGNORE_CASE),
                Regex("home energy", RegexOption.IGNORE_CASE)
            ),
            answer = "Home energy use contributes to your footprint. Switch to renewable energy, use smart thermostats, unplug devices when not in use, and improve insulation for big savings."
        ),
        IntentPattern(
            patterns = listOf(
                Regex("waste|recycle|trash|plastic", RegexOption.IGNORE_CASE),
                Regex("recycling", RegexOption.IGNORE_CASE)
            ),
            answer = "Reducing waste helps lower emissions. Recycle paper, plastic, glass, and metal. Compost organic waste. Avoid single‑use plastics. Every recycled item saves energy!"
        ),
        IntentPattern(
            patterns = listOf(
                Regex("air travel|flight|plane", RegexOption.IGNORE_CASE),
                Regex("fly", RegexOption.IGNORE_CASE)
            ),
            answer = "Air travel has a high carbon impact. For short trips, consider trains. If you must fly, choose direct flights and economy class. You can also offset your flight emissions."
        ),
        IntentPattern(
            patterns = listOf(
                Regex("hello|hi|hey|greetings|start", RegexOption.IGNORE_CASE),
                Regex("good morning|good afternoon|good evening", RegexOption.IGNORE_CASE)
            ),
            answer = "Hello! I'm your carbon footprint assistant. Ask me about reducing emissions, sustainable living, or understanding your carbon footprint. How can I help you today?"
        ),
        IntentPattern(
            patterns = listOf(
                Regex("thank|thanks|appreciate", RegexOption.IGNORE_CASE),
                Regex("good bot", RegexOption.IGNORE_CASE)
            ),
            answer = "You're welcome! I'm glad to help. Keep making sustainable choices – every action counts! 🌍"
        ),
        IntentPattern(
            patterns = listOf(
                Regex("dark mode|brightness", RegexOption.IGNORE_CASE),
                Regex("screen brightness", RegexOption.IGNORE_CASE)
            ),
            answer = "Dark mode can save significant energy on OLED screens (up to 20%). Reducing screen brightness also helps lower your device's carbon footprint."
        ),
        IntentPattern(
            patterns = listOf(
                Regex("battery|charging", RegexOption.IGNORE_CASE),
                Regex("battery health", RegexOption.IGNORE_CASE)
            ),
            answer = "Batteries lose efficiency over time, which increases charging losses. Keeping your battery between 20% and 80% and avoiding extreme temperatures can extend its life."
        ),
        IntentPattern(
            patterns = listOf(
                Regex("grid|electricity|energy mix", RegexOption.IGNORE_CASE),
                Regex("renewable", RegexOption.IGNORE_CASE)
            ),
            answer = "Your local electricity grid's carbon intensity (gCO₂/kWh) directly affects every component's emissions. A cleaner grid means lower carbon footprint for the same usage."
        )
    )

    private val defaultAnswer = """
        I'm your carbon footprint assistant! I can help you understand and reduce your environmental impact.
        
        Try asking about:
        • How to reduce my carbon footprint
        • Diet and food choices
        • Transportation tips
        • Home energy savings
        • Recycling and waste reduction
        • Dark mode and screen brightness
        • Battery health
        • Grid intensity
        
        Just type your question above!
    """.trimIndent()

    fun getResponse(userMessage: String): String {
        val lowerMsg = userMessage.lowercase().trim()
        Log.d(TAG, "User message: $lowerMsg")

        for (intent in intentMap) {
            for (pattern in intent.patterns) {
                if (pattern.containsMatchIn(lowerMsg)) {
                    Log.d(TAG, "Matched intent: ${intent.patterns.first().pattern}")
                    return intent.answer
                }
            }
        }
        return defaultAnswer
    }

    private data class IntentPattern(val patterns: List<Regex>, val answer: String)
}