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

> **CẬP NHẬT 2026-09-11 — cả năm mục #1–#5 nay đã ĐÓNG.** #1 sửa 2026-07-20;
> #2/#3/#4 sửa 2026-08-13; **#5 sửa 2026-09-11**. Câu "cần quyết định của chủ dự án"
> ở trên là của thời điểm viết (2026-07-20) và lý do hoãn ("ngoài phạm vi Phase 3/4")
> đã hết hiệu lực ngay hôm đó. Đợt tái thẩm 2026-08-13 đã chỉ ra rằng nhãn *"mở do
> chủ ý"* mà §8 roadmap gán cho nhóm này **chưa bao giờ là một phán quyết của chủ dự
> án** — không có ruling nào được ghi ở đâu cả. Giữ nguyên đoạn trên để không xoá dấu
> vết, nhưng **đừng đọc nó như trạng thái hiện tại.**

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

> **✅ ĐÃ SỬA (2026-08-13).** Tham số thứ hai (`minIntegerDigits`) đổi `0` → `1` ở **hai** chỗ trong
> `dashboard-analytics.html`: `:372` (cột "Giờ lái hôm nay", chính là chỗ mục này ghi — số dòng đã
> dịch từ `:357`) và `:345` (`avgExperienceYears`, **mục này bỏ sót**, mắc y hệt nhưng chưa lộ vì
> trung bình hiện là 4.9).
>
> **Quét toàn bộ 58 lời gọi `formatDecimal` trong templates: đúng hai chỗ đó dính, không còn chỗ nào
> khác.** Dạng `(x, 0, 0)` và dạng 5 tham số `(x, 0, 'COMMA', 0, 'POINT')` **KHÔNG** dính — đã đo bằng
> `DecimalFormat` thật, không suy luận: khi không có chữ số thập phân nào, `DecimalFormat` rơi vào
> nhánh "in một số 0" nên ra `"0"` đúng. Đừng "sửa cho đồng bộ" các chỗ đó — chúng không hỏng.
>
> Bằng chứng hai chiều, chạy bằng Java thật: `(0.0, 0, 1)` → `".0"`; `(0.0, 1, 1)` → `"0.0"`;
> `(0.4, 0, 1)` → `".4"`; `(0.0, 0, 0)` → `"0"`.
>
> **⚠️ ĐÍNH CHÍNH một khẳng định sai của chính đợt rà 2026-08-13 (ghi lại thay vì chôn đi).** Đợt rà
> đó tuyên bố lỗi này *"đang hiện trên cả 5/5 dòng ngay bây giờ"*, lập luận rằng DB không có chuyến
> nào hôm nay nên mọi tài xế đều 0.0h. **Sai.** Tiền đề bị thiếu một vế: `sumDrivingHours()` — xem
> javadoc `TripService:718` — **cộng thêm giờ nền mock `totalDrivingHours24h` khi ngày là hôm nay**,
> chứ không chỉ cộng giờ từ các chuyến. Đo trên app thật: top-5 là **11.0 / 10.0 / 9.0 / 8.0 / 7.9**,
> đều ≥ 1 nên **không dòng nào hiển thị sai**. Đo trên DB: **0/33** tài xế hoạt động có giờ nền trong
> khoảng (0,1). ⇒ **Đánh giá gốc của mục này ("hiếm khi lộ ra") là ĐÚNG; đợt rà 2026-08-13 đã lật nó
> trên một tiền đề sai.** Cùng khuôn với các lần trước — xem §9 roadmap, ghi chú về những câu văn sai
> khiến người rà tin nhầm.
>
> **Vẫn đáng sửa dù tiềm ẩn:** 21/33 tài xế có giờ nền = 0, nên chỉ cần bộ fixture "tài xế bận" của
> `DataInitializer` vắng mặt (cài mới không chạy profile `demo`) là cả 5 dòng rơi về 0.0 → `.0`; và
> một chuyến thật cho ai đó 0.5h cũng đủ. Sửa tốn 2 ký tự, không rủi ro.
>
> Kiểm chứng: `mvnw clean test` **71/71**; drive app thật (PID 6576 rồi 24044), 14/14 trang admin 200,
> **0** template/SpEL exception, cột giờ lái render `11.0/10.0/9.0/8.0/7.9` đúng như DB, snapshot DB
> **giống hệt** trước/sau.

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

> **✅ ĐÃ SỬA (2026-08-13).** Chỉ sửa **vế nguyên nhân**, giữ nguyên kết luận "chưa có widget Recent
> Activity" và tính chất *bị từ chối có chủ đích* của nó — đúng như mục này dặn.
>
> **Bán kính thật rộng gấp 5 lần mục này ghi: 5 dòng / 3 file, không phải 1 dòng / 1 file.**
>
> | File | Dòng | Mục này có ghi? |
> |---|---|---|
> | `02_project_context.md` | 31 | ❌ bỏ sót — khẳng định `Trip.createdAt` là timestamp **duy nhất** |
> | `02_project_context.md` | 94 | ✅ dòng duy nhất được ghi |
> | `current_functional_spec.md` | 105 | ❌ bỏ sót |
> | `current_functional_spec.md` | 248 | ❌ bỏ sót |
> | `Proj_functions_summary.md` | 380 | ❌ bỏ sót |
>
> Đã kiểm bằng code chứ không bằng trí nhớ: `Incident.java:76-78` đúng là `@CreationTimestamp` +
> `updatable = false`, cùng dạng với `Trip.createdAt` — nên vế "không entity nào có timestamp" sai ở
> cả 5 chỗ. Dòng `:31` được viết lại cho đủ: hai entity có `@CreationTimestamp`, còn
> `Incident.resolvedAt` và `Trip.saleOpenedAt` là **mốc nghiệp vụ**, không phải cột audit kỹ thuật —
> phân biệt này lấy từ Hidden Cost #2 của roadmap, để không ai đếm nhầm thành bốn.
>
> Bốn dòng còn lại giữ nguyên kết luận và nói thêm **vì sao kết luận vẫn đứng dù lý do cũ đã chết**:
> hai cột `@CreationTimestamp` vẫn không đủ phủ cho một activity feed chung, và **không entity nào có
> `updatedAt`**. Không file code nào bị đụng.

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

> **✅ ĐÃ SỬA (2026-08-13).** `TripService` nay dùng `@Slf4j` (Lombok, khớp `@RequiredArgsConstructor`
> sẵn có — **không thêm dependency**, slf4j đã nằm trong `spring-boot-starter`). Cả **12** lời gọi
> `System.out.printf` của class được chuyển sang logger, chia mức theo ngữ nghĩa:
>
> | Mức | Số dòng | Là gì |
> |---|---|---|
> | `debug` | 4 | nằm trên đường quét 10 giây — chẩn đoán của một job nền |
> | `info` | 6 | sự kiện thật, mỗi lần một hành động (tạo chuyến tăng cường, admin duyệt/phân công/từ chối/xoá) |
> | `warn` | 2 | cảnh báo thật (không tự phân công được; bằng lái sắp hết hạn) |
>
> **Mục này ghi 3 dòng spam. Thật ra có 4** — dòng `🔥 ... HOT` cũng lặp vô hạn, và nó là dòng nguy
> hiểm nhất: tại `:82` điều kiện là `isHotTrip(trip) && !hasAlreadySuggested(trip)`, mà `&&` lượng giá
> **trái trước**, nên một chuyến đã được đề xuất rồi vẫn in `🔥` mỗi 10 giây mãi mãi. **Đây đúng là
> kịch bản Phase 9 sẽ tạo ra** — Booking làm `ticketsSold` tăng, chuyến vượt 90%, dòng này bật.
>
> **CỐ Ý KHÔNG đổi thứ tự hai điều kiện ở `:82`.** Đảo lại sẽ diệt spam tận gốc nhưng là **thay đổi
> logic** (bắt mọi chuyến `ACTIVE` chạy một query tồn tại mỗi 10 giây, trong khi hiện tại cổng
> occupancy in-memory chặn trước) — trộn việc vào một bản dọn log. Ghi lại ở đây như một mục riêng
> nếu chủ dự án muốn xét sau. `fixedRate = 10_000` **giữ nguyên** — là hành vi nghiệp vụ, đúng như
> mục này dặn.
>
> **Bằng chứng hai chiều, đo trên app thật** (không thể chứng minh bằng sự vắng mặt, vì dữ liệu hiện
> tại không có chuyến nào >90% ghế — 4 chuyến `ACTIVE` đều 0%). Đã tạo điều kiện để dòng debug **buộc
> phải** bắn: đặt `trips.tickets_sold=48` cho chuyến 14 (`total_seats=50` → 96%), chuyến này đã khởi
> hành nên rơi đúng Gate 3.
>
> | Mức log | Số dòng về chuyến #14 |
> |---|---|
> | mặc định (INFO), ~4 vòng quét | **0** |
> | `logging.level...TripService=DEBUG`, ~30 vòng | **32** (1 dòng/10 giây — đúng hành vi cũ) |
>
> Tức chẩn đoán **được giữ lại sau một công tắc**, không bị xoá; và 32 dòng đó chính là ảnh chụp của
> lỗi gốc. Bật lại bằng `-Dlogging.level.giang.com.BusManagement.service.TripService=DEBUG`.
>
> **Không đổi một byte nội dung thông điệp nào.** Đã so bản cũ vs bản mới trên cùng một test: phần
> text giống hệt tới từng byte; chỗ nào có `%.1f`/`%.0f` thì truyền `String.format(...)` làm tham số
> để giữ nguyên cách làm tròn. Logger chỉ **thêm** tiền tố timestamp/level/PID/thread/logger.
> Vỡ mã tiếng Việt trên console là **có sẵn** (bản `System.out` cũ vỡ y hệt — đã đo, không suy luận;
> skill `verify` cũng đã ghi nhận từ trước), không phải do bản sửa này.
>
> **Phạm vi dừng ở `TripService`.** `DataInitializer` (1) và `HistoricalDataBackfill` (11) vẫn dùng
> `System.out` — chúng là script seed chạy một lần, xuất ra cho người đang ngồi xem terminal, và
> chuyển chúng là việc không liên quan.
>
> Kiểm chứng: `mvnw clean test` **71/71**; 14/14 trang admin 200; **0** exception; snapshot DB
> **giống hệt** trước/sau (`tickets_sold` của chuyến 14 đã trả về 0).

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

