# Frontend Development Session - Payroll App UI Enhancement

## Context

This is a continuation of the PayrollApp backend development. The backend now has comprehensive event tracking and **correct pending payment logic** that needs to be integrated into the desktop application UI.

## Critical Business Logic: Pending Payments

### How Pending Payments Work

**Grey Cancelled Events (colorId="8")** = Pending Payment
- Client cancelled but will pay next time they come
- **In current period:**  - Counts as +1 session
  - Counts as +€0 revenue (NOT PAID YET!)
  - Shows status "Pending Payment"
- **In next period when they come:**
  - Counts as +1 session (the pending from before)
  - Counts as +€40 revenue (NOW PAID!)
  - One of their current sessions is marked "Paid for pending [date]"

**Red Cancelled Events (colorId="11")** = Cancelled (NOT paid)
- Client cancelled and will NOT pay
- Counts as +0 sessions
- Counts as +€0 revenue
- Does not carry over to next period

### Examples

#### Example 1: Grey in Current Period
```
Period: 1/1 - 15/1
  ⏳ 1/1 10:00 - Cancelled (Pending Payment - Grey) - €0
  ✅ 7/1 14:00 - Completed - €40

Sessions: 2 (1 pending + 1 completed)
Revenue: €40 (only the completed session!)
Note: 1 pending payment will be charged next time client comes
```

#### Example 2: Paying the Pending
```
Period: 15/1 - 30/1 (client has 1 pending from 1/1)
  ✅ 17/1 10:00 - Completed (Paid for pending 1/1) - €40
  ✅ 24/1 14:00 - Completed - €40

Sessions: 3 (1 from pending + 2 current)
Revenue: €120 (€40 for pending + €40 + €40)
✅ Paid 1 pending payment from previous period
```

#### Example 3: Edge Case - Multiple Pending, Fewer Sessions
```
Previous Period: 2 pending (1/1, 7/1)
Current Period: 1 completed (17/1)
  ✅ 17/1 10:00 - Completed (Paid for pending 1/1) - €40

Sessions: 2 (1 from pending + 1 current)
Revenue: €80 (€40 for pending + €40)
⚠️ Warning: Client still owes 1 pending payment (from 7/1)
```

---

## Current System State

### Backend Features (✅ COMPLETED)

1. **Correct Pending Payment Calculation**
   - Grey cancelled: +1 session, +€0 revenue (until paid)
   - When paid: +1 session, +€40 revenue in next period
   - Tracks which sessions paid for which pending
   - Handles edge cases (multiple pending, unresolved pending)

2. **Extended Event Fetching**
   - System fetches **3 weeks** of events (1 week before + 2 week payroll period)
   - Calculations only for 2-week period
   - 3rd week used for cross-checking pending payments

3. **Comprehensive Event Tracking**
   - Unmatched events (potential new clients)
   - Supervision events with special pricing
   - Empty title events (availability hours)

### Tech Stack

- **Backend:** Spring Boot 3.2.1 + Kotlin, running on http://localhost:8080
- **Desktop App:** Electron + TypeScript (existing)
- **Database:** H2 (in-memory), synced from Google Drive Excel
- **External APIs:** Google Calendar API, Google Sheets API, Google Drive API

---

## Your Task

Enhance the desktop application to display pending payment information correctly in the payroll results screen.

## Requirements

### 1. Display Client Sessions with Pending Payment Status

For each client in the payroll breakdown, show:

```
Client: Ζωή Κουσουλού
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Sessions: 3 | Paid: €120
  Completed: 2 | Pending: 0 | Paid from previous: 1

Events:
  ✅ 17/1 10:00 - Completed (Paid for pending 1/1) - €40
  ✅ 24/1 14:00 - Completed - €40

Total for this client: €120
✅ Note: Includes €40 from pending payment (1/1)
```

**Key Display Elements:**
- **Sessions Count:** Total (completed + pending + paid from previous)
- **Revenue:** Only PAID sessions (excludes pending in current)
- **Session Breakdown:**
  - Completed in current period
  - Pending in current period (grey, not paid yet)
  - Paid from previous period (how many pending got paid)
- **Event List:** Show all events with clear status indicators
- **Notes:** Highlight when pending payments were included

### 2. Show Pending Payments Clearly

When a client has pending payments in current period:

