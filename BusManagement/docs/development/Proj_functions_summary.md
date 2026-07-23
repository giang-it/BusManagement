# Tóm tắt Chức năng Project: BusManagement

> Tài liệu này tổng hợp **toàn bộ chức năng đã được lập trình thực tế** trong source code (không phải spec lý thuyết), dựa trên việc đọc trực tiếp từng file trong `repomix-output.xml`. Mục tiêu: làm input cho Codex review/kiểm tra logic.

---

## 0. Thông tin chung

- **Tech stack:** Java 17, Spring Boot 4.0.2, Spring Data JPA (Hibernate), Thymeleaf, MySQL, Lombok.
- **Kiến trúc:** Layered (Controller → Service → Repository → Domain/Entity), có một REST Controller (`TripRestController`) phục vụ AJAX.
- **Bảo mật:** `SecurityConfig` cho phép **mọi request không cần đăng nhập** (`anyRequest().permitAll()`, CSRF/form-login/http-basic đều bị tắt). Không có phân quyền ROLE_ADMIN/ROLE_DRIVER/ROLE_USER nào được enforce ở tầng Spring Security, dù entity `User`/`Role` có định nghĩa enum 3 vai trò.
- **Database init:** Mặc định `spring.jpa.hibernate.ddl-auto=update` — dữ liệu **được giữ** qua các lần khởi động, `DataInitializer` không chạy. Chỉ profile `demo` (`-Dspring-boot.run.profiles=demo`) mới dùng `create-drop` + chạy `DataInitializer` (`@Profile("demo")`) để xóa sạch và seed lại dữ liệu test. Test dùng DB riêng `busmanagement_test`.
- **Soft delete:** `Trip` dùng `@SQLDelete` + `@SQLRestriction("is_deleted = false")` — mọi `delete()` qua JPA chỉ là UPDATE `is_deleted=true`, và mọi SELECT tự động loại các trip đã xóa.

### Khoảng cách giữa spec ban đầu và code thực tế

`.agent_instructions/03_functional_spec.md` mô tả một hệ thống đầy đủ 4 nhóm tác nhân (Admin, Driver/Staff, Client/User, Hệ thống tự động) bao gồm: đặt vé online, thanh toán, QR check-in, JWT auth, thống kê doanh thu, đánh giá khách hàng, báo cáo sự cố, v.v. **Phần lớn các mục này CHƯA được lập trình.** Code hiện tại chỉ triển khai phần **Admin — Điều hành thông minh (Smart Scheduling)** và một phần nhỏ của **Quản lý danh mục**. Không có entity/controller nào cho Ticket, Payment, Seat, Review, Notification, JWT, hay giao diện Driver/Client.

---

## 1. Domain Model (Entities)

| Entity | Vai trò | Trường đáng chú ý |
|---|---|---|
| `User` | Tài khoản hệ thống | `username`, `password` (plaintext, không mã hóa), `role` (enum `Role`), quan hệ 1-1 với `Driver` |
| `Role` | Enum | `ROLE_ADMIN`, `ROLE_DRIVER`, `ROLE_USER` — định nghĩa nhưng không được Spring Security sử dụng để phân quyền |
| `Driver` | Hồ sơ tài xế, `@MapsId` dùng chung PK với `User` | `licenseNumber`, `licenseExpiryDate`, `experienceYears`, `totalDrivingHours24h` (giờ lái baseline, dữ liệu mock seed), `monthlyRestDays`, `isActive`; method `isLicenseValid(LocalDate)` (kiểm theo ngày khởi hành) + `isLicenseValid()` (theo hôm nay, chỉ để hiển thị) |
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
6. Với mỗi người (driver chính, từng coDriver, assistant): check `isLicenseValid(ngày khởi hành)` — bằng lái phải còn hiệu lực VÀO NGÀY KHỞI HÀNH, không phải hôm nay (áp cho CẢ driver chính, từng coDriver VÀ phụ xe — phụ xe cũng là Driver nên cũng bắt buộc còn bằng), check không bận trong cửa sổ `[departure - 30p, arrival + 30p]` qua `isDriverBusyInWindow()` (gồm cả vai trò driver chính, coDriver, assistant ở các trip khác có status `PENDING_APPROVAL/ACTIVE/DEPARTED`).
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
- Filter: `isActive = true`, không thuộc `excludeDrivers`, `isLicenseValid(departure.toLocalDate())`, `getDrivingHoursForDate(d, departure) + effectiveHours <= 8.0`, không bận trong cửa sổ (cả vai trò driver chính và assistant ở chuyến khác).
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

