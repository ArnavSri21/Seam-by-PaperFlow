package com.example.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

data class CalendarSyncResult(
    val isSynced: Boolean,
    val eventId: Long? = null,
    val calendarDisplayName: String = "",
    val statusMessage: String = ""
)

object GoogleCalendarSyncHelper {

    fun hasCalendarPermissions(context: Context): Boolean {
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return writeGranted && readGranted
    }

    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private data class TargetCalendar(
        val id: Long,
        val displayName: String,
        val accountType: String,
        val isPrimary: Boolean
    )

    private fun findBestGoogleOrWritableCalendar(context: Context): TargetCalendar? {
        if (!hasCalendarPermissions(context)) return null
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        val candidates = mutableListOf<TargetCalendar>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val typeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                while (cursor.moveToNext()) {
                    val calId = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else "Calendar"
                    val acctType = if (typeIdx >= 0) cursor.getString(typeIdx).orEmpty() else ""
                    val isPrimary = primaryIdx >= 0 && cursor.getInt(primaryIdx) == 1
                    candidates.add(TargetCalendar(calId, name.ifBlank { "Google Calendar" }, acctType, isPrimary))
                }
            }
        }

        return candidates.sortedWith(
            compareByDescending<TargetCalendar> { it.accountType == "com.google" }
                .thenByDescending { it.isPrimary }
        ).firstOrNull()
    }

    fun insertEventIntoGoogleCalendar(
        context: Context,
        title: String,
        description: String,
        startMillis: Long,
        durationMinutes: Int = 60,
        location: String = ""
    ): CalendarSyncResult {
        if (!hasCalendarPermissions(context)) {
            return CalendarSyncResult(
                isSynced = false,
                statusMessage = "Calendar permission needed to auto-sync directly to Google Calendar."
            )
        }

        val target = findBestGoogleOrWritableCalendar(context)
            ?: return CalendarSyncResult(
                isSynced = false,
                statusMessage = "Saved in app To-Do list (no signed-in device calendar provider found — tap Calendar icon to export)."
            )

        val safeStart = startMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val safeEnd = safeStart + durationMinutes.coerceIn(15, 480) * 60_000L
        val timeZoneId = TimeZone.getDefault().id

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, target.id)
            put(CalendarContract.Events.TITLE, title.trim().ifBlank { "Seam Scheduled Task" })
            if (description.isNotBlank()) {
                put(CalendarContract.Events.DESCRIPTION, description.trim())
            }
            if (location.isNotBlank()) {
                put(CalendarContract.Events.EVENT_LOCATION, location.trim())
            }
            put(CalendarContract.Events.DTSTART, safeStart)
            put(CalendarContract.Events.DTEND, safeEnd)
            put(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId)
        }

        return try {
            val insertedUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = insertedUri?.lastPathSegment?.toLongOrNull()
            if (eventId != null && eventId > 0L) {
                CalendarSyncResult(
                    isSynced = true,
                    eventId = eventId,
                    calendarDisplayName = target.displayName,
                    statusMessage = "Added to To-Do & ${target.displayName}"
                )
            } else {
                CalendarSyncResult(
                    isSynced = false,
                    statusMessage = "Saved in To-Do list; system calendar insert returned no ID."
                )
            }
        } catch (e: SecurityException) {
            CalendarSyncResult(
                isSynced = false,
                statusMessage = "Calendar permission missing for direct sync."
            )
        } catch (e: Exception) {
            CalendarSyncResult(
                isSynced = false,
                statusMessage = "Saved in To-Do list (${e.localizedMessage ?: "calendar unavailable"})."
            )
        }
    }

    fun buildCreateCalendarEventIntent(
        title: String,
        description: String,
        startMillis: Long,
        durationMinutes: Int = 60,
        location: String = ""
    ): Intent {
        val safeStart = startMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val safeEnd = safeStart + durationMinutes.coerceIn(15, 480) * 60_000L
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            if (description.isNotBlank()) {
                putExtra(CalendarContract.Events.DESCRIPTION, description)
            }
            if (location.isNotBlank()) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            }
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, safeStart)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, safeEnd)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildOpenCalendarEventIntent(eventId: Long): Intent {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return Intent(Intent.ACTION_VIEW).apply {
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
