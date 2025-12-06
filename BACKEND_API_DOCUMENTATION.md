# PayrollApp Backend API - Complete Documentation

**Base URL:** `http://localhost:8080`

---

## 📋 Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Data Models](#data-models)
3. [API Endpoints](#api-endpoints)
4. [Frontend Integration Guide](#frontend-integration-guide)
5. [Authentication & Security](#authentication--security)
6. [Error Handling](#error-handling)

---

## Architecture Overview

### Tech Stack
- **Framework:** Spring Boot 3.2.1 + Kotlin
- **Database:** H2 (in-memory) for development, PostgreSQL ready for production
- **External APIs:** Google Calendar, Google Sheets, Google Drive
- **Architecture:** RESTful API with service-oriented architecture

### Key Components

```
┌─────────────────┐
│   Frontend      │
│  (Desktop/Web)  │
└────────┬────────┘
         │ HTTP REST
         │
┌────────▼────────────────────────────────────────┐
│              Spring Boot Backend                │
│                                                  │
│  ┌──────────────┐  ┌──────────────┐            │
│  │ Controllers  │  │  Services    │            │
│  │ (REST API)   │→ │  (Logic)     │            │
│  └──────────────┘  └──────┬───────┘            │
│                           │                     │
│  ┌──────────────┐  ┌──────▼───────┐            │
│  │ Repositories │  │  External    │            │
│  │ (Database)   │  │  APIs        │            │
│  └──────────────┘  └──────────────┘            │
│         │                  │                    │
└─────────┼──────────────────┼────────────────────┘
          │                  │
    ┌─────▼─────┐     ┌─────▼──────────┐
    │ H2/Postgres│     │ Google APIs    │
    │  Database  │     │ (Calendar,     │
    └────────────┘     │  Sheets, Drive)│
                       └────────────────┘
```

### Data Flow

1. **Initial Setup (One-time):**
   - Excel file in Google Drive contains employees & clients
   - Run `/api/db/sync` to populate database
   - Database becomes source of truth

2. **Payroll Calculation:**
   - Frontend sends: `employeeId`, `startDate`, `endDate`
   - Backend fetches Google Calendar events
   - Matches events with clients from database
   - Applies cancellation rules (color-based)
   - Returns detailed breakdown with revenue calculations

3. **Data Management:**
   - Frontend can CRUD employees & clients via REST API
   - Changes persist in database
   - Optional: Re-sync from Excel if needed

---

## Data Models

### Employee

```kotlin
data class Employee(
    val id: String,              // Unique ID (can be email or custom)
    val name: String,            // e.g., "Αναστασία Καλαμποκά"
    val email: String,           // Optional email
    val calendarId: String,      // Google Calendar ID
    val color: String,           // Color for UI display
    val sheetName: String,       // Google Sheets tab name
    val supervisionPrice: Double // Price for supervision sessions (€)
)
```

**Example JSON:**
```json
{
  "id": "-991962534",
  "name": "Αναστασία Καλαμποκά",
  "email": "",
  "calendarId": "g2j3s51fto3mkvp9d5esm69bhg@group.calendar.google.com",
  "color": "#9fc6e7",
  "sheetName": "Αναστασία Καλαμποκά",
  "supervisionPrice": 0.0
}
```

---

### Client

```kotlin
data class Client(
    val id: Long?,               // Auto-generated database ID
    val name: String,            // e.g., "Ndrekaj Ornela - Ντρεκαι Ορνελα"
    val price: Double,           // Total session price (€)
    val employeePrice: Double,   // Amount employee receives (€)
    val companyPrice: Double,    // Amount company receives (€)
    val employeeId: String,      // Foreign key to Employee
    val pendingPayment: Boolean  // If true, cancelled session counts
)
```

**Example JSON:**
```json
{
  "id": 123,
  "name": "Ndrekaj Ornela - Ντρεκαι Ορνελα",
  "price": 50.0,
  "employeePrice": 22.5,
  "companyPrice": 27.5,
  "employeeId": "-991962534",
  "pendingPayment": false
}
```

**Important:** `employeePrice + companyPrice` should equal `price` (validated on create/update)

---

### Calendar Event

```kotlin
data class CalendarEvent(
    val id: String,              // Google Calendar event ID
    val title: String,           // e.g., "Ντρεκαι Ορνελα Google meet"
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val colorId: String?,        // "8" = grey (pending), "11" = red (cancelled)
    val isCancelled: Boolean,    // Derived from status or colorId
    val isPendingPayment: Boolean, // Grey cancelled events
    val attendees: List<String>  // Email addresses
)
```

**Cancellation Rules:**
- `colorId = "8"` (Grey) → Cancelled but client will pay next time (`isPendingPayment = true`)
- `colorId = "11"` (Red) → Cancelled, should NOT be paid (`isCancelled = true`)
- `status = "cancelled"` → Standard cancellation

---

### Payroll Calculation Result

```kotlin
data class PayrollCalculationResult(
    val employee: Employee,
    val period: Period,
    val clientSessions: List<ClientSession>,
    val supervisionSessions: List<SupervisionSession>,
    val summary: PayrollSummary
)

data class Period(
    val startDate: LocalDate,
    val endDate: LocalDate
)

data class ClientSession(
    val client: Client,
    val sessions: List<SessionDetail>,
    val totalSessions: Int,
    val totalRevenue: Double,
    val employeeRevenue: Double,
    val companyRevenue: Double
)

data class SessionDetail(
    val date: LocalDate,
    val time: LocalTime,
    val duration: Duration,
    val status: String,     // "✅ Completed", "❌ Cancelled", "⏳ Pending Payment"
    val price: Double
)

data class SupervisionSession(
    val date: LocalDate,
    val time: LocalTime,
    val duration: Duration,
    val price: Double
)

data class PayrollSummary(
    val totalSessions: Int,
    val totalRevenue: Double,
    val employeeRevenue: Double,
    val companyRevenue: Double,
    val supervisionRevenue: Double
)
```

---

## API Endpoints

### 1. Employee Management

#### **GET /api/employees**
Get all employees.

**Response:**
```json
[
  {
    "id": "-991962534",
    "name": "Αναστασία Καλαμποκά",
    "email": "",
    "calendarId": "g2j3s51fto3mkvp9d5esm69bhg@group.calendar.google.com",
    "color": "#9fc6e7",
    "sheetName": "Αναστασία Καλαμποκά",
    "supervisionPrice": 0.0
  }
]
```

**Use Case:** Populate dropdown for employee selection

---

#### **GET /api/employees/{id}**
Get specific employee by ID.

**Response:** Single Employee object (see above)

**Error:** 404 if not found

---

#### **POST /api/employees**
Create new employee.

**Request Body:**
```json
{
  "id": "john.doe@example.com",
  "name": "Γιάννης Δημητρίου",
  "email": "john.doe@example.com",
  "calendarId": "primary",
  "color": "#ff0000",
  "sheetName": "Γιάννης Δημητρίου",
  "supervisionPrice": 50.0
}
```

**Validation:**
- `id`: Required, unique
- `name`: Required
- `calendarId`: Required
- `supervisionPrice`: Must be ≥ 0

**Response:**
- 201 Created + Employee object
- 409 Conflict if ID already exists
- 400 Bad Request if validation fails

---

#### **PUT /api/employees/{id}**
Update existing employee.

**Request Body:** Same as POST (without `id`)

**Response:**
- 200 OK + Updated Employee object
- 404 Not Found if employee doesn't exist

---

#### **DELETE /api/employees/{id}**
Delete employee.

**Response:**
- 200 OK + Success message
- 404 Not Found if employee doesn't exist

**Warning:** This also deletes all associated clients!

---

### 2. Client Management

#### **GET /api/clients**
Get all clients.

**Response:**
```json
[
  {
    "id": 123,
    "name": "Ζωή Κουσουλού",
    "price": 40.0,
    "employeePrice": 18.0,
    "companyPrice": 22.0,
    "employeeId": "-991962534",
    "pendingPayment": false
  }
]
```

---

#### **GET /api/clients/employee/{employeeId}**
Get all clients for a specific employee.

**Response:** Array of Client objects

**Use Case:** Display client list for selected employee in desktop app

---

#### **POST /api/clients**
Create new client.

**Request Body:**
```json
{
  "name": "Μαρία Παπαδοπούλου",
  "price": 40.0,
  "employeePrice": 18.0,
  "companyPrice": 22.0,
  "employeeId": "-991962534",
  "pendingPayment": false
}
```

**Validation:**
- `name`: Required
- `price`: Must be > 0
- `employeePrice`: Must be > 0
- `companyPrice`: Must be > 0
- `employeePrice + companyPrice` must equal `price` (±0.01 tolerance)
- `employeeId`: Must reference existing employee

**Response:**
- 201 Created + Client object
- 400 Bad Request if validation fails
- 404 Not Found if employee doesn't exist

---

#### **PUT /api/clients/{id}**
Update existing client.

**Request Body:** Same as POST (without `id`)

**Response:**
- 200 OK + Updated Client object
- 404 Not Found if client doesn't exist
- 400 Bad Request if validation fails

---

#### **DELETE /api/clients/{id}**
Delete client.

**Response:**
- 200 OK + Success message
- 404 Not Found if client doesn't exist

---

#### **DELETE /api/clients/employee/{employeeId}**
Delete all clients for a specific employee.

**Response:**
- 200 OK + Success message with count
- 404 Not Found if employee doesn't exist

---

### 3. Payroll Calculation

#### **GET /api/payroll/calculate**
Calculate payroll for an employee in a date range.

**Query Parameters:**
- `employeeId` (required): Employee ID
- `startDate` (required): Format `YYYY-MM-DD`
- `endDate` (required): Format `YYYY-MM-DD`

**Example:**
```
GET /api/payroll/calculate?employeeId=-991962534&startDate=2024-11-01&endDate=2024-11-30
```

**Response:**
```json
{
  "employee": {
    "id": "-991962534",
    "name": "Αναστασία Καλαμποκά",
    ...
  },
  "period": {
    "startDate": "2024-11-01",
    "endDate": "2024-11-30"
  },
  "clientSessions": [
    {
      "client": {
        "id": 123,
        "name": "Ζωή Κουσουλού",
        "price": 40.0,
        ...
      },
      "sessions": [
        {
          "date": "2024-11-05",
          "time": "10:00",
          "duration": "PT1H",
          "status": "✅ Completed",
          "price": 40.0
        }
      ],
      "totalSessions": 2,
      "totalRevenue": 80.0,
      "employeeRevenue": 36.0,
      "companyRevenue": 44.0
    }
  ],
  "supervisionSessions": [
    {
      "date": "2024-11-10",
      "time": "14:00",
      "duration": "PT1H",
      "price": 50.0
    }
  ],
  "summary": {
    "totalSessions": 15,
    "totalRevenue": 600.0,
    "employeeRevenue": 270.0,
    "companyRevenue": 330.0,
    "supervisionRevenue": 100.0
  }
}
```

**Error Cases:**
- 404: Employee not found
- 400: Invalid date format
- 500: Google Calendar API error

---

### 4. Database Sync

#### **POST /api/db/sync**
Manually sync database from Excel file in Google Drive.

**Response:**
```json
{
  "employeesInserted": 7,
  "employeesUpdated": 0,
  "clientsInserted": 585,
  "clientsUpdated": 0,
  "durationMs": 1234
}
```

**What it does:**
1. Downloads Excel from Google Drive
2. Parses EMPLOYEES tab + individual employee sheets
3. Syncs to database:
   - Matches employees by ID (insert or update)
   - Matches clients by (employeeId + name) (insert or update)
   - Deletes orphaned clients (in DB but not in Excel)

**Use Case:**
- Initial setup
- Sync button in desktop app if user made changes in Excel

---

#### **GET /api/db/stats**
Get database sync statistics.

**Response:**
```json
{
  "database": {
    "employees": 7,
    "clients": 585
  },
  "excel": {
    "employees": 7,
    "clients": 585
  },
  "inSync": true,
  "lastLoadTime": "2024-12-06T10:30:45"
}
```

---

### 5. Debug Endpoints (Optional)

#### **GET /api/debug/events/{employeeId}**
Get all calendar events for an employee (last 2 weeks).

**Response:** Detailed event breakdown with matching information

**Use Case:** Debugging, troubleshooting event matching issues

---

## Frontend Integration Guide

### Desktop App - Current Features

#### 1. **Employee Selector Dropdown**

```typescript
// Fetch employees
const employees = await fetch('http://localhost:8080/api/employees')
  .then(res => res.json());

// Populate dropdown
employees.forEach(emp => {
  dropdown.add(new Option(emp.name, emp.id));
});
```

---

#### 2. **Payroll Calculation**

```typescript
// User selects employee & dates
const employeeId = selectedEmployee.value;
const startDate = '2024-11-01';
const endDate = '2024-11-30';

// Calculate payroll
const result = await fetch(
  `http://localhost:8080/api/payroll/calculate?employeeId=${employeeId}&startDate=${startDate}&endDate=${endDate}`
).then(res => res.json());

// Display results
console.log('Total Revenue:', result.summary.totalRevenue);
console.log('Employee Gets:', result.summary.employeeRevenue);
console.log('Company Gets:', result.summary.companyRevenue);

// Show session breakdown
result.clientSessions.forEach(cs => {
  console.log(`${cs.client.name}: ${cs.totalSessions} sessions, €${cs.totalRevenue}`);
});
```

---

#### 3. **Client Management Screen**

```typescript
// Fetch clients for selected employee
const clients = await fetch(
  `http://localhost:8080/api/clients/employee/${employeeId}`
).then(res => res.json());

// Display in table
clients.forEach(client => {
  table.addRow({
    name: client.name,
    price: `€${client.price}`,
    employeePart: `€${client.employeePrice}`,
    companyPart: `€${client.companyPrice}`
  });
});

// Add new client
async function addClient(clientData) {
  const response = await fetch('http://localhost:8080/api/clients', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: clientData.name,
      price: clientData.price,
      employeePrice: clientData.employeePrice,
      companyPrice: clientData.companyPrice,
      employeeId: selectedEmployee.id,
      pendingPayment: false
    })
  });

  if (!response.ok) {
    const error = await response.json();
    alert('Error: ' + error.message);
  } else {
    // Refresh list
    loadClients();
  }
}

