package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.local.VaultEntryEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

private const val TAG = "MindfulAiService"

data class AiEntryAnalysis(
    val category: String,
    val moodTag: String,
    val aiSummary: String,
    val aiKeywords: String
)

data class SemanticSearchExpansion(
    val expandedTerms: List<String>,
    val matchedEntryIds: List<Long>,
    val mindfulSearchInsight: String
)

class MindfulAiService {

    private val entryAnalysisSchema = Schema.obj(
        properties = mapOf(
            "category" to Schema.string(description = "A concise 1-2 word category such as Reflection, Gratitude, Architecture, Nature, Security, Finance, Creative, or Travel"),
            "moodTag" to Schema.string(description = "A 1-word mindful emotional or security tone such as Serene, Contemplative, Protected, Grounded, Luminous, or Focused"),
            "aiSummary" to Schema.string(description = "A poetic yet clear 1-sentence summary of the entry"),
            "aiKeywords" to Schema.string(description = "Comma-separated list of 10-15 semantic synonyms, related concepts, emotions, and alternate search terms so the entry is found even when searched with similar keywords")
        )
    )

    private val searchExpansionSchema = Schema.obj(
        properties = mapOf(
            "expandedTerms" to Schema.array(
                items = Schema.string(),
                description = "6 to 10 semantically similar keywords, synonyms, and related concepts for the user query"
            ),
            "matchedEntryIds" to Schema.array(
                items = Schema.integer(),
                description = "IDs of vault entries that semantically match the query intent"
            ),
            "mindfulSearchInsight" to Schema.string(
                description = "A calm 1-sentence synthesis explaining how the matched entries connect to the user query"
            )
        )
    )

    private val analysisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = entryAnalysisSchema
            }
        )
    }

    private val searchModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = searchExpansionSchema
            }
        )
    }

    private val reflectionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    suspend fun analyzeEntry(entry: VaultEntryEntity): Result<AiEntryAnalysis> = withContext(Dispatchers.IO) {
        try {
            val bitmap: Bitmap? = if (entry.itemType == "GALLERY" && entry.mediaPath.isNotBlank() && !entry.mediaPath.startsWith("raw://")) {
                loadScaledBitmap(entry.mediaPath)
            } else {
                null
            }

            val promptText = buildString {
                appendLine("Analyze and organize this personal vault entry for a mindful space app called Paper Flow.")
                appendLine("Entry Type: ${entry.itemType}")
                appendLine("Title: ${entry.title}")
                if (entry.itemType == "PASSWORD") {
                    // Never send the raw password value; only analyze service title, username label, and domain
                    appendLine("Account / Label: ${entry.primaryContent}")
                    appendLine("Domain / Service: ${entry.websiteOrLocation}")
                    appendLine("Generate semantic keywords for finding this credential (e.g., login, account, service category, banking, developer, email, cloud, security).")
                } else {
                    appendLine("Content / Reflection: ${entry.primaryContent}")
                    if (entry.websiteOrLocation.isNotBlank()) {
                        appendLine("Context / Location: ${entry.websiteOrLocation}")
                    }
                }
                appendLine("Provide a refined category, a mindful moodTag, a 1-sentence aiSummary, and rich comma-separated aiKeywords (synonyms, related themes, feelings, and concepts) so the user can find this item even when searching for similar keywords.")
            }

            val response = withTimeout(60_000L) {
                if (bitmap != null) {
                    analysisModel.generateContent(
                        content {
                            image(bitmap)
                            text(promptText)
                        }
                    )
                } else {
                    analysisModel.generateContent(promptText)
                }
            }

            val jsonText = response.text ?: throw IllegalStateException("Empty AI analysis response")
            val json = JSONObject(jsonText)
            Result.success(
                AiEntryAnalysis(
                    category = json.optString("category", entry.category.ifBlank { "Mindful" }),
                    moodTag = json.optString("moodTag", entry.moodTag.ifBlank { "Serene" }),
                    aiSummary = json.optString("aiSummary", ""),
                    aiKeywords = json.optString("aiKeywords", "")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI entry analysis timed out", e)
            Result.failure(Exception("AI analysis timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI entry analysis failed", e)
            Result.failure(e)
        }
    }

    suspend fun performSemanticSearch(
        query: String,
        entries: List<VaultEntryEntity>
    ): Result<SemanticSearchExpansion> = withContext(Dispatchers.IO) {
        try {
            val catalogSummary = entries.take(50).joinToString("\n") { item ->
                "ID:${item.id} | Type:${item.itemType} | Title:${item.title} | Category:${item.category} | Mood:${item.moodTag} | Summary:${item.aiSummary} | Keywords:${item.aiKeywords}"
            }
            val prompt = """
                The user is searching their private Paper Flow sanctuary for: "$query"
                
                Even if the exact word does not appear, match entries by similar meaning, synonyms, emotional resonance, or domain category.
                Here is the user's indexed catalog:
                $catalogSummary
                
                Return expandedTerms (similar keywords), matchedEntryIds (IDs of semantically relevant entries), and a calm 1-sentence mindfulSearchInsight.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                searchModel.generateContent(prompt)
            }
            val jsonString = response.text ?: throw IllegalStateException("Empty semantic search response")
            val json = JSONObject(jsonString)

            val termsArray = json.optJSONArray("expandedTerms")
            val terms = mutableListOf<String>()
            if (termsArray != null) {
                for (i in 0 until termsArray.length()) {
                    terms.add(termsArray.getString(i))
                }
            }

            val idsArray = json.optJSONArray("matchedEntryIds")
            val ids = mutableListOf<Long>()
            if (idsArray != null) {
                for (i in 0 until idsArray.length()) {
                    ids.add(idsArray.getLong(i))
                }
            }

            val insight = json.optString("mindfulSearchInsight", "Matched entries based on semantic similarity.")
            Result.success(SemanticSearchExpansion(terms, ids, insight))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Semantic search timed out", e)
            Result.failure(Exception("Semantic search timed out. Showing local keyword matches."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Semantic search failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateSanctuarySynthesis(entries: List<VaultEntryEntity>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val recent = entries.take(15).joinToString("\n") {
                "- [${it.itemType}] ${it.title} (${it.category}, ${it.moodTag}): ${it.aiSummary.ifBlank { it.primaryContent.take(90) }}"
            }
            val prompt = """
                You are the quiet, mindful curator inside 'Paper Flow'.
                Based on these recent reflections, visual memories, and organized vault entries:
                $recent
                
                Write a serene, deeply thoughtful 2-sentence reflection noticing the themes, emotional cadence, and organization of their sanctuary.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                reflectionModel.generateContent(prompt)
            }
            val text = response.text?.trim().orEmpty()
            if (text.isNotBlank()) {
                Result.success(text)
            } else {
                Result.failure(Exception("Empty synthesis returned."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate sanctuary synthesis", e)
            Result.failure(e)
        }
    }

    private fun loadScaledBitmap(path: String): Bitmap? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            var inSampleSize = 1
            val maxDim = 1024
            if (options.outHeight > maxDim || options.outWidth > maxDim) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxDim && (halfWidth / inSampleSize) >= maxDim) {
                    inSampleSize *= 2
                }
            }
            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (e: Exception) {
            null
        }
    }
}
