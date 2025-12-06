package com.fkcoding.PayrollApp.app.controller

import com.fkcoding.PayrollApp.app.service.GoogleCalendarService
import com.fkcoding.PayrollApp.app.service.CalendarEvent
import com.fkcoding.PayrollApp.app.service.ClientMatchingService
import com.fkcoding.PayrollApp.app.repository.ClientRepository
import com.fkcoding.PayrollApp.app.repository.EmployeeRepository
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/api/debug")
@CrossOrigin(origins = ["*"])
class DebugController(
    private val googleCalendarService: GoogleCalendarService,
    private val clientRepository: ClientRepository,
    private val employeeRepository: EmployeeRepository,
    private val clientMatchingService: ClientMatchingService
) {

    /**
     * 🔍 DEBUG: Εμφανίζει ΟΛΑ τα events για έναν employee στις τελευταίες 2 εβδομάδες
     * GET /api/debug/events/{employeeId}
     */
    @GetMapping("/events/{employeeId}")
    fun debugEvents(@PathVariable employeeId: String): Map<String, Any> {
        return try {
            println("\n" + "=" * 100)
            println("🔍 DEBUG: FETCHING ALL EVENTS FOR LAST 2 WEEKS")
            println("=" * 100)

            // 1. Get employee
            val employee = employeeRepository.findById(employeeId).orElse(null)
                ?: return mapOf("error" to "Employee not found")

            println("👤 Employee: ${employee.name}")
            println("📧 Email: ${employee.email}")
            println("📅 Calendar ID: ${employee.calendarId}")

            // 2. Get clients
            val clients = clientRepository.findByEmployeeId(employeeId)
            val clientNames = clients.map { it.name }
            println("\n👥 Registered Clients (${clients.size}):")
            clients.forEach { client ->
                println("   - ${client.name} (€${client.price}/session)")
            }

            // 3. Define period (last 2 weeks)
            val now = LocalDateTime.now()
            val twoWeeksAgo = now.minusWeeks(2)
            val startDate = twoWeeksAgo.withHour(0).withMinute(0).withSecond(0)
            val endDate = now.withHour(23).withMinute(59).withSecond(59)

            println("\n📆 Period:")
            println("   Start: ${startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}")
            println("   End:   ${endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}")

            // 4. Fetch ALL events
            println("\n🔄 Fetching events from Google Calendar...")
            val allEvents = googleCalendarService.getEventsForPeriod(
                employee.calendarId,
                startDate,
                endDate
            )

            println("\n📊 TOTAL EVENTS FOUND: ${allEvents.size}")

            // 5. Filter by client names
            val clientEvents = googleCalendarService.filterEventsByClientNames(allEvents, clientNames)

            val matchedEvents = clientEvents.flatMap { it.value }
            val unmatchedEvents = allEvents.filter { event ->
                matchedEvents.none { it.id == event.id }
            }

            // 6. Categorize events
            val categorizedEvents = categorizeEvents(allEvents, matchedEvents)

            // 7. Detailed breakdown
            println("\n" + "=" * 100)
            println("📋 EVENT BREAKDOWN")
            println("=" * 100)

            println("\n✅ MATCHED EVENTS (${matchedEvents.size}):")
            matchedEvents.forEach { event ->
                printEventDetails(event, clientEvents)
            }

            println("\n❌ UNMATCHED EVENTS (${unmatchedEvents.size}):")
            unmatchedEvents.forEach { event ->
                printEventDetails(event, emptyMap())
            }

            // 8. Summary by client
            println("\n" + "=" * 100)
            println("📊 SUMMARY BY CLIENT")
            println("=" * 100)
            clientEvents.forEach { (clientName, events) ->
                val completed = events.count { !it.isCancelled }
                val cancelled = events.count { it.isCancelled && !it.isPendingPayment }
                val pendingPayment = events.count { it.isPendingPayment }

                println("\n👤 $clientName:")
                println("   Total Events: ${events.size}")
                println("   ✅ Completed: $completed")
                println("   ❌ Cancelled: $cancelled")
                println("   ⏳ Pending Payment: $pendingPayment")
            }

            println("\n" + "=" * 100)

            // Return JSON response
            mapOf(
                "employee" to mapOf(
                    "id" to employee.id,
                    "name" to employee.name,
                    "email" to employee.email,
                    "calendarId" to employee.calendarId
                ),
                "period" to mapOf(
                    "start" to startDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    "end" to endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                ),
                "clients" to clients.map { mapOf(
                    "name" to it.name,
                    "price" to it.price,
                    "employeePrice" to it.employeePrice,
                    "companyPrice" to it.companyPrice
                )},
                "summary" to mapOf(
                    "totalEvents" to allEvents.size,
                    "matchedEvents" to matchedEvents.size,
                    "unmatchedEvents" to unmatchedEvents.size,
                    "completedEvents" to categorizedEvents["completed"],
                    "cancelledEvents" to categorizedEvents["cancelled"],
                    "pendingPaymentEvents" to categorizedEvents["pendingPayment"]
                ),
                "eventsByClient" to clientEvents.mapValues { (_, events) ->
                    events.map { event ->
                        mapOf(
                            "id" to event.id,
                            "title" to event.title,
                            "start" to event.startTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                            "end" to event.endTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                            "colorId" to (event.colorId ?: "none"),
                            "cancelled" to event.isCancelled,
                            "pendingPayment" to event.isPendingPayment,
                            "status" to when {
                                event.isPendingPayment -> "⏳ Pending Payment"
                                event.isCancelled -> "❌ Cancelled"
                                else -> "✅ Completed"
                            }
                        )
                    }
                },
                "unmatchedEvents" to unmatchedEvents.map { event ->
                    mapOf(
                        "id" to event.id,
                        "title" to event.title,
                        "start" to event.startTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                        "colorId" to (event.colorId ?: "none"),
                        "cancelled" to event.isCancelled,
                        "reason" to "No client name match found"
                    )
                }
            )

        } catch (e: Exception) {
            println("\n❌ ERROR: ${e.message}")
            e.printStackTrace()
            mapOf(
                "error" to (e.message ?: "Unknown error"),
                "stackTrace" to e.stackTraceToString()
            )
        }
    }

    private fun printEventDetails(event: CalendarEvent, clientMatches: Map<String, List<CalendarEvent>>) {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val matchedClient = clientMatches.entries.find { it.value.any { e -> e.id == event.id } }?.key

        val status = when {
            event.isPendingPayment -> "⏳ PENDING PAYMENT"
            event.isCancelled -> "❌ CANCELLED"
            else -> "✅ COMPLETED"
        }

        println("   ---")
        println("   📅 ${event.startTime.format(formatter)}")
        println("   📝 Title: ${event.title}")
        println("   🆔 ID: ${event.id}")
        println("   🎨 Color: ${event.colorId ?: "none"}")
        println("   📊 Status: $status")
        if (matchedClient != null) {
            println("   👤 Matched Client: $matchedClient")
        }
        println("   🔗 Attendees: ${event.attendees.joinToString(", ").ifEmpty { "none" }}")
    }

    private fun categorizeEvents(
        allEvents: List<CalendarEvent>,
        matchedEvents: List<CalendarEvent>
    ): Map<String, Int> {
        return mapOf(
            "completed" to matchedEvents.count { !it.isCancelled },
            "cancelled" to matchedEvents.count { it.isCancelled && !it.isPendingPayment },
            "pendingPayment" to matchedEvents.count { it.isPendingPayment }
        )
    }

    /**
     * 🔍 DEBUG: Δοκιμάζει το matching για ένα συγκεκριμένο event title
     * GET /api/debug/match-test?title=...&employeeId=...
     */
    @GetMapping("/match-test")
    fun testMatching(
        @RequestParam title: String,
        @RequestParam employeeId: String
    ): Map<String, Any> {
        return try {
            val clients = clientRepository.findByEmployeeId(employeeId)
            val clientNames = clients.map { it.name }

            // Use ClientMatchingService for matching
            val result = clientMatchingService.findClientMatchesDebug(title, clientNames)

            mapOf(
                "title" to title,
                "clientNames" to clientNames,
                "result" to result
            )
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Unknown error"))
        }
    }

}

// Helper
private operator fun String.times(n: Int): String = this.repeat(n)