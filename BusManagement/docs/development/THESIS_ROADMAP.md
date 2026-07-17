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
- **No driver-facing portal** (original proposal section II.1–II.4: personal schedule view, QR passenger check-in, driver-side status updates, driving-hours logbook). All of these presuppose an authenticated driver session, and enabling login is itself out of scope (see above). The only part of section II carried into this roadmap is **II.5 incident reporting — deliberately re-interpreted as an Admin-recorded CRUD** (Phase 2): the Admin records incidents on behalf of drivers, because without authentication the reporter cannot be a driver session. State this re-interpretation explicitly in the thesis defense.
- **No fare/pricing management module** (original proposal I.1.d: price by route / bus type / time slot). `Trip.price` stays a manually-entered per-trip field; Cost/Revenue Estimation (Phase 7) consumes it as-is.
- **No ticket cancellation and no customer reviews/feedback** (original proposal III.3.2, III.4) — Phase 9's Booking flow is create + view history only, per the "prove the data flow, not e-commerce" pillar weighting.
- **No daily cron job** (original proposal IV.3: nightly reset of driving hours / maintenance schedule updates). `totalDrivingHours24h` remains documented mock-seed data (see Developer Notes); adding a scheduler is a separate owner decision.

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
- **Current status:** COMPLETED (2026-07-16)
- **As delivered:**
  - `Incident` entity + `IncidentType` / `IncidentStatus` enums, `IncidentRepository`, `IncidentService`, `AdminIncidentController`, `incident-list` / `incident-form` templates, dashboard entry point.
  - Owner-approved: **`IncidentType` has 5 values** (`VEHICLE_BREAKDOWN`, `ACCIDENT`, `ROAD_ISSUE`, `STAFF_ISSUE`, `OTHER`) — a superset of the proposal's "xe hỏng, vấn đề trên đường", so incidents can be grouped for later statistics.
  - Owner-approved: **`bus` is mandatory; `trip` and `driver` are optional** — every incident resolves to a specific vehicle (enabling "which bus fails most often" later), while a breakdown in the depot has no trip.
  - Owner-approved: **no FSM on `IncidentStatus`** — the Admin may move freely between `OPEN`/`IN_PROGRESS`/`RESOLVED` (including reopening a wrongly-closed incident). The only enforced rule is `resolvedAt`: stamped on entering `RESOLVED`, cleared on leaving it.
  - As specified in the original scope: recording an incident does **not** change `Bus.status` to `REPAIRING`; that stays a manual Admin action in `BusService`.
  - Only additive changes to existing code: one new generic query method `TripService.getAllTrips()` (feeds the trip dropdown, keeps the controller free of direct repository access) and one dashboard link.

### Phase 3 — Business Rule Validation as an Independent Component
- **Objective:** Make the existing validation logic consumable by Decision Support without throwing exceptions.
- **Scope:** Add non-throwing dry-run wrapper methods around the existing `validateBusForTrip()` / `validateStaffForTrip()` (return a structured result instead of throwing). Do **not** rewrite the underlying validation logic.
- **Dependencies:** None.
- **Expected deliverables:** New dry-run methods in `TripService`, existing throw-based flows unchanged and unaffected.
- **Refactoring risk:** Low if implemented strictly as a wrapper; would be high if the underlying logic were rewritten (explicitly disallowed).
- **Current status:** NOT STARTED

### Phase 4 — Decision Support: Current-State (no Forecast dependency)
- **Objective:** Deliver Driver Recommendation — a quick win that does not depend on historical data or forecasting.
- **Scope:** Build on the existing public `TripService.getDrivingHoursForDate()` to support staffing decisions. **The "who is busiest today" half of this already shipped** — `DashboardService.buildDriverStats()` computes `topLoadedDrivers` (top-N by `getDrivingHoursForDate(driver, now)`, busiest first) and renders it live on `dashboard-analytics.html`. Phase 4 must therefore deliver only what that view does **not**: the **under-utilization / availability** side (least-loaded active drivers, i.e. who *can* take a trip), over a **selectable date** (not just "now"), presented as a staffing recommendation rather than a monitoring stat. Reuse `DriverWorkloadDto` if it fits; do not re-deliver the top-busy ranking under a new name. *(Same trap as the Phase 7 Vehicle Replacement correction of 2026-07-17 — see Developer Notes.)*
- **Dependencies:** Phase 3 (optional reuse).
- **Expected deliverables:** Driver Recommendation view/report (under-utilization/availability focus, per the scope above).
- **Refactoring risk:** None.
- **Current status:** NOT STARTED

