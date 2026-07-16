# THESIS_ROADMAP.md

> **PRIORITY NOTICE:** This document has higher priority than conversation memory for any Claude Code session working on this project.
>
> At the start of every new session:
> 1. Read this file completely before making any implementation decision.
> 2. Determine the current implementation phase from the "Current Progress" section.
> 3. Continue from the latest recorded progress. Never restart planning from scratch.
> 4. Never modify the roadmap (phase order, scope, or content) without explicit approval from the project owner.
> 5. This file is NOT end-user documentation. It is a long-term implementation tracker for this thesis project.

---

## 1. Thesis Vision

The project is evolving from a simple Bus Management System into a **thesis-level Bus Operations & Decision Support System**. The system is built around three pillars, in order of priority:

1. **Operations** (primary focus, ~70-80%) — Fleet Management, Driver Management, Trip Scheduling, Dispatch Center, Business Rule Validation, Incident Management, Analytics.
2. **Intelligent Decision Support** (~20-30%) — Demand Forecast, Recommendation Engine, Vehicle Replacement Recommendation, Driver Recommendation, Cost/Revenue Estimation, What-if Simulation.
3. **Customer Portal** (minimal scope, ~10-20%) — Search Trips, View Seat Availability, Simple Booking, Booking History. Goal is to prove the data flow, not build a full e-commerce experience.

## 2. Project Scope

- Backend: Java / Spring Boot 4, Spring Data JPA / Hibernate, MySQL, Thymeleaf, Spring Security (currently disabled/permit-all by design).
- The existing codebase (Fleet, Trip FSM, AI auto-assign, Dashboard & Analytics) is the **foundation**, not something to be rewritten. All new work builds on top of it.
- The system currently contains **rule-based automation**, not real AI. This distinction must be preserved and stated accurately in all documentation and in the thesis defense.

## 3. Architectural Principles (must be respected in every phase)

- Reuse existing `TripService` logic whenever possible. Do not duplicate business logic that already exists (e.g. `findBestAvailableBus()`, `findBestAvailableDriver()`, `validateBusForTrip()`, `validateStaffForTrip()`, `getDrivingHoursForDate()`).
- Do not create parallel architecture. New modules follow the existing layered pattern: Controller → Service → Repository → Domain.
- New admin controllers must follow the `AdminBusController` pattern (inject Service only) — **not** the `AdminTripManagementController` pattern (injects Repository directly alongside Service; this is a known, documented anti-pattern, not something to copy into new code).
- **Business Rule Validation** and **AI Prediction (Demand Forecast)** are two independent components. Business Rule Validation verifies operational constraints; AI Prediction forecasts future demand. Decision Support consumes outputs from both — neither is allowed to silently absorb the other's responsibility.
- **Recommendation Engine is an orchestrator, not a prediction engine.** It composes outputs from Demand Forecast, Resource Selection (which reuses existing rule+availability-filtered methods), Cost Estimation, and a final Business Rule Validation re-confirmation gate. It does not contain its own prediction or rule logic.
- "Business Rules" and "Resource Availability" are not separate sequential services in the implementation — in the existing code (`findBestAvailableBus()`) they are interleaved filters over the same candidate stream. Keep them as two conceptual boxes in diagrams/thesis writing, but do not implement them as two duplicated service layers.
- The final Business Rule Validation gate inside the Recommendation Engine pipeline reuses the same throw-based validators (`validateBusForTrip`/`validateStaffForTrip`) via a non-throwing dry-run wrapper — mirroring the existing double-check pattern already used in `TripService.confirmAutoAssignedTrip()`.
- Incident and Booking are independent entities referencing `Trip` via foreign key. Do not add their responsibilities as new fields directly on `Trip` — `Trip` already carries FSM + soft-delete + self-reference responsibilities and must not become a god-entity.
- What-if Simulation must stay at the aggregate/statistical level. Do not refactor `findBestAvailableBus()`/`findBestAvailableDriver()` into pure functions accepting hypothetical resource pools — this is the single highest refactoring-risk change identified in the whole roadmap and is explicitly rejected in favor of a narrower, statistics-based simulation.
- Every phase must leave the application in a runnable, independently testable state. No phase may be left half-implemented across a session boundary without being marked `IN PROGRESS` with clear notes.
- Minimize refactoring. Prefer additive changes (new entity, new service, new controller) over modifying existing, already-verified logic (especially anything inside `TripService`).

## 4. Non-Goals (explicitly out of scope unless separately approved)

