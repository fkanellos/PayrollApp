package com.fkcoding.PayrollApp.app.controller

import com.fkcoding.PayrollApp.app.dto.CreateEmployeeRequest
import com.fkcoding.PayrollApp.app.dto.UpdateEmployeeRequest
import com.fkcoding.PayrollApp.app.entity.Client
import com.fkcoding.PayrollApp.app.entity.Employee
import com.fkcoding.PayrollApp.app.repository.ClientRepository
import com.fkcoding.PayrollApp.app.repository.EmployeeRepository
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/employees")
class EmployeeController(
    private val employeeRepository: EmployeeRepository,
    private val clientRepository: ClientRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(EmployeeController::class.java)
    }

    // ================== READ OPERATIONS ==================

    @GetMapping
    fun getAllEmployees(): List<Employee> {
        return employeeRepository.findAll()
    }

    @GetMapping("/{id}")
    fun getEmployee(@PathVariable id: String): ResponseEntity<*> {
        val employee = employeeRepository.findById(id).orElse(null)
        return if (employee != null) {
            ResponseEntity.ok(employee)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "Employee not found with id: $id"))
        }
    }

    @GetMapping("/{id}/clients")
    fun getEmployeeClients(@PathVariable id: String): List<Client> {
        return clientRepository.findByEmployeeId(id)
    }

    // ================== CREATE OPERATION ==================

    @PostMapping
    @Transactional
    fun createEmployee(@Valid @RequestBody request: CreateEmployeeRequest): ResponseEntity<*> {
        try {
            // Check if employee already exists
            if (employeeRepository.existsById(request.id)) {
                logger.warn("❌ Employee with id '${request.id}' already exists")
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(mapOf("error" to "Employee with id '${request.id}' already exists"))
            }

            // Create new employee
            val employee = Employee(
                id = request.id,
                name = request.name,
                email = request.email,
                calendarId = request.calendarId,
                color = request.color,
                sheetName = request.sheetName,
                supervisionPrice = request.supervisionPrice
            )

            val saved = employeeRepository.save(employee)
            logger.info("✅ Created employee: ${saved.name} (id: ${saved.id})")

            return ResponseEntity.status(HttpStatus.CREATED).body(saved)

        } catch (e: Exception) {
            logger.error("❌ Error creating employee: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to create employee: ${e.message}"))
        }
    }

    // ================== UPDATE OPERATION ==================

    @PutMapping("/{id}")
    @Transactional
    fun updateEmployee(
        @PathVariable id: String,
        @Valid @RequestBody request: UpdateEmployeeRequest
    ): ResponseEntity<*> {
        try {
            val existing = employeeRepository.findById(id).orElse(null)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "Employee not found with id: $id"))

            // Update employee
            val updated = existing.copy(
                name = request.name,
                email = request.email,
                calendarId = request.calendarId,
                color = request.color,
                sheetName = request.sheetName,
                supervisionPrice = request.supervisionPrice
            )

            val saved = employeeRepository.save(updated)
            logger.info("✅ Updated employee: ${saved.name} (id: ${saved.id})")

            return ResponseEntity.ok(saved)

        } catch (e: Exception) {
            logger.error("❌ Error updating employee: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to update employee: ${e.message}"))
        }
    }

    // ================== DELETE OPERATION ==================

    @DeleteMapping("/{id}")
    @Transactional
    fun deleteEmployee(@PathVariable id: String): ResponseEntity<*> {
        try {
            if (!employeeRepository.existsById(id)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "Employee not found with id: $id"))
            }

            // Check if employee has clients
            val clientCount = clientRepository.findByEmployeeId(id).size
            if (clientCount > 0) {
                logger.warn("⚠️ Cannot delete employee '$id' - has $clientCount clients")
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(mapOf(
                        "error" to "Cannot delete employee with existing clients",
                        "clientCount" to clientCount,
                        "hint" to "Delete all clients first or use force=true"
                    ))
            }

            employeeRepository.deleteById(id)
            logger.info("✅ Deleted employee: $id")

            return ResponseEntity.ok(mapOf("message" to "Employee deleted successfully", "id" to id))

        } catch (e: Exception) {
            logger.error("❌ Error deleting employee: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to delete employee: ${e.message}"))
        }
    }
}
