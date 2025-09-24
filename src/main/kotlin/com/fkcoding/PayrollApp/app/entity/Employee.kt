package com.fkcoding.PayrollApp.app.entity

import jakarta.persistence.*

@Entity
@Table(name = "employees")
data class Employee(
    @Id
    val id: String,
    
    @Column(nullable = false)
    val name: String,
    
    @Column(nullable = false, unique = true)
    val email: String,
    
    @Column(nullable = false)
    val calendarId: String,
    
    @Column
    val color: String? = null
) {
    constructor() : this("", "", "", "", null)
}
