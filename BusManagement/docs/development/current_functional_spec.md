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
    *   The three maintenance figures are validated in `BusService` on both create and edit: odometer and last-maintenance reading may not be negative, the odometer may not be **below** the last-maintenance reading (that would make `kmSinceLastMaintenance` negative, so the vehicle would never come due for service), and the threshold must be greater than zero (a threshold of `0` makes `needsMaintenance()` permanently true, locking the vehicle out of every trip — servicing it cannot clear the condition).
    *   When **editing** a bus, leaving one of those three boxes empty means **keep the current value**, not "set it to zero"; to store a zero the operator types `0`. Only on **creation** does an empty box fall back to the defaults (0 / 0 / 5000 km).
    *   Bus status is automatically synchronized during trip lifecycle transitions.
    *   **A bus's type may not be lowered below the seats its own unfinished trips are selling (Group C(d), 2026-09-23).** Rule #22 ("a trip's `totalSeats` may not exceed the assigned bus's capacity") lives in `validateBusForTrip()`, so it is only checked when a bus is *assigned to a trip*; changing the bus's **type** afterwards was a back door around it, and moving a 50-seat bus down to 22 left every open trip overselling with a success message. `BusService.updateBus()` now refuses it, using the same unfinished-trip status set as the `REPAIRING` guard (`PENDING_APPROVAL`/`ACTIVE`/`DEPARTED`). The rule is **"do not make it worse"**: it fires only when the new type is *smaller than the current one*, so keeping the same type or upgrading is always allowed — upgrading is in fact how an operator clears a pre-existing violation. That asymmetry is deliberate: bus 20 already carries a legacy violation (trip 6, `DEPARTED`, 40 seats on a 22-seat bus, kept as history), and a guard phrased only as "new capacity < seats sold" would have made that bus permanently unsaveable, even for an odometer correction. A bus with no type has no capacity to compare and is skipped, exactly as rule #22 skips it.
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
    *   CRUD management of Routes (`AdminRouteController` / `RouteService`, entry point `/admin/routes`): total distance in kilometres, estimated duration in minutes, an optional suitable bus type, an optional holiday passenger flow, and an ordered list of stops held in the join entity `RouteStation`. The form supports adding/removing stops; `stopOrder` is renumbered `1..n` from the submitted row order, so the first stop is the departure point and the last is the destination.
    *   A route has **no name field** — its display label is derived from its first and last stop (`Route.getDeparturePointDisplay()` / `getDestinationPointDisplay()`).
*   **Business Rules:**
    *   A route must have at least 2 stops (a departure point and a destination).
    *   A station may appear at most once per route: `RouteStation`'s primary key is the composite `(routeId, stationId)`, so a route revisiting the same station (e.g. A→B→A) is not representable.
    *   A route already used by any trip cannot be deleted (operational and revenue history is preserved). Deleting an unused route removes its `RouteStation` rows via `cascade = ALL`.
    *   **The suitable bus type is a preference, not a rule (Group C(a), owner ruling 2026-09-23).** No validator checks it. The approve screen, the trip edit form and the trip create form offer **every** bus type, list buses of the route's type first and mark them "★ đúng loại tuyến"; a bus of another type is accepted whenever it passes the other rules — in particular the seat-capacity rule (#22), which is what actually protects passengers. AI auto-assignment (`findBestAvailableBus`) follows the same order since 2026-09-24 (owner ruling, defect #26): a bus of the route's type first, and when none is free it takes a bus of another type instead of giving up — until then it applied the type as a hard filter, so a single shortage of the preferred type sent the extra trip to manual approval. *(Until 2026-09-23 the approve/edit dropdowns hard-filtered by type while the create form and the validator did not, so the same trip could be created with a bus the edit form would not offer.)*
    *   **The holiday passenger flow is read only by demand forecasting (forecast redesign, decision QĐ-8, 2026-10-21).** `Route.holidayFlow` says whether the route carries passengers away from a labour hub (Hà Nội, TP.HCM — "Rời TP lớn"), toward one ("Về TP lớn"), or neither (empty — "Không theo chiều"). Around Tết the two directions peak at opposite times, so the redesigned forecast learns holiday effects per flow instead of pooling every route. The Admin chooses it on the route form; it is optional, and choosing "-- Không theo chiều --" on edit clears a previous value. No validator, assignment or pricing rule reads it. *(As of 2026-10-21 the redesigned forecast is not built yet, so the field is stored and shown but nothing consumes it.)*
    *   **Editing a route's distance while a trip is on the road warns, it does not block (Group C(c), owner ruling 2026-09-23).** The odometer credit at `DEPARTED → COMPLETED` reads `route.distanceKm` at completion time, so a changed distance applies to trips already `DEPARTED` on that route. The save goes through and the route list shows a warning naming how many trips are affected and the old and new distance. It is not blocked because the system cannot tell a typo fix (which should apply) from a real re-routing; snapshotting the distance on the trip at departure would need a schema change. `ACTIVE` and `COMPLETED` trips do not trigger it: the first have not departed yet, the second were already credited.

