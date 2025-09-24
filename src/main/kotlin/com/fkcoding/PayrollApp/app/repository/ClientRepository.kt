package com.fkcoding.PayrollApp.app.repository

import com.fkcoding.PayrollApp.app.entity.Client
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ClientRepository : JpaRepository<Client, Long> {
    fun findByEmployeeId(employeeId: String): List<Client>
}
