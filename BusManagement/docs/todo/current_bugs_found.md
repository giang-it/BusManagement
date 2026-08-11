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

## 7. What-if: năng lực bị gộp cho cả chân trời nên báo "đủ năng lực" khi đang thiếu

> **✅ ĐÃ SỬA (2026-07-30).** Độ phủ nay đối chiếu **theo từng ngày**:
> `coverable = floor(Σ_ngày min(số khung đông của ngày đó, servablePerDay))`.
> Năng lực nhàn rỗi của ngày vắng không còn "cho vay" sang ngày cao điểm.
>
> **Hai quyết định làm nên tính đúng đắn của bản sửa:**
> 1. **Làm tròn xuống MỘT LẦN ở cuối, không từng ngày.** `servablePerDay` là tỉ lệ
>    thực của mô hình cycle-time — 7,5 chuyến/ngày nghĩa là "có ngày 7, có ngày 8".
>    Chặt xuống 7 mỗi ngày sẽ vứt bỏ năng lực có thật **và** làm sai lệch các con số
>    roadmap đã kiểm chứng (200%+2xe sẽ thành 49 thay vì 52). Nhờ chọn cách này,
>    **mọi số đã ghi nhận đều không đổi**.
> 2. **Tập khung dùng cho phần tài chính cũng chọn theo ngày** (`selectCoveredSlots`),
>    nếu không con số tiền sẽ nói về một kế hoạch khác với con số độ phủ ngay trên nó.
>    Trần mỗi ngày làm tròn LÊN, tổng bị chặn cứng bởi `coverable`.
>
> Phép tính được tách thành hàm thuần package-private
> `WhatIfSimulationService.coverableSlots(List<LocalDate>, double)` để test chốt
> trực tiếp — cùng lý do `TripService.isBusBusy`/`getDrivingHoursForDate` là
> package-private. `WhatIfOutcomeDto.servableOverHorizon` **đã bị xoá**: nó chưa
> từng được render ra màn nào, chỉ tồn tại để tính `coverable` theo công thức gộp,
> và để lại một con số "năng lực cả chân trời" trong DTO chính là mời lỗi này quay
> lại.
>
> **Pin bằng test:** `service/WhatIfCoverageTest` (11 test, JUnit thuần — hàm thuần
> nên không cần Spring context). Dữ liệu test là số **đo được trên app thật**: phân
> bố 17 khung `[1,6,6,1,1,1,1]` và phân bố đều `15×7` của nhu cầu 200%. Bốn test
> chốt bản sửa, bốn test control chốt **các con số roadmap không đổi** (52 và 74),
> một test ghi lại chính xác sự khác biệt với công thức cũ, hai test biên.
> `mvnw test` **36/36**. **Non-vacuous:** quay `coverableSlots` về công thức gộp →
> đúng 4 test đỏ, tất cả đều `was: <17>` (con số sai của công thức cũ), 7 test
> control vẫn xanh — tức test vừa bắt được lỗi vừa bảo vệ số cũ.
>
> **Kiểm chứng trên app thật** (default profile, PID 22612):
> | Cần gạt | Trước fix | Sau fix |
> |---|---|---|
> | Δtx −32 (→1 tài xế) | 100% "Đủ năng lực" | **58,8%**, thiếu 7, **nút thắt DRIVER** |
> | Δtx −31 (→2 tài xế) | 100% "Đủ năng lực" | **88,2%**, thiếu 2, DRIVER |
> | Δxe −12 (→1 xe) | 100% "Đủ năng lực" | **70,6%**, thiếu 5, **nút thắt BUS** |
> | baseline | 100%, 18.850.000đ | **100%, 18.850.000đ** (không đổi) |
> | Δxe −11 (→2 xe) | 100% | **100%** (không đổi) |
> | nhu cầu 130% | 71 khung, 100% | **71 khung, 100%** (không đổi) |
> | 200% + Δxe −11 | 49,5% | **49,5%** ✓ đúng số roadmap |
> | 200% + Δtx −29 | 70,5% | **70,5%** ✓ đúng số roadmap |
>
> Ba con số sửa được khớp **chính xác** dự đoán 58,8 / 88,2 / 70,6 tính từ ma trận
> dự báo. 10/10 trang admin 200, 0 lỗi template/SpEL, DB giống hệt trước/sau
> (`20/36/1380/8/8/1/37`, tổng odometer `228325/209970`).
>
> **Thêm minh bạch trên màn:** thẻ "Throughput được suy từ dữ liệu" nay có một dòng
> nói rõ độ phủ đối chiếu theo từng ngày và vì sao năng lực ngày vắng không chuyển
> sang ngày cao điểm — theo đúng nguyên tắc "state the boundary rather than
> approximate across it" của §9.

- **Mức độ:** nghiệp vụ — ra **con số sai** trên màn Decision Support.
- **Ở đâu:** `service/WhatIfSimulationService.java:200-216`.
  `servableOverHorizon = floor(servablePerDay × horizonDays)` rồi
  `coverable = min(hotSlots, servableOverHorizon)`. Khung đông (`hotSlots`) là
  **theo từng ngày** (chuỗi × ngày), nhưng phép so sánh diễn ra trên **bể 7 ngày**
  ⇒ năng lực nhàn rỗi của ngày thường được "cho vay" sang cuối tuần.
- **Dữ liệu thật làm lỗi này lộ ra:** 17 khung đông của baseline phân bố
  T6 1 / **T7 6** / **CN 6** / T2 1 / T3 1 / T4 1 / T5 1 — **12/17 (70%) dồn vào
  cuối tuần**, đúng theo hệ số mùa vụ cuối tuần (~1,19) mà chính `ForecastService`
  đo được. Đây là phân bố lệch, tức là trường hợp phép gộp nói dối.
- **Đã đo trên app thật (2026-07-30, PID 46552):** mô hình gộp mà tôi dựng lại
  khớp app **6/6** kịch bản (gồm 3 control), nên con số đối chứng "tính theo ngày"
  là đáng tin:
  | Cần gạt | Màn hiện | Nút thắt màn báo | Tính theo ngày |
  |---|---|---|---|
  | Δtài xế −32 (33→**1**) | **100.0%** | Đủ năng lực | **58.8%** (phải là DRIVER) |
  | Δxe −12 (13→**1**) | **100.0%** | Đủ năng lực | **70.6%** (phải là BUS) |
  | Δtài xế −31 (33→2) | **100.0%** | Đủ năng lực | **88.2%** |
  | Δxe −11 (13→2) | 100.0% | Đủ năng lực | 100% ✔ control |
  | Δxe −12, nhu cầu 130% | 36.6% | Thiếu xe | 36.6% ✔ control |
  Nói cách khác: màn khẳng định **1 tài xế phủ trọn 17 chuyến tăng cường trong 7
  ngày và "không bị bó"**.
- **Biên kích hoạt:** chỉ khi `servablePerDay` < số khung của ngày cao nhất (= 6),
  tức **≤1 xe** hoặc **≤2 tài xế**. Baseline và mọi mức vận hành bình thường **không**
  bị. Nên các con số roadmap đã ghi cho Phase 8 vẫn đúng.
- **Vì sao verification Phase 8 không bắt được:** roadmap kiểm ở nhu cầu 130%/200%,
  khi đó khung đông trải **đều** cả 7 ngày (71 rồi 105 = 15/ngày) — chính là phân bố
  duy nhất mà gộp-cả-tuần và tính-từng-ngày cho **cùng một số**. Lỗi chỉ hiện ở
  nhu cầu **100%**, tức mức mặc định khi mở trang.
- **Vì sao đây là lỗi, không phải simplification đã duyệt:** §9 duyệt cho Phase 8
  đúng **hai** phép đơn giản hoá — (a) `servable` là năng lực **gộp**, không trừ
  lịch chạy thường xuyên; (b) "max trips/bus/day observed" hoãn lại. Việc **gộp
  năng lực theo ngày thành bể cả chân trời** không thuộc cả hai, không được nêu ở
  §5 Design, và **không có trên honesty banner**. Nó còn đi ngược đúng nguyên tắc
  ghi ở cuối ghi chú (a): *"state the boundary rather than approximate across it"* —
  đây là approximate across, và im lặng.
- **Cách sửa:** gom `hot` theo ngày rồi cộng `min(sốKhungCủaNgày, servablePerDay)`
  qua các ngày (~5 dòng trong `computeOutcome`). Sau khi sửa, 3 dòng sai ở trên
  phải thành 58.8 / 70.6 / 88.2 và 2 control giữ nguyên.
- **Vì sao chưa sửa:** thuộc Phase 8 **chưa commit** — nên sửa trước khi commit,
  gộp cùng phần việc Phase 8.

## 8. `THESIS_ROADMAP.md` §9 mô tả một hành vi mà code Phase 8 không có

