# Tóm tắt Chức năng Project: BusManagement

> Tài liệu này tổng hợp **toàn bộ chức năng đã được lập trình thực tế** trong source code (không phải spec lý thuyết), dựa trên việc đọc trực tiếp từng file trong `repomix-output.xml`. Mục tiêu: làm input cho Codex review/kiểm tra logic.

---

## 0. Thông tin chung

- **Tech stack:** Java 17, Spring Boot 4.0.2, Spring Data JPA (Hibernate), Thymeleaf, MySQL, Lombok.
- **Kiến trúc:** Layered (Controller → Service → Repository → Domain/Entity), có một REST Controller (`TripRestController`) phục vụ AJAX.
- **Bảo mật:** `SecurityConfig` cho phép **mọi request không cần đăng nhập** (`anyRequest().permitAll()`, CSRF/form-login/http-basic đều bị tắt). Không có phân quyền ROLE_ADMIN/ROLE_DRIVER/ROLE_USER nào được enforce ở tầng Spring Security, dù entity `User`/`Role` có định nghĩa enum 3 vai trò.
- **Database init:** `spring.jpa.hibernate.ddl-auto=create-drop` + `DataInitializer` (`CommandLineRunner`) xóa toàn bộ dữ liệu cũ và seed lại dữ liệu test mỗi lần khởi động app — **không phù hợp production**.
- **Soft delete:** `Trip` dùng `@SQLDelete` + `@SQLRestriction("is_deleted = false")` — mọi `delete()` qua JPA chỉ là UPDATE `is_deleted=true`, và mọi SELECT tự động loại các trip đã xóa.

### Khoảng cách giữa spec ban đầu và code thực tế

`.agent_instructions/03_functional_spec.md` mô tả một hệ thống đầy đủ 4 nhóm tác nhân (Admin, Driver/Staff, Client/User, Hệ thống tự động) bao gồm: đặt vé online, thanh toán, QR check-in, JWT auth, thống kê doanh thu, đánh giá khách hàng, báo cáo sự cố, v.v. **Phần lớn các mục này CHƯA được lập trình.** Code hiện tại chỉ triển khai phần **Admin — Điều hành thông minh (Smart Scheduling)** và một phần nhỏ của **Quản lý danh mục**. Không có entity/controller nào cho Ticket, Payment, Seat, Review, Notification, JWT, hay giao diện Driver/Client.

---

## 1. Domain Model (Entities)

| Entity | Vai trò | Trường đáng chú ý |
|---|---|---|
| `User` | Tài khoản hệ thống | `username`, `password` (plaintext, không mã hóa), `role` (enum `Role`), quan hệ 1-1 với `Driver` |
| `Role` | Enum | `ROLE_ADMIN`, `ROLE_DRIVER`, `ROLE_USER` — định nghĩa nhưng không được Spring Security sử dụng để phân quyền |
| `Driver` | Hồ sơ tài xế, `@MapsId` dùng chung PK với `User` | `licenseNumber`, `licenseExpiryDate`, `experienceYears`, `totalDrivingHours24h` (giờ lái baseline, dữ liệu mock seed), `monthlyRestDays`, `isActive`; method `isLicenseValid()` |
| `Bus` | Xe khách | `licensePlate`, `busType` (N-1 `BusType`), `odometer`, `lastMaintenanceOdometer`, `maintenanceThreshold`, `status` (enum `BusStatus`); methods `getKmSinceLastMaintenance()`, `needsMaintenance()` |
| `BusStatus` | Enum | `READY`, `TRAVELING`, `REPAIRING` |
| `BusType` | Loại xe | `typeName` (unique), `capacity` |
| `Route` | Tuyến đường | `departurePoint`/`destinationPoint` (String, KHÔNG liên kết Station entity), `distanceKm`, `estimatedDuration`, `suitableBusType` (N-1 `BusType`), `routeStations` (1-N); method `getSuitableBusType()` chỉ trả field, không có AI logic dù docstring nói có |
| `Station` | Bến xe | `stationName`, `address`, `routeStations` (1-N) |
| `RouteStation` | Bảng nối Route-Station (Embeddable composite key `RouteStationId`) | `stopOrder` |
| `Trip` | Chuyến xe — entity trung tâm | `route`, `bus`, `driver` (tài xế chính), `assistant` (phụ xe, cũng là `Driver`), `coDrivers` (`List<Driver>`, ManyToMany — tài xế phụ cho chuyến dài), `departureTime`, `arrivalTimeExpected`, `totalSeats`, `ticketsSold`, `price`, `status` (enum `TripStatus`, default `PENDING_APPROVAL`), `originalTrip` (self-reference cho chuyến tăng cường), `isExtraTrip`, `saleOpenedAt`, `isDeleted` (soft-delete flag). Methods: `getOccupancyRate()`, `needsReinforcement()` (>90%), `getTripDurationHours()`, `getHoursOnSale()`, `getHoursUntilDeparture()` |
| `TripStatus` | Enum | `ACTIVE`, `PENDING_APPROVAL`, `DEPARTED`, `COMPLETED`, `CANCELLED` |

---

## 2. Finite State Machine (FSM) cho Trip.status

Định nghĩa whitelist trong `TripService.canTransition()`:

