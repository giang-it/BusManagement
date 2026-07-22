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
    *   A bus that has ever been assigned to a trip, or that has **any** incident recorded against it, cannot be hard-deleted — operational history is preserved; the bus is moved to `REPAIRING` instead. "Any" includes incidents already `RESOLVED`: the guard protects referential integrity, not open work. Because `Incident.bus` is mandatory, an incident cannot be unlinked from its bus, so the incident records must be deleted first if the bus itself is genuinely to be removed.

### Driver Management
*   **Purpose:** Maintain driver personnel records.
*   **Implemented Functionality:**
    *   CRUD management of Drivers (`AdminDriverController` / `DriverService`, entry point `/admin/drivers`): login account details, full name, contact, licence number, licence expiry, years of experience, and an active/locked switch.
    *   Creating a driver creates the backing `User` in the same form and forces its role to `ROLE_DRIVER` — `Driver` maps its primary key to `User.id` via `@MapsId` and cannot exist without one.
*   **Business Rules:**
    *   A driver who is locked (`isActive = false`), or whose licence will have expired by the departure date of the trip in question, is excluded from every trip assignment path (see Trip Management constraints). The "Hết hạn" badge on the driver list itself reports validity **today**, since that screen has no trip context.
    *   A driver cannot be locked while holding a trip in `PENDING_APPROVAL`, `ACTIVE`, or `DEPARTED` — the same "busy" definition used by `TripService.isDriverBusyInWindow()`.
    *   A driver who has ever been assigned to a trip, or who has any incident recorded against them (regardless of its status, as for a bus), cannot be hard-deleted (operational history is preserved); the driver is locked instead. Unlike a bus, `Incident.driver` is optional, so an incident can simply be unlinked from the driver ("Không xác định") rather than deleted. Deleting an unused driver removes the backing `User` too, via `User.driver`'s `cascade = ALL`.
    *   `monthlyRestDays` and `totalDrivingHours24h` are not editable through this module — see Known Behavioral Notes.

### Route & Station Management
*   **Purpose:** Define the geographical network of bus operations.
*   **Implemented Functionality:**
    *   CRUD management of Stations (name, address).
    *   CRUD management of Routes (`AdminRouteController` / `RouteService`, entry point `/admin/routes`): total distance in kilometres, estimated duration in minutes, an optional suitable bus type, and an ordered list of stops held in the join entity `RouteStation`. The form supports adding/removing stops; `stopOrder` is renumbered `1..n` from the submitted row order, so the first stop is the departure point and the last is the destination.
    *   A route has **no name field** — its display label is derived from its first and last stop (`Route.getDeparturePointDisplay()` / `getDestinationPointDisplay()`).
*   **Business Rules:**
    *   A route must have at least 2 stops (a departure point and a destination).
    *   A station may appear at most once per route: `RouteStation`'s primary key is the composite `(routeId, stationId)`, so a route revisiting the same station (e.g. A→B→A) is not representable.
    *   A route already used by any trip cannot be deleted (operational and revenue history is preserved). Deleting an unused route removes its `RouteStation` rows via `cascade = ALL`.

### Trip Management
*   **Purpose:** Coordinate specific dispatches of vehicles and staff.
*   **Implemented Functionality:**
    *   Trip scheduling and editing.
    *   Trip status monitoring.
    *   Resource allocation.
    *   Administrative management of trip lifecycle including creation, modification, approval, cancellation, and operational state transitions.
    *   Dynamic resource allocation API endpoint (`/api/admin/trips/available-resources`) that returns conflict-free available buses and drivers for a specified timeframe.