Cả 4 hàm đều áp dụng cùng bộ điều kiện cốt lõi: `isActive`, `isLicenseValid(ngày khởi hành)`, giờ lái trong ngày + effective hours ≤ 8h, không bận trong cửa sổ nghỉ tối thiểu.

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
10. ~~**`ddl-auto=create-drop` + xóa toàn bộ data mỗi lần start**~~ — **đã xử lý (Phase 0)**: mặc định giờ là `update` (giữ dữ liệu), việc wipe+seed phải opt-in qua profile `demo`. Vẫn còn hạn chế: chưa có công cụ migration (Flyway/Liquibase), schema tiến hóa dựa vào `update` của Hibernate.
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
- **CRUD Tài xế** (Phase 1) — `DriverService`/`AdminDriverController`. Một form gộp tạo **cả `User` lẫn `Driver`** (role ép `ROLE_DRIVER`), vì `Driver` dùng `@MapsId` nên không thể tồn tại thiếu `User`. Guard: không cho vô hiệu hóa hoặc xóa tài xế đang còn chuyến chưa kết thúc. Hai trường `monthlyRestDays` và `totalDrivingHours24h` cố ý **không** đưa lên form: `monthlyRestDays` không được đọc ở bất kỳ đâu trong code (quy tắc "nghỉ đủ 2 ngày/tháng" của spec gốc chưa từng được cài), nên một ô nhập được sẽ ngụ ý một tác dụng không tồn tại; còn `totalDrivingHours24h` thì **có** tác dụng thật (`getDrivingHoursForDate()` cộng nó vào tổng giờ trong ngày) nhưng được ghi rõ trong `TripService` là dữ liệu mock seed, sau này sẽ do IoT/GPS bơm vào — nên cũng không phải trường cho Admin sửa.
- **CRUD Tuyến đường** (Phase 1) — `RouteService`/`AdminRouteController`, có trình sửa nhiều điểm dừng động; `stopOrder` được đánh lại 1..n theo thứ tự gửi lên. Sửa điểm dừng là **xóa rồi dựng lại** chứ không update, vì PK của `RouteStation` là cặp `(routeId, stationId)` — đổi trạm nghĩa là đổi khóa chính. Chặn xóa tuyến đang có chuyến; chặn một tuyến đi qua cùng một trạm hai lần (giới hạn mô hình, không phải lựa chọn nghiệp vụ).
- **Bảng Điều phối** (Phase 1) — `DispatchController` (`/admin/dispatch`), gom chuyến thành 3 nhóm: quá giờ khởi hành / đang trên đường / sắp khởi hành trong 48h. Mọi thao tác đổi trạng thái đều **ủy quyền cho `TripService.updateTripStatus()`**, không tự đổi, nên FSM và đồng bộ `BusStatus` vẫn là một đường duy nhất.
- **Quản lý Sự cố** (Phase 2) — entity `Incident` + enum `IncidentType` (5 giá trị: `VEHICLE_BREAKDOWN`, `ACCIDENT`, `ROAD_ISSUE`, `STAFF_ISSUE`, `OTHER`) và `IncidentStatus` (`OPEN`/`IN_PROGRESS`/`RESOLVED`), `IncidentService`, `AdminIncidentController`. `bus` **bắt buộc** (mọi sự cố đều quy về một xe cụ thể), `trip` và `driver` tùy chọn (xe hỏng trong bãi thì không có chuyến; không có đăng nhập nên không suy ra được người báo). **Không có FSM** cho `IncidentStatus` — Admin đi lại tự do, kể cả mở lại sự cố đã đóng nhầm; quy tắc duy nhất là `resolvedAt` tự đóng dấu khi vào `RESOLVED` và bị xóa khi rời khỏi. Ghi sự cố **không** tự đổi `Bus.status`; chuyển xe sang `REPAIRING` vẫn là thao tác tay có chủ đích. Xe hoặc tài xế còn bản ghi sự cố thì **không xóa cứng được** — kể cả khi sự cố đã `RESOLVED`, vì ràng buộc này bảo vệ toàn vẹn tham chiếu chứ không phải công việc đang mở.
- **API kiểm tra nghiệp vụ dạng dry-run** (Phase 3) — `ValidationResult` + `TripService.validateBusForTripDryRun()` / `validateStaffForTripDryRun()`: bọc `try`/`catch` quanh **chính hai validator sẵn có** (mục 6.7), trả về kết quả đạt/không đạt thay vì ném exception. Hai validator gốc và cả 4 nơi gọi kiểu ném exception **không bị sửa một dòng nào**. Chỉ bắt `IllegalArgumentException` — loại mà validator dùng để báo vi phạm nghiệp vụ; lỗi kỹ thuật (NPE, lỗi truy cập dữ liệu…) vẫn được ném lên chứ không bị biến thành "tài nguyên không hợp lệ". Giữ nguyên fail-fast nên mỗi kết quả chỉ mang **một** lý do — quy tắc đầu tiên bị vi phạm. Không có UI: đây là API để Decision Support (Phase 7) gọi.
- **Bộ sinh dữ liệu lịch sử** (Phase 5) — `HistoricalDataBackfill`, `@Profile("backfill")`: sinh 12 tuần chuyến **mô phỏng** làm đầu vào cho Dự báo nhu cầu, vì dữ liệu vận hành thật không tích lũy kịp trong thời gian làm đồ án. Idempotent nhờ khóa `(tuyến, thời điểm khởi hành)` — chạy lại chỉ bù phần còn thiếu, không nhân đôi, và không cần thêm cột đánh dấu nào vào `Trip`. Km của chuyến hoàn thành được cộng vào **cả** `odometer` **lẫn** `lastMaintenanceOdometer`, nên `kmSinceLastMaintenance` — và do đó mọi quy tắc bảo dưỡng — không đổi.
- **Đề xuất tài xế khả dụng** (Phase 4, đọc-only, xem mục 15) — deliverable trụ cột 2 đầu tiên: ai còn hạn mức giờ lái trong một ngày Admin chọn, lọc theo đúng ràng buộc mà `validateStaffForTrip()` sẽ áp.
- **Dự báo nhu cầu** (Phase 6, đọc-only, xem mục 16) — module duy nhất có tính dự đoán: tỉ lệ lấp đầy theo tuyến × khung giờ cho 7 ngày tới, kèm tự chấm điểm sai số trên dữ liệu giữ lại.
- **Đề xuất thay thế phương tiện** (Phase 7 bước 1, đọc-only, xem mục 17) — xếp hạng cả đội xe theo km trọn đời và tần suất sự cố. Đây cũng là **đường đọc đầu tiên** của dữ liệu sự cố: trước đó `incidents` chỉ được ghi vào chứ chưa nuôi quyết định nào.
- **Đề xuất tăng cường** (Phase 7 bước 2, đọc-only, xem mục 18) — người điều phối ghép Dự báo + Chọn tài nguyên + Cổng ràng buộc: mỗi khung giờ được dự báo đông thành một thẻ kèm xe, tài xế và doanh thu ước tính (chỉ doanh thu; chi phí bị chặn). Tính lại mỗi lần xem, không tạo chuyến. Có `AvailabilityContext` nạp lịch một lần để chọn tài nguyên trên bộ nhớ (~19× nhanh hơn) mà luật vẫn nằm nguyên trong `TripService`.

