package com.fkcoding.PayrollApp.app.entity

/**
 * Client entity
 * Loaded από Excel (in-memory)
 */
data class Client(
    val id: Long,
    val name: String,
    val price: Double,
    val employeePrice: Double,
    val companyPrice: Double,
    val employeeId: String,
    val pendingPayment: Boolean = false
)