```
Client: Άννα Παπαδοπούλου
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Sessions: 2 | Paid: €40
  Completed: 1 | Pending: 1 | Paid from previous: 0

Events:
  ✅ 1/1 10:00 - Completed - €40
  ⏳ 7/1 14:00 - Cancelled (Pending Payment) - €0

Total for this client: €40
⚠️ Note: 1 pending payment (€40) - will be charged next time
```

**Visual Treatment:**
- Grey/blue background for pending events
- Show €0 for pending (not paid yet)
- Clear note that it will be paid next time

### 3. Handle Unresolved Pending Payments

When a client owes pending but didn't come:

```
Client: Μαρία Γεωργίου
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Sessions: 1 | Paid: €80
  Completed: 1 | Pending: 0 | Paid from previous: 1

Events:
  ✅ 17/1 10:00 - Completed (Paid for pending 1/1) - €40

Total for this client: €80
⚠️ Warning: Client still owes 1 pending payment (from 7/1)
```

**Visual Treatment:**
- Orange/yellow warning indicator
- Clear message about unresolved pending

### 4. Show Unmatched Events

At the bottom of the payroll results:

```
❓ UNMATCHED NAMES - Add to Database
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
These names don't match any client in the database:

- Νέος Πελάτης
- Άλλος Πελάτης Β
- Test Client

[Button: Manage Clients →]
```

**Visual Treatment:**
- Orange/yellow background (requires action)
- Simple list of names only (no dates/times)
- Action button to go to client management

---

## API Integration

### Updated Data Structures

```typescript
interface ClientPayrollDetail {
  clientName: string;
  pricePerSession: number;
  employeePricePerSession: number;
  companyPricePerSession: number;
  sessions: number;                   // Total (completed + pending + paid from previous)
  totalRevenue: number;               // Only PAID sessions
  employeeEarnings: number;
  companyEarnings: number;
  eventDetails: EventDetail[];
  completedSessions: number;          // NEW: Completed in current
  pendingSessions: number;            // NEW: Pending in current (not paid)
  paidPendingCount: number;           // NEW: Paid from previous period
  unresolvedPendingCount: number;     // NEW: Still owe from previous
}

interface EventDetail {
  date: string;
  time: string;
  duration: string;
  status: string;                     // "completed", "pending_payment", "paid_for_pending"
  colorId: string;
  isPending: boolean;                 // NEW: Is this pending (grey)?
  paidForPending: boolean;            // NEW: Did this pay for previous pending?
  pendingDate: string | null;         // NEW: Date of pending it paid for
}

interface EventTracking {
  totalEvents: number;
  matchedEvents: number;
  unmatchedEvents: UnmatchedEvent[];  // Just names to add
  cancelledGrey: CancelledEvent[];
  cancelledRed: CancelledEvent[];
  supervision: SupervisionEvent[];
  emptyTitle: number;
}

interface UnmatchedEvent {
  title: string;                      // The name to add to database
  date: string;
  time: string;
  colorId: string;
  status: string;
}
```

### Status Values

- `"completed"` - Normal completed session
- `"pending_payment"` - Grey cancelled in current period (will be paid next time)
- `"paid_for_pending"` - This session paid for a previous pending
- `"cancelled"` - Red cancelled (not paid)

---

## UI Layout

### Payroll Results Screen Structure

```
┌─────────────────────────────────────────────────┐
│ Employee: Αναστασία Καλαμποκά                  │
│ Period: 1/1 - 15/1                             │
│                                                 │
│ SUMMARY                                        │
│ Total Sessions: 25                             │
│ Total Revenue: €980                            │
│   Employee: €450                               │
│   Company: €530                                │
├─────────────────────────────────────────────────┤
│ CLIENT BREAKDOWN                               │
│                                                 │
│ ┌─ Ζωή Κουσουλού ──────────────────────┐      │
│ │ Sessions: 3 | Paid: €120              │      │
│ │   Completed: 2 | Pending: 0 | Paid: 1│      │
│ │                                        │      │
│ │ ✅ 17/1 10:00 - Completed (Paid for   │      │
│ │              pending 1/1) - €40       │      │
│ │ ✅ 24/1 14:00 - Completed - €40       │      │
│ │                                        │      │
│ │ Total: €120                           │      │
│ │ ✅ Includes €40 from pending (1/1)    │      │
│ └────────────────────────────────────────┘      │
│                                                 │
│ ┌─ Άννα Παπαδοπούλου ──────────────────┐      │
│ │ Sessions: 2 | Paid: €40               │      │
│ │   Completed: 1 | Pending: 1 | Paid: 0│      │
│ │                                        │      │
│ │ ✅ 1/1 10:00 - Completed - €40        │      │
│ │ ⏳ 7/1 14:00 - Pending Payment - €0   │      │
│ │                                        │      │
│ │ Total: €40                            │      │
│ │ ⚠️ 1 pending (€40) - will charge next│      │
│ └────────────────────────────────────────┘      │
│                                                 │
│ [More clients...]                              │
├─────────────────────────────────────────────────┤
│ ❓ UNMATCHED NAMES                             │
│                                                 │
│ - Νέος Πελάτης                                │
│ - Άλλος Πελάτης                               │
│                                                 │
│ [Manage Clients →]                            │
└─────────────────────────────────────────────────┘
```

