package com.fkcoding.PayrollApp.app.service

import com.fkcoding.PayrollApp.app.entity.Client
import com.fkcoding.PayrollApp.app.entity.Employee
import com.google.api.client.http.GenericUrl
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 📦 Excel Data Service
 * Uses SHARED credentials from GoogleCredentialProvider
 * Downloads Excel from Google Drive export URL
 */
@Service
class ExcelDataService(
    private val credentialProvider: GoogleCredentialProvider  // ✅ Shared provider!
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ExcelDataService::class.java)
    }

    @Value("\${google.drive.excel.file.id:}")
    private lateinit var excelFileId: String

    private var employees: List<Employee> = emptyList()
    private var clients: List<Client> = emptyList()
    private var lastLoadTime: Long = 0

    @PostConstruct
    fun initialize() {
        if (excelFileId.isBlank()) {
            logger.warn("⚠️ Google Drive Excel file ID not configured")
            logger.warn("⚠️ Set 'google.drive.excel.file.id' in application.properties")
            logger.warn("⚠️ Using empty data.")
        } else {
            try {
                loadDataFromExcel()
                logger.info("✅ Excel data loaded: ${employees.size} employees, ${clients.size} clients")
            } catch (e: Exception) {
                logger.error("❌ Failed to load Excel: ${e.message}", e)
                logger.warn("⚠️ Using empty data.")
            }
        }
    }

    /**
     * 📥 Load Excel from Drive export URL
     * Uses shared credentials!
     */
    fun loadDataFromExcel() {
        if (excelFileId.isBlank()) {
            logger.warn("No Excel file ID configured, skipping load")
            return
        }

        try {
            logger.info("📥 Fetching Excel from Google Drive (ID: $excelFileId)")

            // ✅ Use shared HTTP transport & credentials!
            val httpTransport = credentialProvider.getHttpTransport()
            val credential = credentialProvider.getCredential()

            // ✅ Direct export URL (fast!)
            val exportUrl = "https://docs.google.com/spreadsheets/d/$excelFileId/export?format=xlsx"
            logger.info("🌐 Using export URL")

            // Create request
            val httpRequest = httpTransport.createRequestFactory(credential)
                .buildGetRequest(GenericUrl(exportUrl))

            // Set timeouts
            httpRequest.connectTimeout = 30000  // 30s
            httpRequest.readTimeout = 30000     // 30s

            // Execute
            logger.info("⏱️ Downloading Excel...")
            val response = httpRequest.execute()

            val outputStream = ByteArrayOutputStream()
            response.content.use { input ->
                input.copyTo(outputStream)
            }

            val excelBytes = outputStream.toByteArray()
            logger.info("✅ Downloaded ${excelBytes.size} bytes")

            // Parse
            parseExcel(excelBytes)
            lastLoadTime = System.currentTimeMillis()

        } catch (e: java.net.SocketTimeoutException) {
            logger.error("❌ Timeout downloading Excel")
            logger.error("   Try: Check internet, disable VPN, or increase timeout")
            throw e
        } catch (e: Exception) {
            logger.error("❌ Error loading Excel: ${e.message}", e)
            throw e
        }
    }

    /**
     * 📊 Parse Excel file
     */
    private fun parseExcel(excelBytes: ByteArray) {
        val workbook = WorkbookFactory.create(ByteArrayInputStream(excelBytes))

        val employeesList = mutableListOf<Employee>()
        val clientsList = mutableListOf<Client>()

        try {
            // Parse EMPLOYEES sheet
            val employeesSheet = workbook.getSheet("EMPLOYEES")
            if (employeesSheet != null) {
                logger.info("📊 Parsing EMPLOYEES sheet...")

                for (rowIndex in 1..employeesSheet.lastRowNum) {
                    val row = employeesSheet.getRow(rowIndex) ?: continue

                    try {
                        // Column A (0): Name
                        // Column B (1): Email
                        // Column C (2): Calendar ID
                        // Column D (3): Client Sheet Name
                        // Column E (4): Supervision (€)
                        // Column F (5): Clients (count - ignore)

                        val name = row.getCell(0)?.stringCellValue ?: continue
                        val email = row.getCell(1)?.stringCellValue ?: ""
                        val calendarId = row.getCell(2)?.stringCellValue ?: ""
                        val sheetName = row.getCell(3)?.stringCellValue ?: name
                        val supervisionPrice = row.getCell(4)?.numericCellValue ?: 0.0

                        val employee = Employee(
                            id = name.hashCode().toString(),
                            name = name,
                            email = email,
                            calendarId = calendarId,
                            sheetName = sheetName,
                            supervisionPrice = supervisionPrice
                        )

                        employeesList.add(employee)
                        logger.debug("  ✓ Employee: $name")

                    } catch (e: Exception) {
                        logger.warn("⚠️ Error parsing employee row $rowIndex: ${e.message}")
                    }
                }
            }

            // Parse client sheets
            for (employee in employeesList) {
                val clientSheet = workbook.getSheet(employee.sheetName)
                if (clientSheet != null) {
                    logger.info("📊 Parsing clients for ${employee.name}...")

                    for (rowIndex in 1..clientSheet.lastRowNum) {
                        val row = clientSheet.getRow(rowIndex) ?: continue

                        try {
                            val clientName = row.getCell(0)?.stringCellValue?.trim() ?: continue
                            if (clientName.isBlank()) continue

                            val price = row.getCell(1)?.numericCellValue ?: 0.0
                            val employeePrice = row.getCell(2)?.numericCellValue ?: 0.0
                            val companyPrice = row.getCell(3)?.numericCellValue ?: 0.0
                            val status = row.getCell(4)?.stringCellValue ?: ""

                            // ✅ Generate proper Long ID by combining hashes
                            val employeeHash = employee.id.hashCode().toLong()
                            val clientHash = clientName.hashCode().toLong()
                            val uniqueId = (employeeHash shl 32) or (clientHash and 0xFFFFFFFFL)

                            val client = Client(
                                id = uniqueId,
                                name = clientName,
                                price = price,
                                employeePrice = employeePrice,
                                companyPrice = companyPrice,
                                employeeId = employee.id,
                                pendingPayment = status.contains("Σταμάτησε", ignoreCase = true)
                            )

                            clientsList.add(client)

                        } catch (e: Exception) {
                            logger.warn("⚠️ Error parsing client row $rowIndex: ${e.message}")
                        }
                    }
                }
            }

            employees = employeesList
            clients = clientsList

            logger.info("✅ Parsed ${employees.size} employees and ${clients.size} clients")

        } finally {
            workbook.close()
        }
    }

    fun getAllEmployees(): List<Employee> = employees
    fun getEmployeeById(id: String): Employee? = employees.find { it.id == id }
    fun getAllClients(): List<Client> = clients
    fun getClientsByEmployeeId(employeeId: String): List<Client> = clients.filter { it.employeeId == employeeId }
    fun refresh() = loadDataFromExcel()
    fun getLastLoadTime(): Long = lastLoadTime
}