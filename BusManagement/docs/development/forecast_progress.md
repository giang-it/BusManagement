# Tiến độ thiết kế lại Demand Forecast

> **File theo dõi qua nhiều phiên.** Cuối mỗi phiên làm việc phải cập nhật: mục 1 (trạng thái), mục 2 (bảng bước), mục 3 (đánh dấu việc xong), và thêm một dòng vào mục 4 (nhật ký).
> - Quyết định đã chốt: [`forecast_decisions.md`](forecast_decisions.md).
> - Thảo luận và số đo: [`forecast_discussion.md`](forecast_discussion.md).
> - `THESIS_ROADMAP.md` vẫn là tracker chung của cả dự án. §7 của nó trỏ về file này; §8 ghi một mục ngắn cho mỗi phiên.

## Bắt đầu một phiên mới: đọc theo thứ tự

1. `THESIS_ROADMAP.md`: §7 và §10.
2. File này: mục 1–3.
3. `forecast_decisions.md`: mục 1 và mục 4. Mục 4 là những chỗ chưa chốt.
4. Chỉ khi cần lý do hay số đo: `forecast_discussion.md` mục 11–12.

---

## 1. Trạng thái hiện tại (cập nhật 2026-10-22)

- **Đã chốt** QĐ-1…QĐ-14 (`forecast_decisions.md` mục 1). QĐ-8, QĐ-9 là thiết kế của GĐ1; QĐ-10…QĐ-13 của GĐ2; QĐ-14 của GĐ3.
- **Đã chia** bước 1–4 thành 7 giai đoạn (mục 2–3 dưới đây). Mỗi giai đoạn xong thì app vẫn chạy được.
- **GĐ1 xong và đã commit ngày 2026-10-21, chưa push.**
  - Code: `79ba96c`. Tài liệu: commit `docs:` ngay sau đó.
  - Chủ dự án đã chạy app trên DB thật, gán nhãn 8 tuyến và tự test (*"test xong rồi, commit đi"*).
  - Chi tiết và bằng chứng ở mục 3.
- **GĐ2 xong ngày 2026-10-21, đã commit ngày 2026-10-22, chưa push** (QĐ-10…QĐ-13; chi tiết ở mục 3):
  - Code: `01560cc` (bộ sinh mới) và `6a68937` (màn danh sách chuyến). Tài liệu: commit `docs:` ngay sau đó.
  - bộ sinh mới, màn danh sách chuyến có cửa sổ ngày, 23 test mới;
  - tổng duyệt GĐ3 trên bản sao `busmanagement_gd2`;
  - đo lại bằng Python, kết quả ở `forecast_discussion.md` §13;
  - ghi lỗi #31 và #32;
  - M-15 đã chốt ngày 2026-10-22: quy tắc (b) vào QĐ-3, EM 5 vòng vào QĐ-1.