---

## Visual Design Guidelines

### Color Coding

- **Completed (✅):** Green - `#22c55e`
- **Pending Payment (⏳):** Blue/Grey - `#64748b`
- **Paid for Pending (✅💰):** Green with note - `#10b981`
- **Cancelled Red (❌):** Light red - `#ef4444` (low opacity)
- **Unmatched (❓):** Orange - `#f59e0b`
- **Warning (⚠️):** Yellow/Orange - `#eab308`

### Typography

- **Client Name:** Bold, 16px
- **Session Count:** Regular, 14px
- **Event Details:** Regular, 12px
- **Notes:** Italic, 12px
- **Warnings:** Bold, 12px

### Spacing

- Section padding: 16px
- Client card margin: 8px
- Event spacing: 4px
- Note margin-top: 8px

---

## Testing Checklist

After implementation, test with:

1. **Normal Payroll (No Pending):**
   ```
   POST /api/payroll/calculate
   {
     "employeeId": "-991962534",
     "startDate": "2025-01-01T00:00:00",
     "endDate": "2025-01-15T23:59:59",
     "syncToSheets": false
   }
   ```

2. **With Pending in Current:**
   - Verify pending shows €0
   - Verify sessions count includes pending
   - Verify revenue excludes pending

3. **With Pending from Previous:**
   - Verify "Paid for pending [date]" shows
   - Verify revenue includes paid pending
   - Verify sessions count correct

4. **Edge Case - Unresolved Pending:**
   - Verify warning shows
   - Verify correct amounts

5. **Unmatched Events:**
   - Verify names show at bottom
   - Verify action button works

---

## Success Criteria

✅ Grey pending shows as session but €0 revenue
✅ When paid, shows "Paid for pending [date]" with €40
✅ Revenue totals are correct (exclude current pending, include paid pending)
✅ Session counts are correct (include all)
✅ Unresolved pending warnings display
✅ Unmatched names show clearly at bottom
✅ UI is clean and not overwhelming
✅ Status icons and colors are clear

---

## Key Business Rules

**CRITICAL:**
1. **Pending payment (grey) in current period:**
   - Counts as session ✅
   - Revenue is €0 ❌ (not paid yet)
   - Shows in event list as "Pending Payment"

2. **When client comes next time:**
   - Their first completed session "pays for" the pending
   - That session shows "Paid for pending [date]"
   - Revenue includes both: the pending (€40) + current session (€40)
   - Session count: +1 for pending + +1 for current = +2

3. **Multiple pending:**
   - Pay as many as they have completed sessions
   - min(pending count, completed count)
   - Unresolved = pending count - paid count
   - Show warning if unresolved > 0

4. **Revenue calculation:**
   - ONLY paid sessions count in revenue
   - Pending in current = €0
   - Paid pending from previous = €40 each

---

## Reference Documents

1. **BACKEND_API_DOCUMENTATION.md** - Complete API reference
   - Line 392: `/api/payroll/calculate` endpoint
   - Line 569: `/api/debug/analyze` endpoint

2. **Backend Logic:**
   - PayrollCalculationService.kt:112-166 - Pending payment calculation
   - PayrollController.kt:492-560 - Response creation with pending tracking

---

**Ready to start?** Begin by updating the TypeScript interfaces, then enhance the client payroll detail component to show pending payment information clearly.