// Delete client
async function deleteClient(clientId) {
  await fetch(`http://localhost:8080/api/clients/${clientId}`, {
    method: 'DELETE'
  });
  loadClients(); // Refresh
}
```

---

#### 4. **Sync Button**

```typescript
async function syncDatabase() {
  showLoadingSpinner();

  const result = await fetch('http://localhost:8080/api/db/sync', {
    method: 'POST'
  }).then(res => res.json());

  hideLoadingSpinner();

  alert(`Sync complete!\n` +
        `Employees: ${result.employeesInserted} new, ${result.employeesUpdated} updated\n` +
        `Clients: ${result.clientsInserted} new, ${result.clientsUpdated} updated`);

  // Refresh all data
  loadEmployees();
  loadClients();
}
```

---

### Future Mobile Apps - Planned Features

#### **Employee App (React Native / Flutter)**

**Features:**
- Login (employee-specific)
- View own calendar
- Add/cancel appointments
- Daily income view (read-only)
- Session history

**Required Endpoints:**
- ✅ GET `/api/payroll/calculate` (with daily breakdown)
- 🆕 POST `/api/calendar/events` (add appointment)
- 🆕 DELETE `/api/calendar/events/{id}` (cancel appointment)
- 🆕 GET `/api/sessions/daily` (daily income view)

---

#### **Admin App (Office Manager)**

**Features:**
- View all employees' calendars
- Manage employees & clients (CRUD)
- View total payroll for all
- Statistics & reports

**Required Endpoints:**
- ✅ All current employee/client CRUD
- ✅ GET `/api/payroll/calculate` (for all employees)
- 🆕 GET `/api/payroll/summary` (company-wide totals)
- 🆕 GET `/api/statistics/monthly` (charts, graphs)

---

## Authentication & Security

### Current State: **⚠️ NO AUTHENTICATION**

The API is currently **completely open**. For production:

**Recommended Security Stack:**
- Spring Security with JWT tokens
- Role-based access control (RBAC):
  - `ROLE_EMPLOYEE`: Can only view/edit own data
  - `ROLE_ADMIN`: Can view/edit all data
- OAuth2 integration with Google (since you already use Google APIs)

**Example Secured Endpoints:**
```kotlin
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/api/employees")
fun createEmployee(...)

