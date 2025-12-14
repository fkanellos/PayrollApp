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

data class EventTracking(
    val totalEvents: Int,
    val matchedEvents: Int,
    val unmatchedEvents: List<UnmatchedEvent>,
    val cancelledGreyEvents: List<CancelledEvent>,  // Pending payment
    val cancelledRedEvents: List<CancelledEvent>,    // Will NOT be paid
    val supervisionEvents: List<SupervisionEvent>,
    val emptyTitleEvents: Int  // Availability hours etc.
)

data class UnmatchedEvent(
    val title: String,
    val date: String,
    val time: String,
    val colorId: String?,
    val status: String
)

data class CancelledEvent(
    val title: String,
    val date: String,
    val time: String,
    val colorId: String,
    val type: String  // "grey" or "red"
)

data class SupervisionEvent(
    val date: String,
    val time: String,
    val counted: Boolean,  // If it was included in payroll
    val reason: String?    // Why it wasn't counted (if applicable)
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
    val eventTracking: EventTracking,  // 🆕 NEW!
    val generatedAt: LocalDateTime = LocalDateTime.now()
)

// Data class για supervision config
data class SupervisionConfig(
    val enabled: Boolean,
    val price: Double,
    val employeePrice: Double,
    val companyPrice: Double,
    val keywords: List<String> = listOf("Εποπτεία", "Supervision")
)

@Service
class PayrollCalculationService {

    fun calculatePayroll(
        employee: Employee,
        clients: List<Client>,
        allEvents: List<CalendarEvent>,  // 🆕 All events (for tracking)
        clientEvents: Map<String, List<CalendarEvent>>,
        periodStart: LocalDateTime,
        periodEnd: LocalDateTime,
        supervisionConfig: SupervisionConfig? = null
    ): PayrollReport {

        val entries = mutableListOf<PayrollEntry>()
        var totalSessions = 0
        var totalRevenue = 0.0
        var totalEmployeeEarnings = 0.0
        var totalCompanyEarnings = 0.0

        // Track all events for comprehensive reporting
        val unmatchedEvents = mutableListOf<UnmatchedEvent>()
        val cancelledGreyEvents = mutableListOf<CancelledEvent>()
        val cancelledRedEvents = mutableListOf<CancelledEvent>()
        val supervisionEventsList = mutableListOf<SupervisionEvent>()
        var emptyTitleCount = 0

        val clientLookup = clients.associateBy { it.name }
        val matchedEventIds = clientEvents.values.flatten().map { it.id }.toSet()

        // 1. Process client events (existing logic)
        clientEvents.forEach { (clientName, events) ->
            // Skip if this is the supervision keyword
            if (supervisionConfig != null && clientName in supervisionConfig.keywords) {
                return@forEach // Handle separately below
            }

            val client = clientLookup[clientName] ?: return@forEach

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

        // 2. 🆕 NEW: Process supervision sessions
        if (supervisionConfig != null) {
            supervisionConfig.keywords.forEach { keyword ->
                val supervisionEvents = clientEvents[keyword] ?: emptyList()

                val validSupervisionEvents = supervisionEvents.filter { event ->
                    event.startTime.isAfter(periodStart) &&
                            event.startTime.isBefore(periodEnd) &&
                            (!event.isCancelled || event.isPendingPayment)  // ✅ Same logic as regular clients!
                }

                if (validSupervisionEvents.isNotEmpty()) {
                    val sessionsCount = validSupervisionEvents.size
                    val clientRevenue = sessionsCount * supervisionConfig.price
                    val employeeEarnings = sessionsCount * supervisionConfig.employeePrice
                    val companyEarnings = sessionsCount * supervisionConfig.companyPrice

                    val entry = PayrollEntry(
                        clientName = "Εποπτεία (Supervision)",
                        clientPrice = supervisionConfig.price,
                        employeePrice = supervisionConfig.employeePrice,
                        companyPrice = supervisionConfig.companyPrice,
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
        }

        // 3. 🆕 Categorize ALL events for tracking
        allEvents.forEach { event ->
            // Skip if empty title (availability hours)
            if (event.title.isBlank()) {
                emptyTitleCount++
                return@forEach
            }

            // Check if it's a supervision event
            val isSupervision = supervisionConfig?.keywords?.any {
                event.title.contains(it, ignoreCase = true)
            } ?: false

            if (isSupervision) {
                val counted = event.startTime.isAfter(periodStart) &&
                              event.startTime.isBefore(periodEnd) &&
                              (!event.isCancelled || event.isPendingPayment)

                supervisionEventsList.add(SupervisionEvent(
                    date = event.startTime.toLocalDate().toString(),
                    time = event.startTime.toLocalTime().toString(),
                    counted = counted,
                    reason = when {
                        event.isCancelled && !event.isPendingPayment -> "Cancelled (red)"
                        !event.startTime.isAfter(periodStart) ||
                        !event.startTime.isBefore(periodEnd) -> "Outside period"
                        else -> null
                    }
                ))
                return@forEach
            }

            // Track cancelled events by color
            if (event.isCancelled) {
                val cancelledEvent = CancelledEvent(
                    title = event.title,
                    date = event.startTime.toLocalDate().toString(),
                    time = event.startTime.toLocalTime().toString(),
                    colorId = event.colorId ?: "unknown",
                    type = when {
                        event.isPendingPayment -> "grey"
                        else -> "red"
                    }
                )

                if (event.isPendingPayment) {
                    cancelledGreyEvents.add(cancelledEvent)
                } else {
                    cancelledRedEvents.add(cancelledEvent)
                }
            }

            // Track unmatched events (not matched to any client)
            if (event.id !in matchedEventIds && !isSupervision) {
                unmatchedEvents.add(UnmatchedEvent(
                    title = event.title,
                    date = event.startTime.toLocalDate().toString(),
                    time = event.startTime.toLocalTime().toString(),
                    colorId = event.colorId,
                    status = when {
                        event.isCancelled && event.isPendingPayment -> "⏳ Cancelled (will pay next time)"
                        event.isCancelled -> "❌ Cancelled"
                        else -> "❓ No client match"
                    }
                ))
            }
        }

        val eventTracking = EventTracking(
            totalEvents = allEvents.size,
            matchedEvents = matchedEventIds.size,
            unmatchedEvents = unmatchedEvents,
            cancelledGreyEvents = cancelledGreyEvents,
            cancelledRedEvents = cancelledRedEvents,
            supervisionEvents = supervisionEventsList,
            emptyTitleEvents = emptyTitleCount
        )

        return PayrollReport(
            employee = employee,
            periodStart = periodStart,
            periodEnd = periodEnd,
            entries = entries,
            totalSessions = totalSessions,
            totalRevenue = totalRevenue,
            totalEmployeeEarnings = totalEmployeeEarnings,
            totalCompanyEarnings = totalCompanyEarnings,
            eventTracking = eventTracking  // 🆕 Include tracking!
        )
    }
}