**Chưa làm (có trong spec nhưng không có code):**
- Đặt vé, chọn ghế, thanh toán online (Client/User module — toàn bộ).
- QR check-in, app/trang riêng cho Driver (lịch trình cá nhân, nhật ký lái xe, tài xế tự báo sự cố). Toàn bộ nhóm này giả định có phiên đăng nhập của tài xế, mà bật đăng nhập thật lại nằm ngoài phạm vi — nên đây là **Non-Goal có chủ đích**, không phải việc còn nợ. ⚠️ Đừng đọc dòng này thành "chưa có quản lý sự cố": việc **Admin ghi nhận sự cố thay tài xế** thì đã có (Phase 2, xem phần "Đã làm"). Đó là một **diễn giải lại có chủ đích** của yêu cầu gốc, và phải được nói rõ như vậy khi bảo vệ.
- Thống kê doanh thu, báo cáo hiệu suất tài xế, đánh giá khách hàng. *(Doanh thu đã có ở mức tổng hợp trên Dashboard — mục 14.3, tab Trips/Routes; thứ chưa có là báo cáo doanh thu chuyên biệt.)*
- JWT, phân quyền thực tế theo Role.
- Notification (Email/SMS).
- Cron job reset giờ lái 00:00 hàng ngày (chỉ có job quét extra-trip mỗi 10 giây, không có job reset).
- Locking xử lý đặt ghế đồng thời (chưa có Ticket/Seat entity nên chưa cần).