- No real ML/deep learning model. Demand Forecast uses simple statistical methods (moving average / linear regression) written in plain Java — no new ML dependency.
- No full e-commerce features for Customer Portal (no wallet, voucher, loyalty points, online payment gateway).
- No seat-map / specific seat number selection — only aggregate seat availability (`totalSeats - ticketsSold`).
- No enabling of real Spring Security / RBAC login as part of this roadmap. `SecurityConfig` remains permit-all unless the project owner explicitly requests turning it on as a separate, standalone task.
- No notification system (email/SMS) unless separately requested (dependency `spring-boot-starter-mail` exists in `pom.xml` but is unused by design, for now).
- No deep What-if Simulation that re-runs the full resource-assignment engine against hypothetical inputs (see architectural principles above).

---

## 5. Roadmap (approved — do not reorder, redesign, or simplify without explicit owner approval)

### Phase 0 — Foundation
- **Objective:** Unblock historical data persistence; this is the prerequisite for the entire Decision Support pillar.
- **Scope:** Introduce a persistent profile (schema not wiped on every restart — currently `ddl-auto=create-drop` + `DataInitializer.run()` deletes and reseeds all tables on every startup); add `Trip.createdAt`.
- **Dependencies:** None.
- **Expected deliverables:** A working persistent profile (dev/demo), `Trip.createdAt` column added, existing flows unaffected.
- **Refactoring risk:** None.
- **Current status:** COMPLETED (2026-07-16)
- **As delivered:**
  - Default profile is now **persistent** (`ddl-auto=update`); wiping/reseeding requires explicitly opting in via `-Dspring-boot.run.profiles=demo` (`application-demo.properties`, `ddl-auto=create-drop`). Owner-approved decision: inverting the default so an accidental restart can never destroy the historical dataset.
  - `DataInitializer` gated with `@Profile("demo")` — this is the actual fix; changing `ddl-auto` alone was insufficient because the bean calls `deleteAll()` on every table regardless of DDL policy.
  - `Trip.createdAt` added via Hibernate `@CreationTimestamp` (column `created_at`, nullable, `updatable = false`).
  - **Scope addition approved during the phase:** `src/test/resources/application.properties` added to isolate tests onto a separate `busmanagement_test` database (auto-created via `createDatabaseIfNotExist=true`). Discovered during analysis that `mvnw test` was wiping the real database on every run — same root cause as the phase objective, so it was folded in with owner approval.

### Phase 1 — Operations Gaps (Driver Management, Route Management, Dispatch Center)
- **Objective:** Close known Operations gaps using existing, proven patterns.
- **Scope:**
  - `AdminDriverController` + `DriverService` (pattern: `AdminBusController`/`BusService`). Driver CRUD does not currently exist — only seeded via `DataInitializer`.
  - `AdminRouteController` + `RouteService`. Route currently has zero CRUD UI — only seeded via `DataInitializer`.
  - Dispatch Center board — operational view of upcoming/in-progress trips with quick status-change actions, reusing `TripService.updateTripStatus()`.
- **Dependencies:** None (does not require Phase 0).
- **Expected deliverables:** Driver CRUD pages, Route CRUD pages, Dispatch Center page — all functional and independently testable.
- **Refactoring risk:** None.
- **Current status:** COMPLETED (2026-07-16)
- **As delivered:**
  - `DriverService` + `AdminDriverController` + `driver-list/driver-form` templates. Owner-approved: a **single combined form creates User + Driver** (role auto-set to `ROLE_DRIVER`), mirroring `DataInitializer.createDriver()`, because `Driver` uses `@MapsId` and cannot exist without a `User`.
  - `RouteService` + `AdminRouteController` + `route-list/route-form` templates. Owner-approved: **full dynamic multi-stop editor** (add/remove stops, `stopOrder` auto-numbered 1..n), matching the `RouteStation` design and the original proposal's "danh sách các trạm dừng".
  - `DispatchController` + `dispatch-board` template — three groups (overdue / in progress / upcoming within 48h), all status changes delegated to `TripService.updateTripStatus()`.
  - Owner-approved: `monthlyRestDays` and `totalDrivingHours24h` are **deliberately excluded** from the driver form (see Developer Notes).
  - Guard rules reuse existing patterns rather than inventing new ones — see Developer Notes.

### Phase 2 — Incident Management
- **Objective:** Add the one genuinely new Operations module, matching the original thesis proposal (`.agent_instructions/03_functional_spec.md`, section II.5 — incident reporting).
- **Scope:** New entity `Incident` (trip/bus/driver FK, incident type, description, `reportedAt`, status `OPEN/IN_PROGRESS/RESOLVED`, `resolvedAt`); `IncidentService`; `AdminIncidentController`; list/detail templates. Bus status changes to `REPAIRING` remain a manual Admin action, not automatic, to avoid unexpected FSM side effects.
- **Dependencies:** None.
- **Expected deliverables:** Full Incident CRUD + linkage to Trip/Bus/Driver.
- **Refactoring risk:** Low (purely additive).
- **Current status:** NOT STARTED

