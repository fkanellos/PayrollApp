package com.fkcoding.PayrollApp.app.service

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.auth.oauth2.TokenResponseException
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
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import java.io.File
import java.io.InputStreamReader
import java.net.SocketTimeoutException
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

        // ✅ ABSOLUTE PATH
        private val TOKENS_DIRECTORY_PATH = File(
            System.getProperty("user.home"),
            ".credentials/payroll-app"
        )

        private val SCOPES = listOf(
            CalendarScopes.CALENDAR_READONLY,
            "https://www.googleapis.com/auth/spreadsheets",
            "https://www.googleapis.com/auth/drive.file",
            "https://www.googleapis.com/auth/drive.readonly"  // ✅ Πρόσθεσε Drive access!
        )

        private val logger = LoggerFactory.getLogger(GoogleCalendarService::class.java)
    }

    @Value("\${google.calendar.credentials.path:classpath:data/credentials.json}")
    private lateinit var credentialsFilePath: String

    private var service: Calendar? = null  // ✅ Nullable!
    private var isInitialized = false

    @PostConstruct
    fun initialize() {
        try {
            logger.info("📁 Tokens directory: ${TOKENS_DIRECTORY_PATH.absolutePath}")
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
            service = Calendar.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                .setApplicationName(APPLICATION_NAME)
                .build()
            isInitialized = true
            logger.info("✅ Google Calendar service initialized successfully")
        } catch (e: SocketTimeoutException) {
            logger.error("❌ Network timeout while initializing Google Calendar. App will continue without Calendar integration.")
            logger.error("   To fix: Check internet connection or delete tokens: rm -rf ~/.credentials/payroll-app")
            isInitialized = false
            // ✅ DON'T THROW - Let app continue!
        } catch (e: Exception) {
            logger.error("❌ Failed to initialize Google Calendar service: ${e.message}", e)
            logger.warn("⚠️ App will continue without Calendar integration")
            isInitialized = false
            // ✅ DON'T THROW - Let app continue!
        }
    }

    private fun getCredentials(httpTransport: NetHttpTransport): Credential {
        try {
            // 1️⃣ Create folder if needed
            if (!TOKENS_DIRECTORY_PATH.exists()) {
                logger.info("📁 Creating tokens directory: ${TOKENS_DIRECTORY_PATH.absolutePath}")
                TOKENS_DIRECTORY_PATH.mkdirs()
            }

            val resource = resourceLoader.getResource(credentialsFilePath)
            if (!resource.exists()) {
                throw RuntimeException("Credentials file not found at: $credentialsFilePath")
            }

            val clientSecrets = GoogleClientSecrets.load(
                JSON_FACTORY,
                InputStreamReader(resource.inputStream)
            )

            val flow = GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES
            )
                .setDataStoreFactory(FileDataStoreFactory(TOKENS_DIRECTORY_PATH))
                .setAccessType("offline")
                .build()

            val receiver = LocalServerReceiver.Builder()
                .setPort(8889)
                .build()

            return try {
                logger.info("🔐 Loading credentials...")
                val credential = AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

                if (credential.refreshToken != null) {
                    logger.info("✅ Refresh token found! Credentials will persist.")
                } else {
                    logger.warn("⚠️ No refresh token!")
                }

                // ✅ Try refresh with timeout protection
                try {
                    if (credential.expiresInSeconds != null && credential.expiresInSeconds!! <= 300) {
                        logger.info("⚠️ Token expiring in ${credential.expiresInSeconds}s, refreshing...")

                        // Set timeout for refresh
                        val refreshed = credential.refreshToken()
                        if (refreshed) {
                            logger.info("✅ Token refreshed successfully")
                        }
                    } else {
                        logger.info("✅ Token is valid (expires in ${credential.expiresInSeconds}s)")
                    }
                } catch (e: SocketTimeoutException) {
                    logger.warn("⚠️ Token refresh timed out - will use existing token")
                    // Continue with existing token
                }

                credential

            } catch (e: TokenResponseException) {
                logger.error("❌ Token invalid/expired: ${e.message}")
                logger.info("🔄 Deleting invalid token...")

                val storedCredFile = File(TOKENS_DIRECTORY_PATH, "StoredCredential")
                if (storedCredFile.exists()) {
                    storedCredFile.delete()
                    logger.info("🗑️ Deleted invalid token file")
                }

                logger.warn("⚠️ Please authorize in the browser window...")
                AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

            } catch (e: SocketTimeoutException) {
                logger.error("❌ Network timeout during authorization")
                throw RuntimeException("Network timeout - check internet connection", e)
            } catch (e: Exception) {
                logger.error("❌ Unexpected error during authorization", e)
                throw RuntimeException("Failed to authorize: ${e.message}", e)
            }

        } catch (e: Exception) {
            throw RuntimeException("Failed to load credentials: ${e.message}", e)
        }
    }

    /**
     * ✅ Check if service is available before using
     */
    private fun ensureInitialized(): Boolean {
        if (!isInitialized || service == null) {
            logger.warn("⚠️ Google Calendar service not available")
            return false
        }
        return true
    }

    fun getEventsForPeriod(
        calendarId: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<CalendarEvent> {
        if (!ensureInitialized()) {
            logger.warn("⚠️ Cannot fetch events - Calendar service not initialized")
            return emptyList()
        }

        return try {
            val timeMin = DateTime(startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            val timeMax = DateTime(endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

            val events: Events = service!!.events().list(calendarId)
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
            logger.error("❌ Error fetching calendar events: ${e.message}", e)
            emptyList()
        }
    }

    private fun isGreyCancellation(colorId: String?): Boolean {
        return colorId == "8"
    }

    private fun isRedCancellation(colorId: String?, summary: String): Boolean {
        return colorId == "11" && !isSupervision(summary)
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

                if (matches.size > 1) {
                    logger.warn("⚠️ Multiple matches for '${event.title}': $matches")
                }
            } else {
                unmatchedEvents.add(event)
            }
        }

        val matchedCount = clientEvents.values.sumOf { it.size }
        val cancelledCount = events.count { it.isCancelled }
        val pendingPaymentCount = events.count { it.isPendingPayment }

        logger.info("🎯 Matched events: $matchedCount")
        logger.info("❌ Cancelled events: $cancelledCount")
        logger.info("⏳ Pending payment events: $pendingPaymentCount")
        logger.info("❓ Unmatched events: ${unmatchedEvents.size}")

        return clientEvents.mapValues { it.value.toList() }
    }

    private fun findClientMatches(
        title: String,
        clientNames: List<String>,
        specialKeywords: List<String> = emptyList()
    ): List<String> {
        if (title.isBlank()) return emptyList()

        val titleLower = title.lowercase().trim()
            .replace("ά", "α").replace("έ", "ε")
            .replace("ή", "η").replace("ί", "ι")
            .replace("ό", "ο").replace("ύ", "υ")
            .replace("ώ", "ω")

        val matches = mutableListOf<String>()

        for (keyword in specialKeywords) {
            if (keyword.lowercase() in titleLower) {
                matches.add(keyword)
                return matches
            }
        }

        for (clientName in clientNames) {
            if (clientName.isBlank()) continue

            val clientLower = clientName.lowercase()
                .replace("ά", "α").replace("έ", "ε")
                .replace("ή", "η").replace("ί", "ι")
                .replace("ό", "ο").replace("ύ", "υ")
                .replace("ώ", "ω")

            val nameParts = clientLower.split(" ").filter { it.isNotBlank() }

            if (clientLower in titleLower) {
                matches.add(clientName)
                continue
            }

            if (nameParts.size < 2) {
                if (nameParts.first() in titleLower) {
                    matches.add(clientName)
                }
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
            if (firstName.length > 3) {
                val regex = "\\b${Regex.escape(firstName)}\\b".toRegex()
                if (regex.find(titleLower) != null) {
                    matches.add(clientName)
                    continue
                }
            }

            if ("-" in clientName) {
                val parts = clientName.split("-").map { it.trim().lowercase() }
                for (part in parts) {
                    if (part in titleLower) {
                        matches.add(clientName)
                        break
                    }
                }
            }
        }

        return matches
    }

    fun getCalendarList(): List<Map<String, Any>> {
        if (!ensureInitialized()) {
            return emptyList()
        }

        return try {
            val calendarList = service!!.calendarList().list().execute()
            calendarList.items?.map { calendar ->
                mapOf(
                    "id" to (calendar.id ?: ""),
                    "summary" to (calendar.summary ?: ""),
                    "primary" to (calendar.primary ?: false),
                    "accessRole" to (calendar.accessRole ?: "")
                )
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error("❌ Error fetching calendar list: ${e.message}", e)
            emptyList()
        }
    }

    private fun isSupervision(summary: String): Boolean {
        val normalized = normalizeGreekText(summary)
        return normalized == "εποπτεια"
    }

    private fun normalizeGreekText(text: String): String {
        return text.lowercase().trim()
            .replace("ά", "α")
            .replace("έ", "ε")
            .replace("ή", "η")
            .replace("ί", "ι")
            .replace("ό", "ο")
            .replace("ύ", "υ")
            .replace("ώ", "ω")
            .replace("ΐ", "ι")
            .replace("ΰ", "υ")
    }
}