# PayrollApp API Documentation

## 🚀 Database Management Strategy

The application now uses a **database-first approach**:
- ✅ **Database is the source of truth** for employees and clients
- ✅ **Auto-sync from Excel is DISABLED by default** (configurable)
- ✅ **Calendar is READ-ONLY** (used only for fetching events)
- ✅ **CRUD API endpoints** for managing employees and clients

---

## 🔧 Configuration

### Enable/Disable Auto-Sync

Edit `application.properties`:

```properties
# Auto-sync from Excel on startup (default: false)
database.sync.enabled=false
```

**Recommended:**
- Set `database.sync.enabled=true` **only for initial data load**
- After first load, set it back to `false` and restart

---

## 📝 Employee Management API

### Base URL: `/api/employees`

#### 1. Get All Employees
```http
GET /api/employees
```

**Response:**
```json
[
  {
    "id": "aggeliki_gkountopoulou",
    "name": "Αγγελική Γκουντοπούλου",
    "email": "aggeliki@example.com",
    "calendarId": "aggeliki@gmail.com",
    "color": "#FF5733",
    "sheetName": "Aggeliki_Sheet",
    "supervisionPrice": 50.0
  }
]
```

---

#### 2. Get Single Employee
```http
GET /api/employees/{id}
```

**Example:**
```bash
curl http://localhost:8080/api/employees/aggeliki_gkountopoulou
```

**Response (200 OK):**
```json
{
  "id": "aggeliki_gkountopoulou",
  "name": "Αγγελική Γκουντοπούλου",
  "email": "aggeliki@example.com",
  ...
}
```

**Response (404 Not Found):**
```json
{
  "error": "Employee not found with id: invalid_id"
}
```

---

#### 3. Create New Employee
```http
POST /api/employees
Content-Type: application/json
```

**Request Body:**
```json
{
  "id": "maria_papadopoulou",
  "name": "Μαρία Παπαδοπούλου",
  "email": "maria@example.com",
  "calendarId": "maria.papadopoulou@gmail.com",
  "color": "#3498DB",
  "sheetName": "Maria_Sheet",
  "supervisionPrice": 60.0
}
```

**Validation Rules:**
- ✅ `id`: Required, unique
- ✅ `name`: Required, not blank
- ✅ `email`: Required, valid email format
- ✅ `calendarId`: Required, not blank
- ✅ `color`: Required, not blank
- ✅ `sheetName`: Required, not blank
- ✅ `supervisionPrice`: Must be ≥ 0

**Example:**
```bash
curl -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -d '{
    "id": "maria_papadopoulou",
    "name": "Μαρία Παπαδοπούλου",
    "email": "maria@example.com",
    "calendarId": "maria@gmail.com",
    "color": "#3498DB",
    "sheetName": "Maria_Sheet",
    "supervisionPrice": 60.0
  }'
```

**Response (201 Created):**
```json
{
  "id": "maria_papadopoulou",
  "name": "Μαρία Παπαδοπούλου",
  ...
}
```

**Response (409 Conflict):**
```json
{
  "error": "Employee with id 'maria_papadopoulou' already exists"
}
```

---

#### 4. Update Employee
```http
PUT /api/employees/{id}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "Μαρία Παπαδοπούλου (Updated)",
  "email": "maria.new@example.com",
  "calendarId": "maria@gmail.com",
  "color": "#E74C3C",
  "sheetName": "Maria_Sheet_v2",
  "supervisionPrice": 70.0
}
```

**Example:**
```bash
curl -X PUT http://localhost:8080/api/employees/maria_papadopoulou \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Μαρία Παπαδοπούλου (Updated)",
    "email": "maria.new@example.com",
    "calendarId": "maria@gmail.com",
    "color": "#E74C3C",
    "sheetName": "Maria_Sheet_v2",
    "supervisionPrice": 70.0
  }'
```

**Response (200 OK):**
```json
{
  "id": "maria_papadopoulou",
  "name": "Μαρία Παπαδοπούλου (Updated)",
  ...
}
```

---

#### 5. Delete Employee
```http
DELETE /api/employees/{id}
```

**Example:**
```bash
curl -X DELETE http://localhost:8080/api/employees/maria_papadopoulou
```

**Response (200 OK):**
```json
{
  "message": "Employee deleted successfully",
  "id": "maria_papadopoulou"
}
```

**Response (409 Conflict) - Has Clients:**
```json
{
  "error": "Cannot delete employee with existing clients",
  "clientCount": 15,
  "hint": "Delete all clients first or use force=true"
}
```

**⚠️ Note:** You must delete all clients associated with an employee before deleting the employee.

---

#### 6. Get Employee's Clients
```http
GET /api/employees/{id}/clients
```

**Example:**
```bash
curl http://localhost:8080/api/employees/aggeliki_gkountopoulou/clients
```

