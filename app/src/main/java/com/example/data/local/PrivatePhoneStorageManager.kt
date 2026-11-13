package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages automatic private phone storage inside context.filesDir/vault_media
 * and AES-GCM encryption for stored credentials and sensitive reflections.
 */
class PrivatePhoneStorageManager(private val context: Context) {

    private val vaultMediaDir: File by lazy {
        File(context.filesDir, "vault_media").apply {
            if (!exists()) mkdirs()
        }
    }

    private val exportVaultDir: File by lazy {
        File(context.filesDir, "vault_backups").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun copyPickedUriToPrivateStorage(uri: Uri): String = withContext(Dispatchers.IO) {
        val fileName = "memory_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
        val destFile = File(vaultMediaDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        destFile.absolutePath
    }

    /**
     * Generates curated high-resolution mindful gallery artwork directly inside private phone storage
     * on first launch so the user has tangible, inspectable files in their private phone storage.
     */
    suspend fun ensureMindfulArtworkFile(
        fileName: String,
        primaryColor: Int,
        secondaryColor: Int,
        accentColor: Int,
        styleIndex: Int
    ): String = withContext(Dispatchers.IO) {
        val targetFile = File(vaultMediaDir, fileName)
        if (targetFile.exists() && targetFile.length() > 0L) {
            return@withContext targetFile.absolutePath
        }
        val width = 900
        val height = 700
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                primaryColor, secondaryColor, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width * 0.65f, height * 0.35f, width * 0.55f,
                accentColor, 0x00000000, Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(width * 0.65f, height * 0.35f, width * 0.55f, glowPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = 0x55FFFFFF
        }

        for (i in 1..6) {
            val radius = (i * 58f) + (styleIndex * 14f)
            canvas.drawCircle(width * 0.5f, height * 0.52f, radius, ringPaint)
            canvas.drawOval(
                width * 0.5f - radius * 1.2f,
                height * 0.52f - radius * 0.68f,
                width * 0.5f + radius * 1.2f,
                height * 0.52f + radius * 0.68f,
                ringPaint
            )
        }

        FileOutputStream(targetFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bitmap.recycle()
        targetFile.absolutePath
    }

    suspend fun writeAutoSnapshotJson(userId: String, jsonPayload: String): String = withContext(Dispatchers.IO) {
        val snapshotFile = File(exportVaultDir, "vault_snapshot_${userId.take(8)}.json")
        snapshotFile.writeText(jsonPayload)
        snapshotFile.absolutePath
    }

    suspend fun cleanupPreloadedSampleFiles() = withContext(Dispatchers.IO) {
        listOf("seam_constellation_01.jpg", "seam_editorial_solstice_02.jpg").forEach { sampleName ->
            val file = File(vaultMediaDir, sampleName)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun getPrivateStorageStats(): Pair<Int, Long> {
        val mediaFiles = vaultMediaDir.listFiles().orEmpty()
        val backupFiles = exportVaultDir.listFiles().orEmpty()
        val totalFiles = mediaFiles.size + backupFiles.size
        val totalBytes = mediaFiles.sumOf { it.length() } + backupFiles.sumOf { it.length() }
        return totalFiles to totalBytes
    }

    fun encryptSecret(plainText: String, userId: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val keyBytes = MessageDigest.getInstance("SHA-256")
                .digest("paperflow_vault_key_$userId".toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            plainText
        }
    }

    fun decryptSecret(encodedText: String, userId: String): String {
        if (encodedText.isBlank()) return ""
        return try {
            val combined = Base64.decode(encodedText, Base64.NO_WRAP)
            if (combined.size <= 12) return encodedText
            val iv = combined.copyOfRange(0, 12)
            val cipherText = combined.copyOfRange(12, combined.size)
            val keyBytes = MessageDigest.getInstance("SHA-256")
                .digest("paperflow_vault_key_$userId".toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            encodedText
        }
    }
}
