package com.fkcoding.PayrollApp.app.controller

import com.fkcoding.PayrollApp.app.entity.Employee
import com.fkcoding.PayrollApp.app.service.*
import com.fkcoding.PayrollApp.app.repository.EmployeeRepository
import com.fkcoding.PayrollApp.app.repository.ClientRepository
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class PayrollRequest(
    val employeeId: String,
    val startDate: String,
    val endDate: String,
    val syncToSheets: Boolean = false  // Option για να γράψει στο Sheets
)

data class PayrollResponse(
    val employee: EmployeeInfo,
    val period: String,
    val summary: PayrollSummary,
    val clientBreakdown: List<ClientPayrollDetail>,
    val generatedAt: String,
    val syncedToSheets: Boolean = false
)

data class EmployeeInfo(
    val id: String,
    val name: String,
    val email: String
)

data class PayrollSummary(
    val totalSessions: Int,
    val totalRevenue: Double,
    val employeeEarnings: Double,
    val companyEarnings: Double
)

data class ClientPayrollDetail(
    val clientName: String,
    val pricePerSession: Double,
    val employeePricePerSession: Double,
    val companyPricePerSession: Double,
    val sessions: Int,
    val totalRevenue: Double,
    val employeeEarnings: Double,
    val companyEarnings: Double,
    val eventDetails: List<EventDetail>
)

data class EventDetail(
    val date: String,
    val time: String,
    val duration: String,
    val status: String,
    val colorId: String?
)


// 🆕 ADD THIS DATA CLASS
data class PayrollCalculationResponse(
    val id: String,
    val payroll: PayrollResponse
)

