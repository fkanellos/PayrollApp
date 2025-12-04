package com.fkcoding.PayrollApp.app.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

/**
 * DTO for updating an existing client
 * Client ID comes from URL path parameter
 */
data class UpdateClientRequest(
    @field:NotBlank(message = "Client name is required")
    val name: String,

    @field:Positive(message = "Price must be greater than zero")
    val price: Double,

    @field:Positive(message = "Employee price must be greater than zero")
    val employeePrice: Double,

    @field:Positive(message = "Company price must be greater than zero")
    val companyPrice: Double,

    @field:NotBlank(message = "Employee ID is required")
    val employeeId: String,

    val pendingPayment: Boolean = false
) {
    /**
     * Validate that price split is correct
     * employeePrice + companyPrice should equal price
     */
    fun validatePriceSplit(): String? {
        val sum = employeePrice + companyPrice
        val tolerance = 0.01 // Allow 1 cent difference for rounding

        return if (kotlin.math.abs(sum - price) > tolerance) {
            "Price split is invalid: employeePrice ($employeePrice) + companyPrice ($companyPrice) = $sum, but price is $price"
        } else {
            null
        }
    }
}
