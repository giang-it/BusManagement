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

---

# Lỗi phát hiện khi rà soát toàn dự án + kiểm chứng Phase 8 (2026-07-30)

Rà theo yêu cầu chủ dự án ("đọc project xem có lỗi gì không"), rồi **đối chiếu
từng phát hiện với `THESIS_ROADMAP.md` §3/§5/§6/§9 và file này** để loại những
thứ đã được duyệt là *tính năng*. Ba mục còn lại dưới đây là lỗi thật; phần
"ĐÃ LOẠI" ở cuối ghi lại những gì đã kiểm và **không** phải lỗi, để không ai
nêu lại.

Chưa sửa gì. Chờ quyết định của chủ dự án.

## 6. Hoàn thành lại một chuyến ĐÃ hoàn thành → odometer cộng thêm lần nữa

> **✅ ĐÃ SỬA (2026-07-30).** Thêm guard `if (trip.getStatus() == newStatus) return;`
> trong `TripService.updateTripStatus()`, đặt **sau** `canTransition()` để giữ
> nguyên hợp đồng đã tài liệu hoá "Same state set again → Always allowed": lệnh
> vẫn được chấp nhận (không ném lỗi), chỉ không còn là một lần "vào" trạng thái
> nên không tác dụng phụ nào chạy lại. **`canTransition()` không bị sửa** — quy
> tắc `from == to → true` là có chủ đích và vẫn còn.
>
> Đây là sửa code cho khớp tài liệu, không phải đổi hành vi đã thoả thuận:
> `docs/architecture/trip_lifecycle_fsm.md` vốn đã ghi mọi tác dụng phụ là
> "Side effects on **ENTRY**", liệt kê "Same state set again" với đích là *(same)*,
> và state diagram không có self-loop nào. Doc đó nay được bổ sung một đoạn nói
> thẳng "no-op, no side effects" để không ai phải suy luận lại.
>
> **Pin bằng test:** `service/TripServiceStatusTransitionTest` (4 test) — cặp
> chính là "hoàn thành lại không cộng thêm km" + "DEPARTED→COMPLETED vẫn cộng
> đúng một lần" (chống sửa quá tay), cộng hai test giữ hợp đồng: set lại cùng
> trạng thái không ném lỗi, và transition sai vẫn bị whitelist chặn.
> `mvnw test` **25/25**. **Non-vacuous:** vô hiệu hoá guard → test regression đỏ
> với `expected: <1120.0> but was: <1360.0>` (đúng hai lần cộng thừa), 3 test còn
> lại vẫn xanh.
>
> **Kiểm chứng trên app thật** (default profile, PID 17052, đúng request đã gây
> lỗi): `POST /admin/dispatch/status` `tripId=2545&newStatus=COMPLETED` **ba lần
> liên tiếp** → odometer xe #23 đứng yên **1080** cả ba lần (trước fix: một lần
> đã thành 1200). Transition sai vẫn bị chặn với nguyên thông điệp cũ
> (`[COMPLETED] sang [ACTIVE]`). 15/15 trang admin 200, 0 lỗi template/SpEL.
> **Lần verify này không phải hoàn nguyên gì cả** — chính là bằng chứng lỗi đã
> hết: snapshot DB và tổng odometer (228325 / 209970) giống hệt trước/sau.
>
> **Guard trùng ở `AdminTripManagementController:261` được giữ lại** có chủ đích:
> nó chỉ là early-out tránh một lần gọi service vô nghĩa, không phải bản sao của
> luật; xoá đi là churn trên code Phase 1 đã kiểm chứng mà không đổi hành vi gì.

