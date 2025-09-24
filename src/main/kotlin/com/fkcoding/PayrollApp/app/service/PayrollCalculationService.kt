package com.fkcoding.PayrollApp.app.service

import com.fkcoding.PayrollApp.app.entity.Client
import com.fkcoding.PayrollApp.app.entity.Employee
import org.springframework.stereotype.Service
import java.time.LocalDateTime

data class PayrollEntry(
    val clientName: String,
    val clientPrice: Double,
    val employeePrice: Double,
    val companyPrice: Double,
    val sessionsCount: Int,
    val totalRevenue: Double,
    val employeeEarnings: Double,
    val companyEarnings: Double
)

data class PayrollReport(
    val employee: Employee,
    val periodStart: LocalDateTime,
    val periodEnd: LocalDateTime,
    val entries: List<PayrollEntry>,
    val totalSessions: Int,
    val totalRevenue: Double,
    val totalEmployeeEarnings: Double,
    val totalCompanyEarnings: Double,
    val generatedAt: LocalDateTime = LocalDateTime.now()
)

@Service
class PayrollCalculationService {

    fun calculatePayroll(
        employee: Employee,
        clients: List<Client>,
        clientEvents: Map<String, List<CalendarEvent>>,
        periodStart: LocalDateTime,
        periodEnd: LocalDateTime
    ): PayrollReport {

        val entries = mutableListOf<PayrollEntry>()
        var totalSessions = 0
        var totalRevenue = 0.0
        var totalEmployeeEarnings = 0.0
        var totalCompanyEarnings = 0.0

        val clientLookup = clients.associateBy { it.name }

        clientEvents.forEach { (clientName, events) ->
            val client = clientLookup[clientName] ?: return@forEach

            // Filter events for the period and exclude cancelled (unless pending payment)
            val validEvents = events.filter { event ->
                event.startTime.isAfter(periodStart) &&
                        event.startTime.isBefore(periodEnd) &&
                        (!event.isCancelled || event.isPendingPayment)
            }

            if (validEvents.isNotEmpty()) {
                val sessionsCount = validEvents.size
                val clientRevenue = sessionsCount * client.price
                val employeeEarnings = sessionsCount * client.employeePrice
                val companyEarnings = sessionsCount * client.companyPrice

                val entry = PayrollEntry(
                    clientName = clientName,
                    clientPrice = client.price,
                    employeePrice = client.employeePrice,
                    companyPrice = client.companyPrice,
                    sessionsCount = sessionsCount,
                    totalRevenue = clientRevenue,
                    employeeEarnings = employeeEarnings,
                    companyEarnings = companyEarnings
                )

                entries.add(entry)
                totalSessions += sessionsCount
                totalRevenue += clientRevenue
                totalEmployeeEarnings += employeeEarnings
                totalCompanyEarnings += companyEarnings
            }
        }

        return PayrollReport(
            employee = employee,
            periodStart = periodStart,
            periodEnd = periodEnd,
            entries = entries,
            totalSessions = totalSessions,
            totalRevenue = totalRevenue,
            totalEmployeeEarnings = totalEmployeeEarnings,
            totalCompanyEarnings = totalCompanyEarnings
        )
    }
}