> **Đã đối soát lại toàn bộ mục 13 ngày 2026-07-22.** Trước lần đối soát này, danh sách "Đã làm" phản ánh trạng thái *trước Phase 1* — thiếu CRUD Tài xế, CRUD Tuyến đường, Bảng Điều phối (Phase 1), Quản lý Sự cố (Phase 2), API dry-run (Phase 3), bộ sinh dữ liệu lịch sử (Phase 5), Đề xuất tài xế (Phase 4) và Dự báo nhu cầu (Phase 6). Tất cả đã được bổ sung, và dòng "app/trang riêng cho Driver" ở phần "Chưa làm" đã được làm rõ để không bị đọc nhầm thành "chưa có quản lý sự cố".
>
> **Còn nợ, không phải nợ tính năng mà là nợ tài liệu:** trong số các module vừa bổ sung, mới có Phase 4 và Phase 6 được viết thành mục chi tiết riêng (mục 15 và 16). Phase 1, 2, 3, 5 hiện chỉ có mô tả gọn ở danh sách trên; nếu cần mức chi tiết như mục 14–16 thì phải viết thêm mục riêng cho từng module. Nguồn tham chiếu đầy đủ nhất cho chúng lúc này là `THESIS_ROADMAP.md` mục 5 (phần "As delivered" của từng phase) và mục 8 (Session Log).

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

---

## 15. Module: Đề xuất tài xế khả dụng (Driver Recommendation) — `DriverRecommendationController` (`/admin/analytics/driver-recommendation`)

Deliverable **trụ cột 2 (Hỗ trợ ra quyết định)** đầu tiên của hệ thống. Trả lời câu hỏi *"ngày này ai còn nhận thêm chuyến được?"* — **chiều ngược lại** của bảng "Tài xế có tải cao nhất hôm nay" đã có ở tab Drivers của Dashboard (mục 14.3).

Toàn bộ module **chỉ đọc**: không tạo chuyến, không gán ai vào đâu. Đây là ĐỀ XUẤT để Admin tự quyết, không phải phân công tự động.

### 15.1. Cấu trúc
- **Controller:** `DriverRecommendationController` — 1 `GET` duy nhất, nhận tham số tùy chọn `?date=yyyy-MM-dd` (bỏ trống thì mặc định hôm nay, để link từ Dashboard không cần mang sẵn tham số). Chỉ inject Service, theo mẫu `AdminBusController`.
- **Service:** `DriverRecommendationService` — `@Transactional(readOnly = true)`, inject `DriverRepository` và `TripService`.
- **DTO:** `DriverRecommendationDto` (một tài xế được đề xuất) và `DriverRecommendationViewDto` (toàn bộ màn hình).
- **Template:** `admin/driver-recommendation.html`.

### 15.2. Bộ lọc — soi gương đúng những gì `validateStaffForTrip()` sẽ áp
Danh sách không bao giờ đề xuất một người mà hệ thống sẽ từ chối ngay sau đó, vì dùng đúng ba ràng buộc của validator:
- `isActive` — validator chặn tài xế đã bị khóa;
- `isLicenseValid(date)` — bằng lái còn hiệu lực **vào ngày được chọn**, đúng hàm và đúng ngữ nghĩa ngày mà validator dùng với ngày khởi hành;
- còn hạn mức giờ trong ngày — validator chặn "vượt 8h/ngày".

Sắp xếp **tăng dần** theo số giờ đã phân công (rảnh nhất lên đầu), tie-break theo tên.

### 15.3. Giờ lái lấy từ đâu
100% từ `TripService.getDrivingHoursForDate(driver, date.atStartOfDay())`. Service này **tuyệt đối không tự cộng giờ**: quy tắc chia giờ cho tài xế phụ, quy ước phụ xe tính 0 giờ, và phần giờ nền mock `totalDrivingHours24h` đều nằm trong method đó — chỉ được phép tồn tại một cách tính duy nhất trong hệ thống.

### 15.4. Ba con số giải thích vì sao danh sách ngắn
`activeDriversConsidered`, `noCapacityCount` (hết hạn mức giờ), `licenseBlockedCount` (bằng hết hạn). Không có chúng, Admin nhìn 27 dòng trên 36 tài xế sẽ tưởng hệ thống lỗi.

