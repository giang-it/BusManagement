# Bus Management System

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-blue?logo=mysql)](https://www.mysql.com/)
[![Maven](https://img.shields.io/badge/Maven-3.x-red?logo=apachemaven)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Academic%20Project-lightgrey)](#license)

Bus Management System is a Spring Boot application for managing bus fleets, routes, drivers, and trip operations. It features an AI-assisted scheduling engine that recommends additional trips and validates operational constraints such as driver availability and vehicle maintenance.

---

## 🚀 Key Features

### 1. Fleet & Vehicle Management
*   **Bus Configuration:** Track bus profiles, license plates, odometer mileage, and status monitoring.
*   **Capacity Definition:** Define seat capacities for different vehicle categories.
*   **Maintenance Warnings:** Monitor vehicle conditions and prevent the dispatch of buses requiring service.

### 2. Route & Station Management
*   **Station Registry:** Define operational stops and terminals.
*   **Route Setup:** Configure routes, calculate total distances, and organize ordered station sequences.

### 3. Trip Scheduling & Lifecycle
*   **Manual Dispatch:** Schedule trips directly and validate driver and bus conflicts.
*   **Resource Finder:** Dynamically scan and display available buses and drivers for selected timeframes.
*   **FSM Transitions:** Drive trips through systematic operational states (e.g., Active, Departed, Completed).

### 4. AI Scheduling Recommendation
*   **AI Recommendation Engine:** Automatically detects high-demand trips and recommends additional trips with automatic resource allocation.
*   **Constraint Checking:** Auto-assigns ready vehicles and qualified staff while auditing rest limits and driving hours.

---

## 🛠️ Tech Stack

*   **Backend:** Java 17, Spring Boot 3.x, Spring Security, Spring Scheduler
*   **Database & ORM:** MySQL, Spring Data JPA, Hibernate
*   **Frontend Views:** Thymeleaf, CSS

---

## 📐 Architecture

The application is structured following a standard layered architecture:

```
[Browser / Frontend] 
       │ (HTTP Requests / Thymeleaf / AJAX)
       ▼
[Controller Layer] (REST & MVC Controllers)
       │ (Interactions)
       ▼
[Service Layer] (Core Business Logic & Validations)
       │ (JPA Queries)
       ▼
[Repository Layer] (Spring Data JPA Repositories)
       │ (SQL Commands)
       ▼
[MySQL Database]
```

*   **Controller Layer:** Manages web routes, request routing, and handles REST requests.
*   **Service Layer:** Executes business rules, FSM checks, and handles resource constraint calculations.
*   **Repository Layer:** Handles database communication and custom persistence queries.

---

## 🗄️ Database

### Main Entities
*   **Users:** System accounts.
*   **Drivers:** Staff profiles and license tracking.
*   **Buses:** Vehicle profiles.
*   **Routes:** Defined paths between stations.
*   **Stations:** Dispatch terminals and stops.
*   **Trips:** Scheduled dispatches associating a route, bus, and crew.

---

## 📂 Project Structure

```
BusManagement/
├── src/
│   ├── main/
│   │   ├── java/giang/com/BusManagement/
│   │   │   ├── config/          # Configurations (Security, Web)
│   │   │   ├── controller/      # REST APIs & MVC View Controllers
│   │   │   ├── domain/          # Database JPA Entity models
│   │   │   ├── repository/      # Spring Data JPA Repositories
│   │   │   └── service/         # Business logic & FSM validators
│   │   └── resources/
│   │       ├── templates/       # Thymeleaf HTML pages
│   │       └── application.properties # App configurations
│   └── test/                    # Unit & integration tests
├── docs/                        # Project documentation
│   ├── ai/                      # AI assistant context guidelines
│   ├── development/             # Specifications and setups
│   └── archive/                 # Historical specifications & reports
└── pom.xml                      # Maven configuration
```

---

## 💻 Getting Started

### Prerequisites
*   Java 17 JDK or higher installed
*   MySQL database instance running locally
*   Maven installed (or use the included Maven wrapper)

### Setup & Launch
1.  **Configure Database Credentials:**
    Update your MySQL username, password, and database URI inside `src/main/resources/application.properties`:
    ```properties
    spring.datasource.url=jdbc:mysql://localhost:3306/bus_management?createDatabaseIfNotExist=true
    spring.datasource.username=...
    spring.datasource.password=....
    ```

2.  **Run the Application:**
    Navigate to the project root directory containing the Maven wrapper and run:
    ```bash
    cd BusManagement
    ./mvnw spring-boot:run
    ```
    *(On Windows Command Prompt/PowerShell, use: `.\mvnw.cmd spring-boot:run`)*

3.  **Access Dashboard:**
    Open your browser and navigate to `http://localhost:8080/admin/trips`. The project includes automatic database initialization for development.

---

## 📸 Demo & Screenshots

*Demo screenshots and UI previews coming soon.*

---

## 📊 Current Status

### Implemented Modules
*   ✓ Fleet Management
*   ✓ Route Management
*   ✓ Trip Scheduling
*   ✓ AI Recommendation

### Future Roadmap
*   Planned - Authentication & Authorization
*   Planned - Customer Ticketing & Booking
*   Planned - Notification Services
*   Planned - Online Payment Integrations

---

## 👤 Author
Developed by **Giang**  
Pre-thesis Project — International University  
