package com.fkcoding.PayrollApp.app.service

import com.fkcoding.PayrollApp.app.entity.Client
import com.fkcoding.PayrollApp.app.entity.Employee
import com.fkcoding.PayrollApp.app.repository.ClientRepository
import com.fkcoding.PayrollApp.app.repository.EmployeeRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 💾 Database Sync Service
 * Syncs data from Excel to Database
 */
@Service
class DatabaseSyncService(
    private val excelDataService: ExcelDataService,
    private val employeeRepository: EmployeeRepository,
    private val clientRepository: ClientRepository
) {

    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseSyncService::class.java)
    }

    /**
     * 🔄 Auto-sync on startup
     */
    @PostConstruct
    fun syncOnStartup() {
        try {
            logger.info("💾 Syncing Excel data to database...")
            syncEmployeesAndClients()
            logger.info("✅ Database sync completed!")
        } catch (e: Exception) {
            logger.error("❌ Database sync failed: ${e.message}", e)
        }
    }

    /**
     * 💾 Sync employees and clients to database
     */
    @Transactional
    fun syncEmployeesAndClients(): SyncResult {
        val startTime = System.currentTimeMillis()

        // Get data from Excel
        val employees = excelDataService.getAllEmployees()
        val clients = excelDataService.getAllClients()

        logger.info("📊 Syncing ${employees.size} employees and ${clients.size} clients...")

        var employeesSaved = 0
        var clientsSaved = 0
        var employeesUpdated = 0
        var clientsUpdated = 0

        // Sync employees
        employees.forEach { employee ->
            try {
                val existing = employeeRepository.findById(employee.id)

                if (existing.isPresent) {
                    // Update
                    val updated = existing.get().copy(
                        name = employee.name,
                        email = employee.email,
                        calendarId = employee.calendarId,
                        color = employee.color,
                        sheetName = employee.sheetName,
                        supervisionPrice = employee.supervisionPrice
                    )
                    employeeRepository.save(updated)
                    employeesUpdated++
                } else {
                    // Insert
                    employeeRepository.save(employee)
                    employeesSaved++
                }
            } catch (e: Exception) {
                logger.warn("⚠️ Error syncing employee ${employee.name}: ${e.message}")
            }
        }

        // Sync clients
        clients.forEach { client ->
            try {
                val existing = clientRepository.findById(client.id)

                if (existing.isPresent) {
                    // Update
                    val updated = existing.get().copy(
                        name = client.name,
                        price = client.price,
                        employeePrice = client.employeePrice,
                        companyPrice = client.companyPrice,
                        employeeId = client.employeeId,
                        pendingPayment = client.pendingPayment
                    )
                    clientRepository.save(updated)
                    clientsUpdated++
                } else {
                    // Insert
                    clientRepository.save(client)
                    clientsSaved++
                }
            } catch (e: Exception) {
                logger.warn("⚠️ Error syncing client ${client.name}: ${e.message}")
            }
        }

        val duration = System.currentTimeMillis() - startTime

        logger.info("✅ Sync completed in ${duration}ms")
        logger.info("   Employees: $employeesSaved new, $employeesUpdated updated")
        logger.info("   Clients: $clientsSaved new, $clientsUpdated updated")

        return SyncResult(
            employeesInserted = employeesSaved,
            employeesUpdated = employeesUpdated,
            clientsInserted = clientsSaved,
            clientsUpdated = clientsUpdated,
            durationMs = duration
        )
    }

    /**
     * 🔄 Manual refresh from Excel
     */
    fun refreshFromExcel(): SyncResult {
        logger.info("🔄 Manual refresh requested...")
        excelDataService.refresh()
        return syncEmployeesAndClients()
    }

    /**
     * 📊 Get sync stats
     */
    fun getSyncStats(): Map<String, Any> {
        val dbEmployees = employeeRepository.count()
        val dbClients = clientRepository.count()
        val excelEmployees = excelDataService.getAllEmployees().size
        val excelClients = excelDataService.getAllClients().size

        return mapOf(
            "database" to mapOf(
                "employees" to dbEmployees,
                "clients" to dbClients
            ),
            "excel" to mapOf(
                "employees" to excelEmployees,
                "clients" to excelClients
            ),
            "inSync" to (dbEmployees == excelEmployees.toLong() && dbClients == excelClients.toLong()),
            "lastLoadTime" to excelDataService.getLastLoadTime()
        )
    }
}

/**
 * 📊 Sync result data class
 */
data class SyncResult(
    val employeesInserted: Int,
    val employeesUpdated: Int,
    val clientsInserted: Int,
    val clientsUpdated: Int,
    val durationMs: Long
)