### 15.5. Quyết định thiết kế đáng ghi
- **Hằng số 8.0 khai báo lại tại chỗ** (`MAX_DAILY_DRIVING_HOURS`) thay vì rút ra hằng số dùng chung từ `TripService`. Lý do: trong `TripService` số 8.0 xuất hiện 14 lần với **ba ý nghĩa nghiệp vụ khác nhau** (hạn mức giờ/ngày; trần quy đổi giờ cho một chuyến khi chia cho tài xế phụ; ngưỡng "chuyến dài" bắt buộc thêm người). Gom thành một hằng số sẽ **sai về nghiệp vụ** dù giá trị giống nhau, và khiến sửa một quy tắc vô tình đổi cả ba.
- **`DriverWorkloadDto` đã được cân nhắc tái sử dụng và bị loại**: nó chỉ có 3 trường (`userId`/`fullName`/`drivingHoursToday`), không mang nổi hạn mức còn lại, tình trạng bằng lái, nhãn tình trạng hay lý do đề xuất. Mở rộng nó sẽ kéo 6 trường thừa vào đường `topLoadedDrivers` đã chạy ổn.
- **Trang riêng, không phải tab thứ 7** của `dashboard-analytics.html`: `DashboardService.getOverview()` không nhận tham số nào, nên một màn hình theo ngày không có chỗ đứng trong đó nếu không sửa chữ ký đã ship; và đây là deliverable trụ cột 2, cần trông khác biệt rõ với thống kê trụ cột 1.

### 15.6. Giới hạn có chủ đích
- Chỉ **đề xuất**, không phân công. Màn hình không có action ghi nào.
- Không xét kinh nghiệm hay loại bằng khi xếp hạng — chỉ xếp theo tải, vì đó là câu hỏi module này đặt ra. Kinh nghiệm và hạn bằng được **hiển thị** thành cột để Admin tự cân nhắc.

---

## 16. Module: Dự báo nhu cầu (Demand Forecast) — `ForecastController` (`/admin/analytics/forecast`)

Thành phần **AI Prediction**, và là module **duy nhất có tính dự đoán** trong hệ thống — mọi module khác đều báo cáo chuyện đã xảy ra. Chỉ đọc.

### 16.1. Cấu trúc
- **Controller:** `ForecastController` — 1 `GET`, không tham số (chân trời cố định 7 ngày kể từ ngày mai). Chỉ inject Service.
- **Service:** `ForecastService` — `@Transactional(readOnly = true)`, inject `TripRepository` và `RouteRepository`.
- **DTO:** `ForecastPointDto` (một ngày dự báo), `ForecastSeriesDto` (một chuỗi), `ForecastAccuracyDto` (kết quả tự chấm điểm), `DemandForecastViewDto` (toàn màn hình).
- **Template:** `admin/demand-forecast.html`.

### 16.2. Đơn vị dự báo
Một **chuỗi** = một tuyến ở một khung giờ khởi hành. Nhóm chỉ được dự báo khi có **≥ 28 chuyến `COMPLETED`** (4 tuần trọn, để mỗi thứ trong tuần có ít nhất 4 điểm); nhóm dưới ngưỡng bị đếm và báo ra chứ không âm thầm biến mất.

Dùng **ngưỡng** thay vì danh sách khung giờ hợp lệ cứng, vì ngoài các chuỗi do backfill sinh ra, dữ liệu thật còn lẫn chuyến lẻ Admin tự tạo ở giờ bất kỳ — danh sách cứng sẽ lạc hậu ngay khi có thêm một chuyến giờ lạ.

### 16.3. Dự báo cái gì — và vì sao không phải số vé
Dự báo **tỉ lệ lấp đầy**, cùng đại lượng với `Trip.getOccupancyRate()`. Đây là ràng buộc kiến trúc: hệ thống đã có đúng **một** định nghĩa "chuyến đông" (`Trip.needsReinforcement()`, > 0.90) đang điều khiển luồng chuyến tăng cường ở mục 7. Dự báo bằng occupancy nghĩa là ngưỡng đó được dùng lại nguyên vẹn với đầu vào là số dự báo; dự báo bằng số vé sẽ buộc phải đẻ ra ngưỡng thứ hai bằng đơn vị codebase chưa từng dùng.

Lý do đo được đi kèm: số vé = occupancy × sức chứa **chiếc xe được xếp hôm đó**, mà đội xe có ba loại 22/40/50 ghế xuất hiện trong cả 15 chuỗi. Hệ số biến thiên của số vé là 0,32–0,35 còn của occupancy chỉ 0,08–0,11.

### 16.4. Mô hình
1. **Khử mùa vụ:** chia tỉ lệ lấp đầy cho hệ số thứ-trong-tuần.
2. **Hồi quy tuyến tính** bình phương tối thiểu trên phần đã khử → mức nền + độ dốc.
3. **Dự báo** ngày D = `(mức nền + độ dốc × số ngày) × hệ số thứ của D`, kẹp trong [0, 1].

