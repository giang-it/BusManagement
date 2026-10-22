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

## 1. Trạng thái hiện tại (cập nhật 2026-10-23)

- **Đã chốt** QĐ-1…QĐ-17 (`forecast_decisions.md` mục 1). QĐ-8, QĐ-9 là thiết kế của GĐ1; QĐ-10…QĐ-13 của GĐ2; QĐ-14 của GĐ3; QĐ-15 của GĐ4; QĐ-16 của GĐ5; QĐ-17 của GĐ6.
- **Đã chia** bước 1–4 thành 7 giai đoạn (mục 2–3 dưới đây). Mỗi giai đoạn xong thì app vẫn chạy được.
- **GĐ1 xong và đã commit ngày 2026-10-21, đã push ngày 2026-10-22.**
  - Code: `79ba96c`. Tài liệu: commit `docs:` ngay sau đó.
  - Chủ dự án đã chạy app trên DB thật, gán nhãn 8 tuyến và tự test (*"test xong rồi, commit đi"*).
  - Chi tiết và bằng chứng ở mục 3.
- **GĐ2 xong ngày 2026-10-21, đã commit và push ngày 2026-10-22** (QĐ-10…QĐ-13; chi tiết ở mục 3):
  - Code: `01560cc` (bộ sinh mới) và `6a68937` (màn danh sách chuyến). Tài liệu: commit `docs:` ngay sau đó.
  - bộ sinh mới, màn danh sách chuyến có cửa sổ ngày, 23 test mới;
  - tổng duyệt GĐ3 trên bản sao `busmanagement_gd2`;
  - đo lại bằng Python, kết quả ở `forecast_discussion.md` §13;
  - ghi lỗi #31 và #32;
  - M-15 đã chốt ngày 2026-10-22: quy tắc (b) vào QĐ-3, EM 5 vòng vào QĐ-1.
