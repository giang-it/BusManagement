5. AI Scheduling

Đây là chỗ mình thích nhất.

Nhưng vẫn thiếu.

Theo code

hasAlreadySuggested()

rất quan trọng.

Spec không nói.

Trong code còn

CANCELLED

không block

nên AI có thể đề xuất lại.

Đây là business rule.

Nên thêm.

---

Update: đã thêm.

Xem current_functional_spec.md

mục "Duplicate-Suggestion Rule"

và trip_lifecycle_fsm.md

mục 6.1.

Đã ghi rõ: CANCELLED bị loại

khỏi blocking status có chủ đích,

để chuyến tăng cường cũ bị hủy

không chặn AI đề xuất lại.

---

# Lỗi phát hiện khi kiểm chứng Phase 3 & Phase 4 (2026-07-20)

Ghi lại tại đây thay vì sửa ngay, vì tất cả đều **nằm ngoài phạm vi Phase 3/4**
và sửa kèm sẽ trộn việc không liên quan vào commit của phase. Cần quyết định của
chủ dự án trước khi xử lý.

## 1. Bằng lái được kiểm tra theo NGÀY HÔM NAY, không theo ngày khởi hành

> **✅ ĐÃ SỬA (2026-07-20).** Thêm `Driver.isLicenseValid(LocalDate)`; bản không
> tham số giữ nguyên và chỉ còn dùng cho nhãn hiển thị ở danh sách tài xế. Toàn
> bộ 6 điểm kiểm tra trong `TripService` nay truyền ngày khởi hành, và
> `DriverRecommendationService` truyền ngày Admin chọn.
>
> **Phát hiện thêm khi sửa:** `findBestAvailableDriver()` vốn ĐÃ mâu thuẫn với
> chính nó — tầng ưu tiên (dòng 365) so bằng lái với `departure + 7 ngày`, trong
> khi bộ lọc gốc (dòng 352) so với hôm nay. Hậu quả: tài xế hết hạn trước ngày
> chạy lọt qua bộ lọc gốc, trượt tầng ưu tiên, rồi được nhánh **fallback** chọn
> kèm log `⚠️ bằng lái SẮP HẾT HẠN` — trong khi thực tế bằng đã hết hạn hẳn vào
> ngày khởi hành. Dòng 365 chính là bằng chứng ý định ban đầu của tác giả là
> tính theo ngày khởi hành; dòng 352 chỉ đơn giản là chưa được sửa theo.
>
> Kiểm chứng: chuyến khởi hành 2027-08-01 với tài xế hết hạn 2027-07-16 nay bị
> chặn (trước đây cho qua); chuyến khởi hành trước ngày hết hạn vẫn qua bình
> thường; endpoint cấp tài nguyên cho dropdown trả 0 tài xế thay vì 26 cho ngày
> sau hạn. Không có chuyến nào đang tồn tại trong DB bị ảnh hưởng.

- **Mức độ:** nghiệp vụ — đây là lỗi thật, không phải chuyện hiển thị.
- **Ở đâu:** `Driver.isLicenseValid()` (`domain/Driver.java`) so `licenseExpiryDate`
  với `LocalDate.now()`. Được dùng bởi `TripService.validateStaffForTrip()`,
  `findBestAvailableDriver()`, `getAvailableDriversForTrip()` và (kế thừa)
  `DriverRecommendationService`.
- **Hậu quả:** một chuyến khởi hành 2027-01-01 vẫn được duyệt cho tài xế có bằng
  hết hạn 2026-08-01, vì tại thời điểm bấm nút bằng vẫn còn hạn. Tài xế sẽ cầm
  lái bằng một giấy phép đã hết hiệu lực.
- **Vì sao chưa sửa:** phải sửa trong `TripService` — vùng mà Phase 3 và Phase 4
  đều bị cấm động vào. Nếu sửa, phải sửa **một chỗ duy nhất** (thêm overload
  `isLicenseValid(LocalDate)` và cho các validator truyền ngày khởi hành vào),
  tuyệt đối không thêm quy tắc riêng ở tầng đề xuất — sẽ thành hai định nghĩa
  "bằng còn hạn" khác nhau.