### Trip Management
*   **Purpose:** Coordinate specific dispatches of vehicles and staff.
*   **Implemented Functionality:**
    *   Trip scheduling and editing.
    *   Trip status monitoring.
    *   Resource allocation.
    *   Administrative management of trip lifecycle including creation, modification, approval, cancellation, and operational state transitions.
*   **Trip list scope (forecast redesign GĐ2, decision QĐ-12, 2026-10-21):** the list (`/admin/trip-management/trips`) shows every **unfinished** trip (`PENDING_APPROVAL`, `ACTIVE`, `DEPARTED`) whatever its date, plus **finished** ones (`COMPLETED`, `CANCELLED` — the FSM's terminal states, derived from `canTransition`) that departed within the last 30 days; the Admin can switch that window to 7 or 90 days. The status filter keeps the same window, newest first. There is no "all" option: with the simulated history from 2023 (~21,000 trips) the old load-everything page would weigh about 80 MB. The full history is read through the analytics and forecast screens.
*   **Business Rules:**
    *   A trip can be **edited only until it departs**. Once it is `DEPARTED` or `COMPLETED` it is a record, not a plan, and **no field** may be changed — the same status set that `deleteTrip()` refuses to delete, so the two entry points agree status by status. `PENDING_APPROVAL`, `ACTIVE` and `CANCELLED` trips remain editable (a cancelled trip may also still be deleted). A departed trip advances only through the dispatch board, which is the single remaining door for status transitions.
    *   The rule exists because editing a departed or completed trip rewrites facts that other records were derived from: the odometer was advanced at completion using the route assigned **at that moment**, so changing the bus or route afterwards leaves the kilometres on a different vehicle, and completed trips are the observation set the demand forecast reads.
    *   Dynamic resource allocation API endpoint (`/api/admin/trips/available-resources`) that returns conflict-free available buses, drivers and assistants for a specified timeframe. **Drivers and assistants are two separate lists**, because the two roles are bound by two different rule sets: an assistant does not drive, so `validateStaffForTrip()` does not apply the 8-hour daily cap to that role. The assistant list is therefore always a **superset** of the driver list — it omits exactly that one filter and keeps the other three (active, licence valid on the departure date, no schedule clash). The create form feeds `drivers` to the main-driver and co-driver selects and `assistants` to the assistant select; using one list for all three was defect #13.

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
    *   The incident's bus does **not** have to be the trip's bus (ruled 2026-09-21): `Trip.bus` is the planned/departed vehicle and cannot be edited once the trip has departed, so after a mid-trip vehicle swap the only truthful record of a breakdown on the replacement vehicle is an incident whose bus differs from the trip's. No validator compares the two.
    *   The "related trip" dropdown is an **offer scope, not a rule** (2026-09-21): it lists trips that are `ACTIVE`/`DEPARTED` (any date) plus `COMPLETED`/`CANCELLED` trips that departed within the last 7 days (`AdminIncidentController.RECENT_TRIP_DAYS` → `TripService.getRunningAndRecentTrips(since)`), newest first, and the edit form always re-adds the incident's current trip even when it has left that window. `PENDING_APPROVAL` trips are never offered. A request carrying any other existing trip id is still accepted — the foreign key is the only constraint.
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
*   **Known Gaps (by design, not oversight):** No "Recent Activity" feed — it was explicitly declined for this feature to avoid a schema change, and the decision still stands. (The original reason, "no entity has creation/update timestamps", is out of date: `Trip.createdAt` arrived in Phase 0 and `Incident.reportedAt` in Phase 2. Those two alone would still not back a general activity feed, which needs coverage across every entity.) Fleet utilization and AI-suggestion outcome counts are point-in-time snapshots, not historical trends.

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
*   **Purpose:** Predict how many passengers will want each route/departure slot over the coming week (for reinforcement cards) and up to 35 days ahead (preview), so the Administrator can plan reinforcement before demand materialises. This is the system's only genuinely predictive component; every other module reports what has already happened. *(Rebuilt in forecast redesign GĐ5, 2026-10-22, decision QĐ-16 in `forecast_decisions.md`. The occupancy-rate model it replaced is described in `THESIS_ROADMAP.md` §5, Phase 6 "As delivered".)*
*   **Implemented Functionality:**
    *   Entry point: `GET /admin/analytics/forecast` (`ForecastController` → `ForecastService.buildForecastPage()`), read-only (`@Transactional(readOnly = true)`). The screen forecasts; it creates no trip and assigns nobody.
    *   **Unit of forecast — the slot:** one route, one departure hour, one day. All `COMPLETED` trips of the slot add up, extra trips included (they leave at :30/:40/:50, inside the same hour). A slot is sold out when its tickets ≥ its seats. A slot with no completed trip is *unobserved*, not "zero passengers". A series (route × hour) is forecast only with at least **28 observed days** (four full weeks); smaller groups — such as the Administrator's one-off trips at odd hours — are counted and reported rather than silently dropped.
    *   **What is predicted:** passenger **demand** — how many people want to travel — not capped at the seats. A sold-out day only says "demand ≥ seats"; the model's EM step estimates how far above.
    *   **Method:** `SlotDemandModel`, a calendar Poisson regression written in plain Java: per-series level, weekday and piecewise trend (frozen at the last day of data), holiday effects learned jointly per direction group (`Route.holidayFlow`) and pulled towards ×1 by a ridge penalty, 5 EM rounds, and a level correction over the last 28 non-holiday days. It reproduces the Python model measured during the research to about 10⁻⁸.
    *   **Screen:** a 7-day table in passengers per series, with the route's standard bus (its `suitableBusType`; the fleet's average capacity when a route has none) and, on cells above 90% of it, the number of trips needed; a 35-day chart per series; the learned holiday multipliers per direction group; when and how fast the model was fitted.
    *   **Computed once, reused until the data changes:** every call re-reads the history; if the model's input is identical to the previous call's, the fitted model is reused, otherwise it is refitted. There is no scheduler and no stored forecast.
    *   **Self-assessed accuracy (forecast page only):** 8 weekly origins over the last 8 weeks; at each, the model learns only up to that day and forecasts the next 7. It is compared with "SN4 clean" (mean of the same weekday over the last 4 weeks, holidays skipped). Scoring uses only what the app can observe: `min(forecast, seats that ran)` against tickets sold (WAPE), how many sold-out slots were foreseen (forecast ≥ seats), and how well the reinforcement flag matched slots that actually sold above 90% of the standard bus. A "last Tết" block repeats this for the 35 days after the last Sunday at least three weeks before the most recent Tết. Comparing raw demand with tickets would penalise the model exactly when it is right, so it is not done.
    *   **Input filter:** `COMPLETED` trips only. Cancelled trips retain the tickets they had sold before cancellation, so including them would mix unserved demand into the actuals.
