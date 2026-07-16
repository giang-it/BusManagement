# Functional Specification (Current Implementation)

This document describes the functional specifications and business rules of the Bus Management System as implemented in the active codebase. 

---

## 1. System Overview
The **Bus Management System** is a fleet scheduling and operations management system designed for administrators. Its core capability lies in managing vehicles, stations, routes, and scheduling trips under strict operational safety constraints (e.g., driver rest rules, bus maintenance thresholds). It includes a background AI recommendation engine that automatically recommends and prepares extra trips for administrator approval.

---

## 2. User Roles
Only the Administrator workflow is currently exposed through the application:
*   **Administrator (Admin):** Responsible for managing the vehicle fleet, defining routes and stations, manually creating and updating trips, reviewing and approving AI-suggested trips, and driving the operational status of trips through a Finite State Machine (FSM).

*Note: Client/Passenger and Driver/Staff frontend portals are intentionally deferred and not implemented.*

---

## 3. Functional Modules

### fleet & Vehicle Management
*   **Purpose:** Track and manage the physical bus fleet.
*   **Implemented Functionality:**
    *   CRUD management of Bus Types (defining standard seat capacities).
    *   CRUD management of Bus vehicles (license plate, brand, current status, current odometer, odometer at last maintenance, and maintenance threshold limit).
*   **Business Rules:**
    *   A bus has one of three statuses: `READY` (available for dispatch), `TRAVELING` (currently on a trip), or `REPAIRING` (under maintenance).
    *   Vehicles exceeding their maintenance thresholds cannot be assigned to trips.
    *   Bus status is automatically synchronized during trip lifecycle transitions.

### Route & Station Management
*   **Purpose:** Define the geographical network of bus operations.
*   **Implemented Functionality:**
    *   CRUD management of Stations (name, address).
    *   CRUD management of Routes (route name, total distance in kilometers, and ordered list of stations using a sequence-based join entity `RouteStation`).

### Trip Management
*   **Purpose:** Coordinate specific dispatches of vehicles and staff.
*   **Implemented Functionality:**
    *   Trip scheduling and editing.
    *   Trip status monitoring.
    *   Resource allocation.
    *   Administrative management of trip lifecycle including creation, modification, approval, cancellation, and operational state transitions.
    *   Dynamic resource allocation API endpoint (`/api/admin/trips/available-resources`) that returns conflict-free available buses and drivers for a specified timeframe.

### AI Scheduling & Recommendations
*   **Purpose:** Automatically schedule extra trips to prevent capacity bottlenecks.
*   **Implemented Functionality:**
    *   A background process flags active trips experiencing high passenger demand (occupancy) and recommends a cloned "extra" trip.
    *   **Auto-Assignment:** The engine automatically attempts to assign an available `READY` bus and qualified drivers that meet all safety constraints. If it fails to find qualified resources, it creates the recommendation with empty resources for manual assignment by the Admin.
    *   **Duplicate-Suggestion Prevention:** Because the scan runs every 10 seconds, the engine checks `hasAlreadySuggested()` before cloning a trip, so a single hot trip does not spawn more than one live extra-trip suggestion. See the detailed rule below.

### Dashboard & Analytics
*   **Purpose:** A single, read-only reporting page for the Administrator, built entirely from data already produced by the other modules — no new business rules, entities, or database columns.
*   **Implemented Functionality:**
    *   **Operational KPIs** (always visible, top of page): Pending Approvals (with an AI-auto-assigned vs. needs-manual breakdown), Upcoming Trips (next 48 hours), Active Trips, Maintenance Alerts (overdue vs. near-threshold buses), AI Suggestions pending, and Available Resources (`READY` buses, active drivers).
    *   **Strategic Analytics** (tabbed): Fleet, Trips, Routes, Drivers, Occupancy, and AI Recommendation outcome breakdown — each with supporting cards/tables and one Chart.js chart (Bar or Doughnut).
    *   Entry point: `GET /admin/analytics` (`DashboardController`), backed by a read-only `DashboardService` (`@Transactional(readOnly = true)`).
*   **Reused Thresholds (not redefined):** "Hot trip" uses the same 90% occupancy threshold as `Trip.needsReinforcement()`; "license expiring soon" uses the same 7-day window already used by `TripService`'s driver auto-assignment.
*   **Known Gaps (by design, not oversight):** No "Recent Activity" feed — no entity has creation/update timestamps, and adding one was explicitly declined for this feature to avoid a schema change. Fleet utilization and AI-suggestion outcome counts are point-in-time snapshots, not historical trends.

---

## 4. Business Workflows & Constraints

### Trip Creation & Modification (Manual Workflow)
Manual trip creation bypasses the recommendation queue and sets the trip status directly to `ACTIVE`, initializing its ticket sale state.
During manual creation or modification, the backend enforces the following validation checks:

*   **Bus Availability Constraints:**
    *   The bus status must not be `REPAIRING` or `TRAVELING` (unless editing the currently assigned trip).
    *   **Double-Booking Check:** The bus must not be assigned to another trip overlapping with the window `[departure - 1 hour, arrival + 1 hour]` (preparation buffer).
    *   **Maintenance Block:** The bus cannot be assigned if it has already exceeded its maintenance threshold (`odometer - lastMaintenanceOdometer >= maintenanceThreshold`) or if the distance of the trip will push the odometer into the warning threshold (`odometer + distance >= maintenanceThreshold * 0.9`).
*   **Driver Availability Constraints:**
    *   The driver (main, co-driver, or assistant) must be active (`isActive = true`).
    *   The driver must hold a valid, unexpired driver license.
    *   **Double-Booking Check:** The driver must not be busy on another trip overlapping with the window `[departure - 30 minutes, arrival + 30 minutes]` (minimum rest buffer).
    *   **Daily Driving Limit:** The accumulated driving hours for a driver on a single calendar day (including the duration of the new trip, split equally among assigned drivers) must not exceed 8 hours.
*   **Long-Haul Crew Requirements:**
    *   Trips with an expected duration exceeding 8 hours **require** an Assistant and a minimum number of drivers calculated as `ceil(duration / 8.0)`.
    *   **Assistant Rules:** Assistant roles must be filled by registered drivers, but daily driving hour limits are not applied to their assistant duties. Assistants must still satisfy double-booking checks.
    *   **Personnel Duplication:** No driver can occupy multiple roles (e.g., serving as both the main driver and co-driver/assistant) on the same trip.

### AI Recommendation Workflow
The background scheduler evaluates demand and proposes additional trips through the following logical flow:
1.  **Scan for Active Trips:** Search the database for all active trips (`find ACTIVE`).
2.  **Evaluate Demand (`isHotTrip()`):** Identify trips that exceed passenger occupancy thresholds and time constraints (occupancy $> 90\%$, departs in the future, $\ge 72$ hours remain before departure, and tickets have been on sale for $\ge 48$ hours; the sale duration requirement is bypassed if occupancy hits $\ge 95\%$).
3.  **Check for Existing Suggestions (`hasAlreadySuggested()`):** Check whether the original trip already has an extra trip linked to it (via the `original_trip_id` self-reference) sitting in a "live" status, to prevent duplicates.
4.  **Create Recommendation (`createExtraTrip()`):** Generate a new suggestion in `PENDING_APPROVAL` status with a departure time offset by +30 minutes from the original trip.
5.  **Allocate Resources (`autoAssignResources()`):** Automatically attempt to allocate a free `READY` bus and qualified drivers satisfying all safety constraints.
6.  **Persistence (`save`):** Save the recommendation to the database.

*   **Hot Trip Flagging:** The background scheduler identifies active trips that meet the following thresholds:
    *   Occupancy is $> 90\%$, departure is in the future, at least 72 hours remain before departure, and tickets have been on sale for at least 48 hours.
    *   *Immediate Trigger:* If occupancy reaches $\ge 95\%$, the 48-hour sale requirement is bypassed.
*   **Recommendation Creation:** The system creates a suggestion entry in status `PENDING_APPROVAL` with a departure time offset by +30 minutes from the original trip.
*   **Duplicate-Suggestion Rule (`hasAlreadySuggested()`):**
    *   **Problem it solves:** The scheduler re-scans all `ACTIVE` trips every 10 seconds. Without a guard, a trip that stays hot across multiple scans would get a new cloned extra trip created on every cycle.
    *   **Mechanism:** The check looks up whether any `Trip` exists whose `originalTripId` points back to this trip AND whose status is in a blocking set — it is keyed to the specific original trip, not to route or time window.
    *   **Blocking statuses:** `PENDING_APPROVAL`, `ACTIVE`, `DEPARTED`, `COMPLETED`. Any of these means a suggestion is already "live," so no new extra trip is created.
    *   **`CANCELLED` is intentionally excluded from the blocking set.** If a previously AI-suggested extra trip for this original trip was rejected by the Admin (or later cancelled — e.g. the assigned bus broke down or the driver became unavailable), the underlying capacity problem may still exist. Previously cancelled AI-suggested trips therefore do **not** prevent future recommendations — the scheduler is allowed to propose a brand-new extra trip for the same original trip on a later scan instead of being permanently blocked by one dead, cancelled suggestion.

### Trip Status Transitions (FSM)
Trip status changes are controlled by a Finite State Machine (FSM) implemented in `TripService`.
*   **Whitelisted Transitions:**
    *   `PENDING_APPROVAL` $\rightarrow$ `ACTIVE` (Approved) | `CANCELLED` (Rejected)
    *   `ACTIVE` $\rightarrow$ `DEPARTED` | `CANCELLED`
    *   `DEPARTED` $\rightarrow$ `COMPLETED`
    *   `COMPLETED` and `CANCELLED` are terminal states and cannot transition further.
*   **Side Effects:**
    *   Transitioning `ACTIVE` $\rightarrow$ `DEPARTED` automatically changes the assigned bus status to `TRAVELING`.
    *   Transitioning `DEPARTED` $\rightarrow$ `COMPLETED` updates the bus status to `READY` and automatically appends the route distance to the bus's odometer.