```
PENDING_APPROVAL → ACTIVE | CANCELLED
ACTIVE            → DEPARTED | CANCELLED
DEPARTED          → COMPLETED
COMPLETED, CANCELLED → (terminal, không chuyển đi đâu nữa)
(from == to luôn hợp lệ, coi như no-op)
```

- Mọi thay đổi status hợp lệ **phải** đi qua `TripService.updateTripStatus(tripId, newStatus)`. Hàm này: kiểm tra `canTransition()`, nếu chuyển sang `ACTIVE` thì gọi `changeStatusToActive()` (đóng dấu `saleOpenedAt` nếu chưa có), đồng bộ `BusStatus` (ACTIVE→DEPARTED: set bus = TRAVELING; →COMPLETED: set bus = READY), rồi save.
- Các luồng đã được audit và sửa để **không bypass FSM**: `cancelTrip()`, `rejectTrip()`, `updateTrip()` (tách 2 bước: update field thường qua `updateManualTrip()`, đổi status qua `updateTripStatus()`).
- `createManualTrip()` và `approveTrip()`/`confirmAutoAssignedTrip()` dùng `changeStatusToActive()` trực tiếp (không qua `updateTripStatus()`) vì đây là trường hợp tạo/duyệt — không có "from state" cần kiểm tra transition.

---

## 3. Module: Quản lý Xe (Bus)

**Controller:** `AdminBusController` (`/admin/buses`) — list/create/edit/delete qua `BusService`.
**Service:** `BusService`

- `findAllWithBusType()`, `findById()`, `findAllBusTypes()`.
- `saveBus()`: auto-default `maintenanceThreshold=5000.0`, `lastMaintenanceOdometer=0.0`, `odometer=0.0` nếu null; **chặn** chuyển status sang `REPAIRING` nếu xe đang có trip `ACTIVE`/`PENDING_APPROVAL`.
- `deleteBus()`: **chặn cứng** xóa nếu xe đã từng gắn với bất kỳ trip nào (`existsByBusId`) — gợi ý đổi sang `REPAIRING` thay vì xóa.

**Lưu ý kiến trúc:** `AdminController` (`/admin`) cũng có `GET/POST /buses/new` và `/buses/save` gọi `AdminService.createNewBus()` — đây là **route trùng lặp/song song** với `AdminBusController`, hai luồng tạo bus khác nhau cùng tồn tại (một qua `BusService` có validate, một qua `AdminService` không có validate gì).

---

## 4. Module: Quản lý Bến xe (Station)

**Controller:** `AdminStationController` (`/admin/stations`) — list/create/edit/delete qua `StationService`.
**Service:** `StationService`

- CRUD cơ bản.
- `deleteById()`: chặn xóa nếu station đang có `routeStations` (đang thuộc tuyến nào).
- **Ghi chú đã biết:** `DataInitializer` tạo Station nhưng (tùy version) **không seed `RouteStation`** nối Station với Route — bảng `route_stations` có thể trống, route vẫn dùng String tự do cho điểm đi/đến không liên kết Station thật.

---

## 5. Module: Quản lý Người dùng (User) — tối giản

**Controller:** `AdminController` (`/admin`)

- `GET /admin/dashboard`: thống kê `totalUsers`, `totalBuses`, `pendingTrips` (count theo `TripStatus.PENDING_APPROVAL`).
- `GET/POST /admin/users/new`, `/users/save`: tạo User mới qua `AdminService.createNewUser()` — **không mã hóa password** (có comment TODO nhắc nhưng chưa làm), không có validate trùng username.

Không có chức năng sửa/xóa/list user nào khác trong code.

---

## 6. Module: Quản lý Trip (CRUD thủ công) — `AdminTripManagementController` (`/admin/trip-management`)

### 6.1. Danh sách & lọc
- `GET /trips`: liệt kê tất cả trip (mọi status) qua `tripRepository.findAllWithDetails()` (JOIN FETCH đầy đủ route/bus/busType/driver/assistant/coDrivers, tránh N+1).
- `GET /trips/filter?status=...`: lọc theo `TripStatus`.

### 6.2. Tạo chuyến thủ công (Manual Trip Creation)
- `GET /trips/create`: hiển thị form, đổ toàn bộ `routes`, `buses`, `drivers` từ Repository (không lọc theo tình trạng rảnh/bận).
- `POST /trips/create`:
  1. Resolve `route`/`bus`/`driver` theo ID (404-style RuntimeException nếu không tìm thấy).
  2. Gán `assistant` nếu có; clear & gán `coDrivers` nếu có.
  3. **Hard-code** `trip.setStatus(ACTIVE)` và `trip.setExtraTrip(false)` ngay tại Controller.
  4. Gọi `tripService.createManualTrip(trip)` để validate + lưu.
  5. Bắt mọi `Exception`, hiển thị flash `error`, redirect lại form tạo (form rỗng, mất dữ liệu đã nhập).
