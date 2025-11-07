package com.fkcoding.PayrollApp.app.entity

/**
 * Employee entity
 * Loaded από Excel (in-memory)
 */
data class Employee(
    val id: String,
    val name: String,
    val email: String,
    val calendarId: String,
    val sheetName: String = "",
    val supervisionPrice: Double = 0.0,
)