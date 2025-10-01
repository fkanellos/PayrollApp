package com.fkcoding.PayrollApp.app.controller

import com.fkcoding.PayrollApp.app.entity.Employee
import com.fkcoding.PayrollApp.app.service.GoogleCalendarService
import com.fkcoding.PayrollApp.app.service.PayrollCalculationService
import com.fkcoding.PayrollApp.app.repository.EmployeeRepository
import com.fkcoding.PayrollApp.app.repository.ClientRepository
import com.fkcoding.PayrollApp.app.service.CalendarEvent
import com.fkcoding.PayrollApp.app.service.PayrollCacheService
import com.fkcoding.PayrollApp.app.service.PayrollReport
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class PayrollRequest(
    val employeeId: String,
    val startDate: String, // ISO format: 2024-09-01T00:00:00
    val endDate: String     // ISO format: 2024-09-30T23:59:59
)

data class PayrollResponse(
    val employee: EmployeeInfo,
    val period: String,
    val summary: PayrollSummary,
    val clientBreakdown: List<ClientPayrollDetail>,
    val generatedAt: String
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
    val status: String, // "completed", "cancelled", "pending_payment"
    val colorId: String?
)

@RestController
@RequestMapping("/payroll")
@CrossOrigin(origins = ["http://localhost:3000"])
class PayrollController(
    private val employeeRepository: EmployeeRepository,
    private val clientRepository: ClientRepository,
    private val googleCalendarService: GoogleCalendarService,
    private val payrollService: PayrollCalculationService,
    private val payrollCacheService: PayrollCacheService
) {

    @PostMapping("/calculate")
    fun calculatePayroll(@RequestBody request: PayrollRequest): ResponseEntity<Map<String, Any>> {
        try {
            val employee = employeeRepository.findById(request.employeeId).orElse(null)
                ?: return ResponseEntity.badRequest().build()

            val startDate = LocalDateTime.parse(request.startDate)
            val endDate = LocalDateTime.parse(request.endDate)
            val clients = clientRepository.findByEmployeeId(request.employeeId)

            if (clients.isEmpty()) {
                val emptyResponse = createEmptyPayrollResponse(employee, startDate, endDate)
                val id = payrollCacheService.store(emptyResponse)
                return ResponseEntity.ok(mapOf(
                    "id" to id,
                    "payroll" to emptyResponse
                ))
            }

            val events = googleCalendarService.getEventsForPeriod(employee.calendarId, startDate, endDate)
            if (events.isEmpty()) {
                val emptyResponse = createEmptyPayrollResponse(employee, startDate, endDate)
                val id = payrollCacheService.store(emptyResponse)
                return ResponseEntity.ok(mapOf(
                    "id" to id,
                    "payroll" to emptyResponse
                ))
            }

            val clientNames = clients.map { it.name }
            val clientEvents = googleCalendarService.filterEventsByClientNames(events, clientNames)
            val payrollReport = payrollService.calculatePayroll(employee, clients, clientEvents, startDate, endDate)
            val response = createPayrollResponse(payrollReport, clientEvents)

            // Store in cache
            val payrollId = payrollCacheService.store(response)

            // Return with ID
            return ResponseEntity.ok(mapOf(
                "id" to payrollId,
                "payroll" to response
            ))

        } catch (e: Exception) {
            println("Error calculating payroll: ${e.message}")
            return ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping("/quick-test/{employeeId}")
    fun quickPayrollTest(@PathVariable employeeId: String): ResponseEntity<Map<String, Any>> {
        return try {
            val employee = employeeRepository.findById(employeeId).orElse(null)
                ?: return ResponseEntity.badRequest().body(mapOf<String, Any>("error" to "Employee not found"))

            val clients = clientRepository.findByEmployeeId(employeeId)

            // Use current month as test period
            val now = LocalDateTime.now()
            val startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
            val endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59).withSecond(59)

            val events = googleCalendarService.getEventsForPeriod(employee.calendarId, startOfMonth, endOfMonth)
            val clientNames = clients.map { it.name }
            val clientEvents = googleCalendarService.filterEventsByClientNames(events, clientNames)

            val totalMatchedEvents = clientEvents.values.sumOf { it.size }

            ResponseEntity.ok(mapOf<String, Any>(
                "employee" to employee.name,
                "clients" to clients.size,
                "totalEvents" to events.size,
                "matchedEvents" to totalMatchedEvents,
                "period" to "${startOfMonth.toLocalDate()} to ${endOfMonth.toLocalDate()}",
                "status" to "Ready for payroll calculation"
            ))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf<String, Any>("error" to (e.message ?: "Unknown error")))
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

    private fun createPayrollResponse(report: PayrollReport, clientEvents: Map<String, List<CalendarEvent>>): PayrollResponse {
        val clientBreakdown = report.entries.map { entry ->
            val events = clientEvents[entry.clientName] ?: emptyList()
            val eventDetails = events.filter { event ->
                event.startTime >= report.periodStart && event.startTime <= report.periodEnd
            }.map { event ->
                EventDetail(
                    date = event.startTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    time = event.startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    duration = "1h", // Assuming 1 hour sessions
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
            generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        )
    }

    private fun createEmptyPayrollResponse(employee: Employee, startDate: LocalDateTime, endDate: LocalDateTime): PayrollResponse {
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
            generatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        )
    }
}