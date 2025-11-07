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
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.SheetsScopes
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.io.InputStreamReader

/**
 * ✨ SHARED Google Credential Provider
 *
 * Όλα τα Google services (Calendar, Sheets, Drive)
 * χρησιμοποιούν ΤΟ ΙΔΙΟ credential από εδώ!
 *
 * Benefits:
 * - Single token store
 * - Single authorization
 * - All scopes in one place
 * - No confusion!
 */
@Service
class GoogleCredentialProvider {

    companion object {
        private val logger = LoggerFactory.getLogger(GoogleCredentialProvider::class.java)
        private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()

        // ✅ ΟΛΑ τα scopes που χρειαζόμαστε!
        private val ALL_SCOPES = listOf(
            CalendarScopes.CALENDAR_READONLY,
            SheetsScopes.SPREADSHEETS,
            DriveScopes.DRIVE_READONLY,
            "https://www.googleapis.com/auth/drive.file"
        )
    }

    private lateinit var httpTransport: NetHttpTransport
    private lateinit var credential: Credential
    private lateinit var tokensDir: File

    @PostConstruct
    fun initialize() {
        try {
            logger.info("🔐 Initializing Google Credential Provider...")

            // Setup
            httpTransport = GoogleNetHttpTransport.newTrustedTransport()
            tokensDir = File(System.getProperty("user.home"), ".credentials/payroll-app")

            logger.info("📁 Tokens directory: ${tokensDir.absolutePath}")

            if (!tokensDir.exists()) {
                logger.info("📁 Creating tokens directory")
                tokensDir.mkdirs()
            }

            // Load credential
            credential = loadCredential()

            // Check token validity
            val expiresIn = (credential.expiresInSeconds ?: 0)
            logger.info("✅ Token is valid (expires in ${expiresIn}s)")

            if (credential.refreshToken != null) {
                logger.info("✅ Refresh token found! Credentials will persist.")
            } else {
                logger.warn("⚠️  No refresh token! You'll need to re-authorize after token expires.")
            }

            logger.info("✅ Google Credential Provider initialized successfully")
            logger.info("📋 Scopes: ${ALL_SCOPES.joinToString(", ")}")

        } catch (e: Exception) {
            logger.error("❌ Failed to initialize credential provider: ${e.message}", e)
            throw e
        }
    }

    /**
     * 🔑 Get the shared credential
     * Όλα τα services καλούν αυτό!
     */
    fun getCredential(): Credential {
        // Check if token needs refresh
        if (credential.expiresInSeconds != null && credential.expiresInSeconds!! < 60) {
            logger.info("🔄 Token expiring soon, refreshing...")
            try {
                credential.refreshToken()
                logger.info("✅ Token refreshed successfully")
            } catch (e: Exception) {
                logger.error("❌ Failed to refresh token: ${e.message}")
            }
        }

        return credential
    }

    /**
     * 🌐 Get HTTP transport
     */
    fun getHttpTransport(): NetHttpTransport {
        return httpTransport
    }

    /**
     * 📝 Get JSON factory
     */
    fun getJsonFactory(): JsonFactory {
        return JSON_FACTORY
    }

    /**
     * 🔐 Load or create credential
     */
    private fun loadCredential(): Credential {
        logger.info("🔐 Loading credentials...")

        // Load client secrets
        val resource = javaClass.getResourceAsStream("/data/credentials.json")
            ?: throw RuntimeException("Credentials file not found at /data/credentials.json")

        val clientSecrets = GoogleClientSecrets.load(
            JSON_FACTORY,
            InputStreamReader(resource)
        )

        // Build flow
        val flow = GoogleAuthorizationCodeFlow.Builder(
            httpTransport,
            JSON_FACTORY,
            clientSecrets,
            ALL_SCOPES  // ✅ All scopes!
        )
            .setDataStoreFactory(FileDataStoreFactory(tokensDir))
            .setAccessType("offline")
            .build()

        // Try to load existing credential
        val existingCredential = flow.loadCredential("user")

        if (existingCredential != null) {
            logger.info("✅ Loaded existing credentials")
            return existingCredential
        }

        // No credential found, need to authorize
        logger.info("❗ No credentials found. Starting authorization flow...")
        logger.info("📱 Browser window will open for authorization...")

        val receiver = LocalServerReceiver.Builder()
            .setPort(8889)
            .build()

        val credential = AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

        logger.info("✅ Authorization completed!")
        return credential
    }

    /**
     * 🗑️ Delete credentials (for re-authorization)
     */
    fun deleteCredentials() {
        try {
            tokensDir.listFiles()?.forEach { it.delete() }
            logger.info("✅ Credentials deleted. Re-authorization required.")
        } catch (e: Exception) {
            logger.error("❌ Failed to delete credentials: ${e.message}")
        }
    }

    /**
     * ℹ️ Get token info
     */
    fun getTokenInfo(): Map<String, Any> {
        return mapOf(
            "hasRefreshToken" to (credential.refreshToken != null),
            "expiresIn" to (credential.expiresInSeconds ?: 0),
            "scopes" to ALL_SCOPES,
            "tokensDir" to tokensDir.absolutePath
        )
    }
}