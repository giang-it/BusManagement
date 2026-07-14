# Developer Setup Guide

This guide covers all steps to clone, configure, and run the Bus Management System locally.

---

## 1. Prerequisites

| Requirement      | Version         | Notes                                          |
|------------------|-----------------|------------------------------------------------|
| Java (JDK)       | 17 or higher    | Required. App uses Java 17 features.           |
| Apache Maven     | 3.x             | A Maven Wrapper (`mvnw`) is included.          |
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

The application uses `spring.jpa.hibernate.ddl-auto=create-drop`.

> **Warning:** On every startup, Hibernate drops and recreates the entire schema. All transactional data is lost between restarts. This is intentional for development.

A `DataInitializer` bean executes after schema creation to seed:
- Test users and driver profiles
- Bus types and vehicles
- Stations and routes
- Pre-scheduled active trips for demonstration

No manual SQL import is needed.

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

```bash
./mvnw spring-boot:run
```

On Windows:
```powershell
.\mvnw.cmd spring-boot:run
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
| `spring.jpa.hibernate.ddl-auto`      | `create-drop`              | Drops and recreates schema on every start   |
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
│   │       ├── application.properties
│   │       └── templates/                      ← Thymeleaf HTML templates
│   │           └── admin/
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
**Cause:** `ddl-auto=create-drop` requires the database to exist. Hibernate creates tables but not the schema/database itself.  
**Fix:** Manually create the `busmanagement` database in MySQL before starting the app (see Section 3.1).

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