- **`TripService.createManualTrip(trip)`:**
  - Validate `arrivalTimeExpected` phải sau `departureTime`.
  - `validateBusForTrip(bus, trip, null)` — xem mục 6.5.
  - `validateStaffForTrip(trip, null)` — xem mục 6.5.
  - `changeStatusToActive(trip)` (set ACTIVE + đóng dấu `saleOpenedAt`).
  - Save, trả về `warning` (String|null) nếu có cảnh báo bảo trì.
  - **Vấn đề đã ghi nhận:** trip thủ công luôn ACTIVE ngay, bỏ qua hoàn toàn flow `PENDING_APPROVAL` mà chuyến AI-suggested phải trải qua → 2 luồng tạo trip không nhất quán về FSM entry point.

### 6.3. REST endpoint hỗ trợ form tạo trip (Wizard) — `TripRestController` (`/api/admin/trips`)
- `GET /available-resources?departure=...&arrival=...` (ISO LocalDateTime): trả JSON `{buses: [...], drivers: [...]}` gồm các xe/tài xế **thực sự rảnh** trong khung giờ, dùng cho JS phía form tạo trip lọc động (khắc phục một phần vấn đề "hiển thị toàn bộ DB" từng được ghi nhận ở review trước). Validate `arrival` phải sau `departure`.
- DTO trả về cho bus: `id, licensePlate, typeName, capacity, brand`. DTO cho driver: `userId, fullName, licenseNumber, experienceYears`.
- Nội bộ gọi `tripService.getAvailableBusesForTimeRange()` và `getAvailableDriversForTimeRange()` (xem 6.6).

### 6.4. Sửa chuyến (`updateTrip`)
- `GET /trips/edit/{id}`: load trip + dropdown đầy đủ route/bus/driver, đồng thời gửi `driversForJs` (rút gọn) và `savedCoDriverIds` cho JS hiển thị sẵn lựa chọn cũ.
- `POST /trips/update`: **tách rõ 2 bước** để giữ FSM:
  1. Cập nhật field thường (route, bus, driver, assistant, coDrivers, thời gian, giá, số ghế) qua `tripService.updateManualTrip(existingTrip)` — **không** set status ở bước này.
  2. Nếu status mới khác status cũ → gọi `tripService.updateTripStatus(id, newStatus)` riêng để FSM `canTransition()` chạy và đồng bộ `BusStatus`.
  - Bắt riêng `IllegalStateException` (FSM reject) và `Exception` chung, redirect lại form edit với flash error.
- **`TripService.updateManualTrip()`:** validate `arrival > departure`, validate `totalSeats >= ticketsSold`, gọi `validateBusForTrip`/`validateStaffForTrip` (loại trừ chính trip này khỏi check trùng lịch), save — **không đổi status**.

### 6.5. Hủy chuyến (`cancelTrip`)
- `POST /trips/cancel/{id}`: gọi `tripService.updateTripStatus(id, CANCELLED)` — đã đi qua FSM đúng cách (đã fix khỏi bypass cũ). Bắt `IllegalStateException` riêng cho lỗi transition không hợp lệ (VD COMPLETED→CANCELLED).

### 6.6. Xóa chuyến (`deleteTrip`) — Soft delete có điều kiện
- `POST /trips/delete/{id}` → `tripService.deleteTrip(id)`:
  - `DEPARTED` → **chặn cứng**, không cho xóa.
  - `COMPLETED` → **chặn cứng**, giữ lịch sử báo cáo tài chính.
  - `ACTIVE` + đã bán vé (`ticketsSold > 0`) → **chặn**, yêu cầu dùng "Hủy chuyến" thay vì xóa.
  - `ACTIVE` chưa bán vé nào → cho xóa.
  - `PENDING_APPROVAL`, `CANCELLED` → cho xóa không điều kiện.
  - Khi đủ điều kiện: `tripRepository.delete(trip)` → trigger `@SQLDelete` → `UPDATE trips SET is_deleted=true`.
  - Bắt riêng 3 loại lỗi ở Controller: `IllegalStateException` (vi phạm rule), `EntityNotFoundException` (không tìm thấy), `Exception` (lỗi hệ thống khác).

### 6.7. Validate dùng chung cho Create/Update/Approve (`validateBusForTrip`, `validateStaffForTrip`)

**`validateBusForTrip(bus, trip, excludeTripId)`:**
- Chặn nếu `bus.status == REPAIRING`.
- Chặn nếu `bus.status == TRAVELING`.
- Tính cửa sổ bận `[departure - BUS_PREP_BUFFER_HOURS(1h), arrival + 1h]`, gọi `isBusBusy()` (kiểm tra overlap với các trip khác có status `ACTIVE/DEPARTED/PENDING_APPROVAL`, exclude chính trip này nếu update) → chặn nếu bận.
- Nếu xe sắp/đã vượt `maintenanceThreshold` sau chuyến này → **không chặn**, chỉ trả về `warning` string.