### Phase 3 — Business Rule Validation as an Independent Component
- **Objective:** Make the existing validation logic consumable by Decision Support without throwing exceptions.
- **Scope:** Add non-throwing dry-run wrapper methods around the existing `validateBusForTrip()` / `validateStaffForTrip()` (return a structured result instead of throwing). Do **not** rewrite the underlying validation logic.
- **Dependencies:** None.
- **Expected deliverables:** New dry-run methods in `TripService`, existing throw-based flows unchanged and unaffected.
- **Refactoring risk:** Low if implemented strictly as a wrapper; would be high if the underlying logic were rewritten (explicitly disallowed).
- **Current status:** NOT STARTED

### Phase 4 — Decision Support: Current-State (no Forecast dependency)
- **Objective:** Deliver Driver Recommendation — a quick win that does not depend on historical data or forecasting.
- **Scope:** Wrap the existing public `TripService.getDrivingHoursForDate()` into a driver workload/under-utilization report to support staffing decisions.
- **Dependencies:** Phase 3 (optional reuse).
- **Expected deliverables:** Driver Recommendation view/report.
- **Refactoring risk:** None.
- **Current status:** NOT STARTED

### Phase 5 — Historical Data Preparation
- **Objective:** Build the dataset that Demand Forecast will consume. Deliberately kept as its own phase, separate from the forecasting algorithm, so the thesis can present the "data" concern and the "algorithm" concern distinctly.
- **Scope:** Backfill/demo-data script generating several weeks of synthetic historical trips (COMPLETED/CANCELLED, varied by route/time slot) — necessary because real historical data will not accumulate fast enough during the thesis timeline.
- **Dependencies:** Phase 0.
- **Expected deliverables:** A reusable backfill script/utility and a populated historical dataset in the persistent profile.
- **Refactoring risk:** None.
- **Current status:** NOT STARTED

### Phase 6 — Demand Forecast
- **Objective:** Deliver the first genuinely predictive module — the "AI Prediction" component, independent from Business Rule Validation.
- **Scope:** `ForecastService` using simple statistical methods (moving average / linear regression) per route/time-slot, based on Phase 5's dataset. New DTOs. Wired into a new tab in the existing `dashboard-analytics.html`.
- **Dependencies:** Phase 5.
- **Expected deliverables:** Working forecast output, visible in Analytics, backed by real backfilled historical data.
- **Refactoring risk:** Low (read-only, additive).
- **Current status:** NOT STARTED

### Phase 7 — Recommendation Engine + Cost/Revenue Estimation + Vehicle Replacement (MVP)
- **Objective:** Deliver forecast-driven Decision Support — the component that consumes both Business Rule Validation (Phase 3) and AI Prediction (Phase 6).
- **Scope — Recommendation Engine pipeline (approved, do not alter):**
  ```
  Historical Dataset
          |
          v
  Demand Forecast
          |
          v
  Candidate Trip Generation   (translate a forecast signal into a concrete route+time-window candidate, same pattern as existing createExtraTrip())
          |
          v
  Recommendation Engine
     +--------------+--------------+
     v              v              v
  Bus Selection  Driver Selection  Cost Estimation
  (reuse findBestAvailableBus()/findBestAvailableDriver() — already rule+availability filtered)
     +--------------+--------------+
          |
          v
  Business Rule Validation   (reuse Phase 3 dry-run wrapper — final confirmation gate)
          |
          v
  Final Recommendation Card
  ```
  - If the final Business Rule Validation gate fails (resource became unavailable while the card was pending review), mark the card as **stale — needs regeneration**. Do not silently auto-retry.
  - Vehicle Replacement Recommendation (MVP): independent of the pipeline above. Heuristic based on existing `Bus.kmSinceLastMaintenance` / `Bus.maintenanceThreshold` — no new entity for MVP. A full version (with a `MaintenanceRecord` entity for real history/cost tracking) is a stretch goal only.
- **Dependencies:** Phase 3, Phase 4, Phase 6.
- **Expected deliverables:** Working Recommendation Engine producing Final Recommendation Cards; Vehicle Replacement Recommendation view.
- **Refactoring risk:** Low — everything reuses existing methods; no changes to `TripService` core logic.
- **Current status:** NOT STARTED

### Phase 8 — What-if Simulation
- **Objective:** Deliver scenario simulation at the aggregate/statistical level only.
- **Scope:** Compute scenario outcomes (e.g. "+N buses -> fleet capacity change") on top of Forecast/Analytics statistics. Explicitly does **not** refactor `findBestAvailableBus()`/`findBestAvailableDriver()` into pure functions over hypothetical resource pools (see Architectural Principles).
- **Dependencies:** Phase 6, Phase 7.
- **Expected deliverables:** Aggregate-level what-if scenario view.
- **Refactoring risk:** Medium if kept aggregate-only (approved scope). High and explicitly disallowed if scope creeps into re-running the live constraint engine.
- **Current status:** NOT STARTED