> **✅ ĐÃ SỬA (2026-09-11).** `TripService.getDispatchBoardTrips(until)` nhận lời gọi
> `findDispatchBoardTrips(...)`; `DispatchController` bỏ field `TripRepository`, bỏ
> import, và bỏ hằng `BOARD_STATUSES` (tập trạng thái **hiển thị** giờ thuộc service —
> cùng lý do `getPendingTrips()` tự ôm `PENDING_APPROVAL`: nó là tham số của câu truy
> vấn). Câu query **không đổi một chữ**. Controller giữ lại `UPCOMING_WINDOW_HOURS`
> (màn hình quyết định nhìn xa bao nhiêu), việc chia 3 nhóm để hiển thị, và
> `BOARD_ACTIONS` — tập trạng thái **ĐÍCH**, một khái niệm khác hẳn, nên javadoc của
> nó được sửa để trỏ sang chỗ mới thay vì trỏ tới hằng đã bị xoá.
>
> **Vì sao tên `getDispatchBoardTrips` chứ không trung lập như `getAllTrips()` yêu cầu:**
> javadoc của `getAllTrips()` đòi tên trung lập cho một query **dùng chung nhiều màn**
> (`findAllWithDetails`). Query này chỉ tồn tại cho bảng điều hành và chính repository
> đã mang tên `findDispatchBoardTrips` — đặt tên trung lập ở tầng service sẽ làm đứt
> mạch truy vết giữa hai tầng.
>
> **Phạm vi cố ý hẹp:** chỉ `DispatchController`. Hai controller còn lại vẫn inject
> Repository và **vẫn mở**: `AdminController` (3 lời gọi `count()`, nợ chưa thành mục)
> và `AdminTripManagementController` (18 điểm chạm, **có cả đường ghi** — Warn #4 trong
> `project_report.md`). Gộp vào đây sẽ biến một bản dọn 2 dòng thành refactor 21 điểm
> chạm, trái quy tắc Atomic Changes. Lý do chọn `DispatchController` không phải vì nó
> nhỏ nhất mà vì nó là controller **duy nhất ra đời SAU khi §3 được viết** (Phase 1),
> tức nó vi phạm một luật đang có hiệu lực; hai cái kia là nợ có trước luật.
>
> **Kiểm chứng (drive app thật, default profile, PID 19092 khớp log — 0 ghi):** trang
> `/admin/dispatch` render **đúng 8 chuyến** `{12, 11, 6, 7, 3, 2535, 13, 14}`, khớp
> tuyệt đối với SQL viết độc lập theo đúng vị từ của query
> (`status IN (ACTIVE, DEPARTED) AND departure_time <= NOW()+48h AND is_deleted=0`);
> chuyến 8 (`PENDING_APPROVAL`) bị loại đúng. Nhóm: 5 `inProgress` + 3 `overdue` +
> 0 `upcoming` ⇒ 5×1 + 3×2 = **11 nút**, trang có đúng 11 `name="tripId"`. Đường GHI
> được kiểm bằng hai request **bị từ chối ở hai tầng khác nhau mà không ghi gì**:
> `newStatus=ACTIVE` bị `BOARD_ACTIONS` chặn ở controller (guard #10 còn nguyên), và
> `DEPARTED → CANCELLED` bị FSM chặn trong `updateTripStatus()` (chứng minh dây nối
> service vẫn sống sau khi dời lời gọi đọc). Cách kiểm này **mạnh hơn** so A/B hai
> build như dự định ban đầu: nó chứng minh kết quả *đúng*, không chỉ *không đổi*.
> `mvnw clean test` **75/75**, 17/17 trang admin 200, 0 exception
> template/SpEL/lazy-init, snapshot DB **khớp tuyệt đối** trước/sau.

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

> **✅ ĐÃ SỬA (2026-09-11).** Thêm `TripService.getAvailableAssistantsForTimeRange(departure, arrival)`
> — đúng bộ lọc của `getAvailableDriversForTimeRange()` **trừ** dòng trần 8h, giữ nguyên ba
> ràng buộc còn lại (active / bằng lái còn hạn **vào ngày khởi hành** / không trùng lịch
> trong cửa sổ ±`MIN_REST_BETWEEN_TRIPS_MINUTES`), khớp MỘT-MỘT với đúng những gì
> `validateStaffForTrip()` kiểm cho vai trò phụ xe. `TripRestController` trả thêm khoá
> `assistants`; `trip-create-form.html` đổ `assistants` vào `assistantSelect`.
>
> **Ba chỗ cố ý KHÔNG đổi** (đây là phần dễ sửa hỏng nhất, nên ghi ra): slot **Tài xế phụ**
> (`availableDriversData` → `addCoDriverSlot`) vẫn dùng `drivers`, vì tài xế phụ **có** chịu
> trần 8h (`validateStaffForTrip` ném "Tài xế phụ … sẽ vượt 8h/ngày") — đổ danh sách phụ xe
> vào đó sẽ tạo ra đúng lỗi ngược lại; cổng `hasResources` vẫn xét `drivers`, vì tài xế chính
> là bắt buộc nên 0 tài xế = không tạo được chuyến dù có bao nhiêu phụ xe; và dropdown Tài xế
> chính vẫn dùng `drivers`. Biến chết `assistantOptionsHtml` được xoá — nó nằm trong đúng 6
> dòng bị viết lại, không phải việc lạc. Lời gọi mới `buildDriverOptions(assistants, '-- Không có --')`
> bỏ tham số thứ ba, và `includeEmpty` mặc định `true`, nên tuỳ chọn "không có phụ xe" (bắt
> buộc với chuyến ≤ 8h) được giữ nguyên — đã kiểm trên trang đã render.
>
> **Không phá bất biến nào:** bản sửa không nới lỏng gì, nó chỉ **thôi áp một luật mà
> validator chưa bao giờ áp**. Tập phụ xe là siêu tập của tập tài xế, nên quy tắc một chiều
> của §8 vẫn đúng — đo được `drivers − assistants = ∅` ở cả ba mốc thời gian thử.
>
> **Test:** `TripServiceAssistantAvailabilityTest` (4 test, `@SpringBootTest` + `@Transactional`
> trên `busmanagement_test`). Chốt **cả hai chiều**: người trượt trần 8h phải vắng ở danh sách
> tài xế nhưng **có mặt** ở danh sách phụ xe; và phụ xe **vẫn** bị loại khi bị khoá / hết bằng
> lái / trùng lịch. Test mạnh nhất chạy thẳng `validateStaffForTripDryRun()` lên đúng con người
> bị giấu (validator **chấp nhận**), kèm đối trọng cùng người đó ở vai tài xế chính thì bị từ
> chối vì trần giờ — chứng minh sự bất đối xứng giữa hai vai là **có thật trong validator**,
> không phải giả định của bản sửa. **Non-vacuous:** bật lại filter 8h vào method mới làm đúng
> **2 test đỏ dạng `Failures`** (không phải `Errors`, tức dropdown thật sự co lại), hai test
> chốt ràng buộc-giữ-lại vẫn xanh. `mvnw clean test` **75/75**.
>
> **Kiểm chứng (drive app thật, PID 19092, read-only — 0 ghi, snapshot DB khớp tuyệt đối):**
> endpoint trả **ba** khoá `{buses, drivers, assistants}`; tuyến 30h khởi hành **hôm nay** →
> `drivers=20`, `assistants=31` ⇒ **11 người hợp lệ trước đây bị giấu** (userId 23–33); trang
> form đã render dùng `data.assistants` ở `:492` và `buildDriverOptions(assistants, …)` ở `:554`,
> `availableDriversData = drivers` ở `:514` giữ nguyên.
>
> ---
>
> **ĐÍNH CHÍNH 1 — con số "21/33, giấu 12 người" bên dưới sai VỀ BẢN CHẤT, không chỉ cũ.**
> `33` là tổng tài xế active **chưa qua bộ lọc bằng lái**, còn `21` là số **sau** bộ lọc giờ:
> hai con số ở hai tầng lọc khác nhau thì không trừ được cho nhau. Cả hai dropdown đều áp bộ
> lọc bằng lái. Số đúng, đo trên app thật ngày 2026-09-11: **20 vs 31 ⇒ giấu 11**.
>
> **ĐÍNH CHÍNH 2 — câu "5 tuyến ≤ 8h: không bị" bên dưới SAI, và đây là phần đáng giữ lại
> nhất.** Đo trên app thật cùng ngày: tuyến **2 giờ** khởi hành hôm nay cũng giấu **6** phụ xe
> hợp lệ (`drivers=25` vs `assistants=31`). Lỗi #13 cắn ở **mọi** tuyến, bất cứ khi nào có
> người có giờ lái > 0 trong ngày khởi hành — hiện nguồn duy nhất là giờ nền mock
> `totalDrivingHours24h` (chỉ cộng cho HÔM NAY), sau này là chuyến thật đã xếp vào ngày đó.
> Điều **thật sự** đặc biệt ở 3 tuyến > 8h không phải là "chỉ ở đó mới bị" mà là ở đó phụ xe
> là ràng buộc **BẮT BUỘC** (`validateStaffForTrip`), nên thiệt hại nặng nhất. Câu cũ đã lấy
> một tính chất đúng-về-mức-độ rồi phát biểu thành một khẳng định về **phạm vi** — cùng khuôn
> với các câu sai đã che lỗi #14/#18/#20, lần này nằm trong chính mục bug register.
>
> **Một cái bẫy đo lường cho lần sau:** nếu kiểm endpoint với khởi hành **ngày mai**, hai danh
> sách ra **31/31**, lệch 0 — vì `sumDrivingHours()` chỉ cộng giờ nền mock khi ngày xét là hôm
> nay, và mọi chuyến chưa-kết-thúc hiện đều nằm ở tháng 7–8. Đó là kết quả ĐÚNG, nhưng một lần
> kiểm như vậy **không chứng minh được gì**. Đã ghi vào `.claude/skills/verify/SKILL.md`.

- **Mức độ:** nhất quán code ↔ code ↔ tài liệu. **Tiềm ẩn — chỉ chạm được ở 3 tuyến > 8h.** *(phạm vi này SAI — xem Đính chính 2 ở trên)*
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

- **Biến chết trong `trip-create-form.html:546`:** ✅ **ĐÃ XOÁ cùng bản sửa #13 (2026-09-11)**,
  đúng như dự kiến bên dưới.
  `const assistantOptionsHtml = buildDriverOptions(drivers, '-- Không có --', false);` được tính
  rồi **không dùng ở đâu** — ba dòng ngay dưới (`:547-550`) dựng lại chuỗi **y hệt** bằng
  `drivers.map(...)` inline. Biến chết, **0 ảnh hưởng hành vi**. Không xoá kèm vì không thuộc bản
  sửa nào đang mở; xoá được cùng lúc với #13 (cùng file, cùng khối `renderResources`).
  *(Bản sửa #13 viết lại đúng khối đó: ba dòng inline được thay bằng một lời gọi
  `buildDriverOptions(assistants, '-- Không có --')`, nên biến chết biến mất như một hệ quả
  của việc dùng lại hàm, không phải một lần xoá lạc việc.)*

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

> **✅ ĐÃ SỬA (2026-08-11) — chủ dự án chọn phương án (a-đủ): áp NGUYÊN VẸN chính sách của
> `deleteTrip`, tức chặn sửa cả `DEPARTED` lẫn `COMPLETED`.**
>
> Luật nay phát biểu bằng **một câu**: *chuyến sửa được cho tới khi xuất phát; sau khi xuất phát nó
> là **bản ghi**, không còn là kế hoạch.* Đúng bằng tập trạng thái mà `TripService.deleteTrip()` nêu
> tên, nên hai lối vào khớp nhau **từng trạng thái một**, không phải khớp đại khái.
>
> **Ba lớp, phân vai rõ ràng — chỉ lớp thứ ba là lớp chặn thật:**
>
> | Lớp | Ở đâu | Vai trò |
> |---|---|---|
> | Ẩn nút Sửa | `trip-list.html` | không MỜI |
> | Từ chối mở form | `showEditTripForm()` (GET) | không MỜI (chặn URL gõ tay) |
> | **Từ chối ghi** | **`updateTrip()` (POST)** | **CHẶN THẬT — thứ duy nhất cản được POST tự chế** |
>
> Luật nằm ở **một chỗ duy nhất**, `editRefusalReason()`, dùng chung cho cả GET lẫn POST, để hai lối
> vào không thể trôi ra khác nhau — đúng cái đã sinh ra chính lỗi này. `switch` cố ý **không có
> `default`**: thêm một `TripStatus` mới sẽ làm vỡ biên dịch, buộc người thêm phải quyết định, y hệt
> `deleteTrip`.
>
> **Hai điều đã KIỂM TRƯỚC KHI SỬA, vì chúng quyết định (a-đủ) có lấy mất năng lực thật nào không —
> cả hai đều là không:**
> 1. **Hoàn thành chuyến `DEPARTED` không đi qua form sửa.** Nút "Hoàn thành" nằm ở **Bảng Điều
>    Hành**, post sang `/admin/dispatch/status` — endpoint khác hẳn. Query của bảng
>    (`findDispatchBoardTrips`) chỉ có cận **trên** (`departureTime <= now + 48h`), **không có cận
>    dưới**, nên chuyến `DEPARTED` cũ tới đâu cũng vẫn hiện. Đo 2026-08-11: **5/5** chuyến `DEPARTED`
>    đều có nút Hoàn thành, **0** chuyến bị bỏ sót.
> 2. **"Ghi nhận xe chạy trễ" vốn đã KHÔNG làm được.** `Trip` chỉ có `departureTime` và
>    `arrivalTimeExpected` — **không có trường giờ đến thực tế**. Sửa `arrivalTimeExpected` của chuyến
>    đang chạy là sửa lại **kế hoạch** cho khớp thực tế, tức đúng thứ mục này đang cấm. Năng lực đó
>    chưa bao giờ tồn tại nên không thể bị lấy mất.
>
> **Cái giá thật, sau khi trừ hai thứ trên:** chỉ còn **không sửa được giá / số ghế gõ nhầm sau khi
> xe đã lăn bánh**. Mà đó chính xác là thứ `deleteTrip` tuyên bố phải giữ nguyên (*"báo cáo tài chính
> phải được giữ nguyên"*), nên từ chối nó là **nhất quán**, không phải cứng nhắc.
>
> **(a-đủ) THAY THẾ guard hẹp của #14, không chạy song song.** Guard cũ chỉ khoá **ô xe** của chuyến
> `DEPARTED`; luật mới khoá **cả chuyến**, tức điều kiện mạnh hơn hẳn — giữ cả hai thì guard #14
> **không bao giờ chạy tới**, đúng loại code chết mà dự án đã bác khi loại phương án (B) của #15. Nên
> guard #14 và ô Xe `readonly` ở `trip-edit-form.html` được **gộp vào đây**; lý do bất biến FSM của
> #14 chuyển nguyên vào comment của luật mới và `trip_lifecycle_fsm.md` §8.1. Commit `de67c90` không
> sai — nó **bị thay thế** bởi một quyết định rộng hơn, y như Step B thay Step A ở Hidden Cost #9.
>
> **Thêm một kẽ mà #14 không với tới, nay đã đóng:** đổi **`route`** của chuyến `DEPARTED` làm đổi số
> km cộng vào odometer lúc `COMPLETED` (`TripService:621-627` đọc `trip.getRoute().getDistanceKm()`
> tại thời điểm hoàn thành).
>
> **Kiểm chứng trên app thật** (default profile, PID 17540, port 8099):
>
> | Thao tác | Kết quả | DB |
> |---|---|---|
> | `GET edit/13`, `edit/3` (`DEPARTED`) | **302** + *"Chuyến #13 đang trên đường (DEPARTED)…"* | — |
> | `GET edit/2764`, `edit/1` (`COMPLETED`) | **302** + *"…đã hoàn thành (COMPLETED). Dữ liệu lịch sử và báo cáo tài chính phải được giữ nguyên…"* | — |
> | `GET edit/2535` (`ACTIVE`), `edit/8` (`PENDING`), `edit/2`, `edit/1282` (`CANCELLED`) | **200**, form mở bình thường | — |
> | **POST sửa chuyến `COMPLETED` 2764** — đúng cú tái hiện lỗi: xe 23→10 **kèm** `price` 180000→999999 | từ chối | `bus_id=23`, `price=180000`, odometer xe 23 và 10 **không đổi** |
> | POST sửa chuyến `DEPARTED` 13 — xe 6→11 kèm `price=888888` | từ chối | `bus_id=6`, `price=200000` |
> | **ĐỐI TRỌNG:** POST sửa chuyến `ACTIVE` 2535 | *"Cập nhật chuyến xe thành công!"* | lưu bình thường |
> | Nút Sửa trên danh sách | `DEPARTED` 13/3 → **0**; `ACTIVE` 2535 và `PENDING` 8 → **1** | — |
> | Bảng Điều Hành | **5** nút "Hoàn thành" — lối hoàn thành còn nguyên | — |
>
> Hàng quan trọng nhất là hàng thứ tư: **cùng chuyến 2764 mà hôm 06-08 sửa được**, nay bị từ chối, và
> `price` gửi sai cố ý **không landing** ⇒ controller `return` trước khi chạm setter nào. DB đầu/cuối
> phiên giống hệt (`SUM_odo` 246145, `SUM_lastmaint` 227790), **0** exception.
>
> **Không có test đơn vị** (dự án vẫn chưa có harness MockMvc — đã ghi ở #9/#10), nên luật được ghim
> bằng test case viết ra: **TC_TRIP_040/041/042** và bản viết lại của TC_FSM_023/030/031/032.

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

> **✅ ĐÃ SỬA (2026-08-12) — chủ dự án chọn hướng "giữ tín hiệu chống lệch", sau khi cả hai hướng
> ban đầu (siết / nới) đều bị bác vì mỗi hướng sai ở một ca khác nhau.**
>
> **Luật mới, một câu:** cờ `TRAVELING` chỉ chặn khi **không có chuyến `DEPARTED` nào giải thích nó**;
> nếu có chuyến thật đứng sau cờ, quyền quyết định thuộc về luật cửa sổ thời gian (`isBusBusy`).
>
> **Vì sao không phải "siết" (chặn mọi xe `TRAVELING`)** — hướng này từng được duyệt hôm 2026-08-11 rồi
> phải revert, và đo lại ngày 12-08 vẫn hỏng: `isBusBusy` **đã** chặn ca cửa sổ giao nhau, nên cờ chỉ
> thêm được ca **không giao nhau** — xếp xe cho chuyến tuần sau trong khi hôm nay nó đang chạy, vốn là
> vận hành bình thường. **Bán kính, đo lại chính xác hơn lần 08-11:** vẫn 2 chuyến mang hình dạng này
> (8 trên xe 20, 14 trên xe 7, cả hai cửa sổ không giao), nhưng chỉ **chuyến 8** là bằng chứng trực
> tiếp — nó **đã sửa được thật** trên app sau bản vá này. **Chuyến 14 thì vốn đã không lưu được** vì
> một luật hoàn toàn khác: *"Tài xế chính … đã được phân công lái 2.0h trong ngày 2026-08-01, thêm
> chuyến này (7.5h) sẽ vượt 8h/ngày"* — nên siết chỉ **thêm cho nó một lý do chặn thứ hai**. ⇒ **Bán
> kính mà siết THÊM VÀO là 1 chuyến, không phải 2**; ghi chú ngày 2026-08-11 đếm 2 là đúng về *hình
> dạng dữ liệu* nhưng chưa tách ra ca đã bị chặn sẵn. *(Phép đo này cũng chứng minh luôn bản vá chạy
> đúng: lỗi của chuyến 14 đến từ `validateStaffForTrip` ở `TripService:976`, tức `validateBusForTrip`
> ở dòng **975 ngay trước đó đã CHO QUA** chiếc xe 7 đang `TRAVELING`.)*
>
> Tệ hơn con số: `showEditTripForm():224-225` **cưỡng bức thêm xe hiện tại** vào dropdown ⇒ siết thì
> form sẽ MỜI đúng chiếc xe nó sắp từ chối, phá bất biến một chiều của #12 — lần thứ ba anti-pattern
> này xuất hiện và bị bác. **Lập luận này độc lập với số chuyến**, nên nó mới là lý do quyết định.
>
> **Vì sao không phải "nới" (bỏ hẳn nhánh)** — cờ này là tín hiệu **độc lập** với bảng `trips`. Roadmap
> §9 đã ghi `Bus.status` có hai người ghi và có thể lệch với lịch chuyến. Bỏ nhánh đi thì **xe 19
> (`51B-DAG.CH`, `TRAVELING` mà không có chuyến `DEPARTED` nào — fixture `DataInitializer:158`) trở
> thành gán được**, và `TC_FSM_007` bị đảo. Fail-closed trước một trạng thái không nhất quán mới là
> đúng.
>
> **Ba ca, ba câu trả lời — chỉ hướng đã chọn đúng cả ba:**
>
> | Ca | Nới | Siết | **Đã chọn** |
> |---|---|---|---|
> | TRAVELING + có chuyến DEPARTED + cửa sổ **giao** | chặn | chặn | chặn |
> | TRAVELING + có chuyến DEPARTED + cửa sổ **không giao** | cho | **chặn nhầm** | cho |
> | TRAVELING + **không** chuyến nào giải thích | **cho nhầm** | chặn | chặn |
>
> **Chi phí bằng 0 về hạ tầng:** dùng lại `TripRepository.existsByBusIdAndStatusIn()` **đã có sẵn**
> (`BusService.updateBus()` đang dùng cho guard #15) — **không thêm query mới**. Và query **không cần
> vế loại trừ chuyến hiện tại** như mục #19 dự đoán, vì chuyến đang được validate không bao giờ ở trạng
> thái `DEPARTED`: #18 chặn sửa chuyến đã xuất phát, #20 chỉ cho duyệt chuyến `PENDING_APPROVAL`, còn
> `createManualTrip` thì chuyến chưa tồn tại. **Việc hoãn #19 lại sau #14 đã trả lãi đúng như §9 dự
> đoán — thậm chí hai lần**, vì #18 và #20 tiếp tục thu hẹp nó.
>
> **Kiểm chứng:** `mvnw clean test` **71/71** (+4 ca trong `TripServiceValidationDryRunTest`, phủ đúng
> ba ca trên cộng một ca chốt thẳng vào bản thân lỗi #19: *cùng xe, cùng cửa sổ ⇒ tạo mới và sửa chuyến
> đã có phải cho cùng một câu trả lời*). **Non-vacuous:** khôi phục nguyên trạng logic cũ ⇒ **đúng 4
> đỏ**, 8 ca cũ vẫn xanh. **App thật (PID 32492):** xe 19 bị từ chối với thông điệp mới; xe 7 cửa sổ
> không giao **tạo được**; xe 7 cửa sổ giao bị chặn bởi *"đang bận trong khoảng thời gian này"* (đúng
> luật cửa sổ, không phải cờ); xe 18 `REPAIRING` vẫn bị từ chối (đối trọng). **Và ca chốt:** duyệt
> chuyến `PENDING` #8 bằng xe 19 — cùng input mà `/trips/create` vừa từ chối — nay **cũng bị từ chối
> với đúng thông điệp đó**, chuyến 8 không bị ghi gì. 9/9 màn admin 200, 0 exception, DB giống hệt
> trước/sau.
>
> **Câu hỏi nghiệp vụ ghi ở cuối mục này đã được trả lời:** *"một xe đang trên đường có được xếp lịch
> cho chuyến TƯƠNG LAI không?"* → **CÓ**, miễn cửa sổ không giao nhau. Các dropdown vẫn lọc `READY`
> (chặt hơn validator — đúng chiều được phép), nên giao diện không đổi.

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

---

# Rà ngày 2026-08-12 — kiểm chứng lại bản sửa #18 (chủ dự án chưa test), phát sinh #20

*Chủ dự án yêu cầu kiểm lại `e5afa8f`/`8975c7a` — đã push nhưng anh chưa kịp test. Bản sửa #18
**đứng vững toàn bộ** (đo lại độc lập, không đọc lại ghi chép cũ rồi tin: GET/POST bị từ chối trên
`DEPARTED`/`COMPLETED` với giá + xe + số ghế bị đổi cố ý mà không trường nào rơi xuống DB; đối trọng
`ACTIVE`/`CANCELLED` vẫn sửa được thật; nút Sửa 0 lần cho hai trạng thái bị chặn và 3/1/98 lần cho
ba trạng thái còn lại — khớp đúng số chuyến trong DB; bảng điều hành vẫn đủ 5/5 chuyến `DEPARTED`;
`mvnw clean test` 61/61; DB giống hệt trước/sau). Trong lúc truy dấu "còn lối ghi nào khác vào
`trips`" thì tìm ra #20 — **lỗi có sẵn từ trước, không phải do bản sửa #18.***

## 20. Hai lối PHÊ DUYỆT đi vòng qua FSM — duyệt được cả chuyến `CANCELLED`/`COMPLETED`/`DEPARTED`

> **✅ ĐÃ SỬA (2026-08-12) — chủ dự án chọn phương án B′ (guard ở service, một helper dùng chung cho
> cả hai lối) + lớp KHÔNG-MỜI ở `showApproveForm`.**

- **Mức độ:** **lỗi thật, hỏng dữ liệu** — không phải latent. Đã tái hiện trên app thật.
- **Phân loại (ba nhánh):** nhánh **3 — lỗi thật**. Xem phần "vì sao không phải tính năng" bên dưới,
  vì nhánh 1 ở mục này có một lập luận **thật** cần bác bỏ chứ không phải bù nhìn.
- **Ở đâu:** `TripService.approveTrip():893` và `TripService.confirmAutoAssignedTrip():848`, cả hai
  kết thúc bằng `changeStatusToActive():1656` — hàm **set thẳng** `trip.setStatus(ACTIVE)`, không
  bao giờ gọi `canTransition()`.

| | |
|---|---|
| `canTransition()` được gọi ở | **đúng 1 chỗ**: `TripService:579`, trong `updateTripStatus()` |
| Số lối vào `ACTIVE` | **4**: `updateTripStatus:605`, `createManualTrip:953`, `confirmAutoAssignedTrip:869`, `approveTrip:931` |
| Số lối có kiểm from-state | **2** (hai lối đầu, bảo đảm bằng cấu trúc) |

- **Tái hiện thật (2026-08-12, app thật, PID khớp log):** chuyến **2749** đang `CANCELLED` — trạng
  thái CUỐI, `trip_lifecycle_fsm.md` §5 xếp vào bảng *"Invalid Transitions (**Enforced**)"* — bị lật
  sang `ACTIVE` qua **cả hai** cửa `POST /admin/trips/approve` và `POST /admin/trips/confirm`, app
  báo *"kích hoạt thành công"*. Đã trả về `CANCELLED` bằng đường hợp lệ (`ACTIVE → CANCELLED`), DB
  nguyên vẹn.

### Vì sao KHÔNG phải tính năng — lập luận phản biện phải bác bỏ

`Proj_functions_summary.md:53` (bản cũ) **có ghi hẳn lý do biện minh**:

> *"`createManualTrip()` và `approveTrip()`/`confirmAutoAssignedTrip()` dùng `changeStatusToActive()`
> trực tiếp vì đây là trường hợp tạo/duyệt — **không có "from state" cần kiểm tra transition**."*

Câu đó **đúng 1/3**:

| Method | Có from-state? | Bằng chứng |
|---|---|---|
| `createManualTrip(Trip)` | **Không** — câu văn đúng | Nhận `Trip` **transient** (id `null`, chưa có bản ghi trong DB) ⇒ không có trạng thái cũ nào để chuyển đi |
| `approveTrip(...)` | **CÓ** | Dòng đầu: `tripRepository.findById(tripId)` — `TripService:895` |
| `confirmAutoAssignedTrip(...)` | **CÓ** | Dòng đầu: `tripRepository.findById(tripId)` — `TripService:849` |

Một tính chất **chỉ đúng với method thứ nhất** đã bị khái quát cho cả ba. Và ba tài liệu khác nói
ngược lại: `Proj_functions_summary.md:51` (*"**Mọi** thay đổi status hợp lệ **phải** đi qua
`updateTripStatus()`"*), `trip_lifecycle_fsm.md:3` (*"sole authoritative implementation… **no status
change should bypass it**"*), và `trip_lifecycle_fsm.md` §5 (chữ **Enforced**).

**Nhánh 2 (quyết định hết hiệu lực) cũng bị loại:** `git log -S "changeStatusToActive"` chỉ ra **một**
commit (`18fb37f`) — chưa từng có kiểm tra trạng thái nào bị gỡ. Nó ra đời thiếu, không bị tháo ra.

### Vì sao lỗ này sống qua BA lần rà toàn dự án

`Proj_functions_summary.md:52` ghi: *"Các luồng **đã được audit và sửa để không bypass FSM**:
`cancelTrip()`, `rejectTrip()`, `updateTrip()`"*. Tức đã từng có hẳn một chiến dịch truy bypass FSM —
và nó **dừng ngay ở dòng 53**, vì tài liệu nói ở đây không có gì phải kiểm. Đợt rà 2026-08-05 còn
liệt kê đủ **cả bốn** call site của `changeStatusToActive`, nhưng câu hỏi lúc đó là *"validate nghiệp
vụ có chạy không?"* — với approve/confirm thì **có chạy**. Không ai hỏi *"transition này có hợp lệ
không?"*.

Đối xứng đáng nhớ, và chính nó làm lỗi ẩn được lâu:

| Cửa | Kiểm FSM | Kiểm nghiệp vụ |
|---|---|---|
| `POST /admin/dispatch/status` | ✅ | ❌ → **lỗi #10**, đã vá bằng allow-list |
| `POST /admin/trips/approve` \| `/confirm` | ❌ → **lỗi #20** | ✅ |

Cùng khuôn với **#14**, nơi dự án đã ghi: *"chính câu sai đó đã che lỗi #14 khỏi các lần rà trước"*.

### Hậu quả

1. **Hồi sinh trạng thái cuối** — đã dò thật.
2. **Cộng odometer lần hai:** `COMPLETED` → (approve) → `ACTIVE` → (bảng ĐH) → `DEPARTED` →
   `COMPLETED` chạy lại khối `TripService:617-628`. Đúng họ hỏng dữ liệu của **#6**, qua cửa khác.
   *Suy luận từ code — **cố ý KHÔNG chạy**: không có đường hoàn tác qua app, vì đưa `ACTIVE` về
   `COMPLETED` buộc phải đi qua `DEPARTED`→`COMPLETED`, tức tự gây ra chính sự hỏng đó.*
3. **Phá con trỏ xe (họ #14):** `approveTrip:906` `setBus()` **trước** mọi kiểm tra ⇒ xe cũ của
   chuyến `DEPARTED` kẹt `TRAVELING` vĩnh viễn. #18 chỉ đóng cửa form Sửa.
4. **Mở bán vé lại:** `changeStatusToActive` đóng dấu `saleOpenedAt` nếu đang null.

**Khả năng chạm từ UI = 0** (`getPendingTrips():1548` lọc đúng `PENDING_APPROVAL`; chỉ
`pending-trips.html` link tới màn duyệt) — **đúng hạng #10**, thứ dự án đã coi là lỗi và vẫn vá.

### Bản sửa

- **`TripService.requirePendingApproval(Trip)`** — một helper giữ luật, gọi ở **đầu** cả hai method
  (khuôn `editRefusalReason()` của #18: một luật, hai lối vào, không thể trôi ra khác nhau). Ném
  `IllegalStateException` — cùng kiểu mà `deleteTrip()` và `updateTripStatus()` dùng cho vi phạm
  chính sách theo trạng thái. Ở `approveTrip` phải đứng **trước `setBus()`**, nếu không con trỏ xe
  đã kịp dời trước khi bị từ chối.
- **Kiểm bằng `!=` chứ không phải `switch`-không-`default`** như `deleteTrip`: ở đó mỗi trạng thái có
  chính sách và thông điệp riêng nên bắt người thêm `TripStatus` phải quyết định là đúng; ở đây luật
  chỉ có một vế, và một trạng thái mới rơi vào nhánh **từ chối** là mặc định **an toàn** (fail-closed)
  và cũng là câu trả lời đúng. Sao chép hình dạng `switch` vào đây là bắt chước hình thức.
- **Lớp KHÔNG-MỜI:** `AdminTripController.showApproveForm()` từ chối mở form cho chuyến không phải
  `PENDING_APPROVAL`. **Không phải tô điểm:** thiếu nó, bản sửa sẽ *tự tạo ra* đúng anti-pattern
  "mời thứ sẽ từ chối" — thứ dự án vừa bác khi revert #19 ngày 2026-08-11 — vì trước bản sửa form
  này submit **được**.
- **Hai `catch (IllegalStateException)`** ở controller: nếu để rơi vào nhánh `Exception` thì lời từ
  chối hiện ra là *"Lỗi hệ thống"*, tức đổ cho hệ thống hỏng trong khi hệ thống đang chạy đúng. Cùng
  cách phân biệt mà `AdminTripManagementController.updateTrip()` đã dùng cho ngoại lệ FSM.
- **Đã cân nhắc và TỪ CHỐI (fix gốc):** đưa hẳn transition về `updateTripStatus(tripId, ACTIVE)` để
  thực thi đúng câu ở `Proj_functions_summary.md:51`. Đúng gốc nhất, nhưng đổi **luồng điều khiển
  của đường mở bán vé** — nơi nhạy cảm nhất — trong khi §3 buộc tối thiểu hoá thay đổi trong
  `TripService`. Ghi lại làm giới hạn đã biết, cùng khuôn với việc **tách cột `Bus.status`** đã bị
  từ chối ở #14/#15. B′ không chặn đường lên phương án này về sau.
- **`TripService:1656` (`changeStatusToActive`) nay ghi rõ bất biến** "chỉ vào `ACTIVE` từ
  `PENDING_APPROVAL`" kèm bảng 4 call site và ai bảo đảm nó — để người thêm call site thứ 5 không
  tái tạo lỗi này.

### Kiểm chứng

- **Test:** 6 ca thêm vào `TripServiceStatusTransitionTest` (cùng chủ đề "hợp lệ hoá transition",
  không dựng class song song) — 4 ca từ chối + **2 đối trọng**. `mvnw clean test` **67/67**.
  **Non-vacuous:** vô hiệu hoá hai lời gọi guard ⇒ **đúng 4 đỏ**, và đỏ dạng `Failures` (assertion)
  chứ không phải `Errors` — nghĩa là lệnh duyệt đã **chạy thành công**, bằng chứng mạnh nhất.
  Ca `approveTrip_onDepartedTrip_isRefusedAndDoesNotMoveTheBusPointer` khẳng định thẳng vào con trỏ
  xe, không chỉ vào trạng thái.
- **App thật (PID 20784 khớp log, profile mặc định):** 4 phép POST từng phá được hệ thống nay đều bị
  chặn, DB không đổi — chuyến 13 vẫn ở xe 6 dù `busId=1` được gài cố ý. GET form duyệt: 302 cho
  `CANCELLED`/`COMPLETED`/`DEPARTED`/`ACTIVE`, **200 cho `PENDING_APPROVAL`**. **Đối trọng trên app
  thật:** dựng một chuyến `PENDING_APPROVAL` riêng (không đụng chuyến 8 của chủ dự án), duyệt qua
  **cả hai** cửa — đều `→ ACTIVE` và đóng dấu `saleOpenedAt` — rồi **xoá cứng** fixture đó.
  16/16 màn admin còn 200, **0** exception, DB giống hệt trước/sau (`trips` 1500, phân bố trạng thái
  và cả 20 odometer/status không đổi).

### Ảnh hưởng của bản sửa #20 lên mục #19

*(Viết khi #19 còn mở. **#19 đã được sửa ngay sau đó, cùng ngày 2026-08-12** — xem khối kết luận ở
đầu mục #19. Giữ lại đoạn này vì nó ghi đúng cơ chế "sửa cái này làm cái kia teo lại".)*

Bản sửa #20 chèn code vào `TripService` nên số dòng mục #19 trích đã dịch; mục #19 giữ nguyên theo
luật append-only. **#20 không đóng #19**, nhưng **thu hẹp nó thêm một bậc**: lối `approveTrip` nay chỉ
nhận chuyến `PENDING_APPROVAL`, mà chuyến `PENDING_APPROVAL` thì xe của nó không bao giờ `TRAVELING`
*vì chính nó* — đúng điều mục #19 đã dự đoán khi hoãn lại sau #14.

**Và đó chính là thứ làm bản sửa #19 rẻ đi:** cộng với #18 (không sửa được chuyến đã xuất phát),
ba bản vá cùng bảo đảm *chuyến đang được validate không bao giờ ở trạng thái `DEPARTED`* — nên query
của #19 **không cần vế loại trừ chuyến hiện tại**, và dùng lại được `existsByBusIdAndStatusIn()` có
sẵn thay vì thêm query mới như mục #19 từng lo. Một minh hoạ sạch cho ghi chú §9 *"thứ tự sửa lỗi có
thể mang tải trọng, không chỉ là gọn gàng"*.

## Mục nhỏ (2026-08-12)

- **`AdminTripManagementController.showCreateTripForm():68` nạp `buses` và `drivers` rồi KHÔNG ai dùng.**
  `busRepository.findAll()` + `driverRepository.findAll()` được đẩy vào model mỗi lần mở form Tạo, nhưng
  `trip-create-form.html` **không render hai biến đó**: `<select id="busSelect">` (`:225`) khởi tạo
  `disabled` và rỗng, chỉ được JavaScript đổ dữ liệu từ `/api/admin/trips/available-resources` — API này
  lọc `READY` + `!isBusBusy` + bảo trì. **Không phải lỗi** (không hành vi nào sai, và form KHÔNG mời xe
  `TRAVELING`/`REPAIRING` như một phán đoán ban đầu ngày 12-08 đã tưởng — phán đoán đó **sai**, đã kiểm
  lại bằng `grep` toàn template: không có `th:each` nào trên `${buses}`/`${drivers}`) — là **rác**: hai
  query thừa (20 xe + 36 tài xế) trên mỗi lần mở form. Cùng loại với hai query chết ghi ở đợt rà
  2026-08-04. Dọn thì xoá hai dòng, nhưng đó là **quyết định của chủ dự án**, không nằm trong phạm vi #19.
- **`templates/admin/suggestions.html` là template MỒ CÔI** — không controller nào render (`grep`
  toàn `src/main/java` không ra lời gọi nào). Nó còn `POST` tới `/admin/trips/approve/{id}`, một
  đường **không tồn tại** (mapping thật là `@PostMapping("/approve")` không path-variable, và
  `@GetMapping("/approve/{id}")`). **Không phải lỗi** — không hành vi nào sai vì không ai tới được
  — là **rác cần dọn**, cùng loại với hai query chết đã ghi ở đợt rà 2026-08-04. Đáng dọn vì nó mô
  tả một giao diện phê duyệt không còn tồn tại, dễ làm người sau tin nhầm.

---

# Kiểm chứng độc lập #5 và #13 trước khi commit (2026-09-19) — cả hai đứng vững, đã commit và push

Rà theo yêu cầu chủ dự án (*"kiểm tra lại, đảm bảo đây chính xác là một lỗi và đã được fix hoàn
toàn, không gây tổn thất hay thiếu nhất quán"*), **đo lại từ đầu thay vì tin ghi chú 2026-09-11**.
Cả hai qua bài kiểm tra ba chiều ở nhánh **3 — sai thuần tuý**: #13 vì HEAD đổ đúng danh sách đã
lọc 8h vào `assistantSelect` trong khi `validateStaffForTrip():1224-1241` không có dòng giờ nào cho
phụ xe; #5 vì git chứng minh luật §3 sinh `79b5c4a` 14:20 ngày 2026-07-16 còn `DispatchController`
sinh `765c062` 15:15 **cùng ngày** (hai controller kia có từ tháng 3–4, trước luật). Bằng chứng đầy
đủ ở `THESIS_ROADMAP.md` §8 mục 2026-09-19. **Commit:** `ccee141` (#5), `a6e28af` (#13), `9f9ba15`
(docs) — push `origin/temp`; stash `wip-5-13` đã drop.

## Mục nhỏ (2026-09-19)

- **Màn SỬA chuyến mời toàn bộ tài xế, không lọc — đã ghi từ trước ở `docs/reports/project_report.md`
  mục 🟣 #3, nhưng mục đó cũ nửa câu.** Phát hiện trong lúc kiểm #13 (câu hỏi tự nhiên: màn Sửa chọn
  phụ xe thế nào?). `showEditTripForm():232` đổ `driverRepository.findAllWithUser()` — **36/36** hồ
  sơ, gồm **3** đã khóa + **2** hết bằng, tức 5 người `validateStaffForTrip()` từ chối ngay — vào cả
  hai dropdown (`trip-edit-form.html:95`, `:108`), trong khi ô Xe cùng màn có lọc (`:222`). Chiều
  **ngược** với #13 (mời người sẽ bị từ chối, thay vì giấu người sẽ được nhận); validator vẫn chặn ở
  bước lưu nên không mất dữ liệu. #3 của `project_report.md` đã gộp cả form tạo lẫn form sửa vào
  một câu "chưa gọi API", trong khi form tạo đã gọi từ `27f38de` (2026-06-17) — trước cả khi câu đó
  vào `docs/` (`07af949`). Đã **đính chính tại chỗ** ở `project_report.md` (nửa tạo đóng, nửa sửa
  mở kèm bán kính đo hôm nay). **Không sửa code** — quyết định của chủ dự án, không gộp vào #13.
  *Bài học ghi lại:* lần rà 2026-09-19 lúc đầu khẳng định quan sát này *"chưa được ghi ở đâu"* vì chỉ
  `grep` file này; `project_report.md` đánh số riêng (Bug/Warn/Incon/🟣) và cũng là nơi giữ mục mở.
  Trước khi tuyên bố "chưa ghi", phải quét **cả hai** file.

---

# Rà soát toàn dự án lần bốn (2026-09-19) — năm lỗi mới #21–#25, chín mục nhỏ, một danh sách đã loại

Rà theo yêu cầu chủ dự án (*"xem lại thật kỹ, ko chỉ dựa vào tài liệu, kiểm tra xem có vấn đề,
thiếu nhất quán gì không"*), chạy ngay sau khi #5/#13 được commit (`ccee141`/`a6e28af`/`9f9ba15`/
`f0cb06b`). **Trạng thái nền:** `mvnw test` 75/75, `temp` == `origin/temp`, mọi lỗi #1–#20 đã đóng.

**Phương pháp — code và dữ liệu thật trước, docs chỉ để đối chiếu.** Bốn nguồn bằng chứng, theo
thứ tự: (1) đọc lại từng `@PostMapping` và từng đường `repository.save()`; (2) **13 câu SQL bất
biến** chạy read-only trên DB thật (loại xe ↔ tuyến, số ghế ↔ sức chứa, vé > ghế, DEPARTED ↔
TRAVELING, odometer ↔ bảo trì, trùng lịch xe/tài xế, bằng lái/khoá, sự cố ↔ chuyến…) — 9/13 ra
**0**, 4 câu ra dòng thật và mỗi dòng dẫn về một chỗ code; (3) một **test probe tạm** trên
`busmanagement_test` (viết, chạy, **xoá**, không commit — bản sao ở thư mục tmp của phiên) cho lỗ
hổng mà đo trên DB thật sẽ phải ghi; (4) **drive app thật** (PID 27652, default profile) cho các
finding nằm ở nút bấm/form — chỉ GET, cộng đúng một POST chắc chắn không ghi được vì đụng unique
constraint; snapshot DB **khớp tuyệt đối** trước/sau. Mỗi ứng viên qua bài ba chiều và được quét
**cả ba** file (`current_bugs_found.md`, `project_report.md`, `THESIS_ROADMAP.md`) trước khi gọi
là mới — bài học 2026-09-19 buổi sáng.

**Chưa sửa gì. Chờ quyết định của chủ dự án.**

---

## 21. MỌI endpoint "tạo mới" nhận `id` gửi lên và GHI ĐÈ bản ghi có sẵn — với Trip là cửa thứ năm vào `ACTIVE`, đi vòng qua #18 và #20

> **✅ ĐÃ SỬA (2026-09-19, phiên bốn) — phương án A, tripwire ở tầng service cho cả sáu cửa; NOT
> COMMITTED.** Khuôn của `BusService.saveBus()` (#16) áp cho từng đường tạo: `createManualTrip()`,
> `IncidentService.createIncident()`, `DriverService.createDriver()` (kiểm cả `user.id` lẫn
> `driver.userId`), `AdminService.createNewUser()` ném `IllegalArgumentException` khi entity đã có
> khoá. Hai upsert được **tách**: `RouteService.saveRoute()` → `createRoute(route, stationIds)` +
> `updateRoute(id, form, stationIds)`; `StationService.save()` → `createStation()` +
> `updateStation(id, form)` — đường cập nhật nạp bản ghi theo id từ URL rồi chép từng field (khuôn
> `updateBus`/`updateIncident`), nên merge nguyên object form biến mất và **cột form không gửi
> không còn bị ghi đè thành NULL** (route 1 từng mất `suitable_bus_type_id` vì thế). Controller
> tuyến/bến đổi sang gọi đúng hàm; không thêm `@InitBinder` — đó là lớp không-mời, caller nội bộ
> vẫn lọt, và project đã ba lần chọn "chặn ở service".
>
> **Test:** `CreatePathIdTripwireTest` (9): sáu tripwire, mỗi test khẳng định *ném* **và** *bản ghi
> nạn nhân không đổi* (admin giữ role/mật khẩu, chuyến COMPLETED giữ vé, tuyến giữ loại xe và lộ
> trình…), cộng hai test cho đường cập nhật mới (chép field, dựng lại lộ trình đúng thứ tự, không đẻ
> dòng mới). Non-vacuous: tắt tripwire Station → đúng 1 đỏ. `mvnw clean test` **90/90**.
>
> **Kiểm chứng trên clone DB thật (JVM riêng, port 8098, PID 12120, `processlist` 10/10 vào clone):**
> phát lại **nguyên văn 8 request** đã phá hoại buổi sáng → 8/8 bị từ chối với flash *"Lỗi:
> createX() chỉ dùng để tạo mới (id phải trống)…"*, chữ ký MD5 bảng `trips`, tuyến 1, bến 1, sự cố
> 9, user 1/37, `COUNT(drivers)` đều **IDENTICAL**. Regression: tạo tuyến/bến/sự cố/tài xế/user/chuyến
> bằng form thật → đều thành công (+1 dòng đúng chỗ); sửa tuyến 10 (đổi km, bỏ loại xe, đảo lộ
> trình 3→2→1) và sửa bến 12 → đúng, không đẻ dòng mới. 20/20 trang 200, 0 exception template. DB
> thật snapshot khớp tuyệt đối. **Một cạnh còn lại, thuộc #25:** cửa `/admin/users/save` từ chối
> đúng (không ghi) nhưng hiện ra **HTTP 500** vì `AdminController.saveUser()` không có `try/catch`.
>
> **Docs:** `trip_lifecycle_fsm.md` §5.1 (dòng `createManualTrip` — "id == null" nay là điều được
> bảo đảm) và §8.1 (đính chính "one door" lần hai); `Proj_functions_summary.md` (Route/Station
> service). **Vì sao không sửa ở `changeStatusToActive()` hay FSM:** lỗ hổng nằm ở *tầng tạo mới*
> (upsert), không ở FSM — chặn đúng chỗ thì FSM không cần biết đến nó.

> **✅ KIỂM CHỨNG LẠI END-TO-END (2026-09-19, phiên ba) — ĐỨNG VỮNG, và hai chỗ bên dưới được đính
> chính.** Chủ dự án yêu cầu *"thật sự test, chứ không tự suy luận"*: lần ghi đầu chỉ có 3 probe ở
> tầng service; lần này **clone toàn bộ DB thật sang `busmanagement_test`** (mysqldump, chữ ký MD5
> 2.071 chuyến khớp), chạy một JVM thứ hai (port 8098, PID 28056, `processlist` xác nhận 10/10
> connection vào bản clone, 0 vào DB thật) và **POST thật qua HTTP** vào từng endpoint như một client
> bên ngoài. DB thật snapshot **khớp tuyệt đối** trước/sau. Kết quả từng đường:
>
> | Đường | POST với `id` của… | Kết quả đo được |
> |---|---|---|
> | `/trips/create` | chuyến **3327** `COMPLETED`, 35 vé, 120.000đ, xe 8 | flash *"Tạo chuyến xe thành công!"*; dòng 3327 → **`ACTIVE`, 0 vé, 1đ, xe 23, tài xế 2, `sale_opened_at` đóng dấu mới**; `COUNT(*)` 2071 → 2071. `created_at`/`is_extra_trip`/`original_trip_id` giữ nguyên (form không gửi, giá trị vốn là mặc định) |
> | `/trips/create` | chuyến **3** `DEPARTED`, xe 7 `TRAVELING` | thành công; 3 → `ACTIVE` trên xe 23; **xe 7 kẹt `TRAVELING` với 0 chuyến `DEPARTED` giải thích** — đúng thiệt hại #14, và từ đó bị cả dropdown (lọc `READY`) lẫn `validateBusForTrip` (#19) loại vĩnh viễn cho tới khi admin sửa tay |
> | `/trips/create` | chuyến **10** đã **soft-delete** | **KHÔNG hồi sinh, không tạo dòng mới** — Hibernate ném `StaleStateException` (*"Row was already updated or deleted"*), flash *"Lỗi: …"*. Soft-delete tình cờ được bảo vệ; ghi để không ai phóng đại mục này |
> | `/routes/create` | tuyến **1** (120 km / 120′ / loại 1, trạm 1→2) | → **999 km / 999′ / `suitable_bus_type_id = NULL`**, lộ trình thành 11→10; 8 → 8 tuyến. Cột form không gửi bị **xoá thành NULL** (merge chép cả null). Blast radius: **416** chuyến (388 `COMPLETED`, 2 `DEPARTED`) đang trỏ tuyến này — nhãn tuyến trên mọi màn, quãng đường cộng odometer lúc hoàn thành, và series dự báo của tuyến 1 đều đổi theo |
> | `/stations/create` | bến **1** | tên → `HIJACKED-STATION`; 11 → 11 |
> | `/incidents/create` | sự cố **9** (xe 17, chuyến 1, tài xế 2, `VEHICLE_BREAKDOWN`) | flash *"Đã ghi nhận sự cố mới!"*; → xe 2, chuyến **NULL**, tài xế **NULL**, `OTHER`, mô tả `HIJACKED`, **`resolved_at` bị đóng dấu lại**; `reported_at` giữ (`updatable = false`); 8 → 8 |
> | `/drivers/create` | user **1 = admin** (chưa có driver row) | flash *"Thêm mới tài xế thành công!"*; **admin → `hijacked-admin`, `ROLE_DRIVER`, mật khẩu mới, thêm một driver row** (drivers 36 → 37). **Tài khoản admin duy nhất biến mất.** |
> | `/drivers/create` | user **2** đã là tài xế | **THẤT BẠI, rollback, 0 thiệt hại** — Hibernate `NonUniqueObjectException` khi `persist(driver)` với `@MapsId` trùng PK. *(Đính chính: dòng bảng gốc bên dưới viết "ghi đè bất kỳ User nào" — đúng chỉ với user **chưa** có driver row, tức admin/user thường; user đã là tài xế thì không.)* |
> | `/users/save` | user **37** (`ROLE_DRIVER`) | → `hijacked-user`, **`ROLE_ADMIN`**, mật khẩu `newpw`; 37 → 37. Tự phong admin bằng một request |
>
> **Thống kê thiệt hại trên sandbox sau chín request:** 0 admin còn lại · 1 user tự phong admin ·
> 2 chuyến `COMPLETED`/`DEPARTED` hồi sinh thành `ACTIVE` · 1 xe kẹt `TRAVELING` mới · tuyến 1 đổi
> km kéo theo 416 chuyến · 1 bến đổi tên · 1 sự cố mất liên kết chuyến/tài xế. Không dòng nào để
> lại dấu vết phân biệt được với thao tác hợp lệ (không audit column ngoài `created_at`).

- **Mức độ:** toàn vẹn dữ liệu + bất biến vòng đời. **Tiềm ẩn về UI (form không gửi `id`), chạm
  được bằng POST tự chế** — cùng lớp với #10 và #20, cả hai đã được coi là lỗi và sửa.
- **Phân loại:** nhánh **3 — sai thuần tuý**, và còn là **trái với một quyết định đã có**: #16 đã
  đặt tripwire *"`saveBus()` từ chối entity đã có id, nếu không JPA sẽ merge và ghi đè mọi cột"*
  (`BusService:56`) — nhưng chỉ cho xe. Sáu đường tạo còn lại không có.
- **Cơ chế, hai nửa đều đã chứng minh bằng test probe (3/3 xanh) chứ không suy luận:**
  1. Không có `@InitBinder` nào trong toàn `src/main/java` ⇒ `@ModelAttribute` bind **mọi** property
     có setter, kể cả `id`. Probe: `new WebDataBinder(new Trip()).bind({"id":"2064"})` ⇒ `getId() ==
     2064`.
  2. `SimpleJpaRepository.save()` hỏi `isNew(entity)` = "id có null không"; id ≠ null ⇒ `em.merge()`
     ⇒ **UPDATE** dòng đó bằng toàn bộ trạng thái của object form. Probe trên `busmanagement_test`:
     một chuyến `COMPLETED` (40 ghế, **30 vé**, giá 100.000đ) → gọi `createManualTrip(trip có id đó,
     status ACTIVE như controller set)` ⇒ dòng đó thành **`ACTIVE`, vé về 0, giá 1đ, `count()` không
     đổi** (không có dòng mới). Probe thứ ba: `stationService.save(Station{id có sẵn})` ⇒ tên bến bị
     ghi đè, `count()` không đổi.
- **Ở đâu — bảy đường tạo, sáu hở:**

  | Endpoint | Chuỗi gọi | Guard `id` |
  |---|---|---|
  | `POST /admin/trip-management/trips/create` | `AdminTripManagementController.createTrip():76` → `TripService.createManualTrip():946` → `tripRepository.save():956` | **không** |
  | `POST /admin/routes/create` | `AdminRouteController.createRoute():41` → `RouteService.saveRoute()` — hàm này còn dùng chính `route.getId() == null` để **quyết định** tạo hay sửa (`:64`) | **không** |
  | `POST /admin/stations/create` | `AdminStationController.createStation():30` → `StationService.save()` | **không** |
  | `POST /admin/incidents/create` | `AdminIncidentController.createIncident():47` → `IncidentService.createIncident()` → `save()` | **không** |
  | `POST /admin/drivers/create` | `AdminDriverController.createDriver():39` → `DriverService.createDriver()` → `userRepository.save(user)` — ghi đè **bất kỳ** `User` nào (kể cả admin), ép `role = ROLE_DRIVER`, rồi `driverRepository.save(driver)` với `@MapsId` | **không** |
  | `POST /admin/users/save` | `AdminController.saveUser():48` → `AdminService.createNewUser()` → `save()` | **không** |
  | `POST /admin/buses/create` | `AdminBusController.createBus():33` → `BusService.saveBus()` | ✅ ném `IllegalArgumentException` (#16) |

- **Vì sao với Trip là nặng nhất — nó phá hai bản sửa đã commit và một câu trong tài liệu kiến
  trúc:** `trip_lifecycle_fsm.md` §5.1 liệt kê bốn cửa vào `ACTIVE` và miễn trừ `createManualTrip()`
  với lý do *"không phải transition — Trip còn transient (id == null)"*. Câu đó **chỉ đúng khi
  endpoint bảo đảm id null**, mà endpoint không bảo đảm. Gửi `id` của một chuyến `COMPLETED` /
  `CANCELLED` / `DEPARTED` là thực hiện đúng ba transition §5 ghi *"Invalid — ENFORCED"*, không qua
  `canTransition()`, không qua `requirePendingApproval()` (#20), không qua `editRefusalReason()`
  (#18), và còn **xoá `ticketsSold`** — thứ #18 gọi là "lịch sử tài chính". §8.1 nói *"Status changes
  now leave exactly one door"* — sai lần thứ hai, cùng khuôn với lần #20 đã đính chính. Với
  `DEPARTED` còn thêm thiệt hại #14 (xe cũ kẹt `TRAVELING`): merge đổi `bus_id` của một chuyến
  đang chạy.
- **Bán kính:** chạm được từ ngoài bằng một request; **0 dòng dữ liệu hiện bị hỏng** (đo: không
  thấy dấu vết — không thể phân biệt sau khi xảy ra, đó cũng là vấn đề). `foreign_key_checks=0`
  không cản gì. Không cần đăng nhập (permit-all, CSRF tắt — §4 Non-Goals).
- **Cách sửa (để chủ dự án chọn):** (A) tripwire kiểu `saveBus()` ở **đầu mỗi service create**
  (`createManualTrip`, `saveRoute` khi gọi từ create, `StationService.save` tách create/update,
  `createIncident`, `createDriver`, `createNewUser`) — nhất quán với #16, service tự bảo vệ, không
  phụ thuộc controller; (B) `@InitBinder` + `setDisallowedFields("id")` trên các handler create —
  ít dòng hơn nhưng là lớp **không-mời**, không phải lớp **chặn thật** (một caller nội bộ vẫn lọt), và
  project đã ba lần chọn "chặn ở service" (#16, #20). Riêng `RouteService.saveRoute()` cần tách
  quyết định tạo/sửa ra khỏi `route.getId()` — controller edit đã `route.setId(id)` từ path (`:83`),
  nên tách được mà không đổi hành vi edit.
- **Vì sao chưa sửa:** đụng 6 service + có thể 6 controller; là một quyết định về **khuôn** (A hay
  B) áp cho toàn bộ tầng tạo mới, không phải một dòng vá.

---

## 22. `Trip.totalSeats` không bao giờ được đối chiếu với sức chứa xe — đường AI chép số ghế của chuyến gốc TRƯỚC khi chọn xe; chuyến 6 (DEPARTED) đang bán 40 ghế trên xe 22 chỗ

> **✅ ĐÃ SỬA (2026-09-19, phiên bốn) — luật chốt: `totalSeats ≤ sức chứa xe được gán`; NOT
> COMMITTED.** Chủ dự án yêu cầu sửa; luật được chọn là luật **tối thiểu đúng**: bán ít hơn sức
> chứa là quyết định kinh doanh (hợp lệ), bán nhiều hơn là bán ghế không tồn tại (chặn). Hai nửa:
> (1) **một luật cứng mới ở `validateBusForTrip()`** — sau các luật bảo trì — so `trip.totalSeats`
> với `bus.busType.capacity`, bỏ qua khi xe chưa gán loại (không có thông tin thì không đoán, cùng
> cách các luật bảo trì bỏ qua khi `maintenanceThreshold == null`). Vì đây là chỗ duy nhất giữ luật,
> nó tự phủ tạo thủ công, sửa, cả hai cửa duyệt và cổng dry-run của Recommendation. (2)
> **`createExtraTrip()` đặt số ghế = sức chứa xe AI chọn** sau khi phân công thành công (cùng cách
> `RecommendationService.buildCard():235` và form tạo chuyến `readonly` từ `data-capacity`); không
> có xe thì giữ số của chuyến gốc làm chỗ trống — validator so lại lúc Admin phân công tay. Helper
> `seatCapacityOf(Bus)` dùng chung cho cả hai nửa để chỉ có **một** định nghĩa sức chứa.
>
> **Test:** `TripServiceSeatCapacityTest` (6): 30 > 22 bị từ chối kèm đúng số trong thông điệp; = 22
> và < 22 đều qua; xe không loại → bỏ qua; đường throw (`createManualTrip`) cùng luật kèm đối trọng
> 22 ghế tạo được; và **hai test chạy thẳng `scanAndSuggestExtraTrips()`** với chuyến gốc 40 ghế
> 97,5 % trên xe không đúng loại: có xe 22 chỗ → chuyến tăng cường **22 ghế** (không phải 40), gán
> đúng tài xế rảnh; không còn xe → giữ 40 làm chỗ trống. Non-vacuous: tắt luật → 2 đỏ, bỏ đặt ghế
> theo xe → 1 đỏ. `mvnw clean test` **90/90**.
>
> **Kiểm chứng trên clone DB thật (PID 12120):** tạo chuyến 30 ghế trên xe 22 chỗ → *"Lỗi: Chuyến
> mở bán 30 ghế nhưng xe 11A-111.11 (Limousine) chỉ có 22 chỗ — hãy giảm số ghế của chuyến hoặc chọn
> xe lớn hơn!"*, `COUNT(trips)` không đổi; cùng request với 22 ghế → tạo được. Chuyến 8 (dữ liệu
> thật, 30 ghế / xe 22 chỗ): `confirm` → *"Lỗi xác nhận: …chỉ có 22 chỗ…"*, `approve` thủ công cùng
> xe → *"Vi phạm ràng buộc: …"*, vẫn `PENDING_APPROVAL`; **lối thoát cho dòng cũ đã chứng minh:** sửa
> chuyến 8 về 22 ghế qua form Sửa (PENDING sửa được) → *"Cập nhật thành công"* → `confirm` → `ACTIVE`
> 22/22. DB thật khớp tuyệt đối.
>
> **Dữ liệu thật cần chủ dự án xử lý tay (không tự sửa):** chuyến **8** `PENDING` 30/22 — nay bị cả
> hai cửa duyệt từ chối cho tới khi hạ về ≤ 22 (một thao tác sửa); chuyến **6** `DEPARTED` 40/22 —
> không sửa được (#18), sẽ hoàn thành qua Bảng Điều Hành như lịch sử, 0 vé đã bán nên không có gì
> lệch. `DataInitializer` (profile `demo`) vẫn seed chuyến gốc với số ghế ≠ sức chứa (40 trên xe 22)
> vì ghi thẳng qua repository — dữ liệu seed, ngoài phạm vi; chuyến tăng cường mà AI sinh từ chúng
> nay đúng sức chứa. **Cạnh còn lại, ghi để biết:** đổi *loại* của một xe (`BusService.updateBus`)
> sang loại nhỏ hơn khi xe đang gánh chuyến mở bán nhiều ghế hơn sẽ tạo dòng lệch mà không luật nào
> bắt — cùng hình dạng hai-người-ghi của #15; chưa có ruling.
>
> ✅ **2026-09-21 — chuyến 8 đã hạ 30 → 22 qua form Sửa trên DB thật** (theo yêu cầu của chủ dự án
> *"kiểm tra lại, nếu đúng và cần thiết thì sửa"*; PID 30692, profile mặc định): POST đúng mọi trường
> form đang hiển thị, chỉ đổi `totalSeats` → *"Cập nhật chuyến xe thành công!"*. Chứng minh chỉ một ô
> đổi: hash lại toàn bộ 2.072 dòng `trips` với ghế của chuyến 8 giả lập về 30 ra **đúng** MD5 gốc
> (`group_concat_max_len` đã nâng); các bảng khác giữ nguyên chữ ký. **Vẫn để `PENDING_APPROVAL`, không
> tự duyệt:** duyệt không cần cho tính nhất quán, chuyến khởi hành 2026-07-20 (đã qua hai tháng), và
> đây là dòng chờ duyệt **duy nhất** trong DB — tức là dữ liệu sống để demo màn Duyệt. Nút sẽ chạy
> (phiên bốn đã chứng minh `confirm` → `ACTIVE 22/22` trên clone cùng dữ liệu); duyệt hay từ chối là
> một cú bấm của chủ dự án. Xem THESIS_ROADMAP §8 mục 2026-09-21.
>
> **Docs:** `trip_lifecycle_fsm.md` §7 (thêm dòng luật), `current_functional_spec.md` (ràng buộc xe
> + bước tạo chuyến tăng cường), `Proj_functions_summary.md` §6.7 + §7.4 (kèm đính chính hai câu cũ
> trong §6.7 đã lạc hậu từ trước: "TRAVELING chặn vô điều kiện" — sai từ #19; "bảo trì chỉ warning"
> — sai từ trước Phase 0).

> **✅ KIỂM CHỨNG LẠI END-TO-END (2026-09-19, phiên ba) — ĐỨNG VỮNG, thêm bằng chứng cổng duyệt cho
> qua.** Trên bản clone: `POST /admin/trips/confirm tripId=8` (chuyến AI, 30 ghế, xe 20 = 22 chỗ) →
> flash *"đã được kích hoạt thành công!"* → chuyến 8 **`ACTIVE`, `total_seats = 30`, `capacity = 22`**.
> Tức `confirmAutoAssignedTrip()` chạy đủ `validateBusForTrip()` + `validateStaffForTrip()` mà vẫn
> mở bán 30 ghế trên xe 22 chỗ. Đo lại dữ liệu thật: chuyến 6 (`DEPARTED`) 40/22 và chuyến 8
> (`PENDING`) 30/22, cả hai `tickets_sold = 0` — nên thiệt hại **hiện tại** là 0 vé bán quá, nhưng
> chỉ vì chưa có luồng bán vé (Phase 9 chưa làm) chứ không phải vì có gì chặn. Hậu quả đo được ngay:
> với chuyến 6, 20 vé bán ra sẽ đọc là 20/40 = 50 % (không "đông") thay vì 20/22 = 91 % (đông) — bộ
> quét 10 giây, dashboard và ngưỡng 0,90 đều bị lừa theo cùng hướng.

- **Mức độ:** nhất quán nghiệp vụ; **có dòng thật đang sai** trên DB.
- **Phân loại:** nhánh **3** với một dè dặt: không tài liệu nào **phát biểu** luật "số ghế ≤ sức chứa
  xe", nhưng **ý định** đó nằm ở hai chỗ code mới hơn — form tạo chuyến để ô `totalSeats`
  **`readonly`** và tự điền từ `data-capacity` của xe (`trip-create-form.html:292`, `:677-681`);
  `RecommendationService.buildCard():235` đặt `candidate.setTotalSeats(capacity)` từ xe được chọn.
  Chỉ đường **cũ nhất** (scheduler) và form Sửa là không theo. Chủ dự án cần chốt luật trước khi
  sửa; nếu chốt "số ghế = số ghế mở bán, độc lập với xe" thì mục này thành *không phải lỗi* —
  nhưng lúc đó phải giải thích được vì sao form tạo khoá ô đó.
- **Ở đâu:** `TripService.createExtraTrip():164` — `extraTrip.setTotalSeats(trip.getTotalSeats())`
  chạy **trước** `autoAssignResources()` (`:170`); sau khi có xe (`:173`) không cập nhật lại.
  `confirmAutoAssignedTrip()` / `approveTrip()` không nhận/không chỉnh số ghế. `validateBusForTrip()`
  (5 luật, `trip_lifecycle_fsm.md` §7) không có luật nào so `totalSeats` với `bus.busType.capacity`.
  Form Sửa cho gõ tự do (`trip-edit-form.html`: `min="1"` phía client).
- **Dữ liệu thật (đo 2026-09-19):** 3 chuyến chưa kết thúc có `total_seats ≠ capacity`, trong đó
  **2/3 chuyến AI** — chuyến **6** (`DEPARTED`, tăng cường của chuyến 1): **40 ghế trên xe 20
  (Limousine, 22 chỗ)**, tức hệ thống sẵn sàng bán 18 vé không có ghế; chuyến **8**
  (`PENDING_APPROVAL`, của chuyến 3): 30 ghế trên xe 22 chỗ. Gốc rễ là chuyến gốc do
  `DataInitializer` seed đã lệch (chuyến 1: 40 ghế trên xe 22; chuyến 3: 30 trên xe 50), và AI chép
  nguyên con số. Câu C (`tickets_sold > total_seats`) vẫn 0 — tức chưa ai bán quá, nhưng không gì
  ngăn.
- **Hậu quả ngoài chuyện bán quá ghế:** `getOccupancyRate() = sold / totalSeats` bị **kéo thấp** khi
  `totalSeats` > sức chứa thật ⇒ ngưỡng 0,90 (`needsReinforcement`, scheduler, dashboard) kích
  hoạt **muộn** trên xe nhỏ; và ngược lại, `RecommendationService` tính doanh thu bằng `capacity`
  của xe còn màn Trip tính bằng `totalSeats` — hai định nghĩa "sức chứa" cho cùng một chuyến.
- **Cách sửa (nếu chốt luật):** (1) `createExtraTrip()` đặt `totalSeats` từ `result.getBus()` sau
  khi phân công thành công (giữ số cũ khi thất bại — chưa có xe); (2) `approveTrip()` /
  `confirmAutoAssignedTrip()` đồng bộ `totalSeats = capacity` của xe được duyệt, hoặc ít nhất
  `validateBusForTrip()` chặn `totalSeats > capacity`; (3) mở khoá `readonly` ở form tạo là **không**
  cần — nó đang đúng.
- **Vì sao chưa sửa:** cần chốt luật; đụng `createExtraTrip()` và có thể validator.

---

## 23. `trip-list.html` MỜI hai thao tác mà service chắc chắn từ chối: "Hủy" trên chuyến `DEPARTED`, "Xóa" theo một điều kiện chỉ đúng cho nhánh `ACTIVE`

> **✅ ĐÃ SỬA (2026-09-19, phiên năm) — template hỏi service, không chép nữa; NOT COMMITTED.** Gốc
> chung với #24: chính sách nằm ở `TripService` nhưng template tự viết lại bằng tay rồi lệch. Sửa
> tận gốc bằng cách phơi chính sách ra để HỎI, không thêm luật: (1) **`TripService.deleteRefusalReason(trip)`**
> tách từ `deleteTrip()` — trả câu từ chối hoặc `null`; chính `deleteTrip()` gọi lại nó (một
> nguồn, khuôn `editRefusalReason()` của #18); (2) **`TripService.allowedTransitionsFrom(status)`**
> — liệt kê whitelist FSM từ chính `canTransition()` private (cùng tiền lệ public-overload của
> `getDrivingHoursForDate()`); (3) `AdminTripManagementController.populateTripList()` (dùng chung
> cho list + filter) tính ba `Set<Long>` `editableIds`/`cancellableIds`/`deletableIds` từ đúng ba
> chính sách đó — nút Sửa cũng được đưa về cùng cơ chế — và `trip-list.html` chỉ còn
> `th:if="${…Ids.contains(trip.id)}"`. Nút Hủy còn loại thêm same-state (chuyến đã `CANCELLED`
> không được mời "Hủy" dù FSM nhận no-op — phát hiện khi đo: bản đầu mời 136 nút vô nghĩa, đã sửa
> trước khi ghi). Nhãn "Xóa vĩnh viễn" đổi thành "Xóa chuyến (ẩn khỏi hệ thống)" cho đúng bản chất
> xóa mềm.
>
> **Test:** `TripServicePolicyExposureTest` (5) — bảng `allowedTransitionsFrom` cho 5 trạng thái;
> **tương đương với `updateTripStatus()` trên cả 25 cặp (from, to)**; luật nút Hủy; bảng
> `deleteRefusalReason`; **tương đương với `deleteTrip()` trên 5 trạng thái × có/không vé** (cùng
> câu từ chối ở cả hai nơi). Non-vacuous: tắt nhánh ACTIVE-có-vé → 1 đỏ. Suite **101/101**.
>
> **Kiểm chứng trên clone DB thật (PID 30188, 2.071 dòng render):** mọi tổng nút đối chiếu với SQL
> viết từ chính chính sách — Hủy trên `DEPARTED` **0**, trên `CANCELLED` **0**, tổng **4** (= PENDING +
> ACTIVE); Xóa trên `DEPARTED`/`COMPLETED` 0-vé **0**, tổng **140** = đúng `COUNT` của
> `status ∈ {PENDING, CANCELLED} ∨ (ACTIVE ∧ 0 vé)`; Sửa trên `DEPARTED`/`COMPLETED` **0**, tổng
> **140**. Trước fix: 9 nút Hủy (5 sai) và 9 nút Xóa (5 sai, 136 giấu). Lớp chặn thật ở service
> không đổi; DB thật khớp tuyệt đối.

> **✅ KIỂM CHỨNG LẠI END-TO-END (2026-09-19, phiên ba) — ĐỨNG VỮNG cả ba chiều.** Trên bản clone,
> gửi đúng ba POST mà các nút (hoặc chỗ nút bị giấu) sẽ gửi: (1) `cancel/3` — chuyến `DEPARTED`, nút
> **có** hiện → flash *"Không thể hủy: Lỗi luồng vận hành: … từ [DEPARTED] sang [CANCELLED]"*, DB
> không đổi; (2) `delete/9` — `COMPLETED` 0 vé, nút **có** hiện → flash *"Không thể xóa: Chuyến #9 đã
> hoàn thành…"*, DB không đổi; (3) `delete/2` — `CANCELLED` **21 vé**, nút **bị giấu** → flash *"Đã
> xóa chuyến xe #2 thành công."*, `is_deleted = 1`. Nghĩa là template mời đúng thứ bị từ chối và giấu
> đúng thứ được phép. Thiệt hại: không mất dữ liệu (service chặn đúng), là **10 nút chết + 136 thao
> tác hợp lệ bị giấu** trên dữ liệu thật hôm nay.

- **Mức độ:** lớp không-mời (UI ↔ chính sách service) — cùng lớp #12/#18/#19/#20 đã lập thành nguyên
  tắc. **Chạm được từ UI, không cần POST tự chế.**
- **Phân loại:** nhánh **3 — sai thuần tuý**.
- **Ở đâu:**
  - `trip-list.html:462-463` — nút **Hủy** hiện khi `status != CANCELLED and != COMPLETED` ⇒ hiện cả trên
    `DEPARTED`, mà `canTransition(DEPARTED, CANCELLED) == false` (`TripService:545`, `trip_lifecycle_fsm.md`
    §5 ghi đích danh *"`DEPARTED → CANCELLED`: Not in whitelist"*). Bấm ⇒ *"Không thể hủy: Lỗi luồng
    vận hành…"*, lần nào cũng vậy.
  - `trip-list.html:471` — nút **Xóa** hiện khi `ticketsSold == 0`. `deleteTrip():1669-1695` có chính
    sách **theo trạng thái**: `DEPARTED`/`COMPLETED` cấm tuyệt đối (kể cả 0 vé), `ACTIVE` chỉ khi 0
    vé, `PENDING_APPROVAL`/`CANCELLED` cho **vô điều kiện** (kể cả có vé). Điều kiện của template chỉ
    trùng với nhánh `ACTIVE`; sai cả hai chiều ở bốn nhánh còn lại.
- **Bán kính (đo trên trang đã render, PID 27652):** **5** form Hủy trên 5 chuyến `DEPARTED`
  `{3, 6, 11, 12, 13}` — 5/5 sẽ bị từ chối; **5** form Xóa trên các chuyến `DEPARTED`/`COMPLETED` 0 vé
  `{6, 9, 11, 12, 13}` — 5/5 sẽ bị từ chối; và **136** chuyến `PENDING_APPROVAL`/`CANCELLED` có vé
  (phần lớn là `CANCELLED` của backfill, vốn giữ nguyên vé — có chủ đích, xem §9) bị **giấu** nút Xóa
  dù service cho phép.
- **Cách sửa:** `th:if` soi đúng bảng: Hủy ⇔ `status ∈ {PENDING_APPROVAL, ACTIVE}`; Xóa ⇔
  `status ∈ {PENDING_APPROVAL, CANCELLED} ∨ (status == ACTIVE ∧ ticketsSold == 0)`. Thuần template,
  0 Java. Tiện thể: nhãn "Xóa vĩnh viễn" (`:475`) mô tả sai một thao tác **xoá mềm**.
- **Vì sao chưa sửa:** nhỏ nhưng là một quyết định UI nên đi cùng #24.

---

## 24. Form Sửa liệt kê đủ năm `TripStatus`; chọn một transition FSM cấm thì các trường khác ĐÃ được lưu trước đó rồi màn hình mới báo lỗi

> **✅ ĐÃ SỬA (2026-09-19, phiên năm) — mời đúng đích, và kiểm TRƯỚC khi ghi; NOT COMMITTED.** Hai
> lớp, cùng một nguồn luật (`TripService.allowedTransitionsFrom()`, xem #23): (1) **không-mời** —
> `showEditTripForm()` đưa `statuses = allowedTransitionsFrom(trip.status)` nên select chỉ liệt kê
> trạng thái hiện tại + các đích FSM nhận (ACTIVE → `ACTIVE/DEPARTED/CANCELLED`; CANCELLED → chỉ
> `CANCELLED`); (2) **chặn thật** — `updateTrip()` kiểm đích **trước** `updateManualTrip()`; đích
> không hợp lệ ⇒ flash *"Không thể đổi trạng thái chuyến #7 từ [ACTIVE] sang [COMPLETED] — chưa có
> thay đổi nào được lưu. Các đích hợp lệ: […]"* và **không field nào chạm DB**. Hai bước vẫn là hai
> transaction — **cố ý không gộp** vào một method service mới, vì gộp là tái cấu trúc đường bán vé
> (§3) cho một lỗ mà pre-check đã đóng; sau pre-check, `updateTripStatus()` chỉ còn có thể từ chối
> khi trạng thái đổi giữa hai request, và nhánh `catch` đó nay nói thẳng *"Thông tin chuyến ĐÃ được
> lưu, nhưng không đổi được trạng thái"*. Javadoc `updateTrip()` ghi lại đúng lý lẽ này.
>
> **Kiểm chứng trên clone DB thật:** form sửa chuyến 7 (`ACTIVE`) render đúng **3** option
> (`ACTIVE, DEPARTED, CANCELLED`), chuyến 2 (`CANCELLED`) render **1**; POST tự chế giá 200.000 → 111
> kèm `status=COMPLETED` → flash "chưa có thay đổi nào được lưu", DB **`ACTIVE`, giá 200.000** (trước
> fix: giá đã thành 123.456); POST hợp lệ giá → 222 giữ `ACTIVE` → lưu; POST hợp lệ giá → 333 kèm
> `ACTIVE → CANCELLED` → **cả hai bước chạy**, DB `CANCELLED`/333 — đường hai bước không hỏng.

> **✅ KIỂM CHỨNG LẠI END-TO-END (2026-09-19, phiên ba) — ĐỨNG VỮNG.** Trên bản clone: form sửa
> chuyến 7 (`ACTIVE`) render option `COMPLETED` (đo thật); gửi đúng form đó với **giá 200.000 →
> 123.456** và **status = COMPLETED** → HTTP 302 về `/edit/7`, flash *"Không thể đổi trạng thái: Lỗi
> luồng vận hành: … từ [ACTIVE] sang [COMPLETED]"*, nhưng DB: `status = ACTIVE`, **`price =
> 123456.00`** — giá đã ghi trong khi màn hình đang báo lỗi. Thiệt hại: không mất dữ liệu, nhưng
> người dùng bị dẫn tới kết luận sai ("chưa lưu gì") — và nếu họ sửa "lại" thì ghi hai lần.

- **Mức độ:** lớp không-mời + thông điệp gây hiểu nhầm. **Chạm được từ UI.**
- **Phân loại:** nửa đầu (select không lọc) là nhánh **3**; nửa sau (lưu trước, kiểm transition sau)
  là **thiết kế có chủ đích** — javadoc `updateTrip()` ghi rõ *"updateManualTrip() phải chạy TRƯỚC"*
  — nên không phải lỗi, nhưng **thông điệp** thì sai: người dùng nhận *"Không thể đổi trạng thái"* và
  quay lại form mà không được nói rằng tuyến/xe/giờ/giá vừa sửa **đã được ghi**.
- **Ở đâu:** `trip-edit-form.html:191-195` — `<option th:each="status : ${statuses}">` với
  `statuses = TripStatus.values()` (`AdminTripManagementController:234`). `updateTrip():345` gọi
  `updateManualTrip()` (`@Transactional`, **commit**) rồi `:351` gọi `updateTripStatus()`
  (`@Transactional` **thứ hai**); controller không transactional nên hai bước là hai transaction.
  Ngoại lệ FSM rơi vào `catch (IllegalStateException)` `:360` ⇒ flash lỗi, redirect về form.
- **Đo trên app thật:** form sửa chuyến 7 (`ACTIVE`) render đủ **5** option; FSM từ `ACTIVE` chỉ nhận
  `DEPARTED`/`CANCELLED` (và chính nó). Tương tự chuyến `CANCELLED` (terminal) vẫn được mời cả bốn
  trạng thái khác. Ghi chú: mục #15 (dòng 1412) từng **nhắc qua** select này khi tái hiện, chưa bao
  giờ thành mục riêng hay có ruling.
- **Cách sửa (hai phương án cho nửa đầu, một cho nửa sau):** select chỉ liệt kê `{trạng thái hiện
  tại} ∪ {đích hợp lệ theo canTransition}` — cần một hàm public nhỏ trong `TripService` (vd
  `allowedTransitionsFrom(status)`) vì `canTransition()` đang `private`, **hoặc** controller tự đặt
  tập đích theo bảng (nhân bản bảng FSM ra controller — trái §3). Với nửa sau: hoặc gộp hai bước vào
  một transaction (một method service mới — đụng `TripService`), hoặc giữ nguyên và **đổi thông
  điệp** thành *"Đã lưu thông tin chuyến, nhưng KHÔNG đổi được trạng thái: …"*. Chủ dự án chọn.
- **Vì sao chưa sửa:** liên quan `TripService`; cần ruling về mức độ (chỉ thông điệp, hay atomic).

---

## 25. `/admin/users/new` — form tạo user cũ: trùng username ⇒ HTTP 500; và mời `ROLE_DRIVER` ⇒ tạo user tài xế không có hồ sơ `Driver`, đúng thứ `DriverService` cảnh báo là "mồ côi"

> **✅ ĐÃ SỬA (2026-09-19, phiên năm) — giữ màn, đưa nó về cùng chuẩn với mọi controller khác; NOT
> COMMITTED.** (1) **`AdminService.createNewUser()`** từ chối `ROLE_DRIVER` (câu chỉ sang
> `/admin/drivers/create`, nơi tạo kèm hồ sơ `Driver` — đúng quyết định "một form gộp" của Phase 1),
> và kiểm username trống/trùng **trước** khi DB kịp ném unique-violation. (2) Luật "username duy
> nhất" nay ở **một** chỗ: `AdminService.requireUsernameAvailable(username, currentUserId)` —
> `DriverService` **uỷ quyền** sang (bản `validateUsernameAvailable` riêng của nó bị xoá), nên tạo
> lẫn sửa tài xế và tạo user thường không thể lệch nhau. (3) `AdminController.saveUser()` bọc
> `try/catch` → flash "Lỗi: …" về form (form thêm khối `${error}`, không icon vì trang này không nạp
> bootstrap-icons); `showUserForm()` không mời `ROLE_DRIVER`. Cái 500 của #21 ở cửa này cũng hết
> theo. Kiểu ngoại lệ đổi `RuntimeException` → `IllegalArgumentException` cho khớp mọi vi phạm đầu
> vào khác; controller tài xế bắt `Exception` nên flash không đổi.
>
> **Test:** `AdminServiceTest` (6) — tạo admin/user; trùng username bị chặn **trước DB** (probe: tắt
> luật thì ngoại lệ biến thành `DataIntegrityViolationException` — đúng triệu chứng 500 cũ);
> username trống; `ROLE_DRIVER` bị từ chối và không để lại user mồ côi; giữ tên của chính mình khi
> sửa; và **`DriverService` đi qua cùng luật**. Non-vacuous: tắt hai luật → 4 đỏ. Suite **101/101**.
>
> **Kiểm chứng trên clone DB thật:** `GET /users/new` chỉ mời `ROLE_ADMIN`/`ROLE_USER`; POST trùng
> `admin` → 302 về form + *"Lỗi: Tên đăng nhập 'admin' đã tồn tại…"* (trước: HTTP 500); POST
> `ROLE_DRIVER` → từ chối, 0 dòng; POST `id=37` → tripwire #21, nay thành flash thay vì 500; POST
> username trống → từ chối; POST hợp lệ → `?success=user`, users 37 → 38; POST tạo tài xế trùng
> `admin` → cùng câu từ chối (uỷ quyền chứng minh). Log sandbox **0 ERROR** (phiên bốn còn 1).

> **✅ KIỂM CHỨNG LẠI END-TO-END (2026-09-19, phiên ba) — ĐỨNG VỮNG cả hai nửa.** (a) Trên bản clone
> lặp lại POST trùng username → **HTTP 500**, `users` không đổi (lần đầu đã đo trên app thật, log
> `Duplicate entry 'admin'`). (b) `POST /admin/users/save username=orphan-driver role=ROLE_DRIVER`
> → 302 `?success=user`; `users` 37 → 38, `drivers` 37 → 37; user **42** có `role = ROLE_DRIVER` và
> **0 driver row**. Sau đó: `/admin/drivers` không liệt kê nó (0 lần xuất hiện), `/admin/drivers/edit/42`
> redirect về danh sách (không tìm thấy), và không tồn tại màn danh sách/sửa/xoá user nào
> (`AdminController` chỉ có `/users/new` + `/users/save`). Bản ghi đúng nghĩa **mồ côi**: không màn nào
> thấy, không màn nào sửa được, không thể gắn hồ sơ tài xế. Thiệt hại: một tài khoản rác vĩnh viễn mỗi
> lần lỡ tay.

- **Mức độ:** thấp; code có trước roadmap. **Chạm được từ UI.**
- **Phân loại:** nhánh **3** (hai lỗi độc lập trong cùng một màn).
- **Ở đâu:** `AdminController.saveUser():48-52` → `AdminService.createNewUser()` — endpoint **ghi duy
  nhất** trong project không có `try/catch` và không kiểm `findByUsername` (mọi controller khác đều
  flash + redirect; `DriverService.validateUsernameAvailable()` đã có sẵn nhưng không được gọi ở
  đây). `user-form.html:44` — `th:each="r : ${roles}"` với `Role.values()`.
- **Đo trên app thật:** `POST /admin/users/save username=admin` ⇒ **HTTP 500** (Whitelabel), log
  `DataIntegrityViolationException: Duplicate entry 'admin' for key 'users.UK…'`; DB không đổi (unique
  constraint chặn trước khi ghi — đó là lý do probe này an toàn). Nửa thứ hai chưa tái hiện (sẽ tạo
  dòng thật) nhưng đọc thẳng được: chọn `ROLE_DRIVER` ⇒ `users` có dòng role tài xế, `drivers` không
  — `DriverService.deleteDriver()` javadoc (`:123`) đã mô tả đúng bản ghi này là *"mồ côi không
  còn giao diện nào quản lý được"*, và Phase 1 (owner-approved) chọn **một form gộp User+Driver**
  chính để không sinh ra nó.
- **Cách sửa:** (1) `try/catch` + kiểm username như `DriverService`; (2) bỏ `ROLE_DRIVER` khỏi
  `roles` của màn này (tạo tài xế đi `/admin/drivers/create`), hoặc bỏ hẳn màn này nếu chủ dự án
  thấy nó thừa (chỉ tạo `ROLE_ADMIN`/`ROLE_USER` thủ công, không có màn sửa/xoá user).
- **Vì sao chưa sửa:** quyết định giữ hay bỏ màn thuộc chủ dự án.

---

## Mục nhỏ (2026-09-19) — không phải lỗi hành vi, hoặc cần một ruling trước

- ✅ **ĐÃ SỬA 2026-09-19 (phiên sáu):** javadoc nay gọi đúng `TripService.rejectTrip()` kèm ghi chú đính chính; bốn call site đếm lại bằng `grep` — `updateTrip():424`, `cancelTrip():457`, `DispatchController:116`, `rejectTrip():1411` — vẫn đúng bốn.
  *(Mục gốc:)* **Javadoc `updateTripStatus()` (`TripService:567`) gọi tên `TripService.cancelTrip()` — method không
  tồn tại.** Lối gọi thứ tư thật là `TripService.rejectTrip():1327`. Số đếm "đúng bốn call site" vẫn
  đúng, tên sai. Đây là chính cái javadoc-tripwire mà #10/#20 dựng để lần rà sau đếm — một câu sai
  trong tripwire thì đắt hơn một câu sai thường (§9). Sửa: đổi tên trong comment. 0 hành vi.
- ✅ **ĐÃ SỬA 2026-09-21 (phiên hai):** `isHotTrip()` nay gọi `trip.needsReinforcement()`; hành vi y hệt
  (cùng một double). **Cố ý KHÔNG đụng `DashboardService:270-271`:** hai literal đó là **biên histogram**
  (0,5 / 0,7 / 0,9, nhãn `"70-90%"`/`">90%"` là chuỗi cứng) — phần "ĐÃ KIỂM" bên dưới đã ruling không
  phải lỗi; buộc một biên theo hằng số trong khi nhãn đứng yên là tự tạo ra lệch nhãn-biên. §9 sửa số
  đếm thành **bốn bản khai báo** (Trip + 3 const) và ghi hai biên bucket là thiết kế biểu đồ, không
  phải bản sao. Ghim bằng `TripServiceScannerHotGateTest` (2 test qua `scanAndSuggestExtraTrips()`
  public: 36/40 = 0,90 → 0 chuyến tăng cường; 37/40 → 1). Non-vacuous theo đúng kịch bản cần bắt:
  scanner trôi về `<= 0.85` trong khi `Trip` giữ 0,90 → test biên đỏ **tại assert của scanner**
  (`expected: <0> but was: <1>`). `mvnw clean test` 108/108.
  *(Mục gốc:)* **`TripService.isHotTrip():101` so literal `0.9` trong khi đang cầm một `Trip`** — chỗ **duy nhất**
  trong hệ thống có thể gọi `trip.needsReinforcement()` mà không gọi. Đồng thời ghi chú §9 đếm ngưỡng
  0,90 có *"bốn bản sao"* (Trip / Dashboard / Forecast / What-if) là **đếm thiếu**: `TripService:101`
  và `DashboardService:270-271` (biên bucket) là bản 5 và 6. Hành vi y hệt (`<= 0.9` ⇔ `!(> 0.90)`).
  Sửa là một dòng trong `TripService` ⇒ **ruling** (§3 "minimize changes to TripService"), kèm sửa
  số đếm ở §9.
- ✅ **ĐÃ SỬA 2026-09-21 (phiên hai) — theo cách khác gợi ý gốc, và có một đính chính.** Kiểm trong
  `spring-boot-security-4.0.2.jar` (javap): `@EnableWebSecurity` — nguồn bean `HttpSecurity` mà
  `SecurityConfig` inject — nằm ở `ServletWebSecurityAutoConfiguration$EnableWebSecurityConfiguration`,
  **không** ở ba class đang import; nên "hoàn tất exclude trên annotation" với ba class đó không tắt
  được security và không khớp comment — **bác**. Tắt hẳn = bỏ starter + `SecurityConfig` +
  `thymeleaf-extras-springsecurity6`, trái §2 "permit-all by design" — **ngoài phạm vi**. **Đính chính
  mục gốc: `spring.security.user.name/password=a` KHÔNG chết** — boot với bản properties không có
  khối này (`spring.config.location`, không sửa repo) thì Boot in *"Using generated security password:
  a21eec2a-…"* + WARN *"development use only"*; hai dòng đó đang bịt cái log ấy. Nhưng giữ một
  credential giả `a/a` để bịt log là thứ phải giải thích khi bảo vệ, và file test không có hai dòng đó
  nên mỗi `mvnw test` vẫn sinh mật khẩu. Chốt: `@SpringBootApplication(exclude =
  UserDetailsServiceAutoConfiguration.class)` (không có login thì không có user mặc định; class sai
  tên trên annotation thì **không biên dịch được**, còn property sai tên thì Boot **lặng** — chính là
  lỗi này), xoá cả 4 dòng `spring.security.*`/`exclude` ở **cả hai** `application.properties` (file
  test có cùng dòng chết ở `:29`), xoá 2 import còn lại, viết lại `setup_guide.md` §5 (từng nói "fully
  disabled") + dòng bảng §7 + dòng cây thư mục. Kiểm: app code không đụng Spring Security ở đâu
  (grep = 0 ngoài `SecurityConfig`); boot **0** dòng generated password; 16/16 trang 200 không redirect
  login (gồm 5 URL của `TC_SEC_001`); POST không CSRF vẫn 302; 0 exception; output `mvnw test`
  **0** dòng generated password lần đầu tiên. **Mục nhỏ mới, ghi không sửa:**
  `thymeleaf-extras-springsecurity6` — `sec:` xuất hiện ở 0/27 template, dependency thừa, ruling riêng.
  *(Mục gốc:)* **`application.properties:30` `spring.autoconfigure.exclude` trỏ tới hai class ở package Boot-3
  (`org.springframework.boot.autoconfigure.security.servlet.*`) — không tồn tại trong Boot 4.0.2**
  (đã kiểm trong jar: class thật là `org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration`).
  Boot bỏ qua **im lặng** (log không một dòng) ⇒ security auto-config **đang bật**, app sống nhờ
  `SecurityConfig` permit-all; comment *"vô hiệu hóa hoàn toàn security"* ngay trên dòng đó là **sai**.
  `BusManagementApplication.java:6-8` import đúng ba class Boot-4 tương ứng mà **không dùng** — ai đó
  bắt đầu chuyển sang `@SpringBootApplication(exclude = …)` rồi bỏ dở. `spring.security.user.name/
  password=a` (`:27-28`) cũng chết. Hậu quả: **0**. Là rác cấu hình + một câu sai. Dọn: xoá 3 dòng
  properties + 3 import, hoặc hoàn tất `exclude` trên annotation — cách nào cũng cần chạy lại app.
- ✅ **ĐÃ SỬA 2026-09-19 (phiên sáu):** thêm dòng Entity Overview, section `### cost_parameters` (4 cột, hợp đồng một-dòng, luật > 0 của #11, ghi chú recompute-per-view), và bổ sung vào ER summary + mermaid. **Phát hiện kèm:** mermaid ER cũng **chưa từng có `incidents`** (Phase 2 bỏ sót từ tháng 7) — thêm luôn cùng ba quan hệ của nó.
  *(Mục gốc:)* **`docs/architecture/database_schema.md` không có bảng `cost_parameters`** (thêm 2026-07-24, Phase
  7 bước 3); có đủ 10 bảng còn lại. Tài liệu lạc hậu một bảng. Cùng loại với #3 (docs).
- ✅ **ĐÃ SỬA 2026-09-19 (phiên sáu):** dòng 31 nay mô tả đúng entity hiện tại — hai cột text đã xoá, điểm đi/đến suy từ `routeStations` theo `stopOrder` qua `getDepartureStation()`/`getDeparturePointDisplay()`… — khớp `database_schema.md:403` và `Route.java`.
  *(Mục gốc:)* **`Proj_functions_summary.md:31` vẫn liệt kê `Route.departurePoint`/`destinationPoint` (String) là
  field hiện tại** — code chỉ còn dòng comment *"ĐÃ XÓA"* (`Route.java:31-32`), và
  `database_schema.md:403` nói rõ đã bỏ. Hai doc mâu thuẫn nhau, một cái sai với code.
- ✅ **RULING + ĐÃ SỬA 2026-09-23: là GỢI Ý, không phải luật** — xem khối *"Group C(a) và C(c) ĐÃ SỬA"*
  cuối file. *(Mục gốc:)*
  **Loại xe ↔ `Route.suitableBusType` không phải luật ở validator, nhưng hai trong bốn đường "mời" lại
  lọc cứng theo nó.** `findBestAvailableBus()` (AI) và `getAvailableBusesForTrip()` (dropdown Duyệt/Sửa)
  lọc `findByStatusAndBusType`; `getAvailableBusesForTimeRange()` (API form Tạo — *"không lọc loại xe
  vì form này chưa biết tuyến nào"*, javadoc `:1452`) và `validateBusForTrip()` (5 luật, §7 FSM doc)
  **không**. Dữ liệu thật: **3** chuyến chưa kết thúc do người tạo sai loại (3, 13, 14) và **1.299**
  chuyến toàn cục (backfill quay vòng xe không xét loại — **đã biết**, chính là lý do Phase 6 dự báo
  tỉ lệ lấp đầy thay vì số vé). Không vi phạm bất biến một chiều (mời hẹp hơn validator là được phép).
  **Ruling:** loại xe là **luật** (thì thêm vào validator + API) hay **ưu tiên** (thì bỏ lọc cứng ở
  dropdown, hoặc ghi rõ là ưu tiên)? Không tự sửa.
- **Sự cố 9: `bus_id = 17` nhưng `trip_id = 1` mà xe của chuyến 1 là xe 1.** Form sự cố cho chọn xe
  và chuyến độc lập, `IncidentService.validate()` không đối chiếu. Không tài liệu nào phát biểu luật
  "nếu có chuyến thì xe phải là xe của chuyến". Dòng này sinh 2026-07-16 16:51 (ngày kiểm chứng
  Phase 2) — có thể là dấu vết một lần thử. **Ruling** về luật; nếu có luật thì đây là dòng dữ liệu
  cần sửa tay.
  ✅ **RULING 2026-09-21 (phiên hai): là ưu tiên, KHÔNG phải luật — không thêm validator, dòng 9 là dữ
  liệu hợp lệ.** Lý do từ chính project: Phase 2 (owner-approved) đặt `bus` bắt buộc và `trip` **độc
  lập** để sự cố quy về **xe hỏng** (Phase 7 đếm theo `incident.bus`); #18 làm `trip.bus` bất biến sau
  khi khởi hành, nên khi đổi xe giữa đường (chuyến 12 đi xe 14, xe 14 hỏng, khách sang xe 7, xe 7 lại
  gặp sự cố) thì sự cố trên xe thay thế **chỉ** ghi đúng được nhờ trường `bus` độc lập — luật
  `incident.bus == trip.bus` bắt ghi sai hoặc bỏ link chuyến. Không thêm JS "gắn xe theo chuyến" (form
  điền xe trước, chuyến sau — pre-fill ngược chiều). Đã ghi ở `current_functional_spec.md` (Incident
  business rules), `Proj_functions_summary.md`, THESIS_ROADMAP §9. Chủ dự án muốn dữ liệu demo sạch thì
  sửa tay dòng 9; hệ thống không cần luật.
- ✅ **RULING + ĐÃ SỬA 2026-09-23: CẢNH BÁO, không chặn** — xem khối *"Group C(a) và C(c) ĐÃ SỬA"*
  cuối file. Đính chính số liệu dưới đây: 5 chuyến `DEPARTED` nằm trên **4** tuyến (1 ×2, 2, 3, 4),
  không phải chỉ tuyến 1 và 2 — đo lại 2026-09-22/23. *(Mục gốc:)*
  **Sửa `Route.distanceKm` khi tuyến đang có chuyến `DEPARTED`** (`AdminRouteController.updateRoute()`
  không guard) làm đổi số km cộng vào odometer lúc hoàn thành — `updateTripStatus():623-627` đọc
  `route.getDistanceKm()` **tại thời điểm** `COMPLETED`. Đây là đúng cạnh mà #18 đóng cho `trip.route`
  (đổi tuyến của chuyến), còn mở qua **entity tuyến**. 5 chuyến `DEPARTED` hiện tại nằm trên tuyến 1
  và 2, cả hai sửa được. **Không chắc là lỗi**: sửa một quãng đường nhập sai thì có lẽ *nên* áp vào
  chuyến đang chạy. **Ruling.**
- ✅ **ĐÃ SỬA 2026-09-21 (phiên hai) — và xếp lại loại: đây KHÔNG phải Hidden Cost #4.** Đo ở 2.221
  chuyến: trang **367 KB / 1,2 s**, riêng `<select name="trip">` **353 KB = 96 % / 2.222 option**,
  `findAllWithDetails` (5 fetch-join) chạy mỗi lần mở form. Pagination áp cho *danh sách*; dropdown cần
  **phạm vi**. Sửa: `TripRepository.findRunningAndRecentTrips(live, ended, since)` (chỉ fetch `route`,
  mới nhất lên đầu) → `TripService.getRunningAndRecentTrips(since)`: `ACTIVE`/`DEPARTED` mọi ngày ∪
  `COMPLETED`/`CANCELLED` khởi hành từ mốc trở đi (`CANCELLED` giữ vì "hủy chuyến vì sự cố rồi ghi sự
  cố" là thứ tự tự nhiên; `PENDING_APPROVAL` không bao giờ — chưa có thật); cửa sổ
  `AdminIncidentController.RECENT_TRIP_DAYS = 7` do controller sở hữu (khuôn #5:
  `DispatchController.UPCOMING_WINDOW_HOURS` ↔ `getDispatchBoardTrips(until)`); form sửa force-add
  chuyến đang gắn khi đã ra khỏi cửa sổ (tiền lệ `showEditTripForm()` với xe; `contains()` theo id nhờ
  Hidden Cost #9); `getAllTrips()` xoá. **Là phạm vi MỜI, không phải luật**: `IncidentService.validate()`
  không đổi, POST gửi id chuyến cũ vẫn hợp lệ. Ghim bằng `TripServiceRunningAndRecentTripsTest` (5;
  non-vacuous: `>=`→`>` đỏ đúng test biên, vô hiệu vế live đỏ 2). Đo trên app thật (PID 5412): form tạo
  **27,7 KB / 0,32 s**, 99 option khớp **id-for-id** với SQL viết từ luật (4 ACTIVE + 5 DEPARTED +
  82 COMPLETED + 8 CANCELLED); sửa sự cố 9: 100 option, chuyến 1 (tháng 7) được thêm đầu và đang
  chọn, xe 17 giữ nguyên; sửa sự cố 14: chuyến 12 đang chọn; DB thật không đổi.
  *(Mục gốc:)* **`/admin/incidents/create` nặng 343 KB** vì dropdown chuyến nạp `getAllTrips()` — 2.071 dòng, tăng
  theo mỗi lần backfill. Cùng họ Hidden Cost #4 (trang danh sách chuyến 7,43 MB); ghi để không bị
  bỏ quên khi làm pagination.
- **`README.md:37` (gốc repo) ghi "Spring Boot 3.x"** trong khi `pom.xml` là **4.0.2** — phát hiện
  2026-09-21 khi quét doc cho mục security ở trên (chính lần nâng Boot 4 là nguồn của dòng exclude
  chết). Doc lạc hậu một phiên bản; cùng loại với các mục docs khác. Sửa: một từ. Chưa sửa vì ngoài
  phạm vi phiên này.
  *(Bổ sung 2026-09-23, tự rà: badge ở `README.md:4` cũng ghi `Spring%20Boot-3.x` — sửa cả hai chỗ.)*

## ĐÃ KIỂM ở lần rà 2026-09-19 — không phải lỗi, đừng nêu lại

- **Các file chat log `2026-*.txt` và `repomix-output.xml` ở gốc repo** — đã nằm trong `.gitignore`
  (`/20??-??-??-*.txt`, `repomix-output.xml`), `git ls-files` không có. Không phải rác tracked.
- **`spring.jpa.show-sql: false` dùng dấu hai chấm trong file `.properties`** — cú pháp
  `java.util.Properties` chấp nhận `=`, `:` và khoảng trắng làm dấu phân cách. Hợp lệ.
- **Sửa entity managed trong controller rồi service ném lỗi (vd `updateTrip()` đã `setBus()` trước khi
  `updateManualTrip()` từ chối) — có bị flush không?** Không: OSIV giữ `EntityManager` mở nhưng không
  có transaction ⇒ Hibernate không flush; rollback của service không đóng EM pre-bound nhưng cũng không
  ghi. Đã được các phiên #14/#15/#18 chứng minh bằng "trường bị đổi cố ý không bao giờ chạm DB". Giữ
  nguyên kết luận.
- **9/13 câu SQL bất biến ra 0** trên DB thật: vé > ghế; `DEPARTED` mà xe không `TRAVELING`; odometer
  < mốc bảo trì; giá `NULL`/≤ 0 hoặc ghế ≤ 0; phụ xe trùng tài xế chính; tài xế khoá / hết bằng vào
  ngày khởi hành trên chuyến chưa kết thúc; chuyến `ACTIVE`/`DEPARTED` thiếu xe hoặc tài xế; **0 cặp**
  chuyến chưa kết thúc trùng lịch trên cùng xe; **0 cặp** trùng lịch trên cùng tài xế chính. Xe 19
  `TRAVELING` không có chuyến giải thích: fixture đã biết (#19).
- **`Trip.getHoursUntilDeparture()` cắt phần lẻ (`toHours()`)** rồi so `< 72` — 72,9h đọc 72 (qua),
  71,9h đọc 71 (chặn). Biên đúng hướng bảo thủ, không lỗi.
- **Bucket lấp đầy ở `DashboardService.buildOccupancyStats()`** `<0,5 / [0,5;0,7) / [0,7;0,9] / >0,9`
  — phủ kín, không hở, biên trên `> 0.9` khớp `needsReinforcement()`.

---

# Đối soát §12 + §4 của `Proj_functions_summary.md` với code (2026-09-22) — 4 câu SAI, 1 câu hỏi tồn, 1 câu đúng nửa; CHỈ SỬA DOCS

Chủ dự án yêu cầu kiểm ba chỗ bị nghi *"tận gốc rễ, theo code ko chỉ đọc docs"* rồi sửa cho nhất
quán. Mọi claim được dựng lại từ source bằng `grep`/đọc file, kèm `git log -S` để **định ngày** —
không claim nào dựa vào một tài liệu khác. Không có dòng Java/template/config/test nào bị đụng, nên
không có gì để drive; DB thật không được mở.

## Nguyên nhân gốc: §12 chưa bao giờ là ảnh chụp của code mà nó nhận là đang tóm tắt

Tiêu đề §12 ghi *"tổng hợp từ code"*, nhưng danh sách được mang nguyên từ một bản phân tích CŨ và
commit mà không đối soát lại. Bằng chứng theo ngày:

| Mục | Được sửa ở code | File docs vào repo | Chênh |
|---|---|---|---|
| 3 (giờ lái phụ xe) | `a06b73a` 2026-07-14 **08:30** | `07af949` 2026-07-14 **09:52** | sai trước **1h21'** |
| 6 (Route String) | `a3b0e63` **2026-06-27** | `07af949` **2026-07-14** | sai trước **17 ngày** |
| 1 (2 luồng tạo Bus) | — chưa bao giờ đúng | — | — |

Đây là lần thứ ba project bắt đúng hình dạng này (xem `project_report.md` 🟣 #3: *"đã cũ nửa câu
ngay từ lúc được đưa vào `docs/`"*, và THESIS_ROADMAP §9 về "câu sai trong tài liệu là loại lỗi đắt
nhất"). **Bài học đã ghi vào §12:** một danh sách "điểm chưa nhất quán" phải được đối soát với code
**tại thời điểm commit nó**, không phải tại thời điểm viết nó.

## Bốn câu SAI, đã sửa

- **§12 mục 1 — "2 luồng tạo Bus song song".** `AdminController` có đúng 3 mapping (`/dashboard`,
  `/users/new`, `/users/save`); `AdminService` có đúng 2 method (`createNewUser`,
  `requireUsernameAvailable`) — 0 dòng về `Bus`. Mọi lối ghi `buses`: `BusService:73/147/210` (UI),
  `DataInitializer:311` + `HistoricalDataBackfill:274` (seeder), `TripService:660/672` (FSM đồng bộ
  `BusStatus`). `AdminController` **có** inject `BusRepository` nhưng chỉ `count()` cho thẻ dashboard
  (`:35`) — đó là Warn #4, không phải luồng tạo xe thứ hai. **§3 của chính file này đã đính chính
  đúng câu đó ngày 2026-08-05** (ghi trong §8 roadmap) mà bản sao ở §12 bị bỏ sót ⇒ một file tự nói
  ngược nhau suốt **48 ngày**.
- **§12 mục 3 — giờ lái áp sai cho phụ xe.** Đã sửa ở `a06b73a`: `findBestAvailableDriver()` nhận
  `boolean isAssistantRole` (`TripService:443/455`), trần 8h bị bỏ qua cho vai trò đó (`:478`),
  `autoAssignResources()` truyền `true` đúng một lần cho phụ xe (`:271-272`). Khớp
  `validateStaffForTrip()` — cũng không áp trần giờ cho phụ xe ⇒ một định nghĩa duy nhất.
  `project_report.md` 🟣 #1 đã ghi "✅ ĐÃ FIX" từ đầu ⇒ hai register nói ngược nhau từ ngày đầu.
- **§12 mục 6 — "Route dùng String tự do".** Đã xóa ở `a3b0e63`; `Route.java:31-32` chỉ còn comment
  `// ĐÃ XÓA:`. Grep `src/`: **0** tham chiếu field cũ, 6 template dùng hai getter hiển thị. Vế "seed
  2 bên tên không khớp" cũng hết hiệu lực (cùng commit đó seed `RouteStation`).
- **§12 mục 9 — "Spring Security tắt hoàn toàn".** Auto-config security đang **BẬT**; permit-all đến
  từ `SecurityConfig`. Đây là **bản sao thứ tư** của đúng câu sai mà Group B(b) đã sửa ngày
  2026-09-21 ở `setup_guide.md` §5/§7, `02_project_context.md` §3 và cả hai `application.properties`.
  **Vì sao sót:** lần quét tự-kiểm của Group B grep theo **tên property** đã đổi (`spring.security`,
  `autoconfigure.exclude`), không grep theo **câu văn** — nên bản prose ở file thứ tư lọt. Vế nội
  dung của mục thì đúng và được giữ, nay có số đo: grep `src/main` cho **0** `@PreAuthorize`/
  `hasRole`/`hasAuthority`, **0** `SecurityContextHolder`, **0** bean `PasswordEncoder`, và **không
  có `UserDetailsService` của riêng project** (tên đó xuất hiện 1 lần, ở vế `exclude` tại
  `BusManagementApplication:24`); `Role` chỉ phân loại bản ghi (`AdminService:44`, `DriverService:77`,
  `AdminController:48-49`).
- **§4 ghi chú Station — sai cả ba vế.** Câu cũ: `DataInitializer` *"(tùy version) không seed
  `RouteStation`"*, *"`route_stations` có thể trống"*, *"route vẫn dùng String tự do"*. Thực tế:
  `DataInitializer.createRoute():237` gọi `linkRouteStation()` hai lần mỗi tuyến (`:244-245`), cả 6
  tuyến seed đều qua đó; lối ghi `routes` còn lại là `RouteService.createRoute()`/`updateRoute()`,
  bị `validateRoute()` chặn dưới 2 trạm ⇒ **không đường nào tạo được tuyến không có trạm**.

## Bốn mục ĐÚNG và vẫn mở — nay tự khai trạng thái

Mỗi mục nay ghi rõ "ĐÚNG, CÒN MỞ" kèm file:line, nên §12 không còn đọc như một backlog không phân
loại. **Chỉ 4 mục này là việc còn nợ:**

- **mục 2** — chuyến thủ công vào thẳng `ACTIVE` (`AdminTripManagementController:161`) = Warn #2,
  chờ quyết định của chủ dự án.
- ✅ **mục 4 — ĐÃ SỬA 2026-09-23 cùng Group C(a)** (lời hứa đã xoá, không cài luật). *(Gốc:)* javadoc `Route.getSuitableBusType()` hứa "tự gợi ý dựa trên quãng đường", thân hàm
  `return null` (`Route.java:53-66`). Cùng loại lỗi mà §9 roadmap xếp đắt nhất, nhưng nằm trong
  javadoc. **Sửa là xóa lời hứa**, KHÔNG phải cài luật gợi ý loại xe — luật đó là Group C(a), chưa
  ai ruling.
- **mục 7** — `countBusyTripsAnyRole`/`findAllTripsByDriverOnDate` có **0** call site
  (`TripRepository:207/222`) = Incon #3.
- **mục 8** — mật khẩu plaintext (`AdminService:51` là comment, `DriverService:118`,
  `DataInitializer:284`), không bean `PasswordEncoder` nào.

## Hai chỉnh nhỏ cùng danh sách

- **mục 5** vốn không phải claim mà là một câu hỏi tự đặt (*"cần verify lại trong source thật"*) —
  **đã trả lời:** `BusType` chỉ còn `id`/`typeName`/`capacity` (`BusType.java:18/21/24`),
  `suitableBusType` chỉ nằm ở `Route:51`, đúng chủ sở hữu của nó.
- **mục 11** vế đầu đúng, nhưng vế cuối *"chỉ phần Admin Smart Scheduling được triển khai đầy đủ"*
  đã sai từ Phase 1 (CRUD Tài xế/Tuyến, Bảng Điều Hành, Sự Cố, API dry-run, backfill, 5 màn Decision
  Support) — §13 của chính file đã liệt kê đủ từ 2026-07-22. Và phần lớn danh sách còn thiếu nay là
  **Non-Goal** tường minh ở roadmap §4, không phải nợ.

## Hình thức sửa

Không xóa gì. Câu sai bị gạch ngang, sự thật ghi kèm file:line, và một parenthetical in nghiêng ghi
lại câu cũ + thời điểm nó thành sai + vì sao nó sống sót — theo Rule 7 và đúng tiền lệ §3 của chính
file đó. Đầu §12 thêm một blockquote có ngày, nêu phương pháp, phán quyết từng mục và bài học — theo
tiền lệ blockquote đối soát §13 ngày 2026-07-22.

**Một phép đo của chính phiên này bị sai và được ghi lại thay vì lấp:** lần kiểm line-ending đầu báo
ba file docs là CRLF — do quoting `$'\r$'` dưới Git Bash làm `grep` khớp mọi dòng. Đo lại theo byte:
cả ba là **LF thuần, 0 CRLF**, và `Proj_functions_summary.md` không có newline cuối — trong `HEAD`
cũng vậy, nên các edit giữ đúng quy ước sẵn có của file.

**`project_report.md` không cần sửa gì** — nó đúng ở cả bốn điểm trên (🟣 #1, Incon #1, Warn #2,
Incon #3), và chính sự đúng đó là thứ làm lộ ra các mâu thuẫn.

---

# Group C(d) ĐÃ SỬA (2026-09-23) — cửa sau của luật #22: đổi loại xe sang loại nhỏ hơn

Chủ dự án chốt **CHẶN**. Trước khi sửa đã rà ảnh hưởng, và việc rà đó đổi hình dạng bản sửa
(xem mục "vì sao so với sức chứa cũ" bên dưới) — nên phần phân tích đáng giữ hơn phần code.

## Lỗi là gì

Luật #22 (*"`totalSeats` của chuyến ≤ sức chứa xe"*) sống trong `validateBusForTrip()`, nên nó
CHỈ được kiểm lúc **gán xe vào chuyến**. `BusService.updateBus()` chép thẳng `busType`:

```java
existing.setBusType(form.getBusType());   // không một dòng kiểm tra
```

Nên vào *Sửa xe*, hạ một xe Ghế ngồi (50 chỗ) xuống Limousine (22 chỗ) là **báo THÀNH CÔNG** và
mọi chuyến đang mở của xe đó lập tức bán nhiều ghế hơn số chỗ thật — đi vòng qua #22 bằng cửa sau.
Trong cùng method đã có guard anh em cho `REPAIRING` (#15) nhưng nó chỉ canh `status`, không canh
`busType`.

**Bán kính đo trên DB thật 2026-09-23:** 4 xe (6, 7, 14, 15) đang bán **đúng bằng** sức chứa, nên
hạ một bậc là vỡ ngay; 0 xe không gán loại đang giữ chuyến mở.

## Vì sao KHÔNG viết guard theo cách hiển nhiên — phát hiện làm đổi bản sửa

Cách hiển nhiên là *"chặn khi sức chứa mới < số ghế đang mở bán"*. **Sai**, vì có đúng **1 xe**
đã vi phạm #22 SẴN từ trước bản vá đó:

| Xe | Sức chứa | Chuyến mở | Ghi chú |
|---|---|---|---|
| 20 `51B-MOI.00` | 22 (Limousine) | chuyến 6 `DEPARTED` bán **40** ghế | dữ liệu lịch sử, đã chốt để nguyên |

Với guard hiển nhiên, **mọi** lần lưu xe 20 đều bị từ chối — kể cả khi không đổi loại xe — nên
Admin không sửa nổi cả odometer của nó. Đó đúng là cái bẫy mà #22 đã gài cho **chuyến 8** và phải
sửa tay: một luật mới chặn luôn dữ liệu cũ hợp lệ-theo-luật-cũ.

Nên luật được phát biểu là **"KHÔNG ĐƯỢC LÀM TỆ HƠN"**: chỉ chặn khi loại mới **nhỏ hơn loại đang
có**. Giữ nguyên loại → luôn qua. Nâng lên loại lớn hơn → luôn qua, và đó chính là đường Admin tự
khắc phục một vi phạm tồn đọng. Xe chưa gán loại → bỏ qua, đúng cách `validateBusForTrip()` bỏ qua
#22 khi xe chưa có loại.

## Bản sửa

- `TripRepository.findMaxTotalSeatsForBus(busId, statuses)` — mới, trả `MAX(t.totalSeats)`, **null**
  khi không có chuyến khớp (không dùng 0 vì 0 sẽ bị đọc thành "có chuyến bán 0 ghế"). Chỉ một giá
  trị vô hướng, không nạp entity (Hidden Cost #4). Là cặp đôi của `existsByBusIdAndStatusIn()` ngay
  trên nó: cùng câu hỏi, khác chỗ cần CON SỐ thay vì có/không.
- `BusService.UNFINISHED_TRIP_STATUSES` — gom `{PENDING_APPROVAL, ACTIVE, DEPARTED}` thành hằng số
  vì giờ có HAI guard trong cùng một method hỏi cùng câu đó. Khuôn `DriverService.BUSY_STATUSES`,
  vốn đã ghi chú "không định nghĩa lại khái niệm này ở tầng khác". Guard `REPAIRING` của #15 dùng
  lại hằng số này — hành vi không đổi, `Arrays` import thành thừa nên đã xoá.
- Guard mới đặt **trước mọi setter**, cùng lý do #14/#20: chặn muộn thì một request bị từ chối vẫn
  đã kịp ghi các field khác. Ném `RuntimeException` như guard anh em ngay trên, để
  `AdminBusController` hiện flash `"Lỗi: …"` sẵn có — không sửa controller.
- Thông báo nêu đủ thứ Admin cần để hành động: biển số, loại định đổi + sức chứa, số ghế đang mở
  bán, và ngưỡng tối thiểu phải chọn.

## Kiểm chứng

`BusServiceTest` **12 → 18 test**, `mvnw clean test` **114/114** (16 class). Sáu test mới gồm
1 test chặn + 5 counterweight cho phép, theo đúng khuôn cặp test của #16/#6 (bản sửa không được đi
quá tay thành "chặn mọi lần đổi loại xe").

**Non-vacuous hai chiều** — quan trọng vì luật có hai nửa:

| Probe | Kết quả |
|---|---|
| Vô hiệu guard (`false &&`) | đỏ **đúng 1** test: `update_toSmallerTypeIsBlockedWhenAnOpenTripSellsMoreSeats`, đỏ kiểu **Failure** (update đã *thành công*), không phải Error dựng fixture |
| Bỏ điều kiện "không làm tệ hơn" | đỏ **đúng 1** test: `update_keepingTheSameTypeIsAllowedEvenOnABusThatAlreadyOversells` — tức điều kiện đó load-bearing, không phải phòng xa |

**Drive app thật** trên clone `mysqldump` của DB thật (JVM thứ hai, port 8098; `processlist` xác
nhận 10 kết nối vào `busmanagement_test`, **0** vào `busmanagement`):

- Xe 6 (Ghế ngồi 50, chuyến 13 bán 50) → Limousine: **chặn**, flash đúng câu, và `brand` bị đổi cố
  ý trong cùng request **không lọt** ⇒ guard chặn trước setter.
- Xe 20 giữ nguyên loại + sửa odometer 18.940 → 19.000: **thành công** (ca chống regression).
- Xe 20 nâng lên Giường nằm (40): **thành công** (đường tự khắc phục).
- Xe 5 (0 chuyến mở) hạ xuống Limousine: **thành công**.
- **Regression guard cũ:** #15 vẫn chặn xe 6 sang `REPAIRING`; #16 ô odometer để trống vẫn GIỮ
  NGUYÊN 300 (không về 0).
- **32/32 trang admin + REST API = 200**, log sandbox **0** ERROR, 0 exception template/SpEL/lazy.
- **DB thật byte-identical** trước/sau (`md5_buses=cfb878…`, trips=2222).

## Còn mở sau mục này

Group C còn **(a)** loại xe là luật hay gợi ý, **(c)** sửa `Route.distanceKm` khi tuyến có chuyến
`DEPARTED`. C(b) đã ruling 2026-09-21. Mục này KHÔNG chạm tới cả hai.

*(Cập nhật 2026-09-23, phiên sau: (a) và (c) đã ruling và sửa — xem khối cuối file. Group C đóng.)*

---

# Mục nhỏ (2026-09-23) — giới hạn thiết kế, ghi để không bị quên; KHÔNG sửa code

- **Chuyến chỉ biết MỘT chiếc xe — đổi xe giữa đường thì odometer và trạng thái xe đi theo xe CŨ.**
  Phát sinh khi giải thích lại ruling C(b) cho chủ dự án (ví dụ: chuyến HN→HP đi xe 14, xe 14 nổ lốp,
  khách sang xe 7, xe 7 hỏng điều hoà). **Phía sự cố ghi đúng và đủ:** hai sự cố (`bus = 14` và
  `bus = 7`) đều gắn được cùng `trip = HN→HP`, vì C(b) để hai ô độc lập và chuyến `DEPARTED` luôn nằm
  trong phạm vi mời của form sự cố. **Phía chuyến thì lệch với thực tế:** `trip.bus` vẫn là 14 (bị khoá
  sau khi xuất phát — #18), nên:
  - lúc `COMPLETED`, `updateTripStatus()` (`TripService:661-669`) cộng **toàn bộ** `route.distanceKm`
    vào odometer **xe 14** và đặt xe 14 về `READY`; xe 7 không được cộng km nào dù chạy nửa sau;
  - xe 7 không bao giờ được đánh `TRAVELING` cho chuyến đó;
  - thông tin "xe 7 đã chở khách của chuyến này" chỉ suy ra được qua các sự cố gắn với chuyến.

  **Không phải lỗi, không do C(b) gây ra:** `Trip` có đúng một cột `bus_id`, FSM không có khái niệm đổi
  xe giữa đường — cùng giới hạn đã ghi ở #15 (*"hệ thống vốn không mô hình hoá sự cố giữa đường; thêm
  nó là tính năng mới"*). Mô hình hoá đúng cần chia chuyến thành chặng / bảng lịch sử xe theo chuyến =
  thay đổi schema, trái §3 roadmap khi Phase 9 chưa làm. **Ghi để:** (1) giải thích khi bảo vệ luận
  văn; (2) chủ dự án biết odometer của cả hai xe sẽ lệch trong ca này (có thể sửa tay ở màn Sửa xe —
  #16 cho phép). Chưa ruling, chưa có ai đề nghị sửa.

---

# Group C(a) và C(c) ĐÃ SỬA (2026-09-23) — theo đúng hai phương án đã bàn; Group C đóng

Chủ dự án: *"còn các group C bạn hãy sửa theo như đã bàn"* — tức hai khuyến nghị của phiên 2026-09-22
(C(a) = **gợi ý**, C(c) = **cảnh báo, không chặn**). Chưa commit, chờ chủ dự án test.

## C(a) — loại xe của tuyến là GỢI Ý

**Trước:** 4 nơi chọn xe hiểu `suitableBusType` theo 2 cách — AI (`findBestAvailableBus`) và dropdown
Duyệt/Sửa (`getAvailableBusesForTrip`) lọc cứng; API form Tạo và `validateBusForTrip()` không xét. Hệ
quả thấy được: chuyến 3492 (tuyến 1 Limousine) **tạo được** bằng xe 9 Ghế ngồi, nhưng form **Sửa**
chỉ mời Limousine.

**Sửa:**
- `TripService.getAvailableBusesForTrip()` — bỏ `findByStatusAndBusType`, lấy mọi xe `READY` qua đúng
  ba bộ lọc cũ (bận / quá hạn / sắp hạn bảo trì), xếp **xe đúng loại tuyến trước** rồi mới theo km từ
  bảo trì. Helper `isOfType()` so theo id.
- `approve-form.html` và `trip-edit-form.html` — option ghi sức chứa và `★ đúng loại tuyến`, thêm dòng
  gợi ý dưới dropdown.
- Form Tạo — `<option>` tuyến nay có `data-type` (comment của template đã hứa thuộc tính này từ trước
  mà `th:attr` chưa từng render — một câu hứa sai nữa); `TripRestController` trả thêm `typeId`; JS
  xếp đúng loại lên đầu (sort ổn định, giữ thứ tự km trong mỗi nhóm) và đánh ★. **Không lọc.**
- `Route.getSuitableBusType()` — javadoc hứa "tự gợi ý dựa trên quãng đường" đã xoá (§12 mục 4), thân
  hàm rút về `return this.suitableBusType`.
- **Không đổi:** `validateBusForTrip()` (gợi ý ⇒ không có luật); **`findBestAvailableBus()` cố ý vẫn
  lọc cứng** — đường AI không có người xem lại lựa chọn lúc chọn, và mời hẹp hơn validator là được
  phép theo bất biến một chiều của #12; thêm comment trỏ về quyết định này. Nếu chủ dự án muốn AI
  cũng coi là gợi ý (đúng loại trước, hết thì lấy loại khác) thì đó là một ruling riêng: nó đổi
  đầu ra của chuyến tăng cường và màn Đề xuất tăng cường (hôm nay 17/17 RECOMMENDED nên số hiển thị
  không đổi, nhưng hành vi khi thiếu xe đúng loại sẽ đổi).

**Không nới luật nào:** rủi ro thật của xe sai loại — bán quá số chỗ — vẫn do #22 chặn. Đã kiểm.

## C(c) — sửa km của tuyến có chuyến đang chạy: CẢNH BÁO

- `TripRepository.countByRouteIdAndStatus(routeId, status)` (derived query, chỉ đếm).
- `RouteService.updateRoute()` nay **trả `String`** (null = không có gì cần báo), đúng kênh "warning"
  của `TripService.createManualTrip()/updateManualTrip()`. Đọc km cũ **trước** khi chép field; chỉ
  cảnh báo khi km **thật sự đổi** và tuyến có ≥ 1 chuyến **`DEPARTED`** — `ACTIVE` chưa lăn bánh nên
  chạy theo km mới là đúng, `COMPLETED` đã được cộng xong. Lưu vẫn diễn ra như cũ.
- `AdminRouteController` đưa câu đó ra flash `warning`; `route-list.html` thêm khối
  `alert-warning` (trước chỉ có success/error).

## Kiểm chứng

- `mvnw test` **121/121, 18 class** (+7: `TripServiceBusTypePreferenceTest` 3,
  `RouteServiceDistanceWarningTest` 4). **Non-vacuous, 4 probe, mỗi probe đỏ đúng 1 test rồi khôi
  phục md5:** bỏ xếp-đúng-loại-trước → đỏ test thứ tự; khôi phục lọc cứng → đỏ cùng test đó; tắt
  cảnh báo → đỏ test cảnh báo; đếm mọi trạng thái thay vì chỉ `DEPARTED` → đỏ test đối trọng.
- **Drive app trên clone `mysqldump`** (JVM thứ hai port 8098, PID 16488; processlist: 10 kết nối
  vào clone, 0 của JVM này vào DB thật):
  - Tuyến 1 (2 chuyến `DEPARTED`) 120 → 500 km: *success* + *warning* "đang có 2 chuyến trên đường…
    (500 km, trước đây 120 km)…"; giữ 500 km, đổi thời lượng: chỉ success; về 120: có warning; tuyến
    6 (chỉ có `ACTIVE`) đổi km: chỉ success. DB clone ghi đúng từng lần.
  - Sửa chuyến 3492: dropdown = 6 Limousine ★ (23, 10, 11, 12, 1, 2) rồi 8, 9, 5, 4, 3 — **khớp từng
    xe và thứ tự** với SQL viết từ luật (xe 16 sắp hạn, 17 quá hạn bị loại). Đổi sang xe 2 giữ 50 ghế
    → từ chối bởi #22, không ghi gì; xe 2 + 22 ghế → thành công; xe 8 (Ghế ngồi, trước đây không được
    mời) + 50 ghế → thành công; trả về xe 9.
  - Màn Duyệt MANUAL MODE (trên clone gỡ xe của chuyến 8): 11 xe, Limousine ★ trước, dòng gợi ý "đủ
    chỗ cho 22 ghế"; duyệt bằng xe 5 (Ghế ngồi) → `ACTIVE`.
  - Form Tạo: 8/8 tuyến render `data-type`, tuyến không có loại thì thuộc tính bị bỏ; API trả
    `typeId`. Logic JS chạy bằng node trên đúng JSON của API: đúng loại lên đầu, giữ thứ tự km, tuyến
    không loại giữ nguyên. **Chưa bấm trên trình duyệt thật.**
  - 31/32 trang admin + REST 200 (`approve/8` 302 vì chuyến 8 trên clone vừa được duyệt — đúng #20),
    log 0 ERROR/exception template.
- **DB thật byte-identical** trước/sau (`trips` 2224, `buses`, `routes`, `route_stations` md5 không đổi).

## Chạm tới / không chạm tới

Code: `TripService`, `TripRepository`, `RouteService`, `AdminRouteController`, `TripRestController`,
`Route`; template `approve-form`, `trip-edit-form`, `trip-create-form`, `route-list`; 2 test mới.
Docs: `current_functional_spec.md`, `Proj_functions_summary.md`, `database_schema.md`,
`test_case.md` (`TC_APR_014`), file này, roadmap. **Không** đổi schema, validator, FSM, AI.

## Việc liên quan còn mở (ghi, chưa sửa)

- **Form Sửa chuyến không đồng bộ số ghế với xe** (ca chuyến 3492 chủ dự án gặp): ô "Tổng số ghế" là
  input tự do, không có JS phản ứng khi đổi xe — trong khi form Tạo khoá ô đó và tự điền theo sức
  chứa. Luật #22 chặn đúng; chỉ là form không nhắc. Bản sửa C(a) giảm hiểu nhầm bằng cách in sức
  chứa trong từng option, nhưng không tự đổi số ghế. Chưa ruling.
- **AI có nên coi loại xe là gợi ý** (xem trên). Chưa ruling.

---

# Tự rà toàn dự án sau khi đóng Group C (2026-09-23) — một lỗi #26, ba mục nhỏ; CHƯA SỬA

Chủ dự án: *"bạn hãy tự kiểm tra lại toàn bộ project đi"*, rồi *"hãy ghi nó đi"*. Phạm vi rà: đọc lại
từng dòng diff chưa commit (C(d)/C(a)/C(c)), quét docs + comment tìm câu mô tả hành vi cũ, `mvnw clean
test` 121/121, app thật trên DB thật **chỉ GET** (38/38 trang 200, log 0 ERROR, DB byte-identical),
và 20 câu SQL bất biến chỉ đọc (15 ra 0; 5 còn lại đều là mục đã biết: xe 19 fixture #19, chuyến 6
lịch sử #22, các chuyến quá giờ ở nhóm "quá giờ" của Bảng Điều Hành, sự cố 9 và 14 báo trước giờ
khởi hành — cả hai là dữ liệu thử ngày 16–17/07, không có luật nào về thời điểm này). Bốn mục dưới
đây là **mới**; đã grep cả register này lẫn `project_report.md` trước khi ghi.

## 26. Màn Duyệt (MANUAL MODE) đánh ★ lên đầu cho những xe mà luật #22 CHẮC CHẮN từ chối

**Do chính bản sửa C(a) (`4def1ad`) làm lộ ra**, không phải lỗi có từ trước theo đúng hình dạng này.

- Ở màn Duyệt, **số ghế của chuyến cố định** — form chỉ chọn xe/tài xế, không có ô `totalSeats`.
- `getAvailableBusesForTrip()` (dùng chung cho màn Duyệt và form Sửa) **không lọc theo sức chứa**.
- C(a) xếp xe đúng loại tuyến lên đầu và gắn `★ đúng loại tuyến`, kèm dòng gợi ý *"xe đúng loại
  được đánh ★ … Chọn loại khác vẫn hợp lệ nếu xe đủ chỗ cho N ghế"*.

**Kịch bản với được:** chuyến gốc chạy xe khác loại và bán nhiều ghế hơn sức chứa của loại tuyến
(chuyến 3493: tuyến 1 Limousine 22 chỗ, xe 8 Ghế ngồi, 26 ghế). Chuyến đó đông ⇒ AI tạo chuyến tăng
cường; `createExtraTrip()` giữ **26 ghế** làm chỗ trống khi `autoAssignResources()` không tìm được xe
— mà AI chỉ tìm **Limousine** (`findBestAvailableBus` cố ý lọc cứng). Admin mở màn Duyệt: các xe
Limousine 22 chỗ nằm **trên cùng, có ★**; chọn xe nào trong số đó cũng bị từ chối *"Chuyến mở bán 26
ghế nhưng xe … chỉ có 22 chỗ"*. Đúng anti-pattern "mời thứ sẽ từ chối" mà dự án đã bác nhiều lần
(#19 bị revert vì nó, #20, #23, #24).

**So với trước C(a):** trong kịch bản này, bản cũ lọc cứng theo loại nên **mọi** option đều bị từ
chối — tệ hơn. C(a) đã thêm các xe đủ chỗ vào danh sách; lỗi còn lại là ★ và thứ tự chỉ nhầm hướng.

**Bán kính hôm nay: 0** — DB thật không có chuyến `PENDING_APPROVAL` nào thiếu xe (MANUAL MODE chỉ
mở khi `bus == null || driver == null`). Tiềm ẩn, với được bằng một lần AI thiếu xe đúng loại.

**Đề xuất sửa (chưa làm):** ở màn Duyệt, bỏ khỏi dropdown những xe có `capacity < trip.totalSeats`
— luật #22 đã có, không bịa luật mới. Nên đặt ở tầng service (một biến thể/tham số của
`getAvailableBusesForTrip` cho màn Duyệt) chứ không lọc trong template; form Sửa **không** dùng lọc
này vì ở đó số ghế sửa được cùng lúc (xem mục nhỏ ngay dưới). Kèm test + sửa dòng gợi ý của
`approve-form.html`.

## Mục nhỏ (2026-09-23, tự rà)

- **Form Sửa chuyến mời cả xe có sức chứa < số vé đã bán.** Ở form Sửa, số ghế sửa được nên mời xe
  nhỏ hơn `totalSeats` là chấp nhận được — Admin hạ số ghế cùng lúc. Nhưng xe có `capacity <
  ticketsSold` thì **không bao giờ** qua: `updateManualTrip()` đòi `totalSeats ≥ ticketsSold`
  (`TripService:1037`) và #22 đòi `totalSeats ≤ capacity`, hai điều không thể cùng đúng. Bán kính hôm
  nay **0** (chuyến mở bán nhiều vé nhất là 3492 với 21 vé, loại nhỏ nhất 22 chỗ). Cùng họ với mục
  *"form Sửa không đồng bộ số ghế với xe"* ở khối Group C(a)/C(c) phía trên — nên xử lý cùng một lần.
- **Dấu ★ ở form Sửa tính theo tuyến ĐÃ LƯU.** Đổi tuyến trong form thì ★ và cả danh sách xe không đổi
  theo cho tới khi lưu. Danh sách xe vốn đã tính theo tuyến đã lưu từ trước (`showEditTripForm()` gọi
  `getAvailableBusesForTrip(id)`); C(a) chỉ thêm ★ theo đúng cách đó. Nhỏ; ghi để biết.
- **`README.md` còn "3.x" ở cả dòng 4 (badge)** — mục `README.md:37` phía trên chỉ ghi dòng 37. Xem
  ghi chú bổ sung ở mục đó.
