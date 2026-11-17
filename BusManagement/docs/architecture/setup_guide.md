# Developer Setup Guide

This guide covers all steps to clone, configure, and run the Bus Management System locally.

---

## 1. Prerequisites

| Requirement      | Version         | Notes                                          |
|------------------|-----------------|------------------------------------------------|
| Java (JDK)       | 17 or higher    | Required. App uses Java 17 features.           |
| Apache Maven     | 3.9.12 via wrapper | The Maven Wrapper (`mvnw`, `.mvn/wrapper/maven-wrapper.properties`) downloads it; no separate install needed. |
| MySQL Server     | 8.x recommended | Must be running before starting the app.       |
| Git              | Any recent      | For cloning the repository.                    |
| Docker           | —               | **Not implemented.** No Dockerfile or compose file exists in this project. |

---

## 2. Clone the Repository

```bash
git clone https://github.com/giang-it/BusManagement.git
cd BusManagement
```

The project source lives in the `BusManagement/` subdirectory:

```
BusManagement/          ← repository root
└── BusManagement/      ← Maven project root (contains pom.xml)
```

All subsequent commands must be run from the **inner `BusManagement/` directory**.

---

## 3. Database Setup

### 3.1 Create the Database

Start your MySQL server, then create the target database:

```sql
CREATE DATABASE busmanagement;
```

> The application uses `sessionVariables=foreign_key_checks=0` in its JDBC URL for schema recreation. Ensure your MySQL user has sufficient privileges.

### 3.2 Configure Credentials

Open `src/main/resources/application.properties` and update the datasource block:

```properties
spring.datasource.url=jdbc:mysql://${MYSQL_HOST:localhost}:3306/busmanagement?sessionVariables=foreign_key_checks=0
spring.datasource.username=YOUR_MYSQL_USERNAME
spring.datasource.password=YOUR_MYSQL_PASSWORD
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

The `${MYSQL_HOST:localhost}` syntax allows overriding the host via an environment variable named `MYSQL_HOST`. If the variable is not set, it defaults to `localhost`.

**No other environment variables are required** in the current codebase.

---

## 4. Schema & Seed Data

The application has two data policies, selected by Spring profile.

### 4.1 Default (persistent) — no profile flag

```properties
spring.jpa.hibernate.ddl-auto=update
```

Data is **preserved across restarts**. Hibernate only adds missing schema elements; it never drops tables. `DataInitializer` is annotated `@Profile("demo")` and therefore does **not** run — nothing is deleted or seeded.

This is the default because historical data is a hard prerequisite for the analytics/forecasting modules (see `docs/development/THESIS_ROADMAP.md`, Phase 5-7). If wiping were the default, a single restart without the right flag would silently destroy the dataset.

### 4.2 Demo profile — explicit opt-in

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

```properties
# application-demo.properties
spring.jpa.hibernate.ddl-auto=create-drop
```

> **Warning:** This **drops the entire `busmanagement` schema and wipes all data**, then reseeds. Do not use it when the database holds historical data you need to keep.

Under this profile a `DataInitializer` bean executes after schema creation to seed:
- Test users and driver profiles
- Bus types and vehicles
- Stations and routes
- Pre-scheduled active trips for demonstration

No manual SQL import is needed.

**First-time setup:** a fresh clone starts with an empty database. Run once with `-Dspring-boot.run.profiles=demo` to populate sample data, then use the default profile from then on to accumulate data.

### 4.3 Backfill profile — historical dataset (Phase 5, generator rewritten in the forecast redesign GĐ2 and again in route pairs CT3)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=backfill
# optional, research only: also write the TRUE demand of every slot to a CSV (never stored in the DB)
./mvnw spring-boot:run -Dspring-boot.run.profiles=backfill -Dspring-boot.run.jvmArguments="-Dbackfill.truth-csv=C:/tmp/truth.csv"
# optional: which routes to generate (default: the three Miền Đông pairs of the real DB)
./mvnw spring-boot:run -Dspring-boot.run.profiles=backfill -Dspring-boot.run.jvmArguments="-Dbackfill.route-ids=2,12,5,13,10,11"
```