### Phase 9 — Customer Portal (minimal scope)
- **Objective:** Prove the customer-facing data flow end-to-end, at minimal investment, per the pillar's 10-20% weighting.
- **Scope, in increasing order of risk:**
  1. Search Trips (read `Route`/`Trip`/`Station`, no new entity).
  2. View Trip Detail.
  3. View Seat Availability (`totalSeats - ticketsSold`; no seat-map/seat-number selection).
  4. Simple Booking — new `Booking` entity; seat-decrement write path **must** use pessimistic locking against `Trip.ticketsSold` to prevent the concurrent double-booking race explicitly identified in the original thesis proposal (`.agent_instructions/03_functional_spec.md`, section IV.2).
  5. Booking History — lookup by phone/email + booking reference, **no login required** for MVP. Enabling real Spring Security login is a separate, explicitly-approved task, not bundled into this phase.
- **Dependencies:** Technically independent of other pillars, but deliberately scheduled last per the pillar's priority weighting.
- **Expected deliverables:** Working Search -> Detail -> Seat Availability -> Booking -> Booking History flow.
- **Refactoring risk:** Low to the rest of the system. The Booking write path is the one place in this phase requiring careful, correct concurrency handling.
- **Current status:** NOT STARTED

---

## 6. Hidden Costs Register (cross-cutting — re-check relevance at each phase)

| # | Hidden cost | Blocks a phase? | When to address |
|---|---|---|---|
| 1 | ~~Missing historical data (`ddl-auto=create-drop` + `DataInitializer` wipes DB every restart)~~ | ~~Yes — blocks Phase 5/6/7~~ | **RESOLVED in Phase 0** (persistent default + `@Profile("demo")` gate + isolated test DB). Residual: no migration tool (Flyway/Liquibase); schema evolution relies on Hibernate `update` |
| 2 | Missing timestamps (no entity has `createdAt`/`updatedAt` except `Trip.saleOpenedAt`) | No | **Partly addressed in Phase 0** — `Trip.createdAt` added (sufficient for MVP). Other entities still have none; booking-velocity-based forecasting remains a stretch goal only |
| 3 | Missing real booking data (`Trip.ticketsSold` is a freely-editable int, no `Booking` entity backing it) | **Yes — but it is the central design problem of Phase 9 itself, not a prerequisite** | Handle directly inside Phase 9 |
| 4 | Performance (`spring.jpa.open-in-view=true`; some in-memory Java-stream aggregation in `DashboardService`) | No, until Phase 9 | Revisit as a pre-flight checklist item immediately before Phase 9 ships (public/concurrent traffic changes the risk profile) |
| 5 | Maintainability (`AdminTripManagementController` injects Repositories directly; business-rule constants are hardcoded, not admin-configurable) | No | Follow `AdminBusController` pattern for all new controllers (Phase 1/2); state the "code-configured, not data-driven" nature of Business Rule Validation honestly in the thesis defense |

---

## 7. Current Progress

- **Current phase:** Phase 0 and Phase 1 COMPLETED. Phase 2 not yet started.
- **Last completed milestone:** **Phase 1 (2026-07-16)** — Driver CRUD, Route CRUD (multi-stop editor), and the Dispatch Center board. All three verified end-to-end by driving the real app against real MySQL (see Session Log).
- **Remaining phases:** Phase 2 through Phase 9.
- **Known blockers:** None.
- **Next recommended task:** Begin Phase 2 — Incident Management (`Incident` entity + `IncidentService` + `AdminIncidentController` + templates). Bus status change to `REPAIRING` stays a manual Admin action. Follow the `AdminBusController`/`AdminDriverController` pattern (inject Service only); do **not** follow `AdminTripManagementController`.
- **Operational note for future sessions:** the default run (`./mvnw spring-boot:run`) **preserves** data and seeds nothing. To get sample data, run once with `-Dspring-boot.run.profiles=demo` — but be aware this **wipes** `busmanagement` first.

---

## 8. Session Log

*(Append-only. Do not overwrite or edit previous entries — add a new entry per session.)*

### 2026-07-16 — Architecture review & roadmap approval
- **What was implemented:** No feature code. Conducted a full architecture review of the existing codebase (all entities, repositories, services, controllers, templates, config, docs). Reviewed and refined the Phase 0-9 roadmap across several discussion rounds with the project owner (ordering of Decision Support vs Forecast, splitting Historical Data Preparation from Forecast, adding "View Trip Detail" to the Customer Portal flow, and detailing the Recommendation Engine pipeline). Created this tracker document.
- **Files modified:** `docs/development/THESIS_ROADMAP.md` (new file, this document).
- **Important architectural decisions:** See Section 3 (Architectural Principles) and Section 9 (Developer Notes) below — all decisions from this session are captured there.
- **Remaining TODOs:** Begin Phase 0 implementation in a future session.

