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
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.*
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service
import java.io.File
import java.io.InputStreamReader

/**
 * Google Sheets Service - 3 Tab Structure
 * 1. MASTER_PAYROLL - Summary rows
 * 2. CLIENT_DETAILS - Detailed breakdown
 * 3. MONTHLY_STATS - Auto-calculated formulas
 */
@Service
class GoogleSheetsService(
    private val resourceLoader: ResourceLoader
) {

    companion object {
        private const val APPLICATION_NAME = "Payroll System"
        private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()
        private const val TOKENS_DIRECTORY_PATH = "tokens"
        private val SCOPES = listOf(SheetsScopes.SPREADSHEETS)
    }

    @Value("\${google.sheets.credentials.path:classpath:data/credentials.json}")
    private lateinit var credentialsFilePath: String

    @Value("\${google.sheets.spreadsheet.id}")
    private lateinit var spreadsheetId: String

    @Value("\${google.sheets.master.sheet:MASTER_PAYROLL}")
    private lateinit var masterSheetName: String

    @Value("\${google.sheets.details.sheet:CLIENT_DETAILS}")
    private lateinit var detailsSheetName: String

    @Value("\${google.sheets.stats.sheet:MONTHLY_STATS}")
    private lateinit var statsSheetName: String

    private lateinit var service: Sheets

    @PostConstruct
    fun initialize() {
        try {
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
            service = Sheets.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                .setApplicationName(APPLICATION_NAME)
                .build()
            println("✅ Google Sheets service initialized successfully")
            println("📊 Spreadsheet ID: $spreadsheetId")
            println("📋 Master Sheet: $masterSheetName")
            println("📊 Details Sheet: $detailsSheetName")
            println("📈 Stats Sheet: $statsSheetName")
        } catch (e: Exception) {
            println("❌ Failed to initialize Google Sheets service: ${e.message}")
            throw e
        }
    }

    private fun getCredentials(httpTransport: NetHttpTransport): Credential {
        try {
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
                .setDataStoreFactory(FileDataStoreFactory(File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build()

            val receiver = LocalServerReceiver.Builder().setPort(8888).build()
            return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

        } catch (e: Exception) {
            throw RuntimeException("Failed to load credentials: ${e.message}", e)
        }
    }

    /**
     * 📋 MASTER_PAYROLL: Γράφει 1 summary row per calculation
     */
    fun writeMasterSummary(
        calculationDate: String,
        employeeName: String,
        periodStart: String,
        periodEnd: String,
        totalSessions: Int,
        totalRevenue: Double,
        employeeEarnings: Double,
        companyEarnings: Double,
        notes: String = ""
    ): Boolean {
        return try {
            println("📝 Writing master summary for $employeeName...")

            val values = listOf(
                listOf(
                    calculationDate,
                    employeeName,
                    periodStart,
                    periodEnd,
                    totalSessions,
                    totalRevenue,
                    employeeEarnings,
                    companyEarnings,
                    notes
                )
            )

            val body = ValueRange().setValues(values)
            val range = "$masterSheetName!A:I"

            service.spreadsheets().values()
                .append(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .execute()

            println("✅ Master summary written successfully")
            true

        } catch (e: Exception) {
            println("❌ Error writing master summary: ${e.message}")
            false
        }
    }

    /**
     * 📊 CLIENT_DETAILS: Γράφει N rows (1 per client)
     */
    fun writeClientDetails(
        calculationDate: String,
        employeeName: String,
        period: String,
        clientDetails: List<ClientDetailRow>
    ): Boolean {
        return try {
            println("📝 Writing ${clientDetails.size} client detail rows...")

            val values = clientDetails.map { client ->
                listOf(
                    calculationDate,
                    employeeName,
                    period,
                    client.clientName,
                    client.sessions,
                    client.pricePerSession,
                    client.employeeShare,
                    client.companyShare,
                    client.totalRevenue
                )
            }

            val body = ValueRange().setValues(values)
            val range = "$detailsSheetName!A:I"

            service.spreadsheets().values()
                .append(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .execute()

            println("✅ Client details written successfully")
            true

        } catch (e: Exception) {
            println("❌ Error writing client details: ${e.message}")
            false
        }
    }

    /**
     * 🆕 Create all 3 sheets με headers
     */
    fun initializeAllSheets(): Boolean {
        return try {
            createMasterSheet()
            createDetailsSheet()
            createStatsSheet()
            true
        } catch (e: Exception) {
            println("❌ Error initializing sheets: ${e.message}")
            false
        }
    }

    private fun createMasterSheet(): Boolean {
        return try {
            if (getSheetIdByName(masterSheetName) != null) {
                println("ℹ️  Sheet '$masterSheetName' already exists")
                return true
            }

            // Create sheet
            val addSheetRequest = AddSheetRequest().apply {
                properties = SheetProperties().apply {
                    title = masterSheetName
                }
            }

            val batchUpdateRequest = BatchUpdateSpreadsheetRequest().apply {
                requests = listOf(Request().setAddSheet(addSheetRequest))
            }

            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest).execute()
            println("✅ Created sheet: $masterSheetName")

            // Add headers
            val headers = listOf(
                listOf(
                    "Calculation Date",
                    "Employee Name",
                    "Period Start",
                    "Period End",
                    "Total Sessions",
                    "Total Revenue",
                    "Employee Earnings",
                    "Company Earnings",
                    "Notes"
                )
            )

            val body = ValueRange().setValues(headers)
            service.spreadsheets().values()
                .update(spreadsheetId, "$masterSheetName!A1:I1", body)
                .setValueInputOption("USER_ENTERED")
                .execute()

            // Format headers
            formatHeaders(masterSheetName)
            true

        } catch (e: Exception) {
            println("❌ Error creating master sheet: ${e.message}")
            false
        }
    }

    private fun createDetailsSheet(): Boolean {
        return try {
            if (getSheetIdByName(detailsSheetName) != null) {
                println("ℹ️  Sheet '$detailsSheetName' already exists")
                return true
            }

            // Create sheet
            val addSheetRequest = AddSheetRequest().apply {
                properties = SheetProperties().apply {
                    title = detailsSheetName
                }
            }

            val batchUpdateRequest = BatchUpdateSpreadsheetRequest().apply {
                requests = listOf(Request().setAddSheet(addSheetRequest))
            }

            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest).execute()
            println("✅ Created sheet: $detailsSheetName")

            // Add headers
            val headers = listOf(
                listOf(
                    "Calculation Date",
                    "Employee Name",
                    "Period",
                    "Client Name",
                    "Sessions",
                    "Price/Session",
                    "Employee Share",
                    "Company Share",
                    "Total Revenue"
                )
            )

            val body = ValueRange().setValues(headers)
            service.spreadsheets().values()
                .update(spreadsheetId, "$detailsSheetName!A1:I1", body)
                .setValueInputOption("USER_ENTERED")
                .execute()

            // Format headers
            formatHeaders(detailsSheetName)
            true

        } catch (e: Exception) {
            println("❌ Error creating details sheet: ${e.message}")
            false
        }
    }

    private fun createStatsSheet(): Boolean {
        return try {
            if (getSheetIdByName(statsSheetName) != null) {
                println("ℹ️  Sheet '$statsSheetName' already exists")
                return true
            }

            // Create sheet
            val addSheetRequest = AddSheetRequest().apply {
                properties = SheetProperties().apply {
                    title = statsSheetName
                }
            }

            val batchUpdateRequest = BatchUpdateSpreadsheetRequest().apply {
                requests = listOf(Request().setAddSheet(addSheetRequest))
            }

            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest).execute()
            println("✅ Created sheet: $statsSheetName")

            // Add title
            val title = listOf(listOf("📊 MONTHLY STATISTICS - Auto-Generated"))
            val body = ValueRange().setValues(title)
            service.spreadsheets().values()
                .update(spreadsheetId, "$statsSheetName!A1", body)
                .setValueInputOption("USER_ENTERED")
                .execute()

            // Format title
            val sheetId = getSheetIdByName(statsSheetName)!!
            val requests = listOf(
                Request().setRepeatCell(
                    RepeatCellRequest().apply {
                        range = GridRange().apply {
                            this.sheetId = sheetId
                            startRowIndex = 0
                            endRowIndex = 1
                        }
                        cell = CellData().apply {
                            userEnteredFormat = CellFormat().apply {
                                textFormat = TextFormat().apply {
                                    bold = true
                                    fontSize = 14
                                }
                                backgroundColor = Color().apply {
                                    red = 0.2f
                                    green = 0.5f
                                    blue = 0.9f
                                }
                            }
                        }
                        fields = "userEnteredFormat(textFormat,backgroundColor)"
                    }
                )
            )

            val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(requests)
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()

            true

        } catch (e: Exception) {
            println("❌ Error creating stats sheet: ${e.message}")
            false
        }
    }

    private fun formatHeaders(sheetName: String) {
        try {
            val sheetId = getSheetIdByName(sheetName) ?: return

            val requests = listOf(
                Request().setRepeatCell(
                    RepeatCellRequest().apply {
                        range = GridRange().apply {
                            this.sheetId = sheetId
                            startRowIndex = 0
                            endRowIndex = 1
                        }
                        cell = CellData().apply {
                            userEnteredFormat = CellFormat().apply {
                                textFormat = TextFormat().apply {
                                    bold = true
                                    fontSize = 11
                                }
                                backgroundColor = Color().apply {
                                    red = 0.85f
                                    green = 0.85f
                                    blue = 0.85f
                                }
                            }
                        }
                        fields = "userEnteredFormat(textFormat,backgroundColor)"
                    }
                )
            )

            val batchUpdateRequest = BatchUpdateSpreadsheetRequest().setRequests(requests)
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest).execute()

        } catch (e: Exception) {
            println("⚠️  Error formatting headers: ${e.message}")
        }
    }

    fun getSheetIdByName(sheetName: String): Int? {
        return try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
            val sheet = spreadsheet.sheets.find {
                it.properties.title == sheetName
            }
            sheet?.properties?.sheetId
        } catch (e: Exception) {
            println("❌ Error getting sheet ID: ${e.message}")
            null
        }
    }

    fun testConnection(): Map<String, Any> {
        return try {
            val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()

            mapOf(
                "status" to "success",
                "spreadsheetTitle" to (spreadsheet.properties.title ?: "Unknown"),
                "spreadsheetId" to spreadsheetId,
                "sheets" to (spreadsheet.sheets?.map { it.properties.title } ?: emptyList<String>())
            )
        } catch (e: Exception) {
            mapOf(
                "status" to "error",
                "message" to (e.message ?: "Unknown error")
            )
        }
    }

    /**
     * 🔍 Βρίσκει αν υπάρχει ήδη payroll record για αυτό το Employee + Period
     * Unique key: Employee Name + Period Start + Period End
     */
    fun findExistingPayroll(
        employeeName: String,
        periodStart: String,
        periodEnd: String
    ): ExistingPayrollRecord? {
        return try {
            println("🔍 Searching for existing payroll: $employeeName ($periodStart - $periodEnd)")

            // Read all data from MASTER_PAYROLL
            val range = "$masterSheetName!A2:I" // Skip header row
            val response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute()

            val values = response.getValues() ?: return null

            // Search for matching row
            values.forEachIndexed { index, row ->
                if (row.size >= 4) {
                    // Columns: [0]=CalcDate, [1]=Employee, [2]=PeriodStart, [3]=PeriodEnd
                    val rowEmployee = row[1].toString()
                    val rowPeriodStart = row[2].toString()
                    val rowPeriodEnd = row[3].toString()

                    if (rowEmployee == employeeName &&
                        rowPeriodStart == periodStart &&
                        rowPeriodEnd == periodEnd) {

                        println("✅ Found existing payroll at row ${index + 2}")
                        return ExistingPayrollRecord(
                            rowIndex = index + 2, // +2 because: 0-indexed + skip header
                            masterSheetRange = "$masterSheetName!A${index + 2}:I${index + 2}",
                            existingData = row
                        )
                    }
                }
            }

            println("ℹ️  No existing payroll found")
            null

        } catch (e: Exception) {
            println("❌ Error searching for existing payroll: ${e.message}")
            null
        }
    }

    /**
     * 🔍 Βρίσκει client detail rows για συγκεκριμένο payroll
     */
    fun findExistingClientDetails(
        employeeName: String,
        period: String
    ): List<Int> {
        return try {
            println("🔍 Searching for existing client details: $employeeName ($period)")

            val range = "$detailsSheetName!A2:I"
            val response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute()

            val values = response.getValues() ?: return emptyList()

            val matchingRows = mutableListOf<Int>()

            values.forEachIndexed { index, row ->
                if (row.size >= 3) {
                    // Columns: [0]=CalcDate, [1]=Employee, [2]=Period
                    val rowEmployee = row[1].toString()
                    val rowPeriod = row[2].toString()

                    if (rowEmployee == employeeName && rowPeriod == period) {
                        matchingRows.add(index + 2) // +2 for header + 0-index
                    }
                }
            }

            println("✅ Found ${matchingRows.size} existing client detail rows")
            matchingRows

        } catch (e: Exception) {
            println("❌ Error searching client details: ${e.message}")
            emptyList()
        }
    }

    /**
     * ✏️ UPDATE existing master payroll row
     */
    fun updateMasterSummary(
        rowIndex: Int,
        calculationDate: String,
        employeeName: String,
        periodStart: String,
        periodEnd: String,
        totalSessions: Int,
        totalRevenue: Double,
        employeeEarnings: Double,
        companyEarnings: Double,
        notes: String = "Updated"
    ): Boolean {
        return try {
            println("✏️ Updating master summary at row $rowIndex...")

            val values = listOf(
                listOf(
                    calculationDate,
                    employeeName,
                    periodStart,
                    periodEnd,
                    totalSessions,
                    totalRevenue,
                    employeeEarnings,
                    companyEarnings,
                    notes
                )
            )

            val body = ValueRange().setValues(values)
            val range = "$masterSheetName!A$rowIndex:I$rowIndex"

            service.spreadsheets().values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute()

            println("✅ Master summary updated successfully")
            true

        } catch (e: Exception) {
            println("❌ Error updating master summary: ${e.message}")
            false
        }
    }

    /**
     * 🗑️ DELETE existing client detail rows
     */
    fun deleteClientDetailRows(rowIndices: List<Int>): Boolean {
        if (rowIndices.isEmpty()) return true

        return try {
            println("🗑️ Deleting ${rowIndices.size} client detail rows...")

            val sheetId = getSheetIdByName(detailsSheetName) ?: return false

            // Sort descending για να διαγράψουμε από κάτω προς τα πάνω
            // (αλλιώς τα indices αλλάζουν!)
            val sortedIndices = rowIndices.sortedDescending()

            val requests = sortedIndices.map { rowIndex ->
                Request().setDeleteDimension(
                    DeleteDimensionRequest().apply {
                        range = DimensionRange().apply {
                            this.sheetId = sheetId
                            dimension = "ROWS"
                            startIndex = rowIndex - 1 // API is 0-indexed
                            endIndex = rowIndex
                        }
                    }
                )
            }

            val batchUpdateRequest = BatchUpdateSpreadsheetRequest().setRequests(requests)
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest).execute()

            println("✅ Client detail rows deleted successfully")
            true

        } catch (e: Exception) {
            println("❌ Error deleting client detail rows: ${e.message}")
            false
        }
    }

    /**
     * 📤 INSERT στην κορυφή (row 2, αμέσως μετά τα headers)
     */
    fun insertMasterAtTop(
        calculationDate: String,
        employeeName: String,
        periodStart: String,
        periodEnd: String,
        totalSessions: Int,
        totalRevenue: Double,
        employeeEarnings: Double,
        companyEarnings: Double,
        notes: String = ""
    ): Boolean {
        return try {
            println("📤 Inserting master summary at top...")

            val sheetId = getSheetIdByName(masterSheetName) ?: return false

            // 1. Insert blank row at position 1 (μετά τα headers)
            val insertRequest = Request().setInsertDimension(
                InsertDimensionRequest().apply {
                    range = DimensionRange().apply {
                        this.sheetId = sheetId
                        dimension = "ROWS"
                        startIndex = 1 // After header (row 2)
                        endIndex = 2
                    }
                    inheritFromBefore = false
                }
            )

            val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(listOf(insertRequest))
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()

            // 2. Write data to new row
            val values = listOf(
                listOf(
                    calculationDate,
                    employeeName,
                    periodStart,
                    periodEnd,
                    totalSessions,
                    totalRevenue,
                    employeeEarnings,
                    companyEarnings,
                    notes
                )
            )

            val body = ValueRange().setValues(values)
            service.spreadsheets().values()
                .update(spreadsheetId, "$masterSheetName!A2:I2", body)
                .setValueInputOption("USER_ENTERED")
                .execute()

            println("✅ Master summary inserted at top")
            true

        } catch (e: Exception) {
            println("❌ Error inserting master at top: ${e.message}")
            false
        }
    }

    /**
     * 📤 INSERT client details στην κορυφή
     */
    fun insertClientDetailsAtTop(
        calculationDate: String,
        employeeName: String,
        period: String,
        clientDetails: List<ClientDetailRow>
    ): Boolean {
        return try {
            println("📤 Inserting ${clientDetails.size} client detail rows at top...")

            val sheetId = getSheetIdByName(detailsSheetName) ?: return false

            // 1. Insert N blank rows
            val insertRequest = Request().setInsertDimension(
                InsertDimensionRequest().apply {
                    range = DimensionRange().apply {
                        this.sheetId = sheetId
                        dimension = "ROWS"
                        startIndex = 1
                        endIndex = 1 + clientDetails.size
                    }
                    inheritFromBefore = false
                }
            )

            val batchUpdate = BatchUpdateSpreadsheetRequest().setRequests(listOf(insertRequest))
            service.spreadsheets().batchUpdate(spreadsheetId, batchUpdate).execute()

            // 2. Write data
            val values = clientDetails.map { client ->
                listOf(
                    calculationDate,
                    employeeName,
                    period,
                    client.clientName,
                    client.sessions,
                    client.pricePerSession,
                    client.employeeShare,
                    client.companyShare,
                    client.totalRevenue
                )
            }

            val body = ValueRange().setValues(values)
            val range = "$detailsSheetName!A2:I${1 + clientDetails.size}"

            service.spreadsheets().values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute()

            println("✅ Client details inserted at top")
            true

        } catch (e: Exception) {
            println("❌ Error inserting client details at top: ${e.message}")
            false
        }
    }
}

/**
 * Data class για client detail row
 */
data class ClientDetailRow(
    val clientName: String,
    val sessions: Int,
    val pricePerSession: Double,
    val employeeShare: Double,
    val companyShare: Double,
    val totalRevenue: Double
)
/**
 * Data class για client από Sheets
 */
data class SheetClient(
    val name: String,
    val price: Double,
    val employeePrice: Double,
    val companyPrice: Double,
    val pendingPayment: Boolean = false
)

/**
 * Data class για payroll entry που θα γράψουμε στο Sheet
 */
data class PayrollSheetEntry(
    val employeeName: String,
    val period: String,
    val clientName: String,
    val sessions: Int,
    val pricePerSession: Double,
    val totalRevenue: Double,
    val employeeEarnings: Double,
    val companyEarnings: Double,
    val generatedAt: String
)


/**
 * Data class για existing payroll record
 */
data class ExistingPayrollRecord(
    val rowIndex: Int,
    val masterSheetRange: String,
    val existingData: List<Any>
)