*   **Reused Thresholds (not redefined):** a cell needs reinforcement when forecast demand exceeds 90% of the standard bus's seats — the threshold of `Trip.needsReinforcement()`, applied to forecast demand. Trips needed = max(1, ⌈demand ÷ (0.9 × standard seats)⌉), which is ≥ 2 exactly when the flag is set: one definition of "running hot", not two.
*   **Consumers (since forecast redesign GĐ6, decision QĐ-17):** Reinforcement Recommendation and What-if read forecast demand directly. A hot slot needs (trips needed − 1) extra trips, and an extra trip carries only the *expected overflow* that falls into its own seats, computed from the negative-binomial demand distribution the model has learned. The interim occupancy-rate field `predictedOccupancy` of GĐ5 has been removed.
*   **Known Gaps (by design, not oversight):** The historical data is **simulated** (the `backfill` profile, `setup_guide.md` §4.3, because real operating history cannot accumulate within the project timeline), and the page states this prominently — the figures demonstrate the method, not real trading. The dataset grows stale as time passes; the page reports how stale it is. Forecasts stop at the end of the holiday calendar (31/12/2032), with a warning. Forecasts are per route/slot only — no per-station, per-bus-type or price-sensitivity dimension.

### Vehicle Replacement Recommendation
*   **Purpose:** Answer *"which vehicle in the fleet should be considered for replacement first?"* — deliberately a different question from the dashboard's Maintenance Alerts, which answer *"which vehicle is due for a service?"*
*   **Implemented Functionality:**
    *   Entry point: `GET /admin/analytics/vehicle-replacement` (`VehicleReplacementController`), backed by a read-only `VehicleReplacementService`. Read-only — it changes no bus status and removes no vehicle from dispatch.
    *   Ranks the **whole fleet** by a composite priority score, showing each bus's lifetime odometer, vehicle-incident count (split into breakdowns and accidents), both score components, and a plain-language reason.
    *   **Score = 70% wear + 30% reliability.** Wear is the bus's lifetime odometer normalised between the fleet's lowest and highest; reliability is its vehicle-incident count normalised against the fleet's highest. Both weights are shown on the page, so the formula is not a black box.
    *   **Only vehicle-related incidents count** — technical breakdowns and accidents. Traffic jams, flooding and driver illness are excluded, because they describe the journey rather than the machine.
    *   **The page reports its own signal quality.** When most of the fleet has no recorded incidents, the reliability half cannot separate them, and the screen says explicitly that the ranking is being driven by mileage alone.