### Phase 5 — Historical Data Preparation
- **Objective:** Build the dataset that Demand Forecast will consume. Deliberately kept as its own phase, separate from the forecasting algorithm, so the thesis can present the "data" concern and the "algorithm" concern distinctly.
- **Scope:** Backfill/demo-data script generating several weeks of synthetic historical trips (COMPLETED/CANCELLED, varied by route/time slot) — necessary because real historical data will not accumulate fast enough during the thesis timeline. The dataset's **time axis is `Trip.departureTime`** (freely settable to past dates; every existing time-window query in `TripRepository` already keys on it). **Do not attempt to backdate `Trip.createdAt`** — `@CreationTimestamp` stamps INSERT time unconditionally and the column is `updatable = false`, so backfilled rows will carry the script's run date there; that is expected and harmless (see Developer Notes).
- **Dependencies:** Phase 0.
- **Expected deliverables:** A reusable backfill script/utility and a populated historical dataset in the persistent profile.
- **Refactoring risk:** None.
- **Current status:** NOT STARTED

### Phase 6 — Demand Forecast
- **Objective:** Deliver the first genuinely predictive module — the "AI Prediction" component, independent from Business Rule Validation.
- **Scope:** `ForecastService` using simple statistical methods (moving average / linear regression) per route/time-slot — time-slot meaning a bucket of **`Trip.departureTime`** (the dataset's time axis, per Phase 5; not `createdAt`) — based on Phase 5's dataset. New DTOs. Wired into a new tab in the existing `dashboard-analytics.html`.
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
  (reuse findBestAvailableBus()/findBestAvailableDriver() — already rule+availability filtered.
   NOTE: both are private — this step needs an additive public overload on TripService.
   See Developer Notes; do not flip the existing methods to public.)
     +--------------+--------------+
          |
          v
  Business Rule Validation   (reuse Phase 3 dry-run wrapper — final confirmation gate)
          |
          v
  Final Recommendation Card
  ```
  - If the final Business Rule Validation gate fails (resource became unavailable while the card was pending review), mark the card as **stale — needs regeneration**. Do not silently auto-retry.
  - Vehicle Replacement Recommendation (MVP): independent of the pipeline above. The recommendation is based on existing operational signals available in the current system, primarily the vehicle's lifetime odometer and historical incident frequency. The exact scoring heuristic is intentionally left implementation-defined so it can evolve without introducing new entities. A full version (with a `MaintenanceRecord` entity and richer lifecycle history) remains a stretch goal. See Developer Notes for the signal deliberately excluded, and why.
- **Dependencies:** Phase 3, Phase 6. *(Phase 4 was listed here until 2026-07-17 — a relic of the draft in which Cost/Revenue Estimation still lived in the Phase 4 group before being moved here (see Developer Notes); nothing in this pipeline consumes Phase 4's deliverable.)*
- **Expected deliverables:** Working Recommendation Engine producing Final Recommendation Cards; Vehicle Replacement Recommendation view.
- **Refactoring risk:** Low — everything reuses existing methods; no changes to `TripService` **logic**. It does, however, require one **additive** change to `TripService`: `findBestAvailableBus()`/`findBestAvailableDriver()` are `private`, so the new `RecommendationService` cannot call them. Expose them through new public overloads following the precedent already set by `getDrivingHoursForDate()` (private impl + public overload). No existing method changes visibility; no logic is touched. See Developer Notes.
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
| 2 | Missing timestamps: only `Trip` and `Incident` carry any. `Bus`, `BusType`, `Driver`, `Route`, `RouteStation`, `Station` and `User` still have no `createdAt`/`updatedAt` | No | **Partly addressed in Phase 0 + Phase 2** — `@CreationTimestamp` now covers `Trip.createdAt` (Phase 0) and `Incident.reportedAt` (Phase 2); `Incident.resolvedAt` is `IncidentService`-managed, and `Trip.saleOpenedAt` is a business milestone stamped by the FSM, not an audit column. Sufficient for MVP. Booking-velocity-based forecasting remains a stretch goal only. *(Row last re-checked 2026-07-17 — Section 6 says re-check each phase; Phase 2 shipped `Incident.reportedAt` without updating this row.)* |
| 3 | Missing real booking data (`Trip.ticketsSold` is a freely-editable int, no `Booking` entity backing it) | **Yes — but it is the central design problem of Phase 9 itself, not a prerequisite** | Handle directly inside Phase 9 |
| 4 | Performance (`spring.jpa.open-in-view=true`; some in-memory Java-stream aggregation in `DashboardService`) | No, until Phase 9 | Revisit as a pre-flight checklist item immediately before Phase 9 ships (public/concurrent traffic changes the risk profile) |
| 5 | Maintainability (`AdminTripManagementController` injects Repositories directly; business-rule constants are hardcoded, not admin-configurable) | No | Follow `AdminBusController` pattern for all new controllers (Phase 1/2); state the "code-configured, not data-driven" nature of Business Rule Validation honestly in the thesis defense |
| 6 | Incident data feeds nothing operational. `IncidentRepository` is referenced by `IncidentService` alone — `TripService`/`BusService`/`DriverService` never read it. Recording a `VEHICLE_BREAKDOWN` does not affect bus assignment: the bus stays `READY`, and because `findBestAvailableBus()` ranks by `min(kmSinceLastMaintenance)`, a recently-serviced bus that just broke down is **preferentially** selected. A `STAFF_ISSUE` likewise has no effect on `findBestAvailableDriver()`. The only link between an incident and dispatch is the Admin remembering to set `Bus.status = REPAIRING` by hand on a different screen. | No — this is approved Phase 2 scope (Section 5, Phase 2: "Bus status changes to `REPAIRING` remain a manual Admin action, not automatic, to avoid unexpected FSM side effects"), not an implementation defect | **Read path: Phase 7** — Vehicle Replacement Recommendation consumes incident frequency per bus (read-only; this is what makes `Incident.bus` mandatory worthwhile — see Developer Notes). **Write path (making dispatch itself react to open incidents) is a separate, unapproved owner decision** — it would mean reaching into `TripService`, which Phase 2 was explicitly barred from doing. Do not add it without approval; if approved, the natural home is Phase 3's dry-run wrapper, not new logic inside the validators |

---

## 7. Current Progress

- **Current phase:** Phase 0, Phase 1 and Phase 2 COMPLETED. Phase 3 not yet started.
- **Last completed milestone:** **Phase 2 (2026-07-16)** — Incident Management (entity, enums, repository, service, controller, list/form templates). Verified end-to-end by driving the real app against real MySQL (see Session Log). **Committed** as `8b3015d` (feature), `934b034` (incident delete-guards), `12e7763` (docs + verify skill) — all on branch **`temp`**, **not yet merged to `main`**. Earlier "not yet committed" statements in Section 8 are historical (accurate when written, superseded — see the 2026-07-17 roadmap-amendment entry).
- **Remaining phases:** Phase 3 through Phase 9.
- **Known blockers:** None.
- **Next recommended task:** Begin Phase 3 — add non-throwing dry-run wrappers around the existing `validateBusForTrip()` / `validateStaffForTrip()` in `TripService`. Do **not** rewrite the underlying validation logic; the wrapper is what Decision Support (Phase 7) will call.
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

### 2026-07-16 — Phase 2 implemented (Incident Management) — NOT COMMITTED
- **What was implemented:** `domain/Incident.java`, `domain/IncidentType.java`, `domain/IncidentStatus.java`, `repository/IncidentRepository.java`, `service/IncidentService.java`, `controller/admin/AdminIncidentController.java`, `templates/admin/incident/incident-{list,form}.html`; added `TripService.getAllTrips()` and an entry point in `admin/dashboard.html`.
- **Files modified:** the above (new) plus `service/TripService.java` (one added query method, no existing logic touched), `templates/admin/dashboard.html`, and docs (`THESIS_ROADMAP.md`, `current_functional_spec.md`, `02_project_context.md`, `architecture/database_schema.md`).
- **Important architectural decisions:** three owner-approved business decisions (5-value `IncidentType`; `bus` mandatory / `trip`+`driver` optional; no FSM on `IncidentStatus`) — see the Phase 2 entry in Section 5 and Developer Notes.
- **Verification performed (drove the real app on :8099 against real MySQL):**
  - `incidents` table auto-created by `ddl-auto=update` **without touching existing data** — the first real payoff of Phase 0. Schema confirmed: `bus_id NOT NULL`, `trip_id`/`driver_id` nullable, both enums stored as strings.
  - All new pages render HTTP 200, zero `TemplateProcessingException`/`SpelEvaluationException`.
  - Created an incident linked to a trip+driver, and one with `trip=&driver=` (empty) → stored as `NULL` (the DomainClassConverter empty-string→null behaviour holds, same as `suitableBusType` in Phase 1). `reported_at` auto-stamped.
  - `resolvedAt` lifecycle: `OPEN→RESOLVED` stamped it; reopening `RESOLVED→IN_PROGRESS` cleared it back to `NULL`; creating directly as `RESOLVED` stamped it immediately. `reported_at` survived two edits unchanged (`updatable = false` holds).
  - Validation: submitting without a bus was blocked with the expected message.
  - Status counter cards read 2/0/1, matching the DB exactly.
  - Delete works; deleting is unguarded by design (an incident report is not operational history in the way a trip is).
  - **Soft-delete interaction probed deliberately** (see Developer Notes): soft-deleting a trip referenced by an incident does **not** crash — the list and edit pages still return 200 and the incident degrades to showing "Ngoài hành trình".
  - `mvnw clean test`: `BUILD SUCCESS`, `Tests run: 1, Failures: 0, Errors: 0`.
- **Remaining TODOs:** Phase 3. All Phase 2 test data was cleaned from the database afterwards. **This phase is intentionally left uncommitted** per the owner's instruction.

### 2026-07-17 — Phase 2 consistency review; roadmap amended (no feature code)
- **What was implemented:** No feature code. At the owner's request, audited the still-uncommitted Phase 2 (Incident Management) for consistency against the rest of the codebase and against this roadmap. **Phase 2 code was found sound and was left untouched.** The review instead surfaced a contradiction inside this roadmap, which was corrected with owner approval.
- **Files modified:** `docs/development/THESIS_ROADMAP.md` only (Section 5 Phase 7 scope; Section 6 new Hidden Cost #6; Section 9 new Developer Note). No Java, no templates, no other docs.
- **Verdict on Phase 2 (no changes made):** follows the required `AdminBusController` pattern (Service-only injection); changes to existing code are additive only (`TripService.getAllTrips()` + one dashboard link); `mvnw compile` → `BUILD SUCCESS`. Docs already matched the implementation, including an honest record of the soft-delete interaction.
- **Owner observation that drove the session:** recording an incident does not prevent the same bus/driver from being assigned to other trips — the two modules appear unrelated. **Confirmed:** `IncidentRepository` is referenced by `IncidentService` alone; `TripService`/`BusService`/`DriverService` never read it. This is **approved Phase 2 scope** (Section 5 Phase 2: bus status changes stay manual), **not a defect** — so it was recorded as **Hidden Cost #6** rather than "fixed". Fixing it would mean reaching into `TripService`, which Phase 2 is explicitly barred from doing. The write path remains an unapproved owner decision.
- **Roadmap contradiction found and resolved (owner-approved):** Section 5's Phase 7 scope specified the Vehicle Replacement heuristic as `Bus.kmSinceLastMaintenance`/`maintenanceThreshold`, while Section 9 justified mandatory `Incident.bus` as *"the intended input for Vehicle Replacement Recommendation in Phase 7"*. The two disagreed, and had Phase 7 shipped as literally scoped, `incidents` would have remained a write-only silo **permanently**. **Resolution: Section 5 was corrected and the Developer Note kept** — evidence being that `kmSinceLastMaintenance` resets at every service (it answers "due for a service?", not "worn out?"), and the ranked list it produces is **already shipped** as `DashboardService.buildMaintenanceAlerts()`/`BusAlertDto` (the original proposal's I.3.a). Phase 7 as previously scoped would have re-delivered an existing feature under a new name. Phase 7 now ranks on lifetime `Bus.odometer` + incident frequency; still no new entity. Reasoning recorded in Section 9 so it is not re-litigated.
- **Note for the thesis defense:** Vehicle Replacement Recommendation has **no ancestor in the original proposal** (`.agent_instructions/03_functional_spec.md` contains no "thay thế"/replacement requirement anywhere). It is a Pillar-2 roadmap invention, which is legitimate, but it means the proposal cannot arbitrate its content — only internal coherence can. The proposal's only incident requirement is II.5 ("Báo cáo sự cố về trung tâm"), one line, under Driver, saying nothing about reusing the data.
- **Correction to this session's own earlier analysis (recorded so it is not re-raised):** an initially-reported "`DataInitializer` never wipes `incidents`, so the `demo` profile leaves orphan rows" finding was **wrong**. The `demo` profile runs `ddl-auto=create-drop` (`application-demo.properties`), so the whole schema — `incidents` included — is dropped and recreated before `DataInitializer` runs. The missing `incidentRepository.deleteAll()` is harmless; in fact every existing `deleteAll()` call in that bean is redundant under that profile.
- **Unrelated defect (owner-fixed during the session):** `dashboard-analytics.html` had a corrupted DOCTYPE (a stray backtick had replaced the `T`, giving `<!DOCYPE html>` → quirks mode) and the whole file had been re-indented by a formatter — 412/409 lines of churn masking only 7 lines of real change. Owner fixed the DOCTYPE and reverted the re-indent. Unrelated to Phase 2; do not fold into its commit.
- **Second review round — this roadmap audited against the source (owner-approved amendments):** most factual claims held and were left alone (Spring Boot 4.0.2; `spring-boot-starter-mail` present-but-unused; `Trip.originalTrip` self-reference; `DashboardService.UPCOMING_TRIPS_WINDOW_HOURS` is `private`; `Route.routeStations` uses `@BatchSize(20)`; `AdminTripManagementController` injects 4 repositories per `project_report.md` Warn #4; `02_project_context.md` line 35 bans `SecurityContextHolder`; `totalDrivingHours24h` is documented as MOCK SEED; the filter chain quoted in Developer Notes matches `findBestAvailableBus()`). Two defects were found in the roadmap itself and corrected:
  - **Phase 7 was not implementable as written.** It instructed the (new) Recommendation Engine to "reuse `findBestAvailableBus()`/`findBestAvailableDriver()`" while asserting "no changes to `TripService` core logic" — but both methods are `private`, so a separate class cannot call them. Fixed in three places: the pipeline diagram, Phase 7's Refactoring-risk line, and a new Developer Note prescribing an **additive public overload** on the existing `getDrivingHoursForDate()` precedent (private impl + public overload), and explicitly rejecting the two tempting shortcuts (flipping the privates to public; moving the engine inside the ~1,100-line `TripService`). **Phase 3 and Phase 4 were checked and are unaffected** — Phase 3's wrappers live inside `TripService`, and Phase 4 consumes the one already-public method. This is why the gap went unnoticed: it bites only at Phase 7.
  - **Hidden Cost #2 was stale.** It claimed no entity but `Trip` carried timestamps, but Phase 2 itself had added `Incident.reportedAt` (`@CreationTimestamp`) without re-checking the row — exactly the "re-check relevance at each phase" discipline the register asks for. Row rewritten with an explicit re-check date.
- **Nits noticed and deliberately left alone:** Phase 2's *scope* line says "list/detail templates" where list/**form** shipped (the "As delivered" line is correct); Phase 8 is rated "Medium" refactoring risk for a read-only aggregate view while Phase 6, also read-only and additive, is "Low" — a subjective call, left for the owner.
- **Incident delete-guards implemented (owner-approved, same session):** `BusService.deleteBus()` and `DriverService.deleteDriver()` previously guarded against trips but not against incidents, so a bus/driver with incidents and no trips could be hard-deleted into orphan rows (`foreign_key_checks=0` means MySQL does not stop it). Both now refuse, via two new derived queries on `IncidentRepository` (`existsByBusId`, `existsByDriverUserId`) — the same repository-injection style those services already use for `TripRepository`, and the same "preserve operational history" principle as the existing trip guards. The two messages differ deliberately because the model differs: `Incident.bus` is mandatory (the report must be deleted to free the bus), while `Incident.driver` is optional (the driver can simply be unlinked). Docs updated to match: `current_functional_spec.md` (Fleet/Driver/Incident business rules — note that the pre-existing bus trip-guard turned out to be undocumented and was written down at the same time) and `architecture/database_schema.md` (`incidents` notes).
- **Verification of the guards — PASS, driven against the real app on `:8099` + real MySQL (default profile, data preserved):** `mvnw test` (`Tests run: 1, BUILD SUCCESS`) proves Spring Data resolved both derived query names (a bad path — e.g. `existsByDriverUserId` against `Driver`'s `@MapsId` PK — fails bean creation with `PropertyReferenceException`), but the behaviour itself was verified by driving the real form endpoints and reading the rendered flash off the pages:
  - Bus: create bus → record incident → `GET /admin/buses/delete/{id}` **blocked**, bus survives in DB → delete the incident → delete the bus **succeeds**. The guard is a cleanup prompt, not a dead end.
  - Driver: create driver → incident referencing them → delete **blocked** → unlink the driver from the incident (`driver=` empty → `driver_id = NULL`) → delete **succeeds**, `User` cascade fires, and the incident survives. The optional-driver escape hatch works as its message claims.
  - Probes: a bus id that does not exist still returns the pre-existing `Không tìm thấy xe cần xóa với ID: …`; a bus with **both** trips and incidents reports the trip guard first (it is checked first) — the incident guard then appears as a second wall once trips are cleared.
  - DB returned to its exact opening snapshot afterwards (`buses 20 / drivers 36 / trips 12 / incidents 6 / users 37`), no leftover test rows, **orphan check empty**, zero template/SpEL/property errors in the app log.
- **Owner decision from that verification — `RESOLVED` incidents also block deletion (approved):** verification surfaced that the guards do not filter by `IncidentStatus`, so a long-closed report still locks its bus (and `Bus` has no soft-delete). Kept deliberately; full rationale and the explicit "do not optimise this to `OPEN`/`IN_PROGRESS`" warning are in Developer Notes. Docs state it explicitly (`current_functional_spec.md`, Fleet + Driver rules).
- **Correction to the Phase 2 entry above (recorded here rather than editing it, per Rule 7):** that entry states *"All Phase 2 test data was cleaned from the database afterwards"* — **not accurate**. As of 2026-07-17 the real `busmanagement` DB held 6 incidents, three stamped that morning (owner's manual testing). Harmless, but future sessions should not trust that line as a guarantee of a clean incidents table.
- **Observed while driving the app (unrelated, not fixed):** the AI extra-trip scheduler logs `[AI] Chuyến #N: còn … giờ đến khởi hành (< 72 giờ yêu cầu)…` continuously while idle — hundreds of lines in minutes, drowning real output. Worth a look before any phase that needs readable logs.
- **No project verify skill exists** (`.claude/skills/` absent), so every session cold-starts the app. Recipe that worked, if it is ever worth persisting: `mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=8099"` (default profile — **never** `demo`), drive the real form POSTs with a curl cookie jar, read the flash message off the redirected page, cross-check every state change in MySQL, and use a **fresh cookie jar per probe** — a shared session lets one request's flash leak into the next and fake a failure.
- **Remaining TODOs:** Phase 3 (unchanged — begin the dry-run wrappers). One carried-over item: Phase 2 remains **intentionally uncommitted** per the owner, and the incident delete-guards above are a separate, self-contained change that should be its own commit.

### 2026-07-17 — Roadmap-vs-project consistency audit; roadmap amended (no feature code)
- **What was implemented:** No feature code. At the owner's request, audited this roadmap against the full project (source, templates, config, git history, original proposal) for correctness, consistency and logic. Nearly all factual claims held — including every `TripService` line number and visibility in the Developer Notes, the Phase 0 profile/`@Profile("demo")`/test-DB setup, the `Incident` nullability decisions, both delete-guards, and the proposal citations (II.5, IV.2). Three defects were found and corrected with owner approval.
- **Files modified:** `docs/development/THESIS_ROADMAP.md` only (Section 4 Non-Goals, Section 5 Phase 4 scope, Section 7, Section 9 new Developer Note, this entry).
- **Correction 1 — Phase 2 commit status was stale (Section 7 updated):** Section 7 and the two entries above still said Phase 2 was *"not yet committed"*, but git shows it **was committed**: `8b3015d` (Incident Management), `934b034` (incident delete-guards, as its own commit exactly as the carried-over TODO above prescribed), `2e2a613` (owner's DOCTYPE fix), `12e7763` (docs + verify skill) — all on branch **`temp`**, not merged to `main`. The stale statements were accurate when written; they are superseded here rather than edited, per Rule 7. Related staleness, same correction: the entry above says *"No project verify skill exists (`.claude/skills/` absent)"* — the very commit that recorded that line (`12e7763`) also **added** `.claude/skills/verify/SKILL.md`, so future sessions should use the skill, not cold-start the app from the recipe in that entry.
- **Correction 2 — Phase 4 scope re-delivered a shipped feature (Section 5 + Developer Note):** as worded, Phase 4 ("wrap `getDrivingHoursForDate()` into a driver workload report") described `DashboardService.buildDriverStats()`'s `topLoadedDrivers` — already live on `dashboard-analytics.html` — almost verbatim; the same trap caught in Phase 7's Vehicle Replacement scope earlier the same day. Phase 4 is now scoped to the missing half: under-utilization/availability ("who can take a trip?") on a selectable date. Rationale recorded in Developer Notes so it is not re-litigated.
- **Correction 3 — silently-absent proposal items made explicit (Section 4):** the driver-facing portal (proposal II.1–II.4), fare/pricing management (I.1.d), ticket cancellation and reviews (III.3.2, III.4) and the daily cron job (IV.3) appeared in neither scope nor Non-Goals. All are now explicit Non-Goals with reasons, including the note that Phase 2 is a deliberate **re-interpretation** of II.5 (Admin records incidents on behalf of drivers, since no authentication exists) — this must be stated in the thesis defense rather than discovered by comparison with the proposal.
- **Correction 4 — Phase 7's dependency on Phase 4 removed (verified before changing, owner-approved):** the suspicion that the dependency was unjustified was confirmed against git history: `Dependencies: Phase 3, Phase 4, Phase 6` is verbatim from the first roadmap commit (`79b5c4a`), predating the move of Cost/Revenue Estimation out of the Phase 4 group into Phase 7 — the move that made it a relic. Nothing in the approved pipeline consumes Phase 4's deliverable. Now `Phase 3, Phase 6`, with the relic's origin noted inline and in Developer Notes.
- **Correction 5 — Phase 5/6 time axis pinned to `Trip.departureTime` (verified before changing, owner-approved):** confirmed that (i) `@CreationTimestamp` stamps INSERT time unconditionally and `updatable = false` blocks JPA updates, so a backfill cannot backdate `createdAt`; (ii) every existing time-window query in `TripRepository` already keys on `departureTime`, and no validation blocks past departure dates. Phase 5 scope now names `departureTime` as the dataset's time axis and forbids backdating `createdAt`; Phase 6 scope states time-slots bucket `departureTime`; Developer Notes record the semantics + mechanics rationale, including an explicit "do not native-SQL backdate" warning. `createdAt` keeps its booking-velocity/audit role (Hidden Cost #2), unchanged.
- **Remaining TODOs:** Phase 3 (unchanged — begin the dry-run wrappers). Branch situation to decide with the owner: everything sits on `temp`, unmerged to `main`.

---

## 9. Developer Notes

*(Implementation decisions that are not obvious from the code — prevents future sessions from re-litigating the same discussions.)*

- **Why Cost/Revenue Estimation moved out of the "current-state" group (Phase 4) into the post-Forecast group (Phase 7):** "Estimation" implies a forward-looking projection, which needs Demand Forecast's predicted-demand output as input. Driver Recommendation, by contrast, only reports current-day driving-hour workload (`getDrivingHoursForDate()`) and genuinely needs no forecast — it stays in Phase 4.
- **Why Historical Data Preparation (Phase 5) is a separate phase from Demand Forecast (Phase 6):** Bundling backfill script work into "the Forecast phase" blurs an infrastructure/data concern with an algorithm concern. Splitting them makes both easier to grade/present independently in the thesis.
- **Why "Business Rules" and "Resource Availability" are not implemented as two sequential services despite being two boxes in the pipeline diagram:** In the existing code, `TripService.findBestAvailableBus()` already applies both as interleaved filters over the same candidate stream (`.filter(!isBusBusy(...)).filter(!needsMaintenance()).filter(!isNearMaintenance(...))`), and filter intersection is order-independent. Implementing them as two separate, sequential services would duplicate logic that already exists — directly violating the "no parallel architecture" principle. Keep two boxes in diagrams for clarity; implement as one reused call.
- **Why the Recommendation Engine's final Business Rule Validation gate reuses `validateBusForTrip`/`validateStaffForTrip` (via the Phase 3 dry-run wrapper) rather than reusing `findBestAvailableBus`/`findBestAvailableDriver` again:** This mirrors an already-proven pattern in the codebase: `TripService.confirmAutoAssignedTrip()` selects resources via the filtered helper methods, then re-validates via the throw-based validators immediately before committing, because state can change between selection and commit. A Recommendation Card may sit unacted-on for hours or days (much longer than the live AI auto-assign flow), so this staleness risk is even more relevant here — hence the explicit "mark as stale, don't auto-retry" rule if the final gate fails.
- **Why the incident delete-guards deliberately do NOT filter by `IncidentStatus` — a `RESOLVED` incident also blocks deleting its bus (owner-approved 2026-07-17):** `BusService.deleteBus()`/`DriverService.deleteDriver()` call `existsByBusId`/`existsByDriverUserId` with no status filter, so a report closed months ago still prevents a hard delete. This was surfaced by end-to-end verification (a bus whose only incident was `RESOLVED` stayed undeletable) and **explicitly kept**. Rationale: the guard protects **referential integrity**, not open work — an orphaned `incidents.bus_id` is equally broken whether the report is `OPEN` or `RESOLVED`. More importantly, closed reports *are* the data Phase 7's Vehicle Replacement heuristic consumes (incident frequency per bus); silently orphaning them would corrupt that input, defeating the reason `Incident.bus` is mandatory in the first place. Accepted consequences, both deliberate: (a) `Bus` has no soft-delete, so a vehicle with any incident history is retired by moving it to `REPAIRING`, not by deletion; (b) genuinely removing such a bus means deleting its incident reports first, which does destroy that frequency history — an explicit, Admin-made trade rather than a silent one. **Do not "optimise" this to `status IN (OPEN, IN_PROGRESS)`** — that re-opens orphan creation for exactly the closed reports Phase 7 depends on.
- **Why Phase 7 needs additive public overloads on `TripService`, and why that does not contradict "no changes to `TripService`":** Section 3 names five methods to reuse, but as of Phase 2 **only `getDrivingHoursForDate()` is `public`** — `findBestAvailableBus()` (~line 276), `findBestAvailableDriver()` (~335), `validateBusForTrip()` (~684) and `validateStaffForTrip()` (~744) are all `private`. This bites differently per phase, which is why it went unnoticed: Phase 3's dry-run wrappers live **inside** `TripService`, so private access is fine; Phase 4 consumes the one already-public method; but Phase 7's Recommendation Engine is a **new** service (Section 3: new modules follow Controller → Service → Repository), and a separate class cannot call a private method. The resolution is a pattern the codebase already demonstrates: `getDrivingHoursForDate()` exists twice — a `private (Driver, LocalDateTime, Long excludeTripId)` implementation and a `public (Driver, LocalDateTime)` overload delegating to it. Phase 7 adds the same kind of public overload for bus/driver selection: purely additive, no existing method changes visibility, no logic touched. **Do not** instead (a) flip the existing private methods to public wholesale — that widens the blast radius of the most correctness-sensitive code in the app for no benefit, or (b) move the Recommendation Engine inside `TripService` — it is already ~1,100 lines, and Section 3 forbids growing god-components.
- **Why What-if Simulation is scoped to aggregate/statistical output only:** `findBestAvailableBus()`/`findBestAvailableDriver()` are tightly coupled to live repository queries (not pure functions over an injectable resource pool). Refactoring them to support hypothetical "what if we had N more buses" scenarios cleanly would be the single highest-risk refactor identified across the entire roadmap, touching the most correctness-sensitive code in the app. Rejected in favor of a narrower simulation computed from existing Forecast/Analytics aggregates.
- **Why Vehicle Replacement Recommendation's MVP requires no new entity:** `Bus` only stores a maintenance *snapshot* (current odometer, threshold) — there is no historical log of past maintenance events (date, cost). A full version needs a new `MaintenanceRecord` entity; deferred as a stretch goal so MVP can ship with a simple heuristic on existing fields.
- **Why Phase 4's scope is under-utilization/availability, and deliberately not a "busiest drivers" report (owner-approved 2026-07-17):** the busiest-today ranking already exists — `DashboardService.buildDriverStats()` maps active drivers through `tripService.getDrivingHoursForDate(d, now)` into `DriverWorkloadDto`, sorts descending, and `dashboard-analytics.html` renders it as `topLoadedDrivers`. Phase 4 as originally worded ("wrap `getDrivingHoursForDate()` into a driver workload report") described that shipped feature almost verbatim — the same re-delivery trap caught in Phase 7's Vehicle Replacement scope on 2026-07-17 (see the odometer note below). What is genuinely missing, and what makes Phase 4 a *Decision Support* deliverable rather than a monitoring stat, is the inverse question: *"who has capacity to take a trip?"* — least-loaded active drivers, on a date the Admin picks, framed as a staffing recommendation. Do not "simplify" Phase 4 back into a top-busy list.
- **Why Vehicle Replacement ranks on lifetime `Bus.odometer` + incident frequency, and deliberately not on `Bus.kmSinceLastMaintenance`:** that field is `odometer - lastMaintenanceOdometer`, so it **resets at every service** — a bus with 800,000 km and 50 past services reads a low value. It answers *"is this bus due for a service?"*, not *"is this bus worn out?"*. The first question is **already delivered**: `DashboardService.buildMaintenanceAlerts()` filters `needsMaintenance() || isNearMaintenance(0)` and ranks by `kmSinceLastMaintenance` desc into `BusAlertDto` — the original proposal's I.3.a ("Cảnh báo bảo dưỡng"), live on the dashboard today. Scoring replacement on the same field would re-deliver a shipped feature under a new name, violating the "do not duplicate existing business logic" principle. `odometer` never resets (a true wear/age proxy) and incident frequency is the reliability signal — which is also what finally makes mandatory `Incident.bus` pay off (see the note above on why `bus` is mandatory). Do not "simplify" this back to `kmSinceLastMaintenance`.
- **Why Phase 4 was removed from Phase 7's dependencies (owner-approved 2026-07-17):** the line `Dependencies: Phase 3, Phase 4, Phase 6` had been present verbatim since the roadmap's first commit (`79b5c4a`). It dates from the draft in which Cost/Revenue Estimation still lived in the Phase 4 "current-state" group — back then Phase 7 genuinely consumed a Phase 4 deliverable. When Cost Estimation moved into Phase 7 (see the first note in this section), the dependency became a relic: the approved pipeline consumes only Phase 3's dry-run gate and Phase 6's forecast, and Phase 4's under-utilization report feeds nothing in it. The roadmap's own convention (Phase 9: *"Technically independent … but deliberately scheduled last"*) is to state ordering preferences as prose, not as dependencies — so the relic was removed rather than reworded. Phase 4 remains scheduled before Phase 7 simply by its number.
- **Why Demand Forecast keys on `Trip.departureTime`, and deliberately not on `Trip.createdAt` (owner-approved 2026-07-17):** two independent reasons. *Semantics:* demand for a route/time-slot means tickets sold on trips **departing** in that slot — `departureTime` is the business time axis, and every existing time-window query in `TripRepository` (dispatch board, daily driving-hours, analytics ranges) already keys on it; `createdAt` records when the row was inserted, which for admin-created trips is an operational accident. *Mechanics:* the Phase 5 backfill cannot backdate `createdAt` anyway — Hibernate's `@CreationTimestamp` generates the value at INSERT regardless of anything assigned, `updatable = false` blocks later JPA updates, and the entity javadoc explicitly says it is never set manually. **Do not "solve" this with a native-SQL backdate** — that would falsify a technical audit timestamp to serve a purpose `departureTime` already serves honestly. `createdAt`'s intended consumer remains the booking-velocity stretch goal (Hidden Cost #2), where insert-time is exactly the correct meaning.
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
- **Why `Incident.status` has no FSM even though `Trip.status` does (Phase 2, owner-approved):** the two model different things. A trip's status drives real side effects (bus availability, odometer, ticket sales) so an illegal transition is a data-integrity problem — hence the whitelist FSM. An incident's status is a human progress note with one derived field (`resolvedAt`); closing one by mistake is an ordinary clerical error, and an FSM making `RESOLVED` terminal would force the Admin to delete and re-file the report to correct it. Free transitions plus automatic `resolvedAt` sync was chosen instead. Do not "harmonise" this with the Trip FSM without asking.
- **Why `Incident.bus` is mandatory while `trip` and `driver` are optional (Phase 2, owner-approved):** every incident is ultimately about a vehicle, and a mandatory `bus` is what makes "which bus fails most often" answerable — the intended input for Vehicle Replacement Recommendation in Phase 7. `trip` must stay optional because a bus can break down in the depot, outside any trip; `driver` must stay optional because there is no authentication (`SecurityConfig` is permit-all and `02_project_context.md` forbids building on `SecurityContextHolder`), so the reporter cannot be derived from a session and the Admin may simply not know it.
- **What happens when a trip referenced by an incident is soft-deleted (probed, Phase 2):** `Trip` carries `@SQLRestriction("is_deleted = false")`, so a soft-deleted trip disappears from every query — including the `LEFT JOIN FETCH i.trip` in `IncidentRepository`. Verified behaviour: nothing throws (no `EntityNotFoundException`), the incident list and edit pages still load, and the incident simply renders as "Ngoài hành trình" as if it never had a trip. Two consequences to be aware of: the display is silently misleading in that state, and re-saving such an incident writes `trip_id = NULL` permanently, because the soft-deleted trip is absent from the form's dropdown. This was accepted rather than fixed — the alternatives (blocking trip deletion when incidents reference it, or bypassing the restriction) would both reach into `TripService`, which Phase 2 is explicitly not allowed to do.
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