- **GĐ3 xong ngày 2026-10-22** (QĐ-14, G3-1…G3-7; chi tiết ở mục 3):
  - Code và tài liệu chuẩn bị: commit `09f4a4c` (sửa bộ sinh, lỗi #33), `71e3486` (bài học), `db111c9` (ghi chép GĐ3); đã push ngày 2026-10-22.
  - **DB thật đã thay lịch sử:** 21.192 chuyến mô phỏng từ 01/01/2023 tới 21/10/2026, cộng 19 chuyến của chủ dự án. Giống hệt bản sao tổng duyệt tới từng chuyến.
  - Tài liệu ghi sau khi chạy: commit `4dea67b` (2026-10-22), đã push.
- **GĐ4 xong và đã commit ngày 2026-10-22, đã push cùng ngày** (QĐ-15; chi tiết ở mục 3):
  - Code: `c5a830c`. Tài liệu: commit `docs:` ngay sau đó.
  - lõi toán Java `SlotDemandModel` + `ArrowSolver`, chưa nối vào app;
  - khớp bản Python đã đo: 1.050/1.050 dự báo, sai lệch lớn nhất 9,7·10⁻⁹;
  - 0,2–0,6 giây cho một lần khớp đầy đủ; `mvnw test` 184/184.
- **GĐ5 xong, đã commit và push ngày 2026-10-22** (QĐ-16; đóng M-3, M-4, M-5, M-9; chi tiết ở mục 3):
  - Code: `b6dced9`; bài học: `7b58519`; tài liệu: commit `docs:` ngay sau đó.
  - `ForecastService` mới: nhu cầu khung từ chuyến `COMPLETED`, khớp lõi GĐ4, nhớ kết quả; màn Dự báo mới (bảng 7 ngày bằng số khách, biểu đồ 35 ngày, hệ số lễ, tự chấm điểm, khối Tết);
  - trên DB thật: số trên màn khớp Python tới 9,7·10⁻⁹; tự chấm 16,06% so với 17,54% của SN4 (Tết 2026: 19,83% so với 35,92%);
  - Đề xuất và What-if chạy qua cầu nối, không sửa code: cả ba màn cùng 41 khung đông;
  - `mvnw test` 204/204.
- **GĐ6 xong, đã commit và push ngày 2026-10-23** (QĐ-17; đóng M-14; chi tiết ở mục 3):
  - Code: `81b94af`; bài học: `ef3f0eb`; tài liệu: commit `docs:` ngay sau đó.
  - Đề xuất: mỗi chuyến thêm một thẻ; chuyến thêm chở **khách tràn kỳ vọng**; giữ chỗ xe + tài xế giữa các thẻ, xét theo lãi kỳ vọng; chuyến lỗ vẫn có thẻ, gắn nhãn;
  - What-if: hệ số nhân vào số khách, đếm theo chuyến thêm, tiền theo khách tràn, dòng "chỉ chạy chuyến có lãi"; bỏ cầu nối `predictedOccupancy`;
  - trên DB thật: 41 thẻ, mỗi thẻ khớp Python tới từng đồng; 5 xe, 9 tài xế, 0 trùng (trước: 2 xe, 1 tài xế); 4 có lãi · 37 lỗ dự kiến; What-if 70% báo 2 chuyến thêm thay cho 0;
  - `mvnw test` 217/217; 15 lần thử làm hỏng đúng test đỏ.
- **Việc tiếp theo:** thảo luận GĐ7 (tài liệu và chuẩn bị bảo vệ).
- **Code app:** đã đổi ở GĐ1, GĐ2 và GĐ3, đều đã commit; GĐ4 thêm hai lớp mới (commit `c5a830c`); GĐ5 viết lại `ForecastService` để dùng chúng (commit `b6dced9`); GĐ6 chuyển Recommendation và What-if sang đơn vị nhu cầu (commit `81b94af`). **DB thật:** đã mang dữ liệu của bộ sinh mới từ ngày 2026-10-22. Bản sao `busmanagement_gd2` giống DB thật, giữ tới hết GĐ5 để thử thao tác ghi.
- **Commit:** mọi commit của đợt này đã push lên `origin/temp`:
  - `2eebc25` (tài liệu 14/10 → 21/10) push ngày 2026-10-21;
  - ngày 2026-10-22, theo lời chủ dự án *"commit và push tất cả lên đi"*: GĐ1 (`79ba96c`, `fa50ac3`), GĐ2 (`01560cc`, `6a68937`, `d04352a`), GĐ3 (`09f4a4c`, `71e3486`, `db111c9`, `4dea67b`), GĐ4 (`c5a830c`, `36ddc9a`) và GĐ5 (`b6dced9`, `7b58519`, `aa82299`);
  - ngày 2026-10-23, theo lời chủ dự án *"commit và push đi bro"*: GĐ6 (`81b94af`, `ef3f0eb`, cùng commit `docs:` chứa dòng này).
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
| GĐ1 | Nền móng: `Route.holidayFlow` và lịch ngày lễ | 1 | ĐÃ COMMIT `79ba96c`, đã push | 2026-10-21 |
| GĐ2 | Bộ sinh mới, chạy trên bản sao DB; chạy lại thí nghiệm Python | 1 | ĐÃ COMMIT `01560cc`, `6a68937`, đã push | 2026-10-21 → 22 |
| GĐ3 | Thay dữ liệu trên DB thật | 1 | ĐÃ COMMIT `09f4a4c`, `71e3486`, `db111c9`, `4dea67b`, đã push | 2026-10-22 |
| GĐ4 | Lõi toán Java, unit test và đo tốc độ | 2 | ĐÃ COMMIT `c5a830c`, đã push | 2026-10-22 |
| GĐ5 | `ForecastService` mới, cache và màn Dự báo | 2 | ĐÃ COMMIT `b6dced9`, `7b58519`, đã push | 2026-10-22 |
| GĐ6 | Recommendation và What-if theo nhu cầu | 3 | ĐÃ COMMIT `81b94af`, `ef3f0eb`, đã push | 2026-10-22 → 23 |
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
7. **Hiệu năng.** Giải trực tiếp khoảng 1.400 ẩn bằng Java thuần có thể mất hàng chục giây. Ma trận có cấu trúc "mũi tên": mỗi tuyến chỉ dính tới cột của chính nó và cột lễ gộp chung. Giải theo khối (Schur complement) thì dự kiến dưới 1 giây. **Đã đo ở GĐ4:** 1.431 ẩn, một lần khớp đầy đủ mất 0,22–0,56 giây.
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

### GĐ4: lõi toán Java (thuộc bước 2; không cần DB). Chủ dự án duyệt G4-1…G4-8 ngày 2026-10-22 (QĐ-15). Code xong và commit cùng ngày (`c5a830c`)
- [x] **Đo chung cấu hình cuối** (EM 5 vòng + quy tắc (b)) trên dữ liệu GĐ2: 22,35% / 22,58%, hơn mô hình chính GĐ2 0,21 / 0,17 điểm, hơn app hiện tại 3,11 / 2,83 điểm; giống hệt khi đo riêng quy tắc (b) (`forecast_discussion.md` §14.1). Script: `forecast_research/gd4/run_final.py`; chạy lại từ bản trong repo cho đúng 189.000 dòng dự báo như lần đầu. Lần chạy đầu bị treo nửa tiếng vì script nghiên cứu đọc tham số dòng lệnh ở phần đầu file, mà tiến trình con của `multiprocessing` trên Windows chạy lại phần đó; đã sửa (đặt dưới `if __name__ == "__main__"`).
- [x] **Dữ liệu đối chiếu:** `forecast_research/gd4/export_slot_history.sql` (chỉ đọc) xuất 19.487 khung có chuyến hoàn thành của DB thật vào `src/test/resources/forecast/slot_history.csv` (+ `routes.csv`); khớp 19.487/19.487 với file nhu cầu thật của GĐ3. `forecast_research/gd4/reference.py` tạo kết quả mong đợi từ `slot_glm` của GĐ2 với cấu hình cuối, ở hai ngày gốc 25/01/2026 và 21/10/2026.
- [x] **Code** (`service`, package-private):
  - `SlotDemandModel`: dựng cột theo lịch (`HolidayCalendar`), IRLS có ridge và giảm bước, EM 5 vòng, chỉnh mức 28 ngày, dự báo với xu hướng đóng băng, hệ số lễ gộp. Bước E cộng phần đuôi nhị thức âm từ số ghế trở lên. Ngưỡng là **tổng số ghế của cả khung**, kể cả chuyến tăng cường (tới 172), không phải "≤ 50" như kế hoạch cũ ghi.
  - `ArrowSolver`: giải hệ "mũi tên" bằng bù Schur, mỗi chuỗi một khối Cholesky.
- [x] **Test** (14 mới, JUnit thuần):
  - `ArrowSolverTest` (2): nghiệm trùng khử Gauss trên hệ ngẫu nhiên cỡ thật (5 khối 82 ẩn + 67 ẩn chung); ma trận không xác định dương bị từ chối.
  - `SlotDemandModelTest` (8): nhu cầu hằng số, thứ trong tuần, xu hướng đóng băng, hệ số Tết học từ năm trước, EM kéo khung bán hết về gần nhu cầu thật (30 chứ không 26), dữ liệu sau ngày gốc bị bỏ qua, tính chất của bước E, đầu vào sai bị từ chối.
  - `SlotDemandModelReferenceTest` (4): **1.050/1.050 dự báo khớp Python**, sai lệch tương đối lớn nhất 9,7·10⁻⁹; 402/402 hệ số lễ; 168/168 giá trị bước E so với scipy; tốc độ.
  - 5 lần thử làm hỏng (tắt ridge lễ gộp, 15 vòng EM, xu hướng không đóng băng, bỏ bước bù Schur, cửa sổ chỉnh mức 21 ngày): mỗi lần đúng test tương ứng đỏ.
- [x] **Tốc độ:** 0,22–0,26 giây cho một lần khớp đầy đủ khi chạy riêng, 0,56 giây khi chạy chung cả bộ test (mục tiêu ≤ 2 giây). Python mất 3,5–14 giây.
- [x] **Toàn bộ `mvnw test`: 184/184, 27 lớp, BUILD SUCCESS** (170 cũ + 14 mới).
- [x] **Chủ dự án bảo commit** (*"commit đi"*). Trước commit, `verify` chỉ đọc trên DB thật: app khởi động với code mới, 16 trang 200, log sạch, màn Dự báo vẫn 22,7% / 23,90%, 11 bảng giữ nguyên (số dòng và `CHECKSUM TABLE`). Code: `c5a830c`; tài liệu: commit `docs:` ngay sau đó.

### GĐ5: `ForecastService` mới và màn Dự báo (thuộc bước 2). Chủ dự án duyệt G5-1…G5-8 ngày 2026-10-22 (QĐ-16; đóng M-3, M-4, M-5, M-9). Code xong, commit (`b6dced9`, `7b58519`) và push cùng ngày
- [x] **Số liệu cho vòng thảo luận** (`forecast_discussion.md` §15, `forecast_research/gd5/backtest_reference.py`):
  - mô hình mới báo 41/105 ô cần tăng cường cho 23–29/10, màn cũ báo 14; thực tế 12 tuần cuối có 43,8% khung bán quá 90% xe chuẩn;
  - so thẳng nhu cầu với số vé làm mô hình trông kém hơn SN4 (20,11% so với 18,28%), vì nó đúng ở những khung bán hết vé. Cách công bằng — min(dự báo; số ghế) so với số vé — cho 16,06% so với 17,54%.
- [x] **Gom khung (G5-2, M-3):** `ForecastService.aggregateSlots` cộng vé và ghế của mọi chuyến `COMPLETED` cùng (tuyến, giờ, ngày). Dùng lại `findDemandHistoryByStatus`; bỏ dòng ngoài lịch lễ hoặc sau hôm nay. Chuỗi cần ≥ 28 ngày quan sát; nhóm chiều theo `Route.holidayFlow`.
- [x] **Dự báo và cầu nối (G5-1, G5-3, G5-6):**
  - ngày gốc là ngày cuối có dữ liệu; dự báo 7 ngày (`points`) và 35 ngày (`preview`) từ ngày mai, không vượt quá lịch lễ;
  - `predictedDemand`, `demandRatio`, `tripsNeeded`; giữ `predictedOccupancy` (kẹp [0, 1]) và `needsReinforcement` cho Recommendation, What-if;
  - xe chuẩn = `Route.suitableBusType`, hoặc sức chứa trung bình đội xe.
- [x] **Nhớ kết quả (G5-4, M-5):** so nguyên ma trận đầu vào; giống hệt thì dùng lại, khác thì khớp lại. Phần tự chấm điểm chỉ chạy trong `buildForecastPage()` và được nhớ cùng đầu vào.
- [x] **Màn Dự báo (G5-5, G5-7, M-9):**
  - thẻ tóm tắt; bảng 7 ngày bằng số khách, có xe chuẩn, ô đỏ ghi "cần N chuyến";
  - biểu đồ 35 ngày (Chart.js 4.4.1); bảng hệ số lễ theo chiều;
  - khối tự chấm điểm (8 mốc, SN4 sạch, WAPE trên số vé, khung bán hết được báo trước, cờ bắt được / báo đúng) và khối "Tết vừa qua";
  - cảnh báo dữ liệu mô phỏng / dữ liệu cũ / hết lịch lễ.
- [x] **Test:** `ForecastServiceRulesTest` (8), `ForecastServiceReferenceTest` (4: khớp Python ở ngày gốc 21/10/2026 và Tết 25/01/2026, khớp mọi thước đo trong `reference_backtest.csv`), `ForecastServiceTest` (8, đi qua DB test).
  - 11 lần thử làm hỏng đều làm đỏ đúng test, có một lần chạy đối chứng (không phá gì) để chứng minh bộ chạy thử hoạt động.
  - `mvnw test` **204/204**, 30 lớp.
- [x] **Chạy app trên DB thật (chỉ đọc):**
  - màn Dự báo lần đầu 3,6–7,3 giây (tùy lần đo), lần sau 0,16–0,6 giây;
  - 510 số khớp Python tới 9,7·10⁻⁹, khối Tết khớp tới 2,4·10⁻¹³, 12 con số tự chấm đúng;
  - 41 ô đỏ = 41 thẻ Đề xuất = 41 khung đông What-if; Đề xuất mất 5,0–6,7 giây (41 thẻ);
  - 18 trang 200, log sạch, DB giữ nguyên.
- [x] **Tài liệu:**
  - `Proj_functions_summary.md` §16 (viết lại);
  - `current_functional_spec.md`;
  - `test_case.md` mục 8 (TC_FC_001–009);
  - `forecast_discussion.md` §15;
  - roadmap (trạng thái Phase 6, §7, §8, §9);
  - 4 bài học 00a/00b/03/05 trỏ sang code mới, HTML build lại.
- [x] **Chủ dự án bảo commit và push** (*"commit và push tất cả lên đi"*). Trước commit, `verify` chỉ đọc trên DB thật: 17 trang 200, log sạch, DB giữ nguyên. Code `b6dced9`, bài học `7b58519`, tài liệu: commit `docs:` ngay sau đó; push mọi commit lên `origin/temp`.

### GĐ6: Recommendation và What-if (thuộc bước 3). Chủ dự án duyệt G6-1…G6-7 ngày 2026-10-23 (QĐ-17; đóng M-14). Code xong, commit (`81b94af`, `ef3f0eb`) và push cùng ngày
- [x] **Số liệu cho vòng thảo luận** (`forecast_discussion.md` §16, `forecast_research/gd6/discussion_numbers.py`): cầu nối GĐ5 báo 41/41 chuyến thêm có lãi (+134,8 triệu/tuần), tính theo khách tràn kỳ vọng chỉ 4/41 (−19,1 triệu); tuần Tết 57 khung cần 82 chuyến thêm; 41 thẻ cũ dùng chung 2 xe và 1 tài xế. Chủ dự án nhờ giải thích lại từng điểm kèm ví dụ và ưu nhược, rồi duyệt tất cả.
- [x] **Khách của chuyến thêm (G6-1):** `SlotDemandModel.expectedOverflow` (E[(nhu cầu − c)⁺], cùng hằng đẳng thức với bước E), `ForecastService.extraTripPassengers` = EO(ghế trước) − EO(ghế trước + ghế của nó), size lấy từ lần khớp.
- [x] **Recommendation (G6-2, G6-3, G6-4):** mỗi chuyến thêm một thẻ "k/(N − 1)"; xét theo lãi kỳ vọng với xe chuẩn; ghế của chuyến = xe được chọn (không có xe thì xe chuẩn); qua cổng thì giữ chỗ bằng `AvailabilityContext.reserve()` (chuyến `PENDING_APPROVAL` trong bộ nhớ; `TripService` không đổi); hiển thị theo lợi nhuận; nhãn "lỗ dự kiến", tóm tắt lãi/lỗ, trạng thái "Đủ điều kiện"; bỏ cảnh báo trùng khung.
- [x] **What-if (G6-5):** hệ số × số khách; số chuyến thêm bằng `ForecastService.tripsNeeded` (bỏ hằng 0,90 khai lại); `coverableSlots` giữ nguyên, mỗi chuyến thêm một phần tử; tiền theo khách tràn với xe chuẩn, làm tròn từng chuyến; kế hoạch phủ được lấy chuyến có lãi trước; dòng "Nếu chỉ chạy chuyến có lãi".
- [x] **Bỏ cầu nối (G6-6):** `ForecastPointDto.predictedOccupancy`; ba chỗ kiểm trong `ForecastServiceTest` đổi sang `demandRatio`.
- [x] **Test (G6-7):** `RecommendationServiceTest` (6), `WhatIfSimulationServiceTest` (3), `ForecastServiceRulesTest` +2, `SlotDemandModelReferenceTest` +1 (288 giá trị scipy trong `reference_overflow.csv`, do `forecast_research/gd6/overflow_reference.py` tạo), `TripServiceAvailabilityContextTest` +1.
  - 15 lần thử làm hỏng: lần đối chứng xanh; 14 lỗi cài cố ý (bỏ giữ chỗ, `reserve()` rỗng, ghế trước sai hai kiểu, bỏ xe được chọn, xét theo giờ thay vì lãi, hiển thị theo tỉ lệ, What-if kẹp nhu cầu / đếm theo khung / doanh thu đầy xe / phủ chuyến lỗ trước, công thức khách tràn lệch một ghế, khách tràn tất định) đều làm đỏ đúng test.
  - `mvnw test` **217/217**, 32 lớp.
- [x] **Chạy app trên DB thật (chỉ đọc), chân trời 24–30/10/2026:**
  - màn Dự báo vẫn khớp Python tới 9,7·10⁻⁹; size nhị thức âm của lần khớp Python cùng ngày gốc 82,2086;
  - Đề xuất: 41 khung → 41 thẻ, cả 41 "Đủ điều kiện"; mọi thẻ khớp Python (khách tràn cộng trực tiếp bằng scipy) tới từng đồng; 4 có lãi · 37 lỗ dự kiến; −19.113.706đ cả tuần, +1.750.444đ nếu chỉ chạy chuyến có lãi;
  - 41 thẻ dùng 5 xe và 9 tài xế, 0 trùng xe, 0 trùng tài xế, không ai quá 8 giờ lái;
  - What-if khớp phép tính độc lập ở 70/100/130/150% (70%: 2 chuyến thêm; 150%: 103 chuyến cho 100 khung, lãi 28,6 triệu);
  - Đề xuất 4,9–6,0 giây, What-if 0,2–0,3 giây; 17 trang 200, log sạch, DB giữ nguyên (11 bảng).
- [x] **Tài liệu:** QĐ-17 (đóng M-14); `forecast_discussion.md` §16; `Proj_functions_summary.md` §13, §16, §18 viết lại, §19 mới (What-if); `current_functional_spec.md` (Recommendation viết lại, What-if mới); `test_case.md` Module 10 viết lại (TC_RC_001–007), Module 11 mới (TC_WI_001–005), TC_FC_009; `current_bugs_found.md` (mục What-if 50–90% đã hết); roadmap; 5 bài học 00a/00b/03/05/06 trỏ sang code mới, HTML build lại; `forecast_research/README.md`.
- [x] **Chủ dự án bảo commit và push** (*"commit và push đi bro"*). Trước commit, `verify` chỉ đọc trên DB thật: 18 lượt mở trang 200 (17 trang, What-if thêm 70% và 150%), log sạch, màn Đề xuất đúng 41 thẻ / 37 nhãn "lỗ dự kiến" / không còn cảnh báo trùng khung, DB giữ nguyên (11 bảng). Code `81b94af`, bài học `ef3f0eb`, tài liệu: commit `docs:` ngay sau đó; push lên `origin/temp`.

### GĐ7: tài liệu và chuẩn bị bảo vệ (thuộc bước 4)
- Cần chốt trước: không còn mục nào (M-10 đã đóng ngày 2026-10-21).
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
| 2026-10-22 (GĐ4) | Commit tài liệu GĐ3 sau khi chạy (`4dea67b`). Thảo luận GĐ4, đo chung cấu hình cuối (§14.1); chủ dự án duyệt G4-1…G4-8 (QĐ-15). Xuất dữ liệu đối chiếu từ DB thật (chỉ đọc), viết `reference.py`, `SlotDemandModel`, `ArrowSolver`, 14 test; Java khớp Python tới 9,7·10⁻⁹; 0,2–0,6 giây mỗi lần khớp; 5 lần thử làm hỏng; `mvnw test` 184/184 | Thêm 2 lớp, chưa nơi nào gọi tới; DB thật chỉ `SELECT` | `4dea67b` (tài liệu GĐ3); `c5a830c` (GĐ4) + commit `docs:` ngay sau đó |
| 2026-10-22 (GĐ5) | Commit GĐ4 (`c5a830c`, `36ddc9a`) sau `verify` chỉ đọc. Thảo luận GĐ5 kèm số liệu (§15); chủ dự án duyệt G5-1…G5-8 (QĐ-16). Viết lại `ForecastService`, 4 DTO, màn Dự báo; `backtest_reference.py`; 20 test; 11 lần thử làm hỏng; `mvnw test` 204/204; chạy app trên DB thật, số trên màn khớp Python tới 9,7·10⁻⁹ | Viết lại `ForecastService` và màn Dự báo; Recommendation, What-if không đổi code; DB thật chỉ `SELECT` | `c5a830c`, `36ddc9a` (GĐ4); `b6dced9`, `7b58519` + commit `docs:` (GĐ5); push tất cả lên `origin/temp` |
| 2026-10-22 → 23 (GĐ6) | Thảo luận GĐ6 kèm số liệu (§16), giải thích lại từng điểm kèm ví dụ và ưu nhược; chủ dự án duyệt G6-1…G6-7 (QĐ-17, đóng M-14). Viết lại phần dựng thẻ của `RecommendationService` và phần đếm/tiền của `WhatIfSimulationService`; khách tràn kỳ vọng; giữ chỗ trong `AvailabilityContext`; bỏ cầu nối; 13 test mới; 15 lần thử làm hỏng; `mvnw test` 217/217; chạy app trên DB thật, mọi thẻ khớp Python tới từng đồng, 0 trùng xe/tài xế | Đổi Recommendation, What-if, hai template, 5 DTO, `ForecastService`, `SlotDemandModel` (thêm một hàm), `AvailabilityContext`; `TripService` không đổi; DB thật chỉ `SELECT` | `81b94af`, `ef3f0eb` + commit `docs:`; push lên `origin/temp` |

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
