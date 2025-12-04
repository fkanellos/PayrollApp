package com.fkcoding.PayrollApp.app.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

/**
 * DTO for creating a new employee
 */
data class CreateEmployeeRequest(
    @field:NotBlank(message = "Employee ID is required")
    val id: String,

    @field:NotBlank(message = "Employee name is required")
    val name: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Calendar ID is required")
    val calendarId: String,

    @field:NotBlank(message = "Color is required")
    val color: String,

    @field:NotBlank(message = "Sheet name is required")
    val sheetName: String,

    @field:PositiveOrZero(message = "Supervision price must be zero or positive")
    val supervisionPrice: Double = 0.0
)