**`validateStaffForTrip(trip, excludeTripId)`:**
1. Tính `durationHours`, suy ra `requiredDrivers = ceil(durationHours / 8.0)` (tối thiểu 1).
2. Bắt buộc phải có `driver` (tài xế chính) — nếu null → throw.
3. Tổng số tài xế đã gán (`driver` + `coDrivers`) phải ≥ `requiredDrivers`, nếu không → throw.
4. Nếu `durationHours > 8.0` và `assistant == null` → throw ("chuyến >8h bắt buộc phải có phụ xe").
5. Check trùng lặp nhân sự: coDriver không được trùng driver chính, không trùng nhau, assistant không được trùng driver chính hoặc trùng coDriver nào.
6. Với mỗi người (driver chính, từng coDriver, assistant): check `isLicenseValid()` (driver/coDriver bắt buộc; assistant không check riêng dòng này), check không bận trong cửa sổ `[departure - 30p, arrival + 30p]` qua `isDriverBusyInWindow()` (gồm cả vai trò driver chính, coDriver, assistant ở các trip khác có status `PENDING_APPROVAL/ACTIVE/DEPARTED`).
7. Với driver chính và mỗi coDriver: tính `effectiveHours = min(durationHours / assignedDriversCount, 8.0)`, cộng với giờ đã lái trong ngày (`getDrivingHoursForDate`) — nếu tổng > 8.0 → throw.
   - **Lưu ý nghiệp vụ đã biết:** assistant (phụ xe) **không** bị áp ràng buộc giờ lái 8h/ngày trong `validateStaffForTrip` (đúng vì phụ xe không trực tiếp lái) nhưng **không nhất quán** với `findBestAvailableDriver()` (AI auto-assign) — hàm AI áp dụng cùng filter giờ-lái cho cả vai trò driver và assistant, có thể loại nhầm một tài xế đủ điều kiện làm phụ xe.

---

## 7. Module: Chuyến tăng cường tự động (Extra Trip Recommendation System) — trong `TripService`

### 7.1. Scheduled Job
- `scanAndSuggestExtraTrips()` — `@Scheduled(fixedRate = 10_000)` (chạy mỗi 10 giây), `@Transactional` (bắt buộc để giữ session Hibernate mở qua self-invocation `createExtraTrip()`).
- Lấy tất cả trip `ACTIVE` (kèm route, qua `findByStatusWithRoute`), với mỗi trip: nếu `isHotTrip(trip)` và `!hasAlreadySuggested(trip)` → `createExtraTrip(trip)`.

### 7.2. `isHotTrip(trip)` — 3 cổng thời gian (time-based gates) cộng với cổng occupancy gốc
1. **Occupancy gate (gốc):** `occupancyRate > 0.9` (90%), nếu không → false.
2. **Gate "đã khởi hành":** nếu `departureTime` null hoặc đã qua hiện tại → false (trip đã chạy, không tăng cường nữa).
3. **Gate "đủ thời gian bán vé":** `hoursUntilDeparture >= MIN_HOURS_BEFORE_DEPARTURE (72h)`, nếu ít hơn → false (không kịp mở bán vé mới có ý nghĩa).
4. **Gate "sale velocity" (chống spike ảo)**: nếu `occupancyRate < INSTANT_HOT_THRESHOLD (0.95)` thì yêu cầu `hoursOnSale >= MIN_SALE_OPEN_HOURS (48h)` mới được coi là hot; nếu occupancy ≥ 95% thì **bỏ qua** điều kiện này (fast-fill bypass — full ngay tức thì vẫn được tăng cường dù mới mở bán).

### 7.3. `hasAlreadySuggested(originalTrip)`
- Chặn tạo trùng nếu original trip đã có extra trip ở status `PENDING_APPROVAL`, `ACTIVE`, `DEPARTED`, hoặc `COMPLETED`.
- `CANCELLED` **không** nằm trong blocking list → cho phép AI đề xuất lại nếu extra trip cũ bị hủy (xe hỏng, tài xế ốm, v.v.).

### 7.4. `createExtraTrip(trip)`
- Departure mới = departure gốc + 30 phút.
- Arrival mới = departure mới + `route.estimatedDuration` (phút; fallback 240 phút nếu null).
- Trip mới: copy `route`, `totalSeats`, `price` từ gốc; `status = PENDING_APPROVAL`; `isExtraTrip = true`; `originalTrip` = trip gốc.
- Gọi `autoAssignResources(extraTrip)` (mục 7.5) — nếu thành công gán bus/driver/assistant/coDrivers vào trip mới; nếu fail, log lý do, **vẫn save trip** (ở trạng thái `PENDING_APPROVAL` không có resource — chờ Admin xử lý thủ công qua `AdminTripController`).

### 7.5. `autoAssignResources(trip)` — Thuật toán AI trung tâm
1. Tính `tripDurationHours`.
2. `findBestAvailableBus()` — fail nếu null.
3. `requiredDrivers = ceil(tripDurationHours / 8.0)`, tối thiểu 1.
4. Tìm driver chính qua `findBestAvailableDriver()` — fail nếu null.
5. Lặp tìm thêm `requiredDrivers - 1` coDriver — fail nếu không đủ.
6. Tìm `assistant` (cùng hàm `findBestAvailableDriver`, loại trừ những người đã gán) — nếu null và `tripDurationHours > 8.0` → fail (phụ xe bắt buộc cho chuyến dài); nếu ≤8h thì assistant tùy chọn (null vẫn coi là success).
7. Trả `AutoAssignResult.success(bus, driver, assistant, coDrivers)` hoặc `.failure(reason)`.

