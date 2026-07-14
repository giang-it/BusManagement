# BusManagement — Báo cáo tổng hợp lỗi & thiết kế (bản cập nhật)

> Cập nhật dựa trên đối chiếu trực tiếp với `repomix-output.xml` mới nhất + các thay đổi thực hiện trong phiên làm việc này (sửa logic giờ lái Phụ xe). Mục tiêu: phản ánh đúng trạng thái **ĐÃ FIX** vs **CÒN MỞ**, tránh Codex/AI khác sửa nhầm chỗ đã xong.

| Số lượng | Danh mục |
|---|---|
| **4/4** | 🔴 Lỗi logic nghiêm trọng — **đã fix hết** |
| **3/5** | 🟡 Cảnh báo thiết kế — đã fix 3, còn mở 2 |
| **2/3** | 🔵 Thiếu nhất quán — đã fix 2, còn mở 1 |
| **1/4** | 🟣 Vấn đề Phụ xe (Codex review) — đã fix 1, còn mở 3 |

---

## 🔴 Lỗi logic nghiêm trọng — TẤT CẢ ĐÃ FIX ✅

### Bug #1: `BusStatus.TRAVELING` không tự động cập nhật — ✅ ĐÃ FIX
`TripService.updateTripStatus()` giờ đồng bộ: `ACTIVE→DEPARTED` set `bus.status = TRAVELING`; `DEPARTED→COMPLETED` set `bus.status = READY`.

### Bug #2: `coDrivers` kiểu `List<User>` gây type mismatch — ✅ ĐÃ FIX
`Trip.coDrivers` đã đổi thành `List<Driver>`, nhất quán với `driver` và `assistant`.

### Bug #3: `cancelTrip()` bypass FSM — ✅ ĐÃ FIX
`AdminTripManagementController.cancelTrip()` giờ gọi `tripService.updateTripStatus(id, CANCELLED)`, có `catch (IllegalStateException)` riêng khi transition không hợp lệ (VD `COMPLETED → CANCELLED`).

### Bug #4: `BusType.getSuitableBusType()` trùng lặp — ✅ ĐÃ FIX
Field `suitableBusType` + method trùng đã bị xóa khỏi `BusType.java`. Xác nhận qua code hiện tại: `BusType` chỉ còn `id`, `typeName`, `capacity`.

---

## 🟡 Cảnh báo thiết kế

### Warn #1: RouteStation không được seed — ✅ ĐÃ FIX
`DataInitializer.java` giờ có hàm `linkRouteStation()` được gọi thật cho mỗi Route (`linkRouteStation(r, from, 1); linkRouteStation(r, to, 2);`). Bảng `route_stations` có dữ liệu, không còn rỗng.

### Warn #2: `createManualTrip()` set `ACTIVE` ngay, bỏ qua `PENDING_APPROVAL` — ⚠️ **CÒN MỞ**
Chưa xử lý. Vẫn cần quyết định rõ: trip thủ công có nên qua `PENDING_APPROVAL` để nhất quán với flow AI-suggested hay không.

### Warn #3: `findBestAvailableBus()` filter `READY` có rủi ro do Bug #1 — ✅ ĐÃ FIX (hệ quả của Bug #1)
Vì Bug #1 đã fix (bus tự chuyển `TRAVELING` khi `DEPARTED`), rủi ro "xe TRAVELING vẫn mang status READY" không còn xảy ra.

### Warn #4: `AdminTripManagementController` inject Repository trực tiếp — ⚠️ **CÒN MỞ**
Đã verify lại: controller vẫn `@RequiredArgsConstructor` với `TripRepository`, `RouteRepository`, `BusRepository`, `DriverRepository` **song song** với `TripService`, và gọi thẳng `tripRepository.findAllWithDetails()` trong `listAllTrips()`. Vi phạm layered architecture vẫn còn nguyên.

### Warn #5: `ddl-auto` xóa dữ liệu mỗi lần restart — ⚠️ **CÒN MỞ (bản chất không đổi)**
Giá trị thực tế hiện là `create-drop` (không phải `create` như báo cáo cũ ghi), nhưng rủi ro giống hệt: mỗi lần restart xóa sạch dữ liệu. Chưa tách profile dev/prod.

---

## 🔵 Thiếu nhất quán

### Incon #1: Route dùng String tự do cho điểm đi/đến — ✅ ĐÃ FIX
`Route.java` đã xóa hẳn 2 field `departurePoint`/`destinationPoint` (String). Điểm đi/đến giờ suy ra từ `RouteStation.stopOrder` qua `getDepartureStation()`/`getDestinationStation()`/`getDeparturePointDisplay()`. `routeStations` dùng `@BatchSize(20)` để tránh `MultipleBagFetchException` khi xung đột với `Trip.coDrivers`.

