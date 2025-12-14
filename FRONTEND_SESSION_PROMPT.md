# Frontend Development Session - Payroll App UI Enhancement

## Context

This is a continuation of the PayrollApp backend development. The backend now has comprehensive event tracking capabilities that need to be integrated into the desktop application UI.

## Current System State

### Backend Features (✅ COMPLETED)

1. **Comprehensive Event Tracking System**
   - Tracks ALL calendar events in multiple categories
   - Identifies unmatched events (potential new clients)
   - Categorizes cancelled events by color:
     - Grey (colorId=8): Cancelled but client will pay next time (pending payment)
     - Red (colorId=11): Cancelled and will NOT be paid
   - Tracks supervision events with special pricing
   - Identifies empty title events (availability hours)

2. **Extended Event Fetching**
   - System now fetches **3 weeks** of events (1 week before + 2 week payroll period)
   - Allows cross-checking pending payments from previous week
   - Prevents double entries for grey cancelled events that were later completed
   - Payroll calculations still only for 2-week period

3. **New API Endpoint: Event Analysis**
   - **GET /api/debug/analyze/{employeeId}?weeks=3**
   - Provides comprehensive categorization of all events
   - Terminal-friendly output with detailed statistics
   - See BACKEND_API_DOCUMENTATION.md for full details

### Tech Stack

- **Backend:** Spring Boot 3.2.1 + Kotlin, running on http://localhost:8080
- **Desktop App:** Electron + TypeScript (existing)
- **Database:** H2 (in-memory), synced from Google Drive Excel
- **External APIs:** Google Calendar API, Google Sheets API, Google Drive API

## Your Task

Enhance the desktop application to display the new event tracking information in the payroll results screen.

## Requirements

### 1. Update Payroll Results Display

**Current State:**
The payroll results screen shows:
- Employee info
- Period (2 weeks)
- Summary (total sessions, revenue, employee/company earnings)
- Client breakdown with sessions

**Required Enhancement:**
Add a new "Event Tracking" section that displays:

#### A. Summary Statistics
Display at the top of the payroll results:
```
📊 Event Analysis (3 weeks)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Total Events: 75
  ✅ Matched to Clients: 50
  ❓ Unmatched (New Clients?): 12
  🎓 Supervision: 3
  📅 Empty Title (Availability): 5
  ⏳ Cancelled Grey (Pending Payment): 3
  ❌ Cancelled Red (NOT Paid): 2

Period Breakdown:
  💰 In Payroll Period (2 weeks): 50
  📆 Before Payroll Period: 25
```

#### B. Unmatched Events List (CRITICAL)
**Purpose:** Help identify new clients that need to be registered in the database

Display a dedicated section:
```
❓ UNMATCHED EVENTS - ACTION REQUIRED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
These events don't match any client in the database:

1. Νέος Πελάτης A
   📅 16/11/2024 14:00 | Status: ❓ No client match

2. Άλλος Πελάτης B
   📅 18/11/2024 10:00 | Status: ❓ No client match

Action: Add these clients to the database via Clients Management
```

**Visual Treatment:**
- Use warning color (orange/yellow background)
- Make it prominent and eye-catching
- Include action button: "Manage Clients →"

#### C. Pending Payments (Cancelled Grey)
**Purpose:** Track sessions that were cancelled but client will pay next time

```
⏳ PENDING PAYMENT (Grey Cancelled Events)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
These cancelled sessions will be paid in next period:

1. Ζωή Κουσουλού
   📅 12/11/2024 10:00 | Color: Grey (8)

2. Άννα Παπαδοπούλου
   📅 15/11/2024 14:00 | Color: Grey (8)
```

**Visual Treatment:**
- Grey/neutral background
- Info icon
- Note: "Will be included in next payroll"

#### D. Cancelled Events (Red)
**Purpose:** Show sessions that were cancelled and will NOT be paid

```
❌ CANCELLED SESSIONS (Will NOT be paid)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. Μαρία Γεωργίου
   📅 20/11/2024 15:00 | Color: Red (11)
```

**Visual Treatment:**
- Light red background
- Shows they're not included in payroll totals

#### E. Supervision Events
**Purpose:** Show supervision sessions and their payment status

```
🎓 SUPERVISION SESSIONS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. 17/11/2024 15:00 | ✅ Counted in payroll
2. 10/11/2024 14:00 | ❌ Not counted (Outside period)
```

#### F. Empty Title Events (Optional - Collapsible)
**Purpose:** Show availability hours for completeness

```
📅 AVAILABILITY HOURS (Empty Title Events)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5 empty title events found (excluded from calculations)
[Click to expand]
```

### 2. Update API Integration

**Current endpoint:**
```typescript
POST /api/payroll/calculate
```

