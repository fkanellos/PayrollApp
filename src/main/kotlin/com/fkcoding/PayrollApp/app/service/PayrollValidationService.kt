package com.fkcoding.PayrollApp.app.service

import org.springframework.stereotype.Service

/**
 * Validation Service για Payroll Data
 * Option B: Totals Match + No Negatives
 */
@Service
class PayrollValidationService {

    /**
     * Κύριος validation check - επιστρέφει ValidationResult
     */
    fun validatePayrollReport(report: PayrollReport): ValidationResult {
        // Check 1: Totals Match
        val totalsCheck = validateTotalsMatch(report)
        if (!totalsCheck.isValid) {
            return totalsCheck
        }

        // Check 2: No Negatives
        val negativesCheck = validateNoNegatives(report)
        if (!negativesCheck.isValid) {
            return negativesCheck
        }

        // All checks passed
        return ValidationResult.success()
    }

    /**
     * Check 1: Άθροισμα client details = master totals
     */
    private fun validateTotalsMatch(report: PayrollReport): ValidationResult {
        val calculatedSessions = report.entries.sumOf { it.sessionsCount }
        val calculatedRevenue = report.entries.sumOf { it.totalRevenue }
        val calculatedEmployeeEarnings = report.entries.sumOf { it.employeeEarnings }
        val calculatedCompanyEarnings = report.entries.sumOf { it.companyEarnings }

        val errors = mutableListOf<String>()

        if (calculatedSessions != report.totalSessions) {
            errors.add("Sessions mismatch: Master=${report.totalSessions}, Sum=$calculatedSessions")
        }

        if (!areDoublesEqual(calculatedRevenue, report.totalRevenue)) {
            errors.add("Revenue mismatch: Master=€${report.totalRevenue}, Sum=€$calculatedRevenue")
        }

        if (!areDoublesEqual(calculatedEmployeeEarnings, report.totalEmployeeEarnings)) {
            errors.add("Employee earnings mismatch: Master=€${report.totalEmployeeEarnings}, Sum=€$calculatedEmployeeEarnings")
        }

        if (!areDoublesEqual(calculatedCompanyEarnings, report.totalCompanyEarnings)) {
            errors.add("Company earnings mismatch: Master=€${report.totalCompanyEarnings}, Sum=€$calculatedCompanyEarnings")
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(
                "Totals Mismatch",
                errors.joinToString("\n")
            )
        }
    }

    /**
     * Check 2: Όχι αρνητικές τιμές
     */
    private fun validateNoNegatives(report: PayrollReport): ValidationResult {
        val errors = mutableListOf<String>()

        // Check master totals
        if (report.totalSessions < 0) {
            errors.add("Total sessions is negative: ${report.totalSessions}")
        }
        if (report.totalRevenue < 0) {
            errors.add("Total revenue is negative: €${report.totalRevenue}")
        }
        if (report.totalEmployeeEarnings < 0) {
            errors.add("Employee earnings is negative: €${report.totalEmployeeEarnings}")
        }
        if (report.totalCompanyEarnings < 0) {
            errors.add("Company earnings is negative: €${report.totalCompanyEarnings}")
        }

        // Check client details
        report.entries.forEachIndexed { index, entry ->
            if (entry.sessionsCount < 0) {
                errors.add("Client '${entry.clientName}' has negative sessions: ${entry.sessionsCount}")
            }
            if (entry.totalRevenue < 0) {
                errors.add("Client '${entry.clientName}' has negative revenue: €${entry.totalRevenue}")
            }
            if (entry.employeeEarnings < 0) {
                errors.add("Client '${entry.clientName}' has negative employee earnings: €${entry.employeeEarnings}")
            }
            if (entry.companyEarnings < 0) {
                errors.add("Client '${entry.clientName}' has negative company earnings: €${entry.companyEarnings}")
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(
                "Negative Values Detected",
                errors.joinToString("\n")
            )
        }
    }

    /**
     * Helper: Σύγκριση doubles με tolerance (λόγω floating point precision)
     */
    private fun areDoublesEqual(a: Double, b: Double, tolerance: Double = 0.01): Boolean {
        return kotlin.math.abs(a - b) < tolerance
    }
}

/**
 * Validation Result wrapper
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorType: String? = null,
    val errorDetails: String? = null
) {
    companion object {
        fun success() = ValidationResult(isValid = true)

        fun failure(errorType: String, errorDetails: String) =
            ValidationResult(isValid = false, errorType = errorType, errorDetails = errorDetails)
    }
}