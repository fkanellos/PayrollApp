package com.fkcoding.PayrollApp.app.entity

import jakarta.persistence.*

/**
 * 👥 Client Entity - JPA
 */
@Entity
@Table(
    name = "clients",
    indexes = [
        Index(name = "idx_employee_id", columnList = "employee_id")
    ]
)
data class Client(
    @Id
    @Column(name = "id", nullable = false)
    val id: Long,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "price")
    val price: Double = 0.0,

    @Column(name = "employee_price")
    val employeePrice: Double = 0.0,

    @Column(name = "company_price")
    val companyPrice: Double = 0.0,

    @Column(name = "employee_id", nullable = false)
    val employeeId: String,

    @Column(name = "pending_payment")
    val pendingPayment: Boolean = false
) {
    // No-arg constructor for JPA
    constructor() : this(0L, "", 0.0, 0.0, 0.0, "", false)
}