### 7.6. `findBestAvailableBus(trip, departure, arrival)`
- Lấy candidate: nếu route có `suitableBusType` thì lọc theo đúng loại + `READY`; ngược lại lấy tất cả `READY`.
- Cửa sổ bận mở rộng `±BUS_PREP_BUFFER_HOURS (1h)`.
- Ưu tiên xe **chưa** bận và **sau chuyến này vẫn chưa vượt** `maintenanceThreshold`, chọn xe có `kmSinceLastMaintenance` nhỏ nhất (xe "mới" nhất).
- Fallback: nếu không có xe nào thỏa ngưỡng bảo trì, vẫn chọn xe ít km nhất trong số xe không bận, kèm log cảnh báo "SÁT/QUÁ NGƯỠNG BẢO TRÌ".

### 7.7. `findBestAvailableDriver(departure, arrival, tripDurationHours, totalDriversCount, excludeDrivers)`
- Cửa sổ bận: `±MIN_REST_BETWEEN_TRIPS_MINUTES (30 phút)`.
- `effectiveHours = min(tripDurationHours / totalDriversCount, 8.0)`.
- Filter: `isActive = true`, không thuộc `excludeDrivers`, `isLicenseValid()`, `getDrivingHoursForDate(d, departure) + effectiveHours <= 8.0`, không bận trong cửa sổ (cả vai trò driver chính và assistant ở chuyến khác).
- Ưu tiên 1: bằng lái còn hạn > 7 ngày tính từ ngày khởi hành, chọn người có giờ lái trong ngày thấp nhất (load balancing).
- Fallback: nếu không ai thỏa "còn hạn >7 ngày", chọn người giờ lái thấp nhất trong số còn lại (bằng lái sắp hết hạn nhưng vẫn hợp lệ), kèm log cảnh báo.
- **Áp dụng chung cho cả vai trò driver chính, coDriver, và assistant** — đây là nguồn của vấn đề "phụ xe bị áp sai ràng buộc giờ lái" đã ghi nhận ở mục 6.7.

### 7.8. `getDrivingHoursForDate(driver, date, excludeTripId)`
- Lấy tất cả trip của driver trong ngày (vai trò driver chính, coDriver, hoặc assistant) ở status `PENDING_APPROVAL/ACTIVE/DEPARTED`.
- Nếu driver đóng vai **assistant** ở 1 trip nào đó trong ngày → giờ lái từ trip đó tính = 0.0 (phụ xe không lái).
- Nếu đóng vai driver chính hoặc coDriver → tính `durationHours / totalDriversOfThatTrip` (chia đều), cap tối đa 8.0/trip, cộng dồn.
- Cộng thêm `driver.totalDrivingHours24h` (baseline mock-seed) **chỉ khi** `date` là **hôm nay** — đây là design có chủ đích, ghi rõ trong code: field này là dữ liệu giả lập cho demo, dự kiến sẽ được thay bằng API cập nhật real-time từ thiết bị GPS/IoT trong tương lai.

### 7.9. `isDriverBusyInWindow(driver, windowStart, windowEnd, excludeTripId)`
- True nếu driver bận **với vai trò driver chính** (kể cả coDriver, qua `existsOverlappingTripForDriver`) **hoặc** bận **với vai trò assistant** (`existsOverlappingTripForAssistant`) trong cửa sổ.

---

## 8. Module: Phê duyệt chuyến tăng cường (Admin Approval Flow) — `AdminTripController` (`/admin/trips`)

### 8.1. Danh sách chờ duyệt
- `GET /pending`: liệt kê trip `PENDING_APPROVAL` (qua `tripService.getPendingTrips()`), tính số lượng đã được AI auto-assign đầy đủ (`bus != null && driver != null`) vs cần xử lý thủ công.

### 8.2. Xem chi tiết / chọn mode duyệt
- `GET /approve/{id}`: load trip, xác định `isAutoAssigned` (bus & driver đều có). Nếu **chưa** auto-assigned → load `availableBuses`/`availableDrivers` (đã lọc rảnh, hợp lệ) để Admin chọn thủ công.

### 8.3. Xác nhận 1-click (AI đã gán đầy đủ)
- `POST /confirm` (`tripId`) → `tripService.confirmAutoAssignedTrip(tripId)`:
  - Throw nếu thiếu `bus` hoặc thiếu `driver`.
  - Re-validate toàn bộ (`validateBusForTrip`, `validateStaffForTrip`) — đảm bảo tình trạng vẫn hợp lệ tại thời điểm duyệt (có thể đã thay đổi từ lúc AI gán tới lúc Admin click).
  - `changeStatusToActive()` + save.

### 8.4. Phê duyệt thủ công (AI không tự gán được)
- `POST /approve` (`tripId, busId, driverId, assistantId?, coDriverIds?`) → `tripService.approveTrip(...)`:
  - Resolve bus/driver/assistant/coDrivers theo ID Admin chọn.
  - `validateBusForTrip` + `validateStaffForTrip`.
  - `changeStatusToActive()` + save.
  - Bắt riêng `IllegalArgumentException` (lỗi vi phạm ràng buộc nghiệp vụ, hiển thị rõ cho Admin) và `Exception` chung.

### 8.5. Từ chối chuyến tăng cường
- `POST /reject/{id}` → `tripService.rejectTrip(id)` → `updateTripStatus(id, CANCELLED)` (đi qua FSM đúng cách: `PENDING_APPROVAL → CANCELLED` hợp lệ).

