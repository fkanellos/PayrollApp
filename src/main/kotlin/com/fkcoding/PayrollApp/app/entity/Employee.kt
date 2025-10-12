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
    val color: String? = null,

    // Supervision configuration
    @Column(name = "supervision_enabled")
    val supervisionEnabled: Boolean = false,

    @Column(name = "supervision_price")
    val supervisionPrice: Double? = null,

    @Column(name = "supervision_employee_price")
    val supervisionEmployeePrice: Double? = null,

    @Column(name = "supervision_company_price")
    val supervisionCompanyPrice: Double? = null
) {
    constructor() : this("", "", "", "", null, false, null, null, null)
}