@PreAuthorize("hasRole('EMPLOYEE') or hasRole('ADMIN')")
@GetMapping("/api/payroll/calculate")
fun calculatePayroll(@AuthenticationPrincipal user: User, ...)
```

---

## Error Handling

### Standard Error Response Format

```json
{
  "timestamp": "2024-12-06T10:30:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Price split is invalid: employeePrice (20.0) + companyPrice (15.0) = 35.0, but price is 40.0",
  "path": "/api/clients"
}
```

### Common HTTP Status Codes

- `200 OK`: Success
- `201 Created`: Resource created successfully
- `400 Bad Request`: Validation error
- `404 Not Found`: Resource not found
- `409 Conflict`: Duplicate resource (e.g., employee ID already exists)
- `500 Internal Server Error`: Unexpected error

---

## Configuration

### Environment Variables (application.properties)

```properties
# Google Calendar
google.calendar.credentials.path=classpath:data/credentials.json
google.calendar.color.grey.cancelled=8
google.calendar.color.red.cancelled=11

# Google Drive (Excel input)
google.drive.excel.file.id=1NRXJV6Cd_fzdrzQwXL4exje38yOIrYSV

# Google Sheets (Payroll output)
google.sheets.spreadsheet.id=1hCpJWnJCO_bCYNyTDQ0m0Sq_Mwby9H_7nSOXHResATY
google.sheets.master.sheet=MASTER_PAYROLL
google.sheets.details.sheet=CLIENT_DETAILS
google.sheets.stats.sheet=MONTHLY_STATS

