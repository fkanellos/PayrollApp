package com.fkcoding.PayrollApp.app.repository

import com.fkcoding.PayrollApp.app.entity.Employee
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EmployeeRepository : JpaRepository<Employee, String>
