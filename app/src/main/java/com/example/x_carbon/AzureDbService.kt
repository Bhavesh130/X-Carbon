package com.example.x_carbon

import android.util.Log
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets.UTF_8

object AzureDbService {

    private const val ENDPOINT = "https://x-carbon-db.documents.azure.com:443/"

    private const val KEY = BuildConfig.AZURE_COSMOS_KEY
    private const val DATABASE_ID = "xcarbon"

    private const val CONTAINER_ID = "history"
    private const val USER_ID = "user_default"
    private const val TAG = "AzureDbService"

    private val client = OkHttpClient.Builder().build()
    private val gson = Gson()

    private fun generateAuthToken(
        verb: String,
        resourceType: String,
        resourceLink: String,
        date: String
    ): String {
        return try {
            val keyBytes = Base64.decode(KEY.trim(), Base64.DEFAULT)

            val text = verb.lowercase() + "\n" +
                    resourceType.lowercase() + "\n" +
                    resourceLink.lowercase() + "\n" +
                    date.lowercase() + "\n\n"

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))

            val hash = mac.doFinal(text.toByteArray(UTF_8))
            val signature = Base64.encodeToString(hash, Base64.NO_WRAP)

            val token = "type=master&ver=1.0&sig=$signature"

            return java.net.URLEncoder.encode(token, "UTF-8")

        } catch (e: Exception) {
            Log.e(TAG, "Error generating auth token", e)
            ""
        }
    }

    private fun getCurrentUtcDate(): String {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("GMT")
        return sdf.format(Date())
    }

    private fun createDocumentUri(): String {
        return "${ENDPOINT}dbs/$DATABASE_ID/colls/$CONTAINER_ID/docs"
    }

    private fun documentUriById(docId: String): String {
        return "${ENDPOINT}dbs/$DATABASE_ID/colls/$CONTAINER_ID/docs/$docId"
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val value = get(key) ?: return null
        return if (value.isJsonNull) null else value.asString
    }

    private fun JsonObject.longOrDefault(key: String, default: Long = 0L): Long {
        val value = get(key) ?: return default
        return if (value.isJsonNull) default else value.asLong
    }

    private fun JsonObject.doubleOrDefault(key: String, default: Double = 0.0): Double {
        val value = get(key) ?: return default
        return if (value.isJsonNull) default else value.asDouble
    }

    private fun JsonObject.intOrDefault(key: String, default: Int = 0): Int {
        val value = get(key) ?: return default
        return if (value.isJsonNull) default else value.asInt
    }

    suspend fun saveRecord(record: CarbonHistoryRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            val docId = "${USER_ID}_${record.timestamp}"
            val document = mapOf(
                "id" to docId,
                "userId" to USER_ID,
                "timestamp" to record.timestamp,
                "co2Kg" to record.co2Kg,
                "gridIntensity" to record.gridIntensity,
                "thermalState" to record.thermalState,
                "mbWifi" to record.mbWifi,
                "mbMobile" to record.mbMobile
            )
            val json = gson.toJson(document)
            val date = getCurrentUtcDate()
            val resourceLink = "dbs/$DATABASE_ID/colls/$CONTAINER_ID"
            val authToken = generateAuthToken("POST", "docs", resourceLink, date)

            val request = Request.Builder()
                .url(createDocumentUri())
                .header("Authorization", authToken)
                .header("x-ms-date", date)
                .header("x-ms-version", "2018-12-31")
                .header("Content-Type", "application/json")
                .header("x-ms-documentdb-partitionkey", "[\"$USER_ID\"]")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ Record saved to Azure: ${record.co2Kg} kg CO₂")
                    return@withContext true
                } else {
                    Log.e(TAG, "❌ Save failed: ${response.code} $body")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save record", e)
            return@withContext false
        }
    }

    suspend fun loadRecords(): List<CarbonHistoryRecord> = withContext(Dispatchers.IO) {
        try {
            val query = "SELECT * FROM c WHERE c.userId = @userId ORDER BY c.timestamp DESC"

            val bodyJson = """
{
    "query": "$query",
    "parameters": [
        {
            "name": "@userId",
            "value": "$USER_ID"
        }
    ]
}
""".trimIndent()
            val resourceLink = "dbs/$DATABASE_ID/colls/$CONTAINER_ID"
            val records = mutableListOf<CarbonHistoryRecord>()
            var continuationToken: String? = null

            do {
                val date = getCurrentUtcDate()
                val authToken = generateAuthToken("POST", "docs", resourceLink, date)

                val request = Request.Builder()
                    .url(createDocumentUri())
                    .header("Authorization", authToken)
                    .header("x-ms-date", date)
                    .header("x-ms-version", "2018-12-31")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/query+json")
                    .header("x-ms-documentdb-isquery", "true")
                    .header("x-ms-documentdb-query-enablecrosspartition", "true")
                    .header("x-ms-documentdb-partitionkey", "[\"$USER_ID\"]")
                    .apply {
                        continuationToken?.let { header("x-ms-continuation", it) }
                    }
                    .post(bodyJson.toRequestBody("application/query+json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        Log.e(TAG, "❌ Query failed: ${response.code} $body")
                        return@withContext emptyList()
                    }

                    val json = gson.fromJson(body, JsonObject::class.java)
                    val docs = json.getAsJsonArray("Documents")
                    docs?.forEach { doc ->
                        try {
                            val obj = doc.asJsonObject
                            val timestamp = obj.longOrDefault("timestamp")
                            if (timestamp <= 0L) {
                                Log.w(TAG, "Skipping document with invalid timestamp: ${obj.stringOrNull("id")}")
                                return@forEach
                            }

                            records.add(
                                CarbonHistoryRecord(
                                    timestamp = timestamp,
                                    co2Kg = obj.doubleOrDefault("co2Kg"),
                                    gridIntensity = obj.doubleOrDefault("gridIntensity"),
                                    thermalState = obj.intOrDefault("thermalState"),
                                    mbWifi = obj.doubleOrDefault("mbWifi"),
                                    mbMobile = obj.doubleOrDefault("mbMobile")
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping malformed document", e)
                        }
                    }

                    continuationToken = response.header("x-ms-continuation")?.takeIf { it.isNotBlank() }
                }
            } while (continuationToken != null)

            val sortedRecords = records.sortedByDescending { it.timestamp }
            Log.d(TAG, "✅ Loaded ${sortedRecords.size} records from Azure")
            sortedRecords
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load records", e)
            emptyList()
        }
    }

    suspend fun clearRecords(): Boolean = withContext(Dispatchers.IO) {
        try {
            val records = loadRecords()
            var allDeleted = true
            for (record in records) {
                val docId = "${USER_ID}_${record.timestamp}"
                val url = documentUriById(docId)
                val date = getCurrentUtcDate()
                val resourceLink = "dbs/$DATABASE_ID/colls/$CONTAINER_ID/docs/$docId"
                val authToken = generateAuthToken("DELETE", "docs", resourceLink, date)

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", authToken)
                    .header("x-ms-date", date)
                    .header("x-ms-version", "2018-12-31")
                    .header("x-ms-documentdb-query-enablecrosspartition", "true")
                    .delete()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        allDeleted = false
                        Log.e(TAG, "Delete failed for $docId: ${response.code}")
                    }
                }
            }
            Log.d(TAG, "All records cleared")
            allDeleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear records", e)
            false
        }
    }

    fun close() {
        // No-op
    }
}