### Soft Delete Workflow
*   Deleting a trip marks it as `is_deleted = true`.
*   Deleted trips are automatically filtered out from all queries via Hibernate `@SQLRestriction("is_deleted = false")`.

---

## 5. Scheduler
The application implements a Spring Scheduler (`@Scheduled`) to manage background processes. 
*   **Responsibility:** Periodically calls the AI recommendation engine to scan active trips, generate `PENDING_APPROVAL` trip recommendations, and trigger auto-assignment of resources.

---

## 6. Data Persistence & Seeding
*   **Database:** MySQL database mapped via Hibernate.
*   **Schema Structure:** Main entities include `users`, `drivers`, `buses`, `bus_types`, `trips`, `routes`, `stations`, and `route_stations`.
*   **Transactions:** State mutation operations are wrapped in Spring `@Transactional` annotations to guarantee database consistency.
*   **Database Seeding (`DataInitializer`):** A custom `DataInitializer` class seeds mock data (including test users, routes, stations, buses, and active trips) into the database on startup. It is annotated `@Profile("demo")` and therefore runs **only** under the `demo` profile, where it first deletes all existing rows and then reseeds. Under the default (persistent) profile it does not run at all.
*   **Data Retention Profiles:** The **default** profile uses `ddl-auto=update` — the schema is preserved and transactional records survive restarts. The **`demo`** profile uses `ddl-auto=create-drop` and wipes/reseeds everything, for a clean demonstration environment. Tests run against a separate `busmanagement_test` database and never touch real data.
*   **Timestamps:** `Trip.createdAt` (`@CreationTimestamp`, `updatable = false`) records when a trip row was inserted. It is distinct from the business-level `saleOpenedAt` (stamped by the FSM when a trip becomes `ACTIVE`). Trips inserted before this column existed have `NULL`.

---

## 7. Known Behavioral Notes
*   **Workflow Status Inconsistency:**
    *   *Manual Workflow:* Creating a trip manually places it directly into the `ACTIVE` status (stamping `saleOpenedAt = now()`), bypassing approval.
    *   *AI Workflow:* Auto-suggested extra trips are created in the `PENDING_APPROVAL` status and require explicit admin approval to transition to `ACTIVE`.

---

## 8. Error & Constraint Handling
*   **Validation Rejection:** Operations violating business constraints (such as double-booking of drivers or buses, expired driver licenses, rest buffer violations, or bus maintenance thresholds) are rejected at the service layer with validation errors.

---

## 9. Current Limitations
The following capabilities are intentionally deferred or omitted from the current system scope:
1.  **Authentication & Authorization:** Bypassed using a permit-all configuration. There is no active user authentication, JWT processing, or Role-Based Access Control (RBAC).
2.  **Client Ticketing & Payments:** Customers cannot search for trips, reserve seats, purchase tickets, or complete online payments.
3.  **Driver & Crew Logs:** No interface exists for drivers to log hours, view personal schedules, perform check-ins, or submit incident reports.
4.  **Notifications:** The system does not support sending email or SMS alerts, booking confirmations, or QR codes.
5.  ~~**Persistent Data Retention:** The database strategy runs in a recreate-on-startup mode (`create-drop`), which clears transactional records between restarts.~~ **Resolved:** the default profile now persists data (`ddl-auto=update`, `DataInitializer` gated to `@Profile("demo")`). Wiping requires explicitly running the `demo` profile. Note that no migration tool (Flyway/Liquibase) exists — schema evolution still relies on Hibernate's `update`.
6.  **No reporting or analytics:** The system lacks utilization charts, revenue analysis, or reports.
7.  **No audit logs:** Admin actions and historical configuration changes are not tracked in audit trails.
8.  **No activity/audit-log-based analytics:** A "Recent Activity" widget was considered for the Dashboard & Analytics module but intentionally omitted — no entity has `createdAt`/`updatedAt` columns, and adding one just for this feature was explicitly declined. A proper audit log would need to be designed separately.

---

## 10. Glossary
*   **ACTIVE:** The state indicating that a trip is active and open for ticket sales.
*   **DEPARTED:** The state indicating that the bus has departed and the trip is underway.
*   **HOT TRIP:** An active trip that has met passenger occupancy thresholds and time constraints, qualifying it for extra trip recommendations.
*   **EXTRA TRIP:** An additional trip generated by the system (or created manually) on the same route to relieve passenger demand on a hot trip.
*   **PENDING_APPROVAL:** The initial state of an AI-suggested extra trip, awaiting admin review.
*   **OPERATIONAL KPIs:** Dashboard metrics requiring immediate admin action (Pending Approvals, Upcoming Trips, Maintenance Alerts, etc.), always visible at the top of `/admin/analytics`.
*   **STRATEGIC ANALYTICS:** Dashboard metrics for periodic review (Fleet, Trips, Routes, Drivers, Occupancy, AI Recommendation), organized into tabs on `/admin/analytics`.