# Database sync
database.sync.enabled=false
```

---

## Best Practices for Frontend

### 1. **Error Handling**
```typescript
async function apiCall(url, options) {
  try {
    const response = await fetch(url, options);

    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.message || 'Request failed');
    }

    return response.json();
  } catch (error) {
    console.error('API Error:', error);
    showErrorToast(error.message);
    throw error;
  }
}
```

### 2. **Loading States**
Always show loading spinner during API calls (some operations like sync can take 10-20 seconds).

### 3. **Date Formatting**
Backend expects `YYYY-MM-DD` format. Use:
```typescript
const formatDate = (date: Date) => date.toISOString().split('T')[0];
```

### 4. **Validation Before Submit**
```typescript
function validateClient(client) {
  const total = client.employeePrice + client.companyPrice;
  const diff = Math.abs(total - client.price);

  if (diff > 0.01) {
    alert(`Price split error: ${client.employeePrice} + ${client.companyPrice} ≠ ${client.price}`);
    return false;
  }
  return true;
}
```

### 5. **Caching**
Cache employee list since it rarely changes:
```typescript
let employeeCache = null;
let cacheTimestamp = null;

async function getEmployees(forceRefresh = false) {
  const cacheAge = Date.now() - (cacheTimestamp || 0);

  if (!forceRefresh && employeeCache && cacheAge < 5 * 60 * 1000) {
    return employeeCache;
  }

  employeeCache = await fetch('/api/employees').then(r => r.json());
  cacheTimestamp = Date.now();
  return employeeCache;
}
```

---

## Summary

### ✅ Ready for Desktop App
All endpoints needed for your current desktop features are **production-ready**:
- Employee dropdown ✅
- Date selection & payroll calculation ✅
- Client list per employee ✅
- Add/remove clients ✅
- Add/remove employees ✅
- Sync from Excel ✅

### 🆕 Needed for Mobile Apps
When you build native apps, you'll need:
- **Authentication system** (JWT + role-based access)
- **Calendar write endpoints** (add/cancel appointments)
- **Daily income breakdown** (for employee app)
- **Company-wide statistics** (for admin app)

### 📝 Recommendation
For the mobile apps, **open a new session** with this documentation and we'll:
1. Add authentication
2. Create calendar write endpoints
3. Build employee-specific & admin-specific views
4. Add statistics/reporting endpoints

The current backend architecture is **solid** and ready to extend! 🚀