### Incident Management
*   **Purpose:** Record operational incidents reported to the control centre — the module matching the original proposal's "Báo cáo sự cố về trung tâm (xe hỏng, vấn đề trên đường)".
*   **Implemented Functionality:**
    *   CRUD management of Incidents (`AdminIncidentController` / `IncidentService`, entry point `/admin/incidents`): vehicle, incident type, optional trip, optional reporting/involved driver, free-text description, handling status, and a status count strip.
    *   `IncidentType`: `VEHICLE_BREAKDOWN`, `ACCIDENT`, `ROAD_ISSUE`, `STAFF_ISSUE`, `OTHER`.
    *   `IncidentStatus`: `OPEN`, `IN_PROGRESS`, `RESOLVED`.
*   **Business Rules:**
    *   An incident **must** reference a bus; the trip and the driver are optional (a bus can fail in the depot, outside any trip; and with authentication deferred, the reporter cannot always be identified).
    *   `IncidentStatus` is **not** governed by an FSM — unlike `TripStatus`, the Admin may move freely between all three values, including reopening a closed incident. The only enforced rule is that `resolvedAt` is stamped when the status becomes `RESOLVED` and cleared when it leaves `RESOLVED`; it is never entered by hand.
    *   `reportedAt` is stamped by Hibernate on insert (`@CreationTimestamp`, `updatable = false`) and never changes on edit.
    *   Recording an incident **does not** change the bus status. Taking a bus out of service remains a manual action in Fleet Management, where the existing rule "a bus with unfinished trips cannot be moved to `REPAIRING`" still applies.
    *   Incidents can be deleted without restriction — a mis-filed report is not operational history in the sense that a trip or a route is. This is also what keeps the Fleet Management guard escapable: an incident is the one end of that relationship the Admin is always free to remove.
    *   Conversely, an incident **blocks** hard-deletion of the bus (and of the driver) it references, so that no incident is left pointing at a record that no longer exists. This is enforced in `BusService`/`DriverService`, not by the database — the JDBC URL sets `foreign_key_checks=0`, so MySQL would not stop the orphan.
*   **Known Behavioral Note:** if a trip referenced by an incident is soft-deleted, `Trip`'s `@SQLRestriction` hides it from every query, so the incident silently displays as if it had no trip ("Ngoài hành trình") and re-saving it will clear `trip_id`. Nothing fails or throws.

### Dispatch Center
*   **Purpose:** A single operational board for the Administrator to run the day: what is on the road, what is about to leave, and what is late.
*   **Implemented Functionality:**
    *   Entry point `GET /admin/dispatch` (`DispatchController`), grouping trips into three sections: **Trễ Giờ** (still `ACTIVE` past its departure time), **Đang Trên Đường** (`DEPARTED`), and **Sắp Khởi Hành** (`ACTIVE` departing within the next 48 hours — the same window the Dashboard uses for its "Upcoming Trips" KPI).
    *   Quick status actions (Xuất phát / Hoàn thành / Hủy) posted to `/admin/dispatch/status`.
*   **Business Rules:** This module defines none of its own. Every status change is delegated to `TripService.updateTripStatus()`, so the FSM whitelist and the bus-status/odometer side effects behave exactly as they do elsewhere; invalid transitions are rejected there and surfaced to the Admin as an error.

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

### Driver Recommendation
*   **Purpose:** Answer *"who still has capacity to take a trip on this date?"* — the inverse of the "busiest drivers today" ranking already on the Analytics Drivers tab. That ranking is monitoring; this is a staffing recommendation.
*   **Implemented Functionality:**
    *   Entry point: `GET /admin/analytics/driver-recommendation?date=yyyy-MM-dd` (`DriverRecommendationController`), backed by a read-only `DriverRecommendationService`. The date parameter is optional and defaults to today. Read-only — the screen recommends; it assigns nobody and creates nothing.
    *   Lists active drivers who still have driving-hour budget left on the selected date, sorted by **assigned hours ascending** (most available first), with each driver's remaining capacity, licence expiry, experience and a plain-language reason for being listed.
    *   **The filters mirror exactly what `validateStaffForTrip()` will enforce at assignment time** — active, licence valid **on the selected date**, and daily-hour budget remaining — so the list never recommends someone the system would then reject.
    *   Reports three counts alongside the list (active drivers considered, excluded for no remaining hours, excluded for expired licence) so a short list is self-explaining rather than looking like a fault.