Runs `HistoricalDataBackfill` (decisions QĐ-10/QĐ-11 in `docs/development/forecast_decisions.md`, QĐ-21 in `docs/development/route_pairs_decisions.md`). It simulates the **passenger demand** of every slot (route of `backfill.route-ids` × 06:00/12:00/18:00 × day) from **01/01/2023 to yesterday**, then cuts it by the seats a simulated past operator actually ran. Tickets sold = min(demand, seats). Weekday, season, Vietnamese holidays (`HolidayCalendar`, shaped by each route's `holidayFlow`), level drift, storm closures and 6 % random cancellations are all simulated; every magnitude is an assumption. The two directions of a pair share their level, drift, jumps and yearly holiday size, and the extra passengers of a return wave are r × those of the outbound wave, r uniform in [0.85, 1.15].

**Where the data stands.** Since route pairs CT4 (2026-11-17) the real DB holds the CT3 generator's history: 26,670 trips from 01/01/2023 to 16/11/2026 on routes 2, 5, 10, 11, 12, 13 (1,973 extra, 1,642 cancelled), 14.5 % of open slots sold out, 0 violations in `docs/development/route_pairs_research/ct3_check_new_history.sql`. This is the dataset Demand Forecast consumes. The previous history (21,192 trips of the GĐ2 generator written on 2026-10-22, routes 1–5) was **archived, not deleted**: its rows are soft-deleted (`is_deleted = 1`), so no query of the app sees them, and its km were taken off both odometer marks. `ct4_restore_old_history.sql` in the same folder swaps the two histories back. Backups of both are in `ProjectBackup\db\`.

- **Additive, never destructive.** This profile does **not** activate `demo`, so `DataInitializer` does not run and nothing is wiped. The default `ddl-auto=update` still applies.
- **Deterministic and independent of the run date.** Every demand figure of a day comes from a seed mixed with that day, and the slow processes (level drift) are computed from the fixed start 01/01/2023. Running today or topping up next month gives exactly the same demand for the same day — the old generator recomputed its trend from "today − 84" and reset on every top-up.
- **Safe to re-run; tops up by continuing.** A day that already holds generator-shaped trips is skipped entirely; a second run inserts 0 rows. Buses run in loops, so the generated days must be one unbroken stretch from 01/01/2023 (otherwise the run stops), and a later run starts where the last one ended: it reads each bus's and driver's last trip, free time and driving minutes back from the DB. Only extra trips that straddle the boundary may differ from a single run.
- **Refuses to mix with an older generator's data.** The new data always starts on 01/01/2023. If generator-shaped trips exist but that day is empty, the run stops: take the old history out of sight first (back up, then the PART A script and the archive script `ct4_archive_old_history.sql` in `docs/development/route_pairs_research/`; soft-deleted rows are invisible to the generator).
- **Business rules hold by construction (QĐ-21 (c), (d)):** a bus or a driver leaves from the station where their previous trip arrived — the home station (`Bus.homeStation`, `Driver.homeStation`) before their first trip; a bus rests ≥ 1 h and a driver ≥ 30 min between trips; a driver drives ≤ 8 h in every calendar day (an overnight trip is split at 00:00); every route is ≤ 8 h and has its reverse in the list; a driver's licence is valid on the departure date (`Driver.isLicenseValid`); seats = the assigned bus's capacity, tickets ≤ seats. Extra trips use buses standing at their home station; an extra that reaches the far end comes straight back on the earliest regular return both bus and driver can make, and a return wave is fed by sending buses up ahead — those legs usually sell 0 tickets and are stored as `COMPLETED` extra trips. A regular trip cancelled at the far end sends its bus back the same way, so buses do not pile up there.
- **Trips already in the DB keep their bus and crew** (forecast redesign GĐ3, defect #33). A trip that is not the generator's own and is not `CANCELLED` — one an Admin created, or a real trip later on — holds its bus, driver, assistant and co-drivers on every calendar day it touches, so the generator assigns them nothing that day. `COMPLETED` counts too: in history, a completed trip did use the bus. Such trips stay outside the generator's loops: they do not move anyone.
- **Updates bus odometers, without disturbing maintenance state.** Completed trips add their route distance to `odometer` **and the same amount to `lastMaintenanceOdometer`**. Because `kmSinceLastMaintenance` is the difference of the two, it is mathematically unchanged — maintenance alerts, `needsMaintenance()`, and bus selection all behave exactly as before, while lifetime mileage becomes a real wear signal for Phase 7. Cancelled trips add nothing, matching the FSM. Re-running adds 0 km.
- **Never combine with `demo`** — that profile uses `create-drop` and would wipe the schema before the backfill runs.
- **Stops instead of guessing.** It writes nothing when `backfill.route-ids` names a route that is missing, longer than 8 h, without stations, or without exactly one reverse route in the list; when a bus or an active driver has no home station; when there is no bus or no active driver; and it never generates past 31/12/2032, the last day `HolidayCalendar` covers — extend the lunar table there first.
- **Top it up before a demo.** The history ends on the last day it was generated (the real DB: 16/11/2026), and the forecast pages say how many days old it is. A top-up adds only the missing days. **Check first (open item M-16 in `forecast_decisions.md`):** a trip an Admin created at exactly 06:00, 12:00 or 18:00 (minute 0, 30, 40 or 50) on a generated route looks like the generator's own, so the generator skips that whole day and its history keeps only the manual trip. On 2026-10-23 the real DB had no trip at all after 21/10/2026, so a top-up would not hit this.

The generated rows carry the **script's run date** in `created_at` (Hibernate `@CreationTimestamp` cannot be backdated, and the column is `updatable = false`). This is expected: the dataset's time axis is `departure_time`. Do not "fix" it with native SQL.

> **The data is simulated.** After running this, dashboard totals (trip counts, tickets sold, revenue) are dominated by generated figures. Say so explicitly in any report or demo.

### 4.4 Test configuration

`src/test/resources/application.properties` points the test suite at a **separate database** (`busmanagement_test`, auto-created via `createDatabaseIfNotExist=true`) using `create-drop`. This keeps `mvnw test` from touching the real `busmanagement` database.

Note that this file **fully shadows** `src/main/resources/application.properties` during tests (the test classpath takes precedence; the two files are not merged), so every required property is redeclared there.

---

## 5. Security Configuration

Spring Security is **on the classpath and its auto-configuration is active**; the application is **permit-all by code**, not disabled:

- `config/SecurityConfig` defines the one and only `SecurityFilterChain`: `anyRequest().permitAll()`, with CSRF, form-login and HTTP Basic all switched off. There is no login page and no user store in use.
- `BusManagementApplication` excludes `UserDetailsServiceAutoConfiguration` (`@SpringBootApplication(exclude = …)`), so Boot does not create its default in-memory user or print `Using generated security password: …` at startup. The exclusion is on the annotation on purpose: a property-based `spring.autoconfigure.exclude` that names a class which no longer exists is ignored silently (this happened on the Spring Boot 4 upgrade — the two Boot-3 class names sat in `application.properties` doing nothing until 2026-09-21), whereas a wrong class on the annotation fails to compile.
- `application.properties` therefore carries **no** `spring.security.*` or `spring.autoconfigure.exclude` keys. Do not add them back — the earlier `spring.security.user.name/password=a` pair only existed to silence the generated-password message.

No login is required to access any endpoint. Do not deploy with this configuration in a production environment. Enabling real authentication is a separate, explicitly-approved task (see `docs/development/THESIS_ROADMAP.md`, Section 4).

---

## 6. Build the Application

Navigate to the Maven project root and compile:

```bash
cd BusManagement
./mvnw clean compile
```

On Windows:
```powershell
.\mvnw.cmd clean compile
```

---

## 7. Run the Application

Default (persistent — keeps existing data):

```bash
./mvnw spring-boot:run
```

On Windows:
```powershell
.\mvnw.cmd spring-boot:run
```

Demo (**wipes the database** and reseeds sample data — see Section 4.2):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

On Windows:
```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

The application starts on **port 8080** (Spring Boot default). No port override is configured in `application.properties`.

Once started, access the admin dashboard at:

```
http://localhost:8080/admin/trips
```

---

## 8. Key Application Properties

All configuration is in `src/main/resources/application.properties`.

| Property                              | Value / Default            | Notes                                       |
|---------------------------------------|----------------------------|---------------------------------------------|
| `spring.application.name`            | `BusManagement`            |                                             |
| `spring.jpa.hibernate.ddl-auto`      | `update` (default)         | Preserves data. Overridden to `create-drop` by the `demo` profile — see Section 4 |
| `spring.datasource.url`              | `jdbc:mysql://...`         | Uses `MYSQL_HOST` env var (default: localhost) |
| `spring.datasource.username`         | `root` (default)           | Change before running                       |
| `spring.datasource.password`         | *(hardcoded placeholder)*  | Change before running                       |
| `spring.jpa.show-sql`                | `false`                    | Set to `true` to debug SQL queries          |
| *(no `spring.security.*` keys)*      | permit-all by code         | See Section 5 — `SecurityConfig` + the annotation exclude |

---

## 9. Project Structure Overview

```
BusManagement/                      ← Maven project root
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/giang/com/BusManagement/
│   │   │   ├── BusManagementApplication.java   ← Entry point (@EnableScheduling, excludes the default in-memory user)
│   │   │   ├── config/                         ← Security, web config
│   │   │   ├── controller/
│   │   │   │   ├── helloController.java
│   │   │   │   └── admin/                      ← All admin controllers
│   │   │   │       ├── AdminBusController.java
│   │   │   │       ├── AdminController.java
│   │   │   │       ├── AdminStationController.java
│   │   │   │       ├── AdminTripController.java         ← Approval workflow
│   │   │   │       ├── AdminTripManagementController.java ← CRUD + cancel/delete
│   │   │   │       └── TripRestController.java          ← REST API
│   │   │   ├── domain/                         ← JPA entities + enums
│   │   │   ├── repository/                     ← Spring Data JPA interfaces
│   │   │   └── service/                        ← Business logic
│   │   │       ├── TripService.java            ← Core: FSM, scheduler, validation
│   │   │       ├── BusService.java
│   │   │       ├── StationService.java
│   │   │       ├── AdminService.java
│   │   │       └── AutoAssignResult.java       ← Result wrapper for auto-assign
│   │   └── resources/
│   │       ├── application.properties           ← Default: persistent (ddl-auto=update)
│   │       ├── application-demo.properties      ← Profile "demo": wipe + reseed
│   │       └── templates/                      ← Thymeleaf HTML templates
│   │           └── admin/
│   └── test/
│       └── resources/
│           └── application.properties           ← Isolated test DB (busmanagement_test)
└── docs/                                       ← Project documentation
    ├── ai/
    ├── development/
    └── archive/
```

---

## 10. Common Issues & Troubleshooting

### Application fails to start — database connection error
**Symptom:** `Communications link failure` or `Access denied`  
**Cause:** MySQL is not running, or credentials in `application.properties` are incorrect.  
**Fix:** Ensure MySQL is running on port 3306 and update the username/password in `application.properties`.

### Schema not created / `Table 'trips' doesn't exist`
**Symptom:** Hibernate queries fail immediately after startup.  
**Cause:** Hibernate creates tables but not the database itself — the `busmanagement` database must already exist.  
**Fix:** Manually create the `busmanagement` database in MySQL before starting the app (see Section 3.1).

> A `Table 'busmanagement.trips' doesn't exist` line may also appear **harmlessly** in the startup log under the `demo` profile: `create-drop` issues DROP statements before the tables exist on a fresh database. If the application continues to start normally, this is expected noise, not a failure.

### App starts with no data / dashboard is empty
**Symptom:** Every list page is empty on a fresh clone.  
**Cause:** The default profile is persistent and does **not** seed anything — an empty database stays empty.  
**Fix:** Run once with the demo profile to populate sample data: `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` (see Section 4.2).

### Data disappeared after a restart
**Symptom:** Previously created trips/buses are gone.  
**Cause:** The application was started with `-Dspring-boot.run.profiles=demo`, which drops the schema and reseeds fixed sample data.  
**Fix:** Use the default profile (no flag) whenever data must be preserved.

### `LazyInitializationException` in scheduler
**Symptom:** Error log during `scanAndSuggestExtraTrips()` execution.  
**Cause:** A lazy-loaded association was accessed outside a Hibernate session.  
**Fix:** This is handled by `@Transactional` on `scanAndSuggestExtraTrips()` and by using `findByStatusWithRoute()` which eager-loads the `route`. Do not remove the `@Transactional` annotation from this method.

### Port 8080 already in use
**Symptom:** `Web server failed to start. Port 8080 was already in use.`  
**Fix:** Stop the other process on port 8080, or add the following to `application.properties`:
```properties
server.port=8081
```

### `@Scheduled` jobs not running
**Symptom:** No AI scheduling output in the logs.  
**Cause:** `@EnableScheduling` is missing from the application class.  
**Fix:** Verify that `BusManagementApplication.java` has the `@EnableScheduling` annotation. This is present in the current implementation.

### `spring.jpa.show-sql` debugging
To see all Hibernate-generated SQL statements in the console, set:
```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

---

## 11. Docker

> **Docker is not implemented in this project.** No `Dockerfile`, `docker-compose.yml`, or containerization configuration exists in the current codebase.
