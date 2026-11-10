# PROJECT CONTEXT & TECH STACK

## 1. Source of Truth
*   **The implementation is authoritative.** The active Java source code is the only definitive reference for the system's design and business logic.
*   **Documentation is a secondary summary.** This document acts as an overview. If any conflict or discrepancy arises between this document and the source code, you must follow the code.

## 2. Project Purpose
The **Bus Management System** is a Spring Boot application designed to manage bus fleets, routes, drivers, and schedule trip operations. Its core feature is a **Smart Scheduling & AI Recommendation engine** that automatically flags high-occupancy trips, schedules extra trips, and assigns conflict-free buses and drivers based on operational rules. On top of the operations sits a read-only **Decision Support** layer (Section 11): driver recommendation, demand forecast, reinforcement recommendation with cost and profit, vehicle replacement ranking and what-if simulation. "AI" in this codebase means rule-based automation and, for the forecast, a hand-written statistical model — not machine learning.

## 3. Tech Stack
*   **Backend Framework:** Java, Spring Boot
*   **Data Persistence:** Spring Data JPA & Hibernate, MySQL database
*   **View Layer:** Thymeleaf (HTML templates)
*   **Task Automation:** Spring Scheduler (`@Scheduled`)
*   **Security Configuration:** Spring Security on the classpath, permit-all by code (`SecurityConfig`; no login, no user store — see `docs/architecture/setup_guide.md` §5)

## 4. Architecture & Package Structure
The application follows a standard Layered Architecture. The root package is `giang.com.BusManagement`.

*   `giang.com.BusManagement.config`: `SecurityConfig` (permit-all), `DataInitializer` (demo seed, `@Profile("demo")`), and the simulated-history generator `HistoricalDataBackfill` + `HistorySimulation` (`@Profile("backfill")`).
*   `giang.com.BusManagement.domain`: JPA Entities representing the database schema (`Bus`, `BusType`, `Trip`, `Driver`, `Route`, `Station`, `RouteStation`, `User`, `Incident`, `CostParameters`) and their enums; also `Holiday` + `HolidayCalendar`, the holiday calendar the forecast uses (plain Java, no table), and `HolidayFlow` (`Route.holidayFlow`).
*   `giang.com.BusManagement.repository`: Spring Data JPA repositories.
*   `giang.com.BusManagement.service`: Core business logic layers (`TripService`, `BusService`, `DriverService`, `RouteService`, `StationService`, `IncidentService`, `AdminService`, `DashboardService`) and the Decision Support services (`DriverRecommendationService`, `ForecastService`, `RecommendationService`, `VehicleReplacementService`, `CostParameterService`, `WhatIfSimulationService`). `SlotDemandModel` and `ArrowSolver` are the forecast's package-private math core; `AvailabilityContext` is a data-only schedule preload used by Recommendation.
*   `giang.com.BusManagement.controller`: Controllers managing web views and REST endpoints. Admin routes are grouped in the `.admin` subpackage.
*   `giang.com.BusManagement.dto`: Read-only view-model classes for the Dashboard & Analytics module (Section 10) and the Decision Support pages (Section 11) — not a general-purpose response wrapper for other controllers.

## 5. Database Layer & Persistence
*   **Naming Conventions:** Table names are in plural `snake_case` forms (e.g., `buses`, `drivers`, `trips`).
*   **Soft Deletion:** The system uses soft deletes for `Trip` entities through `@SQLDelete` and `@SQLRestriction("is_deleted = false")`. Physical deletions of trips are not allowed in the database.
*   **Schema Strategy:** The **default** profile is persistent (`ddl-auto=update`) — data survives restarts and `DataInitializer` does not run. The **`demo`** profile (`-Dspring-boot.run.profiles=demo`) regenerates the schema (`ddl-auto=create-drop`) and runs `DataInitializer` (`@Profile("demo")`) to wipe and reseed mock database assets. The **`backfill`** profile keeps the data and adds the **simulated** trip history (from 01/01/2023) that the forecast learns from. Tests use a separate database (`busmanagement_test`) via `src/test/resources/application.properties`. See `docs/architecture/setup_guide.md` Section 4.
*   **Timestamps:** Two entities carry a `@CreationTimestamp` — `Trip.createdAt` (column `created_at`, added in Phase 0; rows created before the column was introduced hold `NULL`) and `Incident.reportedAt` (column `reported_at`, added in Phase 2). Both are `updatable = false`. `Incident.resolvedAt` is a business milestone set by `IncidentService`, and `Trip.saleOpenedAt` one set by the FSM — neither is a technical audit column. The only update timestamp is `CostParameters.updatedAt` (`@UpdateTimestamp`, Phase 7 step 3), shown as "rates as of" on the Recommendation page. No other entity has creation/update timestamps.

