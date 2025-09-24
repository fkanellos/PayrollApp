package com.fkcoding.PayrollApp.app.entity

import jakarta.persistence.*

@Entity
@Table(name = "clients")
data class Client(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(nullable = false)
    val name: String,
    
    @Column(nullable = false)
    val price: Double,
    
    @Column(nullable = false)
    val employeePrice: Double,
    
    @Column(nullable = false)
    val companyPrice: Double,
    
    @Column(nullable = false)
    val employeeId: String,
    
    @Column(nullable = false)
    val pendingPayment: Boolean = false
) {
    constructor() : this(0, "", 0.0, 0.0, 0.0, "", false)
}
