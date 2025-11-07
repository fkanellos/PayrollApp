package com.fkcoding.PayrollApp.app.controller

import com.fkcoding.PayrollApp.app.entity.Client
import com.fkcoding.PayrollApp.app.entity.Employee
import com.fkcoding.PayrollApp.app.repository.EmployeeRepository
import com.fkcoding.PayrollApp.app.repository.ClientRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/employees")
class EmployeeController(
    private val employeeRepository: EmployeeRepository,
    private val clientRepository: ClientRepository
) {

    @GetMapping
    fun getAllEmployees(): List<Employee> {
        return employeeRepository.findAll()
    }

    @GetMapping("/{id}")
    fun getEmployee(@PathVariable id: String): Employee? {
        return employeeRepository.findById(id).orElse(null)
    }

    @GetMapping("/{id}/clients")
    fun getEmployeeClients(@PathVariable id: String): List<Client> {
        return clientRepository.findByEmployeeId(id)
    }
}