Hệ số thứ-trong-tuần tính **gộp** trên toàn bộ quan sát đủ điều kiện, không tính riêng từng chuỗi (mỗi chuỗi chỉ ~78 điểm ⇒ ~11 điểm/thứ, quá mỏng).

Trung bình trượt tính theo **7 ngày lịch**, không phải "7 quan sát gần nhất" — vì ~6% chuyến bị hủy đã bị loại nên chuỗi thủng vài ngày, và "7 quan sát" sẽ trải quá 7 ngày rồi tự nhiễm lại đúng chu kỳ tuần mà nó phải khử.

### 16.5. Tự chấm điểm (backtest)
Giấu 14 ngày cuối, huấn luyện trên phần còn lại, dự báo lại quãng đã giấu rồi so với số thật; báo sai số phần trăm tuyệt đối trung bình cạnh sai số của baseline "giữ phẳng trung bình trượt". **Hệ số mùa vụ được tính lại chỉ từ phần huấn luyện** — dùng lại hệ số tính trên toàn bộ dữ liệu sẽ rò rỉ quãng kiểm tra vào mô hình và cho ra con số đẹp một cách vô nghĩa. Trường hợp mô hình **thua** baseline cũng được hiển thị thẳng chứ không giấu.

### 16.6. Hiệu năng
Đọc qua projection 4 cột `TripRepository.findDemandHistoryByStatus(...)` chứ không nạp entity `Trip`, theo tiền lệ `findSeatsAndSoldByStatus`. Trang chạy ~0,10 s / ~37 KB trên 1.200+ chuyến, nên không góp thêm vào vấn đề tổng hợp in-memory đã biết của `DashboardService`.

### 16.7. Giới hạn phải nói rõ
- **Dữ liệu lịch sử là MÔ PHỎNG** (sinh bằng profile `backfill`, xem `setup_guide.md` §4.3). Trang tự hiển thị cảnh báo này. Số liệu minh họa *phương pháp*, không phải kết quả kinh doanh thật.
- Bộ dữ liệu có **ngày kết thúc cố định và cũ dần**; trang hiển thị nó đã cũ bao nhiêu ngày.
- Đây là **thống kê, không phải máy học** — không có mô hình ML nào, chỉ hồi quy tuyến tính và trung bình theo nhóm.
- Chỉ dự báo theo tuyến × khung giờ; không có chiều theo trạm, loại xe hay độ nhạy giá.

---

## 17. Module: Đề xuất thay thế phương tiện (Vehicle Replacement) — `VehicleReplacementController` (`/admin/analytics/vehicle-replacement`)

Bước 1 của Phase 7. Trả lời *"chiếc nào trong đội đáng cân nhắc thay trước?"* — **khác hẳn** câu mà dải "Cảnh báo bảo dưỡng" trên Dashboard (mục 14.2) đang trả lời. Chỉ đọc: không đổi trạng thái xe, không loại xe nào khỏi điều phối.

### 17.1. Cấu trúc
- **Controller:** `VehicleReplacementController` — 1 `GET`, không tham số. Chỉ inject Service.
- **Service:** `VehicleReplacementService` — `@Transactional(readOnly = true)`, inject `BusRepository` và `IncidentRepository`.
- **DTO:** `VehicleReplacementDto` (một xe trong bảng xếp hạng), `VehicleReplacementViewDto` (toàn màn hình, kèm các số mô tả chất lượng tín hiệu).
- **Template:** `admin/vehicle-replacement.html`.
- Một truy vấn projection mới: `IncidentRepository.countIncidentsPerBusAndType(...)` trả `(busId, loại, số lượng)`.

### 17.2. Vì sao KHÔNG chấm điểm trên `kmSinceLastMaintenance`
Đây là chỗ dễ làm sai nhất. Trường đó là hiệu `odometer - lastMaintenanceOdometer` nên **reset sau mỗi lần bảo dưỡng**: xe chạy 800.000 km đã bảo dưỡng 50 lần vẫn đọc ra số nhỏ. Nó trả lời *"tới hạn bảo dưỡng chưa?"*, không phải *"đã cũ chưa?"*. Và câu hỏi thứ nhất **đã được trả lời rồi** bởi `DashboardService.buildMaintenanceAlerts()`. Dùng lại nó ở đây sẽ là giao lại một tính năng cũ dưới tên mới.