**Response:**
```json
[
  {
    "id": 1,
    "name": "Κωνσταντίνος Κουρμούζης",
    "price": 50.0,
    "employeePrice": 20.0,
    "companyPrice": 30.0,
    "employeeId": "aggeliki_gkountopoulou",
    "pendingPayment": false
  }
]
```

---

## 👥 Client Management API

### Base URL: `/api/clients`

#### 1. Get All Clients
```http
GET /api/clients
```

**Response:**
```json
[
  {
    "id": 1,
    "name": "Νίκος Αλεξίου",
    "price": 50.0,
    "employeePrice": 20.0,
    "companyPrice": 30.0,
    "employeeId": "maria_papadopoulou",
    "pendingPayment": false
  }
]
```

---

#### 2. Get Single Client
```http
GET /api/clients/{id}
```

**Example:**
```bash
curl http://localhost:8080/api/clients/1
```

---

#### 3. Create New Client
```http
POST /api/clients
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "Νίκος Αλεξίου",
  "price": 50.0,
  "employeePrice": 20.0,
  "companyPrice": 30.0,
  "employeeId": "maria_papadopoulou",
  "pendingPayment": false
}
```

**Validation Rules:**
- ✅ `name`: Required, not blank
- ✅ `price`: Must be > 0
- ✅ `employeePrice`: Must be > 0
- ✅ `companyPrice`: Must be > 0
- ✅ `employeeId`: Required, must reference existing employee
- ✅ **Price Split Validation**: `employeePrice + companyPrice` must equal `price` (±0.01 tolerance)

**Example:**
```bash
curl -X POST http://localhost:8080/api/clients \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Νίκος Αλεξίου",
    "price": 50.0,
    "employeePrice": 20.0,
    "companyPrice": 30.0,
    "employeeId": "maria_papadopoulou",
    "pendingPayment": false
  }'
```

**Response (201 Created):**
```json
{
  "id": 42,
  "name": "Νίκος Αλεξίου",
  ...
}
```

**Response (400 Bad Request) - Invalid Price Split:**
```json
{
  "error": "Price split is invalid: employeePrice (20.0) + companyPrice (25.0) = 45.0, but price is 50.0"
}
```

**Response (400 Bad Request) - Employee Not Found:**
```json
{
  "error": "Employee not found with id: invalid_employee_id"
}
```

---

#### 4. Update Client
```http
PUT /api/clients/{id}
Content-Type: application/json
```

**Request Body:**
```json
{
  "name": "Νίκος Αλεξίου (Updated)",
  "price": 60.0,
  "employeePrice": 25.0,
  "companyPrice": 35.0,
  "employeeId": "maria_papadopoulou",
  "pendingPayment": false
}
```

**Example:**
```bash
curl -X PUT http://localhost:8080/api/clients/42 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Νίκος Αλεξίου (Updated)",
    "price": 60.0,
    "employeePrice": 25.0,
    "companyPrice": 35.0,
    "employeeId": "maria_papadopoulou",
    "pendingPayment": false
  }'
```

---

#### 5. Delete Client
```http
DELETE /api/clients/{id}
```

**Example:**
```bash
curl -X DELETE http://localhost:8080/api/clients/42
```

**Response (200 OK):**
```json
{
  "message": "Client deleted successfully",
  "id": 42,
  "name": "Νίκος Αλεξίου"
}
```

---

#### 6. Get Clients by Employee
```http
GET /api/clients/employee/{employeeId}
```

**Example:**
```bash
curl http://localhost:8080/api/clients/employee/maria_papadopoulou
```

**Response:**
```json
{
  "employeeId": "maria_papadopoulou",
  "count": 15,
  "clients": [...]
}
```

---

#### 7. Delete All Clients for Employee
```http
DELETE /api/clients/employee/{employeeId}
```

**Example:**
```bash
curl -X DELETE http://localhost:8080/api/clients/employee/maria_papadopoulou
```

**Response:**
```json
{
  "message": "Deleted all clients for employee",
  "employeeId": "maria_papadopoulou",
  "deletedCount": 15
}
```

---

## 🔄 Database Sync API

### Manual Sync from Excel

#### 1. Trigger Manual Sync
```http
POST /api/database-sync/sync
```

This will download the Excel file from Google Drive and sync all employees and clients to the database.

**Example:**
```bash
curl -X POST http://localhost:8080/api/database-sync/sync
```

**Response:**
```json
{
  "employeesInserted": 5,
  "employeesUpdated": 77,
  "clientsInserted": 120,
  "clientsUpdated": 200,
  "durationMs": 3456
}
```

---

#### 2. Get Sync Status
```http
GET /api/database-sync/status
```

**Example:**
```bash
curl http://localhost:8080/api/database-sync/status
```

**Response:**
```json
{
  "database": {
    "employees": 82,
    "clients": 320
  },
  "excel": {
    "employees": 82,
    "clients": 320
  },
  "inSync": true,
  "lastLoadTime": "2024-12-04T18:30:00"
}
```

