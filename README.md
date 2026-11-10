# Bus Management System

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.8-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-blue?logo=mysql)](https://www.mysql.com/)
[![Maven](https://img.shields.io/badge/Maven-3.9%20(wrapper)-red?logo=apachemaven)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Academic%20Project-lightgrey)](#license)

Bus Management System is a Spring Boot application for running a coach company's operations — fleet, routes, drivers, trips and incidents — with a decision-support layer on top: a demand forecast, reinforcement recommendations with cost and profit, and a what-if simulation. Everything is used by an administrator; there is no customer or driver portal yet.

The "AI" parts are **rule-based automation and statistics**, not machine learning: the extra-trip scanner and auto-assignment apply business rules, and the demand forecast is a calendar Poisson regression written in plain Java. The historical data the forecast learns from is **simulated** (see `BusManagement/docs/architecture/setup_guide.md` §4.3).

---

## 🚀 Key Features

### Operations
*   **Fleet & vehicle management:** buses, bus types and seat capacity, odometer and maintenance thresholds, status (`READY` / `TRAVELING` / `REPAIRING`).
*   **Routes & stations:** ordered stop lists, distance, duration, a preferred bus type, and an optional holiday passenger flow used by the forecast.
*   **Drivers:** one form creates the user account and the driver profile; licence expiry and an 8-hour daily driving limit are enforced.
*   **Trip scheduling & lifecycle:** manual trips with conflict checks, a trip state machine (`PENDING_APPROVAL → ACTIVE → DEPARTED → COMPLETED`, or `CANCELLED`) that keeps bus status in sync, soft delete.
*   **Extra-trip scanner:** a background job flags trips selling above 90 % of their seats and prepares an extra trip with a bus and crew for the administrator to approve.
*   **Dispatch board, incident log, dashboard & analytics.**

### Decision support
*   **Demand forecast** (`/admin/analytics/forecast`): passengers per route × departure hour for the next 7 days (35-day preview), holiday effects learned from data, and a self-check against a naive baseline.
*   **Reinforcement recommendation** (`/admin/analytics/recommendation`): one card per extra trip a busy slot needs, with a bus and driver reserved across cards, the expected passengers it would carry, revenue, cost and profit.
*   **What-if simulation** (`/admin/analytics/what-if`): add or remove buses and drivers, scale demand, override cost rates — and see how many needed extra trips the fleet can cover.
*   **Driver recommendation, vehicle replacement ranking, cost parameters.**

---

## 🛠️ Tech Stack

*   **Backend:** Java 17, Spring Boot 4.0.8 (Spring MVC, Spring Data JPA / Hibernate, Spring Scheduler, Spring Security on the classpath but permit-all by code), Lombok
*   **Database:** MySQL 8
*   **Frontend views:** Thymeleaf, Bootstrap 5, Chart.js
*   **Build:** Maven wrapper (`mvnw`, Maven 3.9.12)

---

## 📐 Architecture

The application follows a layered architecture:

```
[Browser]  — Thymeleaf pages, a little JavaScript / AJAX
     │ HTTP
     ▼
[Controller layer]  (MVC controllers + one REST controller)
     │
     ▼
[Service layer]     (business rules, trip FSM, validation, forecast, recommendation)
     │
     ▼
[Repository layer]  (Spring Data JPA)
     │
     ▼
[MySQL]
```

---

## 🗄️ Database

Tables: `users`, `drivers`, `bus_types`, `buses`, `stations`, `routes`, `route_stations`, `trips`, `trip_co_drivers`, `incidents`, `cost_parameters`. Full reference: `BusManagement/docs/architecture/database_schema.md`.

---

## 📂 Project Structure

```
BusManagement/
├── src/
│   ├── main/
│   │   ├── java/giang/com/BusManagement/
│   │   │   ├── config/          # Security, demo seed (DataInitializer), history generator (HistoricalDataBackfill)
│   │   │   ├── controller/      # MVC controllers (admin/) and the REST controller
│   │   │   ├── domain/          # JPA entities, enums, the holiday calendar
│   │   │   ├── dto/             # View models for the dashboard and decision-support pages
│   │   │   ├── repository/      # Spring Data JPA repositories
│   │   │   └── service/         # Business logic, trip FSM, forecast model, recommendation, what-if
│   │   └── resources/
│   │       ├── templates/       # Thymeleaf pages
│   │       └── application*.properties
│   └── test/                    # JUnit tests (separate test database) and the forecast reference fixtures
├── docs/
│   ├── ai/                      # Prompts and rules for AI assistants
│   ├── architecture/            # Setup guide, database schema, trip lifecycle
│   ├── development/             # Roadmap, functional spec, forecast decisions and research
│   ├── study/                   # Study lessons for the thesis defence
│   ├── testing/                 # Test cases
│   └── todo/                    # Defect register
└── pom.xml
```

---

## 💻 Getting Started

Full instructions: `BusManagement/docs/architecture/setup_guide.md`.

1.  **Database:** run MySQL 8 and set the credentials in `BusManagement/src/main/resources/application.properties` (default URL `jdbc:mysql://localhost:3306/busmanagement`; the host can be overridden with `MYSQL_HOST`).
2.  **Run:**
    ```bash
    cd BusManagement
    ./mvnw spring-boot:run                                          # default: keeps the data
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo          # WIPES the database and seeds sample data
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=backfill      # adds the simulated trip history the forecast needs
    ```
    *(On Windows, use `.\mvnw.cmd`.)* A fresh database is empty: run `demo` once, then `backfill`. Never run `demo` on a database whose history you want to keep.
3.  **Open:** `http://localhost:8080/admin/dashboard`.
4.  **Tests:** `./mvnw test` — uses its own database `busmanagement_test`, never the real one.

---

## 📊 Current Status

*   **Done (roadmap Phases 0–8):** operations modules, business-rule validation, incident management, driver recommendation, simulated history, demand forecast, reinforcement recommendation with cost and profit, vehicle replacement ranking, what-if simulation. The demand forecast was redesigned in October 2026 (slot demand, calendar Poisson regression with EM) — see `BusManagement/docs/development/forecast_decisions.md`.
*   **Not started:** Phase 9, a minimal customer portal (search, seat availability, simple booking).
*   **Out of scope by decision:** real login and roles, online payment, notifications, a driver portal. See `BusManagement/docs/development/THESIS_ROADMAP.md` §4.

---

## 👤 Author
Developed by **Giang**  
Pre-thesis Project — International University  