Module này vì thế dùng `odometer` — con số **không bao giờ reset** — và cố ý **không hiển thị lại** cột `kmSinceLastMaintenance`, để hai câu hỏi không bị trộn vào nhau.

*Bằng chứng hai màn khác nhau thật:* lúc kiểm chứng, Cảnh báo bảo dưỡng liệt kê đúng **2** xe, còn màn này xếp hạng cả **20**; hai xe hạng 2–3 ở đây không hề có mặt bên kia, và chiếc đứng thứ 2 về mức cấp bách bảo dưỡng chỉ đứng thứ **4** về ưu tiên thay thế.

### 17.3. Chỉ đếm sự cố nói lên tình trạng chiếc xe
Trong 5 giá trị `IncidentType`, chỉ **`VEHICLE_BREAKDOWN`** và **`ACCIDENT`** được tính. `ROAD_ISSUE` (kẹt xe, ngập, cấm đường) và `STAFF_ISSUE` (tài xế ốm) không nói gì về phương tiện — đếm chúng nghĩa là xe tốt bị trừ điểm vì hôm đó tắc đường.

Danh sách loại nằm ở tầng service chứ không nhúng vào câu truy vấn, vì "loại nào nói lên tình trạng xe" là quy tắc nghiệp vụ.

### 17.4. Công thức
```
hao mòn    = (odometer - min đội) / (max đội - min đội)      → 0..1
độ tin cậy = số sự cố xe / số sự cố cao nhất đội             → 0..1
điểm       = (0,7 × hao mòn + 0,3 × độ tin cậy) × 100
```
Trọng số 70/30 phản ánh **chất lượng tín hiệu**, không phải tầm quan trọng: odometer là số đo liên tục, có trên mọi xe và phân biệt được từng chiếc; số sự cố thì thưa và rời rạc. Cả hai mẫu số đều có lưới chặn chia cho 0.

### 17.5. Tự khai báo chất lượng tín hiệu
Service tự tính số xe chưa có sự cố nào; khi con số đó chiếm từ một nửa đội trở lên, giao diện hiện cảnh báo rằng nửa "độ tin cậy" không phân biệt được ai với ai và thứ hạng thực chất do số km quyết định. Đây là số liệu tính ra, không phải câu chữ viết cứng.

### 17.6. Giới hạn có chủ đích
- **Điểm là tương đối trong đội xe hiện tại**, nên xe đứng đầu luôn đạt điểm cao nhất kể cả khi cả đội còn mới. Đây là **thứ tự ưu tiên xem xét**, không phải phán quyết phải thay.
- **Không có ngưỡng tuyệt đối** ("trên X km thì thay") vì hệ thống không có dữ liệu nào biện minh cho một con số như vậy — `Bus` không có năm sản xuất, ngày mua, hay lịch sử chi phí sửa chữa.
- **Không tính được tổng chi phí sở hữu**: cần entity `MaintenanceRecord` (ngày, chi phí, hạng mục), là stretch goal cố ý để ngoài Phase 7 nhằm không phải đổi schema.
- Xe đang sửa chữa **vẫn được xếp hạng**: đang phải sửa là thông tin đáng cân nhắc khi bàn thay xe, không phải lý do loại khỏi danh sách.

---

## 18. Module: Đề xuất tăng cường (Recommendation Engine) — `RecommendationController` (`/admin/analytics/recommendation`)

Bước 2 của Phase 7 (chỉ doanh thu — phần chi phí bị chặn vì domain không có dữ liệu chi phí). Trả lời *"khung giờ nào sắp tới được dự báo đông, và ta có thể chạy chuyến tăng cường bằng xe + tài xế nào, thu về bao nhiêu?"*. Là **người điều phối**, không phải bộ dự báo hay bộ luật: nó ghép nối Dự báo (mục 16) + Chọn tài nguyên (`TripService`) + Cổng ràng buộc (Phase 3). Chỉ đọc: không tạo chuyến, không phân công ai.

### 18.1. Cấu trúc
- **Controller:** `RecommendationController` — 1 `GET`, không tham số. Chỉ inject Service.
- **Service:** `RecommendationService` — `@Transactional(readOnly = true)`, inject `ForecastService`, `TripService`, `RouteRepository`, `TripRepository`.
- **DTO:** `RecommendationCardDto` (một thẻ), `RecommendationViewDto` (toàn màn hình, kèm độ tươi dữ liệu forecast), `RecommendationStatus` (enum: `RECOMMENDED` / `STALE` / `NO_RESOURCE`).
- **Template:** `admin/recommendation.html`.
- Hai truy vấn projection mới trên `TripRepository`: `findPriceHistoryByStatus(...)` (giá vé lịch sử) và `findScheduleForAvailability(...)` (nạp lịch một lần cho preload).

