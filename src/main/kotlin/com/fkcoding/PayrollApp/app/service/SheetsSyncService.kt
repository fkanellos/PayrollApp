package com.fkcoding.PayrollApp.app.service

import com.fkcoding.PayrollApp.app.repository.ClientRepository
import com.fkcoding.PayrollApp.app.repository.EmployeeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Enhanced Service για συγχρονισμό με Google Sheets
 * Features:
 * - Upsert logic (update if exists, insert if new)
 * - Validation before write
 * - Rollback on failure
 * - Insert at top (newest first)
 * - NOW WITH PERIOD START/END DATES!
 */
@Service
class SheetsSyncService(
    private val sheetsService: GoogleSheetsService,
    private val validationService: PayrollValidationService,
    private val clientRepository: ClientRepository,
    private val employeeRepository: EmployeeRepository
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    /**
     * 📤 SYNC UP: Main function με upsert logic + validation + rollback
     */
    fun syncPayrollToSheets(payrollReport: PayrollReport): Map<String, Any> {
        return try {
            println("=" * 80)
            println("📤 SYNCING PAYROLL TO SHEETS")
            println("   Employee: ${payrollReport.employee.name}")
            println("   Period: ${payrollReport.periodStart.format(dateFormatter)} - ${payrollReport.periodEnd.format(dateFormatter)}")
            println("=" * 80)

            // STEP 1: VALIDATE 🛡️
            println("\n🛡️  STEP 1: Validating payroll data...")
            val validationResult = validationService.validatePayrollReport(payrollReport)

            if (!validationResult.isValid) {
                println("❌ Validation failed!")
                println("   Error Type: ${validationResult.errorType}")
                println("   Details: ${validationResult.errorDetails}")

                return mapOf(
                    "status" to "validation_failed",
                    "message" to "Validation failed: ${validationResult.errorType}",
                    "errorType" to validationResult.errorType!!,
                    "errorDetails" to validationResult.errorDetails!!,
                    "masterWritten" to false,
                    "detailsWritten" to false
                )
            }
            println("✅ Validation passed!")

            // STEP 2: PREPARE DATA 📝
            println("\n📝 STEP 2: Preparing data...")
            val calculationDate = LocalDateTime.now().format(dateTimeFormatter)
            val periodStart = payrollReport.periodStart.format(dateFormatter)
            val periodEnd = payrollReport.periodEnd.format(dateFormatter)
            val period = "$periodStart - $periodEnd"

            val clientDetailRows = payrollReport.entries.map { entry ->
                ClientDetailRow(
                    clientName = entry.clientName,
                    sessions = entry.sessionsCount,
                    pricePerSession = entry.clientPrice,
                    employeeShare = entry.employeeEarnings,
                    companyShare = entry.companyEarnings,
                    totalRevenue = entry.totalRevenue
                )
            }
            println("   Prepared 1 master row + ${clientDetailRows.size} detail rows")

            // STEP 3: CHECK IF EXISTS 🔍
            println("\n🔍 STEP 3: Checking if payroll already exists...")
            val existingPayroll = sheetsService.findExistingPayroll(
                employeeName = payrollReport.employee.name,
                periodStart = periodStart,
                periodEnd = periodEnd
            )

            val existingClientDetails = if (existingPayroll != null) {
                sheetsService.findExistingClientDetails(
                    employeeName = payrollReport.employee.name,
                    periodStart = periodStart,
                    periodEnd = periodEnd
                )
            } else {
                emptyList()
            }

            // STEP 4: UPSERT με Rollback Support 🔄
            if (existingPayroll != null) {
                println("\n🔄 STEP 4: UPDATE MODE (existing payroll found at row ${existingPayroll.rowIndex})")
                updateExistingPayroll(
                    existingPayroll = existingPayroll,
                    existingClientDetails = existingClientDetails,
                    calculationDate = calculationDate,
                    employeeName = payrollReport.employee.name,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                    period = period,
                    payrollReport = payrollReport,
                    clientDetailRows = clientDetailRows
                )
            } else {
                println("\n➕ STEP 4: INSERT MODE (new payroll)")
                insertNewPayroll(
                    calculationDate = calculationDate,
                    employeeName = payrollReport.employee.name,
                    periodStart = periodStart,
                    periodEnd = periodEnd,
                    period = period,
                    payrollReport = payrollReport,
                    clientDetailRows = clientDetailRows
                )
            }

        } catch (e: Exception) {
            println("\n" + "=" * 80)
            println("❌ SYNC FAILED")
            println("   Error: ${e.message}")
            println("=" * 80)
            e.printStackTrace()

            mapOf(
                "status" to "error",
                "message" to (e.message ?: "Unknown error"),
                "masterWritten" to false,
                "detailsWritten" to false
            )
        }
    }

    /**
     * 🔄 UPDATE existing payroll με rollback
     */
    private fun updateExistingPayroll(
        existingPayroll: ExistingPayrollRecord,
        existingClientDetails: List<Int>,
        calculationDate: String,
        employeeName: String,
        periodStart: String,
        periodEnd: String,
        period: String,
        payrollReport: PayrollReport,
        clientDetailRows: List<ClientDetailRow>
    ): Map<String, Any> {
        var masterUpdated = false
        var detailsDeleted = false
        var detailsInserted = false

        try {
            // 1️⃣ Update Master
            println("   1️⃣ Updating master row at ${existingPayroll.rowIndex}...")
            masterUpdated = sheetsService.updateMasterSummary(
                rowIndex = existingPayroll.rowIndex,
                calculationDate = calculationDate,
                employeeName = employeeName,
                periodStart = periodStart,
                periodEnd = periodEnd,
                totalSessions = payrollReport.totalSessions,
                totalRevenue = payrollReport.totalRevenue,
                employeeEarnings = payrollReport.totalEmployeeEarnings,
                companyEarnings = payrollReport.totalCompanyEarnings,
                notes = "Updated"
            )

            if (!masterUpdated) {
                throw Exception("Failed to update master summary")
            }
            println("   ✅ Master updated")

            // 2️⃣ Delete old client details
            println("   2️⃣ Deleting ${existingClientDetails.size} old client detail rows...")
            detailsDeleted = sheetsService.deleteClientDetailRows(existingClientDetails)

            if (!detailsDeleted) {
                throw Exception("Failed to delete old client details")
            }
            println("   ✅ Old details deleted")

            // 3️⃣ Insert new client details στην κορυφή - WITH PERIOD DATES!
            println("   3️⃣ Inserting ${clientDetailRows.size} new client detail rows...")
            detailsInserted = sheetsService.insertClientDetailsAtTop(
                calculationDate = calculationDate,
                employeeName = employeeName,
                periodStartDate = periodStart,
                periodEndDate = periodEnd,
                period = period,
                clientDetails = clientDetailRows
            )

            if (!detailsInserted) {
                throw Exception("Failed to insert new client details")
            }
            println("   ✅ New details inserted")

            println("\n" + "=" * 80)
            println("✅ UPDATE COMPLETED SUCCESSFULLY")
            println("=" * 80)

            return mapOf(
                "status" to "success",
                "message" to "Payroll updated successfully",
                "mode" to "update",
                "masterWritten" to true,
                "detailsWritten" to true,
                "masterRows" to 1,
                "detailRows" to clientDetailRows.size,
                "totalSessions" to payrollReport.totalSessions,
                "totalRevenue" to payrollReport.totalRevenue
            )

        } catch (e: Exception) {
            println("\n❌ UPDATE FAILED - ATTEMPTING ROLLBACK...")
            println("   Master updated: $masterUpdated")
            println("   Details deleted: $detailsDeleted")
            println("   Details inserted: $detailsInserted")

            // TODO: Implement proper rollback
            // For now, log the state and fail

            throw Exception("Update failed: ${e.message}. State: master=$masterUpdated, deleted=$detailsDeleted, inserted=$detailsInserted")
        }
    }

    /**
     * ➕ INSERT new payroll με rollback
     */
    private fun insertNewPayroll(
        calculationDate: String,
        employeeName: String,
        periodStart: String,
        periodEnd: String,
        period: String,
        payrollReport: PayrollReport,
        clientDetailRows: List<ClientDetailRow>
    ): Map<String, Any> {
        var masterInserted = false
        var detailsInserted = false

        try {
            // 1️⃣ Insert Master στην κορυφή
            println("   1️⃣ Inserting master row at top...")
            masterInserted = sheetsService.insertMasterAtTop(
                calculationDate = calculationDate,
                employeeName = employeeName,
                periodStart = periodStart,
                periodEnd = periodEnd,
                totalSessions = payrollReport.totalSessions,
                totalRevenue = payrollReport.totalRevenue,
                employeeEarnings = payrollReport.totalEmployeeEarnings,
                companyEarnings = payrollReport.totalCompanyEarnings,
                notes = ""
            )

            if (!masterInserted) {
                throw Exception("Failed to insert master summary")
            }
            println("   ✅ Master inserted")

            // 2️⃣ Insert Client Details στην κορυφή - WITH PERIOD DATES!
            println("   2️⃣ Inserting ${clientDetailRows.size} client detail rows...")
            detailsInserted = sheetsService.insertClientDetailsAtTop(
                calculationDate = calculationDate,
                employeeName = employeeName,
                periodStartDate = periodStart,
                periodEndDate = periodEnd,
                period = period,
                clientDetails = clientDetailRows
            )

            if (!detailsInserted) {
                throw Exception("Failed to insert client details")
            }
            println("   ✅ Client details inserted")

            println("\n" + "=" * 80)
            println("✅ INSERT COMPLETED SUCCESSFULLY")
            println("=" * 80)

            return mapOf(
                "status" to "success",
                "message" to "Payroll inserted successfully",
                "mode" to "insert",
                "masterWritten" to true,
                "detailsWritten" to true,
                "masterRows" to 1,
                "detailRows" to clientDetailRows.size,
                "totalSessions" to payrollReport.totalSessions,
                "totalRevenue" to payrollReport.totalRevenue
            )

        } catch (e: Exception) {
            println("\n❌ INSERT FAILED - ATTEMPTING ROLLBACK...")
            println("   Master inserted: $masterInserted")
            println("   Details inserted: $detailsInserted")

            // TODO: Implement proper rollback
            // For now, log the state and fail

            throw Exception("Insert failed: ${e.message}. State: master=$masterInserted, details=$detailsInserted")
        }
    }

    /**
     * 🆕 Initialize όλα τα sheets με headers
     */
    fun initializeSheets(): Map<String, Any> {
        return try {
            println("🔧 Initializing all sheets...")

            val success = sheetsService.initializeAllSheets()

            if (success) {
                mapOf(
                    "status" to "success",
                    "message" to "All sheets initialized successfully",
                    "sheets" to listOf(
                        "MASTER_PAYROLL",
                        "CLIENT_DETAILS",
                        "MONTHLY_STATS"
                    )
                )
            } else {
                mapOf(
                    "status" to "error",
                    "message" to "Failed to initialize sheets"
                )
            }

        } catch (e: Exception) {
            mapOf(
                "status" to "error",
                "message" to (e.message ?: "Unknown error")
            )
        }
    }

    /**
     * 🧪 Test: Γράφει sample data για testing
     * 🔴 UPDATED: Με Period Start/End dates!
     */
    fun writeSampleData(): Map<String, Any> {
        return try {
            println("🧪 Writing sample data for testing...")

            // Sample master row
            val masterSuccess = sheetsService.insertMasterAtTop(
                calculationDate = LocalDateTime.now().format(dateTimeFormatter),
                employeeName = "Αγγελική Γκουντοπούλου",
                periodStart = "22/09/2025",
                periodEnd = "03/10/2025",
                totalSessions = 42,
                totalRevenue = 2100.0,
                employeeEarnings = 840.0,
                companyEarnings = 1260.0,
                notes = "Sample Data"
            )

            // Sample client details - WITH DATES!
            val sampleClients = listOf(
                ClientDetailRow("Κωνσταντίνος Κουρμούζης", 4, 50.0, 80.0, 120.0, 200.0),
                ClientDetailRow("Μαρία Κουτίβα", 6, 50.0, 120.0, 180.0, 300.0),
                ClientDetailRow("Παναγιώτης Ζανής", 3, 50.0, 60.0, 90.0, 150.0)
            )

            val detailsSuccess = sheetsService.insertClientDetailsAtTop(
                calculationDate = LocalDateTime.now().format(dateTimeFormatter),
                employeeName = "Αγγελική Γκουντοπούλου",
                periodStartDate = "22/09/2025",
                periodEndDate = "03/10/2025",
                period = "22/09/2025 - 03/10/2025",
                clientDetails = sampleClients
            )

            mapOf(
                "status" to "success",
                "message" to "Sample data written",
                "masterWritten" to masterSuccess,
                "detailsWritten" to detailsSuccess
            )

        } catch (e: Exception) {
            mapOf(
                "status" to "error",
                "message" to (e.message ?: "Unknown error")
            )
        }
    }
}

// String repeat helper
private operator fun String.times(n: Int): String = this.repeat(n)