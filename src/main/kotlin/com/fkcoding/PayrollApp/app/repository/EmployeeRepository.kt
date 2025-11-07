package com.fkcoding.PayrollApp.app.repository

import com.fkcoding.PayrollApp.app.entity.Employee
import com.fkcoding.PayrollApp.app.service.ExcelDataService
import org.springframework.stereotype.Repository
import java.util.Optional

/**
 * Employee Repository
 * Fetches data από το ExcelDataService (in-memory)
 */
@Repository
class EmployeeRepository(
    private val excelDataService: ExcelDataService
) {

    /**
     * Find all employees
     */
    fun findAll(): List<Employee> {
        return excelDataService.getAllEmployees()
    }

    /**
     * Find employee by ID
     */
    fun findById(id: String): Optional<Employee> {
        val employee = excelDataService.getEmployeeById(id)
        return if (employee != null) {
            Optional.of(employee)
        } else {
            Optional.empty()
        }
    }

    /**
     * Count employees
     */
    fun count(): Long {
        return excelDataService.getAllEmployees().size.toLong()
    }

    /**
     * Refresh data από Excel
     */
    fun refresh() {
        excelDataService.refresh()
    }
}