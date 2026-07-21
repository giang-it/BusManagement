# Developer Setup Guide

This guide covers all steps to clone, configure, and run the Bus Management System locally.

---

## 1. Prerequisites

| Requirement      | Version         | Notes                                          |
|------------------|-----------------|------------------------------------------------|
| Java (JDK)       | 17 or higher    | Required. App uses Java 17 features.           |
| Apache Maven     | 4.0.2           | A Maven Wrapper (`mvnw`) is included.          |
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

### 4.3 Backfill profile — historical dataset (Phase 5)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=backfill
```

Runs `HistoricalDataBackfill`, which generates **12 weeks of simulated historical trips** (5 routes × 3 departure slots × 84 days = 1,260 rows, `COMPLETED`/`CANCELLED`) ending yesterday. This is the dataset Demand Forecast (Phase 6) consumes.

- **Additive, never destructive.** This profile does **not** activate `demo`, so `DataInitializer` does not run and nothing is wiped. The default `ddl-auto=update` still applies.
- **Safe to re-run.** Each row is a deterministic `(route, departureTime)` slot and is skipped if it already exists, so a second run inserts 0 rows.
- **Deterministic — but only relative to the run date and the existing database.** The occupancy figures come from a fixed seed, so the *demand pattern* is reproducible. The departure dates are not: the window is computed as "the 84 days ending yesterday", so running the backfill on a different day shifts every date. Bus and driver assignment likewise depends on the rows already in that database. Re-running on the same database on the same day is a no-op; regenerating an identical dataset elsewhere is **not** guaranteed.
- **Updates bus odometers, without disturbing maintenance state.** Completed trips add their route distance to `odometer` **and the same amount to `lastMaintenanceOdometer`**. Because `kmSinceLastMaintenance` is the difference of the two, it is mathematically unchanged — maintenance alerts, `needsMaintenance()`, and bus selection all behave exactly as before, while lifetime mileage becomes a real wear signal for Phase 7. Cancelled trips add nothing, matching the FSM. Re-running adds 0 km.
- **Never combine with `demo`** — that profile uses `create-drop` and would wipe the schema before the backfill runs.

The generated rows carry the **script's run date** in `created_at` (Hibernate `@CreationTimestamp` cannot be backdated, and the column is `updatable = false`). This is expected: the dataset's time axis is `departure_time`. Do not "fix" it with native SQL.

> **The data is simulated.** After running this, dashboard totals (trip counts, tickets sold, revenue) are dominated by generated figures. Say so explicitly in any report or demo.

### 4.4 Test configuration

`src/test/resources/application.properties` points the test suite at a **separate database** (`busmanagement_test`, auto-created via `createDatabaseIfNotExist=true`) using `create-drop`. This keeps `mvnw test` from touching the real `busmanagement` database.

Note that this file **fully shadows** `src/main/resources/application.properties` during tests (the test classpath takes precedence; the two files are not merged), so every required property is redeclared there.

---

## 5. Security Configuration

Spring Security is **fully disabled** in the current configuration:

```properties
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,\
  org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
```

No login is required to access any endpoint. Do not deploy with this configuration in a production environment.

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
| `spring.autoconfigure.exclude`       | Security disabled          | See Section 5                               |

---

## 9. Project Structure Overview

```
BusManagement/                      ← Maven project root
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/giang/com/BusManagement/
│   │   │   ├── BusManagementApplication.java   ← Entry point (@EnableScheduling)
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