### 2026-07-16 — Phase 0 implemented (Foundation)
- **What was implemented:**
  - Split the data policy by Spring profile. `application.properties` default is now persistent (`ddl-auto=update`); new `application-demo.properties` holds `ddl-auto=create-drop`.
  - Gated `DataInitializer` with `@Profile("demo")` so it no longer wipes/reseeds on every startup.
  - Added `Trip.createdAt` (`@CreationTimestamp`, column `created_at`, nullable, `updatable = false`).
  - Added `src/test/resources/application.properties` pointing tests at a separate `busmanagement_test` database (`createDatabaseIfNotExist=true`, `create-drop`).
  - Updated affected documentation to match the new behaviour.
- **Files modified:**
  - `src/main/java/giang/com/BusManagement/domain/Trip.java` (new `createdAt` field + import)
  - `src/main/java/giang/com/BusManagement/config/DataInitializer.java` (`@Profile("demo")` + class javadoc; seed logic untouched)
  - `src/main/resources/application.properties` (`ddl-auto` create-drop → update, policy comments)
  - `src/main/resources/application-demo.properties` (new)
  - `src/test/resources/application.properties` (new)
  - `docs/architecture/setup_guide.md`, `docs/architecture/database_schema.md`, `docs/development/02_project_context.md`, `docs/development/current_functional_spec.md`, `docs/development/Proj_functions_summary.md`, `docs/reports/project_report.md`, `docs/testing/test_case.md` (new TC_SEC_005B / TC_SEC_005C, TC_SEC_005 retitled to the `demo` profile), `docs/development/THESIS_ROADMAP.md`
- **Important architectural decisions:**
  - **Persist is the default; wiping is opt-in** (owner-approved). Rationale in Developer Notes.
  - **`@Profile("demo")` on `DataInitializer` is the real fix**, not the `ddl-auto` change — the bean's `deleteAll()` calls would have destroyed data regardless of DDL policy.
  - **`@CreationTimestamp` chosen over Spring Data JPA Auditing** — consistent with the entity's existing Hibernate-specific annotations (`@SQLDelete`, `@SQLRestriction`), and avoids introducing `@EnableJpaAuditing` + `@EntityListeners` machinery for a single field.
  - **Test isolation folded into Phase 0** (owner-approved scope addition) after discovering `mvnw test` was wiping the real database.
- **Verification performed (against real MySQL 8.0, not just a build):**
  1. Ran with `demo` profile → `DataInitializer` executed, 8 trips seeded in `busmanagement`.
  2. Ran `mvnw test` → `BUILD SUCCESS`, `Tests run: 1, Failures: 0, Errors: 0`; `busmanagement` still held 8 trips (isolation proven); `busmanagement_test` auto-created; no `[AI Test Setup]` log line (proving `DataInitializer` did not run in tests).
  3. Ran with default profile (no flag) → no `[AI Test Setup]` line, `No active profile set`, app started normally; `busmanagement` still held 8 trips **after restart** (persistence proven); `trips.created_at` column present (`datetime(6)`, nullable) and populated by `@CreationTimestamp`.
- **Remaining TODOs:** Phase 1 (Driver CRUD, Route CRUD, Dispatch Center). Note for later: no migration tool (Flyway/Liquibase) — schema evolution still relies on Hibernate `update`; `foreign_key_checks=0` remains in the JDBC URL (pre-existing, out of Phase 0 scope, more consequential now that data is long-lived).

### 2026-07-16 — Phase 1 implemented (Operations gaps)
- **What was implemented:**
  - **Driver Management:** `DriverService` + `AdminDriverController` + `admin/driver/driver-list.html`, `admin/driver/driver-form.html`. One combined form creates the `User` (role forced to `ROLE_DRIVER`) and the `Driver` together.
  - **Route Management:** `RouteService` + `AdminRouteController` + `admin/route/route-list.html`, `admin/route/route-form.html` with a JS-driven multi-stop editor; `stopOrder` is renumbered 1..n from the row order on submit.
  - **Dispatch Center:** `DispatchController` + `admin/dispatch-board.html`, grouping trips into overdue / in progress / upcoming-48h, with quick status actions delegated to `TripService.updateTripStatus()`.
  - Added `Route.getOrderedRouteStations()` helper; added repository methods `existsByRouteId`, `existsAnyTripForDriver`, `existsTripForDriverWithStatusIn`, `findDispatchBoardTrips`; added 4 entry points to `admin/dashboard.html`.