@RestController
@RequestMapping("/payroll")
@CrossOrigin(origins = ["http://localhost:3000", "*"])
class PayrollController(
    private val employeeRepository: EmployeeRepository,
    private val clientRepository: ClientRepository,
    private val googleCalendarService: GoogleCalendarService,
    private val payrollService: PayrollCalculationService,
    private val sheetsSyncService: SheetsSyncService,
    private val payrollCacheService: PayrollCacheService,
    private val sheetsService: GoogleSheetsService
) {

    @PostMapping("/calculate")
    fun calculatePayroll(@RequestBody request: PayrollRequest): ResponseEntity<PayrollCalculationResponse> {
        return try {
            println("📊 Calculating payroll for employee: ${request.employeeId}")

            // 1-6: Same as before (employee, dates, clients, events, filtering, calculation)
            val employee = employeeRepository.findById(request.employeeId).orElse(null)
                ?: return ResponseEntity.badRequest().build()

            val startDate = LocalDateTime.parse(request.startDate)
            val endDate = LocalDateTime.parse(request.endDate)

            val clients = clientRepository.findByEmployeeId(request.employeeId)
            if (clients.isEmpty()) {
                println("⚠️  No clients found in database - returning empty payroll")
                val emptyResponse = createEmptyPayrollResponse(employee, startDate, endDate)
                val id = payrollCacheService.store(emptyResponse)  // Store even empty
                return ResponseEntity.ok(PayrollCalculationResponse(id, emptyResponse))
            }

            val events = googleCalendarService.getEventsForPeriod(employee.calendarId, startDate, endDate)
            if (events.isEmpty()) {
                println("⚠️  No calendar events found - returning empty payroll")
                val emptyResponse = createEmptyPayrollResponse(employee, startDate, endDate)
                val id = payrollCacheService.store(emptyResponse)
                return ResponseEntity.ok(PayrollCalculationResponse(id, emptyResponse))
            }

            // 5. Filter events by client names
            val clientNames = clients.map { it.name }
            val clientEvents = googleCalendarService.filterEventsByClientNames(events, clientNames)

            // 6. Calculate payroll
            val payrollReport = payrollService.calculatePayroll(employee, clients, clientEvents, startDate, endDate)

            // 7. Sync to Sheets if requested
            var syncedToSheets = false
            if (request.syncToSheets) {
                println("📤 Syncing payroll to Sheets...")
                val syncResult = sheetsSyncService.syncPayrollToSheets(payrollReport)
                syncedToSheets = syncResult["status"] == "success"

                if (syncedToSheets) {
                    println("✅ Successfully synced to Sheets")
                    println("   Master rows written: ${syncResult["masterRows"]}")
                    println("   Detail rows written: ${syncResult["detailRows"]}")
                } else {
                    println("⚠️  Failed to sync to Sheets: ${syncResult["message"]}")
                }
            }

            // 8. Convert to response format
            val response = createPayrollResponse(payrollReport, clientEvents, syncedToSheets)

            // 🆕 9. CACHE AND WRAP WITH ID
            val payrollId = payrollCacheService.store(response)
            val wrappedResponse = PayrollCalculationResponse(
                id = payrollId,
                payroll = response
            )

            println("✅ Payroll calculated and cached with ID: $payrollId")
            ResponseEntity.ok(wrappedResponse)

        } catch (e: Exception) {
            println("❌ Error calculating payroll: ${e.message}")
            e.printStackTrace()
            ResponseEntity.internalServerError().build()
        }
    }

    /**
     * 📤 Manual sync existing payroll to Google Sheets
     * POST /payroll/{id}/sync-to-sheets
     */
    @PostMapping("/{id}/sync-to-sheets")
    fun syncPayrollToSheets(@PathVariable id: String): ResponseEntity<Map<String, Any>> {
        return try {
            println("📤 Manual sync request for payroll ID: $id")

            // 1. Retrieve cached payroll
            val cached = payrollCacheService.retrieve(id)
                ?: return ResponseEntity.notFound().build()

            println("✅ Found cached payroll for: ${cached.data.employee.name}")

            // 2. Convert PayrollResponse → PayrollReport
            val payrollReport = convertResponseToReport(cached.data)

            // 3. Check if already exists
            val periodParts = cached.data.period.split(" - ")
            if (periodParts.size != 2) {
                return ResponseEntity.badRequest().body(
                    mapOf(
                        "status" to "error",
                        "message" to "Invalid period format in cached payroll"
                    ) as Map<String, Any>  // 🔴 EXPLICIT CAST!
                )
            }

            val existingPayroll = sheetsService.findExistingPayroll(
                employeeName = cached.data.employee.name,
                periodStart = periodParts[0],
                periodEnd = periodParts[1]
            )

            // 4. Sync to Sheets
            val syncResult = sheetsSyncService.syncPayrollToSheets(payrollReport)

            // 5. Return result με extra info
            val mode = if (existingPayroll != null) "updated" else "inserted"

            ResponseEntity.ok(
                mapOf(
                    "status" to syncResult["status"],
                    "message" to syncResult["message"],
                    "mode" to mode,
                    "employeeName" to cached.data.employee.name,
                    "period" to cached.data.period,
                    "totalSessions" to cached.data.summary.totalSessions,
                    "totalRevenue" to cached.data.summary.totalRevenue,
                    "masterWritten" to syncResult["masterWritten"],
                    "detailsWritten" to syncResult["detailsWritten"],
                    "masterRows" to syncResult.getOrDefault("masterRows", 0),
                    "detailRows" to syncResult.getOrDefault("detailRows", 0)
                ) as Map<String, Any>  // 🔴 EXPLICIT CAST!
            )

        } catch (e: Exception) {
            println("❌ Error syncing payroll to Sheets: ${e.message}")
            e.printStackTrace()

            ResponseEntity.internalServerError().body(
                mapOf(
                    "status" to "error",
                    "message" to (e.message ?: "Unknown error during sync")
                ) as Map<String, Any>  // 🔴 EXPLICIT CAST!
            )
        }
    }

    /**
     * 🔍 Check if payroll exists in Sheets (για confirmation dialog)
     * GET /payroll/{id}/check-sheets
     */
    @GetMapping("/{id}/check-sheets")
    fun checkPayrollInSheets(@PathVariable id: String): ResponseEntity<Map<String, Any>> {
        return try {
            println("🔍 Checking if payroll exists in Sheets: $id")

            // Retrieve cached payroll
            val cached = payrollCacheService.retrieve(id)
                ?: return ResponseEntity.notFound().build()

            // Parse period
            val periodParts = cached.data.period.split(" - ")
            if (periodParts.size != 2) {
                return ResponseEntity.badRequest().body(
                    mapOf("error" to "Invalid period format") as Map<String, Any>  // 🔴 CAST!
                )
            }

            // Check existence
            val existingPayroll = sheetsService.findExistingPayroll(
                employeeName = cached.data.employee.name,
                periodStart = periodParts[0],
                periodEnd = periodParts[1]
            )

            val existingDetails = if (existingPayroll != null) {
                sheetsService.findExistingClientDetails(
                    employeeName = cached.data.employee.name,
                    periodStart = periodParts[0],
                    periodEnd = periodParts[1]
                )
            } else {
                emptyList()
            }

            ResponseEntity.ok(
                mapOf(
                    "exists" to (existingPayroll != null),
                    "employeeName" to cached.data.employee.name,
                    "period" to cached.data.period,
                    "existingMasterRow" to existingPayroll?.rowIndex,
                    "existingDetailRows" to existingDetails.size,
                    "action" to if (existingPayroll != null) "update" else "insert",
                    "message" to if (existingPayroll != null) {
                        "Υπάρχει ήδη payroll για αυτήν την περίοδο. Θα ενημερωθεί."
                    } else {
                        "Νέο payroll - θα προστεθεί στο Sheets."
                    }
                ) as Map<String, Any>  // 🔴 EXPLICIT CAST!
            )

        } catch (e: Exception) {
            println("❌ Error checking Sheets: ${e.message}")

            ResponseEntity.internalServerError().body(
                mapOf(
                    "error" to (e.message ?: "Unknown error"),
                    "exists" to false
                ) as Map<String, Any>  // 🔴 EXPLICIT CAST!
            )
        }
    }

    /**
     * 🔧 Helper: Convert PayrollResponse → PayrollReport
     */
    private fun convertResponseToReport(response: PayrollResponse): PayrollReport {
        // Parse dates from period string
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val periodParts = response.period.split(" - ")

        val periodStart = LocalDateTime.parse(
            periodParts[0].trim() + " 00:00",
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        )
        val periodEnd = LocalDateTime.parse(
            periodParts[1].trim() + " 23:59",
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        )

        // Get employee from repository
        val employee = employeeRepository.findById(response.employee.id).orElseThrow {
            Exception("Employee not found: ${response.employee.id}")
        }

        // Convert client breakdown to PayrollEntry
        val entries = response.clientBreakdown.map { client ->
            PayrollEntry(
                clientName = client.clientName,
                clientPrice = client.pricePerSession,
                employeePrice = client.employeePricePerSession,
                companyPrice = client.companyPricePerSession,
                sessionsCount = client.sessions,
                totalRevenue = client.totalRevenue,
                employeeEarnings = client.employeeEarnings,
                companyEarnings = client.companyEarnings
            )
        }

        return PayrollReport(
            employee = employee,
            periodStart = periodStart,
            periodEnd = periodEnd,
            entries = entries,
            totalSessions = response.summary.totalSessions,
            totalRevenue = response.summary.totalRevenue,
            totalEmployeeEarnings = response.summary.employeeEarnings,
            totalCompanyEarnings = response.summary.companyEarnings,
            generatedAt = LocalDateTime.now()
        )
    }

    @GetMapping("/quick-test/{employeeId}")
    fun quickPayrollTest(@PathVariable employeeId: String): ResponseEntity<Map<String, Any>> {
        return try {
            val employee = employeeRepository.findById(employeeId).orElse(null)
                ?: return ResponseEntity.badRequest().body(mapOf("error" to "Employee not found"))

            val clients = clientRepository.findByEmployeeId(employeeId)

            val now = LocalDateTime.now()
            val startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
            val endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59).withSecond(59)

            val events = googleCalendarService.getEventsForPeriod(employee.calendarId, startOfMonth, endOfMonth)
            val clientNames = clients.map { it.name }
            val clientEvents = googleCalendarService.filterEventsByClientNames(events, clientNames)

            val totalMatchedEvents = clientEvents.values.sumOf { it.size }

            ResponseEntity.ok(
                mapOf(
                    "employee" to employee.name,
                    "clients" to clients.size,
                    "totalEvents" to events.size,
                    "matchedEvents" to totalMatchedEvents,
                    "period" to "${startOfMonth.toLocalDate()} to ${endOfMonth.toLocalDate()}",
                    "status" to "Ready for payroll calculation"
                )
            )
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }

    @GetMapping("/periods")
    fun getCommonPeriods(): List<Map<String, String>> {
        val now = LocalDateTime.now()

        return listOf(
            mapOf(
                "name" to "Current Month",
                "startDate" to now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).toString(),
                "endDate" to now.withDayOfMonth(now.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59).withSecond(59).toString()
            ),
            mapOf(
                "name" to "Previous Month",
                "startDate" to now.minusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).toString(),
                "endDate" to now.minusMonths(1).withDayOfMonth(now.minusMonths(1).toLocalDate().lengthOfMonth()).withHour(23).withMinute(59).withSecond(59).toString()
            ),
            mapOf(
                "name" to "Last 30 Days",
                "startDate" to now.minusDays(30).withHour(0).withMinute(0).withSecond(0).toString(),
                "endDate" to now.withHour(23).withMinute(59).withSecond(59).toString()
            ),
            mapOf(
                "name" to "Current Week",
                "startDate" to now.minusDays(now.dayOfWeek.value - 1L).withHour(0).withMinute(0).withSecond(0).toString(),
                "endDate" to now.plusDays(7 - now.dayOfWeek.value.toLong()).withHour(23).withMinute(59).withSecond(59).toString()
            )
        )
    }

    @GetMapping("/default-period")
    fun getDefaultPeriod(): Map<String, String> {
        val today = LocalDateTime.now()
        val twoWeeksAgo = today.minusWeeks(2)

        return mapOf(
            "startDate" to twoWeeksAgo.withHour(0).withMinute(0).toString(),
            "endDate" to today.withHour(23).withMinute(59).toString()
        )
    }

    private fun createPayrollResponse(
        report: PayrollReport,
        clientEvents: Map<String, List<CalendarEvent>>,
        syncedToSheets: Boolean
    ): PayrollResponse {
        val clientBreakdown = report.entries.map { entry ->
            val events = clientEvents[entry.clientName] ?: emptyList()
            val eventDetails = events.filter { event ->
                event.startTime >= report.periodStart && event.startTime <= report.periodEnd
            }.map { event ->
                EventDetail(
                    date = event.startTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    time = event.startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    duration = "1h",
                    status = when {
                        event.isCancelled && event.isPendingPayment -> "pending_payment"
                        event.isCancelled -> "cancelled"
                        else -> "completed"
                    },
                    colorId = event.colorId
                )
            }

            ClientPayrollDetail(
                clientName = entry.clientName,
                pricePerSession = entry.clientPrice,
                employeePricePerSession = entry.employeePrice,
                companyPricePerSession = entry.companyPrice,
                sessions = entry.sessionsCount,
                totalRevenue = entry.totalRevenue,
                employeeEarnings = entry.employeeEarnings,
                companyEarnings = entry.companyEarnings,
                eventDetails = eventDetails
            )
        }

        return PayrollResponse(
            employee = EmployeeInfo(
                id = report.employee.id,
                name = report.employee.name,
                email = report.employee.email
            ),
            period = "${report.periodStart.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} - ${report.periodEnd.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
            summary = PayrollSummary(
                totalSessions = report.totalSessions,
                totalRevenue = report.totalRevenue,
                employeeEarnings = report.totalEmployeeEarnings,
                companyEarnings = report.totalCompanyEarnings
            ),
            clientBreakdown = clientBreakdown,
            generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
            syncedToSheets = syncedToSheets
        )
    }

    private fun createEmptyPayrollResponse(
        employee: Employee,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): PayrollResponse {
        return PayrollResponse(
            employee = EmployeeInfo(
                id = employee.id,
                name = employee.name,
                email = employee.email
            ),
            period = "${startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} - ${endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
            summary = PayrollSummary(
                totalSessions = 0,
                totalRevenue = 0.0,
                employeeEarnings = 0.0,
                companyEarnings = 0.0
            ),
            clientBreakdown = emptyList(),
            generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
            syncedToSheets = false
        )
    }
}