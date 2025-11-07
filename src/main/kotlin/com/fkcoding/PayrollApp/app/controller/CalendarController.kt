package com.fkcoding.PayrollApp.app.controller

import com.fkcoding.PayrollApp.app.repository.ClientRepository
import com.fkcoding.PayrollApp.app.repository.EmployeeRepository
import com.fkcoding.PayrollApp.app.service.GoogleCalendarService
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/calendar")
@CrossOrigin(origins = ["http://localhost:3000"])
class CalendarController(
    private val googleCalendarService: GoogleCalendarService,
    private val clientRepository: ClientRepository,
    private val employeeRepository: EmployeeRepository
) {

    @GetMapping("/events/{employeeId}")
    fun getEmployeeEvents(
        @PathVariable employeeId: String,
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): Map<String, Any> {

        val employee = employeeRepository.findById(employeeId).orElse(null)
            ?: return mapOf("error" to "Employee not found")

        val start = LocalDateTime.parse(startDate)
        val end = LocalDateTime.parse(endDate)

        val events = googleCalendarService.getEventsForPeriod(employee.calendarId, start, end)
        val clients = clientRepository.findByEmployeeId(employeeId)
        val clientNames = clients.map { it.name }
        val filteredEvents = googleCalendarService.filterEventsByClientNames(events, clientNames)

        return mapOf(
            "totalEvents" to events.size,
            "matchedEvents" to filteredEvents,
            "period" to "$startDate to $endDate",
            "employee" to employee.name
        )
    }

    @GetMapping("/calendars")
    fun getCalendarList(): List<Map<String, Any>> {
        return googleCalendarService.getCalendarList()
    }
}