---

## 9. Các hàm Query hỗ trợ Form (`TripService` — "QUERY METHODS")

| Method | Mục đích | Khác biệt |
|---|---|---|
| `getAvailableBusesForTrip(tripId)` | Lấy xe rảnh cho 1 trip **đã tồn tại** (dùng departure/arrival của chính trip đó) | Cần `tripId` có trước, loại trừ chính trip này khi check bận |
| `getAvailableDriversForTrip(tripId)` | Tương tự, cho driver | Cùng cơ chế loại trừ |
| `getAvailableBusesForTimeRange(departure, arrival)` | Lấy xe rảnh theo khung giờ tự do (chưa có trip) | Dùng cho Wizard tạo trip mới qua REST API, không lọc theo loại xe (chưa biết route) |
| `getAvailableDriversForTimeRange(departure, arrival)` | Tương tự cho driver | Dùng cho REST API `/api/admin/trips/available-resources` |

Cả 4 hàm đều áp dụng cùng bộ điều kiện cốt lõi: `isActive`, `isLicenseValid()`, giờ lái trong ngày + effective hours ≤ 8h, không bận trong cửa sổ nghỉ tối thiểu.

---

## 10. Repository Layer — Query đáng chú ý

- **`TripRepository`**: nhiều query JOIN FETCH tránh N+1 (`findAllWithDetails`, `findByStatusWithDetails`, `findByIdWithDetails`); các query kiểm tra trùng lịch theo từng vai trò riêng (`existsOverlappingTripForDriver`, `existsOverlappingTripForAssistant`, `existsOverlappingTripForBus`); `findTripsForDriverOnDate` dùng để tính giờ lái/ngày; `existsByOriginalTripAndStatusIn` cho `hasAlreadySuggested`; `existsByBusId`/`existsByBusIdAndStatusIn` cho ràng buộc xóa/sửa bus.
- **Dead code đã ghi nhận:** `countBusyTripsAnyRole` và `findAllTripsByDriverOnDate` là một cặp query khác (dùng `Long driverId` thay vì `Driver` entity) **được định nghĩa nhưng không được gọi ở đâu trong TripService** — trùng lặp logic với cặp `existsOverlappingTripForDriver`/`findTripsForDriverOnDate` đang dùng thật.
- **`BusRepository`**: `findByStatus`, `findByStatusAndBusType`, `findAllWithBusType` (JOIN FETCH busType).
- **`DriverRepository`**: `findAllWithUser` (JOIN FETCH user).
- **`RouteStationRepository`**: `findByRouteIdOrderByStopOrderAsc`.
- **`StationRepository`**: `findByStationNameContainingIgnoreCase`.
- **Bổ sung cho Dashboard & Analytics (đọc-only, xem mục 14):**
  - `BusRepository.countByStatus(BusStatus)`.
  - `DriverRepository.countByIsActive(Boolean)`, `findAverageExperienceYears()`, `countActiveDriversWithLicenseExpiringBetween(from, to)`.
  - `TripRepository.countAiSuggestionsByStatus()`, `findSeatsAndSoldByStatus(status)`, `sumTicketsSoldAndRevenueByStatuses(statuses)`, `aggregateByRoute(statuses)`, `findDistinctRouteIdsWithTrips()`.
  - **Lưu ý kỹ thuật đã gặp thực tế khi triển khai:** khai báo 1 query aggregate đa cột (`SUM`, không `GROUP BY`) trả về `Object[]` trực tiếp bị Spring Data hiểu nhầm thành "ép cả result list thành mảng" thay vì "1 dòng kết quả duy nhất" → `ClassCastException` lúc runtime. Lỗi này **không** bị compile hay context-load test bắt được, chỉ lộ ra khi gọi qua HTTP thật. Đã sửa `sumTicketsSoldAndRevenueByStatuses` sang `List<Object[]>` + lấy phần tử đầu ở tầng service — đây là pattern chuẩn, không mơ hồ của Spring Data JPA cho loại query này.

---

## 11. Giao diện (Thymeleaf Templates) hiện có

| Template | Chức năng |
|---|---|
| `admin/dashboard.html` | Trang tổng quan: tổng users, tổng buses, số trip pending |
| `admin/bus/bus-list.html`, `admin/bus/bus-form.html` (và bản cũ `admin/bus-form.html`) | CRUD xe |
| `admin/station/station-list.html`, `admin/station/station-form.html` | CRUD bến xe |
| `admin/user-form.html` | Tạo user |
| `admin/trip-list.html` | Danh sách + filter trip theo status |
| `admin/trip-create-form.html` | Form tạo trip thủ công (wizard, có gọi REST API `available-resources`) |
| `admin/trip-edit-form.html` | Form sửa trip, hiển thị sẵn coDrivers đã lưu |
| `admin/pending-trips.html` | Danh sách trip chờ duyệt, phân loại auto-assigned vs cần thủ công |
| `admin/approve-form.html` | Form duyệt trip (2 mode: auto-confirm 1-click hoặc chọn thủ công) |
| `admin/suggestions.html` | (tồn tại trong templates, liên quan đề xuất AI — chưa rõ controller route gọi tới) |
| `admin/dashboard-analytics.html` | Dashboard & Analytics (xem mục 14): dải KPI vận hành cố định + 6 tab Strategic Analytics (Fleet/Trips/Routes/Drivers/Occupancy/AI), 7 chart Chart.js |
| `hello.html` | Trang chủ mặc định (`/`) qua `helloController` |

