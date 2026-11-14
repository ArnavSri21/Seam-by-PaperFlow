package com.example.data.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class GalleryPermissionStatus {
    GRANTED_FULL,
    GRANTED_SELECTED_ONLY,
    DENIED
}

data class DevicePhotoAsset(
    val mediaStoreId: Long,
    val contentUri: Uri,
    val displayName: String,
    val albumName: String,
    val dateTakenMillis: Long,
    val width: Int,
    val height: Int,
    val mimeType: String
) {
    val dedupeKey: String
        get() = "mediastore:$mediaStoreId"
}

/**
 * Checks Android version-specific gallery permissions and queries device photos from MediaStore
 * so Memories can automatically import and AI-index photos upon user consent.
 */
object DeviceGalleryAutoImporter {

    fun requiredGalleryPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    fun getGalleryPermissionStatus(context: Context): GalleryPermissionStatus {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val hasFull = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
                val hasPartial = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED
                when {
                    hasFull -> GalleryPermissionStatus.GRANTED_FULL
                    hasPartial -> GalleryPermissionStatus.GRANTED_SELECTED_ONLY
                    else -> GalleryPermissionStatus.DENIED
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val hasFull = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
                if (hasFull) GalleryPermissionStatus.GRANTED_FULL else GalleryPermissionStatus.DENIED
            }
            else -> {
                val hasLegacy = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (hasLegacy) GalleryPermissionStatus.GRANTED_FULL else GalleryPermissionStatus.DENIED
            }
        }
    }

    suspend fun queryRecentDevicePhotos(
        context: Context,
        limit: Int = 60
    ): List<DevicePhotoAsset> = withContext(Dispatchers.IO) {
        if (getGalleryPermissionStatus(context) == GalleryPermissionStatus.DENIED) {
            return@withContext emptyList()
        }

        val photos = mutableListOf<DevicePhotoAsset>()
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val dateTakenCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)

                while (cursor.moveToNext() && photos.size < limit) {
                    val id = cursor.getLong(idCol)
                    val displayName = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else "Photo_$id.jpg"
                    val album = if (bucketCol >= 0) cursor.getString(bucketCol).orEmpty().ifBlank { "Recents" } else "Recents"
                    val rawTaken = if (dateTakenCol >= 0) cursor.getLong(dateTakenCol) else 0L
                    val rawAddedSec = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                    val timestamp = when {
                        rawTaken > 0L -> rawTaken
                        rawAddedSec > 0L -> rawAddedSec * 1000L
                        else -> System.currentTimeMillis()
                    }
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    val mimeType = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else "image/jpeg"

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    photos.add(
                        DevicePhotoAsset(
                            mediaStoreId = id,
                            contentUri = contentUri,
                            displayName = displayName,
                            albumName = album,
                            dateTakenMillis = timestamp,
                            width = width,
                            height = height,
                            mimeType = mimeType
                        )
                    )
                }
            }
        }

        photos
    }
}