- **Files modified:**
  - New: `service/DriverService.java`, `service/RouteService.java`, `controller/admin/AdminDriverController.java`, `controller/admin/AdminRouteController.java`, `controller/admin/DispatchController.java`, `templates/admin/driver/driver-{list,form}.html`, `templates/admin/route/route-{list,form}.html`, `templates/admin/dispatch-board.html`
  - Modified: `repository/TripRepository.java`, `domain/Route.java`, `templates/admin/dashboard.html`, docs
- **Important architectural decisions:** all guard rules reuse existing patterns/constants rather than introducing new business rules — see Developer Notes for each.
- **Verification performed (drove the real app on :8099 against real MySQL, not just the build):**
  - All 6 new/affected pages render HTTP 200 with zero `TemplateProcessingException`/`SpelEvaluationException` in the log.
  - Driver: created via form → DB shows `role=ROLE_DRIVER`, blank email normalised to `NULL`; edited → name/experience updated, blank password left the old hash untouched, unticking "Đang hoạt động" flipped `is_active` 1→0 (proving the `_isActive` field marker works).
  - Driver guards: deactivating a driver holding an `ACTIVE`+`PENDING_APPROVAL` trip was **blocked** with the expected message and `is_active` stayed 1; deleting that driver was **blocked**; deleting a driver with no trips **succeeded**.
  - Route: created a 3-stop route → `stopOrder` 1,2,3 in the exact submitted order; edited to 2 stops in reversed order → old rows deleted and rebuilt correctly, `suitable_bus_type_id` cleared to `NULL`; duplicate-station and single-station submissions were **blocked**; deleting a route in use was **blocked**; deleting an unused route **succeeded and cascaded** its `route_stations` away.
  - Dispatch: board content deterministic across 3 consecutive fetches and matched the DB exactly (trips inside 48h shown, trips at 83h/88h correctly excluded); `ACTIVE→DEPARTED` set the bus to `TRAVELING`; `DEPARTED→COMPLETED` set the bus back to `READY` and added the route distance to the odometer (500→620 for a 120 km route); an invalid `PENDING_APPROVAL→DEPARTED` attempt was rejected by the FSM and surfaced as a UI error; a departed trip moved from "Sắp Khởi Hành" to "Đang Trên Đường" and correctly offered only the "Hoàn thành" action.
  - `mvnw test`: `BUILD SUCCESS`, `Tests run: 1, Failures: 0, Errors: 0`.