---

## 12. Các điểm chưa nhất quán / cần lưu ý khi kiểm tra logic (tổng hợp từ code + comment tự ghi nhận trong repo)

1. **2 luồng tạo Bus song song:** `AdminBusController`→`BusService` (có validate) vs `AdminController`→`AdminService` (không validate gì) — cùng tồn tại, dễ gây nhầm lẫn nghiệp vụ.
2. **Tạo trip thủ công luôn ACTIVE ngay**, bỏ qua `PENDING_APPROVAL`, không nhất quán với flow AI-suggested trip.
3. **Ràng buộc giờ lái áp dụng sai cho vai trò Assistant** trong `findBestAvailableDriver()` (AI auto-assign) — phụ xe không lái nhưng vẫn bị tính giờ lái như tài xế khi đang được xét chọn.
4. **`Route.getSuitableBusType()`** docstring nói có "AI logic" theo khoảng cách nhưng thực tế chỉ trả field, luôn trả `null` nếu Admin chưa gán tay.
5. **`BusType` từng có field `suitableBusType` trùng lặp với `Route`** (đã có note "AFTER — đúng" trong code hiện tại cho thấy đã dọn, cần verify lại trong source thật).
6. **Route dùng String tự do cho điểm đi/đến**, không liên kết `Station` entity thật — dữ liệu seed 2 bên tên không khớp nhau.
7. **`countBusyTripsAnyRole`/`findAllTripsByDriverOnDate`** là dead code (định nghĩa nhưng không gọi), trùng chức năng với cặp query đang dùng thật.
8. **Password không được mã hóa** khi tạo User (`AdminService.createNewUser` có TODO comment nhưng chưa implement BCrypt).
9. **Spring Security tắt hoàn toàn** — không có phân quyền thực tế dù có enum `Role` với 3 cấp.
10. **`ddl-auto=create-drop` + xóa toàn bộ data mỗi lần start** — không an toàn nếu deploy thật, chỉ phù hợp demo/dev.
11. **Khoảng cách lớn giữa spec (`03_functional_spec.md`) và code thực tế** — Ticket/Payment/Seat/QR-checkin/Review/Notification/JWT/Driver-app/Client-app đều chưa được lập trình; chỉ phần Admin Smart Scheduling được triển khai đầy đủ.

---

## 13. Tóm tắt nhanh: "Đã làm" vs "Chưa làm" so với spec gốc

**Đã làm (đầy đủ, có business logic thật):**
- CRUD Bus, Station, User (tối giản), Trip.
- FSM whitelist cho Trip.status, đồng bộ BusStatus theo lifecycle.
- Validate ràng buộc: giờ lái 8h/ngày, nghỉ giữa chuyến 30 phút, bằng lái còn hạn, xe không bận/không bảo trì, bus prep buffer 1h, yêu cầu phụ xe cho chuyến >8h, yêu cầu nhiều tài xế cho chuyến >8h.
- AI auto-assign tài nguyên (bus + driver + coDrivers + assistant) cho chuyến tăng cường.
- Cơ chế phát hiện chuyến "hot" (occupancy >90%) với 3 cổng thời gian chống tạo trùng/tạo quá sớm/tạo theo spike ảo.
- Soft-delete Trip với rule theo từng status.
- REST API hỗ trợ form tạo trip lọc tài nguyên rảnh theo thời gian thực.
- Dashboard & Analytics (đọc-only, xem mục 14): Operational KPIs (Pending Approvals, Upcoming Trips, Active Trips, Maintenance Alerts, AI Suggestions, Available Resources) + Strategic Analytics theo 6 tab (Fleet/Trips/Routes/Drivers/Occupancy/AI), biểu đồ Chart.js. Không thêm entity/cột DB nào; không đổi business rule nào ngoài 1 method public wrapper tái sử dụng `TripService.getDrivingHoursForDate()`.

**Chưa làm (có trong spec nhưng không có code):**
- Đặt vé, chọn ghế, thanh toán online (Client/User module — toàn bộ).
- QR check-in, app/trang riêng cho Driver (lịch trình cá nhân, nhật ký lái xe, báo cáo sự cố).
- Thống kê doanh thu, báo cáo hiệu suất tài xế, đánh giá khách hàng.
- JWT, phân quyền thực tế theo Role.
- Notification (Email/SMS).
- Cron job reset giờ lái 00:00 hàng ngày (chỉ có job quét extra-trip mỗi 10 giây, không có job reset).
- Locking xử lý đặt ghế đồng thời (chưa có Ticket/Seat entity nên chưa cần).

---

## 14. Module: Dashboard & Analytics (đọc-only reporting) — `DashboardController` (`/admin/analytics`)

Bổ sung sau khi hệ thống AI Scheduling đã ổn định. Toàn bộ module này **chỉ đọc dữ liệu hiện có** — không thêm entity, không thêm cột DB, không thay đổi business rule nào trong `TripService`/FSM (ngoại trừ 1 method public wrapper thuần tái sử dụng, xem mục 14.4).

