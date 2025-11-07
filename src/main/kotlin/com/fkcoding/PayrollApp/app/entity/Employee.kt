package com.fkcoding.PayrollApp.app.entity

import jakarta.persistence.*

/**
 * 👤 Employee Entity - JPA
 */
@Entity
@Table(name = "employees")
data class Employee(
    @Id
    @Column(name = "id", nullable = false)
    val id: String,

    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "email")
    val email: String = "",

    @Column(name = "calendar_id", nullable = false)
    val calendarId: String = "",

    @Column(name = "color")
    val color: String = "",

    @Column(name = "sheet_name", nullable = false)
    val sheetName: String,

    @Column(name = "supervision_price")
    val supervisionPrice: Double = 0.0
) {
    // No-arg constructor for JPA
    constructor() : this("", "", "", "", "", "", 0.0)
}