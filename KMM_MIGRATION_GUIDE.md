# PayrollApp - KMM Migration Guide

## ΟΔΗΓΙΕΣ ΓΙΑ ΤΟ ΑΛΛΟ SESSION

Αυτό το document περιέχει **όλο τον κώδικα και τις οδηγίες** για να μεταφέρεις το backend logic στο KMM project.

**ΣΤΟΧΟΣ:** Ένα Desktop App (EXE) που περιέχει τα πάντα - χωρίς ξεχωριστό server.

---

## ΠΕΡΙΕΧΟΜΕΝΑ

1. [Project Structure](#1-project-structure)
2. [Dependencies (build.gradle.kts)](#2-dependencies)
3. [Models (commonMain)](#3-models)
4. [Services (commonMain)](#4-services)
5. [Repository Interfaces (commonMain)](#5-repository-interfaces)
6. [SQLDelight Setup](#6-sqldelight-setup)
7. [Desktop Implementation (desktopMain)](#7-desktop-implementation)
8. [Google Calendar Integration](#8-google-calendar-integration)
9. [KSafe για Encryption](#9-ksafe-για-encryption)
10. [Step-by-Step Migration](#10-step-by-step-migration)

---

## 1. PROJECT STRUCTURE

Δημιούργησε αυτή τη δομή στο KMM project:

```
your-kmm-project/
├── shared/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/
│       │       └── com/fkcoding/payroll/
│       │           ├── model/
│       │           │   ├── Employee.kt
│       │           │   ├── Client.kt
│       │           │   ├── CalendarEvent.kt
│       │           │   └── PayrollModels.kt
│       │           ├── service/
│       │           │   ├── PayrollCalculationService.kt
│       │           │   └── ClientMatchingService.kt
│       │           ├── repository/
│       │           │   ├── EmployeeRepository.kt
│       │           │   ├── ClientRepository.kt
│       │           │   └── CalendarRepository.kt
│       │           └── util/
│       │               └── TextUtils.kt
│       │
│       ├── desktopMain/
│       │   └── kotlin/
│       │       └── com/fkcoding/payroll/
│       │           ├── repository/
│       │           │   ├── SqlDelightEmployeeRepository.kt
│       │           │   ├── SqlDelightClientRepository.kt
│       │           │   └── GoogleCalendarRepository.kt
│       │           ├── google/
│       │           │   ├── GoogleCalendarService.kt
│       │           │   └── GoogleCredentialProvider.kt
│       │           └── database/
│       │               └── DriverFactory.kt
│       │
│       └── commonMain/
│           └── sqldelight/
│               └── com/fkcoding/payroll/
│                   └── PayrollDatabase.sq
│
├── desktopApp/
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       └── Main.kt
│
└── settings.gradle.kts
```

---

## 2. DEPENDENCIES

### shared/build.gradle.kts

```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight") version "2.0.1"
}

kotlin {
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

                // DateTime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

                // KSafe for encryption
                implementation("eu.anifantakis:ksafe:1.2.0")
            }
        }

        val desktopMain by getting {
            dependencies {
                // SQLDelight Desktop Driver
                implementation("app.cash.sqldelight:sqlite-driver:2.0.1")

                // Google APIs (JVM only)
                implementation("com.google.api-client:google-api-client:2.2.0")
                implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
                implementation("com.google.apis:google-api-services-calendar:v3-rev20231123-2.0.0")
                implementation("com.google.apis:google-api-services-sheets:v4-rev20231117-2.0.0")
                implementation("com.google.apis:google-api-services-drive:v3-rev20231128-2.0.0")
            }
        }
    }
}

sqldelight {
    databases {
        create("PayrollDatabase") {
            packageName.set("com.fkcoding.payroll.database")
        }
    }
}
```

### desktopApp/build.gradle.kts

```kotlin
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // PDF Generation (optional)
    implementation("com.itextpdf:itext7-core:7.2.5")
}

compose.desktop {
    application {
        mainClass = "com.fkcoding.payroll.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "PayrollApp"
            packageVersion = "1.0.0"
        }
    }
}
```

---

## 3. MODELS

### shared/src/commonMain/kotlin/com/fkcoding/payroll/model/Employee.kt

```kotlin
package com.fkcoding.payroll.model

import kotlinx.serialization.Serializable

/**
 * Employee entity
 * Represents a therapist/employee in the payroll system
 */
@Serializable
data class Employee(
    val id: String,
    val name: String,
    val email: String = "",
    val calendarId: String = "",
    val color: String = "",
    val sheetName: String = "",
    val supervisionPrice: Double = 0.0
) {
    companion object {
        fun empty() = Employee(
            id = "",
            name = "",
            email = "",
            calendarId = "",
            color = "",
            sheetName = "",
            supervisionPrice = 0.0
        )
    }
}
```

### shared/src/commonMain/kotlin/com/fkcoding/payroll/model/Client.kt

```kotlin
package com.fkcoding.payroll.model

import kotlinx.serialization.Serializable

/**
 * Client entity
 * Represents a client/patient linked to an employee
 */
@Serializable
data class Client(
    val id: Long = 0,
    val name: String,
    val price: Double = 0.0,
    val employeePrice: Double = 0.0,
    val companyPrice: Double = 0.0,
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

    companion object {
        fun empty(employeeId: String) = Client(
            id = 0,
            name = "",
            price = 0.0,
            employeePrice = 0.0,
            companyPrice = 0.0,
            employeeId = employeeId,
            pendingPayment = false
        )
    }
}
```

### shared/src/commonMain/kotlin/com/fkcoding/payroll/model/CalendarEvent.kt

```kotlin
package com.fkcoding.payroll.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * Calendar Event from Google Calendar
 * Represents a therapy session
 */
@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val colorId: String? = null,
    val isCancelled: Boolean = false,
    val isPendingPayment: Boolean = false,
    val attendees: List<String> = emptyList()
)

/**
 * Color IDs for cancellation detection
 */
object CalendarColors {
    const val GREY_CANCELLED = "8"   // Cancelled but client will pay next time
    const val RED_CANCELLED = "11"   // Cancelled, should NOT be paid
}
```

### shared/src/commonMain/kotlin/com/fkcoding/payroll/model/PayrollModels.kt

```kotlin
package com.fkcoding.payroll.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * Single entry in payroll report (one client's sessions)
 */
@Serializable
data class PayrollEntry(
    val clientName: String,
    val clientPrice: Double,
    val employeePrice: Double,
    val companyPrice: Double,
    val sessionsCount: Int,
    val totalRevenue: Double,
    val employeeEarnings: Double,
    val companyEarnings: Double
)

/**
 * Complete payroll report for an employee
 */
@Serializable
data class PayrollReport(
    val employee: Employee,
    val periodStart: LocalDateTime,
    val periodEnd: LocalDateTime,
    val entries: List<PayrollEntry>,
    val totalSessions: Int,
    val totalRevenue: Double,
    val totalEmployeeEarnings: Double,
    val totalCompanyEarnings: Double,
    val generatedAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
)

/**
 * Configuration for supervision sessions
 */
@Serializable
data class SupervisionConfig(
    val enabled: Boolean,
    val price: Double,
    val employeePrice: Double,
    val companyPrice: Double,
    val keywords: List<String> = listOf("Εποπτεία", "Supervision")
)
```

---

## 4. SERVICES

### shared/src/commonMain/kotlin/com/fkcoding/payroll/service/ClientMatchingService.kt

```kotlin
package com.fkcoding.payroll.service

/**
 * Client Matching Service
 * Handles client name matching logic for calendar events
 *
 * Matching strategies (in order):
 * 1. Special keywords match (if provided)
 * 2. Full name match (exact substring)
 * 3. Reversed name match (e.g., "John Doe" -> "Doe John")
 * 4. Surname match (word boundary, min 4 chars)
 * 5. First name match (word boundary, min 4 chars)
 * 6. Dash-separated name parts (e.g., "John - Γιάννης")
 */
class ClientMatchingService {

    /**
     * Find client matches for a given event title
     *
     * @param title Event title to match against
     * @param clientNames List of client names to search
     * @param specialKeywords Special keywords that override normal matching
     * @return List of matching client names
     */
    fun findClientMatches(
        title: String,
        clientNames: List<String>,
        specialKeywords: List<String> = emptyList()
    ): List<String> {
        if (title.isBlank()) return emptyList()

        // Normalize title (lowercase + remove Greek accents)
        val titleLower = normalizeText(title)
        val matches = mutableListOf<String>()

        // Strategy 1: Special keywords (e.g., "supervision")
        for (keyword in specialKeywords) {
            if (normalizeText(keyword) in titleLower) {
                matches.add(keyword)
                return matches
            }
        }

        // Match against client names
        for (clientName in clientNames) {
            if (clientName.isBlank()) continue

            val clientLower = normalizeText(clientName)
            val nameParts = clientLower.split(" ").filter { it.isNotBlank() }

            // Strategy 2: Full name match
            if (clientLower in titleLower) {
                matches.add(clientName)
                continue
            }

            // If single name, try direct match
            if (nameParts.size < 2) {
                if (nameParts.first() in titleLower) {
                    matches.add(clientName)
                }
                continue
            }

            // Strategy 3: Reversed name match
            val reversedName = "${nameParts.last()} ${nameParts.first()}"
            if (reversedName in titleLower) {
                matches.add(clientName)
                continue
            }

            // Strategy 4: Surname match (min 4 chars)
            val surname = nameParts.last()
            if (surname.length > 3) {
                val regex = "\\b${Regex.escape(surname)}\\b".toRegex()
                if (regex.find(titleLower) != null) {
                    matches.add(clientName)
                    continue
                }
            }

            // Strategy 5: First name match (min 4 chars)
            val firstName = nameParts.first()
            if (firstName.length > 3) {
                val regex = "\\b${Regex.escape(firstName)}\\b".toRegex()
                if (regex.find(titleLower) != null) {
                    matches.add(clientName)
                    continue
                }
            }

            // Strategy 6: Dash-separated names (e.g., "Ndrekaj Ornela - Ντρεκαι Ορνελα")
            if ("-" in clientName) {
                val parts = clientName.split("-").map { normalizeText(it.trim()) }
                for (part in parts) {
                    if (part in titleLower) {
                        matches.add(clientName)
                        break
                    }
                }
            }
        }

        return matches
    }

    /**
     * Normalize text for matching (lowercase + remove Greek accents)
     */
    private fun normalizeText(text: String): String {
        return text.lowercase().trim()
            .replace("ά", "α").replace("έ", "ε")
            .replace("ή", "η").replace("ί", "ι")
            .replace("ό", "ο").replace("ύ", "υ")
            .replace("ώ", "ω").replace("ΐ", "ι")
            .replace("ΰ", "υ")
    }
}
```

### shared/src/commonMain/kotlin/com/fkcoding/payroll/service/PayrollCalculationService.kt

```kotlin
package com.fkcoding.payroll.service

import com.fkcoding.payroll.model.*
import kotlinx.datetime.LocalDateTime

/**
 * Payroll Calculation Service
 * Core business logic for calculating employee payroll
 */
class PayrollCalculationService {

    /**
     * Calculate payroll for an employee
     *
     * @param employee The employee to calculate payroll for
     * @param clients List of clients belonging to this employee
     * @param clientEvents Map of client name -> list of calendar events
     * @param periodStart Start of the payroll period
     * @param periodEnd End of the payroll period
     * @param supervisionConfig Optional configuration for supervision sessions
     * @return Complete payroll report
     */
    fun calculatePayroll(
        employee: Employee,
        clients: List<Client>,
        clientEvents: Map<String, List<CalendarEvent>>,
        periodStart: LocalDateTime,
        periodEnd: LocalDateTime,
        supervisionConfig: SupervisionConfig? = null
    ): PayrollReport {

        val entries = mutableListOf<PayrollEntry>()
        var totalSessions = 0
        var totalRevenue = 0.0
        var totalEmployeeEarnings = 0.0
        var totalCompanyEarnings = 0.0

        val clientLookup = clients.associateBy { it.name }

        // 1. Process client events
        clientEvents.forEach { (clientName, events) ->
            // Skip if this is the supervision keyword
            if (supervisionConfig != null && clientName in supervisionConfig.keywords) {
                return@forEach // Handle separately below
            }

            val client = clientLookup[clientName] ?: return@forEach

            val validEvents = events.filter { event ->
                isEventInPeriod(event, periodStart, periodEnd) &&
                        (!event.isCancelled || event.isPendingPayment)
            }

            if (validEvents.isNotEmpty()) {
                val sessionsCount = validEvents.size
                val clientRevenue = sessionsCount * client.price
                val employeeEarnings = sessionsCount * client.employeePrice
                val companyEarnings = sessionsCount * client.companyPrice

                val entry = PayrollEntry(
                    clientName = clientName,
                    clientPrice = client.price,
                    employeePrice = client.employeePrice,
                    companyPrice = client.companyPrice,
                    sessionsCount = sessionsCount,
                    totalRevenue = clientRevenue,
                    employeeEarnings = employeeEarnings,
                    companyEarnings = companyEarnings
                )

                entries.add(entry)
                totalSessions += sessionsCount
                totalRevenue += clientRevenue
                totalEmployeeEarnings += employeeEarnings
                totalCompanyEarnings += companyEarnings
            }
        }

        // 2. Process supervision sessions
        if (supervisionConfig != null && supervisionConfig.enabled) {
            supervisionConfig.keywords.forEach { keyword ->
                val supervisionEvents = clientEvents[keyword] ?: emptyList()

                val validSupervisionEvents = supervisionEvents.filter { event ->
                    isEventInPeriod(event, periodStart, periodEnd) &&
                            (!event.isCancelled || event.isPendingPayment)
                }

                if (validSupervisionEvents.isNotEmpty()) {
                    val sessionsCount = validSupervisionEvents.size
                    val clientRevenue = sessionsCount * supervisionConfig.price
                    val employeeEarnings = sessionsCount * supervisionConfig.employeePrice
                    val companyEarnings = sessionsCount * supervisionConfig.companyPrice

                    val entry = PayrollEntry(
                        clientName = "Εποπτεία (Supervision)",
                        clientPrice = supervisionConfig.price,
                        employeePrice = supervisionConfig.employeePrice,
                        companyPrice = supervisionConfig.companyPrice,
                        sessionsCount = sessionsCount,
                        totalRevenue = clientRevenue,
                        employeeEarnings = employeeEarnings,
                        companyEarnings = companyEarnings
                    )

                    entries.add(entry)
                    totalSessions += sessionsCount
                    totalRevenue += clientRevenue
                    totalEmployeeEarnings += employeeEarnings
                    totalCompanyEarnings += companyEarnings
                }
            }
        }

        return PayrollReport(
            employee = employee,
            periodStart = periodStart,
            periodEnd = periodEnd,
            entries = entries,
            totalSessions = totalSessions,
            totalRevenue = totalRevenue,
            totalEmployeeEarnings = totalEmployeeEarnings,
            totalCompanyEarnings = totalCompanyEarnings
        )
    }

    /**
     * Check if event falls within the period
     */
    private fun isEventInPeriod(
        event: CalendarEvent,
        periodStart: LocalDateTime,
        periodEnd: LocalDateTime
    ): Boolean {
        return event.startTime > periodStart && event.startTime < periodEnd
    }
}
```

---

## 5. REPOSITORY INTERFACES

### shared/src/commonMain/kotlin/com/fkcoding/payroll/repository/EmployeeRepository.kt

```kotlin
package com.fkcoding.payroll.repository

import com.fkcoding.payroll.model.Employee

/**
 * Repository interface for Employee data access
 * Implementation will be platform-specific (SQLite for desktop)
 */
interface EmployeeRepository {
    suspend fun getAll(): List<Employee>
    suspend fun getById(id: String): Employee?
    suspend fun insert(employee: Employee)
    suspend fun update(employee: Employee)
    suspend fun delete(id: String)
    suspend fun deleteAll()
    suspend fun count(): Long
}
```

### shared/src/commonMain/kotlin/com/fkcoding/payroll/repository/ClientRepository.kt

```kotlin
package com.fkcoding.payroll.repository

import com.fkcoding.payroll.model.Client

/**
 * Repository interface for Client data access
 * Implementation will be platform-specific (SQLite for desktop)
 */
interface ClientRepository {
    suspend fun getAll(): List<Client>
    suspend fun getById(id: Long): Client?
    suspend fun getByEmployeeId(employeeId: String): List<Client>
    suspend fun getByName(name: String): Client?
    suspend fun getByEmployeeIdAndName(employeeId: String, name: String): Client?
    suspend fun insert(client: Client): Long
    suspend fun update(client: Client)
    suspend fun delete(id: Long)
    suspend fun deleteByEmployeeId(employeeId: String)
    suspend fun deleteAll()
    suspend fun count(): Long
}
```

### shared/src/commonMain/kotlin/com/fkcoding/payroll/repository/CalendarRepository.kt

```kotlin
package com.fkcoding.payroll.repository

import com.fkcoding.payroll.model.CalendarEvent
import kotlinx.datetime.LocalDateTime

/**
 * Repository interface for Calendar data access
 * Implementation will be platform-specific (Google Calendar API for desktop)
 */
interface CalendarRepository {
    /**
     * Get calendar events for a specific period
     */
    suspend fun getEventsForPeriod(
        calendarId: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<CalendarEvent>

    /**
     * Filter events by client names and return a map
     */
    suspend fun filterEventsByClientNames(
        events: List<CalendarEvent>,
        clientNames: List<String>,
        specialKeywords: List<String> = emptyList()
    ): Map<String, List<CalendarEvent>>

    /**
     * Get list of available calendars
     */
    suspend fun getCalendarList(): List<CalendarInfo>

    /**
     * Check if calendar service is available
     */
    fun isAvailable(): Boolean
}

/**
 * Basic calendar info
 */
data class CalendarInfo(
    val id: String,
    val name: String,
    val isPrimary: Boolean = false
)
```

---

## 6. SQLDELIGHT SETUP

### shared/src/commonMain/sqldelight/com/fkcoding/payroll/PayrollDatabase.sq

```sql
-- Employee table
CREATE TABLE Employee (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL DEFAULT '',
    calendar_id TEXT NOT NULL DEFAULT '',
    color TEXT NOT NULL DEFAULT '',
    sheet_name TEXT NOT NULL DEFAULT '',
    supervision_price REAL NOT NULL DEFAULT 0.0
);

-- Client table
CREATE TABLE Client (
    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    price REAL NOT NULL DEFAULT 0.0,
    employee_price REAL NOT NULL DEFAULT 0.0,
    company_price REAL NOT NULL DEFAULT 0.0,
    employee_id TEXT NOT NULL,
    pending_payment INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (employee_id) REFERENCES Employee(id) ON DELETE CASCADE
);

-- Index for faster lookups
CREATE INDEX idx_client_employee_id ON Client(employee_id);

-- Employee Queries
selectAllEmployees:
SELECT * FROM Employee ORDER BY name;

selectEmployeeById:
SELECT * FROM Employee WHERE id = ?;

insertEmployee:
INSERT OR REPLACE INTO Employee (id, name, email, calendar_id, color, sheet_name, supervision_price)
VALUES (?, ?, ?, ?, ?, ?, ?);

updateEmployee:
UPDATE Employee SET name = ?, email = ?, calendar_id = ?, color = ?, sheet_name = ?, supervision_price = ?
WHERE id = ?;

deleteEmployee:
DELETE FROM Employee WHERE id = ?;

deleteAllEmployees:
DELETE FROM Employee;

countEmployees:
SELECT COUNT(*) FROM Employee;

-- Client Queries
selectAllClients:
SELECT * FROM Client ORDER BY name;

selectClientById:
SELECT * FROM Client WHERE id = ?;

selectClientsByEmployeeId:
SELECT * FROM Client WHERE employee_id = ? ORDER BY name;

selectClientByName:
SELECT * FROM Client WHERE name = ? LIMIT 1;

selectClientByEmployeeIdAndName:
SELECT * FROM Client WHERE employee_id = ? AND name = ? LIMIT 1;

insertClient:
INSERT INTO Client (name, price, employee_price, company_price, employee_id, pending_payment)
VALUES (?, ?, ?, ?, ?, ?);

updateClient:
UPDATE Client SET name = ?, price = ?, employee_price = ?, company_price = ?, employee_id = ?, pending_payment = ?
WHERE id = ?;

deleteClient:
DELETE FROM Client WHERE id = ?;

deleteClientsByEmployeeId:
DELETE FROM Client WHERE employee_id = ?;

deleteAllClients:
DELETE FROM Client;

countClients:
SELECT COUNT(*) FROM Client;

lastInsertRowId:
SELECT last_insert_rowid();
```

### shared/src/desktopMain/kotlin/com/fkcoding/payroll/database/DriverFactory.kt

```kotlin
package com.fkcoding.payroll.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

object DriverFactory {

    private const val DATABASE_NAME = "payroll.db"

    fun createDriver(): SqlDriver {
        // Store in user's home directory
        val dbPath = File(System.getProperty("user.home"), ".payroll-app")
        if (!dbPath.exists()) {
            dbPath.mkdirs()
        }

        val dbFile = File(dbPath, DATABASE_NAME)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        // Create tables if they don't exist
        if (!dbFile.exists() || dbFile.length() == 0L) {
            PayrollDatabase.Schema.create(driver)
        }

        return driver
    }
}
```

---

## 7. DESKTOP IMPLEMENTATION

### shared/src/desktopMain/kotlin/com/fkcoding/payroll/repository/SqlDelightEmployeeRepository.kt

```kotlin
package com.fkcoding.payroll.repository

import com.fkcoding.payroll.database.PayrollDatabase
import com.fkcoding.payroll.model.Employee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightEmployeeRepository(
    private val database: PayrollDatabase
) : EmployeeRepository {

    private val queries = database.payrollDatabaseQueries

    override suspend fun getAll(): List<Employee> = withContext(Dispatchers.IO) {
        queries.selectAllEmployees().executeAsList().map { it.toEmployee() }
    }

    override suspend fun getById(id: String): Employee? = withContext(Dispatchers.IO) {
        queries.selectEmployeeById(id).executeAsOneOrNull()?.toEmployee()
    }

    override suspend fun insert(employee: Employee) = withContext(Dispatchers.IO) {
        queries.insertEmployee(
            id = employee.id,
            name = employee.name,
            email = employee.email,
            calendar_id = employee.calendarId,
            color = employee.color,
            sheet_name = employee.sheetName,
            supervision_price = employee.supervisionPrice
        )
    }

    override suspend fun update(employee: Employee) = withContext(Dispatchers.IO) {
        queries.updateEmployee(
            id = employee.id,
            name = employee.name,
            email = employee.email,
            calendar_id = employee.calendarId,
            color = employee.color,
            sheet_name = employee.sheetName,
            supervision_price = employee.supervisionPrice
        )
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        queries.deleteEmployee(id)
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        queries.deleteAllEmployees()
    }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        queries.countEmployees().executeAsOne()
    }

    // Extension function to convert database row to model
    private fun com.fkcoding.payroll.database.Employee.toEmployee(): Employee {
        return Employee(
            id = this.id,
            name = this.name,
            email = this.email,
            calendarId = this.calendar_id,
            color = this.color,
            sheetName = this.sheet_name,
            supervisionPrice = this.supervision_price
        )
    }
}
```

### shared/src/desktopMain/kotlin/com/fkcoding/payroll/repository/SqlDelightClientRepository.kt

```kotlin
package com.fkcoding.payroll.repository

import com.fkcoding.payroll.database.PayrollDatabase
import com.fkcoding.payroll.model.Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightClientRepository(
    private val database: PayrollDatabase
) : ClientRepository {

    private val queries = database.payrollDatabaseQueries

    override suspend fun getAll(): List<Client> = withContext(Dispatchers.IO) {
        queries.selectAllClients().executeAsList().map { it.toClient() }
    }

    override suspend fun getById(id: Long): Client? = withContext(Dispatchers.IO) {
        queries.selectClientById(id).executeAsOneOrNull()?.toClient()
    }

    override suspend fun getByEmployeeId(employeeId: String): List<Client> = withContext(Dispatchers.IO) {
        queries.selectClientsByEmployeeId(employeeId).executeAsList().map { it.toClient() }
    }

    override suspend fun getByName(name: String): Client? = withContext(Dispatchers.IO) {
        queries.selectClientByName(name).executeAsOneOrNull()?.toClient()
    }

    override suspend fun getByEmployeeIdAndName(employeeId: String, name: String): Client? = withContext(Dispatchers.IO) {
        queries.selectClientByEmployeeIdAndName(employeeId, name).executeAsOneOrNull()?.toClient()
    }

    override suspend fun insert(client: Client): Long = withContext(Dispatchers.IO) {
        queries.insertClient(
            name = client.name,
            price = client.price,
            employee_price = client.employeePrice,
            company_price = client.companyPrice,
            employee_id = client.employeeId,
            pending_payment = if (client.pendingPayment) 1L else 0L
        )
        queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun update(client: Client) = withContext(Dispatchers.IO) {
        queries.updateClient(
            id = client.id,
            name = client.name,
            price = client.price,
            employee_price = client.employeePrice,
            company_price = client.companyPrice,
            employee_id = client.employeeId,
            pending_payment = if (client.pendingPayment) 1L else 0L
        )
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        queries.deleteClient(id)
    }

    override suspend fun deleteByEmployeeId(employeeId: String) = withContext(Dispatchers.IO) {
        queries.deleteClientsByEmployeeId(employeeId)
    }

    override suspend fun deleteAll() = withContext(Dispatchers.IO) {
        queries.deleteAllClients()
    }

    override suspend fun count(): Long = withContext(Dispatchers.IO) {
        queries.countClients().executeAsOne()
    }

    // Extension function to convert database row to model
    private fun com.fkcoding.payroll.database.Client.toClient(): Client {
        return Client(
            id = this.id,
            name = this.name,
            price = this.price,
            employeePrice = this.employee_price,
            companyPrice = this.company_price,
            employeeId = this.employee_id,
            pendingPayment = this.pending_payment == 1L
        )
    }
}
```

---

## 8. GOOGLE CALENDAR INTEGRATION

### shared/src/desktopMain/kotlin/com/fkcoding/payroll/google/GoogleCalendarService.kt

```kotlin
package com.fkcoding.payroll.google

import com.fkcoding.payroll.model.CalendarColors
import com.fkcoding.payroll.model.CalendarEvent
import com.fkcoding.payroll.repository.CalendarInfo
import com.fkcoding.payroll.repository.CalendarRepository
import com.fkcoding.payroll.service.ClientMatchingService
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.ZoneId

class GoogleCalendarRepository(
    private val credentialProvider: GoogleCredentialProvider,
    private val clientMatchingService: ClientMatchingService
) : CalendarRepository {

    private var calendarService: Calendar? = null

    init {
        try {
            calendarService = credentialProvider.getCalendarService()
        } catch (e: Exception) {
            println("Failed to initialize Google Calendar: ${e.message}")
        }
    }

    override fun isAvailable(): Boolean = calendarService != null

    override suspend fun getEventsForPeriod(
        calendarId: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val service = calendarService ?: return@withContext emptyList()

        try {
            val timeMin = DateTime(
                java.time.LocalDateTime.of(
                    startDate.year, startDate.monthNumber, startDate.dayOfMonth,
                    startDate.hour, startDate.minute, startDate.second
                ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )

            val timeMax = DateTime(
                java.time.LocalDateTime.of(
                    endDate.year, endDate.monthNumber, endDate.dayOfMonth,
                    endDate.hour, endDate.minute, endDate.second
                ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )

            val events = service.events().list(calendarId)
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .setShowDeleted(false)
                .execute()

            events.items?.map { event ->
                val eventStartTime = if (event.start.dateTime != null) {
                    java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(event.start.dateTime.value),
                        ZoneId.systemDefault()
                    ).toKotlinLocalDateTime()
                } else {
                    java.time.LocalDateTime.parse(event.start.date.toString() + "T00:00:00")
                        .toKotlinLocalDateTime()
                }

                val eventEndTime = if (event.end.dateTime != null) {
                    java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(event.end.dateTime.value),
                        ZoneId.systemDefault()
                    ).toKotlinLocalDateTime()
                } else {
                    java.time.LocalDateTime.parse(event.end.date.toString() + "T23:59:59")
                        .toKotlinLocalDateTime()
                }

                val colorId = event.colorId
                val isCancelled = event.status == "cancelled" ||
                    (colorId == CalendarColors.RED_CANCELLED && !isSupervision(event.summary ?: ""))
                val isPendingPayment = isCancelled && colorId == CalendarColors.GREY_CANCELLED

                CalendarEvent(
                    id = event.id ?: "",
                    title = event.summary ?: "Χωρίς τίτλο",
                    startTime = eventStartTime,
                    endTime = eventEndTime,
                    colorId = colorId,
                    isCancelled = isCancelled,
                    isPendingPayment = isPendingPayment,
                    attendees = event.attendees?.map { it.email } ?: emptyList()
                )
            } ?: emptyList()

        } catch (e: Exception) {
            println("Error fetching calendar events: ${e.message}")
            emptyList()
        }
    }

    override suspend fun filterEventsByClientNames(
        events: List<CalendarEvent>,
        clientNames: List<String>,
        specialKeywords: List<String>
    ): Map<String, List<CalendarEvent>> {
        val clientEvents = clientNames.associateWith { mutableListOf<CalendarEvent>() }.toMutableMap()

        // Add special keywords to the map
        specialKeywords.forEach { keyword ->
            clientEvents[keyword] = mutableListOf()
        }

        for (event in events) {
            val matches = clientMatchingService.findClientMatches(
                event.title,
                clientNames,
                specialKeywords
            )
            if (matches.isNotEmpty()) {
                val clientName = matches.first()
                clientEvents[clientName]?.add(event)
            }
        }

        return clientEvents.mapValues { it.value.toList() }
    }

    override suspend fun getCalendarList(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        val service = calendarService ?: return@withContext emptyList()

        try {
            val calendarList = service.calendarList().list().execute()
            calendarList.items?.map { calendar ->
                CalendarInfo(
                    id = calendar.id ?: "",
                    name = calendar.summary ?: "",
                    isPrimary = calendar.primary ?: false
                )
            } ?: emptyList()
        } catch (e: Exception) {
            println("Error fetching calendar list: ${e.message}")
            emptyList()
        }
    }

    private fun isSupervision(summary: String): Boolean {
        val normalized = summary.lowercase().trim()
            .replace("ά", "α").replace("έ", "ε")
            .replace("ή", "η").replace("ί", "ι")
            .replace("ό", "ο").replace("ύ", "υ")
            .replace("ώ", "ω")
        return normalized == "εποπτεια"
    }
}
```

### shared/src/desktopMain/kotlin/com/fkcoding/payroll/google/GoogleCredentialProvider.kt

```kotlin
package com.fkcoding.payroll.google

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.SheetsScopes
import java.io.File
import java.io.InputStreamReader

/**
 * Provides Google API credentials and services
 *
 * IMPORTANT: You need to place credentials.json in your resources folder
 * Get it from: https://console.cloud.google.com/
 */
class GoogleCredentialProvider {

    companion object {
        private const val APPLICATION_NAME = "Payroll App"
        private val JSON_FACTORY = GsonFactory.getDefaultInstance()

        private val SCOPES = listOf(
            CalendarScopes.CALENDAR_READONLY,
            SheetsScopes.SPREADSHEETS,
            DriveScopes.DRIVE_READONLY,
            "https://www.googleapis.com/auth/drive.file"
        )

        private val TOKENS_DIR = File(System.getProperty("user.home"), ".payroll-app/tokens")
    }

    private val httpTransport: NetHttpTransport = GoogleNetHttpTransport.newTrustedTransport()
    private var credential: Credential? = null

    init {
        try {
            credential = loadCredential()
        } catch (e: Exception) {
            println("Failed to load credentials: ${e.message}")
            println("Please ensure credentials.json exists in resources/data/")
        }
    }

    private fun loadCredential(): Credential {
        if (!TOKENS_DIR.exists()) {
            TOKENS_DIR.mkdirs()
        }

        // Load credentials.json from resources
        val credentialsStream = javaClass.getResourceAsStream("/data/credentials.json")
            ?: throw RuntimeException("credentials.json not found in resources/data/")

        val clientSecrets = GoogleClientSecrets.load(
            JSON_FACTORY,
            InputStreamReader(credentialsStream)
        )

        val flow = GoogleAuthorizationCodeFlow.Builder(
            httpTransport,
            JSON_FACTORY,
            clientSecrets,
            SCOPES
        )
            .setDataStoreFactory(FileDataStoreFactory(TOKENS_DIR))
            .setAccessType("offline")
            .build()

        // Try to load existing credential
        val existingCredential = flow.loadCredential("user")
        if (existingCredential != null) {
            println("Loaded existing credentials")
            return existingCredential
        }

        // No credential found, need to authorize
        println("No credentials found. Browser will open for authorization...")
        val receiver = LocalServerReceiver.Builder()
            .setPort(8889)
            .build()

        return AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
    }

    fun getCalendarService(): Calendar? {
        val cred = credential ?: return null
        return Calendar.Builder(httpTransport, JSON_FACTORY, cred)
            .setApplicationName(APPLICATION_NAME)
            .build()
    }

    fun isAuthenticated(): Boolean = credential != null

    fun deleteCredentials() {
        TOKENS_DIR.listFiles()?.forEach { it.delete() }
        credential = null
        println("Credentials deleted. Re-authorization required.")
    }
}
```

---

## 9. KSAFE ΓΙΑ ENCRYPTION

### Για να κρύψεις sensitive data (emails, calendar IDs):

```kotlin
// shared/src/commonMain/kotlin/com/fkcoding/payroll/security/SecureStorage.kt

package com.fkcoding.payroll.security

import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.serialization.Serializable

@Serializable
data class SecureCredentials(
    val clientId: String = "",
    val clientSecret: String = ""
)

@Serializable
data class SecureEmployeeData(
    val email: String = "",
    val calendarId: String = ""
)

/**
 * Secure storage wrapper using KSafe
 */
class SecureStorage(private val ksafe: KSafe) {

    suspend fun saveCredentials(credentials: SecureCredentials) {
        ksafe.put("google_credentials", credentials, encrypted = true)
    }

    suspend fun getCredentials(): SecureCredentials {
        return ksafe.get("google_credentials", SecureCredentials(), encrypted = true)
    }

    suspend fun saveEmployeeSecureData(employeeId: String, data: SecureEmployeeData) {
        ksafe.put("employee_$employeeId", data, encrypted = true)
    }

    suspend fun getEmployeeSecureData(employeeId: String): SecureEmployeeData {
        return ksafe.get("employee_$employeeId", SecureEmployeeData(), encrypted = true)
    }
}
```

---

## 10. STEP-BY-STEP MIGRATION

### Βήμα 1: Δημιούργησε τη δομή φακέλων

```bash
# Στο KMM project
mkdir -p shared/src/commonMain/kotlin/com/fkcoding/payroll/model
mkdir -p shared/src/commonMain/kotlin/com/fkcoding/payroll/service
mkdir -p shared/src/commonMain/kotlin/com/fkcoding/payroll/repository
mkdir -p shared/src/commonMain/sqldelight/com/fkcoding/payroll
mkdir -p shared/src/desktopMain/kotlin/com/fkcoding/payroll/repository
mkdir -p shared/src/desktopMain/kotlin/com/fkcoding/payroll/google
mkdir -p shared/src/desktopMain/kotlin/com/fkcoding/payroll/database
```

### Βήμα 2: Πρόσθεσε τα dependencies

Αντέγραψε τα dependencies από την ενότητα 2 στα αντίστοιχα build.gradle.kts αρχεία.

### Βήμα 3: Δημιούργησε τα Models

Αντέγραψε τα αρχεία από την ενότητα 3:
- Employee.kt
- Client.kt
- CalendarEvent.kt
- PayrollModels.kt

### Βήμα 4: Δημιούργησε τα Services

Αντέγραψε τα αρχεία από την ενότητα 4:
- ClientMatchingService.kt
- PayrollCalculationService.kt

### Βήμα 5: Δημιούργησε τα Repository Interfaces

Αντέγραψε τα αρχεία από την ενότητα 5:
- EmployeeRepository.kt
- ClientRepository.kt
- CalendarRepository.kt

### Βήμα 6: Setup SQLDelight

1. Δημιούργησε το PayrollDatabase.sq (ενότητα 6)
2. Δημιούργησε το DriverFactory.kt
3. Sync το Gradle για να generate τα database classes

### Βήμα 7: Υλοποίησε τα Desktop Repositories

Αντέγραψε τα αρχεία από την ενότητα 7:
- SqlDelightEmployeeRepository.kt
- SqlDelightClientRepository.kt

### Βήμα 8: Υλοποίησε το Google Calendar

1. Αντέγραψε το GoogleCredentialProvider.kt (ενότητα 8)
2. Αντέγραψε το GoogleCalendarRepository.kt
3. Βάλε το credentials.json στο resources/data/

### Βήμα 9: Test

```kotlin
// Main.kt
fun main() = application {
    // Initialize database
    val driver = DriverFactory.createDriver()
    val database = PayrollDatabase(driver)

    // Initialize repositories
    val employeeRepo = SqlDelightEmployeeRepository(database)
    val clientRepo = SqlDelightClientRepository(database)

    // Initialize Google Calendar
    val credentialProvider = GoogleCredentialProvider()
    val clientMatchingService = ClientMatchingService()
    val calendarRepo = GoogleCalendarRepository(credentialProvider, clientMatchingService)

    // Initialize services
    val payrollService = PayrollCalculationService()

    Window(onCloseRequest = ::exitApplication, title = "Payroll App") {
        // Your UI here
        App(
            employeeRepo = employeeRepo,
            clientRepo = clientRepo,
            calendarRepo = calendarRepo,
            payrollService = payrollService
        )
    }
}
```

---

## CHECKLIST

- [ ] Dependencies added to build.gradle.kts
- [ ] Models created in commonMain
- [ ] Services created in commonMain
- [ ] Repository interfaces created in commonMain
- [ ] SQLDelight schema created
- [ ] DriverFactory created in desktopMain
- [ ] Repository implementations created in desktopMain
- [ ] GoogleCredentialProvider created
- [ ] GoogleCalendarRepository created
- [ ] credentials.json placed in resources/data/
- [ ] Main.kt updated to initialize all components
- [ ] Test basic functionality

---

## ΣΗΜΑΝΤΙΚΕΣ ΣΗΜΕΙΩΣΕΙΣ

1. **credentials.json**: Πρέπει να το πάρεις από το Google Cloud Console και να το βάλεις στο `resources/data/credentials.json`

2. **Tokens**: Αποθηκεύονται στο `~/.payroll-app/tokens/` - αν έχεις προβλήματα authentication, διέγραψε αυτόν τον φάκελο

3. **Database**: Αποθηκεύεται στο `~/.payroll-app/payroll.db`

4. **kotlinx-datetime**: Χρησιμοποιούμε αυτό αντί για java.time για KMM compatibility

5. **Coroutines**: Όλες οι database operations είναι suspend functions

6. **Error Handling**: Πρόσθεσε try-catch σε όλα τα critical paths

---

## CONTACT

Αν έχεις ερωτήσεις, ρώτα το session που δημιούργησε αυτό το document!