> **✅ ĐÃ SỬA (2026-07-31) — theo cách (b), chủ dự án chọn: sửa TÀI LIỆU, giữ
> nguyên code.** Câu ở §9 (roadmap dòng 777) đã được viết lại cho khớp code:
> ngưỡng 0.90 được **khai lại tại chỗ** (`WhatIfSimulationService`
> `REINFORCEMENT_THRESHOLD`, dòng 105) và dùng cho **cả hai cột ở mọi hệ số nhu
> cầu** — không có nhánh nào đọc `ForecastPointDto.needsReinforcement`. Kèm theo
> là ba ghi chú con: mốc sửa (nói rõ câu cũ sai ở nửa nào), **vì sao** Phase 8 bắt
> buộc phải tự tính lại, và cảnh báo tripwire.
>
> **Kiểm chứng lại trước khi sửa (2026-07-31), tĩnh — không cần chạy app:**
> - Grep `isNeedsReinforcement()` trên toàn `src/main/java`: chỉ có
>   `ForecastService` (2 nơi, dùng nội bộ) và `RecommendationService:128`.
>   `WhatIfSimulationService` **không** có trong danh sách — nó chỉ *nhắc tới* cờ
>   đó trong comment ở dòng 103. Xác nhận doc ≠ code.
> - `WhatIfSimulationService.computeOutcome()` dòng **231-238** (bug report cũ ghi
>   205-212 — số dòng đã dịch sau khi sửa lỗi #7) tự tính `scaled >
>   REINFORCEMENT_THRESHOLD`, không rẽ nhánh theo hệ số.
> - `new ForecastPointDto(...)` chỉ được dựng ở **đúng một chỗ**
>   (`ForecastService:312`), cờ luôn = `predicted > REINFORCEMENT_THRESHOLD`.
>
> **Vì sao chọn (b) chứ không phải (a) — điểm quyết định, tìm ra khi kiểm chứng:**
> cách (a) **không đạt được chính mục tiêu của câu văn đó**. Muốn code đọc cờ ở hệ
> số 100% thì phải viết `(demandFactorPercent == 100) ? point.isNeedsReinforcement()
> : scaled > REINFORCEMENT_THRESHOLD` — hằng số **vẫn phải giữ** cho nhánh còn lại,
> nên "bản sao thứ tư" không hề biến mất; đổi lại là hai đường code trả lời cùng
> một câu hỏi. Lý do sâu hơn: cờ `needsReinforcement` là quyết định được nướng sẵn
> ở **một** mức nhu cầu — mức của chính dự báo. Phase 7 hỏi đúng ở mức đó nên tiêu
> thụ được quyết định; Phase 8 sinh ra để hỏi *"nếu nhu cầu 130% thì sao"*, câu mà
> cờ boolean đó không trả lời được. Việc What-if tự tính lại là **bắt buộc**, không
> phải cẩu thả.
>
> **Số liệu không đổi (đã chứng minh, không phải phỏng đoán):** ở hệ số 100%,
> `factor = 100/100.0` đúng bằng `1.0`; nhân double với `1.0` là phép **không làm
> tròn** trong IEEE-754; `ForecastService.predict()` (dòng 377) đã kẹp `[0,1]` nên
> `Math.min(1.0, …)` của màn What-if không cắt gì; hai bên cùng so với cùng literal
> `0.90`. ⇒ `hotSlots` của cột hiện trạng **luôn** bằng
> `DemandForecastViewDto.reinforcementSignalCount`, khớp con số 17 đã đo trên app
> và đúng bằng 17 thẻ của Phase 7. (Kể cả ca biên NaN hai bên cũng cùng cho `false`.)
>
> **Tripwire đã ghi vào §9:** vì hai màn tới cùng một đáp số bằng **hai đường khác
> nhau**, nếu sau này ai đổi phép so trong `ForecastService` (`>` → `>=`) hoặc bỏ
> phép kẹp trong `predict()` thì đẳng thức trên **vỡ trong im lặng**. Phải kiểm cả
> hai cùng lúc.
>
> **Không có dòng code nào bị sửa** — chỉ `THESIS_ROADMAP.md` (§9 + §7 + một mục
> Session Log mới) và file này. Thuộc commit của Phase 8.

- **Mức độ:** tài liệu — nhưng là tài liệu **sẽ được đem đi bảo vệ**.
- **Nội dung §9 (ghi chú "Phase 8 reuses decisions/constants, does not fork them",
  2026-07-25):** *"the 0.90 reinforcement threshold **is read via
  `ForecastPointDto.needsReinforcement` at factor = 100%**; for factor ≠ 100% it is
  redeclared locally with a pointer comment"*.
- **Thực tế code:** `WhatIfSimulationService.computeOutcome()` (dòng 205-212) **luôn**
  tự tính lại bằng hằng số nội bộ `REINFORCEMENT_THRESHOLD` cho **cả hai cột**;
  nó **không hề** đọc `ForecastPointDto.needsReinforcement`, kể cả ở hệ số 100%.
- **Có sai số không: KHÔNG.** Ở hệ số 100%, `factor = 100/100.0` đúng bằng `1.0`
  nên `predicted × 1.0 == predicted` chính xác trong IEEE-754, và cả hai nơi đều
  dùng `> 0.90` ⇒ `hotSlots` baseline **luôn** bằng
  `DemandForecastViewDto.reinforcementSignalCount`. Đã kiểm trên app: baseline 17,
  khớp Phase 7. Đây thuần là chuyện tài liệu ≠ code.
- **Vì sao đáng ghi:** CLAUDE.md yêu cầu *"Documentation should always match
  implementation"*, và nửa sau của chính câu đó (redeclare khi hệ số ≠ 100%) **đúng
  và đã được duyệt** — nên câu này dễ bị tin là đã kiểm chứng cả hai nửa.
- **Hai cách sửa, chọn một:** (a) cho code đọc `needsReinforcement` ở hệ số 100%
  đúng như §9 nói — sạch hơn, và trùng khuôn "engine tiêu thụ quyết định, không
  tiêu thụ con số" mà §9 khen ở Phase 7; hoặc (b) sửa lại câu đó trong roadmap cho
  khớp code. **Cách (b) cần chủ dự án phê duyệt** (Rule 4: không tự sửa roadmap).
- **Vì sao chưa sửa:** chờ chủ dự án chọn (a) hay (b).

## Mục nhỏ, ghi để không bị quên

- **`TripRepository.findScheduleForAvailability` javadoc (dòng 420-425) nêu lý do đã
  hết hiệu lực:** vẫn giải thích việc không `JOIN FETCH` `coDrivers` là vì *"@Data
  trên Trip/Driver/User sinh hashCode đệ quy hai chiều → StackOverflow"*. Nguyên
  nhân đó đã bị loại bỏ ngày 2026-07-24 (`2e101f9`, equals/hashCode theo id).
  **Quyết định vẫn đúng** (fetch bag + `DISTINCT` vẫn là vấn đề riêng), chỉ lý do
  là lạc hậu. Cùng loại với defect #3.
- **What-if: khung thiếu giá vé lịch sử vào chi phí nhưng không vào doanh thu**
  (`WhatIfSimulationService.java:242-262`) → lợi nhuận bị hụt, màn không nói có bao
  nhiêu khung được định giá. Phase 7 không bị vì thẻ thiếu giá thì `profit = null`.
  Hiện chưa cắn: cả 15 chuỗi đều cần ≥28 chuyến `COMPLETED` tại (tuyến, giờ) đó và
  `findPriceHistoryByStatus` đã lọc `price IS NOT NULL`.
- **What-if: công thức throughput thoái hoá nếu chỉ còn 1 khung giờ đủ điều kiện**
  (`deriveThroughput`, dòng 294-302): `operatingWindow = maxHour + avgDur − minHour`
  → còn đúng `avgDur` → `tripsPerBusPerDay = avgDur/(avgDur+1) < 1`, tức một xe không
  chạy nổi một chuyến/ngày. Chưa xảy ra (dữ liệu có 06/12/18 → window 15.0h, đã đo).
  `MIN_OBSERVATIONS = 28` không bảo đảm có ≥2 khung giờ.
- **What-if: mọi hệ số nhu cầu từ 50% đến 90% đều cho kết quả y hệt nhau** (0 khung
  đông, cả cột thành "—", badge xanh "Đủ năng lực"). Toán học: cần
  `predicted × factor > 0.90` mà `ForecastService.predict()` kẹp `predicted ≤ 1.0`
  ⇒ phải có `factor > 0.90`. Đã kiểm ở 90% và 70%: giống hệt.
  **Không phải lỗi tính toán** — nhu cầu giảm thì thật sự không còn chuyến cần tăng
  cường, đó là câu trả lời đúng. Chỉ là 41/251 giá trị kéo được cho một màn rỗng
  giống nhau và cái badge xanh trở nên vô nghĩa. Sửa thì phải đụng vào ý nghĩa
  "khung đông" ở mức nhu cầu giảm ⇒ **quyết định của chủ dự án**, không tự sửa.

## ĐÃ LOẠI sau khi đối chiếu — không phải lỗi, đừng nêu lại

- **Phase 8 khai lại các hằng số `0.90` / `8.0` / `PREP_BUFFER_HOURS`:**
  `THESIS_ROADMAP.md` §9 **duyệt đích danh** — *"a fourth copy consistent with the
  established pattern, not a new definition"*, theo tiền lệ
  `DashboardService`/`ForecastService`/`DriverRecommendationService` và
  `DispatchController`/`RecommendationService`, vì bản gốc là `private` trong
  `TripService` và mở rộng visibility cho một màn chỉ-đọc không đáng đánh đổi.
  **Là tính năng.** (Xem thêm #8: chỉ nửa "đọc cờ ở hệ số 100%" của ghi chú đó là
  chưa được hiện thực.)
- **Nhân bản `estimateCost` và `loadLatestPrices()` sang `WhatIfSimulationService`:**
  đúng là nhân bản (12 dòng `loadLatestPrices` giống từng chữ, cùng công thức chi
  phí), và roadmap không nói tới. **Nhưng** cả hai đều `private` trong
  `RecommendationService`, nên tái dùng đòi hỏi đúng cái mở-rộng-visibility mà chủ
  dự án **đã từ chối** cho hằng số ⇒ nhất quán về tinh thần với một khuôn đã duyệt.
  Và đã kiểm: hai bản ra **cùng con số** (khác biệt duy nhất là `round(Σ)` so với
  `Σ round()`, lệch < 1đ/khung). **Không tính là lỗi**; nếu muốn gom về một chỗ thì
  đó là quyết định kiến trúc của chủ dự án.
- **Dòng "Tổng ghế đội xe" tự mâu thuẫn với dòng "Xe khả dụng":** dự đoán ban đầu
  là Δxe = −13 sẽ ra 0 xe mà vẫn còn ghê. Đo thật: `AVG(capacity)` toàn đội = 35.30,
  riêng READY = 32.6154, `baselineSeats` = 424 ⇒ `max(0, 424 − 13×35.30)` = **0**.
  App xác nhận `xe=0 ghe=0`. **Không xảy ra.** Còn lại chỉ là lệch hướng nhỏ ở một
  dòng **chỉ để hiển thị** (`totalSeats` không tham gia phép tính nào).
- **Sức chứa tính doanh thu lấy trung bình cả đội (kể cả xe đang sửa):** SQL cho
  thấy **8/8 tuyến đều có `suitable_bus_type_id`** (không tuyến nào NULL), nên
  `route.getSuitableBusType()` luôn non-null và `avgFleetCapacity` **không bao giờ**
  được dùng làm sức chứa doanh thu. **Phán đoán ban đầu sai.**
- **`bottleneck` báo NONE khi `hotSlots = 0`:** là hệ quả trực tiếp của mục
  "nhu cầu ≤90%" ở trên, không phải lỗi riêng.

---

# Lỗi phát hiện khi kiểm chứng commit sửa lỗi render UI (2026-07-31)

Phát hiện khi drive nhánh MANUAL MODE của màn phê duyệt, trong lúc kiểm chứng một
commit **chỉ đụng tầng view** (viewport/charset/icon/table-responsive/empty-state).
Lỗi này **không do commit đó gây ra** — đã đối chứng A/B trực tiếp với code gốc,
xem phần bằng chứng. Chưa sửa. Chờ quyết định của chủ dự án.

## 9. Màn phê duyệt ở MANUAL MODE vỡ giữa chừng vì inline entity `Driver` vào JavaScript

> **✅ ĐÃ SỬA (2026-08-03).** Controller không đẩy entity sang JS nữa: thêm
> `AdminTripController.toDriverOptionsForJs()` dựng `List<Map<String, Object>>`
> phẳng gồm đúng 3 khoá JS đọc (`userId`, `fullName`, `totalDrivingHours24h`),
> đưa sang view dưới tên **mới** `approveDriversForJs`. `availableDrivers` giữ
> nguyên cho các dropdown `th:each` phía HTML — chúng chỉ đọc thuộc tính phía
> server, không serialize, nên vốn không dính lỗi. Template đổi 2 dòng:
> dòng 341 bind sang `${approveDriversForJs}`, dòng 383 đọc `d.fullName` thay cho
> `d.user.fullName`. Đúng khuôn `driversForJs` mà
> `AdminTripManagementController.showEditTripForm()` đã dùng từ trước.
>
> **Giữ nguyên khoá `totalDrivingHours24h` và giá trị thô của nó** (không đổi tên,
> không ép về 0) để dropdown tài xế phụ hiển thị **cùng con số** với dropdown tài
> xế chính ngay phía trên — cả hai cùng đọc trường mock-seed trên entity.
> **Đã cân nhắc và loại `DriverWorkloadDto`** dù nó cũng có đúng 3 trường
> (`userId`/`fullName`/`drivingHoursToday`): trường giờ của nó được
> `DashboardService:238-245` tính bằng `getDrivingHoursForDate()` (giờ **tính từ
> chuyến**), khác hẳn `Driver.totalDrivingHours24h` (**mock seed**, xem
> `TripService:728-741`). Tái dùng sẽ khiến hai dropdown trên cùng một màn hiện
> hai con số khác nhau cho cùng một tài xế — đúng cái bẫy Phase 4 đã ghi ở §9
> roadmap.
>
> **Kiểm chứng trên app thật** (default profile, JVM PID 1192, port 8099): tạo
> chuyến tạm id **2643** (`route_id=2`, `bus_id`/`driver_id` = NULL) để ép vào
> MANUAL MODE — cần thiết vì cả 1380 chuyến đang có đều đủ xe + tài xế.
> Response giờ **hoàn chỉnh**: có cả `</body>` lẫn `</html>` (trước đây thiếu cả
> hai), 23.459 byte, kết thúc sạch. Payload JS parse được bằng `json.loads`:
> **31 phần tử, mỗi phần tử đúng 3 khoá, 0 object lồng nhau**; chuỗi `"user"`,
> `"driver"`, `"password"` xuất hiện **0 lần** trong toàn trang. Đối chiếu từng
> dòng với SQL độc lập: **0 sai lệch** tên lẫn giờ. Hai dropdown khớp nhau: cả
> hai badge đều báo *31 người hợp lệ*, và số giờ trong payload JS **khớp 31/31**
> với số giờ dropdown chính in ra. Log: `StackOverflowError` **0**,
> `HttpMessageNotWritableException` **0**, tổng exception **0**.
> **Hồi quy AUTO MODE:** trip 7 và 8 vẫn 200 và render đủ thẻ đóng; ở nhánh này
> biến JS ra `null` — **giống hệt trước khi sửa** (bản cũ cũng chỉ set
> `availableDrivers` trong nhánh MANUAL), và vô hại vì thẻ
> `id="coDriverRequirementInfo"` không tồn tại ở AUTO MODE (đếm được: 0 thẻ HTML,
> 1 chuỗi trong JS) nên guard `neededCoDrivers > 0 && infoDiv` chặn, `forEach`
> không bao giờ chạy. Đã xoá chuyến 2643 (`ROW_COUNT()=1`); snapshot DB trước/sau
> **giống hệt** (trips 1380, buses 20, drivers 36, stations 11, routes 8,
> incidents 8, users 37, pending 2). `mvnw test` **36/36 BUILD SUCCESS**.
>
> **Đối chứng A/B với code gốc (2026-08-03, cùng URL, cùng lúc, 2 JVM riêng —
> PID 9260 bản sửa ở cổng 8099, PID 14356 bản gốc `c044276` ở cổng 8098):**
> bản gốc trả **257.617 byte, thiếu cả `</body>` lẫn `</html>`, 2
> `StackOverflowError`**; bản sửa **23.459 byte, đủ thẻ đóng, 0 lỗi**. Quan
> trọng nhất: hai response **giống hệt nhau 17.490 byte đầu** — tức toàn bộ phần
> HTML, mọi dropdown, mọi nhãn — rồi lệch **đúng tại ký tự đầu tiên bên trong
> `const approveDriversForJs = [{"`**, không sớm hơn một byte nào. Đây là bằng
> chứng máy móc rằng thay đổi **chỉ** chạm vào payload JS.
>
> *Ghi chú đính chính (viết sau khi kiểm lại):* thoạt đầu ghi rằng bản cũ **rò
> `User.password`** ra HTML. **Sai — đã kiểm và bác bỏ:** chuỗi `"password"`
> xuất hiện **0 lần** trong cả 257.617 byte của bản gốc. Lý do: Thymeleaf
> serialize thuộc tính theo thứ tự chữ cái, nên vào `user` là gặp ngay `driver`
> (đứng trước `password`) và tái nhập vòng lặp — stack vỡ ở độ sâu 690 vòng
> **trước khi** kịp chạm tới `password`. Đúng bản chất: `User.password`
> (`User.java:28`, không `@JsonIgnore`) **nằm trong đồ thị có thể bị serialize**,
> nhưng thực tế chưa bao giờ bị in ra. Bản sửa loại bỏ nguy cơ tiềm ẩn đó, không
> phải một vụ rò rỉ đã xảy ra.
>
> **Đã drive luồng ghi thật (không chỉ GET):** dùng chuyến tạm **2644** dài 10h
> (nên bắt buộc có tài xế phụ, ép đúng vào khối JS từng hỏng).
> (a) *Nhánh lỗi:* submit thiếu phụ xe → 302 quay lại trang approve, flash
> `⛔ Vi phạm ràng buộc: Chuyến xe kéo dài trên 8 tiếng bắt buộc phải có phụ xe!`,
> DB **không đổi** (`bus_id`/`driver_id` vẫn NULL). (b) *Nhánh thành công:*
> submit đủ (bus 23, tài xế 2, tài xế phụ 3, phụ xe 5) → 302 về
> `/admin/trips/pending`, flash `✅ Chuyến xe #2644 đã được phân công và kích
> hoạt thành công!`, DB ghi đúng **bus 23 / driver 2 / assistant 5 / ACTIVE /
> `sale_opened_at` được đóng dấu**, và bảng `trip_co_drivers` có đúng dòng
> `(2644, 3)`. Tức màn hình này giờ **dùng được thật**, không chỉ render đẹp.
> Đã xoá cả dòng join lẫn chuyến (mỗi lệnh `ROW_COUNT()=1`); snapshot DB
> trước/sau khớp tuyệt đối (thêm `trip_co_drivers` 3/3), và đội xe không bị đụng
> — bus 16 vẫn 4.995 km NEAR, bus 17 vẫn 6.000 km OVERDUE đúng như Hidden Cost
> #7 ghi (phê duyệt chỉ đưa chuyến sang ACTIVE, odometer chỉ đổi ở
> DEPARTED→COMPLETED).

- **Mức độ:** kỹ thuật, **hỏng hẳn một màn chức năng**. Trang trả HTTP 200 nhưng
  response bị cắt cụt và JS không bao giờ chạy — nên nhìn qua tưởng bình thường.
- **Ở đâu:** `templates/admin/approve-form.html:341` —
  `const approveDriversForJs = /*[[${availableDrivers}]]*/[];` nhét thẳng
  `List<Driver>` (entity JPA) vào khối `th:inline="javascript"`. Thymeleaf
  `StandardJavaScriptSerializer` duyệt đồ thị đối tượng và rơi vào vòng vô hạn:
  `Driver.user` (`domain/Driver.java:25-28`, `@OneToOne @MapsId`) →
  `User.driver` (`domain/User.java:72-73`, `@OneToOne(mappedBy = "user")`) →
  `Driver.user` → … **Không bên nào có `@JsonIgnore`.**
- **Chỉ xảy ra ở MANUAL MODE:** `availableDrivers` chỉ được nạp khi
  `!isAutoAssigned` (`controller/admin/AdminTripController.java:63-66`), với
  `isAutoAssigned = (trip.getBus() != null && trip.getDriver() != null)` (dòng 60).
  Nhánh AUTO không nạp biến này nên không đụng tới serializer.
- **Vì sao chưa ai gặp:** **cả 1380 chuyến trong DB đều có đủ `bus_id` và
  `driver_id`** (query `WHERE bus_id IS NULL OR driver_id IS NULL` → 0 dòng), nên
  MANUAL MODE hiện **không thể chạm tới bằng bất kỳ URL nào**. Nó chỉ hiện ra khi
  auto-assign thất bại — đúng lúc Admin cần nó nhất.
- **Hậu quả:** `java.lang.StackOverflowError` ngay giữa lúc ghi response. Thân
  trang HTML render đủ (dropdown xe/tài xế có 71 `<option>` thật), nhưng response
  **thiếu cả `</body>` lẫn `</html>`**, cắt ngang giữa chuỗi JSON.
  `approveDriversForJs` không bao giờ được gán ⇒ toàn bộ JS tài xế phụ chết: nút
  "Thêm tài xế phụ" (`onclick="addCoDriverSlotApprove(null)"`, dòng 283) bấm sẽ
  `ReferenceError`, vòng `approveDriversForJs.forEach` (dòng 382) không chạy.
  Tomcat log thêm `HttpMessageNotWritableException: ... response committed already`.
- **Đã tái hiện trên app thật (2026-07-31, default profile, DB đã hoàn nguyên):**
  vì không có chuyến MANUAL nào, phải tạo tạm `trips` id **2642** (`route_id=2`,
  `PENDING_APPROVAL`, `bus_id`/`driver_id` = NULL) rồi mở
  `GET /admin/trips/approve/2642`. 200 ký tự cuối của response lộ nguyên vòng lặp:
  `..."user":{"driver":{"experienceYears":5,..."user":{"driver":{...`. Đã `DELETE`
  đúng dòng đó (`ROW_COUNT()=1`); snapshot DB trước/sau giống hệt (trips 1380,
  buses 20, drivers 36, stations 11, routes 8, incidents 8, pending 2).
- **Bằng chứng đây là lỗi CÓ SẴN, không do commit sửa render:** chạy đối chứng A/B
  cùng lúc, cùng một URL — code gốc (`git worktree --detach temp` = 355917b, working
  tree **sạch**, port 8098) và bản đã sửa (port 8099) **đều StackOverflowError**.
  Thêm nữa, `git show temp:...approve-form.html` cho thấy dòng 341 **giống hệt từng
  ký tự**; diff của commit render trong file này chỉ có 5 dòng (2 thẻ `<meta>`, 1
  `<link>` bootstrap-icons, 3 lần đổi tên class trên thẻ `<i>`), không chạm khối
  `<script>`.
- **Bằng chứng nội tại mạnh nhất — trang anh em đã làm ĐÚNG:**
  `AdminTripManagementController.java:174-181` (màn sửa chuyến, cùng nhiệm vụ chọn
  tài xế) **không** đẩy entity sang view mà dựng `List<Map<String, Object>>` phẳng
  chỉ gồm `userId` + `fullName`, kèm comment sẵn *"Đẩy danh sách rút gọn này sang
  View"*. `trip-edit-form.html:238` inline bản rút gọn đó và **chạy bình thường**
  (đã drive `GET /admin/trip-management/trips/edit/1` → 200, không lỗi). Tức
  codebase đã có sẵn quy ước đúng; `approve-form` chỉ là **sót**.
  Khối inline còn lại — `dashboard-analytics.html:505-518` — chỉ inline
  `.labels`/`.values` (List<String>/List<Number>), an toàn.
- **Cách sửa:** trong `AdminTripController.showApproveForm()`, thay
  `model.addAttribute("availableDrivers", ...)` bằng một danh sách rút gọn theo
  đúng khuôn của `AdminTripManagementController`. JS chỉ dùng **3 trường**:
  `userId`, `user.fullName`, `totalDrivingHours24h` (`approve-form.html:383-385`),
  nên map phẳng 3 khoá là đủ. Lưu ý: biến này còn được dùng bởi `th:each` HTML ở
  phần dropdown chính, nên hoặc đặt tên biến JS riêng (khuyến nghị, giống
  `driversForJs`), hoặc sửa cả hai chỗ dùng.
- **Vì sao chưa sửa:** commit đang làm **chỉ đụng tầng view**, không một dòng Java;
  fix đúng phải sửa controller. Trộn vào là gộp việc không liên quan
  (`CLAUDE.md` — "Do not combine unrelated work").

---

# Lỗi phát hiện khi rà soát toàn dự án lần hai (2026-08-04)

Rà theo yêu cầu chủ dự án ("đọc project xem còn lỗi nào không"), chạy ngay sau khi
profile `backfill` được chạy lại cùng ngày. Trạng thái nền khi rà: `mvnw test`
**36/36 PASS** (exit 0); các lỗi **#1 / #6 / #7 / #8 / #9 đều đã đóng** đúng như ghi
nhận; #2, #3, #4, #5 vẫn mở nguyên trạng.

## Phương pháp — điểm khác biệt của lần rà này

Bản nháp đầu tiên báo **4 lỗi + 6 ghi chú nhỏ**. Chủ dự án yêu cầu soi lại từng mục
để chắc chắn *"đúng là lỗi cần sửa, chứ không phải một tính năng chưa soi kỹ"*, đồng
thời nói rõ rằng **một quyết định thiết kế vẫn có thể sai, đã lỗi thời, hoặc không
còn nhất quán với project hiện tại** — loại đó vẫn là phát hiện hợp lệ. Nên mỗi mục
được phân loại theo **BA nhánh**, không phải hai:

1. **Không phải lỗi — rút lại.** Có chủ đích, và ý định đó **vẫn đúng, vẫn nhất
   quán** với project hôm nay. Người rà đọc chưa kỹ.
2. **Là lỗi — quyết định đã hết hiệu lực.** Có chủ đích, **nhưng** ý định đó nay mâu
   thuẫn với chỗ khác trong project, hoặc tiền đề của nó không còn đúng. Phải chỉ ra
   **mâu thuẫn với cái gì, ở đâu**.
3. **Là lỗi — sai thuần túy.** Không có ý định nào đứng sau; code mâu thuẫn với chính
   comment/javadoc hoặc method anh em của nó.

Kèm theo, mỗi mục giữ lại phải trả lời được: **có chạm tới được không, trên dữ liệu
và luồng hiện tại** — không chỉ đúng trên lý thuyết. Mục nào sai lệch bằng 0 phải
được ghi thẳng là *tiềm ẩn*, không tô thành nghiêm trọng.

**Kết quả: 3/10 mục sống sót.** Bảy mục bị rút nằm ở phần "ĐÃ LOẠI" cuối tài liệu,
kèm lý do, để không ai nêu lại. **Chưa sửa gì. Chờ quyết định của chủ dự án.**

---

## 10. Dispatch kích hoạt chuyến (`→ ACTIVE`) mà bỏ qua TOÀN BỘ Business Rule Validation

> **✅ ĐÃ SỬA (2026-08-04) — theo phương án (a), chủ dự án duyệt.** Thêm allow-list
> `DispatchController.BOARD_ACTIONS = {DEPARTED, CANCELLED, COMPLETED}` — đúng ba nút
> mà `dispatch-board.html` thực sự render — và chặn ngay đầu `changeStatus()` bằng
> flash error + redirect. **`TripService` không bị sửa một dòng logic nào.**
>
> **Vì sao (a) chứ không phải (b) (validate trong `updateTripStatus`):** lỗi không nằm
> ở chỗ FSM thiếu validation, mà ở chỗ endpoint phơi ra một transition nó không bao
> giờ định phơi. `updateTripStatus()` là **bộ thực thi FSM** ("transition có hợp lệ
> không"); `confirmAutoAssignedTrip()` mới là **cổng phê duyệt** ("ràng buộc nghiệp vụ
> có thỏa không"). Nhét validate vào FSM sẽ trộn hai trách nhiệm mà codebase đang tách,
> **và** vấp NPE ngay khi `bus == null` (javadoc `validateBusForTripDryRun` đã ghi rõ),
> **và** khiến `AdminTripManagementController.updateTrip` validate hai lần.
>
> **Kèm một tripwire bù cho điểm yếu của (a).** Điểm yếu duy nhất của allow-list là nó
> theo màn hình, không theo domain — một controller tương lai vẫn có thể gọi
> `updateTripStatus(id, ACTIVE)`. Nên javadoc của `updateTripStatus()` nay nói thẳng
> **"METHOD NÀY KHÔNG KIỂM TRA RÀNG BUỘC NGHIỆP VỤ"**, liệt kê cách từng lối gọi hiện
> tại tự lo, và yêu cầu lối gọi mới phải validate trước. Chính việc method này *không*
> nói điều đó là lý do dispatch bị sót ngay từ đầu.
>
> **Kiểm chứng trên app thật** (default profile, port 8099, PID 14236), thiết kế để
> **không mutate một dòng nào** — tận dụng chính FSM làm chốt chặn:
> | Request | Kết quả |
> |---|---|
> | trip 8 `PENDING_APPROVAL → ACTIVE` | **bị allow-list chặn** — *"Bảng điều hành không hỗ trợ chuyển chuyến #8 sang trạng thái ACTIVE…"* |
> | trip 8 `→ PENDING_APPROVAL` | **bị allow-list chặn** (cũng ngoài tập) |
> | trip 7 `ACTIVE → COMPLETED` | **qua** allow-list, FSM chặn: *"Lỗi luồng vận hành…"* ⇒ chứng minh COMPLETED không bị chặn nhầm |
> | trip 3 `DEPARTED → CANCELLED` | **qua** allow-list, FSM chặn ⇒ chứng minh CANCELLED không bị chặn nhầm |
> | trip 3 `DEPARTED → DEPARTED` | **qua** allow-list, same-status no-op (guard lỗi #6 vẫn chạy) ⇒ chứng minh DEPARTED không bị chặn nhầm |
>
> Cả 4 trạng thái đều được phủ mà **không đổi một dòng dữ liệu nào**. Snapshot DB
> trước/sau **giống hệt** (`trips 1500 / buses 20 / drivers 36 / SUM(odometer) 246145 /
> SUM(last_maintenance_odometer) 227790`), chuyến 3/7/8 giữ nguyên trạng thái,
> **16/16 trang admin HTTP 200**, log **0** exception.
>
> **Không viết test:** test cho việc này sẽ chỉ kiểm `EnumSet.contains`, và project chưa
> có harness MockMvc (đã ghi ở bản sửa lỗi #9). Bằng chứng là lần drive app ở trên.

- **Mức độ:** nghiệp vụ — **nặng nhất lần rà này**. Một chuyến có thể lên ACTIVE (mở
  bán vé) mà không một ràng buộc nào được kiểm.
- **Phân loại:** nhánh **3 — sai thuần túy** (endpoint phơi ra toàn bộ FSM trong khi
  màn hình chỉ cần 3 transition; lối gọi anh em đã chặn đúng).
- **Ở đâu:** `controller/admin/DispatchController.java:83-97` — `changeStatus()` nhận
  `@RequestParam TripStatus newStatus` **không giới hạn giá trị**, ủy quyền thẳng cho
  `TripService.updateTripStatus()` (`service/TripService.java:549-607`).
- **Chuỗi thực thi khi gửi `newStatus=ACTIVE` cho một chuyến `PENDING_APPROVAL`:**
  1. `canTransition(PENDING_APPROVAL, ACTIVE)` → `true` (`TripService:541`) — **đúng**,
     đây chính là transition phê duyệt.
  2. Guard same-status (`:575-577`) không chặn vì trạng thái có đổi.
  3. `newStatus == ACTIVE` → `changeStatusToActive(trip)` (`:579-580` → `:1460-1465`):
     set `ACTIVE` + đóng dấu `saleOpenedAt`.
  4. Khối đồng bộ `Bus` (`:588-605`) chỉ chạy cho `DEPARTED`/`COMPLETED` nên không
     đụng gì.
  5. **Không một lần gọi `validateBusForTrip()` hay `validateStaffForTrip()`.**
- **Đối chiếu lối đi ĐÚNG:** `TripService.confirmAutoAssignedTrip()` (`:768-795`) bắt
  buộc `bus != null` (`:772`), `driver != null` (`:775`), rồi chạy **cả hai** validator
  (`:781-782`) **trước** `changeStatusToActive()`. Hai đường vào cùng một trạng thái,
  một đường có cổng, một đường không.
- **Hậu quả:** chuyến lên ACTIVE với xe **quá hạn bảo trì**, tài xế **hết bằng vào ngày
  khởi hành**, **trùng lịch** xe/người, **thiếu tài xế phụ** cho chuyến > 8h, hoặc
  **không có cả xe lẫn tài xế** (khối bus-sync có null-check nên không ném lỗi — chuyến
  ACTIVE rỗng người trôi thẳng vào bảng điều hành và Dashboard).
- **Vì sao đây là lỗi, không phải tính năng — ba lớp bằng chứng:**
  1. **Tiền lệ đã được chủ dự án duyệt.** `THESIS_ROADMAP.md` §8 (2026-07-21, bằng lái
     phụ xe) ghi: *"the trip controllers load the assistant straight from the request's
     `assistantId` … **a stale page or a crafted POST** could assign an assistant whose
     licence had already expired by the departure date. The owner confirmed the business
     rule … and **approved Decision A**."* ⇒ trong project này, "crafted POST vượt qua
     ràng buộc nghiệp vụ" **đã được công nhận là lỗi thật đáng sửa**, không bị bác vì lý
     do "không tới được từ UI".
  2. **Cùng khuôn với lỗi #6 đã đóng** — *lối gọi kia đã chặn đúng, dispatch chỉ là sót*.
     `AdminTripManagementController.updateTrip:256-263` gọi `updateManualTrip()` (validate
     đầy đủ) **trước** rồi mới `updateTripStatus()`. `DispatchController` gọi thẳng.
  3. **Lỗi KHÔNG nằm ở `canTransition()`.** Quy tắc `PENDING_APPROVAL → ACTIVE` là đúng và
     phải giữ (đó là transition phê duyệt). Sai nằm ở việc endpoint không giới hạn tập
     transition mà màn hình thực sự dùng.
- **Khả năng chạm — nói rõ để không thổi phồng:** **không tới được từ giao diện.**
  `dispatch-board.html` chỉ render hidden input `DEPARTED` (`:81`, `:201`), `CANCELLED`
  (`:88`, `:208`), `COMPLETED` (`:146`) — không có nút nào gửi `ACTIVE`. Phải tự chế
  request. Không có rào nào khác chặn: `SecurityConfig:15` permit-all, `:17` tắt CSRF.
  Khác với lỗi #6 (chỉ cần double-click là tái hiện), mục này **chưa từng xảy ra tự
  nhiên**.
- **Hai cách sửa, chọn một:**
  - **(a) Giới hạn ở controller** — chỉ nhận `newStatus ∈ BOARD_STATUSES ∪ {COMPLETED}`,
    đúng phạm vi màn hình. **Không đụng `TripService`.** *Khuyến nghị:* nhỏ nhất, hợp
    nguyên tắc §3 "Minimize refactoring", và đặt luật đúng chỗ (controller quyết định
    màn hình mình phơi ra cái gì).
  - **(b) Bắt buộc validate khi vào ACTIVE trong `updateTripStatus()`** — sửa tận gốc,
    nhưng đụng `TripService` và khiến `AdminTripManagementController.updateTrip` validate
    **hai lần** (nó đã validate ở `updateManualTrip()` ngay trước đó).
- **Vì sao chưa sửa:** chờ chủ dự án chọn (a) hay (b).

---

## 11. `CostParameterService.save()` — service DUY NHẤT không validate đầu vào

> **✅ ĐÃ SỬA (2026-08-04).** `save()` gọi `validate(form)` trước khi ghi; cả hai suất
> phí bắt buộc **> 0**, ném `IllegalArgumentException` với thông điệp đúng khuôn
> `RouteService` (*"… phải lớn hơn 0!"*). Form đổi `min="0"` → `min="1"` ở cả hai ô.
> **Controller không phải sửa** — `CostParameterController` đã có sẵn
> `catch → flash "Lỗi: " → redirect`.
>
> **CHỐT CỦA CHỦ DỰ ÁN: số 0 KHÔNG phải đầu vào hợp lệ.** Bốn lý do, xếp theo sức nặng:
> 1. **Chặn một thừa số mà thả thừa số kia thì vô nghĩa.** Chi phí nhiên liệu =
>    `fuelCostPerKm × Route.distanceKm`, mà `RouteService.validateRoute():131-133` **đã**
>    từ chối `distanceKm <= 0`. Project đã quyết tích này không được bằng 0 nhưng mới
>    canh **một** thừa số; chặn nốt thừa số kia là **hoàn tất một quyết định đã có**,
>    không phải đặt luật mới.
> 2. **Số 0 khôi phục đúng trạng thái mà `CostParameters` sinh ra để xoá bỏ.** §9 ghi lý
>    do tồn tại của entity: domain không có dữ liệu chi phí nào để suy, nên nó **phải**
>    là tham số do operator nhập. Nhập 0 đưa hệ thống về đúng tình trạng "không có thông
>    tin chi phí" nhưng **nguỵ trang thành một con số đã tính**, rồi biến "lợi nhuận"
>    thành "doanh thu" trên hai màn Decision Support mà không một cảnh báo nào.
> 3. **Đã grep toàn `src/main/java`: 0 không được xử lý đặc biệt ở đâu cả** — nó chỉ âm
>    thầm bị nuốt thành 0đ trong phép nhân.
> 4. **Phase 8 đã tuyên bố lập trường này rồi** — `WhatIfSimulationService.positiveOrNull()`
>    loại 0/âm *"để tránh chi phí âm"*. Đây chỉ là áp cùng lập trường cho nguồn cấu hình
>    **được lưu**, thay vì chỉ cho ô ghi đè tạm thời.
>
> *Phản biện đã cân nhắc và bác:* "chặn 0 là bịa luật nghiệp vụ, §4 cấm module định giá".
> Không — đây là ràng buộc **tính hợp lệ của đầu vào**, cùng hạng với "quãng đường > 0".
> Nó không quyết định suất phí **là bao nhiêu** (operator vẫn nhập), chỉ từ chối một giá
> trị không mang thông tin. *Hệ quả được chấp nhận:* sau bản sửa, "chi phí = 0" không
> biểu diễn được ở bất cứ đâu trong hệ thống — chi phí vận hành bằng 0 không phải kịch
> bản vận tải có thật.
>
> **Pin bằng test:** `CostParameterServiceTest` +4 test (chặn 0 / chặn âm / chặn null /
> **đối trọng** "giá trị dương vẫn lưu bình thường", gồm cả biên 1đ) → 2 thành **6**.
> Test đối trọng theo đúng cặp test của bản sửa lỗi #6, để bản sửa không chặn quá tay.
> **Non-vacuous:** tạm bỏ `validate(form)` → **đúng 3 test chặn đỏ**, 3 test cũ + đối
> trọng vẫn xanh.
>
> **Kiểm chứng trên app thật** (POST trực tiếp, bỏ qua validation client — đúng đường mà
> lỗi này đi được): `fuel=0` → *"Chi phí nhiên liệu mỗi km phải lớn hơn 0!"*; `wage=0` →
> *"Lương tài xế mỗi giờ phải lớn hơn 0!"*; `fuel=-5000` → chặn; `fuel=` (rỗng) → chặn;
> cả hai rỗng → chặn. Dòng `cost_parameters` giữ nguyên **`6000 / 50000`, `updated_at`
> vẫn là 2026-07-24** — chứng minh không request nào lọt qua.

- **Mức độ:** nghiệp vụ — làm sai con số tiền trên **hai** màn Decision Support.
- **Phân loại:** nhánh **3 — sai thuần túy** (khoảng trống, không có quyết định nào
  đứng sau).
- **Ở đâu:** `service/CostParameterService.java:57-62` chép thẳng object của form xuống
  DB: **không kiểm null, không kiểm âm, không kiểm 0**.
  `controller/admin/CostParameterController.java:36-44` cũng không kiểm. Guard duy nhất
  của toàn hệ thống là `required min="0"` **phía client**
  (`templates/admin/cost-parameters.html:45,58`).
- **Hậu quả:**
  - **Đi được bằng UI bình thường:** `min="0"` cho phép **chính số 0**. Nhập 0 vào cả hai
    ô → chi phí = 0 ở **mọi** thẻ màn Đề Xuất Tăng Cường và ở **cột hiện trạng** màn
    What-if → **lợi nhuận = doanh thu**, tô xanh, không một cảnh báo nào.
  - **Crafted POST** (bỏ qua validation client) → null hoặc số âm được lưu → chi phí âm →
    lợi nhuận thổi phồng.
  - **Nguy hiểm dai dẳng nhất:** nếu một dòng NULL lọt vào bảng, `getOrDefault():43`
    (`findAll().stream().findFirst()`) trả về **chính dòng đó**, không trả mặc định ⇒
    `DEFAULT_FUEL_COST_PER_KM` / `DEFAULT_DRIVER_WAGE_PER_HOUR` bị vô hiệu **vĩnh viễn**
    cho tới khi Admin lưu lại tay.
- **Vì sao đây là lỗi, không phải "tôn trọng đầu vào của operator" — mâu thuẫn NỘI BỘ,
  không phải best-practice nhập khẩu:** `RouteService.validateRoute():131-136` là ca song
  song gần nhất và nó **validate đúng loại số này ở tầng service**:
  ```java
  if (route.getDistanceKm() == null || route.getDistanceKm() <= 0)
      throw new IllegalArgumentException("Quãng đường phải lớn hơn 0 km!");
  if (route.getEstimatedDuration() == null || route.getEstimatedDuration() <= 0)
      throw new IllegalArgumentException("Thời gian di chuyển dự kiến phải lớn hơn 0 phút!");
  ```
  `IncidentService.validate():97-107`, `DriverService.validateUsernameAvailable():168-174`,
  `TripService.validateBusForTrip()/validateStaffForTrip()` — **tất cả** đều validate ở
  service. `CostParameterService` là ngoại lệ duy nhất.
- **Bằng chứng tự tố cáo mạnh nhất:** `WhatIfSimulationService.positiveOrNull():438-440`
  **đã** loại 0 và số âm, kèm comment *"giá trị âm/0 bị coi là 'không ghi đè' để tránh chi
  phí âm"*. Tức màn **mô phỏng tạm thời** thì phòng thủ đầu vào, còn **nguồn cấu hình chính
  thức được lưu xuống DB** thì không. Hai màn hiểu số 0 theo hai nghĩa khác nhau.
- **Roadmap không đứng về phía nào:** §9 ghi chú *"Why cost is a parameter, not a
  derivation"* chỉ nói rate là *"a business input the operator supplies"* và *"profit may
  be negative — show it, do not clamp"*; **không nói gì về validation đầu vào**. Nên đây
  là khoảng trống, không phải quyết định đã cân nhắc.
- **Test không canh:** `CostParameterServiceTest` chỉ ghim (1) mặc định không null khi bảng
  rỗng, (2) `save()` giữ đúng một dòng. Không có test nào về giá trị hợp lệ.
- **Cách sửa:** guard trong `save()` theo đúng khuôn `validateRoute()` — ném
  `IllegalArgumentException` khi `null` hoặc `signum() <= 0`; đổi `min="0"` → `min="1"`
  trên form. **Cần chủ dự án chốt một điểm:** nếu coi 0 là đầu vào hợp lệ (kịch bản "giả
  định miễn phí nhiên liệu") thì chỉ chặn null/âm và giữ 0.
- **Vì sao chưa sửa:** chờ chủ dự án chốt 0 có hợp lệ hay không.

---

## 12. Dropdown tài xế dùng TOÀN BỘ thời lượng chuyến thay vì phần chia ca — **sai lệch hiện tại bằng 0**

> **✅ ĐÃ SỬA (2026-08-04).** Cả hai dropdown nay gọi hàm thuần package-private
> `TripService.driverShareHours(durationHours)` =
> `min(duration / ceil(duration/8h), 8h)` — phần chia ở **mức nhân sự tối thiểu mà
> `validateStaffForTrip()` sẽ đòi hỏi**.
>
> **Ba quyết định làm nên tính đúng đắn của bản sửa:**
> 1. **Bất biến một chiều được BẢO TOÀN, và có test canh.** Vì
>    `assignedDriversCount >= requiredDrivers`, phần chia của validator **luôn ≤** phần
>    chia ở đây ⇒ dropdown vẫn là **tập con** của "người validator chấp nhận", đúng quy
>    tắc §8 (*"the dropdowns no longer offer drivers the validator would then reject"*).
>    Test `dropdownNeverOffersWhatTheValidatorWouldReject` quét 9 thời lượng × 6 mức nhân
>    sự để chốt điều này.
> 2. **KHÔNG gom chung với `findBestAvailableDriver()`/`validateStaffForTrip()`.** Hai chỗ
>    đó chia cho số tài xế chúng **đã biết** (`totalDriversCount` / `assignedDriversCount`);
>    dropdown phải **suy ra** mức tối thiểu vì chạy trước khi có phân công. Cùng trần 8h
>    nhưng khác đầu vào — tôn trọng ghi chú §9 về việc hằng số `8.0` mang ba nghĩa.
> 3. **Package-private để test chốt trực tiếp trên số học**, cùng lý do
>    `WhatIfSimulationService.coverableSlots` và `TripService.isBusBusy`.
>
> **Pin bằng test:** `service/TripServiceDriverShareTest` (**9 test**, JUnit thuần — hàm
> thuần nên không cần Spring context). Fixture là số **đo được**: 8 thời lượng tuyến thật
> và phân bố giờ nền `{0×21, 1, 2×2, 3, 5, 6, 7, 7.9, 8, 9, 10, 11}` (tổng 33 = số tài xế
> hoạt động). **Chính test này đã bắt lỗi trong fixture bản nháp của nó** — bản đầu làm
> phẳng mức 2h thành một người và ra 24 thay vì 25; SQL độc lập
> (`WHERE total_driving_hours24h <= 3.5` = **25**) xác nhận con số 25 mới đúng. Đúng kiểu
> sai mà fixture bịa sẽ mắc còn fixture đo được thì không.
> **Non-vacuous:** quay `driverShareHours` về `min(duration, 8.0)` → **đúng 4 test đỏ**
> (3 tuyến dài + mìn 9h), **5 test control xanh** (5 tuyến ngắn không đổi, biên 8h, bất
> biến một chiều, và test "vì sao #12 chưa cắn").
>
> **Bán kính đã chứng minh là nhỏ nhất có thể:** với mọi tuyến ≤ 8h thì
> `requiredDrivers = 1` nên công thức mới **trùng khít** công thức cũ — 5/8 tuyến hiện tại
> **không đổi một chút nào**. Test `onCurrentRoutes_bothFormulasSelectTheSameDrivers` còn
> chốt rằng trên **cả 3 tuyến dài**, hai công thức vẫn chọn **cùng tập tài xế** trên dữ
> liệu hôm nay — tức bản sửa **không đổi hành vi quan sát được**, đúng tinh thần bản sửa
> lỗi #7 (mọi số đã ghi nhận giữ nguyên). Nếu sau này test đó đỏ, nghĩa là lỗi #12 đã bắt
> đầu cắn thật, **không phải test hỏng**.
>
> `mvnw clean test` → **49/49 PASS** (36 + 4 của #11 + 9 của #12). Báo cáo surefire cũ
> `TempHashCodeProbeTest` cũng biến mất sau `clean` — xong luôn mục dọn dẹp ở cuối tài liệu.

- **Mức độ:** nhất quán code ↔ tài liệu. **Tiềm ẩn — chưa cắn.**
- **Phân loại:** nhánh **3 — sai thuần túy** (code mâu thuẫn với chính comment của nó),
  nhưng **không** vi phạm bất biến nào project đã tuyên bố (xem dưới).
- **Ở đâu:** `TripService.getAvailableDriversForTimeRange:1342-1344` và
  `getAvailableDriversForTrip:1247-1248` tính `effectiveHours = Math.min(durationHours, 8.0)`
  — **không chia cho số tài xế**.
- **Trái với chính nó:** comment `:1343` ghi *"effectiveHours: phần giờ mà **mỗi** tài xế
  phải lái (cắt tối đa 8h/người)"*; javadoc `:1334` ghi *"Tổng giờ lái hôm nay + **phần
  chia** của chuyến này ≤ 8h"*.
- **Trái với hai method anh em:** `findBestAvailableDriver:440-441` và
  `validateStaffForTrip:1038-1039` đều dùng `Math.min(duration / driverCount, 8.0)`.
- **Roadmap xếp cả năm vị trí này vào cùng một nghĩa:** §9 (*"Why Phase 4 redeclares the
  8-hour daily limit"*) gọi chúng là *"the **per-trip share cap** applied when splitting a
  long trip across co-drivers"*.
- **HAI điều bắt buộc phải ghi kèm, để không ai thổi phồng mục này:**
  1. **KHÔNG vi phạm bất biến nào project đã tuyên bố.** Roadmap §8 (2026-07-20, licence
     fix) chỉ phát biểu **một chiều**: *"the dropdowns no longer offer drivers **the
     validator would then reject**"*. Dropdown **chặt hơn** validator không phá quy tắc đó.
     Không chỗ nào trong project đòi dropdown phải hiện **đủ** người hợp lệ.
  2. **Sai lệch hiện tại = 0, đã đo.** Giờ đến ở form tạo chuyến là `readonly`, do JS tính
     từ `estimatedDuration` (`trip-create-form.html:181-182`) ⇒ thời lượng chỉ nhận 7 giá
     trị của 8 tuyến. Ba tuyến > 8h (`id 6` = 1800′, `id 8` = 3000′, `id 9` = 4500′) cho
     khoảng lệch **0,5 / 0,857 / 0,5 h**. Phân bố giờ nền thật của 36 tài xế:
     `0(×21), 1, 2, 3, 5, 6, 7, 7.9, 8, 9, 10, 11` — **không ai rơi vào khoảng (0; 0,857]**
     ⇒ dropdown và validator cho ra **cùng một tập người**. Lối gọi còn lại
     (`getAvailableDriversForTrip`, MANUAL MODE màn approve) vẫn **không chạm tới được**:
     đo được **0/1.499** chuyến thiếu `bus_id` hoặc `driver_id`.
- **Vì sao vẫn ghi lại — ngòi nổ nằm trong tay Admin:** `RouteService.saveRoute()` cho nhập
  `estimatedDuration` tự do (chỉ đòi `> 0`). Thêm **một tuyến 9h** ⇒ `requiredDrivers = 2`
  ⇒ validator dùng **4,5h** còn dropdown dùng **8,0h** ⇒ lệch **3,5h** ⇒ validator nhận tài
  xế có ≤ 3,5h (**25 người** trên dữ liệu hiện tại) trong khi dropdown chỉ hiện người có
  **đúng 0h** (**21 người**) ⇒ **4 tài xế hợp lệ bị giấu khỏi Admin**.
- **Cách sửa:** hai dòng ở mỗi method, đưa về đúng công thức validator:
  ```java
  int requiredDrivers = Math.max(1, (int) Math.ceil(durationHours / 8.0));
  double effectiveHours = Math.min(durationHours / requiredDrivers, 8.0);
  ```
- **Vì sao chưa sửa:** **ưu tiên thấp nhất** trong ba mục — đụng `TripService` (§3
  "Minimize refactoring") để sửa một sai lệch hiện đang bằng 0. Hợp lý nhất là gộp vào lần
  nào đó đã phải mở `TripService` vì việc khác.

---

## ĐÃ LOẠI ở lần rà 2026-08-04 — không phải lỗi, đừng nêu lại

Bảy mục dưới đây **đã bị chính người rà rút lại** sau khi đối chiếu. Ghi đủ lý do để phiên
sau không tốn công nêu lần nữa.

- **Màn Đề Xuất Tài Xế "đề xuất người mà validator sẽ từ chối"** (bộ lọc
  `DriverRecommendationService:98` là `remainingHours > 0`, trong khi validator cần
  `hours + thờiLượngChuyến ≤ 8`). **RÚT — hành vi đã được kiểm chứng và duyệt.**
  `THESIS_ROADMAP.md` §8, mục kiểm chứng Phase 4, **điểm 4** ghi nguyên văn: *"**Both
  boundary cases correct.** `TX Đã lái 8h` (exactly at the limit, remaining 0.0) is
  **excluded**; `TX Sát 8h (7.9h)` (remaining 0.1) is **included** as the last row. ✓"* —
  đúng chính xác ca bị đem ra tố cáo. Điểm 2 còn ghi công thức chủ ý
  (`27 = 33 hoạt động − 4 (giờ ≥ 8) − 2 (hết bằng)`). Ngữ nghĩa màn hình là *"ai còn dư hạn
  mức trong ngày"*, **không phải** *"ai nhận được một chuyến cụ thể"*. Dữ liệu seed còn được
  đặt tên `TX Sát 8h (7.9h)` — fixture **cố ý** dựng để nằm sát ngưỡng. *(Câu javadoc "không
  bao giờ đề xuất một tài xế mà hệ thống sẽ từ chối" có hơi quá lời, nhưng ba ràng buộc nó
  liệt kê đều đã cài đủ và hành vi đã được duyệt — **không** nâng chuyện chữ nghĩa này thành
  lỗi.)*
- **5/6 endpoint xóa dùng GET** (`AdminBusController:71`, `AdminDriverController:83`,
  `AdminIncidentController:86`, `AdminRouteController:93`, `AdminStationController:67`;
  chỉ trip dùng POST). **RÚT — là best-practice nhập khẩu, không phải chuẩn của project.**
  Ba lý do: (1) `docs/testing/test_case.md` **ghi `GET /admin/buses/delete/1`,
  `GET /admin/stations/delete/1`… làm quy trình test chính thức** ở nhiều chỗ ⇒ đây là giao
  diện đã tài liệu hoá, không phải chỗ bị sót; (2) `SecurityConfig.java:17` **tắt CSRF có
  chủ đích** (*"Tắt bảo vệ CSRF để không bị lỗi khi làm Form"*), `:15` permit-all, `:18-19`
  tắt login — §4 Non-Goals xác nhận không RBAC/login trong roadmap; (3) **hệ quả then chốt:
  đổi sang POST KHÔNG tăng an toàn** vì CSRF đã tắt nên POST cũng giả mạo được y hệt — lợi
  ích duy nhất còn lại là chống prefetch/lịch sử/dán URL. Không tài liệu nào tuyên bố
  "delete phải là POST", nên trip-delete dùng POST **không** chứng minh được là một tuyên bố
  đổi quy ước. *Rủi ro còn lại là thật nhưng không phải bảo mật, và bán kính đã đo: **0 xe**
  (guard chặn hết), **3 tài xế**, **2 tuyến** đang xoá cứng được. Siết lại là **lựa chọn**
  của chủ dự án, không phải lỗi phải sửa.*
- **Thêm tài xế phụ làm loãng hạn mức 8h/ngày** (`validateStaffForTrip:1038` chia cho số
  người Admin thực chọn, không có trần trên). **RÚT — chia ca LÀ luật được thiết kế.**
  `TripService.sumDrivingHours()` javadoc (`:687-693`) gọi đích danh: *"luật tính giờ **DUY
  NHẤT** của hệ thống: quy ước phụ xe = 0h, **chia ca cho tài xế phụ**, cắt trần 8h/chuyến"*.
- **Hai query chết trong `TripRepository`** — `countBusyTripsAnyRole:199` và
  `findAllTripsByDriverOnDate:214`, **0 nơi gọi**. **KHÔNG phải lỗi** (không hành vi nào
  sai) — là **rác cần dọn**. Đáng dọn vì chúng nhân bản logic "bận / giờ lái" với tập trạng
  thái **khác** (`status <> 'CANCELLED'` thay vì whitelist), nên người sau dễ dùng nhầm bản
  sai.
- **Soft-delete che chuyến khỏi các guard chống xoá cứng** (`@SQLRestriction` khiến
  `existsAnyTripForDriver` / `existsByBusId` / `existsByRouteId` không thấy chuyến đã xoá
  mềm ⇒ có thể xoá cứng thực thể chỉ còn dấu vết ở đó, để lại FK treo vì
  `foreign_key_checks=0`). **RÚT — bán kính = 0, đã đo:** chuyến soft-delete duy nhất
  (`id 10`) dùng `bus 2`, mà `bus 2` còn **78 chuyến sống**. Cùng loại với ghi chú
  soft-delete ↔ Incident mà roadmap §9 đã **chấp nhận không sửa**.
- **`getAvailableBusesForTrip:1211` deref `trip.getRoute()` không null-check** trong khi
  `:1220` ngay dưới lại có check. **RÚT — thuần mỹ quan;** `route_id` thực tế không bao giờ
  null (form tạo chuyến bắt buộc `routeId`).
- **Báo cáo surefire cũ** `TEST-…TempHashCodeProbeTest.xml` (2026-07-24) cho một class không
  còn trong `src/test`. **KHÔNG phải lỗi** — rác build; đọc số test từ `target/` sẽ ra 37
  thay vì 36. `mvnw clean` là xong.

*(Quan sát phụ, chưa truy: lúc bootstrap test có một câu lệnh schema-update của Hibernate ném
ERROR vào log rồi build vẫn SUCCESS. Cờ `-q` đã cắt mất đầu stack trace nên chưa biết câu lệnh
nào; không ảnh hưởng kết quả test.)*

> **✅ ĐÃ TRUY RA (2026-08-05) — KHÔNG phải lỗi, đóng mục này.** Ngoại lệ là
> `java.sql.SQLSyntaxErrorException: Table 'busmanagement_test.trips' doesn't exist`, ném từ
> `GenerationTargetToDatabase.accept()`. Nguyên nhân: `src/test/resources/application.properties`
> đặt `spring.jpa.hibernate.ddl-auto=create-drop`, nên **pha DROP chạy trước pha CREATE lúc khởi
> động** — mà lần chạy test trước đó đã drop sạch bảng khi tắt. Hibernate log ERROR rồi đi tiếp,
> đúng thiết kế. Xuất hiện ở **mọi** lần chạy trên DB test sạch, không liên quan đến bản sửa nào
> và không ảnh hưởng kết quả (`mvnw clean test` vẫn exit 0, 49/49).

---

# Kiểm chứng lại ba bản sửa #10/#11/#12 (2026-08-05) — cả ba đứng vững, phát hiện thêm #13

Rà theo yêu cầu chủ dự án (*"kiểm tra lại các lỗi mới fix hôm qua ổn cả chưa, đảm bảo mọi thứ
logic và nhất quán"*). **Kết luận: cả ba bản sửa đứng vững**, không sửa lại dòng code nào của
chúng. Bằng chứng kiểm chứng độc lập (chạy lại test, đối chiếu allow-list với template, đếm lại
call site, truy vấn read-only trên DB thật) ghi ở `THESIS_ROADMAP.md` §8 mục 2026-08-05.

Ba việc tài liệu/chú thích được làm trong cùng phiên: bổ sung bullet §7 cho #10–#12 (Rule 3/4),
sửa javadoc tripwire của `updateTripStatus()` cho đủ 4 call site, và ghi mục #13 dưới đây.

Mục #13 phát hiện **trong lúc đo lại bán kính của #12** — không do bản sửa hôm qua gây ra; bản
sửa #12 thậm chí **thu hẹp** khoảng lệch này (trên tuyến 30h: 8,0h → 7,5h).

---

## 13. Dropdown "Phụ xe" ở form TẠO chuyến bị lọc bằng luật 8h — màn Phê Duyệt thì không

- **Mức độ:** nhất quán code ↔ code ↔ tài liệu. **Tiềm ẩn — chỉ chạm được ở 3 tuyến > 8h.**
- **Phân loại:** nhánh **3 — sai thuần túy** (một màn áp lên vai trò phụ xe đúng cái ràng buộc mà
  project tuyên bố **không** áp cho vai trò đó; màn anh em cùng nhiệm vụ đã làm đúng).
- **Ở đâu:** `TripRestController:58-62` trả về **một** danh sách `drivers` duy nhất, lấy từ
  `TripService.getAvailableDriversForTimeRange()` — vốn có bộ lọc giờ lái
  (`:1369`, `getDrivingHoursForDate(...) + effectiveHours <= 8.0`). `trip-create-form.html:546-551`
  đổ **chính danh sách đó** vào `assistantSelect` (dropdown Phụ xe), y hệt dropdown Tài xế chính.
- **Trái với bốn chỗ khác trong chính project:**
  1. `TripService.getAvailableAssistantsForTrip():1289-1305` — cùng nhiệm vụ chọn phụ xe, nhưng ở
     màn Phê Duyệt — **cố ý bỏ** bộ lọc giờ lái, và javadoc `:1283-1287` giải thích rõ vì sao.
  2. `findBestAvailableDriver():458` — `isAssistantRole ||` cho phụ xe đi vòng qua đúng bộ lọc đó.
  3. `validateStaffForTrip():1051-1104` — trần 8h chỉ áp cho tài xế chính và tài xế phụ, **không**
     áp cho phụ xe.
  4. `sumDrivingHours()` javadoc (`:687-693`) gọi *"quy ước phụ xe = 0h"* là luật tính giờ **DUY
     NHẤT** của hệ thống.
- **Bán kính, đo trên DB thật ngày 2026-08-05 (read-only):** 8 tuyến, phân bố giờ lái nền của 33
  tài xế hoạt động là `0(×21), 1, 2(×2), 3, 5, 6, 7, 7.9, 8, 9, 10, 11`.
  - 5 tuyến ≤ 8h (`90/120/120/210/360` phút): **không bị** — phụ xe không bắt buộc, và phần chia
    bằng đúng thời lượng như cũ.
  - 3 tuyến > 8h: `1800′` → phần chia 7,5h; `3000′` → 7,143h; `4500′` → 7,5h ⇒ cả ba trường hợp
    dropdown chỉ mời **21/33** người (những ai có ≤ 0,5h). **12 người hợp lệ bị giấu.**
- **Vì sao vẫn đáng ghi dù chưa cắn nặng:** chuyến > 8h **bắt buộc** phải có phụ xe
  (`validateStaffForTrip:1014`). Tức đúng lúc phụ xe là ràng buộc cứng thì danh sách bị co lại
  mạnh nhất. Nếu 21 người đó đều bận, form sẽ báo hết người trong khi vẫn còn 12 người hợp lệ.
- **KHÔNG vi phạm bất biến nào project đã tuyên bố** — giống hệt #12: quy tắc §8 chỉ một chiều
  (*"dropdown không được mời người validator sẽ từ chối"*), chặt hơn thì không phá. Nhưng nó khiến
  **hai màn cùng chọn phụ xe trả lời khác nhau**, đúng loại mâu thuẫn đã được công nhận ở #12.
- **Cách sửa:** thêm `getAvailableAssistantsForTimeRange(departure, arrival)` — song sinh của
  `getAvailableAssistantsForTrip()`, tức bộ lọc của `getAvailableDriversForTimeRange()` **trừ**
  filter giờ lái; `TripRestController` trả thêm khoá `assistants`; `trip-create-form.html` đổ
  `assistants` vào `assistantSelect` thay vì `drivers`. Vì đây là **mở rộng** tập, phải giữ nguyên
  hai filter còn lại (bằng lái còn hạn vào ngày khởi hành, không trùng lịch) để quy tắc một chiều
  không bị phá theo hướng ngược lại.
- **Vì sao chưa sửa:** đụng `TripService` + `TripRestController` + template cho một sai lệch chỉ
  chạm tới được ở 3 tuyến dài, và đây là khoảng trống **thứ hai cùng loại với #12** ⇒ chủ dự án
  nên quyết một lần: gộp cả hai vào cùng một lần mở `TripService`, hay để riêng.

## Mục nhỏ (2026-08-05)

- **Biến chết trong `trip-create-form.html:546`:**
  `const assistantOptionsHtml = buildDriverOptions(drivers, '-- Không có --', false);` được tính
  rồi **không dùng ở đâu** — ba dòng ngay dưới (`:547-550`) dựng lại chuỗi **y hệt** bằng
  `drivers.map(...)` inline. Biến chết, **0 ảnh hưởng hành vi**. Không xoá kèm vì không thuộc bản
  sửa nào đang mở; xoá được cùng lúc với #13 (cùng file, cùng khối `renderResources`).

---

# Lỗi phát hiện khi rà soát toàn dự án lần ba (2026-08-05)

Rà theo yêu cầu chủ dự án (*"rà soát lại xem còn lỗi gì chưa sửa không"*), chạy ngay sau khi ba bản
sửa #10/#11/#12 được kiểm chứng lại và giữ nguyên. Trạng thái nền khi rà: `mvnw clean test`
**49/49 PASS**; #1, #6–#12 đã đóng; #2, #3, #4, #5, #13 vẫn mở nguyên trạng.

## Phương pháp — hai vòng, và một mục do chính người rà rút

Bản nháp đầu báo **4 lỗi**. Chủ dự án yêu cầu soi lại từng mục để chắc chắn *"đúng là lỗi chứ
không phải tính năng hay chức năng nào đó chưa tìm hiểu kỹ"*. Vòng hai:

- **#17 bị RÚT** (chi tiết ở "ĐÃ LOẠI" cuối mục này) — không vi phạm bất biến nào project tuyên bố.
- **#15 bị PHÁT BIỂU LẠI.** Bản đầu tố *"`COMPLETED` đặt xe về `READY` vô điều kiện"* là lỗi. **Sai**
  — đó là hành vi đã tài liệu hoá (`trip_lifecycle_fsm.md` §2), đúng loại "tính năng chưa đọc kỹ" mà
  chủ dự án cảnh báo. Lỗi thật nằm ở guard thiếu `DEPARTED`, xem dưới.
- **#14 và #16 giữ nguyên**, và cả ba mục còn lại đều được **tái hiện trên app thật** trước khi ghi
  vào đây.

Toàn bộ phần tái hiện dùng **hàng tạm do chính lần verify tạo ra** (xe 25/26/27, chuyến 2765), nên
**không một dòng dữ liệu có sẵn nào bị đụng**. Snapshot DB trước/sau **giống hệt** ở cả hai lần
chạy: `buses 20 / drivers 36 / trips 1500 / incidents 8 / users 37 / routes 8 / trip_co_drivers 3`,
`SUM(odometer) 246145`, `SUM(last_maintenance_odometer) 227790`; bảng `buses` khớp **từng dòng**;
0 exception trong log.

**Chưa sửa gì. Chờ quyết định của chủ dự án.**

---

## 14. Đổi xe cho một chuyến ĐANG CHẠY làm xe cũ kẹt `TRAVELING` vĩnh viễn

> **✅ ĐÃ SỬA (2026-08-06) — chủ dự án chọn phương án (a): chặn ở controller.**
> `AdminTripManagementController.updateTrip()` từ chối request đổi `busId` khi chuyến đang
> `DEPARTED`, bằng flash `error` + redirect về chính form sửa — **`TripService` không bị đụng
> một dòng nào**.
>
> **Vì sao (a) chứ không phải (b) "đồng bộ cả hai xe":**
> 1. **Trùng tiền lệ #10 (2026-08-04):** ở đó chủ dự án đã duyệt allow-list phía controller thay
>    vì nhét validate vào `TripService`, với lập luận *"lỗi là ở chỗ endpoint phơi ra một
>    transition nó không hề định phơi"*. Ở đây màn Sửa phơi ra một **ô** nó không định phơi cho
>    chuyến đang chạy — cùng hình dạng.
> 2. **`trip_lifecycle_fsm.md` dòng 3** nói thẳng *"TripService is the sole authoritative
>    implementation of all FSM logic"*. Đặt (b) ở controller là vi phạm trực tiếp; đặt trong
>    `TripService` là sửa logic đã verified — §3 "Minimize refactoring".
> 3. **(b) không đơn giản như nó trông.** "Xe cũ → `READY`" **không phải lúc nào cũng đúng**:
>    `isBusBusy:327-328` chỉ coi là bận khi **cửa sổ thời gian giao nhau**, nên hai chuyến
>    `DEPARTED` không giao giờ vẫn dùng chung một xe được. Trả xe cũ về `READY` khi đó sẽ **xoá
>    nhầm** dấu `TRAVELING` hợp lệ của chuyến kia. (b) đúng thì phải kèm truy vấn phụ.
> 4. **(b) phải thêm một side-effect không gắn với transition nào** vào bảng §8 của tài liệu FSM,
>    phá đúng khuôn "Side effects on **entry**" mà bản sửa #6 đang dựa vào để tồn tại.
>
> **Chặn đúng MỘT trạng thái là đủ, không chặn thừa:** `DEPARTED` là trạng thái duy nhất xe mang
> dấu `TRAVELING` do FSM đặt — FSM §8 ghi rõ *"`PENDING_APPROVAL → ACTIVE` does not change bus
> status"* — nên đổi xe ở `PENDING_APPROVAL`/`ACTIVE` không phá gì và vẫn được phép.
>
> **Javadoc `:199-202` đã sửa.** Câu cũ khẳng định *"đảm bảo BusStatus synchronization cũng chạy
> đúng trên bus mới (nếu bus bị thay)"* — mô tả một hành vi mà điều kiện `status != newStatus` ở
> dưới **không cho xảy ra**. Chính câu sai đó đã che lỗi này khỏi các lần rà trước, nên nó được
> thay bằng lời cảnh báo ngược lại: đồng bộ **chỉ** chạy khi trạng thái đổi.
>
> **Kiểm chứng trên app thật** (default profile, PID 14688, port 8099), chuyến `DEPARTED` **thật**
> số 13 (route 1, xe 6):
> | Thao tác qua HTTP | Flash | DB sau đó |
> |---|---|---|
> | Đổi xe 6 → 1, **kèm `price` cố tình đổi** 200000 → 999999 | *"Không thể đổi xe cho chuyến #13 vì chuyến đang trên đường (DEPARTED)…"* | `bus_id=6`, `price=200000` — **không field nào landing** |
> | **ĐỐI TRỌNG:** cùng chuyến, **giữ nguyên** xe 6 | *"Cập nhật chuyến xe thành công!"* | lưu bình thường |
>
> Đối trọng là phần quan trọng: nó chứng minh bản sửa chặn đúng việc **ĐỔI** xe, **không** chặn
> mọi thao tác sửa trên chuyến đang chạy. Bằng chứng mạnh nhất ở hàng đầu là `price` — tôi cố tình
> gửi sai và nó **không** được ghi, tức controller `return` trước khi chạm tới bất kỳ setter nào.
>
> **Bán kính đo lại hôm nay (không chép số hôm qua):** 5 chuyến `DEPARTED`, 6 xe `TRAVELING`, và
> **0 vi phạm bất biến thật** — dòng duy nhất SQL bắt được là xe 19, fixture seed đã nằm ở danh
> sách "ĐÃ LOẠI". Tức bản sửa là **phòng ngừa**, không phải dọn dữ liệu: không có dòng hỏng nào
> cần chữa.
>
> **Không có test đơn vị:** dự án vẫn chưa có harness MockMvc (đã ghi ở #9 và #10), nên guard ở
> controller được chứng minh bằng cách drive app như trên — cùng cách #10 đã được chấp nhận.
>
> **⚠️ GIỚI HẠN PHẠM VI — nói rõ để lần rà sau không tưởng #14 đã khoá cả chuyến `DEPARTED`.**
> Bản sửa khoá **duy nhất trường `bus`**. Các trường khác của chuyến đang chạy vẫn sửa được, và
> đáng chú ý nhất là **`route`**: khối đồng bộ đọc `trip.getRoute().getDistanceKm()` **tại thời
> điểm `COMPLETED`** (`TripService:621-627`), nên đổi tuyến của chuyến `DEPARTED` rồi bấm hoàn
> thành sẽ cộng số km của tuyến **mới** vào odometer. Đó **không** phải #14 (không xe nào bị kẹt
> trạng thái) mà thuộc **#18** — `updateTrip()` không có chính sách theo trạng thái. Ghi ở đây vì
> nó lộ ra đúng lúc rà lại #14, và vì nó ảnh hưởng tới cách chọn bản sửa cho #18 (xem #18).
>
> ---
>
> **BỔ SUNG 2026-08-11 — bản sửa 06-08 đúng nhưng CHƯA ĐỦ: màn hình vẫn MỜI thứ nó sắp từ chối.**
> Phát hiện khi drive app để kiểm chứng lại toàn bộ ba bản sửa chưa test. Guard chặn đúng, nhưng
> `trip-edit-form.html` vẫn render dropdown Xe đầy đủ cho chuyến `DEPARTED` — không disable, không
> một dòng cảnh báo. Đo trên dữ liệu thật:
>
> | Chuyến `DEPARTED` | Số option trong dropdown | Trong đó xe sẽ bị từ chối |
> |---|---|---|
> | #3, #6, #13 | 7 | **6** |
> | #11, #12 | 3 | **2** |
>
> Chạm được bằng **ba cú click** (danh sách chuyến → Sửa → đổi dropdown → Lưu), không cần POST tự chế.
>
> **Vì sao đây là lỗi chứ không phải chuyện thẩm mỹ:** javadoc của chính `showEditTripForm()`
> (`AdminTripManagementController:140-149`) phát biểu luật cho đúng dropdown này — dropdown được lọc
> *"để Create và Edit nhất quán, **không cho Admin chọn lại một xe mà `validateBusForTrip()` sẽ từ
> chối khi submit**"*. Bản sửa #14 thêm một lối từ chối mới ở submit mà **không** siết nguồn mời, nên
> tự nó tạo ra đúng hình dạng câu đó cấm. Cùng nguyên tắc chủ dự án đã chốt ở #11/#15/#16: một thứ
> không dùng được **không được giả vờ dùng được**.
>
> **Đã sửa (chủ dự án duyệt phương án "khoá ô Xe khi DEPARTED"):** `trip-edit-form.html` — chuyến
> `DEPARTED` render một ô `readonly` hiển thị đúng xe hiện tại + một `input hidden` mang `busId`,
> kèm dòng giải thích. **Không** dùng `select disabled`: select bị disabled **không gửi** `busId`, mà
> `busId` là `@RequestParam` bắt buộc ⇒ request hỏng ngay ở tầng bind, đổi một lỗi nghiệp vụ có thông
> báo thành một lỗi 400 trần. Cặp `readonly` + `hidden` là khuôn sẵn có của dự án (ô "Loại chuyến"
> ngay dưới, và hai ô tự-điền ở `trip-create-form.html:182/293`).
>
> **Guard ở controller được GIỮ NGUYÊN, cố ý.** Template quyết định *mời* cái gì, controller quyết
> định *chấp nhận* cái gì — đúng phân vai của bản sửa #10. Bỏ guard đi thì một POST tự chế lại phá
> được bất biến; bỏ template đi thì màn hình lại nói dối. Hai lớp, hai việc khác nhau.

- **Mức độ:** nghiệp vụ — **làm sai trạng thái bền trong DB** và **mất một xe khỏi đội**. Nặng nhất
  lần rà này.
- **Phân loại:** nhánh **3 — sai thuần túy** (code mâu thuẫn chính javadoc của nó, và phá một cặp
  bất biến đã được tài liệu hoá).
- **Ở đâu:** `controller/admin/AdminTripManagementController.java:261-263` — `updateTrip()` chỉ gọi
  `tripService.updateTripStatus()` **khi trạng thái đổi**:
  ```java
  if (existingTrip.getStatus() != newStatus) {
      tripService.updateTripStatus(existingTrip.getId(), newStatus);
  }
  ```
  Sửa một chuyến `DEPARTED` mà chỉ đổi **xe** (không đổi trạng thái) ⇒ **không lần đồng bộ
  `BusStatus` nào chạy**. `updateManualTrip()` chỉ validate và `save()`, nó không đụng `Bus.status`.
- **Trái với chính javadoc của method (`:199-202`):** *"updateTripStatus() re-fetch trip từ DB và
  apply FSM transition… **đảm bảo BusStatus synchronization cũng chạy đúng trên bus mới (nếu bus bị
  thay)**"*. Câu này mô tả một hành vi mà điều kiện ở `:261` không cho xảy ra.
- **Trái với cặp bất biến trong `docs/architecture/trip_lifecycle_fsm.md` §2:** *"`DEPARTED` — Side
  effects on entry: `bus.status = TRAVELING`"* và *"`COMPLETED` — Side effects on entry:
  `bus.status = READY`"*. Tức **xe được đặt `TRAVELING` lúc vào `DEPARTED` phải là xe được trả về
  `READY` lúc vào `COMPLETED`**. Đổi xe giữa chừng làm vỡ đúng cặp đó: xe A nhận `TRAVELING`, xe B
  nhận `READY`. Không tài liệu nào cho ngoại lệ.
- **Không có lối tự phục hồi:** toàn dự án chỉ có **hai** nơi ghi `Bus.status` là
  `TripService.java:615` (`TRAVELING`) và `:618` (`READY`) — cả hai đều tác động lên
  `trip.getBus()`, tức xe **mới**. Xe cũ không còn chuyến nào trỏ tới nên vĩnh viễn không ai gỡ.
  Lối chữa duy nhất là vào form Quản Lý Xe sửa tay.

> **Đã tái hiện trên app thật (2026-08-05, default profile, PID 28352, port 8099):**
>
> | Bước | Kết quả |
> |---|---|
> | Chuyến tạm 2765 (tuyến 4, xe 25) → bấm Xuất phát | xe 25 `READY` → **`TRAVELING`** ✓ đúng FSM |
> | Mở `GET /admin/trip-management/trips/edit/2765` | dropdown xe có **4 option**: 26, 4, 3 và xe hiện tại 25 (`selected`) ⇒ Admin **được mời 3 xe khác** |
> | POST update `busId=25→26`, **giữ nguyên** `status=DEPARTED` | flash **"Cập nhật chuyến xe thành công!"** — không một cảnh báo |
> | DB ngay sau đó | `trips.bus_id=26`; **xe 25 vẫn `TRAVELING`**; xe 26 (đang thật sự chạy) vẫn `READY` |
> | `GET /api/admin/trips/available-resources` cho khung giờ trống hoàn toàn | xe 25 **0 lần xuất hiện**; xe 26 **được mời** |
> | Gán tay xe 25 vào chuyến mới | *"Xe 99T-TMP.01 đang trên đường (TRAVELING), không thể gán vào chuyến mới **cho đến khi hoàn thành chuyến hiện tại**!"* — chuyến ấy **không còn tồn tại** |
> | Đưa chuyến 2765 sang `COMPLETED` (trạng thái cuối) | xe 25 **vẫn `TRAVELING`** ⇒ vĩnh viễn |

- **Hậu quả:** xe cũ bị loại khỏi `findBestAvailableBus()` (ứng viên lấy từ `findByStatus(READY)`),
  khỏi `getAvailableBusesForTrip/ForTimeRange`, và bị `validateBusForTrip:936-943` từ chối — **đội xe
  mất hẳn một chiếc mà không ai được báo**. Đồng thời xe mới ở `READY` trong khi đang trên đường, nên
  biểu đồ trạng thái đội xe ở Dashboard đếm sai (`DashboardService:157`).
- **Chạm được bằng UI, đã đo:** nút Sửa render cho **mọi** chuyến (`trip-list.html:446`, không guard
  theo trạng thái), `showEditTripForm:151` không guard, select trạng thái liệt kê đủ
  `TripStatus.values()` (`trip-edit-form.html:185-187`). Hiện có **5 chuyến `DEPARTED`**; mô phỏng
  lại bộ lọc của `getAvailableBusesForTrip` bằng SQL cho từng chuyến → **6 / 6 / 2 / 2 / 6** xe thay
  thế hợp lệ trong dropdown.
- **Cách sửa — hai hướng, cần chủ dự án chọn:**
  - **(a) Chặn ở controller:** không cho đổi `busId` khi chuyến đang `DEPARTED` (xe đã lăn bánh thì
    việc đổi xe là chuyện của nghiệp vụ ngoài hệ thống). Nhỏ nhất, và trùng tinh thần bản sửa #10:
    controller quyết định màn hình mình phơi ra cái gì.
  - **(b) Đồng bộ khi đổi xe:** nếu chuyến đang `DEPARTED` và xe bị thay, trả xe cũ về `READY` và
    đặt xe mới `TRAVELING`. Đúng với câu javadoc hiện có, nhưng đưa logic vòng đời `Bus` vào một
    method không thuộc FSM — cần cân nhắc với ghi chú §9 về việc tách FSM khỏi cổng nghiệp vụ.
- **Vì sao chưa sửa:** chờ chủ dự án chọn (a) hay (b).

---

## 15. Xe có chuyến ĐANG CHẠY vẫn đặt được `REPAIRING`, rồi dấu đó bị `COMPLETED` xoá âm thầm

> **✅ ĐÃ SỬA (2026-08-06) — chủ dự án chọn phương án (A): thêm `DEPARTED` vào guard.**
> `BusService.updateBus()` nay chặn `REPAIRING` khi xe còn chuyến ở
> `{PENDING_APPROVAL, ACTIVE, DEPARTED}` — **đúng phần bù của tập trạng thái cuối trong FSM**,
> tức phát biểu được bằng MỘT câu ("mọi chuyến chưa kết thúc") thay vì một danh sách tuỳ ý.
> Thông báo lỗi đổi theo cho khớp.
>
> **Vì sao (A) chứ không phải (B) "cho `COMPLETED` không ghi đè `REPAIRING`":**
> 1. (B) sửa một side-effect **đang đúng như tài liệu** (`trip_lifecycle_fsm.md` §8) và buộc viết
>    lại bảng đó thành có điều kiện, đẻ ra câu hỏi mới mà FSM không trả lời được: *"xe kết thúc
>    chuyến ở trạng thái nào?"*.
> 2. (B) **không chặn được nửa còn lại**: suốt chuyến `DEPARTED` xe vẫn ngồi ở `REPAIRING`, nên
>    `DashboardService:158` đếm nó là "đang sửa" **trong khi nó đang trên đường** — bất biến vẫn
>    vỡ, chỉ đổi kiểu.
> 3. Sau (A), đường xoá âm thầm **không còn tồn tại**, nên (B) sẽ là **code chết**.
>
> **Cái giá đã được nói rõ và chấp nhận:** sau bản sửa, đánh dấu bảo trì cho xe hỏng **giữa đường**
> trở thành **bị từ chối có thông báo**, thay vì "thành công rồi bị xoá ngầm" như trước. FSM không
> có `DEPARTED → CANCELLED` nên hệ thống vốn **không mô hình hoá sự cố giữa đường**; thêm nó là
> **tính năng mới**, không thuộc phạm vi sửa lỗi. Cùng nguyên tắc chủ dự án đã chốt ở #11 và #16:
> một giá trị không mang thông tin không được nguỵ trang thành giá trị thật.
>
> **Pin bằng test:** 2 test mới trong `BusServiceTest` (10 → **12**). Cặp cốt lõi là ca chặn
> (`DEPARTED`) + **đối trọng** "chuyến `COMPLETED`/`CANCELLED` **không** chặn" — nếu không có đối
> trọng, bản sửa dễ trượt thành "cứ có chuyến là chặn", và sau vài tháng vận hành sẽ không xe nào
> bảo trì được nữa. `mvnw test` **61/61**. **Non-vacuous:** gỡ `DEPARTED` khỏi danh sách →
> **đúng 1 test đỏ** (`update_toRepairingIsBlockedWhenBusIsOnADepartedTrip`), 11 xanh.
>
> **Kiểm chứng trên app thật** (PID 14688, port 8099) — trên **xe thật số 6**, đang `TRAVELING` vì
> chuyến `DEPARTED` 13 và **không** có chuyến `ACTIVE`/`PENDING` (đúng điều kiện guard cũ bỏ lọt):
> | Thao tác | Flash | DB sau đó |
> |---|---|---|
> | Đặt `REPAIRING`, **kèm `brand` cố tình đổi** thành `PROBE-15` | *"Lỗi: Không thể chuyển trạng thái xe sang bảo trì vì xe đang được phân công cho các chuyến xe chưa kết thúc…"* | xe 6 **y nguyên**; `brand` vẫn `Thaco-Mới6`; **0 dòng** mang brand `PROBE%` |
> | **ĐỐI TRỌNG** trên xe tạm không có chuyến nào | *"Cập nhật thông tin xe thành công!"* | `REPAIRING` — bản sửa **không quá tay** |
>
> Xe tạm đã xoá **qua app**. `brand` cố tình sai không landing ⇒ service ném **trước khi chép** bất
> cứ field nào — cùng bằng chứng đã dùng cho bản sửa #16.
>
> **Bán kính đo lại hôm nay:** đúng **3 xe** (id 6, 14, 15) đang ở tình trạng guard cũ cho lọt —
> khớp con số đo ngày 2026-08-05, tái đo độc lập.
>
> **Bằng chứng củng cố tìm được khi rà lại (2026-08-06) — dự án đã ra đúng quyết định này ở entity
> anh em từ trước:** `DriverService.BUSY_STATUSES:40-41` là
> `{PENDING_APPROVAL, ACTIVE, DEPARTED}` — **đủ cả ba** — và được dùng để chặn khóa một tài xế còn
> chuyến dở dang. Comment tại `DriverService:85` còn tự nhận là *"Cùng nguyên tắc với
> `BusService.updateBus()` — chặn chuyển xe sang `REPAIRING` khi xe còn được phân công cho chuyến
> chưa kết thúc"*. **Trước bản sửa này, câu đó SAI:** phía tài xế chặn 3 trạng thái, phía xe chặn 2.
> Tức #15 không phải là áp một chuẩn ngoại lai, mà là **kéo phía xe về đúng quyết định project đã
> có** — bên xe là ngoại lệ duy nhất, đúng như `CostParameterService` từng là ngoại lệ về validate
> ở #11. Câu thông báo lỗi cũng đã đổi cho dùng chung cách diễn đạt với guard tài xế
> (*"chờ duyệt / đang bán vé / đang trên đường"*), để không ai đọc hai câu rồi tưởng hai tập khác
> nhau.
>
> **Tài liệu đã chỉnh cho khớp:** `database_schema.md:106` đổi từ *"active or pending trips"* sang
> "unfinished" kèm ghi chú vì sao. `current_functional_spec.md:74` **đã đúng từ đầu** (*"unfinished
> trips"*) nên giữ nguyên — đây chính là tài liệu mà code vừa được kéo về cho khớp.
> `test_case.md`: TC_BUS_005/006 cập nhật danh sách + thông báo, và thêm **TC_BUS_006B** cho kịch
> bản `DEPARTED` (test hồi quy của lỗi này).

- **Mức độ:** nghiệp vụ — một quyết định an toàn của Admin bị hoàn tác không thông báo.
- **Phân loại:** nhánh **3 — sai thuần túy**, nhưng **chỉ sau khi phát biểu lại**: chỗ sai là
  guard, **không phải** side-effect `COMPLETED → READY` (xem ghi chú phương pháp ở đầu mục).
- **Ở đâu:** `service/BusService.java:55-63` chỉ chặn chuyển sang `REPAIRING` khi xe có chuyến
  `ACTIVE` hoặc `PENDING_APPROVAL`:
  ```java
  boolean hasActiveTrips = tripRepository.existsByBusIdAndStatusIn(
          bus.getId(), Arrays.asList(TripStatus.ACTIVE, TripStatus.PENDING_APPROVAL));
  ```
  **`DEPARTED` không có trong danh sách** — mà `DEPARTED` là ràng buộc mạnh nhất: xe đang lăn bánh.
- **Trái với functional spec:** `docs/development/current_functional_spec.md:74` phát biểu luật là
  *"the existing rule 'a bus with **unfinished** trips cannot be moved to `REPAIRING`' still
  applies"*. Một chuyến `DEPARTED` là **chưa** kết thúc. Code khớp bản hẹp hơn ở
  `docs/architecture/database_schema.md:106` (*"active or pending trips"*) ⇒ **hai tài liệu đang
  mâu thuẫn nhau**, sửa xong phải chỉnh một trong hai.
- **Hệ quả của khoảng trống:** side-effect `COMPLETED` (`TripService:617-618`, **đúng và đã tài liệu
  hoá**) đặt `bus.status = READY` không xét trạng thái hiện tại, nên dấu `REPAIRING` mà Admin đặt
  giữa chuyến bị xoá. Đây đúng là kịch bản mà tài liệu mô tả là quy trình chuẩn: ghi nhận sự cố
  **không** tự đổi trạng thái xe (`Proj_functions_summary.md:320`), *"chuyển xe sang `REPAIRING` vẫn
  là thao tác tay có chủ đích"*.

> **Đã tái hiện trên app thật (2026-08-05, cùng phiên PID 28352):**
>
> | Bước | Kết quả |
> |---|---|
> | Xe 26 đang có chuyến 2765 `DEPARTED`, 0 chuyến `ACTIVE`/`PENDING` → POST đặt `status=REPAIRING` | **"Cập nhật thông tin xe thành công!"** — guard cho qua |
> | Bấm Hoàn thành chuyến 2765 | flash chỉ có *"Đã cập nhật chuyến #2765 sang trạng thái COMPLETED."* |
> | DB | xe 26 `REPAIRING` → **`READY`**; odometer 1000 → **1120** (+120 km, đúng tuyến 4 — phần này ĐÚNG) |
> | `GET /api/admin/trips/available-resources` | xe 26 **quay lại ngay** danh sách điều phối |
>
> Tức chiếc xe Admin vừa đánh dấu hỏng được mời chạy lại, không một dòng cảnh báo.

- **Bán kính trên dữ liệu hiện tại:** trong **5** xe đang `TRAVELING` vì có chuyến `DEPARTED`, có
  **3 xe (id 6, 14, 15)** không có chuyến `ACTIVE`/`PENDING` nào ⇒ guard hiện **cho phép** đặt
  `REPAIRING` cho cả ba ngay bây giờ.
- **Cách sửa:** thêm `TripStatus.DEPARTED` vào danh sách ở `BusService:56-58` (1 dòng) — vừa khớp
  functional spec, vừa làm đường xoá âm thầm biến mất, vì không còn cách nào để xe ở `REPAIRING`
  trong lúc có chuyến chưa kết thúc. Kèm theo phải sửa `database_schema.md:106` cho khớp.
  *Đã cân nhắc và loại hướng ngược lại* (cho `COMPLETED` không ghi đè `REPAIRING`): nó sửa một
  side-effect đang đúng-như-tài-liệu, và sẽ đẻ ra câu hỏi mới "xe kết thúc chuyến trong trạng thái
  nào" mà FSM không trả lời được.
- **Vì sao chưa sửa:** đụng `BusService` + một tài liệu kiến trúc; chờ chủ dự án duyệt.

---

## 16. `BusService.saveBus()` không kiểm tra ba con số odometer — xoá trắng một ô là mất dữ liệu

> **✅ ĐÃ SỬA (2026-08-05) — chủ dự án chọn phương án (a): ô trống = GIỮ NGUYÊN.**
> `saveBus()` nay **chỉ dùng để tạo mới** (ném `IllegalArgumentException` nếu entity đã
> có id), còn `updateBus(id, form)` nạp bản ghi cũ rồi **chép từng field** — đúng khuôn
> `IncidentService.updateIncident()`. Cả hai đi qua `validate()` dùng chung.
> `AdminBusController.updateBus` bỏ `bus.setId(id)` và gọi `updateBus(id, bus)`.
>
> **Vì sao (a) chứ không phải (b) "báo lỗi bắt nhập lại":**
> 1. **Cùng nguyên tắc chủ dự án đã chốt ở #11, áp cho "thiếu giá trị" thay vì "số 0".**
>    Ở #11, số 0 bị bác vì nó *"nguỵ trang thành một con số đã tính"*. Ô rỗng biến thành
>    `0.0` chính là cái nguỵ trang đó, cộng thêm việc xoá mất số thật.
> 2. **Project đã có sẵn khuôn này ở hai chỗ:** `IncidentService.updateIncident:50-55` và
>    `AdminTripManagementController.updateTrip:214-231`. `BusService` là ngoại lệ, đúng
>    như `CostParameterService` từng là ngoại lệ về validate.
> 3. **Không mất khả năng nào:** `validate()` cho phép 0, nên muốn đặt odometer = 0 thì
>    **gõ số 0**. Ba cột này là số dùng để tính toán, không có trạng thái "để trống" hợp lệ.
> 4. Cả hai phương án đều **buộc phải tách ngữ nghĩa tạo/sửa**, nên (a) không tốn thêm gì.
>
> **Ba quyết định phụ:** (i) chỉ **ba ô số** áp luật "trống = giữ nguyên"; biển số / hãng /
> loại xe / trạng thái vẫn chép nguyên như form gửi, vì để trống ở đó là ý định hợp lệ —
> ghi rõ trong javadoc để lần rà sau không coi đây là bất đối xứng vô cớ. (ii) **Tripwire**:
> `saveBus()` từ chối entity đã có id, nếu không thì một lời gọi cũ sẽ merge và ghi đè mọi
> cột, đồng thời đi vòng qua ràng buộc `REPAIRING` nay nằm trong `updateBus()`. (iii) Ràng
> buộc `REPAIRING` được **dời nguyên văn**, **KHÔNG** thêm `DEPARTED` — đó là lỗi #15, một
> quyết định riêng, không trộn vào đây.
>
> **Pin bằng test:** `service/BusServiceTest` (**10 test**, `@SpringBootTest @Transactional`
> cùng khuôn `CostParameterServiceTest`). Cặp cốt lõi là "ô trống thì giữ nguyên" +
> **đối trọng** "gõ số 0 thì vẫn ghi 0", để bản sửa không trượt thành "bỏ qua mọi số 0".
> `mvnw test` **59/59**. **Non-vacuous, hai lần đo:** quay `updateBus` về hành vi cũ
> (null → mặc định) → **đúng 1 test đỏ** (`update_blankNumberKeepsExistingValue`), 9 test
> còn lại xanh; bỏ `validate(existing)` → **đúng 3 test đỏ** (ba ca ràng buộc), 7 test còn
> lại xanh.
>
> **Kiểm chứng trên app thật** (default profile, PID 11944, port 8099), xe tạm 28 xuất phát
> `8000 / 4000 / 5000`:
> | Thao tác qua form | Flash | DB sau đó |
> |---|---|---|
> | **Xoá trắng ô Odometer** (đúng cú đã phá dữ liệu) | *"…thành công!"* | **8000 / 4000 / 5000** — giữ nguyên |
> | Gõ số 0 tường minh (đối trọng) | *"…thành công!"* | **0 / 0 / 5000** — vẫn ghi được 0 |
> | `odometer = -500` | *"Lỗi: Odometer không được âm!"* | không đổi |
> | `maintenanceThreshold = 0` | *"Lỗi: Ngưỡng cảnh báo bảo trì phải lớn hơn 0 km!"* | không đổi |
> | `odometer < lastMaintenanceOdometer` | *"Lỗi: Odometer (3999.0 km) không được nhỏ hơn km lần bảo trì cuối (4000.0 km)!"* | không đổi |
>
> **Hai hồi quy đã chứng minh không hỏng:** (A) **tạo** xe bỏ trống cả ba ô vẫn nhận mặc
> định **0 / 0 / 5000** như trước; (B) ràng buộc `REPAIRING` vừa dời chỗ vẫn chặn đúng trên
> **xe thật số 7** (đang có chuyến `ACTIVE`/`PENDING`) — và bằng chứng mạnh nhất là **không
> field nào bị ghi**: tôi cố tình gửi `brand=Hyundai`, DB vẫn giữ `Thaco-Mới7`, tức service
> ném lỗi trước khi chép bất cứ thứ gì.
>
> Đã xoá hai xe tạm **qua app**; snapshot trước/sau **giống hệt** (`buses 20 / trips 1500`,
> `SUM(odometer) 246145`, `SUM(last_maintenance_odometer) 227790`), 0 dòng vi phạm ràng
> buộc mới, 0 exception trong log.
>
> **Phát hiện kèm khi rà lối ghi:** `Proj_functions_summary.md` khẳng định `AdminController`
> có route tạo xe song song `/buses/new`, `/buses/save` gọi `AdminService.createNewBus()`
> *"không validate gì"*. **Sai — không thứ nào tồn tại**: `AdminController` chỉ có
> `/users/new` + `/users/save`, `AdminService` chỉ có `createNewUser()`. Đã sửa câu đó;
> `BusService` là lối ghi `buses` duy nhất từ giao diện, nên bản sửa này không bị hở.

- **Mức độ:** nghiệp vụ — **mất dữ liệu bền** (số km trọn đời) và **khoá xe vĩnh viễn**. Cùng họ với
  **#11** đã được chủ dự án chốt là lỗi thật.
- **Phân loại:** nhánh **3 — sai thuần túy** (mặc-định-lúc-tạo bị áp cả cho luồng sửa; project đã
  nhận diện và phòng đúng khuôn lỗi này ở chỗ khác).
- **Ở đâu:** `service/BusService.java:41-51` điền mặc định khi null rồi lưu thẳng:
  ```java
  if (bus.getMaintenanceThreshold() == null) bus.setMaintenanceThreshold(5000.0);
  if (bus.getLastMaintenanceOdometer() == null) bus.setLastMaintenanceOdometer(0.0);
  if (bus.getOdometer() == null) bus.setOdometer(0.0);
  ```
  Không kiểm âm, không kiểm `odometer >= lastMaintenanceOdometer`, không kiểm `threshold > 0`.
  `AdminBusController.updateBus:60-63` dựng `Bus` **mới** từ form rồi `setId(id)`, nên field vắng
  mặt = null = **bị mặc định đè**, không phải "giữ nguyên".
  Ba ô ở `templates/admin/bus/bus-form.html:60, 64-65, 69-70` **không có `required`, cũng không có
  `min`**.
- **Trái với chính quy ước của project — hai chứng cứ nội bộ:**
  1. `IncidentService.updateIncident:50-55` javadoc ghi thẳng lý do phải chép từng field:
     *"form không gửi reportedAt/resolvedAt, nên lưu thẳng sẽ ghi đè 2 mốc thời gian đó thành
     null"*. Tức **project đã nhận diện đúng khuôn lỗi này** và phòng ở đó; `BusService` làm ngược
     lại trên cùng loại luồng.
  2. `RouteService.validateRoute:131-136` validate đúng loại số này ở tầng service — cùng lý lẽ đã
     dùng để chốt #11.
- **Trái với mức độ quan trọng mà roadmap gán cho odometer:** Hidden Cost #7 yêu cầu *"do not
  'simplify' it to a single-column update"*, và lỗi #6 (cộng odometer hai lần) từng được xếp là lỗi
  nặng nhất của lần rà đầu. Cùng một con số, ở đây bị xoá về 0 chỉ bằng một ô để trống.

> **Đã tái hiện trên app thật (2026-08-05, default profile, PID 22452, port 8099).** Xe tạm 27 xuất
> phát: odo **8000**, bảo trì cuối **4000** (đã chạy 4000 km), ngưỡng **5000**.
>
> | Thao tác qua form | Flash | DB sau đó |
> |---|---|---|
> | Xoá trắng ô "Odometer Hiện Tại" rồi Lưu | *"Cập nhật thông tin xe thành công!"* | odo **8000 → 0**, `km_since` = **−4000** |
> | `odometer = -500` | *"…thành công!"* | lưu nguyên **−500** |
> | `maintenanceThreshold = 0` | *"…thành công!"* | lưu nguyên **0** |
>
> **Hai hệ quả đã đo:**
> 1. **Mất lịch bảo trì.** `km_since = −4000` ⇒ `needsMaintenance()` và `isNearMaintenance()` đều
>    false; xe phải chạy thêm **9.000 km** mới chạm ngưỡng. Con số 8.000 km trọn đời — đầu vào 70%
>    điểm của màn Đề Xuất Thay Xe — biến mất, phục hồi phải sửa tay trong DB.
> 2. **Ngưỡng 0 = khoá xe vĩnh viễn.** Gán xe vào chuyến →
>    *"(Odo: 4000km) đã QUÁ HẠN bảo trì (ngưỡng: 0km)"*. Thử đúng lối thoát nghiệp vụ là "cho xe đi
>    bảo trì" (`lastMaintenanceOdometer = odometer` ⇒ `km_since = 0`) → **vẫn bị chặn**:
>    *"(Odo: 0km) đã QUÁ HẠN bảo trì (ngưỡng: 0km)"*. Vì `needsMaintenance()` là
>    `km_since >= threshold` mà `0 >= 0` luôn đúng ⇒ **không thao tác nghiệp vụ nào gỡ được**, chỉ
>    sửa lại chính ô ngưỡng.

- **Bán kính trên dữ liệu hiện tại: 0** — đo được `odometer < last_maintenance_odometer` hoặc âm:
  **0 dòng**; `maintenance_threshold IS NULL OR <= 0`: **0 dòng**. Tiềm ẩn, nhưng đi được bằng
  **một cú xoá ô** trên UI bình thường, không cần crafted POST.
- **Cách sửa:** tách hai trách nhiệm đang bị gộp trong `saveBus()` — (1) chỉ điền mặc định khi
  **tạo mới** (`bus.getId() == null`), còn khi **sửa** thì field vắng mặt phải giữ giá trị cũ
  (chép từng field từ bản ghi trong DB, đúng khuôn `IncidentService.updateIncident`); (2) thêm
  `validate()` theo khuôn `RouteService.validateRoute()`: `odometer >= 0`,
  `lastMaintenanceOdometer >= 0`, `odometer >= lastMaintenanceOdometer`, `maintenanceThreshold > 0`.
  Kèm `min="0"`/`min="1"` trên form cho khớp — nhưng guard phải ở service, đúng bài học #11.
- **Vì sao chưa sửa:** đụng `BusService` + controller + template; và điểm (1) là thay đổi ngữ nghĩa
  của một method đang được cả tạo lẫn sửa dùng chung ⇒ cần chủ dự án duyệt phạm vi.

---

## ĐÃ LOẠI ở lần rà 2026-08-05 — không phải lỗi, đừng nêu lại

- **#17 (bị rút): "Sửa hạn bằng lái không kiểm lại các chuyến đã phân công".** `DriverService.updateDriver:104-105`
  ghi thẳng `licenseExpiryDate` mới, nên rút ngắn hạn bằng lái có thể để lại chuyến `ACTIVE` với tài
  xế hết hạn. **RÚT — không có bất biến nào bị vi phạm.** Bất biến project **thực sự** tuyên bố là
  bằng lái hợp lệ **tại thời điểm phân công**: mục `THESIS_ROADMAP.md` §8 ngày 2026-07-21 nói về
  *"a stale page or a crafted POST"* lúc **gán** phụ xe, không nói gì về việc giữ bất biến đó theo
  thời gian. Bán kính đo được **0/0/0** (tài xế chính / tài xế phụ / phụ xe). Và bản sửa hiển nhiên
  nhất — chặn edit — sẽ **sai**, vì bằng lái hết hạn là sự kiện có thật phải ghi nhận được. Nếu sau
  này muốn làm, hình dạng đúng là **cảnh báo kèm danh sách chuyến bị ảnh hưởng**, không phải chặn —
  và đó là quyết định của chủ dự án, không phải lỗi phải sửa.
- **Xe 19 (`51B-DAG.CH`) ở `TRAVELING` mà không có chuyến `DEPARTED` nào.** SQL bắt được ngay và
  trông hệt một xe kẹt như #14. **KHÔNG phải lỗi:** `config/DataInitializer.java:158` seed đúng
  chiếc này với `BusStatus.TRAVELING`, tên nó là **"Xe Đang Chạy"**. Fixture cố ý.
- **Xe 17 (`51B-QUA.BT`) quá hạn bảo trì 6.000/5.000 mà vẫn `READY`.** Fixture đã ghi trong roadmap
  (Hidden Cost #7). Và `active_trips_on_overdue_bus` đo được = **0**, tức không chuyến sống nào đang
  chạy trên xe quá hạn.
- **`#numbers.formatDecimal(avgExperienceYears, 0, 1)`** (`dashboard-analytics.html:345`) — cùng
  khuôn lỗi #2 nhưng chỉ lộ khi trung bình số năm kinh nghiệm toàn đội < 1. Không nâng thành mục
  riêng; sửa kèm khi nào sửa #2.

## Mục nhỏ — ghi để mai sửa (phát hiện 2026-08-05, hẹn xử lý 2026-08-06)

- **`current_functional_spec.md:158` ghi sai công thức chặn bảo trì — nửa sau của câu.**
  Chủ dự án đã xem và quyết định **hoãn sang 2026-08-06**, không gộp vào bản sửa #16.
  - **Nội dung sai:** *"…or if the distance of the trip will push the odometer into the warning
    threshold (`odometer + distance >= maintenanceThreshold * 0.9`)"*.
  - **Code thật:** `Bus.isNearMaintenance(additionalKm)` (`domain/Bus.java:72-76`) tính
    `(getKmSinceLastMaintenance() + additionalKm) >= maintenanceThreshold * 0.9`, tức **km kể từ
    lần bảo trì cuối**, không phải **odometer trọn đời**. Với dữ liệu hiện tại hai vế lệch nhau
    rất xa: xe 16 có odometer 17.755 nhưng `km_since` chỉ 4.995.
  - **Vì sao đáng ghi:** **nửa trước của chính câu đó lại đúng**
    (`odometer - lastMaintenanceOdometer >= maintenanceThreshold`), nên người đọc dễ tin cả câu đã
    được kiểm. Nếu tin theo vế sai thì gần như **mọi** xe trong đội đều bị coi là "sắp đến hạn"
    (odometer ~12.000 > 4.500), tức mô tả một hệ thống không thể xếp nổi một chuyến nào.
  - **Mức độ:** tài liệu thuần — **0 dòng code sai**, không ảnh hưởng hành vi. Cùng họ với #3.
  - **Cách sửa:** đổi `odometer + distance` thành `kmSinceLastMaintenance + distance` trong đúng
    một mệnh đề; không đụng phần còn lại của câu.

---

# Rà ngày 2026-08-06 — hai mục phát sinh khi phân tích #14/#15

*Cả hai được tìm thấy trong lúc đọc để sửa #14/#15, và được tách riêng theo đúng kỷ luật đã áp cho
cặp #15-vs-#16: mỗi mục một quyết định của chủ dự án, không gộp vào bản sửa đang làm.*

## 18. Sửa được MỌI trường của một chuyến đã `COMPLETED` — trong khi XOÁ chính chuyến đó thì bị cấm

- **Mức độ:** nghiệp vụ — **sai lệch odometer bền và không thể phát hiện**, cộng với sửa được dữ
  liệu lịch sử mà Phase 6 đang đọc. Cùng họ tổn thất với **#6** và **#16**.
- **Phân loại:** nhánh **3 — sai thuần túy**. Không phải "chưa nghĩ tới": dự án **đã ra chính sách**
  cho đúng câu hỏi này, chỉ là lối vào thứ hai không áp nó.
- **Ở đâu:** `AdminTripManagementController.updateTrip()` không xét trạng thái chuyến ở bất kỳ đâu.
  Nó ghi đè route / bus / driver / departureTime / arrivalTime / totalSeats / price rồi gọi
  `updateManualTrip()` (chỉ validate ràng buộc nghiệp vụ, **không** xét trạng thái) và `save()`.
  Đến `:261` mới xét trạng thái, nhưng chỉ để quyết định **có gọi FSM không** — status không đổi ⇒
  bỏ qua, báo thành công.
- **Mâu thuẫn nội tại — đây là bằng chứng chính:** `TripService.deleteTrip():1469-1492` có một
  chính sách theo trạng thái **viết thành văn**:
  ```
  DEPARTED  → NGHIÊM CẤM (đang trên đường, ảnh hưởng hành trình thực)
  COMPLETED → NGHIÊM CẤM (báo cáo tài chính & lịch sử phải được giữ nguyên)
  CANCELLED → Cho phép   (đã kết thúc vòng đời, dọn dẹp DB)
  ```
  Tức dự án **đã tuyên bố** chuyến `COMPLETED` là dữ liệu được bảo vệ. Nhưng thao tác **ồn ào**
  (xoá — có xác nhận, mất cả dòng) thì bị cấm, còn thao tác **im lặng** (sửa mọi trường) thì cho
  qua. **Cấm cái ồn ào, thả cái im lặng — ngược.**
- **Template cũng đã biết luật này, chỉ nút Sửa bị bỏ sót:** nút **Hủy** ngay cạnh có sẵn guard
  `th:if="${trip.status.name() != 'CANCELLED' and trip.status.name() != 'COMPLETED'}"`
  (`trip-list.html:451`); nút **Sửa** (`:446`) không có guard nào.
- **Thiệt hại cụ thể (không chỉ là "viết lại lịch sử"):** lúc vào `COMPLETED`, `TripService:618-627`
  đã cộng `route.distanceKm` vào **chiếc xe đang được gán lúc đó**. Đổi xe sau đó ⇒ `trips.bus_id`
  trỏ xe B nhưng **số km nằm vĩnh viễn trên xe A**. Hai bản ghi nói ngược nhau và **không gì phát
  hiện được**. Kéo theo: `kmSinceLastMaintenance` sai → bộ lọc bảo trì trong `findBestAvailableBus()`
  sai → màn Đề Xuất Thay Xe (70% điểm theo odometer) xếp sai. Thiệt hại thứ hai: sửa
  `route`/`departureTime` của chuyến `COMPLETED` là sửa thẳng một quan sát mà
  `TripRepository.findDemandHistoryByStatus(...)` của Phase 6 đọc để dựng dự báo.

> **Đã tái hiện trên app thật (2026-08-06, default profile, PID 14688, port 8099)** — trên chuyến
> `COMPLETED` **thật** số 2764 (route 5 / 180 km, xe 23, chạy xong 2026-08-03):
>
> | Bước | Kết quả |
> |---|---|
> | `GET /admin/trip-management/trips/edit/2764` | **200**, 27.835 bytes — form render bình thường, **0** `disabled`, không một cảnh báo nào |
> | Dropdown xe trên chính form đó | **7 option** ⇒ Admin được mời **6 xe thay thế** cho một chuyến đã chạy xong |
> | `POST /trips/update` đổi xe **23 → 10** | **"Cập nhật chuyến xe thành công!"** |
> | DB ngay sau đó | `trips.bus_id` = **10**; odometer xe 23 = **2280**, xe 10 = **12630** — **cả hai KHÔNG đổi** |
> | ⇒ hệ quả | 180 km của chuyến vẫn nằm trên **xe 23**, trong khi chuyến nay ghi là **xe 10** |
> | **CÙNG chuyến đó**, `POST /trips/delete/2764` | **"⛔ Không thể xóa: Chuyến #2764 đã hoàn thành (COMPLETED). Dữ liệu lịch sử và báo cáo tài chính phải được giữ nguyên, không thể xóa."** |
>
> Hai hàng cuối là toàn bộ luận điểm: **cùng một chuyến, cùng một phiên** — sửa mọi trường thì
> "thành công", xoá thì bị cấm kèm câu giải thích. Chuyến 2764 đã được **khôi phục về xe 23** ngay
> sau đó và khớp mốc từng field; snapshot DB đầu/cuối phiên **giống hệt nhau**.

- **Chạm được bằng UI, không cần POST tự chế:** ba cú click (danh sách chuyến → Sửa → đổi dropdown →
  Lưu). Bán kính: **1.392** chuyến `COMPLETED` trên dữ liệu hiện tại.
- **Cách sửa đề xuất — (a):** áp đúng chính sách của `deleteTrip` lên `updateTrip` — chặn khi chuyến
  `COMPLETED`, thông báo soi gương câu của `deleteTrip`; kèm `th:if` cho nút Sửa như nút Hủy đã có.
  Chỉ chặn `COMPLETED`; **`CANCELLED` giữ nguyên cho sửa**, vì `deleteTrip` cũng cho xoá `CANCELLED`
  — code sẽ khớp chính sách **từng dòng một**, không khớp đại khái.
  *Đã cân nhắc và loại (b) "chặn theo từng field"*: phải phán xử từng trường và phải giải thích vì
  sao `price` sửa được trong khi `deleteTrip` nói *"báo cáo tài chính phải được giữ nguyên"* — nó
  tạo ra chính sách **thứ hai**, khác chính sách đã có.
- **⚠️ Điều chủ dự án cần biết TRƯỚC khi chọn: chính sách của `deleteTrip` nêu tên CẢ HAI trạng
  thái, không chỉ `COMPLETED`.** Nó cấm xoá cả `DEPARTED` (*"đang trên đường, ảnh hưởng hành trình
  thực"*). Nên có hai mức độ áp dụng, và chúng khác nhau về hệ quả:
  - **(a-hẹp)** chỉ chặn sửa chuyến `COMPLETED` → bản sửa #14 vẫn cần thiết và vẫn là thứ duy nhất
    bảo vệ chuyến `DEPARTED`.
  - **(a-đủ)** chặn sửa cả `DEPARTED` lẫn `COMPLETED`, tức áp chính sách `deleteTrip` **nguyên vẹn**
    → **bao trùm luôn bản sửa #14**, biến guard ở `updateTrip` thành thừa (không sai, chỉ thừa), và
    đồng thời đóng nốt cạnh đã ghi ở cuối mục #14: đổi **`route`** của chuyến `DEPARTED` làm đổi số
    km cộng vào odometer lúc `COMPLETED`, thứ mà #14 **không** khoá.
  Hai mức này là quyết định nghiệp vụ thật, không phải chi tiết kỹ thuật: (a-đủ) nhất quán hơn với
  chính sách đã viết, nhưng lấy đi khả năng sửa bất kỳ thông tin nào của một chuyến đang chạy — kể
  cả sửa giá hay số ghế do gõ nhầm. Nếu chọn (a-đủ) thì **không cần** revert #14; chỉ cần ghi nhận
  guard của #14 trở thành lớp phòng thủ thứ hai.
- **Vì sao chưa sửa:** chủ dự án mới chỉ duyệt việc **xác minh**; bản sửa là quyết định riêng.

---

## 19. Guard `TRAVELING` trong `validateBusForTrip` chỉ sống ở 1 trong 4 lối gọi

- **Mức độ:** **latent** — sai lệch đo được hôm nay bằng **0**, chỉ chạm được bằng POST tự chế.
- **Phân loại:** nhánh **3 theo câu chữ** (tên biến khẳng định một điều mà code không kiểm), nhưng
  **có một cách đọc ngược lại hợp lý** — xem phần cuối. Ghi lại chính vì chưa chốt được hướng đọc.
- **Ở đâu:** `TripService.validateBusForTrip:936-944`
  ```java
  if (bus.getStatus() == BusStatus.TRAVELING) {
      boolean travelingForThisTrip = excludeTripId != null
              && trip.getId() != null
              && excludeTripId.equals(trip.getId());   // ← chỉ so hai ID CHUYẾN
      if (!travelingForThisTrip) { throw ...; }
  }
  ```
  Tên biến nói *"xe đang chạy **vì chính chuyến này**"* — một khẳng định về **chiếc xe** — nhưng
  biểu thức **không hỏi gì về chiếc xe**.

| Lối gọi | `excludeTripId` truyền vào | Biểu thức | Guard |
|---|---|---|---|
| `createManualTrip:882` | `null` | false | ✅ **sống** |
| `updateManualTrip:907` | `existingTrip.getId()` | **luôn true** | ❌ chết |
| `approveTrip:860` | `tripId` | **luôn true** | ❌ chết |
| `confirmAutoAssignedTrip:806` | `tripId` | **luôn true** | ❌ chết |

- **Hệ quả:** cùng một chiếc xe `TRAVELING`, `POST /trips/create` **từ chối** còn
  `POST /trips/approve` **nhận** — chỉ còn `isBusBusy` (giao cửa sổ thời gian) đứng chắn.
- **Vì sao KHÔNG nâng thành lỗi nặng:** (1) mọi nguồn chọn xe đều lọc `READY` —
  `getAvailableBusesForTrip:1249-1251` (dropdown Sửa **và** màn Phê Duyệt, `AdminTripController:69`),
  `getAvailableBusesForTimeRange:1344`, `findBestAvailableBus:292-293` — nên xe `TRAVELING` **không
  bao giờ xuất hiện trên giao diện**; (2) **không vi phạm bất biến nào**: luật §8 là một chiều
  (dropdown không được *mời* thứ validator sẽ từ chối), ở đây dropdown **chặt hơn** validator, đúng
  như lập luận đã dùng cho #12; (3) **cách đọc ngược lại hợp lý**: `isBusBusy` mới là luật đúng, còn
  chặn theo `TRAVELING` là **thừa và quá chặt** — xếp một xe đang chạy vào chuyến chiều mai là bình
  thường. Theo cách đọc này thì đây là *nới lỏng đúng, viết sai cách*.
- **HOÃN CÓ CHỦ ĐÍCH — chủ dự án đã quyết định sửa mục này SAU #14 (2026-08-06), và lý do là kỹ
  thuật, không phải trì hoãn:** bản sửa #14 làm cho lối `update` **tự đúng**. Khi không được đổi xe
  của chuyến `DEPARTED` nữa, thì trên lối edit một xe `TRAVELING` **chỉ có thể là chính xe của
  chuyến đang sửa** (vì `DEPARTED` là trạng thái duy nhất xe mang dấu `TRAVELING`) — đúng y điều tên
  biến muốn nói, **đúng do cấu trúc, không thêm dòng nào**. Hố còn lại chỉ ở `approveTrip:860`, nơi
  chuyến đang `PENDING_APPROVAL` nên xe của nó **không bao giờ** `TRAVELING` vì chính nó ⇒ escape ở
  đó vô nghĩa và siết được bằng **một điều kiện**.
  **Sửa trước #14** thì phải thêm query repository mới (kiểu `existsByBusIdAndStatusInAndIdNot`);
  **sửa sau #14** thì còn 1 dòng. Đó là lý do không gộp và không làm trước.
- **Câu hỏi nghiệp vụ cần chốt trước khi sửa:** *"một xe đang trên đường có được xếp lịch cho chuyến
  TƯƠNG LAI không?"* Mọi lối chọn xe hôm nay đều trả lời **KHÔNG** ⇒ tính nhất quán nghiêng về việc
  làm guard nói đúng điều nó đang định nói.
