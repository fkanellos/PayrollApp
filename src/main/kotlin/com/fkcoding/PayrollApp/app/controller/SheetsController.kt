package com.fkcoding.PayrollApp.app.controller

import com.fkcoding.PayrollApp.app.service.GoogleSheetsService
import com.fkcoding.PayrollApp.app.service.SheetsSyncService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST Controller για Google Sheets operations - 3 Tab Structure
 */
@RestController
@RequestMapping("/api/sheets")
@CrossOrigin(origins = ["http://localhost:3000", "*"])
class SheetsController(
    private val sheetsService: GoogleSheetsService,
    private val sheetsSyncService: SheetsSyncService
) {

    /**
     * 🧪 Test connection με το Google Sheet
     * GET /api/sheets/test
     */
    @GetMapping("/test")
    fun testConnection(): ResponseEntity<Map<String, Any>> {
        return try {
            val result = sheetsService.testConnection()
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf(
                    "status" to "error",
                    "message" to (e.message ?: "Unknown error")
                )
            )
        }
    }

    /**
     * 🆕 Initialize όλα τα sheets (MASTER + DETAILS + STATS)
     * POST /api/sheets/initialize
     */
    @PostMapping("/initialize")
    fun initializeSheets(): ResponseEntity<Map<String, Any>> {
        return try {
            val result = sheetsSyncService.initializeSheets()
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf(
                    "status" to "error",
                    "message" to (e.message ?: "Error initializing sheets")
                )
            )
        }
    }

    /**
     * 🧪 Write sample data για testing
     * POST /api/sheets/sample-data
     */
    @PostMapping("/sample-data")
    fun writeSampleData(): ResponseEntity<Map<String, Any>> {
        return try {
            val result = sheetsSyncService.writeSampleData()
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf(
                    "status" to "error",
                    "message" to (e.message ?: "Error writing sample data")
                )
            )
        }
    }

    /**
     * 📊 Get spreadsheet info
     * GET /api/sheets/info
     */
    @GetMapping("/info")
    fun getSpreadsheetInfo(): ResponseEntity<Map<String, Any>> {
        return try {
            val info = sheetsService.testConnection()
            ResponseEntity.ok(info)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(
                mapOf(
                    "status" to "error",
                    "message" to (e.message ?: "Error getting spreadsheet info")
                )
            )
        }
    }
}