*   **Reused Thresholds (not redefined):** all driving hours come from `TripService.getDrivingHoursForDate()` — the single implementation that already encodes co-driver hour-splitting, the "assistant counts as 0 hours" convention, and the mock `totalDrivingHours24h` baseline. No second workload calculation exists. The 8-hour daily limit is restated locally rather than extracted into a shared constant, because in `TripService` that literal carries three distinct business meanings that merely share a value.
*   **Known Gaps (by design, not oversight):** Ranking is by workload only — experience and licence expiry are displayed for the Administrator to weigh, not scored. The screen performs no assignment, so acting on a recommendation is still a manual step through the normal trip forms.

### Demand Forecast
*   **Purpose:** Predict how full each route/departure-slot will be over the coming week, so the Administrator can plan reinforcement before demand materialises. This is the system's only genuinely predictive component; every other module reports what has already happened.
*   **Implemented Functionality:**
    *   Entry point: `GET /admin/analytics/forecast` (`ForecastController`), backed by a read-only `ForecastService` (`@Transactional(readOnly = true)`). Read-only — the screen forecasts and recommends; it creates no trip and assigns nobody.
    *   **Unit of forecast:** one series per (route × departure hour). A group is forecast only if it has at least **28 completed trips** (four full weeks), so every weekday is represented; groups below that are counted and reported rather than silently dropped.
    *   **What is predicted:** the **occupancy rate**, deliberately the same quantity as `Trip.getOccupancyRate()`, so a predicted value can be compared against the *existing* 90% reinforcement threshold rather than a new one.
    *   **Method:** the historical series is de-seasonalized by a day-of-week index, a least-squares linear trend is fitted to the de-seasonalized values, and the prediction re-applies the day-of-week factor — giving `(level + slope × days) × dayFactor`, clamped to 0–100%. A 7-calendar-day moving average is shown alongside as the current baseline level.
    *   **Self-assessed accuracy:** the service holds out the last 14 days of history, retrains on the remainder (**including recomputing the day-of-week index from the training window only**, so the held-out period cannot leak into the model), and reports its mean absolute percentage error next to that of a naive "keep the moving average flat" baseline. Whether the model beats the baseline is stated explicitly, in both directions.
    *   **Input filter:** `COMPLETED` trips only. Cancelled trips retain the tickets they had sold before cancellation, so including them would mix unserved demand into the actuals.
*   **Reused Thresholds (not redefined):** the "needs reinforcement" flag on a forecast cell uses the same 90% occupancy threshold as `Trip.needsReinforcement()` and `DashboardService`.
*   **Known Gaps (by design, not oversight):** The historical data is currently **simulated** (generated by the `backfill` profile because real operating history cannot accumulate within the project timeline), and the page states this prominently — the figures demonstrate the method, not real trading. The dataset also has a fixed end date and grows stale as time passes; the page reports how stale it is. Forecasts are per route/slot only — no per-station, per-bus-type or price-sensitivity dimension.

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
    *   The driver (main, co-driver, or assistant) must hold a driver licence that is still valid **on the trip's departure date** (`licenceExpiryDate > departureDate`) — not merely valid on the day the assignment is made. A trip departing after the licence expires is rejected even if the licence is still valid today.
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
*   **Unused driver fields:** `Driver.monthlyRestDays` is persisted but read nowhere — the original proposal's "at least 2 rest days per month" constraint has never been implemented. `Driver.totalDrivingHours24h` *is* used (`TripService.getDrivingHoursForDate()` adds it as baseline hours) but is documented in that method as mock seed data intended to be fed by an external IoT/GPS integration. Neither is exposed in the driver management form, to avoid implying an effect that does not exist.
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