*   **Reused Thresholds (not redefined):** none — this module introduces no threshold. It deliberately does **not** score on `kmSinceLastMaintenance`: that value resets at every service, so it measures service-interval urgency, not lifetime wear, and it is already the basis of the existing Maintenance Alerts.
*   **Known Gaps (by design, not oversight):** The score is **relative to the current fleet**, so the top-ranked bus always scores highest even if every vehicle is nearly new — it is a priority order for review, never a verdict that a vehicle must be replaced. There is no absolute threshold because the system holds no data to justify one (`Bus` has no manufacture year, purchase date or service-cost history). Total cost of ownership is not computable; that would require a maintenance-record entity, which is a deliberate stretch goal.

### Reinforcement Recommendation (Recommendation Engine)
*   **Purpose:** Answer *"which upcoming route/time-slot is forecast to run hot, how many extra trips does it need, which bus + driver could run each one, how many extra passengers would each carry, and would it make or lose money?"* — the forecast-driven Decision-Support component that consumes both the Demand Forecast (prediction) and Business Rule Validation (constraints). It is an **orchestrator**, not a predictor or a rule engine of its own. *(Moved to demand units in forecast redesign GĐ6, 2026-10-23, decision QĐ-17 in `forecast_decisions.md`.)*
*   **Implemented Functionality:**
    *   Entry point: `GET /admin/analytics/recommendation` (`RecommendationController`), backed by a read-only `RecommendationService` (`@Transactional(readOnly = true)`). Read-only — it creates no trip, assigns nobody, and writes nothing.
    *   **One card per extra trip:** the Demand Forecast is computed **once** per request; a slot whose forecast needs N trips yields N − 1 cards, labelled "extra trip k/(N − 1)".
    *   **Passengers of an extra trip = expected overflow:** demand on a given day is a distribution (negative binomial around the forecast, with the dispersion the model learned). The k-th extra trip carries EO(seats before it) − EO(seats before it + its own seats), where EO(c) is the expected number of people left without a seat when the slot has c seats, and "seats before it" are the standard bus plus the earlier extra trips of the slot. Its own seats are the selected bus's capacity, or the standard bus when none could be selected. Example: 20.1 forecast passengers against a 22-seat standard bus — the extra trip carries about 1.2 passengers on average, not 20.
    *   **Money:** revenue = passengers × the most recent completed trip's price for that route and departure hour (an existing price, consumed as-is); cost = fuel per km × distance + driver wage per hour × duration × drivers, from the Administrator-configured cost parameters; profit = revenue − cost, **which may be negative**. With current prices and costs an extra trip breaks even at about 7 overflow passengers.
    *   **Reservation across cards:** extra trips are considered in descending order of expected profit (computed with the standard bus, before any bus is chosen). For each, the engine selects a bus and a driver (reusing `TripService`) and re-confirms feasibility through the Phase 3 dry-run validation gate; when the card passes, its bus and driver are **reserved** — added to the in-memory schedule as a `PENDING_APPROVAL` trip — so the existing `TripService` rules make later cards avoid that bus and driver at overlapping times and count the hours against the 8-hour daily limit. The list is therefore jointly feasible, and when resources are short the most profitable extra trips get them first.
    *   **Every extra trip gets a card, including the ones expected to lose money** — those carry a "lỗ dự kiến" (expected loss) badge, and the page summarises "X profitable · Y expected loss". The 90% threshold remains the only definition of "needs reinforcement"; whether to run a trip is the Administrator's call. Cards are shown in descending order of expected profit and marked **Feasible** ("Đủ điều kiện", status `RECOMMENDED`), **Stale (needs regeneration)**, or **No resource**.
    *   The cards are **recomputed on every view** — there is no stored recommendation entity and no pending-approval lifecycle; reservations live only for that one view, and acting on a recommendation is a manual step through the normal trip forms.
    *   The page states how stale the underlying (simulated) forecast data is.
