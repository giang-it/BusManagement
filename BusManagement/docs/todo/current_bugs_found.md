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