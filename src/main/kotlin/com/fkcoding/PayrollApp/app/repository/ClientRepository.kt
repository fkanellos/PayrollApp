package com.fkcoding.PayrollApp.app.repository

import com.fkcoding.PayrollApp.app.entity.Client
import com.fkcoding.PayrollApp.app.service.ExcelDataService
import org.springframework.stereotype.Repository

/**
 * Client Repository
 * Fetches data από το ExcelDataService (in-memory)
 */
@Repository
class ClientRepository(
    private val excelDataService: ExcelDataService
) {

    /**
     * Find all clients
     */
    fun findAll(): List<Client> {
        return excelDataService.getAllClients()
    }

    /**
     * Find clients by employee ID
     */
    fun findByEmployeeId(employeeId: String): List<Client> {
        return excelDataService.getClientsByEmployeeId(employeeId)
    }

    /**
     * Count clients
     */
    fun count(): Long {
        return excelDataService.getAllClients().size.toLong()
    }

    /**
     * Refresh data από Excel
     */
    fun refresh() {
        excelDataService.refresh()
    }
}