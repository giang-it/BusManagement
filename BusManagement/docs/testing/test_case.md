# 📋 TÀI LIỆU KIỂM THỬ TOÀN DIỆN
# Dự án: Bus Management System — Hệ thống Quản lý và Điều hành Xe khách Thông minh
**Phiên bản:** 1.0  
**Ngày soạn:** 18/06/2026  
**Người soạn:** Senior QA/QC Engineer  
**Nền tảng:** Spring Boot + Thymeleaf + JPA/Hibernate + Spring Security  
**Phạm vi:** Kiểm thử toàn diện dựa trên mã nguồn thực tế (phân tích từ `repomix-output.xml`)

---

## MỤC LỤC

1. [Module 1: Quản lý Xe khách (Bus Management)](#module-1)
2. [Module 2: Quản lý Bến xe (Station Management)](#module-2)
3. [Module 3: Quản lý Chuyến xe Thủ công & Máy trạng thái FSM](#module-3)
4. [Module 4: Hệ thống AI Đề xuất Chuyến tăng cường (AI Scheduler)](#module-4)
5. [Module 5: Quy trình Phê duyệt của Admin (Admin Approval Flow)](#module-5)
6. [Module 6: Tầng Bảo mật & Cấu hình (Security & Config)](#module-6)

---

## BẢNG QUY ƯỚC

| Ký hiệu | Ý nghĩa |
|---|---|
| `✅ Pass` | Hành vi hệ thống đúng theo kỳ vọng |
| `❌ Fail` | Hành vi không đúng, cần sửa |
| `flash[success]` | `redirectAttributes.addFlashAttribute("success", "...")` |
| `flash[error]` | `redirectAttributes.addFlashAttribute("error", "...")` |
| `flash[warning]` | `redirectAttributes.addFlashAttribute("warning", "...")` |
| `HTTP 400` | ResponseEntity.badRequest() từ REST controller |
| `@SQLRestriction` | `WHERE is_deleted = false` tự động thêm vào mọi SELECT |

---

<a name="module-1"></a>
## MODULE 1: QUẢN LÝ XE KHÁCH (Bus Management)
**Controller:** `AdminBusController` — `@RequestMapping("/admin/buses")`  
**Service:** `BusService`  
**Repository:** `BusRepository`, `TripRepository`

---

### TC_BUS_001 — Hiển thị danh sách xe khách

- **Mã TC:** TC_BUS_001
- **Tên Kịch Bản:** Hiển thị đầy đủ danh sách tất cả xe khách hiện có trong hệ thống
- **Điều kiện tiên quyết:** Database có ít nhất 3 bản ghi xe với các trạng thái khác nhau (READY, TRAVELING, REPAIRING). `DataInitializer` đã seed sẵn dữ liệu.
- **Các bước thực hiện:**
  1. Truy cập `GET /admin/buses`
- **Kết quả mong đợi:**
  - HTTP 200 OK, render template `admin/bus/bus-list`
  - Model chứa attribute `buses` = kết quả truy vấn `busRepository.findAllWithBusType()` (JOIN FETCH `busType` để tránh N+1)
  - Danh sách hiển thị đầy đủ thông tin: biển số, loại xe, thương hiệu, odometer, trạng thái
  - Không bị `LazyInitializationException` khi render cột "Loại xe"

---

### TC_BUS_002 — Tạo xe mới hợp lệ (Happy Path đầy đủ thông tin)

- **Mã TC:** TC_BUS_002
- **Tên Kịch Bản:** Admin tạo mới xe với đầy đủ thông tin hợp lệ
- **Điều kiện tiên quyết:** Tồn tại ít nhất 1 bản ghi `BusType` trong DB (ví dụ: id=1, typeName="Limousine")
- **Các bước thực hiện:**
  1. `GET /admin/buses/create` — kiểm tra form render đúng (model có `bus`, `busTypes`, `statuses`)
  2. Điền form: `licensePlate = "30A-12345"`, `brand = "Thaco"`, `busType.id = 1`, `status = READY`, `odometer = 1500.0`, `maintenanceThreshold = 5000.0`, `lastMaintenanceOdometer = 0.0`
  3. `POST /admin/buses/create`
- **Kết quả mong đợi:**
  - `busService.saveBus(bus)` được gọi thành công
  - Bản ghi được INSERT vào bảng `buses` với đúng giá trị
  - `flash[success]` = `"Thêm mới xe thành công!"`
  - Redirect về `GET /admin/buses`

---

### TC_BUS_003 — Tạo xe mới với các trường Odometer bị bỏ trống (Auto-Default)

- **Mã TC:** TC_BUS_003
- **Tên Kịch Bản:** Hệ thống tự động gán giá trị mặc định cho 3 trường Odometer khi Admin bỏ trống
- **Điều kiện tiên quyết:** Không có xe nào với biển số "30A-99999" trong DB
- **Các bước thực hiện:**
  1. `POST /admin/buses/create` với payload: `licensePlate = "30A-99999"`, `brand = "Test"`, `status = READY`
  2. **Không điền** `odometer`, `maintenanceThreshold`, `lastMaintenanceOdometer` (null)
- **Kết quả mong đợi:**
  - Trong `BusService.saveBus()`, logic kiểm tra null được kích hoạt:
    - `bus.getMaintenanceThreshold() == null` → `setMaintenanceThreshold(5000.0)` ✅
    - `bus.getLastMaintenanceOdometer() == null` → `setLastMaintenanceOdometer(0.0)` ✅
    - `bus.getOdometer() == null` → `setOdometer(0.0)` ✅
  - DB lưu: `odometer = 0.0`, `maintenance_threshold = 5000.0`, `last_maintenance_odometer = 0.0`
  - `flash[success]` = `"Thêm mới xe thành công!"`

---

### TC_BUS_004 — Tạo xe mới với giá trị Odometer do người dùng nhập (Không ghi đè)

- **Mã TC:** TC_BUS_004
- **Tên Kịch Bản:** Hệ thống KHÔNG ghi đè giá trị Odometer khi Admin đã nhập sẵn
- **Điều kiện tiên quyết:** Không có xe nào với biển số "30A-88888" trong DB
- **Các bước thực hiện:**
  1. `POST /admin/buses/create` với: `odometer = 2000.0`, `maintenanceThreshold = 8000.0`, `lastMaintenanceOdometer = 1000.0`
- **Kết quả mong đợi:**
  - Logic `if (bus.getMaintenanceThreshold() == null)` trả về false → không ghi đè
  - DB lưu đúng giá trị người dùng nhập: `odometer = 2000.0`, `maintenance_threshold = 8000.0`, `last_maintenance_odometer = 1000.0`
  - `kmSinceLastMaintenance = 2000.0 - 1000.0 = 1000.0`

---

### TC_BUS_005 — Cập nhật xe: Chặn chuyển sang REPAIRING khi xe đang có chuyến ACTIVE

- **Mã TC:** TC_BUS_005
- **Tên Kịch Bản:** Admin cố tình chuyển trạng thái xe sang `REPAIRING` nhưng xe đang có chuyến ở trạng thái `ACTIVE`
- **Điều kiện tiên quyết:**
  - Xe ID=1 (biển số "29A-001.01") đang có trạng thái `READY`
  - Có ít nhất 1 chuyến đi với `bus.id = 1` và `status = ACTIVE` trong DB
- **Các bước thực hiện:**
  1. `GET /admin/buses/edit/1` — form render đúng
  2. Thay đổi `status = REPAIRING`, giữ nguyên các trường khác
  3. `POST /admin/buses/edit/1`
- **Kết quả mong đợi:**
  - Trong `BusService.saveBus()`: `bus.getId() != null && bus.getStatus() == BusStatus.REPAIRING` → TRUE
  - `tripRepository.existsByBusIdAndStatusIn(1L, [ACTIVE, PENDING_APPROVAL])` → TRUE
  - `throw new RuntimeException("Không thể chuyển trạng thái xe sang bảo trì vì xe đang được phân công cho các chuyến xe đang hoạt động hoặc chờ duyệt!")`
  - Controller bắt Exception → `flash[error]` = `"Lỗi: Không thể chuyển trạng thái xe sang bảo trì vì xe đang được phân công cho các chuyến xe đang hoạt động hoặc chờ duyệt!"`
  - Redirect về `/admin/buses`, trạng thái xe trong DB KHÔNG thay đổi

---

### TC_BUS_006 — Cập nhật xe: Chặn chuyển sang REPAIRING khi xe đang có chuyến PENDING_APPROVAL

- **Mã TC:** TC_BUS_006
- **Tên Kịch Bản:** Admin cố tình chuyển trạng thái xe sang `REPAIRING` nhưng xe đang có chuyến `PENDING_APPROVAL`
- **Điều kiện tiên quyết:**
  - Xe ID=2 đang ở trạng thái `READY`
  - Có chuyến đi với `bus.id = 2` và `status = PENDING_APPROVAL`
- **Các bước thực hiện:**
  1. `POST /admin/buses/edit/2` với `status = REPAIRING`
- **Kết quả mong đợi:**
  - `tripRepository.existsByBusIdAndStatusIn(2L, [ACTIVE, PENDING_APPROVAL])` → TRUE (match PENDING_APPROVAL)
  - Throw RuntimeException với cùng thông báo như TC_BUS_005
  - `flash[error]` xuất hiện, DB không thay đổi

---

### TC_BUS_007 — Cập nhật xe: Cho phép chuyển sang REPAIRING khi xe KHÔNG có chuyến đang chạy

- **Mã TC:** TC_BUS_007
- **Tên Kịch Bản:** Admin chuyển xe sang `REPAIRING` thành công khi xe không có chuyến `ACTIVE` hoặc `PENDING_APPROVAL`
- **Điều kiện tiên quyết:**
  - Xe ID=5 đang ở trạng thái `READY`
  - Tất cả chuyến của xe ID=5 (nếu có) đều ở trạng thái `CANCELLED` hoặc `COMPLETED`
- **Các bước thực hiện:**
  1. `POST /admin/buses/edit/5` với `status = REPAIRING`
- **Kết quả mong đợi:**
  - `tripRepository.existsByBusIdAndStatusIn(5L, [ACTIVE, PENDING_APPROVAL])` → FALSE
  - `busRepository.save(bus)` thành công
  - `flash[success]` = `"Cập nhật thông tin xe thành công!"`
  - DB: bảng `buses`, row id=5, cột `status = 'REPAIRING'`

---

### TC_BUS_008 — Xóa xe: Chặn cứng khi xe đã từng gắn với bất kỳ chuyến nào

- **Mã TC:** TC_BUS_008
- **Tên Kịch Bản:** Admin cố xóa xe đã từng được phân công cho chuyến đi (dù chuyến đó đã COMPLETED hay CANCELLED)
- **Điều kiện tiên quyết:**
  - Xe ID=1 đã có ít nhất 1 chuyến đi trong bảng `trips` (`bus_id = 1`, bất kể trạng thái nào)
- **Các bước thực hiện:**
  1. `GET /admin/buses/delete/1`
- **Kết quả mong đợi:**
  - `tripRepository.existsByBusId(1L)` → TRUE
  - `throw new RuntimeException("Không thể xóa xe này vì xe đã có dữ liệu lịch sử vận hành hoặc phân công. Hãy đổi trạng thái sang bảo trì thay vì xóa cứng!")`
  - `flash[error]` = `"Lỗi: Không thể xóa xe này vì xe đã có dữ liệu lịch sử vận hành hoặc phân công. Hãy đổi trạng thái sang bảo trì thay vì xóa cứng!"`
  - Redirect về `/admin/buses`
  - DB: bản ghi xe ID=1 **vẫn còn tồn tại**, `DELETE` không được phát ra

---

### TC_BUS_009 — Xóa xe: Thành công khi xe chưa từng có chuyến nào

- **Mã TC:** TC_BUS_009
- **Tên Kịch Bản:** Admin xóa thành công một xe chưa từng được phân công cho bất kỳ chuyến đi nào
- **Điều kiện tiên quyết:**
  - Xe vừa được tạo, chưa có dòng nào trong bảng `trips` 
- **Các bước thực hiện:**
  1. `GET /admin/buses/delete/99`
- **Kết quả mong đợi:**
  - `tripRepository.existsByBusId(99L)` → FALSE
  - `busRepository.delete(bus)` được gọi → DELETE SQL thực thi
  - `flash[success]` = `"Xóa xe thành công!"`
  - Bản ghi xe ID=99 không còn tồn tại trong bảng `buses`

---

### TC_BUS_010 — Xóa xe: Báo lỗi khi ID không tồn tại

- **Mã TC:** TC_BUS_010
- **Tên Kịch Bản:** Admin gọi xóa với ID xe không tồn tại trong DB
- **Điều kiện tiên quyết:** Không có xe nào với ID=9999 trong DB
- **Các bước thực hiện:**
  1. `GET /admin/buses/delete/9999`
- **Kết quả mong đợi:**
  - `busRepository.findById(9999L)` → `Optional.empty()`
  - `orElseThrow()` ném `RuntimeException("Không tìm thấy xe cần xóa với ID: 9999")`
  - `flash[error]` = `"Lỗi: Không tìm thấy xe cần xóa với ID: 9999"`

---

### TC_BUS_011 — Hiển thị form sửa xe: Báo lỗi khi ID không tồn tại

- **Mã TC:** TC_BUS_011
- **Tên Kịch Bản:** Admin truy cập form sửa xe với ID không hợp lệ
- **Điều kiện tiên quyết:** Không có xe nào với ID=8888
- **Các bước thực hiện:**
  1. `GET /admin/buses/edit/8888`
- **Kết quả mong đợi:**
  - `busService.findById(8888L)` → `Optional.empty()`
  - `orElseThrow()` ném `RuntimeException("Không tìm thấy xe với ID: 8888")`
  - Controller catch Exception → `flash[error]` = `"Không tìm thấy xe với ID: 8888"`
  - Redirect về `/admin/buses` (KHÔNG render form)

---

<a name="module-2"></a>
## MODULE 2: QUẢN LÝ BẾN XE (Station Management)
**Controller:** `AdminStationController` — `@RequestMapping("/admin/stations")`  
**Service:** `StationService`  
**Repository:** `StationRepository`

---

### TC_STA_001 — Hiển thị danh sách bến xe

- **Mã TC:** TC_STA_001
- **Tên Kịch Bản:** Hiển thị danh sách tất cả bến xe
- **Điều kiện tiên quyết:** `DataInitializer` đã seed 7 bến xe (Hà Nội, Hải Phòng, Sài Gòn, Đà Lạt, Đà Nẵng, Huế, Cần Thơ)
- **Các bước thực hiện:**
  1. `GET /admin/stations`
- **Kết quả mong đợi:**
  - HTTP 200, template `admin/station/station-list`
  - Model attribute `stations` chứa đủ 7 bến xe
  - Mỗi bến xe hiển thị tên và địa chỉ

---

### TC_STA_002 — Tạo bến xe mới hợp lệ

- **Mã TC:** TC_STA_002
- **Tên Kịch Bản:** Admin tạo bến xe mới với thông tin đầy đủ
- **Điều kiện tiên quyết:** Không có bến xe nào tên "Bến xe Vũng Tàu"
- **Các bước thực hiện:**
  1. `GET /admin/stations/create` — model có attribute `station = new Station()`
  2. Điền form: `stationName = "Bến xe Vũng Tàu"`, `address = "Vũng Tàu"`
  3. `POST /admin/stations/create`
- **Kết quả mong đợi:**
  - `stationService.save(station)` gọi `stationRepository.save(station)` thành công
  - INSERT vào bảng `stations`
  - `flash[success]` = `"Thêm mới bến xe thành công!"`
  - Redirect về `/admin/stations`

---

### TC_STA_003 — Cập nhật thông tin bến xe (lỗi1)

- **Mã TC:** TC_STA_003
- **Tên Kịch Bản:** Admin sửa tên và địa chỉ của bến xe đã tồn tại
- **Điều kiện tiên quyết:** Bến xe ID=1 (Bến xe Mỹ Đình) tồn tại trong DB
- **Các bước thực hiện:**
  1. `GET /admin/stations/edit/1`
  2. Đổi `stationName = "Bến xe Mỹ Đình (Mới)"`, `address = "Nam Từ Liêm, Hà Nội"`
  3. `POST /admin/stations/edit/1`
- **Kết quả mong đợi:**
  - `station.setId(1L)` được gọi trước khi save (merge UPDATE)
  - DB: row ID=1 được cập nhật đúng giá trị mới
  - `flash[success]` = `"Cập nhật thông tin bến xe thành công!"`

---

### TC_STA_004 — Xóa bến xe: Chặn khi bến đang nằm trong lộ trình tuyến đường

- **Mã TC:** TC_STA_004
- **Tên Kịch Bản:** Admin cố xóa bến xe đang là trạm dừng của ít nhất một tuyến đường
- **Điều kiện tiên quyết:**
  - Bến xe ID=1 ("Bến xe Mỹ Đình") đang nằm trong `routeStations` của tuyến "Hà Nội → Sài Gòn"
  - `station.getRouteStations()` trả về danh sách không rỗng
- **Các bước thực hiện:**
  1. `GET /admin/stations/delete/1`
- **Kết quả mong đợi:**
  - `StationService.deleteById(1L)`: tìm thấy station → kiểm tra `station.getRouteStations() != null && !station.getRouteStations().isEmpty()` → TRUE
  - `throw new RuntimeException("Không thể xóa bến xe này vì đang thuộc lộ trình của một số tuyến đường hiện hành!")`
  - `flash[error]` = `"Lỗi: Không thể xóa bến xe này vì đang thuộc lộ trình của một số tuyến đường hiện hành!"`
  - DB: bản ghi bến xe ID=1 **vẫn còn tồn tại**

---

### TC_STA_005 — Xóa bến xe: Thành công khi bến không thuộc tuyến nào

- **Mã TC:** TC_STA_005
- **Tên Kịch Bản:** Admin xóa thành công bến xe chưa được gắn vào bất kỳ tuyến nào
- **Điều kiện tiên quyết:** Bến xe ID=50 (vừa tạo mới) có `routeStations` rỗng hoặc null
- **Các bước thực hiện:**
  1. `GET /admin/stations/delete/50`
- **Kết quả mong đợi:**
  - `station.getRouteStations().isEmpty()` → TRUE → không throw Exception
  - `stationRepository.delete(station)` được gọi
  - `flash[success]` = `"Xóa bến xe thành công!"`
  - Bản ghi bến xe ID=50 bị xóa khỏi bảng `stations`

---

### TC_STA_006 — Xóa bến xe: Báo lỗi khi ID không tồn tại

- **Mã TC:** TC_STA_006
- **Tên Kịch Bản:** Admin gọi xóa bến xe với ID không tồn tại
- **Điều kiện tiên quyết:** Không có bến xe với ID=7777
- **Các bước thực hiện:**
  1. `GET /admin/stations/delete/7777`
- **Kết quả mong đợi:**
  - `stationRepository.findById(7777L)` → `Optional.empty()`
  - `throw new RuntimeException("Không tìm thấy bến xe cần xóa với ID: 7777")`
  - `flash[error]` = `"Lỗi: Không tìm thấy bến xe cần xóa với ID: 7777"`

---

<a name="module-3"></a>
## MODULE 3: QUẢN LÝ CHUYẾN XE THỦ CÔNG & MÁY TRẠNG THÁI (FSM)
**Controller:** `AdminTripManagementController` — `@RequestMapping("/admin/trip-management")`  
**Service:** `TripService`  
**REST Controller:** `TripRestController` — `@RequestMapping("/api/admin/trips")`

---

### NHÓM 3.1: ENDPOINT LỌC ĐỘNG TÀI NGUYÊN RẢNHí

### TC_FSM_001 — API tài nguyên rảnh: Happy path với khung giờ hợp lệ

- **Mã TC:** TC_FSM_001
- **Tên Kịch Bản:** Gọi API lọc xe và tài xế rảnh với thời gian departure < arrival hợp lệ
- **Điều kiện tiên quyết:**
  - Có ít nhất 5 xe READY không bị trùng lịch trong khung giờ test
  - Có ít nhất 5 tài xế active, bằng lái còn hạn, tổng giờ lái trong ngày + duration <= 8h
- **Các bước thực hiện:**
  1. `GET /api/admin/trips/available-resources?departure=2026-09-01T08:00&arrival=2026-09-01T14:00`
- **Kết quả mong đợi:**
  - HTTP 200 OK
  - Response JSON: `{ "buses": [...], "drivers": [...] }`
  - `buses` array: chỉ chứa xe READY, trạng thái hiện tại không phải REPAIRING/TRAVELING, không bận trong window `[07:00, 15:00]` (departure-1h đến arrival+1h), chưa quá hạn/sắp quá hạn bảo trì
  - `drivers` array: chỉ chứa tài xế active, bằng lái còn hạn, tổng giờ lái hôm đó + 6h <= 8h, không bận trong window `[07:30, 14:30]` (departure-30p đến arrival+30p)
  - Mỗi bus DTO chứa: `id`, `licensePlate`, `typeName`, `capacity`, `brand`
  - Mỗi driver DTO chứa: `userId`, `fullName`, `licenseNumber`, `experienceYears`
  - Xe và tài xế được sắp xếp theo `kmSinceLastMaintenance` / `totalDrivingHours24h` tăng dần

---

### TC_FSM_002 — API tài nguyên rảnh: Arrival trước Departure → HTTP 400

- **Mã TC:** TC_FSM_002
- **Tên Kịch Bản:** Gọi API với thời gian arrival ≤ departure — hệ thống phải trả lỗi ngay
- **Điều kiện tiên quyết:** Không cần điều kiện tiên quyết đặc biệt
- **Các bước thực hiện (Case 1 — arrival trước departure):**
  1. `GET /api/admin/trips/available-resources?departure=2026-09-01T14:00&arrival=2026-09-01T08:00`
- **Các bước thực hiện (Case 2 — arrival bằng departure):**
  2. `GET /api/admin/trips/available-resources?departure=2026-09-01T08:00&arrival=2026-09-01T08:00`
- **Kết quả mong đợi (cả 2 case):**
  - Điều kiện `arrival.isBefore(departure) || arrival.isEqual(departure)` → TRUE
  - `ResponseEntity.badRequest()` → HTTP 400
  - Body: `{ "error": "Thời gian đến phải sau thời gian khởi hành" }`
  - **KHÔNG** có bất kỳ query nào đến DB được thực thi

---

### TC_FSM_003 — API tài nguyên rảnh: Xác nhận xe đang bận KHÔNG xuất hiện trong kết quả

- **Mã TC:** TC_FSM_003
- **Tên Kịch Bản:** Xe đang có chuyến ACTIVE trong khung giờ được lọc không được xuất hiện trong danh sách trả về
- **Điều kiện tiên quyết:**
  - Xe "29A-001.01" (ID=1) đang có chuyến ACTIVE từ `2026-06-23T06:00` đến `2026-06-23T10:00`
  - Các xe khác không bị trùng lịch
- **Các bước thực hiện:**
  1. `GET /api/admin/trips/available-resources?departure=2026-06-23T06:00&arrival=2026-06-23T10:00`
- **Kết quả mong đợi:**
  - `isBusBusy(busID1, windowStart=08:00, windowEnd=14:00, null)` → TRUE
  - Xe "29A-001.01" **KHÔNG** xuất hiện trong `buses` array của response
  - Các xe khác không bị trùng lịch vẫn xuất hiện bình thường
- **lưu ý:**
  - `buses` array: chỉ chứa xe READY, trạng thái hiện tại không phải REPAIRING/TRAVELING, không bận trong window `[05:00, 11:00]` (departure-1h đến arrival+1h), chưa quá hạn/sắp quá hạn bảo trì
  - `drivers` array: chỉ chứa tài xế active, bằng lái còn hạn, tổng giờ lái hôm đó + 6h <= 8h, không bận trong window `[05:30, 10:30]` (departure-30p đến arrival+30p)

---

### NHÓM 3.2: RÀNG BUỘC TẠO CHUYẾN THỦ CÔNG

### TC_FSM_004 — Tạo chuyến thủ công: Happy path hợp lệ (chuyến ngắn < 8h)

- **Mã TC:** TC_FSM_004
- **Tên Kịch Bản:** Admin tạo chuyến đi hợp lệ, thời gian < 8h, có đủ tài xế chính
- **Điều kiện tiên quyết:**
  - Route ID=1 (Hà Nội → Hải Phòng, 2h, distanceKm=120, suitableBusType=Limousine) tồn tại
  - Xe "29A-001.01" (READY, Limousine, odometer=500, threshold=5000) không bị trùng lịch
  - Tài xế "TX Rảnh 0h (A1)" (active, bằng hạn, 0h lái hôm nay) rảnh
- **Các bước thực hiện:**
  1. `GET /admin/trip-management/trips/create`
  2. Chọn: `routeId=1`, `busId=1`, `driverId=1`, `departureTime=2026-08-01T07:00`, `arrivalTimeExpected=2026-08-01T09:00`, `totalSeats=22`, `price=250000`
  3. `POST /admin/trip-management/trips/create`
- **Kết quả mong đợi:**
  - `tripService.createManualTrip(trip)` được gọi
  - `arrivalTimeExpected.isBefore(departureTime)` → FALSE → tiếp tục
  - `validateBusForTrip`: xe READY → OK; window `[06:00, 10:00]` không trùng → OK; `needsMaintenance()=false` (500<5000) → OK; `isNearMaintenance(120)=(500+120)/5000=12.4% < 90%` → OK
  - `validateStaffForTrip`: duration=2h → `requiredDrivers=ceil(2/8)=1` → có 1 tài xế chính → OK; không trùng lịch → OK; giờ lái: `0 + min(2/1, 8) = 2h <= 8h` → OK
  - `changeStatusToActive(trip)`: `status = ACTIVE`, `saleOpenedAt = now()`
  - INSERT vào bảng `trips` với `status='ACTIVE'`, `sale_opened_at=<now>`
  - `flash[success]` = `"Tạo chuyến xe thành công!"`

---

### TC_FSM_005 — Tạo chuyến thủ công: Báo lỗi khi ArrivalTime ≤ DepartureTime

- **Mã TC:** TC_FSM_005
- **Tên Kịch Bản:** Admin nhập thời gian đến dự kiến trước hoặc bằng thời gian khởi hành
- **Điều kiện tiên quyết:** Không cần điều kiện đặc biệt
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` với `departureTime=2026-08-01T14:00`, `arrivalTimeExpected=2026-08-01T10:00`
- **Kết quả mong đợi:**
  - `trip.getArrivalTimeExpected().isBefore(trip.getDepartureTime())` → TRUE
  - `throw new IllegalArgumentException("Thời gian đến dự kiến phải sau thời gian khởi hành!")`
  - Controller catch Exception → `flash[error]` = `"Lỗi: Thời gian đến dự kiến phải sau thời gian khởi hành!"`
  - Redirect về `/admin/trip-management/trips/create`

---

### TC_FSM_006 — Tạo chuyến thủ công: Chặn khi xe đang ở trạng thái REPAIRING

- **Mã TC:** TC_FSM_006
- **Tên Kịch Bản:** Admin chọn xe đang ở trạng thái `REPAIRING` để gán vào chuyến mới
- **Điều kiện tiên quyết:** Xe "51B-SUA.XE" có `status = REPAIRING` tồn tại trong DB
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` với busId của xe REPAIRING
- **Kết quả mong đợi:**
  - `validateBusForTrip`: `bus.getStatus() == BusStatus.REPAIRING` → TRUE
  - `throw new IllegalArgumentException("Xe 51B-SUA.XE đang được bảo trì/sửa chữa, không thể gán vào chuyến!")`
  - `flash[error]` = `"Lỗi: Xe 51B-SUA.XE đang được bảo trì/sửa chữa, không thể gán vào chuyến!"`

---

### TC_FSM_007 — Tạo chuyến thủ công: Chặn khi xe đang ở trạng thái TRAVELING

- **Mã TC:** TC_FSM_007
- **Tên Kịch Bản:** Admin chọn xe đang ở trạng thái `TRAVELING` (đang chạy chuyến khác)
- **Điều kiện tiên quyết:** Xe "51B-DAG.CH" có `status = TRAVELING`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` với busId của xe TRAVELING
- **Kết quả mong đợi:**
  - `validateBusForTrip`: `bus.getStatus() == BusStatus.TRAVELING` → TRUE
  - `throw new IllegalArgumentException("Xe 51B-DAG.CH đang trên đường (TRAVELING), không thể gán vào chuyến mới cho đến khi hoàn thành chuyến hiện tại!")`
  - `flash[error]` chứa thông báo lỗi tương ứng

---

### TC_FSM_008 — Tạo chuyến thủ công: Chặn khi xe bị trùng lịch (buffer 1 giờ)

- **Mã TC:** TC_FSM_008
- **Tên Kịch Bản:** Admin chọn xe đang có chuyến khác trong khoảng thời gian, kể cả buffer 1 giờ trước/sau
- **Điều kiện tiên quyết:**
  - Xe ID=3 đang có chuyến ACTIVE: `departure=10:00`, `arrival=14:00`
  - Chuyến mới cần tạo: `departure=14:30`, `arrival=16:30` (overlap với window của chuyến cũ: arrival+1h = 15:00 > 14:30)
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` với busId=3, `departure=14:30`, `arrival=16:30`
- **Kết quả mong đợi:**
  - `windowStart = 14:30 - 1h = 13:30`, `windowEnd = 16:30 + 1h = 17:30`
  - `isBusBusy(bus3, 13:30, 17:30, null)`: chuyến cũ `departure=10:00 <= 17:30 AND arrival=14:00 >= 13:30` → TRUE (overlap)
  - `throw new IllegalArgumentException("Xe ... đang bận trong khoảng thời gian này!")`
  - `flash[error]` chứa thông báo lỗi tương ứng

---

### TC_FSM_009 — Tạo chuyến thủ công: Chặn khi xe đã quá hạn bảo trì (needsMaintenance)

- **Mã TC:** TC_FSM_009
- **Tên Kịch Bản:** Admin chọn xe đã vượt ngưỡng bảo trì (odometer - lastMaintenance >= threshold)
- **Điều kiện tiên quyết:**
  - Xe "51B-QUA.BT": `odometer=6000`, `lastMaintenanceOdometer=0`, `maintenanceThreshold=5000`
  - `kmSinceLastMaintenance = 6000 - 0 = 6000 >= 5000` → `needsMaintenance()=TRUE`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` với busId của xe "51B-QUA.BT"
- **Kết quả mong đợi:**
  - `validateBusForTrip`: `bus.needsMaintenance()` → TRUE
  - `throw new IllegalArgumentException("Xe 51B-QUA.BT (Odo: 6000km) đã QUÁ HẠN bảo trì (ngưỡng: 5000km) — không thể gán vào chuyến cho đến khi được bảo trì!")`
  - `flash[error]` chứa thông báo lỗi tương ứng

---

### TC_FSM_010 — Tạo chuyến thủ công: Chặn khi xe sẽ sắp/quá hạn bảo trì sau chuyến (isNearMaintenance)

- **Mã TC:** TC_FSM_010
- **Tên Kịch Bản:** Xe sắp đến hạn bảo trì sau khi chạy thêm quãng đường của chuyến mới
- **Điều kiện tiên quyết:**
  - Xe "51B-SAT.BT": `odometer=4995`, `lastMaintenanceOdometer=0`, `maintenanceThreshold=5000`
  - `kmSinceLastMaintenance = 4995`, `NEAR_MAINTENANCE_RATIO = 0.9`
  - Route có `distanceKm = 120`
  - `isNearMaintenance(120)`: `(4995 + 120) = 5115 >= 5000 * 0.9 = 4500` → TRUE
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` với busId của xe "51B-SAT.BT", routeId=1 (120km)
- **Kết quả mong đợi:**
  - `validateBusForTrip`: `bus.isNearMaintenance(120)` → TRUE
  - `throw new IllegalArgumentException("Xe 51B-SAT.BT (Odo: 4995km) sẽ SẮP/QUÁ ngưỡng bảo trì (5000km) sau chuyến này — vui lòng chọn xe khác hoặc đưa xe đi bảo trì trước!")`
  - `flash[error]` chứa thông báo lỗi tương ứng

---

### TC_FSM_011 — Ràng buộc nhân sự: Chặn khi không có tài xế chính

- **Mã TC:** TC_FSM_011
- **Tên Kịch Bản:** Admin tạo chuyến mà không chọn tài xế chính (driverId bị thiếu/null)
- **Điều kiện tiên quyết:** Xe hợp lệ, tuyến hợp lệ
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` không có `driverId`
- **Kết quả mong đợi:**
  - `validateStaffForTrip`: `driver == null` → TRUE
  - `throw new IllegalArgumentException("Chuyến xe bắt buộc phải có tài xế chính!")`
  - `flash[error]` = `"Lỗi: Chuyến xe bắt buộc phải có tài xế chính!"`

---

### TC_FSM_012 — Ràng buộc nhân sự: Chặn khi số tài xế không đủ cho chuyến dài (> 8h)

- **Mã TC:** TC_FSM_012
- **Tên Kịch Bản:** Chuyến xe kéo dài 16h nhưng chỉ có 1 tài xế chính, thiếu tài xế phụ
- **Điều kiện tiên quyết:**
  - Route "Hà Nội → Sài Gòn" (duration=1800 phút = 30h)
  - Xe "51B-MOI.00" READY, tài xế chính hợp lệ, không có coDrivers
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` với route HN→SG, 1 tài xế chính, không có coDriver
  2. `departureTime=2026-08-01T07:00`, `arrivalTimeExpected=2026-08-02T13:00` (30h)
- **Kết quả mong đợi:**
  - `durationHours = 30.0`, `requiredDrivers = ceil(30/8) = 4`
  - `assignedDriversCount = 1 (chỉ driver chính)` < `requiredDrivers = 4`
  - `throw new IllegalArgumentException("Chuyến xe kéo dài 30.0h, yêu cầu ít nhất 4 tài xế lái xe, nhưng hiện tại chỉ gán 1 người!")`
  - `flash[error]` chứa thông báo lỗi tương ứng

---

### TC_FSM_013 — Ràng buộc nhân sự: Chặn khi chuyến > 8h nhưng không có phụ xe

- **Mã TC:** TC_FSM_013
- **Tên Kịch Bản:** Chuyến dài 16h có đủ tài xế chính + phụ nhưng thiếu phụ xe (assistant)
- **Điều kiện tiên quyết:**
  - Chuyến kéo dài 16h (`durationHours = 16.0`)
  - Có 2 tài xế (chính + 1 coDriver), `assistantId = null`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` với 16h route, 2 tài xế, không có assistant
- **Kết quả mong đợi:**
  - `requiredDrivers = ceil(16/8) = 2` → OK (đủ tài xế)
  - `durationHours = 16 > 8.0 && assistant == null` → TRUE
  - `throw new IllegalArgumentException("Chuyến xe kéo dài trên 8 tiếng bắt buộc phải có phụ xe!")`
  - `flash[error]` chứa thông báo lỗi tương ứng

---

### TC_FSM_014 — Ràng buộc nhân sự: Chặn khi tài xế chính trùng với tài xế phụ

- **Mã TC:** TC_FSM_014
- **Tên Kịch Bản:** Admin chọn cùng 1 người làm cả tài xế chính lẫn tài xế phụ (coDriver)
- **Điều kiện tiên quyết:** Tài xế ID=5 tồn tại trong DB
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` với `driverId=5`, `coDriverIds=[5]`
- **Kết quả mong đợi:**
  - `validateStaffForTrip`: kiểm tra coDriver.getUserId().equals(driver.getUserId()) → TRUE
  - `throw new IllegalArgumentException("Nhân sự trùng lặp: Tài xế phụ TX Rảnh 0h (A1) đang là tài xế chính của chuyến này!")`
  - `flash[error]` chứa thông báo tương ứng

---

### TC_FSM_015 — Ràng buộc nhân sự: Chặn khi phụ xe trùng với tài xế chính

- **Mã TC:** TC_FSM_015
- **Tên Kịch Bản:** Admin chọn cùng 1 người làm tài xế chính và phụ xe (assistant)
- **Điều kiện tiên quyết:** Tài xế ID=5 và tài xế ID=6 tồn tại, chuyến kéo dài > 8h
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` với `driverId=5`, `assistantId=5` (cùng người)
- **Kết quả mong đợi:**
  - `validateStaffForTrip`: `assistant.getUserId().equals(driver.getUserId())` → TRUE
  - `throw new IllegalArgumentException("Nhân sự trùng lặp: Phụ xe TX... đang là tài xế chính của chuyến này!")`

---

### TC_FSM_016 — Ràng buộc nhân sự: Chặn tài xế chính bận ở chuyến khác (30-phút buffer)

- **Mã TC:** TC_FSM_016
- **Tên Kịch Bản:** Tài xế chính đang có chuyến khác kết thúc lúc 14:00, chuyến mới bắt đầu lúc 14:15 (trong buffer 30 phút)
- **Điều kiện tiên quyết:**
  - Tài xế ID=1 có chuyến ACTIVE kết thúc lúc `14:00`
  - Chuyến mới: `departure=14:15`, `arrival=16:15`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` với driverId=1, departure=14:15, arrival=16:15
- **Kết quả mong đợi:**
  - `windowStart = 14:15 - 30min = 13:45`, `windowEnd = 16:15 + 30min = 16:45`
  - `isDriverBusyInWindow(driver1, 13:45, 16:45, null)`: chuyến cũ `arrival=14:00 > 13:45` → overlap → TRUE
  - `throw new IllegalArgumentException("Tài xế chính TX Rảnh 0h (A1) đang bận ở chuyến khác!")`

---

### TC_FSM_017 — Ràng buộc nhân sự: Chặn khi tổng giờ lái trong ngày vượt 8h

- **Mã TC:** TC_FSM_017
- **Tên Kịch Bản:** Tài xế đã lái 7h trong ngày, chuyến mới thêm 2h sẽ vượt 8h
- **Điều kiện tiên quyết:**
  - Tài xế "TX Đã lái 7h" (username="tx_7h"): `totalDrivingHours24h = 7.0`
  - Chuyến mới kéo dài 2h, ngày hôm nay
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` với driverId của "TX Đã lái 7h", departure=TODAY+1h, arrival=TODAY+3h
- **Kết quả mong đợi:**
  - `getDrivingHoursForDate(driver, today)`: `hoursFromTrips + totalDrivingHours24h = 0 + 7.0 = 7.0`
  - `effectiveHours = min(2h/1driver, 8) = 2.0`
  - `7.0 + 2.0 = 9.0 > 8.0` → FALSE (điều kiện `<= 8.0` không thỏa)
  - `throw new IllegalArgumentException("Tài xế chính TX Đã lái 7h đã được phân công lái 7.0h trong ngày ..., thêm chuyến này (2.0h) sẽ vượt 8h/ngày!")`

---

### TC_FSM_018 — Ràng buộc nhân sự: Chặn tài xế bằng lái đã hết hạn

- **Mã TC:** TC_FSM_018
- **Tên Kịch Bản:** Admin chọn tài xế có bằng lái đã hết hạn
- **Điều kiện tiên quyết:** Tài xế "TX Bằng Lái Đã Hết" có `licenseExpiryDate = today - 5 ngày`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/create` với driverId của "TX Bằng Lái Đã Hết"
- **Kết quả mong đợi:**
  - `validateStaffForTrip`: `driver.isLicenseValid()` → `licenseExpiryDate.isAfter(LocalDate.now())` = FALSE
  - `throw new IllegalArgumentException("Bằng lái của tài xế chính TX Bằng Lái Đã Hết đã hết hạn!")`
  - `flash[error]` chứa thông báo lỗi

---

### TC_FSM_019 — Ràng buộc nhân sự: Chặn tài xế bị khóa (isActive=false)

- **Mã TC:** TC_FSM_019
- **Tên Kịch Bản:** Admin chọn tài xế bị vô hiệu hóa (`isActive = false`)
- **Điều kiện tiên quyết:** Tài xế "TX Bị Khóa" có `isActive = false`
- **Các bước thực hiện:**
  1. Trong dropdown form tạo chuyến, select tài xế "TX Bị Khóa"
  2. `POST /admin/trip-management/trips/create`
- **Kết quả mong đợi:**
  - `validateStaffForTrip`: tài xế được load từ DB, `driver.getIsActive() = false`
  - `!Boolean.TRUE.equals(driver.getIsActive())` → TRUE → `throw new IllegalArgumentException("Tài xế chính TX Bị Khóa đã bị khóa (ngừng hoạt động)!")`
  - `flash[error]` chứa thông báo lỗi tương ứng, DB không thay đổi
  - **Đã sửa:** `validateStaffForTrip` nay kiểm tra `isActive` cho cả tài xế chính, tài xế phụ (coDriver), và phụ xe (assistant) — đồng nhất với `findBestAvailableDriver` (AI path) và `getAvailableDriversForTrip`/`getAvailableAssistantsForTrip` (dropdown thủ công), vốn đã luôn lọc theo `isActive`.

---

### NHÓM 3.3: MÁY TRẠNG THÁI FSM (TripStatus)

### TC_FSM_020 — FSM: Chuyển trạng thái hợp lệ PENDING_APPROVAL → ACTIVE

- **Mã TC:** TC_FSM_020
- **Tên Kịch Bản:** Admin chuyển chuyến từ `PENDING_APPROVAL` sang `ACTIVE` (bước hợp lệ)
- **Điều kiện tiên quyết:** Chuyến ID=10 đang ở trạng thái `PENDING_APPROVAL`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/update` với `tripId=10`, `status=ACTIVE`
- **Kết quả mong đợi:**
  - `canTransition(PENDING_APPROVAL, ACTIVE)` → TRUE (whitelist match)
  - `changeStatusToActive(trip)` được gọi → `status=ACTIVE`, `saleOpenedAt = now()`
  - DB: `trips.status = 'ACTIVE'`, `sale_opened_at` được set
  - `flash[success]` = `"Cập nhật chuyến xe thành công!"`

---

### TC_FSM_021 — FSM: Chuyển trạng thái hợp lệ PENDING_APPROVAL → CANCELLED

- **Mã TC:** TC_FSM_021
- **Tên Kịch Bản:** Admin hủy chuyến đang ở `PENDING_APPROVAL`
- **Điều kiện tiên quyết:** Chuyến ID=11 ở trạng thái `PENDING_APPROVAL`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/cancel/11`
- **Kết quả mong đợi:**
  - `canTransition(PENDING_APPROVAL, CANCELLED)` → TRUE
  - `trip.setStatus(CANCELLED)` (không qua `changeStatusToActive`)
  - DB: `status = 'CANCELLED'`
  - `flash[success]` = `"Hủy chuyến thành công!"`

---

### TC_FSM_022 — FSM: Chuyển trạng thái hợp lệ ACTIVE → DEPARTED và đồng bộ BusStatus

- **Mã TC:** TC_FSM_022
- **Tên Kịch Bản:** Chuyển chuyến từ `ACTIVE` sang `DEPARTED` — xe phải tự động chuyển sang `TRAVELING`
- **Điều kiện tiên quyết:**
  - Chuyến ID=1 ở trạng thái `ACTIVE`, được gắn xe ID=1 (đang `READY`)
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/update` với `tripId=1`, `status=DEPARTED`
- **Kết quả mong đợi:**
  - `canTransition(ACTIVE, DEPARTED)` → TRUE
  - `trip.setStatus(DEPARTED)`
  - **Đồng bộ BusStatus:** `newStatus == TripStatus.DEPARTED` → `trip.getBus().setStatus(BusStatus.TRAVELING)` → `busRepository.save(bus)`
  - DB kiểm tra 2 bảng:
    - `trips`: `status = 'DEPARTED'`
    - `buses` (row id=1): `status = 'TRAVELING'`
  - `flash[success]` = `"Cập nhật chuyến xe thành công!"`

---

### TC_FSM_023 — FSM: Chuyển trạng thái hợp lệ DEPARTED → COMPLETED và đồng bộ BusStatus

- **Mã TC:** TC_FSM_023
- **Tên Kịch Bản:** Chuyển chuyến từ `DEPARTED` sang `COMPLETED` — xe phải tự động chuyển về `READY`
- **Điều kiện tiên quyết:**
  - Chuyến ID=2 ở trạng thái `DEPARTED`, được gắn xe ID=2 (đang `TRAVELING`)
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/update` với `tripId=2`, `status=COMPLETED`
- **Kết quả mong đợi:**
  - `canTransition(DEPARTED, COMPLETED)` → TRUE
  - `trip.setStatus(COMPLETED)`
  - **Đồng bộ BusStatus:** `newStatus == TripStatus.COMPLETED` → `trip.getBus().setStatus(BusStatus.READY)` → `busRepository.save(bus)`
  - DB kiểm tra:
    - `trips`: `status = 'COMPLETED'`
    - `buses` (row id=2): `status = 'READY'` ← **quan trọng nhất**
  - Xe sẵn sàng được tái sử dụng cho chuyến tiếp theo

---

### TC_FSM_024 — FSM: Chuyển trạng thái ACTIVE → CANCELLED hợp lệ

- **Mã TC:** TC_FSM_024
- **Tên Kịch Bản:** Admin hủy chuyến đang ở `ACTIVE`
- **Điều kiện tiên quyết:** Chuyến ID=3 ở trạng thái `ACTIVE`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/cancel/3`
- **Kết quả mong đợi:**
  - `canTransition(ACTIVE, CANCELLED)` → TRUE
  - `trip.setStatus(CANCELLED)`, không đồng bộ BusStatus (chỉ DEPARTED mới trigger)
  - DB: `status = 'CANCELLED'`

---

### TC_FSM_025 — FSM: Chặn chuyển trạng thái COMPLETED → CANCELLED (Terminal State)

- **Mã TC:** TC_FSM_025
- **Tên Kịch Bản:** Admin cố hủy chuyến đã `COMPLETED` — FSM phải từ chối
- **Điều kiện tiên quyết:** Chuyến ID=5 ở trạng thái `COMPLETED`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/cancel/5`
- **Kết quả mong đợi:**
  - `canTransition(COMPLETED, CANCELLED)` → `switch(COMPLETED)` → `default -> false` → FALSE
  - `throw new IllegalStateException("Lỗi luồng vận hành: Không thể chuyển trạng thái chuyến xe từ [COMPLETED] sang [CANCELLED].")`
  - Controller catch `IllegalStateException` → `flash[error]` = `"Không thể hủy: Lỗi luồng vận hành: Không thể chuyển trạng thái chuyến xe từ [COMPLETED] sang [CANCELLED]."`
  - DB: trạng thái chuyến ID=5 KHÔNG thay đổi

---

### TC_FSM_026 — FSM: Chặn chuyển trạng thái COMPLETED → ACTIVE (Invalid Backward Transition)

- **Mã TC:** TC_FSM_026
- **Tên Kịch Bản:** Admin cố kích hoạt lại chuyến đã `COMPLETED`
- **Điều kiện tiên quyết:** Chuyến ID=6 ở trạng thái `COMPLETED`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/update` với `tripId=6`, `status=ACTIVE`
- **Kết quả mong đợi:**
  - `updateTripStatus(6, ACTIVE)` được gọi
  - `canTransition(COMPLETED, ACTIVE)` → FALSE
  - `throw new IllegalStateException("Lỗi luồng vận hành: Không thể chuyển trạng thái chuyến xe từ [COMPLETED] sang [ACTIVE].")`
  - Controller catch `IllegalStateException` → `flash[error]` = `"Không thể đổi trạng thái: Lỗi luồng vận hành: ..."`
  - Redirect về form edit, DB không thay đổi

---

### TC_FSM_027 — FSM: Chặn chuyển CANCELLED → ACTIVE (Terminal State)

- **Mã TC:** TC_FSM_027
- **Tên Kịch Bản:** Admin cố kích hoạt lại chuyến đã `CANCELLED`
- **Điều kiện tiên quyết:** Chuyến ID=7 ở trạng thái `CANCELLED`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/update` với `tripId=7`, `status=ACTIVE`
- **Kết quả mong đợi:**
  - `canTransition(CANCELLED, ACTIVE)` → `switch(CANCELLED)` → `default -> false` → FALSE
  - `throw new IllegalStateException(...)` với thông báo tương ứng
  - DB không thay đổi

---

### TC_FSM_028 — FSM: Chặn chuyển DEPARTED → ACTIVE (Invalid)

- **Mã TC:** TC_FSM_028
- **Tên Kịch Bản:** Admin cố chuyển ngược chuyến từ `DEPARTED` về `ACTIVE`
- **Điều kiện tiên quyết:** Chuyến ID=8 ở trạng thái `DEPARTED`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/update` với `tripId=8`, `status=ACTIVE`
- **Kết quả mong đợi:**
  - `canTransition(DEPARTED, ACTIVE)` → `switch(DEPARTED) → to==COMPLETED` chỉ TRUE với COMPLETED → FALSE
  - Ném `IllegalStateException`, DB không thay đổi

---

### TC_FSM_029 — FSM: Giữ nguyên trạng thái (Self-Transition)

- **Mã TC:** TC_FSM_029
- **Tên Kịch Bản:** Cập nhật chuyến mà không thay đổi trạng thái — FSM cho phép
- **Điều kiện tiên quyết:** Chuyến ID=1 ở trạng thái `ACTIVE`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/update` với `status=ACTIVE` (giữ nguyên)
- **Kết quả mong đợi:**
  - Trong controller: `existingTrip.getStatus() != newStatus` → FALSE → `updateTripStatus` KHÔNG được gọi
  - Chỉ `updateManualTrip` chạy → lưu thông tin chuyến bình thường
  - DB: trạng thái vẫn là `ACTIVE`

---

### NHÓM 3.4: XÓA MỀM CHUYẾN XE (Soft-Delete)

### TC_DEL_001 — Soft Delete: Chặn xóa chuyến đang ở trạng thái DEPARTED

- **Mã TC:** TC_DEL_001
- **Tên Kịch Bản:** Admin cố xóa chuyến đang `DEPARTED` (xe đang trên đường)
- **Điều kiện tiên quyết:** Chuyến ID=20 ở trạng thái `DEPARTED`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/delete/20`
- **Kết quả mong đợi:**
  - `switch(DEPARTED)` → `throw new IllegalStateException("Chuyến #20 đang trên đường (DEPARTED). Không thể xóa chuyến đang vận hành — sẽ phá vỡ hành trình thực tế và dữ liệu GPS.")`
  - Controller catch `IllegalStateException` → `flash[error]` = `"⛔ Không thể xóa: Chuyến #20 đang trên đường (DEPARTED). ..."`
  - DB: `is_deleted = false` KHÔNG thay đổi, bản ghi vẫn còn

---

### TC_DEL_002 — Soft Delete: Chặn xóa chuyến đã COMPLETED

- **Mã TC:** TC_DEL_002
- **Tên Kịch Bản:** Admin cố xóa chuyến đã `COMPLETED`
- **Điều kiện tiên quyết:** Chuyến ID=21 ở trạng thái `COMPLETED`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/delete/21`
- **Kết quả mong đợi:**
  - `switch(COMPLETED)` → `throw new IllegalStateException("Chuyến #21 đã hoàn thành (COMPLETED). Dữ liệu lịch sử và báo cáo tài chính phải được giữ nguyên, không thể xóa.")`
  - `flash[error]` = `"⛔ Không thể xóa: Chuyến #21 đã hoàn thành (COMPLETED). ..."`

---

### TC_DEL_003 — Soft Delete: Chặn xóa chuyến ACTIVE đã có vé bán

- **Mã TC:** TC_DEL_003
- **Tên Kịch Bản:** Admin cố xóa chuyến `ACTIVE` đã bán được vé
- **Điều kiện tiên quyết:** Chuyến ID=22: `status=ACTIVE`, `ticketsSold=5`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/delete/22`
- **Kết quả mong đợi:**
  - `switch(ACTIVE)` → `trip.getTicketsSold() = 5 > 0` → TRUE
  - `throw new IllegalStateException("Chuyến #22 đang ACTIVE và đã có 5 vé bán ra. Vui lòng dùng chức năng 'Hủy chuyến' thay vì xóa để hệ thống xử lý hoàn vé và thông báo cho hành khách.")`
  - `flash[error]` chứa thông báo hướng dẫn dùng tính năng "Hủy chuyến"

---

### TC_DEL_004 — Soft Delete: Cho phép xóa chuyến ACTIVE chưa bán vé

- **Mã TC:** TC_DEL_004
- **Tên Kịch Bản:** Admin xóa thành công chuyến `ACTIVE` chưa có vé bán
- **Điều kiện tiên quyết:** Chuyến ID=23: `status=ACTIVE`, `ticketsSold=0`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/delete/23`
- **Kết quả mong đợi:**
  - `switch(ACTIVE)` → `ticketsSold = 0` → KHÔNG throw exception
  - `tripRepository.delete(trip)` được gọi
  - **@SQLDelete kích hoạt:** SQL thực thi là `UPDATE trips SET is_deleted = true WHERE id = 23` (KHÔNG phải DELETE)
  - DB: `trips.is_deleted = true` cho row id=23
  - `flash[success]` = `"✅ Đã xóa chuyến xe #23 thành công."`
  - **Kiểm tra @SQLRestriction:** `GET /admin/trip-management/trips` → chuyến #23 KHÔNG xuất hiện trong danh sách (Hibernate tự thêm `WHERE is_deleted = false`)

---

### TC_DEL_005 — Soft Delete: Cho phép xóa chuyến PENDING_APPROVAL

- **Mã TC:** TC_DEL_005
- **Tên Kịch Bản:** Admin xóa mềm chuyến đang ở `PENDING_APPROVAL` (bản nháp AI/Admin)
- **Điều kiện tiên quyết:** Chuyến ID=24: `status=PENDING_APPROVAL`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/delete/24`
- **Kết quả mong đợi:**
  - `switch(PENDING_APPROVAL)` → `case PENDING_APPROVAL, CANCELLED -> { /* proceed */ }`
  - `tripRepository.delete(trip)` → `UPDATE trips SET is_deleted = true WHERE id = 24`
  - `flash[success]` = `"✅ Đã xóa chuyến xe #24 thành công."`
  - Chuyến #24 không xuất hiện trong bất kỳ SELECT nào (do `@SQLRestriction`)

---

### TC_DEL_006 — Soft Delete: Cho phép xóa chuyến CANCELLED

- **Mã TC:** TC_DEL_006
- **Tên Kịch Bản:** Admin dọn dẹp chuyến đã `CANCELLED`
- **Điều kiện tiên quyết:** Chuyến ID=25: `status=CANCELLED`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/delete/25`
- **Kết quả mong đợi:**
  - `switch(CANCELLED)` → cho phép xóa mềm
  - `UPDATE trips SET is_deleted = true WHERE id = 25` thực thi thành công
  - `flash[success]` hiển thị thành công

---

### TC_DEL_007 — Soft Delete: Báo lỗi khi Trip ID không tồn tại

- **Mã TC:** TC_DEL_007
- **Tên Kịch Bản:** Admin gọi xóa với Trip ID không tồn tại trong DB
- **Điều kiện tiên quyết:** Không có chuyến nào với ID=9999
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/delete/9999`
- **Kết quả mong đợi:**
  - `tripRepository.findById(9999L)` → `Optional.empty()`
  - `throw new EntityNotFoundException("Không tìm thấy chuyến xe #9999")`
  - Controller catch `EntityNotFoundException` → `flash[error]` = `"❌ Không tìm thấy chuyến xe #9999"`

---

### TC_DEL_008 — Cập nhật chuyến: Chặn khi totalSeats < ticketsSold

- **Mã TC:** TC_DEL_008
- **Tên Kịch Bản:** Admin cố cập nhật số ghế xuống thấp hơn số vé đã bán
- **Điều kiện tiên quyết:** Chuyến ID=1: `ticketsSold=37`, `totalSeats=40`
- **Các bước thực hiện:**
  1. `POST /admin/trip-management/trips/update` với `totalSeats=30` (< 37 vé đã bán)
- **Kết quả mong đợi:**
  - `updateManualTrip`: `existingTrip.getTotalSeats() = 30 < existingTrip.getTicketsSold() = 37` → TRUE
  - `throw new IllegalArgumentException("Tổng số ghế (30) không được nhỏ hơn số vé đã bán (37)!")`
  - `flash[error]` chứa thông báo tương ứng

---

<a name="module-4"></a>
## MODULE 4: HỆ THỐNG AI ĐỀ XUẤT CHUYẾN TĂNG CƯỜNG (AI Scheduler)
**Service:** `TripService` — `@Scheduled(fixedRate = 10_000)`  
**Method chính:** `scanAndSuggestExtraTrips()`, `isHotTrip()`, `createExtraTrip()`, `autoAssignResources()`

---

### NHÓM 4.1: CÁC CỔNG CHẶN isHotTrip()

### TC_AI_001 — isHotTrip: Chặn khi tỷ lệ lấp đầy ≤ 90%

- **Mã TC:** TC_AI_001
- **Tên Kịch Bản:** Chuyến ACTIVE có tỷ lệ lấp đầy đúng bằng 90% — không phải "hot"
- **Điều kiện tiên quyết:** Chuyến: `ticketsSold=9`, `totalSeats=10` → `occupancyRate = 0.9` (= 90%, không > 90%)
- **Các bước thực hiện:**
  1. Chạy `scanAndSuggestExtraTrips()` (thủ công hoặc chờ 10 giây)
- **Kết quả mong đợi:**
  - `isHotTrip(trip)`: `trip.getOccupancyRate() <= 0.9` → TRUE (0.9 <= 0.9)
  - `return false` ngay ở gate đầu tiên
  - **Không** tạo chuyến tăng cường
  - **Log:** Không có dòng log nào với "HOT"

---

### TC_AI_002 — isHotTrip: Kích hoạt khi tỷ lệ lấp đầy > 90%

- **Mã TC:** TC_AI_002
- **Tên Kịch Bản:** Chuyến ACTIVE có tỷ lệ lấp đầy 92.5% (37/40) — cần kiểm tra tiếp các gate
- **Điều kiện tiên quyết:** Chuyến TRIP1 từ `DataInitializer`: `ticketsSold=37`, `totalSeats=40`, `departureTime = now() + 80h`, `saleOpenedAt = now() - 50h`
- **Các bước thực hiện:**
  1. Chờ 10 giây cho cron job chạy
- **Kết quả mong đợi:**
  - Gate 1: `0.925 > 0.9` → TRUE, tiếp tục
  - Gate 3: `departureTime.isAfter(now)` → TRUE, tiếp tục
  - Gate 4: `hoursUntilDeparture = 80 >= 72` → TRUE, tiếp tục
  - Gate 5: `occupancyRate = 0.925 < 0.95` → kiểm tra hoursOnSale: `50h >= 48h` → TRUE, tiếp tục
  - `isHotTrip` → `return true` ✅
  - Log: `"🔥 [AI] Chuyến #1 HOT: 92.5% ghế, còn 80 giờ, đã mở bán 50 giờ."`

---

### TC_AI_003 — isHotTrip: Gate 3 — Chặn khi chuyến đã khởi hành (departureTime trong quá khứ)

- **Mã TC:** TC_AI_003
- **Tên Kịch Bản:** Chuyến hot về tỷ lệ (95%) nhưng đã khởi hành
- **Điều kiện tiên quyết:** Chuyến: `occupancyRate = 0.96`, `departureTime = now() - 2h` (đã qua)
- **Các bước thực hiện:**
  1. Chạy `scanAndSuggestExtraTrips()`
- **Kết quả mong đợi:**
  - Gate 1: `0.96 > 0.9` → TRUE
  - Gate 3: `trip.getDepartureTime().isAfter(now)` → FALSE (đã khởi hành)
  - `return false`
  - Log: `"⏩ [AI] Chuyến #X: đã khởi hành, bỏ qua."`
  - Không tạo chuyến tăng cường

---

### TC_AI_004 — isHotTrip: Gate 4 — Chặn khi còn ít hơn 72 giờ trước khi khởi hành

- **Mã TC:** TC_AI_004
- **Tên Kịch Bản:** Chuyến hot (95%) nhưng chỉ còn 48 giờ trước khi khởi hành (< 72h)
- **Điều kiện tiên quyết:** `departureTime = now() + 48h`, `occupancyRate = 0.96`
- **Các bước thực hiện:**
  1. Chạy `scanAndSuggestExtraTrips()`
- **Kết quả mong đợi:**
  - Gate 4: `hoursUntilDeparture = 48 < MIN_HOURS_BEFORE_DEPARTURE = 72` → TRUE (chặn)
  - `return false`
  - Log: `"⏩ [AI] Chuyến #X: còn 48.0 giờ đến khởi hành (< 72 giờ yêu cầu). Không đủ thời gian mở vé mới."`

---

### TC_AI_005 — isHotTrip: Gate 5 — Chặn khi mới mở bán chưa đủ 48h (90-95% occupancy)

- **Mã TC:** TC_AI_005
- **Tên Kịch Bản:** Chuyến đạt 92% nhưng mới mở bán 24h — có thể là spike ảo
- **Điều kiện tiên quyết:**
  - `occupancyRate = 0.92` (< 0.95 → không bypass gate 5)
  - `departureTime = now() + 100h`
  - `saleOpenedAt = now() - 24h` → `hoursOnSale = 24`
- **Các bước thực hiện:**
  1. Chạy `scanAndSuggestExtraTrips()`
- **Kết quả mong đợi:**
  - Gate 5: `occupancyRate = 0.92 < INSTANT_HOT_THRESHOLD = 0.95` → cần kiểm tra hoursOnSale
  - `hoursOnSale = 24 < MIN_SALE_OPEN_HOURS = 48` → TRUE (chặn)
  - `return false`
  - Log: `"⏩ [AI] Chuyến #X: chỉ mới mở bán 24 giờ (< 48 giờ yêu cầu). Có thể là spike ảo."`

---

### TC_AI_006 — isHotTrip: Gate 5 — BYPASS khi occupancy >= 95% dù mới mở bán

- **Mã TC:** TC_AI_006
- **Tên Kịch Bản:** Chuyến đạt 95.5% (21/22) — bypass gate 48h, tạo đề xuất ngay lập tức
- **Điều kiện tiên quyết:**
  - TRIP2 từ `DataInitializer`: `ticketsSold=21`, `totalSeats=22` → `occupancyRate = 0.9545`
  - `departureTime = now() + 85h`
  - `saleOpenedAt = now() - 2h` → `hoursOnSale = 2` (rất mới)
- **Các bước thực hiện:**
  1. Chạy `scanAndSuggestExtraTrips()`
- **Kết quả mong đợi:**
  - Gate 5: `occupancyRate = 0.9545 >= INSTANT_HOT_THRESHOLD = 0.95` → `if` điều kiện (`< 0.95`) = FALSE → **bỏ qua kiểm tra hoursOnSale**
  - `isHotTrip` → `return true`
  - Log: `"🔥 [AI] Chuyến #2 HOT: 95.5% ghế, còn 85 giờ, đã mở bán 2 giờ."`
  - `createExtraTrip(trip)` được gọi ngay lập tức

---

### NHÓM 4.2: CHỐNG TRÙNG LẮP ĐỀ XUẤT (hasAlreadySuggested)

### TC_AI_007 — Chống trùng lắp: Không tạo thêm khi đã có chuyến tăng cường PENDING_APPROVAL

- **Mã TC:** TC_AI_007
- **Tên Kịch Bản:** Chuyến gốc HOT đã có chuyến tăng cường ở trạng thái `PENDING_APPROVAL` — không tạo thêm
- **Điều kiện tiên quyết:**
  - Chuyến gốc ID=1 là "hot"
  - Tồn tại chuyến tăng cường với `originalTripId=1` và `status=PENDING_APPROVAL`
- **Các bước thực hiện:**
  1. Chạy `scanAndSuggestExtraTrips()` nhiều lần
- **Kết quả mong đợi:**
  - `hasAlreadySuggested(trip)`: `tripRepository.existsByOriginalTripAndStatusIn(trip1, [PENDING_APPROVAL, ACTIVE, DEPARTED, COMPLETED])` → TRUE
  - `createExtraTrip(trip)` **KHÔNG** được gọi
  - Không có chuyến tăng cường mới được INSERT vào DB

---

### TC_AI_008 — Chống trùng lắp: Không tạo thêm khi đã có chuyến tăng cường ACTIVE

- **Mã TC:** TC_AI_008
- **Tên Kịch Bản:** Chuyến tăng cường đã được Admin phê duyệt và ACTIVE — không tạo lại
- **Điều kiện tiên quyết:** Chuyến tăng cường với `originalTripId=1`, `status=ACTIVE`
- **Các bước thực hiện:**
  1. Chạy `scanAndSuggestExtraTrips()`
- **Kết quả mong đợi:**
  - `existsByOriginalTripAndStatusIn(trip1, [...ACTIVE...])` → TRUE
  - Không tạo thêm chuyến

---

### TC_AI_009 — Chống trùng lắp: CHO PHÉP tạo lại khi chuyến tăng cường cũ đã CANCELLED

- **Mã TC:** TC_AI_009
- **Tên Kịch Bản:** Chuyến tăng cường cũ đã bị hủy (CANCELLED) — AI được phép đề xuất lại
- **Điều kiện tiên quyết:**
  - Chuyến gốc ID=1 vẫn đang HOT
  - Chuyến tăng cường cũ với `originalTripId=1`, `status=CANCELLED`
  - Không có chuyến tăng cường nào khác của trip1 ở trạng thái sống
- **Các bước thực hiện:**
  1. Chạy `scanAndSuggestExtraTrips()`
- **Kết quả mong đợi:**
  - `existsByOriginalTripAndStatusIn(trip1, [PENDING_APPROVAL, ACTIVE, DEPARTED, COMPLETED])` → FALSE (CANCELLED **không** nằm trong danh sách chặn)
  - `createExtraTrip(trip)` được gọi → tạo chuyến tăng cường MỚI
  - DB: INSERT chuyến mới với `original_trip_id=1`, `status=PENDING_APPROVAL`

---
//////////////////////////////////////
### NHÓM 4.3: THUẬT TOÁN TỰ ĐỘNG PHÂN CÔNG TÀI NGUYÊN (autoAssignResources)

### TC_AI_010 — autoAssignResources: Gán xe tốt nhất (ít km nhất, đúng loại, không bận)

- **Mã TC:** TC_AI_010
- **Tên Kịch Bản:** AI chọn xe Limousine có km kể từ bảo trì cuối thấp nhất
- **Điều kiện tiên quyết:**
  - Route "HN→HP" yêu cầu `suitableBusType = Limousine`
  - Các xe Limousine READY: "29A-001.01" (500km), "29A-001.02" (800km), "29A-001.10" (70km)
  - Không xe nào bị trùng lịch trong khung giờ test
  - Không xe nào quá hạn bảo trì
- **Các bước thực hiện:**
  1. Gọi `autoAssignResources(extraTrip)` (gián tiếp qua `createExtraTrip`)
- **Kết quả mong đợi:**
  - `findBestAvailableBus`: lọc xe READY đúng loại Limousine → 3 xe đủ điều kiện
  - Sắp xếp theo `kmSinceLastMaintenance` tăng dần → xe "29A-001.10" (70km) được chọn
  - Log: `"✅ [AI] Phân công thành công: Xe 29A-001.10 | Tài xế chính: ... | ..."`

---

### TC_AI_011 — findBestAvailableBus: Loại xe quá hạn bảo trì

- **Mã TC:** TC_AI_011
- **Tên Kịch Bản:** AI không chọn xe đã quá hạn bảo trì dù xe đó có km thấp nhất
- **Điều kiện tiên quyết:**
  - Xe "51B-QUA.BT": `kmSinceLastMaintenance = 6000 >= 5000` → `needsMaintenance() = true`
- **Các bước thực hiện:**
  1. Gọi `autoAssignResources()` khi xe "51B-QUA.BT" là xe duy nhất đúng loại
- **Kết quả mong đợi:**
  - `.filter(bus -> !bus.needsMaintenance())` → xe "51B-QUA.BT" bị loại
  - `findBestAvailableBus` trả về `null`
  - `autoAssignResources` trả về `AutoAssignResult.failure("Không có xe nào sẵn sàng / đúng loại / không bận trong khung giờ này")`
  - Log: `"⚠️ [AI] Không tự phân công được: Không có xe nào sẵn sàng... → Admin xử lý thủ công."`
  - Chuyến tăng cường vẫn được lưu vào DB nhưng `bus = null`, `driver = null`

---

### TC_AI_012 — findBestAvailableBus: Loại xe sắp quá hạn bảo trì (isNearMaintenance)

- **Mã TC:** TC_AI_012
- **Tên Kịch Bản:** AI không chọn xe sẽ đạt >= 90% ngưỡng bảo trì sau chuyến này
- **Điều kiện tiên quyết:**
  - Xe "51B-SAT.BT": `kmSinceLastMaintenance = 4995`, `maintenanceThreshold = 5000`
  - Route có `distanceKm = 120`
  - `isNearMaintenance(0)`: `4995 >= 5000 * 0.9 = 4500` → TRUE (đã ở vùng cảnh báo dù chưa tính chuyến mới)
- **Các bước thực hiện:**
  1. Gọi `autoAssignResources()` — `findBestAvailableBus` dùng `isNearMaintenance(0)` (tripDistance=0 vì route chưa biết lúc filter)
- **Kết quả mong đợi:**
  - `.filter(bus -> !bus.isNearMaintenance(0))`: `4995 >= 4500` → TRUE → lọc ra xe "51B-SAT.BT"
  - Xe "51B-SAT.BT" không được chọn bởi AI

---

### TC_AI_013 — findBestAvailableDriver: Ưu tiên tài xế bằng lái còn hạn > 7 ngày

- **Mã TC:** TC_AI_013
- **Tên Kịch Bản:** AI ưu tiên tài xế bằng lái còn hạn trên 7 ngày, chọn người ít giờ lái nhất
- **Điều kiện tiên quyết:**
  - Nhóm tài xế rảnh: "TX Rảnh 0h (A1-A20)" tất cả `licenseExpiryDate = now() + 1 năm` (> 7 ngày)
  - "TX Bằng Lái Sắp Hết" (`licenseExpiryDate = now() + 1 ngày` < 7 ngày)
  - Tất cả không bận, tổng giờ 0h
- **Các bước thực hiện:**
  1. Gọi `autoAssignResources()` cho chuyến 2h
- **Kết quả mong đợi:**
  - Filter ưu tiên 1: chọn từ nhóm bằng hạn > 7 ngày → "TX Rảnh 0h (A1)" được chọn (ít giờ lái nhất = 0h)
  - "TX Bằng Lái Sắp Hết" không được chọn ở bước ưu tiên 1

---

### TC_AI_014 — findBestAvailableDriver: Fallback khi tất cả bằng lái < 7 ngày còn hạn

- **Mã TC:** TC_AI_014
- **Tên Kịch Bản:** Tất cả tài xế hợp lệ có bằng lái còn < 7 ngày — AI chọn fallback và ghi log cảnh báo
- **Điều kiện tiên quyết:**
  - Chỉ còn "TX Bằng Lái Sắp Hết" (`licenseExpiryDate = now() + 1 ngày`, còn hiệu lực) là duy nhất rảnh
  - Không có tài xế nào khác rảnh
- **Các bước thực hiện:**
  1. Gọi `autoAssignResources()` khi chỉ còn tài xế bằng sắp hết hạn
- **Kết quả mong đợi:**
  - Ưu tiên 1 (bằng hạn > 7 ngày): không tìm được → `best = null`
  - Fallback: `availableDrivers.stream().min(...)` → "TX Bằng Lái Sắp Hết" được chọn
  - Log: `"⚠️ [AI] Tài xế/Phụ xe TX Bằng Lái Sắp Hết được chọn nhưng bằng lái SẮP HẾT HẠN (vào <date>)!"`
  - Chuyến tăng cường vẫn được tạo với tài xế fallback này

---

### TC_AI_015 — findBestAvailableDriver: Loại tài xế bằng lái đã hết hạn hoàn toàn

- **Mã TC:** TC_AI_015
- **Tên Kịch Bản:** AI hoàn toàn không chọn tài xế có bằng lái đã hết hạn
- **Điều kiện tiên quyết:** Chỉ còn "TX Bằng Lái Đã Hết" (`licenseExpiryDate = now() - 5 ngày`) là rảnh
- **Các bước thực hiện:**
  1. Gọi `autoAssignResources()`
- **Kết quả mong đợi:**
  - `.filter(Driver::isLicenseValid)`: `licenseExpiryDate.isAfter(now()) = false` → bị lọc ra
  - `availableDrivers` rỗng
  - `findBestAvailableDriver` trả về `null`
  - `autoAssignResources` failure: `"Không có tài xế chính hợp lệ (cần bằng còn hạn, rảnh, và đủ định mức ngày)"`

---

### TC_AI_016 — autoAssignResources: Phân công đủ tài xế cho chuyến dài > 8h

- **Mã TC:** TC_AI_016
- **Tên Kịch Bản:** AI tìm đủ tài xế chính + tài xế phụ + phụ xe cho chuyến 16h
- **Điều kiện tiên quyết:**
  - Chuyến tăng cường kéo dài 16h (`tripDurationHours = 16`)
  - Có đủ tài xế rảnh (ít nhất 3 người: 1 chính + 1 coDriver + 1 assistant)
  - `requiredDrivers = ceil(16/8) = 2`
- **Các bước thực hiện:**
  1. `autoAssignResources(extraTrip)` với `tripDurationHours = 16`
- **Kết quả mong đợi:**
  - Xe được chọn 1 chiếc
  - Tài xế chính được chọn, thêm vào `assignedStaff`
  - coDriver vòng lặp i=1: 1 tài xế phụ được chọn (khác tài xế chính)
  - `tripDurationHours = 16 > 8.0` → phụ xe bắt buộc → 1 assistant được chọn (khác tài xế chính và coDriver)
  - `AutoAssignResult.success(bus, driver, assistant, coDrivers)`
  - Mỗi tài xế trong chuyến chỉ lái `16/2 = 8h` → không vượt giới hạn

---

### TC_AI_017 — createExtraTrip: Thời gian chuyến tăng cường được tính đúng

- **Mã TC:** TC_AI_017
- **Tên Kịch Bản:** Thời gian khởi hành chuyến tăng cường phải là chuyến gốc + 30 phút, thời gian đến dựa trên `route.estimatedDuration`
- **Điều kiện tiên quyết:** Chuyến gốc TRIP1: `departureTime = 2026-08-01T08:00`, Route "HN→HP" `estimatedDuration = 120 phút`
- **Các bước thực hiện:**
  1. `createExtraTrip(trip1)` được gọi
- **Kết quả mong đợi:**
  - `extraDeparture = trip.getDepartureTime().plusMinutes(30) = 2026-08-01T08:30`
  - `routeDurationMinutes = 120`
  - `extraArrival = 2026-08-01T08:30 + 120min = 2026-08-01T10:30`
  - DB: chuyến tăng cường có `departure_time = 2026-08-01T08:30`, `arrival_time_expected = 2026-08-01T10:30`
  - `is_extra_trip = true`, `original_trip_id = 1`, `status = 'PENDING_APPROVAL'`

---

### TC_AI_018 — Cron Job: Kiểm tra @Transactional ngăn LazyInitializationException

- **Mã TC:** TC_AI_018
- **Tên Kịch Bản:** Cron job truy cập `trip.getRoute()` (lazy) không bị LazyInitializationException do @Transactional
- **Điều kiện tiên quyết:** Chuyến TRIP1 có lazy-loaded `route` association
- **Các bước thực hiện:**
  1. Chờ 10 giây cho `scanAndSuggestExtraTrips()` chạy
- **Kết quả mong đợi:**
  - `@Transactional` trên `scanAndSuggestExtraTrips()` giữ Hibernate Session mở
  - `tripRepository.findByStatusWithRoute(ACTIVE)` dùng `JOIN FETCH t.route` → route được load eagerly
  - `trip.getRoute().getEstimatedDuration()` trong `createExtraTrip()` không throw `LazyInitializationException`
  - Không có stack trace lỗi trong console

---

<a name="module-5"></a>
## MODULE 5: QUY TRÌNH PHÊ DUYỆT CỦA ADMIN (Admin Approval Flow)
**Controller:** `AdminTripController` — `@RequestMapping("/admin/trips")`  
**Service:** `TripService.confirmAutoAssignedTrip()`, `TripService.approveTrip()`, `TripService.rejectTrip()`

---

### TC_APR_001 — Xem danh sách chuyến chờ duyệt (Pending Trips)

- **Mã TC:** TC_APR_001
- **Tên Kịch Bản:** Hiển thị đúng danh sách chuyến `PENDING_APPROVAL` với thông tin AI phân công
- **Điều kiện tiên quyết:** Có ít nhất 2 chuyến `PENDING_APPROVAL`: 1 đã được AI phân công (bus + driver != null), 1 chưa được phân công
- **Các bước thực hiện:**
  1. `GET /admin/trips/pending`
- **Kết quả mong đợi:**
  - HTTP 200, template `admin/pending-trips`
  - `pendingTrips = tripService.getPendingTrips()` → query `findByStatusWithDetails(PENDING_APPROVAL)`
  - `autoAssignedCount` = số chuyến có cả `bus != null && driver != null`
  - `needsManualCount` = tổng - autoAssignedCount
  - Danh sách đầy đủ thông tin (route, bus, driver, assistant) do JOIN FETCH

---

### TC_APR_002 — Xem form phê duyệt: Chế độ AUTO (AI đã phân công đủ)

- **Mã TC:** TC_APR_002
- **Tên Kịch Bản:** Khi AI đã phân công đủ xe + tài xế, form chỉ hiển thị nút xác nhận 1-click
- **Điều kiện tiên quyết:** Chuyến ID=30 ở `PENDING_APPROVAL`, có `bus != null && driver != null`
- **Các bước thực hiện:**
  1. `GET /admin/trips/approve/30`
- **Kết quả mong đợi:**
  - `isAutoAssigned = (bus != null && driver != null)` = TRUE
  - Model không load `availableBuses` và `availableDrivers` (chế độ auto, không cần dropdown)
  - Template `admin/approve-form` hiển thị thông tin phân công của AI và nút "Xác nhận 1 click"

---

### TC_APR_003 — Xem form phê duyệt: Chế độ MANUAL (AI không phân công được)

- **Mã TC:** TC_APR_003
- **Tên Kịch Bản:** Khi AI không phân công được (bus = null), form hiển thị dropdown để Admin tự chọn
- **Điều kiện tiên quyết:** Chuyến ID=31 ở `PENDING_APPROVAL`, `bus = null`, `driver = null`
- **Các bước thực hiện:**
  1. `GET /admin/trips/approve/31`
- **Kết quả mong đợi:**
  - `isAutoAssigned = false`
  - `availableBuses = tripService.getAvailableBusesForTrip(31)` → danh sách xe rảnh, hợp lệ
  - `availableDrivers = tripService.getAvailableDriversForTrip(31)` → danh sách tài xế rảnh, hợp lệ
  - Template hiển thị form dropdown cho Admin chọn thủ công

---

### TC_APR_004 — Xác nhận 1-click (confirmAutoAssigned): Happy Path

- **Mã TC:** TC_APR_004
- **Tên Kịch Bản:** Admin xác nhận chuyến tăng cường AI đã phân công đủ — hệ thống tái thẩm định và kích hoạt
- **Điều kiện tiên quyết:**
  - Chuyến ID=30: `PENDING_APPROVAL`, `bus = 29A-001.10` (READY, không bận), `driver = TX Rảnh 0h (A1)` (rảnh, bằng hạn, 0h)
  - Tài nguyên vẫn còn rảnh (chưa bị chiếm bởi chuyến khác)
- **Các bước thực hiện:**
  1. `POST /admin/trips/confirm?tripId=30`
- **Kết quả mong đợi:**
  - `confirmAutoAssignedTrip(30)`:
    - `trip.getBus() != null` → OK
    - `trip.getDriver() != null` → OK
    - `validateBusForTrip(bus, trip, 30)` → xe hợp lệ → return null
    - `validateStaffForTrip(trip, 30)` → nhân sự hợp lệ
    - `changeStatusToActive(trip)` → `status = ACTIVE`, `saleOpenedAt = now()`
  - `flash[success]` = `"✅ Chuyến tăng cường #30 đã được kích hoạt thành công!"`
  - DB: `trips.status = 'ACTIVE'`
  - Redirect về `/admin/trips/pending`
  - Log: `"✅ Admin xác nhận chuyến #30 | Xe: 29A-001.10 | Tài xế chính: TX Rảnh 0h (A1) | ..."`

---

### TC_APR_005 — Xác nhận 1-click: Chặn khi xe đã bị chiếm dụng trong thời gian chờ duyệt

- **Mã TC:** TC_APR_005
- **Tên Kịch Bản:** Tài nguyên AI phân công đã bị chiếm bởi chuyến thủ công tạo sau — tái thẩm định phát hiện conflict
- **Điều kiện tiên quyết:**
  - Chuyến ID=30 (AI phân công): `bus=29A-001.10`, `departure=10:00`, `arrival=14:00`
  - Trong khi chờ Admin duyệt, Admin tạo thủ công chuyến khác dùng xe "29A-001.10" cùng khung giờ → xe bị bận
- **Các bước thực hiện:**
  1. `POST /admin/trips/confirm?tripId=30`
- **Kết quả mong đợi:**
  - `validateBusForTrip(bus, trip, 30)` (excludeTripId=30):
    - `isBusBusy(bus, 09:00, 15:00, 30)`: tìm thấy chuyến thủ công mới trùng giờ → TRUE
    - `throw new IllegalArgumentException("Xe 29A-001.10 đang bận trong khoảng thời gian này!")`
  - Controller catch Exception → `flash[error]` = `"Lỗi xác nhận: Xe 29A-001.10 đang bận trong khoảng thời gian này!"`
  - Redirect về `/admin/trips/approve/30` (form phê duyệt để Admin chọn lại)
  - Chuyến #30 vẫn ở trạng thái `PENDING_APPROVAL`

---

### TC_APR_006 — Xác nhận 1-click: Báo lỗi khi trip chưa có bus (chưa phân công)

- **Mã TC:** TC_APR_006
- **Tên Kịch Bản:** Admin gọi endpoint confirm cho chuyến mà AI không phân công được xe
- **Điều kiện tiên quyết:** Chuyến ID=31: `bus = null`
- **Các bước thực hiện:**
  1. `POST /admin/trips/confirm?tripId=31`
- **Kết quả mong đợi:**
  - `trip.getBus() == null` → TRUE
  - `throw new IllegalStateException("Chuyến chưa được phân công xe. Vui lòng dùng form phân công thủ công.")`
  - `flash[error]` = `"Lỗi xác nhận: Chuyến chưa được phân công xe. Vui lòng dùng form phân công thủ công."`

---

### TC_APR_007 — Phê duyệt thủ công (approveTrip): Happy Path

- **Mã TC:** TC_APR_007
- **Tên Kịch Bản:** Admin chọn xe + tài xế từ dropdown và kích hoạt chuyến thành công
- **Điều kiện tiên quyết:**
  - Chuyến ID=31: `PENDING_APPROVAL`, `bus = null`, `driver = null`
  - Xe "29A-001.11" (READY, không bận, không quá hạn bảo trì)
  - Tài xế "TX Rảnh 0h (A5)" (active, bằng hạn, 0h)
- **Các bước thực hiện:**
  1. `POST /admin/trips/approve` với `tripId=31`, `busId=<id xe 29A-001.11>`, `driverId=<id TX A5>`, `assistantId=null`, `coDriverIds=null`
- **Kết quả mong đợi:**
  - `approveTrip(31, busId, driverId, null, null)`:
    - Tìm bus, driver thành công
    - `validateBusForTrip` → OK
    - `validateStaffForTrip` → OK
    - `changeStatusToActive(trip)` → `status = ACTIVE`, `saleOpenedAt = now()`
  - `flash[success]` = `"✅ Chuyến xe #31 đã được phân công và kích hoạt thành công!"`
  - DB: chuyến ID=31 có `bus_id = busId`, `driver_id = driverId`, `status = 'ACTIVE'`
  - Redirect về `/admin/trips/pending`

---

### TC_APR_008 — Phê duyệt thủ công: Báo lỗi khi vi phạm ràng buộc xe

- **Mã TC:** TC_APR_008
- **Tên Kịch Bản:** Admin chọn xe REPAIRING từ dropdown — hệ thống chặn dù dropdown đã lọc trước
- **Điều kiện tiên quyết:** Chuyến ID=31 chưa được phân công; xe "51B-SUA.XE" (REPAIRING) bị chọn
- **Các bước thực hiện:**
  1. `POST /admin/trips/approve` với `busId` của xe REPAIRING
- **Kết quả mong đợi:**
  - `validateBusForTrip` ném `IllegalArgumentException`
  - Controller catch `IllegalArgumentException` → `flash[error]` = `"⛔ Vi phạm ràng buộc: Xe 51B-SUA.XE đang được bảo trì/sửa chữa, không thể gán vào chuyến!"`
  - Redirect về `/admin/trips/approve/31` (giữ ở form để Admin chọn lại)

---

### TC_APR_009 — Từ chối chuyến tăng cường (rejectTrip): Happy Path

- **Mã TC:** TC_APR_009
- **Tên Kịch Bản:** Admin từ chối chuyến tăng cường AI đề xuất không cần thiết
- **Điều kiện tiên quyết:** Chuyến ID=30 ở `PENDING_APPROVAL`
- **Các bước thực hiện:**
  1. `POST /admin/trips/reject/30`
- **Kết quả mong đợi:**
  - `rejectTrip(30)` gọi `updateTripStatus(30, CANCELLED)`
  - `canTransition(PENDING_APPROVAL, CANCELLED)` → TRUE
  - `trip.setStatus(CANCELLED)`
  - DB: `trips.status = 'CANCELLED'` cho row id=30
  - `flash[success]` = `"Đã từ chối chuyến tăng cường #30."`
  - Log: `"❌ Admin từ chối chuyến tăng cường #30"`

---

### TC_APR_010 — Từ chối chuyến: Chặn khi chuyến đang ACTIVE (FSM)

- **Mã TC:** TC_APR_010
- **Tên Kịch Bản:** Admin cố từ chối chuyến đã được kích hoạt (ACTIVE) qua endpoint reject
- **Điều kiện tiên quyết:** Chuyến ID=5 ở trạng thái `ACTIVE`
- **Các bước thực hiện:**
  1. `POST /admin/trips/reject/5`
- **Kết quả mong đợi:**
  - `rejectTrip(5)` → `updateTripStatus(5, CANCELLED)`
  - `canTransition(ACTIVE, CANCELLED)` → TRUE (theo FSM ACTIVE cho phép → CANCELLED)
  - Chuyến ACTIVE có thể bị reject thành CANCELLED — đây là behavior hiện tại
  - **Lưu ý:** Nếu chuyến đã bán vé, nghiệp vụ có thể cần kiểm tra thêm nhưng code hiện tại không chặn ở `rejectTrip()`

---

<a name="module-6"></a>
## MODULE 6: TẦNG BẢO MẬT & CẤU HÌNH (Security & Configuration)
**Config:** `SecurityConfig`, `DataInitializer`

---

### TC_SEC_001 — SecurityConfig: Tất cả request được phép không cần xác thực

- **Mã TC:** TC_SEC_001
- **Tên Kịch Bản:** Xác nhận cấu hình `anyRequest().permitAll()` hoạt động đúng — tất cả URL đều truy cập được mà không cần đăng nhập
- **Điều kiện tiên quyết:** Application đang chạy, SecurityConfig được load
- **Các bước thực hiện:**
  1. Truy cập không có session/cookie: `GET /admin/buses`
  2. `GET /admin/stations`
  3. `GET /admin/trip-management/trips`
  4. `GET /admin/trips/pending`
  5. `GET /api/admin/trips/available-resources?departure=2026-09-01T08:00&arrival=2026-09-01T10:00`
- **Kết quả mong đợi:**
  - Tất cả 5 request đều trả về HTTP 200 (không phải 401, 403, hoặc redirect đến `/login`)
  - Không có header `WWW-Authenticate` trong response
  - Không có pop-up nhập username/password của browser (HTTP Basic đã tắt)

---

### TC_SEC_002 — SecurityConfig: CSRF được tắt — POST request không cần CSRF token

- **Mã TC:** TC_SEC_002
- **Tên Kịch Bản:** Xác nhận CSRF protection đã được disable — form POST không cần `_csrf` token
- **Điều kiện tiên quyết:** Application chạy với SecurityConfig load
- **Các bước thực hiện:**
  1. `POST /admin/buses/create` với form data hợp lệ, **KHÔNG gửi** `_csrf` token
  2. `POST /admin/stations/create` tương tự
  3. `POST /admin/trip-management/trips/create` tương tự
- **Kết quả mong đợi:**
  - HTTP 302 Redirect (không phải 403 Forbidden)
  - Không có response body chứa "Invalid CSRF Token"
  - Form submission thành công

---

### TC_SEC_003 — SecurityConfig: Form Login đã tắt — không có trang /login mặc định

- **Mã TC:** TC_SEC_003
- **Tên Kịch Bản:** Xác nhận Spring Security không tự tạo trang login khi `formLogin(form -> form.disable())`
- **Điều kiện tiên quyết:** Application chạy
- **Các bước thực hiện:**
  1. `GET /login`
- **Kết quả mong đợi:**
  - HTTP 404 Not Found (Spring Security không tạo endpoint `/login` mặc định)
  - KHÔNG phải HTTP 200 với form đăng nhập

---

### TC_SEC_004 — SecurityConfig: HTTP Basic Authentication đã tắt

- **Mã TC:** TC_SEC_004
- **Tên Kịch Bản:** Xác nhận không có hộp thoại nhập username/password khi gửi request Authorization header
- **Điều kiện tiên quyết:** Application chạy
- **Các bước thực hiện:**
  1. Gửi `GET /admin/buses` với header `Authorization: Basic dXNlcjpwYXNz` (base64 của "user:pass")
- **Kết quả mong đợi:**
  - HTTP 200 (không phải 401 với `WWW-Authenticate: Basic realm="..."`)
  - Header `WWW-Authenticate` không xuất hiện trong response
  - Request được xử lý bình thường

---

### TC_SEC_005 — DataInitializer: Dữ liệu seed được tái tạo đúng khi khởi động với profile `demo`

- **Mã TC:** TC_SEC_005
- **Tên Kịch Bản:** Kiểm tra `DataInitializer.run()` xóa dữ liệu cũ và tạo lại đúng bộ seed data
- **Điều kiện tiên quyết:** Có dữ liệu cũ trong DB từ lần chạy trước
- **Các bước thực hiện:**
  1. Khởi động ứng dụng **với profile `demo`**: `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`
  2. Kiểm tra DB sau khi khởi động
- **Kết quả mong đợi:**
  - Thứ tự xóa: `trips → drivers → users → buses → busTypes → routeStations → routes → stations` (đúng thứ tự FK)
  - Tạo lại: 3 BusType (Limousine/22, Giường nằm/40, Ghế ngồi/50)
  - Tạo 30+ tài xế theo các nhóm A-G
  - Tạo xe với các trạng thái READY/REPAIRING/TRAVELING như đã định nghĩa
  - Tạo 7 bến xe, 6 tuyến đường, các chuyến test TRIP1-TRIPX ở trạng thái ACTIVE
  - Log: `"🤖 [AI Test Setup] Đang khởi tạo dữ liệu test..."`

---

### TC_SEC_005B — DataInitializer KHÔNG chạy ở profile mặc định (dữ liệu được giữ nguyên)

- **Mã TC:** TC_SEC_005B
- **Tên Kịch Bản:** Kiểm tra `@Profile("demo")` chặn `DataInitializer` ở profile mặc định — dữ liệu sống sót qua restart
- **Điều kiện tiên quyết:** DB `busmanagement` có sẵn dữ liệu (VD: N chuyến)
- **Các bước thực hiện:**
  1. Khởi động ứng dụng **không kèm cờ profile**: `./mvnw spring-boot:run`
  2. Đếm lại số chuyến trong DB
- **Kết quả mong đợi:**
  - Log KHÔNG xuất hiện dòng `"🤖 [AI Test Setup] Đang khởi tạo dữ liệu test..."`
  - Log hiển thị `No active profile set, falling back to 1 default profile: "default"`
  - Số chuyến vẫn đúng bằng N — không có bản ghi nào bị xóa hay seed thêm
  - Cột `trips.created_at` tồn tại (`ddl-auto=update` tự bổ sung mà không drop bảng)

---

### TC_SEC_005C — Test suite không đụng vào database thật

- **Mã TC:** TC_SEC_005C
- **Tên Kịch Bản:** Kiểm tra `src/test/resources/application.properties` cách ly test khỏi DB `busmanagement`
- **Điều kiện tiên quyết:** DB `busmanagement` có sẵn dữ liệu (VD: N chuyến)
- **Các bước thực hiện:**
  1. Chạy `./mvnw test`
  2. Đếm lại số chuyến trong `busmanagement`
- **Kết quả mong đợi:**
  - Test pass (`BUILD SUCCESS`)
  - Database `busmanagement_test` được tự động tạo (`createDatabaseIfNotExist=true`)
  - Số chuyến trong `busmanagement` vẫn đúng bằng N — test không xóa dữ liệu thật
  - Log KHÔNG xuất hiện `"🤖 [AI Test Setup]"` (test không kích hoạt profile `demo`)

---

### TC_SEC_006 — DataInitializer: Tài xế với totalDrivingHours24h được cộng đúng vào tổng giờ lái hôm nay

- **Mã TC:** TC_SEC_006
- **Tên Kịch Bản:** Kiểm tra mock seed `totalDrivingHours24h` được cộng dồn vào `getDrivingHoursForDate()` đúng
- **Điều kiện tiên quyết:**
  - "TX Đã lái 7h" có `totalDrivingHours24h = 7.0`
  - Ngày hôm nay không có chuyến nào của tài xế này trong DB (chỉ có mock seed)
- **Các bước thực hiện:**
  1. Gọi `getDrivingHoursForDate(txDaLai7h, LocalDateTime.now(), null)`
- **Kết quả mong đợi:**
  - `hoursFromTrips = 0.0` (không có chuyến thực nào hôm nay)
  - `date.toLocalDate().equals(LocalDateTime.now().toLocalDate())` → TRUE
  - `baseHours = 7.0` → `hoursFromTrips += 7.0`
  - Kết quả trả về: `7.0`
  - Tài xế này chỉ còn có thể nhận chuyến có `effectiveHours <= 1.0` (8 - 7 = 1h)

---

## PHỤ LỤC: MA TRẬN CHUYỂN TRẠNG THÁI FSM

| Từ → Đến | ACTIVE | PENDING | DEPARTED | COMPLETED | CANCELLED |
|---|---|---|---|---|---|
| **PENDING_APPROVAL** | ✅ | ✅ (self) | ❌ | ❌ | ✅ |
| **ACTIVE** | ✅ (self) | ❌ | ✅ | ❌ | ✅ |
| **DEPARTED** | ❌ | ❌ | ✅ (self) | ✅ | ❌ |
| **COMPLETED** | ❌ | ❌ | ❌ | ✅ (self) | ❌ |
| **CANCELLED** | ❌ | ❌ | ❌ | ❌ | ✅ (self) |

*Ghi chú: Self-transition (from == to) luôn trả về `true` và không kích hoạt đồng bộ BusStatus*

---

## PHỤ LỤC: MA TRẬN XÓA MỀM CHUYẾN XE

| Trạng thái | ticketsSold | Kết quả | Exception |
|---|---|---|---|
| DEPARTED | bất kỳ | ❌ Chặn cứng | `IllegalStateException` |
| COMPLETED | bất kỳ | ❌ Chặn cứng | `IllegalStateException` |
| ACTIVE | > 0 | ❌ Chặn | `IllegalStateException` |
| ACTIVE | = 0 | ✅ Cho phép | - |
| PENDING_APPROVAL | bất kỳ | ✅ Cho phép | - |
| CANCELLED | bất kỳ | ✅ Cho phép | - |

---

## PHỤ LỤC: MA TRẬN isHotTrip() — 5 GATE CHẶN

| Gate | Điều kiện | Kết quả nếu FAIL |
|---|---|---|
| **Gate 1** | `occupancyRate > 0.9` | `return false` |
| **Gate 3** | `departureTime.isAfter(now)` | `return false` — log "đã khởi hành" |
| **Gate 4** | `hoursUntilDeparture >= 72` | `return false` — log "không đủ thời gian" |
| **Gate 5A** | Chỉ áp dụng khi `occupancyRate < 0.95` | — |
| **Gate 5B** | `hoursOnSale >= 48` (nếu Gate 5A) | `return false` — log "spike ảo" |
| **BYPASS** | `occupancyRate >= 0.95` | Bỏ qua Gate 5 hoàn toàn |

---

## PHỤ LỤC: THỐNG KÊ TEST CASE

| Module | Số TC | Happy Path | Error/Edge Case |
|---|---|---|---|
| Module 1 — Bus Management | 11 | 4 | 7 |
| Module 2 — Station Management | 6 | 3 | 3 |
| Module 3 — Trip FSM & CRUD | 25 | 6 | 19 |
| Module 4 — AI Scheduler | 18 | 5 | 13 |
| Module 5 — Admin Approval | 10 | 4 | 6 |
| Module 6 — Security & Config | 6 | 2 | 4 |
| **TỔNG** | **76** | **24** | **52** |

---

*Tài liệu này được soạn dựa trên phân tích mã nguồn thực tế từ `repomix-output.xml`. Mọi tên biến, thông báo lỗi, tên hàm và logic nghiệp vụ được trích xuất trực tiếp từ code.*