- **Remaining TODOs:** Phase 2 (Incident Management). Note: verification mutated demo data (trip #1 and #4 advanced through the FSM, one bus odometer 500→620) — re-run with `-Dspring-boot.run.profiles=demo` for a clean demo dataset.

---

## 9. Developer Notes

*(Implementation decisions that are not obvious from the code — prevents future sessions from re-litigating the same discussions.)*

- **Why Cost/Revenue Estimation moved out of the "current-state" group (Phase 4) into the post-Forecast group (Phase 7):** "Estimation" implies a forward-looking projection, which needs Demand Forecast's predicted-demand output as input. Driver Recommendation, by contrast, only reports current-day driving-hour workload (`getDrivingHoursForDate()`) and genuinely needs no forecast — it stays in Phase 4.
- **Why Historical Data Preparation (Phase 5) is a separate phase from Demand Forecast (Phase 6):** Bundling backfill script work into "the Forecast phase" blurs an infrastructure/data concern with an algorithm concern. Splitting them makes both easier to grade/present independently in the thesis.
- **Why "Business Rules" and "Resource Availability" are not implemented as two sequential services despite being two boxes in the pipeline diagram:** In the existing code, `TripService.findBestAvailableBus()` already applies both as interleaved filters over the same candidate stream (`.filter(!isBusBusy(...)).filter(!needsMaintenance()).filter(!isNearMaintenance(...))`), and filter intersection is order-independent. Implementing them as two separate, sequential services would duplicate logic that already exists — directly violating the "no parallel architecture" principle. Keep two boxes in diagrams for clarity; implement as one reused call.
- **Why the Recommendation Engine's final Business Rule Validation gate reuses `validateBusForTrip`/`validateStaffForTrip` (via the Phase 3 dry-run wrapper) rather than reusing `findBestAvailableBus`/`findBestAvailableDriver` again:** This mirrors an already-proven pattern in the codebase: `TripService.confirmAutoAssignedTrip()` selects resources via the filtered helper methods, then re-validates via the throw-based validators immediately before committing, because state can change between selection and commit. A Recommendation Card may sit unacted-on for hours or days (much longer than the live AI auto-assign flow), so this staleness risk is even more relevant here — hence the explicit "mark as stale, don't auto-retry" rule if the final gate fails.
- **Why What-if Simulation is scoped to aggregate/statistical output only:** `findBestAvailableBus()`/`findBestAvailableDriver()` are tightly coupled to live repository queries (not pure functions over an injectable resource pool). Refactoring them to support hypothetical "what if we had N more buses" scenarios cleanly would be the single highest-risk refactor identified across the entire roadmap, touching the most correctness-sensitive code in the app. Rejected in favor of a narrower simulation computed from existing Forecast/Analytics aggregates.
- **Why Vehicle Replacement Recommendation's MVP requires no new entity:** `Bus` only stores a maintenance *snapshot* (current odometer, threshold) — there is no historical log of past maintenance events (date, cost). A full version needs a new `MaintenanceRecord` entity; deferred as a stretch goal so MVP can ship with a simple heuristic on existing fields.
- **Why `ddl-auto=create-drop` must be addressed before Phase 5/6:** `DataInitializer.run()` calls `deleteAll()` on every repository and reseeds fixed demo data on every application startup. Any backfilled historical dataset would silently be destroyed on the next restart, and Demand Forecast would then run on near-empty data **without raising any error** — the failure mode is silent and easy to miss, not a crash. *(Resolved in Phase 0.)*
- **Why persist became the default and wiping became opt-in (Phase 0, owner-approved):** the two options were "keep wipe-on-start as default, persist via a flag" versus "persist by default, wipe via a flag". The second was chosen because the failure modes are asymmetric — forgetting the flag under a wipe-default silently destroys the historical dataset (unrecoverable, no error raised), whereas forgetting the flag under a persist-default merely means you don't get fresh sample data (obvious and trivially fixed by re-running with `demo`). The cost accepted: a fresh clone starts empty and must be seeded once with `-Dspring-boot.run.profiles=demo`, and the old "restart = clean data" habit no longer applies.
- **Why gating `DataInitializer` with `@Profile("demo")` was the actual fix, not the `ddl-auto` change:** `DataInitializer implements CommandLineRunner`, so it runs on *every* startup independently of Hibernate's DDL policy, and its first action is `deleteAll()` across all repositories. Setting `ddl-auto=update` alone would have preserved the *schema* while the bean still deleted every *row* — the data loss would have persisted while looking fixed.
- **Why `@CreationTimestamp` was chosen over Spring Data JPA Auditing for `Trip.createdAt`:** the `Trip` entity already relies on Hibernate-specific annotations (`@SQLDelete`, `@SQLRestriction`), so `@CreationTimestamp` matches the established style and requires no extra configuration. Spring Data Auditing would have needed an `@EnableJpaAuditing` config bean plus `@EntityListeners(AuditingEntityListener.class)` on the entity — new machinery for a single field, contradicting the "minimize refactoring / no new abstractions unless required" principle.
- **Why `Trip.createdAt` is nullable rather than `NOT NULL`:** `@CreationTimestamp` only populates on INSERT, so rows that already existed when the column was introduced (via `ddl-auto=update`'s `ALTER TABLE`) hold `NULL`. Enforcing `NOT NULL` would either break the schema update on a populated database or demand a backfill migration that no tooling exists for. Any future consumer of this field must handle `NULL`.
- **Why test isolation (`src/test/resources/application.properties`) was folded into Phase 0:** analysis revealed that with no test-specific config, `BusManagementApplicationTests` loaded the app's own `application.properties` and therefore dropped and reseeded the **real** `busmanagement` database on every `mvnw test` run. This is the same root cause as the phase objective (unintended data destruction), so fixing it separately would have left Phase 0's guarantee incomplete — a single test run would still wipe the Phase 5 dataset. Note that `src/test/resources/application.properties` **shadows** the main file entirely (test classpath precedence; the files are not merged), which is why every property is redeclared there rather than only the overrides.
- **Why Driver creation builds the `User` and the `Driver` in one form (Phase 1, owner-approved):** `Driver` maps its PK to `User.id` via `@MapsId`, so a `Driver` row cannot exist without a `User` row. The alternative was a two-step flow (create the `User` at `/admin/users/new`, then attach a driver profile), which keeps the two concerns separate but costs two screens. The combined form was chosen because it mirrors what `DataInitializer.createDriver()` already does and treats "a driver" as one thing in the UI. Consequence: `DriverService.deleteDriver()` deletes the **`User`** and relies on `User.driver`'s `cascade = ALL` to remove the `Driver` — deleting only the `Driver` would strand an unreachable `ROLE_DRIVER` user, since the combined form offers no way to re-attach a profile to an existing account.
- **Why the driver-deactivation guard reuses `TripService`'s `busyStatuses` set:** the question "which trip statuses mean a driver still has obligations?" was already answered in the codebase by `TripService.isDriverBusyInWindow()`, which uses `{PENDING_APPROVAL, ACTIVE, DEPARTED}`. `DriverService.BUSY_STATUSES` repeats that exact set instead of inventing a second definition of "busy". The guard itself mirrors `BusService.saveBus()`, which already blocks moving a bus to `REPAIRING` while it holds unfinished trips.
- **Why `monthlyRestDays` and `totalDrivingHours24h` are hidden from the driver form (owner-approved):** `monthlyRestDays` is read nowhere in the codebase — the original proposal's "nghỉ đủ 2 ngày/tháng" rule was never implemented — so exposing an editable field would imply an effect that does not exist. `totalDrivingHours24h` *does* have an effect (`getDrivingHoursForDate()` adds it to the daily total) but is documented in `TripService` as mock seed data destined to be fed by IoT/GPS, so it is not an admin-editable field either. Both were left out rather than shipped as misleading inputs.
- **Why editing a route's stops deletes and rebuilds the `RouteStation` rows instead of updating them:** `RouteStation`'s PK is the composite `(routeId, stationId)`, so "changing which station a stop points at" is not an update — it is a different primary key. `Route.routeStations` also declares `cascade = ALL` **without** `orphanRemoval`, so clearing the collection would not delete anything. `RouteService.saveRoute()` therefore deletes the existing rows explicitly (then `flush()`es, so the re-inserted rows cannot collide with the old ones inside the same transaction) and re-creates them with `stopOrder` renumbered from the submitted order.
- **Why a route cannot revisit the same station (e.g. A→B→A):** the composite PK `(routeId, stationId)` makes a repeated station impossible at the schema level. `RouteService.validateRoute()` rejects duplicates up front with a clear business message (and the form pre-checks in JS) rather than letting it surface as a PK violation. This is a known modelling limitation, not a validation choice — round-trip routes need a different `RouteStation` key design.
- **Why the Dispatch Center repeats the 48-hour window instead of importing it:** `DashboardService.UPCOMING_TRIPS_WINDOW_HOURS` is `private`, and widening its visibility purely for reuse would couple the dispatch screen to the analytics service for a single integer. `DispatchController` declares its own constant with a comment pointing at the Dashboard one, so the two stay recognisably the same concept without a structural dependency. If a third consumer appears, promote it to a shared constant then.
- **Why `findDispatchBoardTrips` JOIN FETCHes only `t.coDrivers` and not `r.routeStations`:** Hibernate rejects fetching two `List` (bag) collections in one query (`MultipleBagFetchException`) — the same constraint documented on `Route.routeStations`, which uses `@BatchSize(20)` for that reason. The dispatch board needs bus/driver/assistant eagerly (it renders them), and gets departure/destination names through the batched `routeStations`.
- **Why a separate MySQL test database instead of an in-memory H2:** H2 would have required a new dependency and introduced dialect divergence from production behaviour (the JDBC URL uses the MySQL-specific `sessionVariables=foreign_key_checks=0`, and `@SQLDelete` issues raw MySQL SQL). Pointing tests at `busmanagement_test` with `createDatabaseIfNotExist=true` keeps the dialect identical, adds no dependency, and needs no manual `CREATE DATABASE` step.
- **Why Booking's seat-decrement needs pessimistic locking:** The original thesis proposal (`.agent_instructions/03_functional_spec.md`, section IV.2) explicitly called out the concurrent double-booking race condition as a requirement to handle. `Trip.ticketsSold` today has zero concurrency protection because only a single Admin edits it manually; once real customer bookings exist, this becomes a genuine multi-writer field.
- **Why `AdminBusController` (not `AdminTripManagementController`) is the required template for new controllers:** `AdminTripManagementController` injects `TripRepository`/`RouteRepository`/`BusRepository`/`DriverRepository` directly alongside `TripService` — a known, already-documented layering violation (`docs/reports/project_report.md`, Warn #4). New controllers in Phase 1/2 must not copy this pattern.

---

## 10. Rules for Future Claude Code Sessions

Every future Claude Code session working on this project must:

1. Read this file completely before making any implementation decision.
2. Determine the current phase from Section 7 (Current Progress) and continue from there — never restart planning from scratch.
3. Update this file whenever a phase progresses (update Section 5's status field, update Section 7, append to Section 8).
4. Never skip updating the progress tracker after implementation work.
5. Never silently change the agreed roadmap — do not reorder, redesign, simplify, or optimize phases in Section 5 without explicit approval from the project owner.
6. Ask for approval before changing architecture (Section 3 principles are binding unless the project owner explicitly revises them).
7. Append to the Session Log (Section 8) — never overwrite or edit previous entries.
8. Record any non-obvious implementation decision in Developer Notes (Section 9) so future sessions do not repeat the same discussion.