### 18.2. Dây chuyền (đúng thiết kế roadmap, không đổi)
```
buildForecast() [gọi ĐÚNG 1 lần]
   → mỗi ForecastPointDto có needsReinforcement = true
   → dựng Trip TẠM (tuyến + ngày + khung giờ, giống createExtraTrip())
   → chọn xe (selectBestAvailableBus)  +  chọn tài xế (selectBestAvailableDriver)
   → doanh thu = round(tỉ lệ dự báo × sức chứa xe được chọn) × giá vé lịch sử
   → cổng Business Rule Validation (dry-run Phase 3)
   → thẻ: RECOMMENDED / STALE / NO_RESOURCE
```

### 18.3. Chỉ đọc `needsReinforcement`, KHÔNG biết ngưỡng 0.90
Quyết định "đông" đã được nướng sẵn vào `ForecastPointDto.needsReinforcement` (mục 16). Engine chỉ đọc cờ boolean đó ⇒ ngưỡng 0.90 vẫn nằm đúng ba chỗ cũ (`Trip`, `DashboardService`, `ForecastService`), không sinh chỗ thứ tư. Đây là cách "dùng lại ngưỡng" đúng nghĩa: tiêu thụ **quyết định**, không tiêu thụ **con số**.

### 18.4. Doanh thu lấy giá từ đâu
Chuyến ứng viên chưa có `Trip` nên chưa có giá. Giá = `Trip.price` của **chuyến COMPLETED gần nhất cùng (tuyến, giờ khởi hành)** — giá THẬT đã tồn tại, "consume as-is" đúng Non-Goal của Section 4, không bịa số. Lấy qua projection scalar (`findPriceHistoryByStatus`), không nạp entity `Trip`.

### 18.5. Thẻ tính lại mỗi lần xem, không lưu DB (chốt của chủ dự án)
Không entity mới, không ghi, không tạo chuyến — nhất quán với hai màn Decision Support kia. Hệ quả đã ghi rõ: `STALE` gần như không xuất hiện trong mô hình này (chọn và cổng chạy sát nhau trên cùng một ảnh chụp), nhưng vẫn giữ để trung thực khi cổng thật sự trượt và làm lưới an toàn nếu logic chọn/validate về sau lệch nhau. Cổng validation cuối chạy **SQL trực tiếp** (độc lập với dữ liệu preload) — tái xác nhận đúng khuôn `confirmAutoAssignedTrip`.

### 18.6. Tối ưu hiệu năng bằng preload — luật vẫn một nguồn
Tái dùng `findBestAvailableDriver` cho từng thẻ khiến trang mất ~18,7s (mỗi thẻ quét N tài xế × truy vấn giờ/bận). Thêm `AvailabilityContext` (class **chỉ chứa dữ liệu**) nạp toàn bộ lịch liên quan **một lần**, cùng các overload nhận `ctx` trên `findBestAvailableBus/Driver` và ba leaf (`isBusBusy`, `isDriverBusyInWindow`, `getDrivingHoursForDate`): `ctx == null` → SQL như cũ (đường phân công **không đụng tới**); `ctx != null` → xét trên dữ liệu preload. Kết quả **~0,97s (≈19×)**, nội dung **byte-identical**. Luật tính giờ tách vào `sumDrivingHours(...)` (một chỗ); predicate giao-khoảng/vai-trò là phép toán nền tảng, có test `TripServiceAvailabilityContextTest` chốt Java ≡ SQL ở các ca biên.

### 18.7. Giới hạn có chủ đích
- **Mỗi thẻ được đánh giá độc lập**, pool không "giữ chỗ" giữa các thẻ — hai thẻ cùng (ngày, giờ) có thể cùng đề xuất một xe/tài xế. Giao diện bật cảnh báo khi phát hiện trùng khung. Đây là **danh sách gợi ý**, không phải lịch phân công khả thi đồng thời.
- **Chỉ doanh thu, chưa có chi phí** — domain không có trường chi phí nào; ước tính chi phí (bước 3) bị chặn chờ chủ dự án quyết.
- **MVP một tài xế chính**: mọi tuyến vào được forecast đều ≤ 8h nên `requiredDrivers` luôn = 1; chuyến > 8h (cần tài xế phụ + phụ xe) nằm ngoài phạm vi bước 2.