- **Mức độ:** nghiệp vụ, **làm sai dữ liệu bền trong DB**. Nặng nhất trong lần rà này.
- **Ở đâu:** `service/TripService.java` — `canTransition()` (dòng 536-538) trả
  `true` khi `from == to` ("Giữ nguyên trạng thái luôn hợp lệ"), nhưng khối đồng bộ
  `Bus` (dòng 569-586) chỉ xét `newStatus == COMPLETED`, **không** xét chuyến có
  thật sự vừa chuyển trạng thái hay không. Lối vào:
  `controller/admin/DispatchController.java:83-88` (`POST /admin/dispatch/status`),
  không có guard; nút bấm ở `templates/admin/dispatch-board.html:140-145` là form
  POST trần, **không bị disable sau khi bấm** → double-click là 2 request thật.
- **Hậu quả:** `Bus.odometer` cộng thêm `Route.distanceKm` mỗi lần gọi. Vì
  `kmSinceLastMaintenance = odometer − lastMaintenanceOdometer` (`domain/Bus.java:43-45`),
  sai số lan sang `needsMaintenance()`/`isNearMaintenance()` → xe tốt bị **loại
  khỏi `findBestAvailableBus()`**, cảnh báo bảo dưỡng sai, và màn Đề Xuất Thay Xe
  (xếp hạng 70% theo odometer lifetime) sai thứ tự. Tải lại trang không hết —
  phải sửa tay trong DB.
- **Đã tái hiện trên app thật (2026-07-30, default profile, PID 20924, DB đã hoàn nguyên):**
  chuyến #2545 đang ở `COMPLETED`; `POST /admin/dispatch/status` với
  `tripId=2545&newStatus=COMPLETED` → **302 kèm flash "Đã cập nhật chuyến #2545
  sang trạng thái COMPLETED."** (báo THÀNH CÔNG, không phải lỗi); xe #23
  `odometer` **1080 → 1200** (= đúng 120 km của tuyến),
  `km_since_maintenance` **0 → 120**, `last_maintenance_odometer` không đổi.
  Đã `UPDATE` trả về 1080; snapshot DB trước/sau giống nhau.
- **Vì sao đây là lỗi, không phải tính năng:** ý định thiết kế là tác dụng phụ của
  **một lần chuyển trạng thái** — `THESIS_ROADMAP.md` §8 (2026-07-16, Phase 1) ghi
  *"`DEPARTED→COMPLETED` set the bus back to `READY` and added the route distance
  to the odometer (500→620 for a 120 km route)"*, và `.claude/skills/verify/SKILL.md`
  cũng diễn đạt theo cặp transition. Roadmap **không** có chỗ nào nói về
  `canTransition` khi `from == to`, về tính idempotent của `updateTripStatus()`,
  hay chấp nhận cộng lặp (grep "canTransition" / "from == to" / "idempot" — mọi
  ghi chú idempotent đều thuộc backfill Phase 5). Quyết định "giữ nguyên trạng
  thái là hợp lệ" bản thân nó là có chủ đích, nhưng việc **chạy lại tác dụng phụ**
  thì không được ghi ở đâu.
- **Bằng chứng nội tại mạnh nhất:** lối gọi còn lại **đã chặn đúng** —
  `AdminTripManagementController.java:261` kiểm `if (existingTrip.getStatus() != newStatus)`
  trước khi gọi. Tức chính codebase đã có quy ước "đừng gọi khi không có gì đổi";
  đường Dispatch chỉ là **sót**. Ngoài ra Hidden Cost #7 cho thấy dự án coi số học
  odometer là chỗ trọng yếu (*"do not 'simplify' it to a single-column update"*).
- **Cách sửa:** early-return trong `updateTripStatus()` khi
  `trip.getStatus() == newStatus` (1 dòng), hoặc gate khối tác dụng phụ theo cặp
  (trạng thái cũ, trạng thái mới). Ưu tiên cách 1 — nó cũng làm hai lối gọi hành xử
  giống nhau.
- **Vì sao chưa sửa:** là code đã commit từ Phase 1, **không thuộc Phase 8**. Phải
  là commit riêng, không trộn vào Phase 8.