### 14.1. Cấu trúc
- **Controller:** `DashboardController` (`/admin/analytics`) — duy nhất 1 `GET`, gọi `dashboardService.getOverview()`, render `admin/dashboard-analytics.html`. Tách riêng khỏi `AdminController` (vốn chỉ là trang chủ/shortcut đơn giản), theo đúng quy ước 1 controller/1 mối quan tâm đã có trong package `controller.admin`.
- **Service:** `DashboardService` — `@Transactional(readOnly = true)`, constructor-inject `TripRepository`, `BusRepository`, `DriverRepository`, `RouteRepository`, và `TripService` (chỉ để tái sử dụng `getDrivingHoursForDate()`).
- **DTO package mới:** `giang.com.BusManagement.dto` (14 class) — `DashboardOverviewDto` là view-model gốc, gồm `OperationalKpisDto` (dải KPI cố định) và `StrategicAnalyticsDto` (nội dung 6 tab: `FleetStatsDto`, `TripStatsDto`, `RouteStatsDto`, `DriverStatsDto`, `OccupancyStatsDto`, `AiStatsDto`), cộng các DTO phụ trợ (`TripPreviewDto`, `BusAlertDto`, `RoutePerformanceDto`, `DriverWorkloadDto`, `ChartSeriesDto` dùng chung cho mọi chart).

### 14.2. Operational KPIs (luôn hiển thị, không nằm trong tab)
- **Pending Approvals** (tổng + số AI đã auto-assign vs cần thủ công) — tái sử dụng `TripService.getPendingTrips()`.
- **Upcoming Trips** (48h tới, status ACTIVE) — tái sử dụng `TripRepository.findByDepartureTimeBetweenAndStatus()` (đã tồn tại sẵn trong repo nhưng trước đó chưa nơi nào gọi tới).
- **Active Trips** — tái sử dụng `TripRepository.countByStatus()`.
- **Maintenance Alerts** (quá hạn / sắp đến hạn) — dùng lại `Bus.needsMaintenance()`/`Bus.isNearMaintenance(0)` nguyên vẹn, không tính lại ngưỡng bảo trì ở tầng khác.
- **AI Suggestions Pending** — lấy bucket `PENDING_APPROVAL` từ `countAiSuggestionsByStatus()`.
- **Available Resources** — số xe `READY` + số tài xế `isActive`.

### 14.3. Strategic Analytics (6 tab Bootstrap)
Fleet, Trips, Routes, Drivers, Occupancy, AI Recommendation — mỗi tab có card số liệu + bảng + 1 chart Chart.js (Bar hoặc Doughnut, load qua CDN), dữ liệu lấy từ các query repository mới thêm (mục 10) hoặc tổng hợp trong `DashboardService` (không tính toán business rule mới, chỉ nhóm/đếm số liệu đã có).

**Ngưỡng dùng lại nguyên vẹn từ `TripService`, không định nghĩa lại:**
- "Hot trip" = `occupancyRate > 0.90`, đúng ngưỡng `Trip.needsReinforcement()`.
- "Bằng lái sắp hết hạn" = trong vòng 7 ngày, đúng ngưỡng `findBestAvailableDriver()` đang dùng.

Bootstrap tabs dùng `data-bs-toggle="tab"` thuần client-side (không gọi lại server) — toàn bộ dữ liệu 6 tab được nạp 1 lần trong cùng response HTML. Có xử lý riêng: canvas Chart.js nằm trong tab đang ẩn (`display:none`) bị đo kích thước 0x0 lúc khởi tạo, nên có listener `shown.bs.tab` gọi `chart.resize()` mỗi khi chuyển tab.

### 14.4. Thay đổi kỹ thuật nhỏ để hỗ trợ Dashboard
- `TripService`: thêm 1 method `public double getDrivingHoursForDate(Driver driver, LocalDateTime date)` — thuần delegate sang method `private` đã có sẵn (`excludeTripId = null`), không đổi logic nghiệp vụ nào của method gốc.
- Không có thay đổi nào khác trong `TripService`, `BusService`, `StationService`, FSM, hay AI scheduler.
- 1 shortcut link mới trong `admin/dashboard.html` trỏ tới `/admin/analytics`, không đổi 6 shortcut cũ.

### 14.5. Giới hạn có chủ đích (đã thống nhất với người dùng khi thiết kế, không phải thiếu sót)
- **Không có "Recent Activity" feed:** không có cột `createdAt`/`updatedAt` nào trên `Trip` hay entity khác; việc thêm cột mới chỉ để phục vụ Dashboard đã bị từ chối có chủ đích khi thiết kế — nếu sau này cần lịch sử hoạt động thật, nên thiết kế Audit Log riêng, không sửa từng entity hiện có.
- **Utilization rate / AI outcome breakdown chỉ là snapshot tức thời**, không phải xu hướng lịch sử (không có bảng lưu trạng thái theo thời gian).
- **Không có entity Ticket/Assistant/MaintenanceRecord riêng** — Dashboard suy ra toàn bộ số liệu liên quan từ field có sẵn trên `Trip`/`Bus`/`Driver` (assistant vẫn là `Driver` qua FK `Trip.assistant`, không phải entity riêng).