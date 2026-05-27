# PROJECT CONTEXT & TECH STACK

## 1. Tổng quan dự án
* **Tên dự án:** Bus Management System (Hệ thống Quản lý và Điều hành Xe khách Thông minh).
* **Đối tượng sử dụng:** Admin (Quản trị viên), Driver/Staff (Tài xế/Phụ xe), Client/User (Khách hàng).

## 2. Công nghệ sử dụng (Tech Stack)
* **Backend:** Java 17+, Spring Boot 3.x
* **Security:** Spring Security + JWT Tokens
* **Database:** MySQL
* **Concurrency Handling:** JPA/Hibernate Locking (Optimistic/Pessimistic) hoặc Redis Distributed Lock.
* **Automation:** Spring Scheduler (`@Scheduled`) cho các tác vụ Cron Job chạy ngầm.

## 3. Quy chuẩn cấu trúc thư mục (Package Structure)
Mọi code Java backend phải được tổ chức nghiêm ngặt theo mô hình Layered Architecture tiêu chuẩn:
* `com.busmanagement.config`: Các file cấu hình hệ thống (Security, Async, Cors, v.v.)
* `com.busmanagement.entity`: Định nghĩa các thực thể Map với Database (Vehicle, Driver, Trip, Ticket, v.v.)
* `com.busmanagement.repository`: Lớp giao tiếp Database (Spring Data JPA)
* `com.busmanagement.service`: Lớp xử lý Logic nghiệp vụ (Business Logic)
* `com.busmanagement.controller`: Lớp tiếp nhận Request và trả về API Response (REST APIs)
* `com.busmanagement.dto`: Khai báo các đối tượng chuyển đổi dữ liệu (Request/Response DTOs)
* `com.busmanagement.exception`: Quản lý các Custom Exception và Global Exception Handler