### Incon #2: `hasAlreadySuggested()` thiếu check `DEPARTED`/`COMPLETED` — ✅ ĐÃ FIX
Đã verify: `blockingStatuses` hiện gồm đủ `PENDING_APPROVAL`, `ACTIVE`, `DEPARTED`, `COMPLETED`. `CANCELLED` cố tình để ngoài danh sách chặn (cho phép AI đề xuất lại nếu chuyến tăng cường cũ bị hủy).

### Incon #3: `countBusyTripsAnyRole`/`findAllTripsByDriverOnDate` là dead code — ⚠️ **CÒN MỞ**
Vẫn tồn tại trong `TripRepository`, chưa được gọi ở đâu trong `TripService`. Trùng chức năng với cặp `existsOverlappingTripForDriver`/`findTripsForDriverOnDate` đang dùng thật. Cần xóa hoặc migrate.

---

## 🟣 Vấn đề Phụ xe (từ Codex review)

### #1: Ràng buộc giờ lái áp dụng sai cho Assistant trong AI auto-assign — ✅ ĐÃ FIX (phiên làm việc này)
- `findBestAvailableDriver()` giờ nhận thêm tham số `boolean isAssistantRole`. Khi `true`, bỏ điều kiện `getDrivingHoursForDate() + effectiveHours <= 8.0`, chỉ còn: active, không trùng vai trò, bằng lái còn hạn, không bận trong cửa sổ thời gian.
- `autoAssignResources()`: driver chính và coDriver truyền `isAssistantRole=false`; assistant truyền `true`.
- Thêm mới `getAvailableAssistantsForTrip()` cho luồng duyệt thủ công (không áp trần 8h), và `AdminTripController`/`approve-form.html` đã cập nhật dùng `availableAssistants` riêng cho dropdown phụ xe thay vì dùng chung `availableDrivers`.
- **Lưu ý quan trọng phát sinh sau khi fix:** hiện tại phụ xe **không có bất kỳ trần giờ làm việc nào** (chỉ còn ràng buộc không trùng lịch) — đây là khoảng trống nghiệp vụ có sẵn từ spec gốc (không phải bug do bản sửa này gây ra), đã bàn với Giang và **quyết định tạm thời**: giữ nguyên, không chặn cứng, có thể bổ sung cảnh báo mềm (soft warning) ở lần sửa sau nếu cần. Đây là điểm cần theo dõi tiếp, không phải đã đóng hoàn toàn về mặt nghiệp vụ.

### #2: Mâu thuẫn hiển thị vs validate khi thiếu phụ xe (chuyến >8h) — ⚠️ **CÒN MỞ**
`approve-form.html` (khu vực hiển thị kết quả AI auto-assign) vẫn hiện thông báo "Chuyến vẫn có thể chạy nhưng không có phụ xe" mà **không** disable nút "Xác nhận & Kích hoạt", trong khi `validateStaffForTrip()` sẽ chặn cứng (`throw IllegalArgumentException`) nếu chuyến >8h thiếu assistant. Cần đồng bộ: disable nút + ghi rõ đây là hard block khi rơi vào trường hợp này.

### #3: `trip-create-form.html`/`trip-edit-form.html` hiển thị toàn bộ DB thay vì lọc động — ⚠️ **CÒN MỞ**
Chưa gọi API `/api/admin/trips/available-resources` để lọc xe/tài xế rảnh theo thời gian như wizard đã làm ở nơi khác.

### #4: Dropdown co-driver không loại trừ lẫn nhau trên FE — ⚠️ **CÒN MỞ**
Admin có thể chọn trùng tài xế phụ trên giao diện, chỉ bị chặn khi submit lên server.

---

## Tổng kết việc cần làm tiếp (theo độ ưu tiên đề xuất)

1. **#2 (Phụ xe)** — đồng bộ UI/validate khi thiếu phụ xe cho chuyến >8h (rủi ro UX cao nhất, dễ gây hoang mang Admin).
2. **Warn #2** — quyết định rõ trip thủ công có qua `PENDING_APPROVAL` không.
3. **#3, #4 (Phụ xe)** — lọc động tài nguyên + loại trừ trùng lặp trên `trip-create-form`/`trip-edit-form`.
4. **Warn #4** — refactor `AdminTripManagementController` để không inject Repository trực tiếp.
5. **Incon #3** — dọn dead code `countBusyTripsAnyRole`/`findAllTripsByDriverOnDate`.
6. **Warn #5** — tách profile `dev`/`prod` cho `ddl-auto` (có thể để cuối vì chỉ ảnh hưởng khi deploy thật).