---

## 🎯 Workflow: Initial Setup

### Step 1: Initial Data Load

**Option A: Enable auto-sync once**

1. Edit `application.properties`:
   ```properties
   database.sync.enabled=true
   ```

2. Start the application:
   ```bash
   ./gradlew bootRun
   ```

3. Wait for log message:
   ```
   ✅ Database sync completed!
      Employees: 82 new, 0 updated
      Clients: 320 new, 0 updated
   ```

4. Stop the application

5. Edit `application.properties`:
   ```properties
   database.sync.enabled=false
   ```

6. Restart application

**Option B: Use manual sync endpoint**

1. Start the application (with `database.sync.enabled=false`)

2. Trigger manual sync:
   ```bash
   curl -X POST http://localhost:8080/api/database-sync/sync
   ```

3. Check status:
   ```bash
   curl http://localhost:8080/api/database-sync/status
   ```

---

### Step 2: Add New Client

When a new client appears in the calendar:

```bash
# Add client via API
curl -X POST http://localhost:8080/api/clients \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Νέος Πελάτης",
    "price": 50.0,
    "employeePrice": 20.0,
    "companyPrice": 30.0,
    "employeeId": "aggeliki_gkountopoulou",
    "pendingPayment": false
  }'
```

Now payroll calculations will include this client!

---

### Step 3: Update Client Prices

```bash
# Update client
curl -X PUT http://localhost:8080/api/clients/42 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Existing Client",
    "price": 60.0,
    "employeePrice": 25.0,
    "companyPrice": 35.0,
    "employeeId": "aggeliki_gkountopoulou",
    "pendingPayment": false
  }'
```

---

## 🔍 Example: Full Client Lifecycle

```bash
# 1. Create employee
curl -X POST http://localhost:8080/api/employees \
  -H "Content-Type: application/json" \
  -d '{
    "id": "test_employee",
    "name": "Test Employee",
    "email": "test@example.com",
    "calendarId": "test@gmail.com",
    "color": "#FF5733",
    "sheetName": "Test_Sheet",
    "supervisionPrice": 50.0
  }'

# 2. Add client to employee
curl -X POST http://localhost:8080/api/clients \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Client",
    "price": 50.0,
    "employeePrice": 20.0,
    "companyPrice": 30.0,
    "employeeId": "test_employee",
    "pendingPayment": false
  }'

# 3. Get all clients for employee
curl http://localhost:8080/api/employees/test_employee/clients

# 4. Update client price
curl -X PUT http://localhost:8080/api/clients/1 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Client",
    "price": 60.0,
    "employeePrice": 25.0,
    "companyPrice": 35.0,
    "employeeId": "test_employee",
    "pendingPayment": false
  }'

# 5. Delete client
curl -X DELETE http://localhost:8080/api/clients/1

# 6. Delete employee
curl -X DELETE http://localhost:8080/api/employees/test_employee
```

---

## ❌ Common Errors

### 1. Price Split Validation Error

**Error:**
```json
{
  "error": "Price split is invalid: employeePrice (20.0) + companyPrice (25.0) = 45.0, but price is 50.0"
}
```

**Solution:** Ensure `employeePrice + companyPrice = price`

---

### 2. Cannot Delete Employee with Clients

**Error:**
```json
{
  "error": "Cannot delete employee with existing clients",
  "clientCount": 15
}
```

**Solution:** Delete all clients first:
```bash
curl -X DELETE http://localhost:8080/api/clients/employee/employee_id
```

---

### 3. Invalid Email Format

**Error:**
```json
{
  "email": "Email must be valid"
}
```

**Solution:** Use valid email format (e.g., `user@example.com`)

---

## 📚 Summary

| Operation | Endpoint | Method |
|-----------|----------|--------|
| **Employees** | | |
| List all | `/api/employees` | GET |
| Get one | `/api/employees/{id}` | GET |
| Create | `/api/employees` | POST |
| Update | `/api/employees/{id}` | PUT |
| Delete | `/api/employees/{id}` | DELETE |
| Get clients | `/api/employees/{id}/clients` | GET |
| **Clients** | | |
| List all | `/api/clients` | GET |
| Get one | `/api/clients/{id}` | GET |
| Create | `/api/clients` | POST |
| Update | `/api/clients/{id}` | PUT |
| Delete | `/api/clients/{id}` | DELETE |
| By employee | `/api/clients/employee/{empId}` | GET |
| Delete all for employee | `/api/clients/employee/{empId}` | DELETE |
| **Sync** | | |
| Manual sync | `/api/database-sync/sync` | POST |
| Sync status | `/api/database-sync/status` | GET |

---

## 🔐 Notes

- **No authentication** is currently implemented (admin-only access assumed)
- **Calendar is read-only** - no write operations
- **Database is the source of truth** - not Excel
- **Price validation** ensures correct split between employee and company
- **Transaction support** for data integrity
