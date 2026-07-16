# PROJECT CONTEXT & TECH STACK

## 1. Source of Truth
*   **The implementation is authoritative.** The active Java source code is the only definitive reference for the system's design and business logic.
*   **Documentation is a secondary summary.** This document acts as an overview. If any conflict or discrepancy arises between this document and the source code, you must follow the code.

## 2. Project Purpose
The **Bus Management System** is a Spring Boot application designed to manage bus fleets, routes, drivers, and schedule trip operations. Its core feature is a **Smart Scheduling & AI Recommendation engine** that automatically flags high-occupancy trips, schedules extra trips, and assigns conflict-free buses and drivers based on operational rules.

## 3. Tech Stack
*   **Backend Framework:** Java, Spring Boot
*   **Data Persistence:** Spring Data JPA & Hibernate, MySQL database
*   **View Layer:** Thymeleaf (HTML templates)
*   **Task Automation:** Spring Scheduler (`@Scheduled`)
*   **Security Configuration:** Spring Security (Fully bypassed for development purposes)

## 4. Architecture & Package Structure
The application follows a standard Layered Architecture. The root package is `giang.com.BusManagement`.

*   `giang.com.BusManagement.config`: System configurations (e.g., Security, CORS).
*   `giang.com.BusManagement.domain`: JPA Entities representing the database schema (e.g., `Bus`, `Trip`, `Driver`, `Route`, `Station`, `RouteStation`, `User`).
*   `giang.com.BusManagement.repository`: Spring Data JPA repositories.
*   `giang.com.BusManagement.service`: Core business logic layers (`TripService`, `BusService`, `DriverService`, `RouteService`, `StationService`, `DashboardService`).
*   `giang.com.BusManagement.controller`: Controllers managing web views and REST endpoints. Admin routes are grouped in the `.admin` subpackage.
*   `giang.com.BusManagement.dto`: Read-only view-model classes. Currently used only by the Dashboard & Analytics module (Section 10) — not a general-purpose response wrapper for other controllers.

## 5. Database Layer & Persistence
*   **Naming Conventions:** Table names are in plural `snake_case` forms (e.g., `buses`, `drivers`, `trips`).
*   **Soft Deletion:** The system uses soft deletes for `Trip` entities through `@SQLDelete` and `@SQLRestriction("is_deleted = false")`. Physical deletions of trips are not allowed in the database.
*   **Schema Strategy:** The **default** profile is persistent (`ddl-auto=update`) — data survives restarts and `DataInitializer` does not run. The **`demo`** profile (`-Dspring-boot.run.profiles=demo`) regenerates the schema (`ddl-auto=create-drop`) and runs `DataInitializer` (`@Profile("demo")`) to wipe and reseed mock database assets. Tests use a separate database (`busmanagement_test`) via `src/test/resources/application.properties`. See `docs/architecture/setup_guide.md` Section 4.
*   **Timestamps:** `Trip.createdAt` (`@CreationTimestamp`, column `created_at`) is the only technical audit timestamp in the schema; rows created before the column was introduced hold `NULL`. No other entity has creation/update timestamps.

## 6. Security Architecture (Deferred)
*   **Current State:** User authentication and authorization checks are **intentionally deferred** to a later phase. 
*   **AI Developer Constraint:** Do NOT assume that JWT, OAuth2, or role-based check contexts exist. Do NOT attempt to read `SecurityContextHolder.getContext().getAuthentication()` or build code around authenticated user sessions. The security layer is currently set to permit all requests, and any future authentication setup is outside the scope of current tasks.

## 7. Smart AI Scheduling & Resource Allocation
*   **Scheduler Responsibility:** The system uses a background task manager to automatically scan for "hot" active trips (e.g., based on business rules and time-to-departure conditions) and recommend extra trips.
*   **Auto-Assignment Rules:**
    *   **Buses:** Must be in a ready status, not busy in overlapping trip timeframes (including a preparation buffer), and must have sufficient odometer capacity remaining before reaching their maintenance threshold.
    *   **Drivers:** Must be active, possess valid driver licenses, not be double-booked in overlapping timeframes (including rest buffers), and must not exceed daily driving hour limits.
    *   **Long-Haul Trips:** Trips exceeding a business-defined duration threshold require co-drivers (assigned to meet minimum driver count constraints) and an Assistant. Assistant roles support the crew but do not have daily driving hour limits applied.

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
*   **Known, deliberate gap:** No "Recent Activity"/audit-trail widget exists, because no entity has creation/update timestamps; adding one was explicitly declined for this feature. Do not assume such a field exists or add one without being asked.