## 6. Security Architecture (Deferred)
*   **Current State:** User authentication and authorization checks are **intentionally deferred** to a later phase. 
*   **AI Developer Constraint:** Do NOT assume that JWT, OAuth2, or role-based check contexts exist. Do NOT attempt to read `SecurityContextHolder.getContext().getAuthentication()` or build code around authenticated user sessions. The security layer is currently set to permit all requests, and any future authentication setup is outside the scope of current tasks.

## 7. Smart AI Scheduling & Resource Allocation
*   **Scheduler Responsibility:** The system uses a background task manager to automatically scan for "hot" active trips (e.g., based on business rules and time-to-departure conditions) and recommend extra trips.
*   **Auto-Assignment Rules:**
    *   **Buses:** Must be in a ready status, not busy in overlapping trip timeframes (including a preparation buffer), and must have sufficient odometer capacity remaining before reaching their maintenance threshold.
    *   **Drivers:** Must be active, possess valid driver licenses, not be double-booked in overlapping timeframes (including rest buffers), and must not exceed daily driving hour limits.
    *   **Long-Haul Trips:** Trips exceeding a business-defined duration threshold require co-drivers (assigned to meet minimum driver count constraints) and an Assistant. Assistant roles support the crew but do not have daily driving hour limits applied.
*   **Validation API — two shapes, one rule set:** the assignment rules above are implemented once, in `TripService`'s private `validateBusForTrip()` / `validateStaffForTrip()`, and exposed in two ways. The **throw-based** flows (`createManualTrip`, `updateManualTrip`, `approveTrip`, `confirmAutoAssignedTrip`) raise `IllegalArgumentException`, which Controllers surface as a flash message. The **dry-run** flows (`validateBusForTripDryRun` / `validateStaffForTripDryRun`, added in Phase 3) return a `ValidationResult` instead, for callers where an invalid resource is an expected outcome rather than an error — Decision Support in particular.
*   **AI Developer Constraint:** the dry-run methods are pure `try`/`catch` wrappers over the same private validators — never duplicate a rule into them, and never add a rule that exists in only one of the two shapes. Because the validators are fail-fast, a `ValidationResult` carries **at most one** failure reason (the first rule violated); this is deliberate, not a gap. Reporting every violation at once would require rewriting the validators, which `THESIS_ROADMAP.md` forbids.

## 8. Trip Lifecycle & FSM
*   **FSM Implementation:** Trip status transitions are governed by a Finite State Machine (FSM). `TripService` is the **authoritative implementation** of this FSM. Always inspect `TripService.java` before modifying or referencing lifecycle states.
*   **High-Level Transition Flow:**
    *   `PENDING_APPROVAL` → `ACTIVE` | `CANCELLED`
    *   `ACTIVE` → `DEPARTED` | `CANCELLED`
    *   `DEPARTED` → `COMPLETED`
    *   `COMPLETED` and `CANCELLED` are terminal states.
*   **Bus Status Synchronization:** Bus statuses (e.g., `TRAVELING`, `READY`) are synchronized automatically in response to trip state changes.

## 9. AI Development Constraints

### When implementing new functionality

When implementing a genuinely new feature:

* Reuse existing services whenever possible.
* Reuse existing repositories whenever possible.
* Reuse existing DTO patterns whenever possible.
* Reuse existing validation, response, and architectural patterns whenever possible.

Only create new services, repositories, DTOs, repository methods, response models, or other supporting components when they are genuinely required by the new feature and no equivalent implementation already exists in the codebase.

Avoid creating duplicate abstractions, parallel architectures, or alternative implementations for problems that are already solved.

---

### AI must NOT invent implementation details

Unless they are explicitly required by the current task or already exist in the implementation, AI assistants must **NOT** invent:

* Business rules or operational policies
* Entity fields, enums, or status values
* FSM states or transitions
* Database tables, columns, or relationships
* Scheduler behavior or scheduling rules
* Validation rules or constraints
* API routes or API contracts
* Assumptions about security, authentication, or authorization