- **Liên quan:** `THESIS_ROADMAP.md` §9, ghi chú "Why the licence check in Phase 4
  is valid today, not valid on the selected date".

## 2. `#numbers.formatDecimal(x, 0, 1)` làm mất số 0 đứng đầu

- **Mức độ:** hiển thị, nhẹ.
- **Ở đâu:** `templates/admin/dashboard-analytics.html:357` (ô "Giờ lái hôm nay"
  của bảng `topLoadedDrivers`).
- **Hậu quả:** giá trị 0.4 hiển thị thành `.4` thay vì `0.4`. Tham số thứ hai là
  `minIntegerDigits`, đặt 0 nên phần nguyên bị bỏ khi bằng 0.
- **Cách sửa:** đổi tham số thứ hai thành `1`.
- **Vì sao chưa sửa:** bảng `topLoadedDrivers` là phần Phase 4 được yêu cầu rõ
  ràng là không được chạm vào. Thực tế lỗi hiếm khi lộ ra ở đây, vì tài xế có
  0.x giờ lái gần như không bao giờ lọt vào top 5 người bận nhất.
- **Đã sửa ở chỗ khác:** `templates/admin/driver-recommendation.html` (Phase 4)
  ban đầu mắc đúng lỗi này, phát hiện khi drive app và đã sửa thành `1`.

## 3. `02_project_context.md` §10 khẳng định sai về timestamp

- **Mức độ:** tài liệu.
- **Nội dung sai:** *"No 'Recent Activity'/audit-trail widget exists, because no
  entity has creation/update timestamps"*.
- **Thực tế:** Phase 0 đã thêm `Trip.createdAt`, Phase 2 đã thêm
  `Incident.reportedAt` — cả hai đều `@CreationTimestamp`.
- **Lưu ý khi sửa:** chỉ có **lý do** là sai; kết luận "chưa có widget Recent
  Activity" vẫn đúng và widget đó vẫn bị từ chối có chủ đích. Chỉ sửa mệnh đề
  nguyên nhân, đừng xóa cả câu.
- **Cùng loại với:** Hidden Cost #2 trong `THESIS_ROADMAP.md`, đã được sửa ngày
  2026-07-17 trong khi dòng này bị bỏ sót.

## 4. Scheduler AI in log liên tục khi rảnh

- **Mức độ:** vận hành.
- **Ở đâu:** `TripService.scanAndSuggestExtraTrips()` (`@Scheduled(fixedRate = 10_000)`).
- **Hậu quả:** dòng `[AI] Chuyến #N: còn … giờ đến khởi hành (< 72 giờ yêu cầu)…`
  in mỗi 10 giây cho mỗi chuyến, hàng trăm dòng trong vài phút, nhấn chìm log
  thật. Quan sát lại lần nữa ngày 2026-07-20 khi drive app (log đầy
  `[AI] Chuyến #3: đã khởi hành, bỏ qua.`).
- **Cách sửa gợi ý:** hạ xuống mức debug hoặc chỉ log khi trạng thái đổi. Không
  đổi `fixedRate` — đó là hành vi nghiệp vụ, không phải vấn đề log.
- **Đã ghi nhận trước đó:** `THESIS_ROADMAP.md` §8, mục ngày 2026-07-17.

## 5. `DispatchController` inject thẳng Repository

- **Mức độ:** kiến trúc, nhất quán.
- **Ở đâu:** `controller/admin/DispatchController.java:34` — `private final TripRepository tripRepository;`.
- **Vì sao là vấn đề:** `THESIS_ROADMAP.md` §3 quy định controller admin mới phải
  theo mẫu `AdminBusController` (chỉ inject Service), và nêu đích danh
  `AdminTripManagementController` là anti-pattern không được sao chép. Nhưng
  chính Phase 1 lại ship `DispatchController` với repository inject thẳng —
  roadmap tự mâu thuẫn với sản phẩm của nó.
- **Cách sửa:** chuyển `findDispatchBoardTrips(...)` vào `TripService` và cho
  controller gọi qua service. Thuần túy dời chỗ, không đổi truy vấn.
- **Vì sao chưa sửa:** đụng vào code Phase 1 đã được kiểm chứng, không liên quan
  gì tới Phase 4.