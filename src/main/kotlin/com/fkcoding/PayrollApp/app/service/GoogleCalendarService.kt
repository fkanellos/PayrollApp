package com.fkcoding.PayrollApp.app.service

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.Events
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import java.io.File
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneId

data class CalendarEvent(
    val id: String,
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val colorId: String?,
    val isCancelled: Boolean = false,
    val isPendingPayment: Boolean = false,
    val attendees: List<String> = emptyList()
)

@Service
class GoogleCalendarService(
    private val resourceLoader: ResourceLoader
) {

    companion object {
        private const val APPLICATION_NAME = "Payroll System"
        private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()
        private const val TOKENS_DIRECTORY_PATH = "tokens"
        private val SCOPES = listOf(CalendarScopes.CALENDAR_READONLY)
    }

    @Value("\${google.calendar.credentials.path:classpath:data/credentials.json}")
    private lateinit var credentialsFilePath: String

    private lateinit var service: Calendar

    @PostConstruct
    fun initialize() {
        try {
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
            service = Calendar.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                .setApplicationName(APPLICATION_NAME)
                .build()
            println("✅ Google Calendar service initialized successfully")
        } catch (e: Exception) {
            println("❌ Failed to initialize Google Calendar service: ${e.message}")
            throw e
        }
    }

    private fun getCredentials(httpTransport: NetHttpTransport): Credential {
        try {
            // Load client secrets using ResourceLoader
            val resource = resourceLoader.getResource(credentialsFilePath)

            if (!resource.exists()) {
                throw RuntimeException("Credentials file not found at: $credentialsFilePath")
            }

            val clientSecrets = GoogleClientSecrets.load(
                JSON_FACTORY,
                InputStreamReader(resource.inputStream)
            )

            // Build flow and trigger user authorization request
            val flow = GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES
            )
                .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build()

            val receiver = LocalServerReceiver.Builder().setPort(8888).build()
            return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

        } catch (e: Exception) {
            throw RuntimeException("Credentials file not found at: $credentialsFilePath", e)
        }
    }

    fun getEventsForPeriod(
        calendarId: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<CalendarEvent> {
        return try {
            val timeMin = DateTime(startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            val timeMax = DateTime(endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

            val events: Events = service.events().list(calendarId)
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .setShowDeleted(false)
                .execute()

            events.items?.map { event ->
                val startTime = if (event.start.dateTime != null) {
                    LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(event.start.dateTime.value),
                        ZoneId.systemDefault()
                    )
                } else {
                    LocalDateTime.parse(event.start.date.toString() + "T00:00:00")
                }

                val endTime = if (event.end.dateTime != null) {
                    LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(event.end.dateTime.value),
                        ZoneId.systemDefault()
                    )
                } else {
                    LocalDateTime.parse(event.end.date.toString() + "T23:59:59")
                }

                val isCancelled = event.status == "cancelled"
                val colorId = event.colorId
                val isPendingPayment = isCancelled && isGreyCancellation(colorId)

                val attendeeEmails = event.attendees?.map { it.email } ?: emptyList()

                CalendarEvent(
                    id = event.id ?: "",
                    title = event.summary ?: "Χωρίς τίτλο",
                    startTime = startTime,
                    endTime = endTime,
                    colorId = colorId,
                    isCancelled = isCancelled,
                    isPendingPayment = isPendingPayment,
                    attendees = attendeeEmails
                )
            } ?: emptyList()

        } catch (e: Exception) {
            println("❌ Error fetching calendar events: ${e.message}")
            emptyList()
        }
    }

    private fun isGreyCancellation(colorId: String?): Boolean {
        return colorId == "8" || colorId == "9"
    }

    fun filterEventsByClientNames(
        events: List<CalendarEvent>,
        clientNames: List<String>
    ): Map<String, List<CalendarEvent>> {
        val clientEvents = clientNames.associateWith { mutableListOf<CalendarEvent>() }
        val unmatchedEvents = mutableListOf<CalendarEvent>()

        for (event in events) {
            val matches = findClientMatches(event.title, clientNames)
            if (matches.isNotEmpty()) {
                val clientName = matches.first()
                clientEvents[clientName]?.add(event)
            } else {
                unmatchedEvents.add(event)
            }
        }

        val matchedCount = clientEvents.values.sumOf { it.size }
        val cancelledCount = events.count { it.isCancelled }
        val pendingPaymentCount = events.count { it.isPendingPayment }

        println("🎯 Matched events: $matchedCount")
        println("❌ Cancelled events: $cancelledCount")
        println("⏳ Pending payment events: $pendingPaymentCount")
        println("❓ Unmatched events: ${unmatchedEvents.size}")

        return clientEvents.mapValues { it.value.toList() }
    }

    private fun findClientMatches(title: String, clientNames: List<String>): List<String> {
        if (title.isBlank()) return emptyList()

        val titleLower = title.lowercase().trim()
        val matches = mutableListOf<String>()

        for (clientName in clientNames) {
            if (clientName.isBlank()) continue

            val clientLower = clientName.lowercase()
            val nameParts = clientLower.split(" ")

            if (nameParts.size < 2) {
                if (clientLower in titleLower) {
                    matches.add(clientName)
                }
                continue
            }

            if (clientLower in titleLower) {
                matches.add(clientName)
                continue
            }

            val reversedName = "${nameParts.last()} ${nameParts.first()}"
            if (reversedName in titleLower) {
                matches.add(clientName)
                continue
            }

            val surname = nameParts.last()
            if (surname.length > 3) {
                val regex = "\\b${Regex.escape(surname)}\\b".toRegex()
                if (regex.find(titleLower) != null) {
                    matches.add(clientName)
                    continue
                }
            }

            val firstName = nameParts.first()
            if (firstName.length > 4) {
                val regex = "\\b${Regex.escape(firstName)}\\b".toRegex()
                if (regex.find(titleLower) != null) {
                    matches.add(clientName)
                }
            }
        }

        return matches
    }

    fun getCalendarList(): List<Map<String, Any>> {
        return try {
            val calendarList = service.calendarList().list().execute()
            calendarList.items?.map { calendar ->
                mapOf(
                    "id" to (calendar.id ?: ""),
                    "summary" to (calendar.summary ?: ""),
                    "primary" to (calendar.primary ?: false),
                    "accessRole" to (calendar.accessRole ?: "")
                )
            } ?: emptyList()
        } catch (e: Exception) {
            println("❌ Error fetching calendar list: ${e.message}")
            emptyList()
        }
    }
}