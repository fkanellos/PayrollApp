package com.fkcoding.PayrollApp.app.repository

import com.fkcoding.PayrollApp.app.entity.Employee
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * 💾 Employee Repository - JPA
 * Proper database persistence!
 */
@Repository
interface EmployeeRepository : JpaRepository<Employee, String> {
    // JpaRepository provides:
    // - findAll()
    // - findById(id)
    // - save(entity)
    // - delete(entity)
    // - count()
    // etc.

    // Custom queries if needed:
    fun findByName(name: String): Employee?
    fun findByCalendarId(calendarId: String): Employee?
}