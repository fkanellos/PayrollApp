package com.fkcoding.PayrollApp.app.controller

import com.fkcoding.PayrollApp.app.dto.CreateClientRequest
import com.fkcoding.PayrollApp.app.dto.UpdateClientRequest
import com.fkcoding.PayrollApp.app.entity.Client
import com.fkcoding.PayrollApp.app.repository.ClientRepository
import com.fkcoding.PayrollApp.app.repository.EmployeeRepository
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/clients")
class ClientController(
    private val clientRepository: ClientRepository,
    private val employeeRepository: EmployeeRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(ClientController::class.java)
    }

    // ================== READ OPERATIONS ==================

    @GetMapping
    fun getAllClients(): List<Client> {
        return clientRepository.findAll()
    }

    @GetMapping("/{id}")
    fun getClient(@PathVariable id: Long): ResponseEntity<*> {
        val client = clientRepository.findById(id).orElse(null)
        return if (client != null) {
            ResponseEntity.ok(client)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "Client not found with id: $id"))
        }
    }

    // ================== CREATE OPERATION ==================

    @PostMapping
    @Transactional
    fun createClient(@Valid @RequestBody request: CreateClientRequest): ResponseEntity<*> {
        try {
            // Validate price split
            val priceSplitError = request.validatePriceSplit()
            if (priceSplitError != null) {
                logger.warn("❌ Invalid price split: $priceSplitError")
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to priceSplitError))
            }

            // Validate employee exists
            if (!employeeRepository.existsById(request.employeeId)) {
                logger.warn("❌ Employee not found: ${request.employeeId}")
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "Employee not found with id: ${request.employeeId}"))
            }

            // Create new client
            val client = Client(
                id = 0, // Auto-generated
                name = request.name,
                price = request.price,
                employeePrice = request.employeePrice,
                companyPrice = request.companyPrice,
                employeeId = request.employeeId,
                pendingPayment = request.pendingPayment
            )

            val saved = clientRepository.save(client)
            logger.info("✅ Created client: ${saved.name} (id: ${saved.id}) for employee: ${saved.employeeId}")

            return ResponseEntity.status(HttpStatus.CREATED).body(saved)

        } catch (e: Exception) {
            logger.error("❌ Error creating client: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to create client: ${e.message}"))
        }
    }

    // ================== UPDATE OPERATION ==================

    @PutMapping("/{id}")
    @Transactional
    fun updateClient(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateClientRequest
    ): ResponseEntity<*> {
        try {
            // Validate price split
            val priceSplitError = request.validatePriceSplit()
            if (priceSplitError != null) {
                logger.warn("❌ Invalid price split: $priceSplitError")
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to priceSplitError))
            }

            val existing = clientRepository.findById(id).orElse(null)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "Client not found with id: $id"))

            // Validate employee exists
            if (!employeeRepository.existsById(request.employeeId)) {
                logger.warn("❌ Employee not found: ${request.employeeId}")
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to "Employee not found with id: ${request.employeeId}"))
            }

            // Update client
            val updated = existing.copy(
                name = request.name,
                price = request.price,
                employeePrice = request.employeePrice,
                companyPrice = request.companyPrice,
                employeeId = request.employeeId,
                pendingPayment = request.pendingPayment
            )

            val saved = clientRepository.save(updated)
            logger.info("✅ Updated client: ${saved.name} (id: ${saved.id})")

            return ResponseEntity.ok(saved)

        } catch (e: Exception) {
            logger.error("❌ Error updating client: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to update client: ${e.message}"))
        }
    }

    // ================== DELETE OPERATION ==================

    @DeleteMapping("/{id}")
    @Transactional
    fun deleteClient(@PathVariable id: Long): ResponseEntity<*> {
        try {
            val client = clientRepository.findById(id).orElse(null)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "Client not found with id: $id"))

            clientRepository.deleteById(id)
            logger.info("✅ Deleted client: ${client.name} (id: $id)")

            return ResponseEntity.ok(mapOf(
                "message" to "Client deleted successfully",
                "id" to id,
                "name" to client.name
            ))

        } catch (e: Exception) {
            logger.error("❌ Error deleting client: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to delete client: ${e.message}"))
        }
    }

    // ================== BULK OPERATIONS ==================

    @GetMapping("/employee/{employeeId}")
    fun getClientsByEmployee(@PathVariable employeeId: String): ResponseEntity<*> {
        if (!employeeRepository.existsById(employeeId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("error" to "Employee not found with id: $employeeId"))
        }

        val clients = clientRepository.findByEmployeeId(employeeId)
        return ResponseEntity.ok(mapOf(
            "employeeId" to employeeId,
            "count" to clients.size,
            "clients" to clients
        ))
    }

    @DeleteMapping("/employee/{employeeId}")
    @Transactional
    fun deleteClientsByEmployee(@PathVariable employeeId: String): ResponseEntity<*> {
        try {
            if (!employeeRepository.existsById(employeeId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "Employee not found with id: $employeeId"))
            }

            val clients = clientRepository.findByEmployeeId(employeeId)
            val count = clients.size

            clients.forEach { clientRepository.deleteById(it.id) }

            logger.info("✅ Deleted $count clients for employee: $employeeId")

            return ResponseEntity.ok(mapOf(
                "message" to "Deleted all clients for employee",
                "employeeId" to employeeId,
                "deletedCount" to count
            ))

        } catch (e: Exception) {
            logger.error("❌ Error deleting clients: ${e.message}", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "Failed to delete clients: ${e.message}"))
        }
    }
}