**New response structure includes `eventTracking` field:**
```typescript
interface PayrollResponse {
  id: string;
  payroll: {
    employee: EmployeeInfo;
    period: string;
    summary: PayrollSummary;
    clientBreakdown: ClientPayrollDetail[];
    eventTracking: EventTracking;  // 🆕 NEW!
    generatedAt: string;
    syncedToSheets: boolean;
  };
}

interface EventTracking {
  totalEvents: number;
  matchedEvents: number;
  unmatchedEvents: UnmatchedEvent[];
  cancelledGrey: CancelledEvent[];
  cancelledRed: CancelledEvent[];
  supervision: SupervisionEvent[];
  emptyTitle: number;
}

interface UnmatchedEvent {
  title: string;
  date: string;
  time: string;
  colorId: string;
  status: string;
}

interface CancelledEvent {
  title: string;
  date: string;
  time: string;
  colorId: string;
  type: "grey" | "red";
}

interface SupervisionEvent {
  date: string;
  time: string;
  counted: boolean;
  reason: string | null;
}
```

### 3. Optional Enhancement: Event Analysis Dialog

Add a button "📊 Detailed Event Analysis" that opens a dialog showing the full analysis from:
```
GET /api/debug/analyze/{employeeId}?weeks=3
```

This provides a comprehensive breakdown useful for troubleshooting and data verification.

## User Experience Guidelines

1. **Progressive Disclosure:**
   - Show summary stats always
   - Show unmatched events prominently (they require action)
   - Show pending payments section
   - Make other sections collapsible

2. **Visual Hierarchy:**
   - Most important: Unmatched events (requires action)
   - Important: Pending payments (needs awareness)
   - Informational: Cancelled red, supervision, empty title

3. **Actionability:**
   - Unmatched events → Link to "Add Client" screen
   - Pending payments → Just informational (will auto-resolve next period)
   - Cancelled red → Confirms they're excluded (reassurance)

4. **Color Coding:**
   - Unmatched: Orange/Yellow (warning, action needed)
   - Pending Payment: Blue/Grey (informational)
   - Cancelled Red: Light Red (informational, excluded)
   - Supervision: Purple/Blue (special category)
   - Empty Title: Light Grey (low importance)

## Testing Checklist

After implementation, test with:

1. **Normal Payroll:**
   ```
   POST /api/payroll/calculate
   {
     "employeeId": "-991962534",
     "startDate": "2024-11-01T00:00:00",
     "endDate": "2024-11-15T23:59:59",
     "syncToSheets": false
   }
   ```

2. **Event Analysis:**
   ```
   GET /api/debug/analyze/-991962534?weeks=3
   ```

3. **Verify:**
   - All event categories display correctly
   - Unmatched events are prominent
   - Pending payments show grey color indicator
   - Cancelled red shows red color indicator
   - Supervision events show special icon
   - Totals match between summary and detailed breakdown

## Reference Documents

1. **BACKEND_API_DOCUMENTATION.md** - Complete API reference
   - Line 390: `/api/payroll/calculate` endpoint
   - Line 569: `/api/debug/analyze` endpoint
   - Full request/response examples

2. **Color-Based Event Detection:**
   - Grey (colorId=8): Pending payment
   - Red (colorId=11): Cancelled, will NOT be paid
   - See `application.properties` lines 11-15

## Success Criteria

✅ Event tracking section displays all categories correctly
✅ Unmatched events are visually prominent and actionable
✅ Pending payments (grey) clearly distinguished from cancelled red
✅ Supervision events show payment status
✅ UI is clean and doesn't overwhelm the user
✅ Progressive disclosure works (collapsible sections)
✅ All data from `eventTracking` field is utilized

## Key Insight

**Why This Matters:**
> "It's important to track cancelled appointments both grey and red, supervisions, and events that don't match - i.e., new clients that haven't been registered!" - User

The unmatched events feature is critical because it helps discover:
- New clients that need to be added to the database
- Typos in client names in calendar
- Events that need attention

The grey vs red cancellation distinction prevents:
- Double charging clients (if grey event is paid twice)
- Missing payments (if grey event is not tracked)

## Notes

- The backend automatically fetches 3 weeks of events but only calculates payroll for 2 weeks
- This 3-week window allows cross-checking pending payments from the previous week
- The payroll totals only include events from the 2-week payroll period
- Events outside the payroll period are tracked for analysis but not included in totals

## Questions?

Refer to:
1. BACKEND_API_DOCUMENTATION.md for complete API details
2. The `/api/debug/analyze` endpoint for testing and verification
3. PayrollController.kt:445-483 for the eventTracking response structure
4. PayrollCalculationService.kt:180-246 for the categorization logic

---

**Ready to start?** Begin by updating the TypeScript interfaces to include the `eventTracking` field, then enhance the payroll results display component.
