package com.fkcoding.PayrollApp.app.repository

import com.fkcoding.PayrollApp.app.entity.Client
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 💾 Client Repository - JPA
 * Proper database persistence!
 */
@Repository
interface ClientRepository : JpaRepository<Client, Long> {
    // JpaRepository provides:
    // - findAll()
    // - findById(id)
    // - save(entity)
    // - delete(entity)
    // - count()
    // etc.

    // Custom queries:
    fun findByEmployeeId(employeeId: String): List<Client>
    fun findByName(name: String): Client?
    fun findByEmployeeIdAndName(employeeId: String, name: String): Client?
}