*   **Reused Thresholds (not redefined):** the engine reads the forecast's per-cell `needsReinforcement` flag and `tripsNeeded`, and asks the forecast for the passengers of an extra trip; it does **not** know the 90% threshold or the demand distribution. Bus/driver selection and the final validation gate are the **existing** `TripService` methods, reached through additive overloads; no selection or validation logic is duplicated, and the reservation is data only.
*   **Performance note:** selecting resources for every candidate by re-querying the database per driver/bus was measured at ~18.7 s; an in-memory `AvailabilityContext` that preloads the relevant schedule once (rules unchanged, only the data source) brought it to ~1 s with byte-identical output. An equivalence test pins the in-memory checks to the SQL ones, and another pins that a reserved trip is seen by those same checks. The live dispatch/assignment path is untouched. The page still costs about 0.1 s per card (selection plus the SQL gate), so a holiday week with many extra trips is slower.
*   **Known Gaps (by design, not oversight):** The regular timetable of the coming days is not in the database (the simulated history stops at the last day of data), so the buses and drivers of the main trips are not reserved — a card may pick the very bus that will run the main trip. The final validation gate runs on SQL and does not see reservations; avoiding clashes between cards is the selection step's job. The maintenance check looks at each trip's distance on its own, not the sum of trips reserved on one bus. MVP assumes a single main driver (every forecastable route is ≤ 8h). `Stale` is essentially unreachable in the recompute-per-view model (kept as an honest outcome and a divergence tripwire).

### What-if Simulation
*   **Purpose:** Answer *"if we add or remove buses or drivers, if demand rises or falls, or if fuel or wages change, how many of the needed extra trips can the fleet serve, at what cost and profit?"* — the last Decision-Support component: Demand Forecast → Reinforcement Recommendation → What-if. *(Moved to demand units in forecast redesign GĐ6, decision QĐ-17.)*
*   **Implemented Functionality:**
    *   Entry point: `GET /admin/analytics/what-if` (`WhatIfController`), backed by a read-only `WhatIfSimulationService`. Five levers arrive as query parameters: change in buses, change in drivers, demand factor (%, clamped to 50–300), and optional fuel-cost and wage overrides that live **only** in that simulation and are never saved.
    *   Two columns side by side: **Baseline** (real `READY` buses, active drivers, 100% demand, configured costs) and **Scenario** (levers applied).
    *   **Capacity is a counting model, not an assignment:** trips per bus per day = operating window ÷ (average trip duration + the 1-hour bus preparation buffer); the driver ceiling = drivers × 8 h ÷ average duration; the smaller is the daily capacity and names the bottleneck. On the real data: 15 h ÷ (3 + 1) h = 3.75 trips per bus per day. The service never calls the bus/driver selection and never reserves anything.
    *   **Demand in passengers:** scenario demand = forecast demand × factor, not clamped; extra trips needed = Σ (trips needed − 1), using the forecast's own `tripsNeeded` — so at 100% it equals the number of Recommendation cards. Before GĐ6 the factor multiplied an occupancy rate already clamped at 100%, so at 70% demand the page reported no hot slot while 2 extra trips were still needed.
    *   **Coverage per day:** coverable = ⌊Σ over days of min(extra trips needed that day, daily capacity)⌋ — idle capacity on a quiet Tuesday cannot serve Saturday's demand (defect #7).
    *   **Money of the covered plan:** each extra trip (a standard bus) carries the expected overflow that falls into its seats, as on the Recommendation page, rounded to the đồng per trip; within each day the most profitable extra trips are covered first, the same order Recommendation uses to reserve buses. A separate line, "if only the profitable trips run", gives their count and their profit.
*   **Reused Thresholds (not redefined):** trips needed come from `ForecastService.tripsNeeded`, so this service no longer re-declares the 90% threshold; the 8-hour driver day and the 1-hour preparation buffer are re-declared with a pointer to their source.
*   **Known Gaps (by design, not oversight):** Aggregate only — no double-booking, bus-type, licence or per-bus maintenance check; feasibility is confirmed on the Recommendation page, and the page says so. Capacity is total fleet capacity, not net of the regular timetable — an optimistic estimate. Money assumes standard buses, so it matches the Recommendation cards only when those cards run standard buses. Added buses are converted to seats with the fleet's average capacity.

