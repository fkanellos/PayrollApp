package com.fkcoding.PayrollApp.app.controller

import com.fkcoding.PayrollApp.app.service.DatabaseSyncService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 💾 Database Sync Controller
 * Endpoints για database sync operations
 */
@RestController
@RequestMapping("/api/db")
@CrossOrigin(origins = ["http://localhost:3000", "*"])
class DatabaseSyncController(
    private val databaseSyncService: DatabaseSyncService
) {

    /**
     * 📊 Get sync status
     * GET /api/db/status
     */
    @GetMapping("/status")
    fun getSyncStatus(): ResponseEntity<Map<String, Any>> {
        return try {
            val stats = databaseSyncService.getSyncStats()
            ResponseEntity.ok(mapOf(
                "status" to "success",
                "data" to stats
            ))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf(
                "status" to "error",
                "message" to (e.message ?: "Unknown error")
            ))
        }
    }

    /**
     * 🔄 Manual sync from Excel
     * POST /api/db/sync
     */
    @PostMapping("/sync")
    fun manualSync(): ResponseEntity<Map<String, Any>> {
        return try {
            val result = databaseSyncService.refreshFromExcel()

            ResponseEntity.ok(mapOf(
                "status" to "success",
                "message" to "Database synced successfully",
                "data" to mapOf(
                    "employeesInserted" to result.employeesInserted,
                    "employeesUpdated" to result.employeesUpdated,
                    "clientsInserted" to result.clientsInserted,
                    "clientsUpdated" to result.clientsUpdated,
                    "durationMs" to result.durationMs
                )
            ))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf(
                "status" to "error",
                "message" to (e.message ?: "Sync failed")
            ))
        }
    }

    /**
     * ℹ️ Get database info
     * GET /api/db/info
     */
    @GetMapping("/info")
    fun getDatabaseInfo(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "status" to "success",
            "database" to mapOf(
                "type" to "H2 (In-Memory)",
                "url" to "jdbc:h2:mem:payrolldb",
                "note" to "Data is cleared on restart"
            ),
            "endpoints" to listOf(
                mapOf(
                    "method" to "GET",
                    "path" to "/api/db/status",
                    "description" to "Check sync status"
                ),
                mapOf(
                    "method" to "POST",
                    "path" to "/api/db/sync",
                    "description" to "Manual sync from Excel"
                ),
                mapOf(
                    "method" to "GET",
                    "path" to "/api/db/info",
                    "description" to "Database info"
                )
            )
        ))
    }
}