If the implementation or requirements are unclear, stop and ask the user instead of making assumptions.

Always inspect the current implementation in the codebase first. If a rule or feature is not explicitly defined in the active code, stop and ask the user for clarification.

## 10. Dashboard & Analytics (Reporting)
*   **Purpose:** A read-only reporting page for the Administrator, built entirely from data already produced by the modules above. It does not introduce new business rules, entities, or database columns.
*   **Entry point:** `GET /admin/analytics` (`DashboardController`), backed by `DashboardService` (`@Transactional(readOnly = true)`).
*   **Structure:** Two groups — **Operational KPIs** (Pending Approvals, Upcoming Trips, Active Trips, Maintenance Alerts, AI Suggestions Pending, Available Resources; always visible) and **Strategic Analytics** (Fleet, Trips, Routes, Drivers, Occupancy, AI Recommendation; shown in Bootstrap tabs), each backed by DTOs in `giang.com.BusManagement.dto`.
*   **AI Developer Constraint:** This module reuses existing thresholds verbatim — e.g., the 90% "hot trip" occupancy threshold from `Trip.needsReinforcement()`, and the 7-day "license expiring soon" window already used in `TripService`'s driver auto-assignment. Do not invent new thresholds for Dashboard metrics; if a KPI has no existing basis in the code, treat it as unsupported rather than approximating it.
*   **Known, deliberate gap:** No "Recent Activity"/audit-trail widget exists — it was explicitly declined for this feature. (The original reason given, "no entity has creation/update timestamps", stopped being true in Phase 0/Phase 2; the decision to decline the widget still stands on its own.) Do not assume such a field exists or add one without being asked.

## 11. Decision Support (Phases 4, 6, 7, 8)
*   **Read-only, recomputed on every view:** none of these pages writes to the database or creates a trip; the Admin decides. Each lives under `/admin/analytics/…`, and each controller injects its service only (the `AdminBusController` pattern).
*   **Pages:** Driver Recommendation (`/driver-recommendation`, drivers with driving hours left on a chosen date), Demand Forecast (`/forecast`), Reinforcement Recommendation (`/recommendation`), Vehicle Replacement (`/vehicle-replacement`, buses ranked 70 % lifetime odometer / 30 % vehicle incidents), What-if (`/what-if`). Cost rates are edited at `/admin/cost-parameters` (`CostParameters`, a single row).
*   **Demand Forecast (`ForecastService`, redesigned October 2026 — decisions QĐ-1…QĐ-17 in `forecast_decisions.md`):** forecasts passenger **demand per slot** (route × departure hour × day, every `COMPLETED` trip of the slot summed), not an occupancy rate. Model: `SlotDemandModel`, a calendar Poisson regression with ridge shrinkage and an EM step for sold-out slots, hand-written in Java (no ML library — Non-Goal §4). 7 days for cards, 35-day preview, a self-check against a 4-week seasonal-naive baseline. The fit is cached in memory until the history changes; no scheduler.
*   **One definition of "hot":** a slot needs reinforcement when forecast demand > 90 % of the route's standard bus (`Route.suitableBusType`, else the fleet's average capacity) — `Trip.needsReinforcement()`'s threshold applied to forecast demand. Trips needed = max(1, ⌈demand ÷ (0.9 × standard seats)⌉), computed through that flag (`ForecastService.tripsNeeded`).
*   **Recommendation (`RecommendationService`) is an orchestrator:** one card per extra trip a hot slot needs; an extra trip earns only the **expected overflow** that falls into its own seats (`ForecastService.extraTripPassengers`); bus and driver come from `TripService.selectBestAvailableBus/Driver` on an `AvailabilityContext`, are re-checked by the Phase 3 dry-run validators, and are then reserved in that context so later cards cannot reuse them. Cost = fuel/km × distance + wage/h × hours × drivers, from `CostParameters`.
*   **What-if (`WhatIfSimulationService`) stays aggregate:** it scales forecast passengers, counts extra trips through `ForecastService.tripsNeeded`, compares them with a cycle-time capacity per day, and never calls the assignment engine.
*   **AI Developer Constraint:** do not re-declare the 0.90 threshold in a new class, do not price an extra trip as a full bus, and do not make these pages write anything. The historical data is **simulated**; say so wherever forecast figures are shown.