---

## 4. Business Workflows & Constraints

### Trip Creation & Modification (Manual Workflow)
Manual trip creation bypasses the recommendation queue and sets the trip status directly to `ACTIVE`, initializing its ticket sale state.
During manual creation or modification, the backend enforces the following validation checks:

*   **Bus Availability Constraints:**
    *   The bus status must not be `REPAIRING`. A `TRAVELING` flag blocks only when **no `DEPARTED` trip of that bus explains it** (defect #19) — when a running trip does, the double-booking window below decides.
    *   **Seat-capacity rule (defect #22, 2026-09-19):** the trip's `totalSeats` must not exceed the assigned bus's `busType.capacity`. Selling fewer seats than the bus has is a business choice and is allowed; selling more is rejected. A bus without a type has no capacity to compare against and is not checked. This one rule, inside `validateBusForTrip()`, covers manual create, edit, both approval doors and the Recommendation dry-run gate.
    *   **Double-Booking Check:** The bus must not be assigned to another trip overlapping with the window `[departure - 1 hour, arrival + 1 hour]` (preparation buffer).
    *   **Maintenance Block:** The bus cannot be assigned if it has already exceeded its maintenance threshold (`odometer - lastMaintenanceOdometer >= maintenanceThreshold`) or if the distance of the trip will push it into the warning band (`kmSinceLastMaintenance + distance >= maintenanceThreshold * 0.9`). *(Both clauses measure km **since the last service**, not lifetime odometer — `Bus.kmSinceLastMaintenance` is `odometer - lastMaintenanceOdometer`, which is why the first clause spells that subtraction out. Until 2026-08-11 the second clause read `odometer + distance`, which would have compared a lifetime reading against a service interval; the code, `Bus.isNearMaintenance()`, was always right.)*
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
4.  **Create Recommendation (`createExtraTrip()`):** Generate a new suggestion in `PENDING_APPROVAL` status with a departure time offset by +30 minutes from the original trip. The suggestion's `totalSeats` is the **capacity of the bus the AI assigns** (defect #22, 2026-09-19); only when no bus could be assigned does it keep the original trip's seat count as a **placeholder**. The placeholder never reaches rule #22: manual approval (`approveTrip()`) sets `totalSeats` to the capacity of the bus the Admin picks (defect #26, 2026-09-24), the same rule as the AI path — a seat count the Admin typed on the original trip is a decision for that trip only. A bus without a type keeps the placeholder (no capacity to use); a bus smaller than the tickets already sold is refused. To sell fewer seats than the bus holds, edit the trip after activation.
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
8.  **No activity/audit-log-based analytics:** A "Recent Activity" widget was considered for the Dashboard & Analytics module but intentionally omitted — adding schema just for this feature was explicitly declined. Only `Trip.createdAt` (Phase 0) and `Incident.reportedAt` (Phase 2) carry a creation timestamp; no entity has `updatedAt`, so the coverage a general activity feed needs does not exist. A proper audit log would need to be designed separately.

---

## 10. Glossary
*   **ACTIVE:** The state indicating that a trip is active and open for ticket sales.
*   **DEPARTED:** The state indicating that the bus has departed and the trip is underway.
*   **HOT TRIP:** An active trip that has met passenger occupancy thresholds and time constraints, qualifying it for extra trip recommendations.
*   **EXTRA TRIP:** An additional trip generated by the system (or created manually) on the same route to relieve passenger demand on a hot trip.
*   **EXPECTED OVERFLOW:** The expected number of passengers left without a seat when a slot has a given number of seats, under the forecast's demand distribution. An extra trip's passengers are the part of it that falls into the extra trip's own seats.
*   **PENDING_APPROVAL:** The initial state of an AI-suggested extra trip, awaiting admin review.
*   **OPERATIONAL KPIs:** Dashboard metrics requiring immediate admin action (Pending Approvals, Upcoming Trips, Maintenance Alerts, etc.), always visible at the top of `/admin/analytics`.
*   **STRATEGIC ANALYTICS:** Dashboard metrics for periodic review (Fleet, Trips, Routes, Drivers, Occupancy, AI Recommendation), organized into tabs on `/admin/analytics`.