- **GĐ3 xong ngày 2026-10-22** (QĐ-14, G3-1…G3-7; chi tiết ở mục 3):
  - Code và tài liệu chuẩn bị: commit `09f4a4c` (sửa bộ sinh, lỗi #33), `71e3486` (bài học), `db111c9` (ghi chép GĐ3); chưa push.
  - **DB thật đã thay lịch sử:** 21.192 chuyến mô phỏng từ 01/01/2023 tới 21/10/2026, cộng 19 chuyến của chủ dự án. Giống hệt bản sao tổng duyệt tới từng chuyến.
  - Tài liệu ghi sau khi chạy: commit `docs:` ngay sau đó (2026-10-22), chưa push.
- **Việc tiếp theo:** thảo luận GĐ4 (lõi toán Java), bắt đầu ngày 2026-10-22.

  GĐ4 (lõi toán Java) làm song song được. M-15 đã chốt nên mô hình không còn chỗ treo; GĐ4 vẫn bắt đầu bằng một vòng thảo luận.
- **Code app:** đã đổi ở GĐ1, GĐ2 và GĐ3, đều đã commit. **DB thật:** đã mang dữ liệu của bộ sinh mới từ ngày 2026-10-22. Bản sao `busmanagement_gd2` giống DB thật, giữ tới hết GĐ5 để thử thao tác ghi.
- **Commit:** `2eebc25` (tài liệu 14/10 → 21/10) đã push lên `origin/temp`. Chưa push: GĐ1 (`79ba96c`, `fa50ac3`), GĐ2 (`01560cc`, `6a68937`, `d04352a`) và GĐ3 (`09f4a4c`, `71e3486`, `db111c9`). Lúc đầu, toàn bộ tài liệu dự báo từ 14/10 đến 21/10 đã gom thành commit docs `2eebc25` ngày 2026-10-21; dòng cập nhật này được viết sau commit, nên nó sẽ đi vào commit sau. Commit gồm `forecast_*.md`, `forecast_research/` và các thay đổi trong `THESIS_ROADMAP.md`.
  - Không đưa vào commit: transcript `forecast_research/2026-10-20-chưa xong.txt`. Transcript này để ngoài git như mọi transcript khác.
  - Không thuộc đợt này: bài học `docs/study/05` và `06`. Hai bài này đã commit riêng thành `5a5f30f` và `1dc429f`, và đã push, cùng ngày 2026-10-21.
- **Đang chờ chủ dự án trả lời:**
  - M-12: làm đợt này trước hay sau Phase 9.

  M-10 đã đóng ngày 2026-10-21 (ghi thành lỗi #31). M-15 và M-6 đóng ngày 2026-10-22.

---

## 2. Các giai đoạn

Trạng thái dùng các nhãn: `CHƯA BẮT ĐẦU` / `ĐÃ CHỐT THIẾT KẾ` / `ĐANG LÀM` / `XONG, CHƯA COMMIT` / `ĐÃ COMMIT`.

Thứ tự: GĐ1 → GĐ2 → GĐ3 → GĐ5 → GĐ6 → GĐ7. GĐ4 làm song song với GĐ2–3 và phải xong trước GĐ5.

| GĐ | Nội dung | Thuộc bước | Trạng thái | Ngày |
|---|---|---|---|---|
| 0 | Thí nghiệm chọn thuật toán (Python, ngoài app) | 0 | ĐÃ COMMIT | 2026-10-20 |
| — | Chốt QĐ-1…QĐ-7 | — | ĐÃ COMMIT | 2026-10-21 |
| KH | Đọc lại project; chia bước 1–4 thành 7 giai đoạn | — | ĐÃ COMMIT `fa50ac3` | 2026-10-21 |
| GĐ1 | Nền móng: `Route.holidayFlow` và lịch ngày lễ | 1 | ĐÃ COMMIT `79ba96c` (chưa push) | 2026-10-21 |
| GĐ2 | Bộ sinh mới, chạy trên bản sao DB; chạy lại thí nghiệm Python | 1 | ĐÃ COMMIT `01560cc`, `6a68937` (chưa push) | 2026-10-21 → 22 |
| GĐ3 | Thay dữ liệu trên DB thật | 1 | ĐÃ COMMIT `09f4a4c`, `71e3486`, `db111c9` và commit docs sau khi chạy (chưa push) | 2026-10-22 |
| GĐ4 | Lõi toán Java, unit test và đo tốc độ | 2 | CHƯA BẮT ĐẦU | |
| GĐ5 | `ForecastService` mới, cache và màn Dự báo | 2 | CHƯA BẮT ĐẦU | |
| GĐ6 | Recommendation và What-if theo nhu cầu | 3 | CHƯA BẮT ĐẦU | |
| GĐ7 | Tài liệu và chuẩn bị bảo vệ | 4 | CHƯA BẮT ĐẦU | |

---

## 3. Việc của từng giai đoạn

> Giai đoạn nào **chưa thảo luận** thì danh sách bên dưới chỉ là sơ bộ. Trước khi làm, phải có một vòng thảo luận như GĐ1: đối chiếu với project, phản biện, đưa lựa chọn, chủ dự án chốt. Mục nào cần chốt thì ghi mã M-… của `forecast_decisions.md` mục 4.
> Mỗi việc xong thì đánh `[x]`, ghi ngày và chỗ kiểm chứng (lệnh, số đo, file:dòng).

### Sự thật đã đo khi đọc lại project (2026-10-21; dùng cho các giai đoạn sau)
1. **Ngưỡng 0,90 vẫn chỉ có một định nghĩa.** Gọi d là nhu cầu dự báo, s là số ghế xe chuẩn của tuyến. Có max(1, ⌈d / (0,9·s)⌉) ≥ 2 khi và chỉ khi d/s > 0,90, đúng điều kiện của `Trip.needsReinforcement()`. Xe chuẩn lấy theo `Route.suitableBusType`: tuyến 1, 2, 5 là 22 ghế; tuyến 3, 4 là 40 ghế. Đây là câu trả lời cho M-4, chờ chốt ở GĐ6.
2. **Không dùng `@Scheduled` để tính dự báo mỗi ngày.** Roadmap §4 ghi *"No daily cron job … adding a scheduler is a separate owner decision"*. Cách thay thế: tính lần đầu khi có người mở trang trong ngày, rồi giữ trong bộ nhớ (GĐ5, M-5).
3. **Bộ sinh thiếu xe dịp Tết.** Bộ sinh hiện giữ "mỗi xe tối đa 1 chuyến/ngày": 15 chuyến/ngày trên 20 xe, và có 33 tài xế đang hoạt động (M-13).
4. **Xóa dữ liệu cũ làm odometer bị cộng hai lần.** Bộ sinh cũ đã cộng km vào cả `odometer` lẫn `lastMaintenanceOdometer`. Khi xóa phải trừ km của các chuyến COMPLETED bị xóa khỏi cả hai cột. Tổng odometer hiện là 356.245 km; tổng km kể từ lần bảo dưỡng cuối là 18.355 km, và số này phải giữ nguyên (GĐ3).
5. **Kết nối JDBC đặt `foreign_key_checks=0`** (`application.properties`). DB không chặn bản ghi mồ côi, nên trước khi xóa phải tự kiểm các tham chiếu: `trips.original_trip_id`, `incidents.trip_id`, `trip_co_drivers` (GĐ3).
6. **Nhiều chuyến tăng cường cho cùng một khung** thì các thẻ Recommendation sẽ chọn trùng xe (M-14, GĐ6).
7. **Hiệu năng.** Giải trực tiếp khoảng 1.400 ẩn bằng Java thuần có thể mất hàng chục giây. Ma trận có cấu trúc "mũi tên": mỗi tuyến chỉ dính tới cột của chính nó và cột lễ gộp chung. Giải theo khối (Schur complement) thì dự kiến dưới 1 giây. **Đây là giả thuyết, phải đo ở GĐ4.**
8. **`Incident.bus` là bắt buộc**, và DB hiện có 0 sự cố `ROAD_ISSUE`. Vì vậy dùng Incident để đánh dấu ngày bão là không tự nhiên (M-7, GĐ2).
9. **Phạm vi xóa đo được:** đúng **2.206** chuyến "dạng bộ sinh" (tuyến 1–5, giờ chẵn 06/12/18, `is_extra_trip = 0`, COMPLETED hoặc CANCELLED). 17 chuyến còn lại do chủ dự án tạo, kể cả 2 chuyến gắn sự cố #9 và #14; chúng nằm ngoài tập xóa. DB hiện có 0 chuyến trong tương lai và 2 chuyến đã xóa mềm.
10. **Bộ quét tăng cường thời gian thực không bị ảnh hưởng.** `TripService.scanAndSuggestExtraTrips()` vẫn xét ngưỡng 0,90 trên vé thật. Chuyến tăng cường của nó khởi hành +30 phút, cùng giờ với chuyến gốc, nên khi gom nhu cầu theo khung (tuyến, giờ, ngày) chúng tự rơi vào đúng khung.

### GĐ1: nền móng (thiết kế ở QĐ-8, QĐ-9). Chủ dự án duyệt danh sách việc ngày 2026-10-21 (*"hãy làm như đã chốt đi"*). Code xong cùng ngày, đã commit `79ba96c`
- [x] Enum `domain/HolidayFlow` gồm `LEAVING_HUB`, `TOWARD_HUB`. Trường `Route.holidayFlow` dùng `@Enumerated(EnumType.STRING)` và `@Column(name = "holiday_flow")`, cho phép null.
  - Hibernate tạo cột kiểu MySQL `ENUM('LEAVING_HUB','TOWARD_HUB')`, nullable. Đã thấy trên bản sao DB khi app khởi động.
- [x] `RouteService.updateRoute()` chép trường mới, javadoc ghi rõ null là ý định hợp lệ. `createRoute()` lưu nguyên object từ form nên không cần sửa, chỉ cập nhật javadoc.
- [x] `AdminRouteController` đưa `HolidayFlow.values()` vào form, theo khuôn `incidentTypes`.
  - `route-form.html`: ô chọn không bắt buộc ("-- Không theo chiều --" / "Rời TP lớn" / "Về TP lớn"), có dòng giải thích.
  - `route-list.html`: thêm cột "Dòng Khách Dịp Lễ".
- [x] `DataInitializer` (profile `demo`) gán nhãn cho 6 tuyến mẫu, đúng như các tuyến 1–6 trong bảng QĐ-8.
- [x] Lớp lịch lễ dùng chung: `domain/Holiday` (5 dịp, cửa sổ tạm theo bước 0) và `domain/HolidayCalendar`.
  - `covers()`: phạm vi 01/01/2023 → 31/12/2032.
  - `anchorOf()` và `holidaysOn()`: ngoài phạm vi thì ném `IllegalArgumentException`.
  - Bảng âm lịch 2022–2033, có hai năm biên (xem QĐ-9).
  - Nguồn đã đối chiếu: `forecast_research/holiday_calendar_check.py` và công cụ lịch âm Báo Hà Tĩnh; đủ 24 ngày, khớp hoàn toàn.
- [x] Test:
  - `HolidayCalendarTest`, 13 test JUnit thuần: phạm vi; từ chối ngoài phạm vi; các ngày của bước 0; Tết 2030 theo GMT+7; Tết luôn trong khoảng 21/1–20/2; Giỗ Tổ cách Tết đúng số ngày của 2 hoặc 3 tháng âm; hai đầu cửa sổ; hai dịp chồng nhau 26/4/2026; cửa sổ vắt qua năm (gồm cả 31/12/2032).
  - `RouteServiceHolidayFlowTest`, 4 test: tạo mới lưu nhãn; sửa chép nhãn; sửa về "Không theo chiều" thì xoá nhãn cũ; cột lưu đúng `LEAVING_HUB`.
  - **Đã thử bỏ dòng chép field:** đúng 2 test cập nhật báo đỏ, nên test không rỗng.
  - **Toàn bộ `mvnw test`: 143/143, 20 lớp, BUILD SUCCESS** (126 test cũ cộng 17 test mới).
- [x] Verify bằng app thật trên **bản sao** DB (cổng 8098; JVM PID 4924 khớp log; pool kết nối chỉ nằm trên `busmanagement_test`):
  - Màn Dự báo, Đề xuất, What-if, Dashboard và Analytics **giống hệt từng byte** so với mốc chụp từ code cũ, trên cùng dữ liệu, cùng ngày.
  - Danh sách tuyến chỉ thêm đúng một cột; 8 tuyến hiện "Không theo chiều".
  - Tạo tuyến với `LEAVING_HUB` → sửa thành `TOWARD_HUB` → sửa về trống thì DB là `NULL`. Form sửa chọn sẵn đúng giá trị đang lưu.
  - Gán nhãn cho tuyến 1 (451 chuyến) qua form: km, thời lượng, loại xe và lộ trình giữ nguyên.
  - Gửi giá trị lạ `holidayFlow=FOO` → HTTP 400, DB không đổi.
  - Xoá tuyến thử: thành công, `route_stations` của nó xoá theo.
  - 11 màn admin khác trả 200; không có lỗi template hay SpEL trong log.
  - **DB thật không đổi:** số dòng và checksum của 5 bảng giống hệt trước và sau; DB thật chưa có cột `holiday_flow`.
- [x] **Chủ dự án** chạy app trên DB thật. Kiểm bằng `SELECT`: cột `holiday_flow enum('LEAVING_HUB','TOWARD_HUB')` đã có và cho phép null; số chuyến vẫn 2.225.
- [x] **Chủ dự án** gán nhãn cho 8 tuyến. Kiểm bằng `SELECT`, khớp đúng bảng QĐ-8: tuyến 1, 2, 8 là `LEAVING_HUB`; tuyến 4, 5 là `TOWARD_HUB`; tuyến 3, 6, 9 để trống.
- [x] **Chủ dự án** tự test rồi bảo commit.
  - Trước khi commit đã chạy skill `verify`: chỉ GET trên app của chủ dự án (cổng 8080, DB thật).
  - Danh sách tuyến hiện 3 "Rời TP lớn", 2 "Về TP lớn", 3 "Không theo chiều"; form sửa tuyến 4 chọn sẵn "Về TP lớn"; Dự báo, Đề xuất, What-if và Dashboard trả 200.
  - Số dòng và checksum của DB thật giống hệt trước và sau lần kiểm.
  - Commit `79ba96c`.

### GĐ2: bộ sinh mới, chỉ chạy trên bản sao DB (thuộc bước 1). Chủ dự án duyệt tất cả ngày 2026-10-21 (QĐ-10…QĐ-13). Code xong cùng ngày; commit ngày 2026-10-22 thành `01560cc` và `6a68937`
- [x] **Bộ sinh mới (QĐ-10).**
  - `config/HistorySimulation` (mô phỏng) và `config/HistoricalDataBackfill` viết lại: từ 01/01/2023, tất định theo ngày, nhu cầu theo xe chuẩn, kỳ nghỉ sinh theo quy tắc.
  - Nhà xe quá khứ: 1 xe và 1 tài xế mỗi ngày; **bằng lái còn hạn**; chuyến thêm chỉ trong số xe rảnh. Bão là đóng tuyến; hủy ngẫu nhiên 6%. Xuất nhu cầu thật khi bật `-Dbackfill.truth-csv`.
  - `TripRepository.findDepartureTimesByRouteIdsBetween` thay `existsByRouteIdAndDepartureTime`.
- [x] **Màn danh sách chuyến (QĐ-12).** `TripService.getTripsForList` và `TripRepository.findForTripList` (đã xoá `findAllWithDetails`), sửa controller và `trip-list.html`.
- [x] **Test** (23 test mới):
  - `HistorySimulationTest` (7): chạy bổ sung không đổi ngày cũ; kỳ nghỉ đúng quy tắc; nhãn chiều đổi hình dạng Tết; nhị thức âm đúng trung bình và phương sai; mức tải theo xe chuẩn.
  - `HistoricalDataBackfillPlanTest` (6): kiểm **đủ 1.389 ngày** trên đội xe giống DB thật.
  - `HistoricalDataBackfillGenerateTest` (5): chạy trên DB test; chạy lại tạo 0 chuyến; chạy bổ sung; từ chối dữ liệu cũ; odometer.
  - `TripServiceTripListTest` (5).
  - **Đã thử làm hỏng 5 chỗ, test đều đỏ đúng chỗ:**
    1. cho xu hướng tính từ ngày cuối như bộ sinh cũ;
    2. bỏ bộ lọc bằng lái;
    3. bỏ chốt chặn dữ liệu cũ;
    4. đổi biên `>=` thành `>`;
    5. bỏ vế "chưa kết thúc".
- [x] **Tổng duyệt GĐ3 trên bản sao `busmanagement_gd2` (QĐ-11).**
  - Phần A (kiểm, chỉ đọc): đúng 2.206 chuyến cũ, 0 tham chiếu, 0 xe thiếu odometer. Km theo từng lần chạy khớp số đã ghi trong roadmap (ví dụ ngày 11/09 là 87.360 km).
  - Phần B (xoá): 2.225 → 19 chuyến; odometer giảm đúng 337.860 km; km kể từ lần bảo dưỡng cuối giữ nguyên 18.355.
  - Bộ sinh chạy khoảng 1 phút: 20.835 khung, **21.177 chuyến** (342 chuyến thêm, 1.360 chuyến hủy), **21,0% khung bán hết vé**.
- [x] **Kiểm bất biến bằng SQL trên bản sao:**
  - **0** lần một xe hay một tài xế chạy 2 chuyến/ngày;
  - **0** chuyến xếp cho tài xế hết hạn bằng;
  - **0** chuyến có vé vượt ghế hay số ghế khác sức chứa xe;
  - chuyến thêm đều trỏ đúng chuyến gốc, tối đa 5 chuyến/ngày;
  - odometer tăng đúng bằng km của các chuyến hoàn thành (3.249.960 km);
  - DB và nhu cầu thật khớp ở 19.475/19.475 khung.
- [x] **Chạy app trên bản sao** (cổng 8097, JVM PID 21892, pool kết nối chỉ nằm trên bản sao):
  - danh sách chuyến mặc định **1,76 MB** (462 chuyến); COMPLETED 90 ngày ra 1.274 chuyến;
  - 14 màn đều trả 200; dự báo cũ vẫn chạy trên dữ liệu mới (15 chuỗi, MAPE 22,9%, là trạng thái tạm tới GĐ5);
  - **DB thật không đổi** (số dòng và checksum giống hệt trước và sau).
- [x] **Ghi lỗi #31 và #32** vào `current_bugs_found.md` (đóng khi GĐ3 xong).
- [x] **Đo lại bằng Python (QĐ-13), ngày 2026-10-21.**
  - Dữ liệu: ghép số vé từ DB bản sao với nhu cầu thật; hai nguồn khớp 19.475/19.475 khung.
  - 72 thời điểm × 11 biến thể = 792 lần khớp, chạy trong 12 phút. Trên dữ liệu thật Hồng Kông, mô hình chính tái hiện đúng từng số của bước 0.
  - Kết quả ở `forecast_discussion.md` §13, bảng số ở `forecast_research/gd2/results/`.
  - **K = 0, λ = 2 và EM được xác nhận.**
  - **Quy tắc (b) tốt hơn quy tắc (a) trên cả hai bộ dữ liệu**; 5 vòng EM là đủ. Hai đề xuất này là M-15; chủ dự án chốt cả hai ngày 2026-10-22 (hai dòng dưới).
- [x] **Toàn bộ `mvnw test`: 166/166, 24 lớp, BUILD SUCCESS** (143 test cũ cộng 23 test mới).
- [x] **M-15 ý 2: chủ dự án duyệt EM 5 vòng (2026-10-22)**, đã sửa QĐ-1.
- [x] **Số đo bổ sung cho M-15 ý 1 (2026-10-22):** tách các thời điểm dự báo theo việc quy tắc (a) còn tác dụng hay không (`gd2/split_by_rule_a.py`, `gd2/results/rule_ab_by_regime.md`, `forecast_discussion.md` §13.7). Khi lịch sử đủ dài như app thật, (a) không đổi gì, còn (b) tốt hơn ở lễ nhỏ 0,65–0,83 điểm trên cả hai bộ dữ liệu.
- [x] **M-15 ý 1: chủ dự án chọn quy tắc (b) (2026-10-22).** Đã viết lại QĐ-3, đóng M-6 và M-15.
- [x] **Chủ dự án bảo commit** (2026-10-22, *"oke. commit đi"*).
  - Ngay trước commit, chạy `verify` chỉ đọc trên DB thật bằng code đang có (cổng 8099). Màn danh sách chuyến khớp từng mã chuyến với SQL: mặc định 12, 7 ngày 12, 90 ngày 898, lọc `COMPLETED` 90 ngày 824, lọc `ACTIVE` 6. `days=15` quay về 30 ngày. 10 trang khác trả 200, log không lỗi. Ảnh chụp DB thật (số dòng và MD5 của `trips`, `buses`) giống hệt trước và sau.
  - 3 commit: `01560cc` (bộ sinh, kèm 3 lớp test), `6a68937` (màn danh sách chuyến, kèm test), rồi commit tài liệu. `TripRepository` được chia theo từng đoạn thay đổi giữa hai commit code. Commit đầu đứng riêng vẫn biên dịch được (`test-compile` trên worktree tạm).
  - Chưa push.

### GĐ3: thay dữ liệu trên DB thật (thuộc bước 1). Chủ dự án duyệt G3-1…G3-7 ngày 2026-10-22 (QĐ-14)
- [x] **G3-1. Sửa bộ sinh: chuyến có sẵn giữ xe và người của nó (lỗi #33).**
  - Rà GĐ3 tìm ra: trên bản sao GĐ2, 12 cặp chuyến chạy cùng lúc dùng chung xe hoặc người với chuyến của chủ dự án (4 cặp cùng xe), ví dụ xe 2 ngày 16/07 lúc 22 giờ.
  - `HistoricalDataBackfill`: `loadOccupied` + `occupiedByDay`, `planDay(…, Occupied, …)`; `TripRepository.findResourceUseBetween`.
  - 4 test mới; 5 lần thử làm hỏng, lần nào đúng test tương ứng cũng đỏ. `mvnw test` **170/170**, 24 lớp.
- [x] **G3-2. Sao lưu và khôi phục thử.** `E:\Y4 S2\PRETHESIS\ProjectBackup\db\busmanagement_2026-10-22_truoc-GD3.sql` (404 KB). Nạp vào bản sao `busmanagement_gd2` tạo mới: cả 11 bảng giống DB thật cả số dòng lẫn `CHECKSUM TABLE`.
- [x] **G3-3. Tổng duyệt lại trên bản sao mới** (code đã sửa, chưa commit):
  - phần A đúng số dự kiến; phần B 2.225 → 19 chuyến, odometer 356.245 → 18.385 km, km kể từ bảo dưỡng giữ 18.355;
  - bộ sinh 74 giây, pool kết nối chỉ trên bản sao: **21.192 chuyến** (342 tăng cường, 1.363 hủy), 01/01/2023 → 21/10/2026, 20,9% khung bán hết, 13 ngày có chuyến có sẵn giữ chỗ;
  - script mới `forecast_research/gd3_check_new_history.sql` (phần C, chỉ đọc): **0 vi phạm** ở mọi mục, odometer khớp từng km. Chạy trên dữ liệu cũ của DB thật thì bắt đúng 69 chuyến của #32 và 8 xe + 12 người của #33;
  - so với lần tổng duyệt GĐ2 (trước khi sửa): chỉ 11 ngày có chuyến của chủ dự án đổi cách xếp, cộng ngày mới 21/10; nhu cầu thật giống hệt ở cả 20.835 khung;
  - 17 trang app trên bản sao trả 200, log sạch. DB thật giống hệt lúc sao lưu.
- [x] **G3-4.** Làm ngay, không dời sau GĐ5.
- [x] **G3-5.** File nhu cầu thật của lần chạy trên DB thật: `ProjectBackup/db/nhu-cau-that_2023-01-01_2026-10-21.csv` (20.850 khung), giống từng byte file của lần tổng duyệt.
- [x] **G3-6 phần trước: sửa tài liệu GĐ2 để sót.** 121 chỗ trích số dòng trong 7 bài `docs/study` và `Proj_functions_summary.md` ánh xạ lại theo code mới (từng chỗ đã soát), 9 chỗ sửa tay (`findAllWithDetails`, "12 tuần"…), `project_report.md` Warn #4; build lại 7 trang HTML (build tái lập được: bài 02 build lại giống từng byte).
- [x] **Chủ dự án bảo commit rồi làm DB thật** (2026-10-22, *"tôi hiểu rồi, bạn commit đi. sau đó bắt đầu làm db thật"*). Commit `09f4a4c`, `71e3486`, `db111c9`, chưa push; trước commit chạy `verify` chỉ đọc trên DB thật (8 trang 200, DB không đổi).
- [x] **Trên DB thật (2026-10-22).** Lời của chủ dự án được coi là xác nhận lần cuối, chỉ sau khi mọi điều kiện đều đúng: không app nào chạy, DB thật giống hệt lúc sao lưu (11 bảng), phần A giống từng ký tự lần tổng duyệt.
  - Phần B: 2.225 → 19 chuyến, odometer 356.245 → 18.385 km, km kể từ bảo dưỡng giữ 18.355.
  - Bộ sinh từ commit `09f4a4c`, 92 giây, pool trên `busmanagement`: 21.192 chuyến (342 tăng cường, 1.363 hủy), 20,9% khung bán hết.
  - Phần C: 0 vi phạm. So với bản sao tổng duyệt: cả 21.192 chuyến, odometer từng xe và file nhu cầu thật đều giống hệt. Không có bản ghi mồ côi.
  - `verify`: 17 trang và hai bộ lọc danh sách chuyến trả 200, log sạch; danh sách chuyến khớp SQL từng mã chuyến (462 / 117 / 1.370 cho 30 / 7 / 90 ngày); DB không đổi sau lần chạy app.
  - Màn dự báo cũ: MAPE 22,7% so với baseline 23,90% trên 19.829 chuyến hoàn thành (trước GĐ3 là 4,29% / 7,43%). Thống kê 3,49 giây, đề xuất tăng cường 2,32 giây, gợi ý tài xế 1,58 giây.
- [x] **G3-6 phần sau:** đóng #31, #32, #33; roadmap (Phase 5, Phase 6, Hidden Cost #1 và #4, §7, §8); QĐ-11, QĐ-14; `setup_guide.md` §4.3. Commit `docs:` ngay sau đó, khi chủ dự án bảo (*"commit đi"*).
- [ ] **G3-7.** Giữ bản sao `busmanagement_gd2` tới hết GĐ5 rồi xoá.
- Kiểm thêm theo kế hoạch cũ, đều đạt trên DB thật: `kmSinceLastMaintenance` không đổi; các màn admin trả 200; màn dự báo cũ vẫn chạy (sai số 22,7% — trạng thái tạm tới GĐ5); trang thống kê, đề xuất tăng cường, gợi ý tài xế chậm còn 1,6–3,5 giây.

### GĐ4: lõi toán Java (thuộc bước 2; không cần DB)
- Một lớp tính toán thuần, theo tiền lệ `WhatIfCoverageTest`. Gồm:
  - IRLS Poisson có ridge, λ = 2 cho cả phần lệch riêng của chuỗi lẫn hệ số lễ gộp (QĐ-3, quy tắc (b));
  - E[nhu cầu | nhu cầu ≥ ghế] theo nhị thức âm, tính bằng cách cộng thẳng các xác suất vì số ghế ≤ 50;
  - vòng EM, 5 vòng (QĐ-1);
  - giải theo khối (sự thật 7).
- Unit test đối chiếu số với bản Python trên cùng một bộ dữ liệu cố định. Đo thời gian chạy trên dữ liệu cỡ thật.
- **Điểm dừng để quyết:** nếu chậm, bàn phương án (bớt hệ số, bớt vòng EM).

### GĐ5: `ForecastService` mới và màn Dự báo (thuộc bước 2)
- Cần chốt trước: M-3, M-5, M-9. (M-6 đã đóng ngày 2026-10-22.)
- Truy vấn mới lấy nhu cầu theo khung (tuyến, giờ, ngày): cộng mọi chuyến trong khung, có cờ "bán hết vé".
- Đầu ra cho mỗi ngày-khung: nhu cầu, số chuyến cần, cờ tăng cường (sự thật 1).
- **Tạm giữ trường `predictedOccupancy`** để Recommendation và What-if vẫn chạy cho tới GĐ6.
- Cache (sự thật 2).
- Màn hình hiển thị:
  - bảng 7 ngày và xem trước 35 ngày;
  - bảng hệ số lễ;
  - backtest đo bằng WAPE, so với seasonal naive;
  - cảnh báo khi lịch lễ không phủ tới ngày cần dự báo.

### GĐ6: Recommendation và What-if (thuộc bước 3)
- Cần chốt trước: M-4, M-14.
- Recommendation:
  - số chuyến thêm = số chuyến cần − 1;
  - doanh thu = phần khách tràn mà xe được chọn chở được (thay `RecommendationService.java:213-215`).
- What-if:
  - hệ số nhu cầu nhân thẳng vào nhu cầu, không còn bị kẹp ở 100%;
  - độ phủ đếm theo số chuyến, vẫn đối chiếu từng ngày;
  - cập nhật `WhatIfCoverageTest`.
- Bỏ trường tạm `predictedOccupancy`.

### GĐ7: tài liệu và chuẩn bị bảo vệ (thuộc bước 4)
- Cần chốt trước: M-10.
- `THESIS_ROADMAP.md`:
  - §5: phần "As delivered" của Phase 5, 6, 7, 8;
  - §9: đánh dấu note "occupancy RATE" là đã bị thay thế, và thêm note mới;
  - Hidden Costs.
- Các file đang nhắc tới phần dự báo. Danh sách lấy bằng grep ngày 2026-10-21; lúc làm phải grep lại:
  - `docs/development/current_functional_spec.md`
  - `docs/development/Proj_functions_summary.md`
  - `docs/architecture/setup_guide.md` (cách chạy bộ sinh mới)
  - `docs/todo/current_bugs_found.md`
  - `docs/ai/02_feature_doc_prompt.md`
  - `docs/ai/03_defense_study_prompt.md`
- Bài học về phần dự báo, và phụ lục so sánh với LightGBM và Prophet bằng Python.

---

## 4. Nhật ký phiên

Thêm một dòng mới vào cuối bảng sau mỗi phiên; không sửa các dòng cũ.

| Ngày | Đã làm | Code app / DB | Commit |
|---|---|---|---|
| 2026-10-14 → 16 | Đọc kỹ `ForecastService`; thảo luận không viết code; backtest cho thấy LR thua Holt-Winters và seasonal naive (`forecast_discussion.md` §0–10) | Không đổi; DB chỉ `SELECT` | Chưa |
| 2026-10-20 | Thảo luận vòng 3 (chủ dự án nghiêng về B1 + C2). Làm bước 0 trên dữ liệu thật Hồng Kông và bộ sinh mô phỏng có chặn trần (§11–12) | Không đổi; không truy vấn DB | Chưa |
| 2026-10-21 | Giải thích G1' + EM, K=0 (hồi quy Poisson) và quy tắc "ít nhất 2 lần". Chủ dự án duyệt 7 quyết định. Tạo `forecast_decisions.md` và file này. Cập nhật roadmap §4, §5 (Phase 5, 6), §7, §8, §9 và phần đầu `forecast_discussion.md`. Chủ dự án bảo commit (*"commit đi bro"*) | Không đổi | Đã commit cả tài liệu 14/10 → 21/10 thành `2eebc25`; đã push lên `origin/temp` |
| 2026-10-21 (sau) | Đọc lại project và chia bước 1–4 thành 7 giai đoạn, ghi 10 sự thật đo được. Thảo luận GĐ1: chủ dự án chọn mọi đề xuất (QĐ-8, QĐ-9) và yêu cầu giá trị cột bằng tiếng Anh. Thêm M-13, M-14 | Không đổi; DB chỉ `SELECT` | Chưa |
| 2026-10-21 (cài GĐ1) | Đối chiếu 24 ngày Tết và Giỗ Tổ 2022–2033 với hai nguồn. Cài `HolidayFlow`, `Route.holidayFlow`, form và danh sách tuyến, `DataInitializer`, `Holiday` và `HolidayCalendar`, cùng 17 test mới. `mvnw test` 143/143. Verify trên bản sao DB: các màn dự báo giống hệt từng byte, thao tác tạo/sửa/xoá đúng. Cập nhật `database_schema.md`, `Proj_functions_summary.md`, `current_functional_spec.md`, file quyết định và roadmap | Code app đổi; DB thật không đổi (checksum giống hệt) | Chưa |
| 2026-10-21 (sau GĐ1) | Chủ dự án hỏi về tuyến giữa hai TP lớn và tác động của nhãn tới dự báo. Chốt: không thêm giá trị mới; thêm quy tắc "chỉ gán khi biết chắc chiều đông, không chắc thì để trống" vào QĐ-8; tuyến 6 giữ để trống | Không đổi; DB chỉ `SELECT` | Chưa |
| 2026-10-21 (commit GĐ1) | Chủ dự án test xong và gán nhãn (kiểm bằng `SELECT`). Chạy `verify` chỉ đọc trên DB thật. Commit code `79ba96c` và một commit tài liệu; chưa push. Bắt đầu vòng thảo luận GĐ2 | Không đổi | `79ba96c` và commit docs; chưa push |
| 2026-10-21 (GĐ2) | Thảo luận GĐ2, chủ dự án duyệt tất cả (QĐ-10…QĐ-13). Viết lại bộ sinh; màn danh sách chuyến có cửa sổ ngày; 23 test mới (đã thử làm hỏng 5 chỗ). Tổng duyệt GĐ3 trên bản sao `busmanagement_gd2` (2 script SQL). Kiểm bất biến và chạy app trên bản sao. Đo lại bằng Python, có thêm dữ liệu thật Hồng Kông. Ghi lỗi #31, #32. Cập nhật tài liệu. `mvnw test` 166/166 | Code app đổi; **DB thật không đổi** (checksum giống hệt); dữ liệu mới chỉ nằm trên bản sao | Chưa |
| 2026-10-22 | Giải thích lại M-15. Chủ dự án duyệt EM 5 vòng (sửa QĐ-1); ý 1 còn chờ. Đo thêm cho ý 1 bằng cách tách các lần chạy GĐ2 theo việc quy tắc (a) còn tác dụng hay không (§13.7). Sửa ngày thời điểm dự báo cuối ở §13.1 (13/09/2026) và dòng M-10 cũ ở mục 1. Chỉ đổi tài liệu và thêm một script nghiên cứu | Không đổi; không truy vấn DB | Chưa |
| 2026-10-22 (sau) | Giải thích (b) là "núm vặn tự động" chứ không phải công tắc. Chủ dự án chọn quy tắc (b) (*"oke tôi chọn (b), ghi lại đi"*). Viết lại QĐ-3; sửa QĐ-1, QĐ-9, QĐ-13 và việc của GĐ4, GĐ5; đóng M-6 và M-15 | Không đổi; không truy vấn DB | Chưa |
| 2026-10-22 (commit GĐ2) | Chủ dự án bảo commit. Chạy `verify` chỉ đọc trên DB thật (màn danh sách chuyến khớp SQL từng mã chuyến; DB giống hệt trước và sau). Chia GĐ2 thành 3 commit; kiểm commit đầu biên dịch được khi đứng riêng. Sửa vài dòng trạng thái cũ (KH, tiêu đề GĐ1). Bắt đầu thảo luận GĐ3 | Không đổi; DB chỉ `SELECT` | `01560cc`, `6a68937` và commit docs; chưa push |
| 2026-10-22 (GĐ3) | Thảo luận GĐ3: rà ra lỗi #33 (bộ sinh không né chuyến có sẵn) và phần tài liệu GĐ2 để sót; đo tốc độ trang trên 21 nghìn chuyến. Chủ dự án hỏi về quy luật và nhiễu của bộ sinh (đo lại trên dữ liệu đã sinh), rồi duyệt G3-1…G3-7 (QĐ-14). Làm G3-1 (code, 4 test, 5 lần thử làm hỏng, 170/170), G3-2 (sao lưu, khôi phục thử khớp 11/11 bảng), G3-3 (tổng duyệt lại trên bản sao: 0 vi phạm), G3-6 phần trước (121 + 9 chỗ trích trong bài học, build lại HTML). Ghi lỗi #33, M-16 | Code bộ sinh đổi (chưa commit); DB thật chỉ `SELECT` và `mysqldump`, giống hệt lúc sao lưu; bản sao `busmanagement_gd2` tạo lại | Chưa |
| 2026-10-22 (GĐ3 trên DB thật) | Chủ dự án bảo commit rồi làm DB thật. `verify` chỉ đọc rồi commit 3 phần. Trên DB thật: kiểm ảnh chụp = lúc sao lưu, phần A (giống tổng duyệt), phần B, bộ sinh từ commit `09f4a4c`, phần C (0 vi phạm), so từng chuyến với bản sao (giống hệt), `verify` (17 trang 200, danh sách chuyến khớp SQL). Đóng #31, #32, #33; cập nhật roadmap, QĐ-11, QĐ-14, setup_guide | **DB thật thay lịch sử**: 2.225 → 21.211 chuyến; sao lưu và file nhu cầu thật trong `ProjectBackup/db` | `09f4a4c`, `71e3486`, `db111c9`; tài liệu sau khi chạy chưa commit; chưa push |

---

## 5. Quy tắc làm việc cho đợt này

- **Mỗi bước đi theo vòng:** lập kế hoạch → chủ dự án duyệt → làm → verify → cập nhật file này và roadmap → chủ dự án tự test → **chỉ commit khi chủ dự án bảo**.
  - Commit không mang trailer Co-Authored-By.
  - Tách commit theo loại: feature / fix / docs / refactor.
- **Mỗi giai đoạn bắt đầu bằng một vòng thảo luận**: đối chiếu với project, phản biện, đưa lựa chọn có lý do, chủ dự án chốt. Sau đó ghi vào `forecast_decisions.md`, rồi mới làm.
- **Ghi vào DB thật** chỉ xảy ra ở GĐ3: phải có backup và chủ dự án xác nhận lần cuối.
- **Lưu ý khi bật app:** `TripService.java:76` có `@Scheduled(fixedRate = 10_000)`. Các phiên nghiên cứu đã không bật app.
- **Quyết định mới hay thay đổi quyết định:** ghi vào `forecast_decisions.md` (mục 6, lịch sử). Không đổi im lặng trong code.
- **Chạy lại thí nghiệm Python:** xem `forecast_research/step0/README.md`.
  - Cần venv ở đường dẫn ngắn (`subst`) và shim `pydoc` trong `step0/env/`.
  - Không nạp code tải từ ngoài vào venv.
  - File kết quả (khoảng 50 MB mỗi file) ghi ra ngoài repo.
