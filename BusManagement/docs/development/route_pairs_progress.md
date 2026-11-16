# Kế hoạch và tiến độ: cặp tuyến và điều độ theo dự báo

> **File theo dõi qua nhiều phiên.** Cuối mỗi phiên làm việc phải cập nhật:
> - mục 1 (trạng thái);
> - mục 2 (bảng giai đoạn);
> - mục 4 (đánh `[x]`, kèm ngày và chỗ kiểm chứng);
> - thêm một dòng vào mục 5 (nhật ký).
>
> Tài liệu liên quan:
> - Quyết định: [`route_pairs_decisions.md`](route_pairs_decisions.md) (QĐ-18…QĐ-27, các mục chưa chốt CM-1…CM-15).
> - Đợt trước (dự báo): [`forecast_progress.md`](forecast_progress.md), [`forecast_decisions.md`](forecast_decisions.md).
> - `THESIS_ROADMAP.md` vẫn là tracker chung của cả dự án. §7 trỏ về file này, §8 ghi một mục ngắn cho mỗi phiên.

## Bắt đầu một phiên mới: đọc theo thứ tự

1. `THESIS_ROADMAP.md`: §7 và §10.
2. File này: mục 1, 2 và giai đoạn đang làm ở mục 4.
3. `route_pairs_decisions.md`: mục 1 (bảng QĐ) và mục 3 (chưa chốt).

---

## 1. Trạng thái hiện tại (cập nhật 2026-11-16)

- **Mục tiêu của đợt** (theo ý thầy hướng dẫn):
  - màn dự báo gọn lại, chỉ còn 3 cặp tuyến hai chiều quanh Bến xe Miền Đông;
  - xe chạy theo vòng đi–về;
  - dự báo dùng để thêm vòng (tăng cường), tự tạo chuyến chờ duyệt, và quyết định giữ xe tăng cường ở bến xa hay cho về;
  - nhìn vào màn hình là thấy chiều nào đã được tăng cường.
- **Đã chốt:** QĐ-18…QĐ-27 (ngày 2026-11-11, 13 và 16).
- **Đã lập kế hoạch:** 11 giai đoạn, CT0…CT10 (mục 2 và 4).
- **Kế hoạch được duyệt** ngày 2026-11-16 (*"oke. chốt hãy thực hiện đi"*).
- **CT0 xong:**
  - CM-1: đã bỏ diff format của `ForecastService.java`.
  - CM-11: lỗi #34 và 7 mục có hướng xử lý đã ghi vào `current_bugs_found.md`.
- **CT1 xong, chưa commit.**
  - `mvnw test` 217/217.
  - Thử làm hỏng: đặt lại công thức cũ thì đúng 2 test đỏ.
  - `verify` trên DB thật (chỉ đọc), DB giống hệt trước và sau. Chi tiết ở mục 4.
- **CT2 xong, chưa commit** (chủ dự án bảo *"làm CT02 luôn đi"*):
  - tuyến 10, 11 đổi sang Limousine 22 qua form tuyến trên DB thật (có sao lưu trước);
  - 3 cặp đúng QĐ-19 và QĐ-20;
  - dữ liệu mẫu khớp DB thật (CM-13);
  - `mvnw test` 217/217. Chi tiết ở mục 4.
- **CT0–CT2 đã commit và push ngày 2026-11-17** (chủ dự án: *"oke, hãy commit và push đi"*):
  - `963aa71`: CT1, code + test;
  - `bbedfd8`: CT2, dữ liệu mẫu;
  - `ed38ce6`: bài học;
  - commit `docs:` ngay sau đó: kế hoạch, quyết định, sổ lỗi, các tài liệu còn lại;
  - trước khi commit đã chạy lại `verify` trên app thật (PID 21568): 11 trang chính trả 200, What-if 200% ra 85, log sạch.
- **2026-11-17 (sau):** chốt CM-2 (H1 "về ngay") và CM-3 (tâm 100%). Tách CT5 thành CT5a (trước CT3) và CT5b (sau CT4).
- **CT5a xong ngày 2026-11-17** (CM-5 cách B, CM-12 cách (a)):
  - bến nhà cho xe và tài xế; luật vị trí `domain/LocationRule`;
  - DB thật: 20 xe và 36 tài xế có bến nhà Miền Đông, có sao lưu trước;
  - việc chọn xe giống hệt trước (chưa bật);
  - test 230/230.

  Chi tiết ở mục 4.
- **CT5a đã commit và push:** `4c3ce51` (code + test), `d39c17e` (bài học), và commit `docs:` ngay sau đó.
  - Trước khi commit đã kiểm lại một lượt trên app thật: 16 trang trả 200; việc chọn xe giống hệt mốc; danh sách và form đúng; log sạch.
- **Việc tiếp theo:** CT3 (bộ sinh mới, chạy trên bản sao). CM-2 và CM-3 đã chốt.
- **Dữ liệu:**
  - DB thật: 4 tuyến 10–13 và bến Vũng Tàu do chủ dự án tạo ngày 2026-11-16;
  - CT2 đổi loại xe của tuyến 10, 11;
  - sao lưu trước CT2: `ProjectBackup\db\busmanagement_2026-11-16_truoc-CT2.sql`.

---

## 2. Các giai đoạn

Nhãn trạng thái: `CHƯA BẮT ĐẦU` / `ĐÃ CHỐT THIẾT KẾ` / `ĐANG LÀM` / `XONG, CHƯA COMMIT` / `ĐÃ COMMIT`.

**Thứ tự:** CT0 → CT1 → CT2 → **CT5a** → CT3 → CT4 → **CT5b** → CT6 → CT7 → CT8 → CT9 → CT10.
- CT5 được tách đôi ngày 2026-11-17. Định nghĩa vị trí và bến nhà (CT5a) làm trước bộ sinh, để bộ sinh tuân đúng luật đó. Bật vị trí vào việc chọn xe (CT5b) làm sau khi có lịch sử mới.
- Lý do đo trên DB thật: trên lịch sử cũ, 11/20 xe "đứng" ở bến ngoài 3 cặp.
- CT1 không phụ thuộc CT2–CT4, nên có thể làm xen kẽ.
- Mọi giai đoạn khác phải theo đúng thứ tự, vì giai đoạn sau dùng kết quả của giai đoạn trước (cột "Cần trước").

| GĐ | Nội dung | QĐ | Cần trước | Ghi DB thật? | Trạng thái | Ngày |
|---|---|---|---|---|---|---|
| CT0 | Khung theo dõi, ghi quyết định, dọn việc tồn | tất cả | — | không | ĐÃ COMMIT (commit `docs:` sau `ed38ce6`), đã push | 2026-11-16 → 17 |
| CT1 | Công thức số chuyến: max(2, ⌈nhu cầu ÷ ghế⌉) | QĐ-18 | CT0 (CM-1) | không | ĐÃ COMMIT `963aa71`, `ed38ce6`, đã push | 2026-11-16 → 17 |
| CT2 | Cấu hình tuyến và loại xe; dữ liệu mẫu | QĐ-19, QĐ-20 | CT0 | **có** (loại xe tuyến 10, 11) | ĐÃ COMMIT `bbedfd8`, `ed38ce6`, đã push | 2026-11-16 → 17 |
| CT5a | Định nghĩa vị trí (luật dùng chung cho bộ sinh và app), bến nhà xe và tài xế; **chưa bật** vào việc chọn xe | QĐ-23 | CT2 | **có** (bến nhà) | ĐÃ COMMIT `4c3ce51`, `d39c17e` + commit `docs:` sau đó, đã push | 2026-11-17 |
| CT3 | Bộ sinh mới: danh sách tuyến, chung mức theo cặp, khách về ±15%, xe chạy vòng, "về ngay" — chạy trên bản sao | QĐ-21 | CT2, CT5a | không | CHƯA BẮT ĐẦU | |
| CT4 | Thay lịch sử trên DB thật | QĐ-21 | CT3 đã commit | **có** | CHƯA BẮT ĐẦU | |
| CT5b | Bật vị trí vào việc chọn xe, tài xế (AI và dropdown) | QĐ-23 | CT4 | không | CHƯA BẮT ĐẦU | |
| CT6 | Cặp tuyến và lịch thường lệ; tạo chuyến thường lệ tương lai | QĐ-22 | CT5b | **có** (chuyến tương lai) | CHƯA BẮT ĐẦU | |
| CT7 | Màn "Kế hoạch cặp tuyến" (M2), lãi cả vòng, trừ chuyến đã có | QĐ-22 | CT6 | không | CHƯA BẮT ĐẦU | |
| CT8 | Tự tạo chuyến chờ duyệt mỗi đêm; sửa job 10 giây | QĐ-24 | CT7 | **có** (chuyến chờ duyệt) | CHƯA BẮT ĐẦU | |
| CT9 | Luật giữ–về, tiền chờ, kíp lái ở lại cùng xe | QĐ-25, QĐ-26 | CT8 | có | CHƯA BẮT ĐẦU | |
| CT10 | Tài liệu, bài học, chuẩn bị bảo vệ | — | CT9 | không | CHƯA BẮT ĐẦU | |

---

## 3. Sự thật đã đo khi lập kế hoạch (2026-11-16; dùng cho các giai đoạn sau)

**Dữ liệu (DB thật `busmanagement`, chỉ đọc):**

1. **Tuyến:**

   | id | Tuyến | Km | Phút | Loại xe gợi ý | holidayFlow | Số chuyến |
   |---|---|---|---|---|---|---|
   | 1 | MĐ → Niệm Nghĩa | 120 | 120 | Limousine 22 | LEAVING_HUB | 4.252 |
   | 2 | Miền Đông → Đà Lạt | 300 | 360 | Giường nằm 40 | LEAVING_HUB | 4.266 |
   | 3 | Đà Nẵng → Huế | 100 | 120 | Giường nằm 40 | — | 4.199 |
   | 4 | Niệm Nghĩa → MĐ | 120 | 90 | Giường nằm 40 | TOWARD_HUB | 4.260 |
   | 5 | Cần Thơ → Miền Đông | 180 | 210 | Ghế ngồi 50 | TOWARD_HUB | 4.233 |
   | 6 | MĐ → Miền Đông | 1.700 | 1.800 | Giường nằm 40 | — | 1 |
   | 8 | MĐ → Đà Nẵng → Cần Thơ | 1.800 | 3.000 | Giường nằm 40 | LEAVING_HUB | 0 |
   | 9 | A → b → c → d (tuyến thử) | 4.500 | 4.500 | Giường nằm 40 | — | 0 |
   | 10 | Miền Đông → Vũng Tàu | 100 | 120 | Ghế ngồi 50 (QĐ-20: đổi sang Limousine 22) | LEAVING_HUB | 0 |
   | 11 | Vũng Tàu → Miền Đông | 100 | 120 | Ghế ngồi 50 (QĐ-20: đổi sang Limousine 22) | TOWARD_HUB | 0 |
   | 12 | Đà Lạt → Miền Đông | 300 | 360 | Giường nằm 40 | TOWARD_HUB | 0 |
   | 13 | Miền Đông → Cần Thơ | 180 | 210 | Ghế ngồi 50 | LEAVING_HUB | 0 |

   Có 3 loại xe trên DB thật: Limousine 22 (id 1), Giường nằm 40 (id 2), Ghế ngồi 50 (id 3).
   - `DataInitializer` (dữ liệu mẫu) cũng có đủ 3 loại xe, và đội xe mẫu cũng là 8 / 7 / 5.
   - Chỗ khác DB thật chỉ có hai: loại xe gợi ý của tuyến Miền Đông→Đà Lạt và Cần Thơ→Miền Đông (mẫu ghi Limousine); và thiếu bến Vũng Tàu cùng 4 tuyến chiều ngược (CM-13, sửa ở CT2).
   - *(Sửa 2026-11-16: bản đầu ghi "dữ liệu mẫu không có loại Ghế ngồi", sai. Grep lúc đó chỉ tìm "Limousine\|Giường".)*
   - *Sau CT2:* tuyến 10 và 11 dùng Limousine 22.
2. **Đội xe 20 chiếc:**
   - Limousine 8 (7 READY, 1 TRAVELING);
   - Giường nằm 7 (3 READY, 3 TRAVELING, 1 REPAIRING);
   - Ghế ngồi 5 (3 READY, 2 TRAVELING).

   36 tài xế.
3. **Chuyến:**
   - 21.192 chuyến do bộ sinh GĐ3 tạo, có `created_at` = 2026-10-22: 19.487 COMPLETED, 342 chuyến tăng cường COMPLETED, 1.363 CANCELLED. Khoảng thời gian 01/01/2023 → 21/10/2026.
   - 17 chuyến (chưa xoá mềm) do chủ dự án tạo từ 2026-07-16 tới 2026-09-23. Một số vẫn ở trạng thái ACTIVE hoặc DEPARTED dù giờ đi đã qua.
   - **0 chuyến tương lai.**
4. **Bản sao `busmanagement_gd2` vẫn còn** (G3-7 chưa đóng). Có thể làm mới nó để tổng duyệt CT3.

**Code (số dòng theo HEAD `cf71dd5`):**

5. **Bộ sinh** (`config/HistoricalDataBackfill.java`):
   - `eligibleRoutes()` (dòng 495) lấy **mọi** tuyến ≤ 480 phút, nên hiện nay sẽ ra 9 tuyến.
   - Mỗi xe và mỗi tài xế tối đa 1 chuyến/ngày. Khung thiếu xe bị bỏ trống (dòng 378).
   - Chạy bổ sung thì **bỏ qua cả ngày** đã có dữ liệu bộ sinh.
   - Giá vé = 1.000đ/km (dòng 674).
   - Chuyến tăng cường chạy sau chuyến gốc 30/40/50 phút (`EXTRA_MINUTES`, dòng 146).
6. **Quy luật** (`config/HistorySimulation.java`): giờ (06h 72%, 12h 58%, 18h 77% số ghế), thứ trong tuần theo chiều, tăng 4%/năm, trôi, nhảy bậc (35%/năm, ±15%), mùa, Tết và lễ nhỏ theo chiều, bão, huỷ 6%, nhiễu nhị thức âm (size 40). Mỗi tuyến tự rút ngẫu nhiên theo id tuyến (các luồng `LEVEL`, `WALK`, `JUMP`, `JITTER`).
7. **Mô hình** (`service/SlotDemandModel.java`):
   - Hệ số lễ theo (dịp lễ, ngày lệch), gộp theo nhóm chiều, cộng một phần lệch riêng của từng chuỗi, dùng chung cho mọi năm.
   - Test tham chiếu đọc file đóng băng trong `src/test/resources/forecast/`, nên sinh lại dữ liệu **không** làm đỏ các test này.
8. **Công thức** (`service/ForecastService.java`):
   - `isHot` dòng 482, `tripsNeeded` dòng 491, javadoc của lớp dòng 58;
   - số ghế xe chuẩn lấy theo `suitableBusType` (dòng 289);
   - `FORECAST_HORIZON_DAYS` = 7 (dòng 121), `PREVIEW_DAYS` = 35 (dòng 124).

   Bản đang nằm trên đĩa đã bị IDE format lại, nên số dòng khác (CM-1).
9. **Màn Đề xuất** (`service/RecommendationService.java`):
   - chỉ đọc (javadoc dòng 84, `@Transactional(readOnly = true)` dòng 129);
   - chi phí chỉ tính một chiều (`estimateCost`, dòng 407);
   - mỗi khung được giả định có đúng một xe chuẩn (`seatsBefore` bắt đầu bằng số ghế chuẩn) và **không đọc các chuyến đã có**;
   - `MAX_DAILY_DRIVING_HOURS` = 8 (dòng 115).
10. **Job 10 giây** (`service/TripService.java`):
    - `@Scheduled(fixedRate = 10_000)` ở dòng 76; `isHotTrip` dòng 99 hỏi `Trip.needsReinforcement()`;
    - `createExtraTrip` tạo chuyến +30 phút (dòng 156);
    - không bỏ qua chuyến tăng cường khi xét chuyến gốc;
    - `@EnableScheduling` ở `BusManagementApplication.java:25`.
11. **Chọn xe:** `findBestAvailableBus` (`TripService.java:308`) lấy xe READY, không bận ±1 giờ, chưa tới ngưỡng bảo trì, ưu tiên đúng loại. **Không xét vị trí.**
    - `Route` có `getDepartureStation()`/`getDestinationStation()` (dòng 115, 129), nên suy được cặp tuyến và vị trí xe mà không phải đổi schema của `Route`.
    - `Bus` và `Driver` không có trường vị trí hay bến nhà.
12. **Không có "lịch thường lệ" trong DB** (Hidden Cost #10c).
    - "Bảng điều hành" `/admin/dispatch` (`DispatchController`) là màn **chuyển trạng thái** chuyến (xuất phát, huỷ, hoàn thành), không phải màn kế hoạch.
13. **Roadmap §4 Non-Goal "No daily cron job"** (`THESIS_ROADMAP.md:53`). Dòng đó tự ghi rằng thêm bộ hẹn giờ là quyết định riêng của chủ dự án.
14. **Test liên quan tới công thức số chuyến:** `ForecastServiceRulesTest` (dòng 57–81), chú thích "⌈45 ÷ 19,8⌉" ở `WhatIfSimulationServiceTest` (dòng 39) và `RecommendationServiceTest` (dòng 80). Với 45 khách thì công thức mới vẫn ra 3 chuyến, nên các ca 45 khách không đổi kết quả.

**Phát hiện phụ chưa có trong sổ lỗi nào** (ghi ở CT0, theo CM-11):

| # | Phát hiện | Chỗ | Xử lý ở |
|---|---|---|---|
| P1 | Giờ lái chỉ cộng vào ngày khởi hành. **Đã ghi thành lỗi #34** | `TripService.getDrivingHoursForDate` ~752 | Chưa sửa. Chuyến thường lệ của 3 cặp không dính; chuyến tăng cường +30 phút trên Đà Lạt 18:00 thì dính 0,5 giờ (QĐ-26). Sửa cùng L1 |
| P2 | Mức 8 giờ bị dùng như "mỗi chuyến" (⌈thời gian ÷ 8⌉ tài xế, min(phần chia, 8)). **Không phải lỗi:** roadmap §9 gọi đây là "per-trip share cap" | `TripService` ~236, ~497 | Xem lại khi làm cặp dài (QĐ-27) |
| P3 | Màn Đề xuất chỉ tính chi phí một chiều | `RecommendationService.java:407` | CT7 |
| P4 | Màn Đề xuất không trừ chuyến đã có; chuyến tạo tay không gắn với chuyến gốc, nên job vẫn thêm được | `RecommendationService`; `AdminTripManagementController.java:178` | CT7, CT8 |
| P5 | Job không bỏ qua chuyến tăng cường, nên tăng cường có thể nối chuỗi +30 phút | `TripService.scanAndSuggestExtraTrips` dòng 76 | CT8 |
| P6 | Tài xế chỉ bận ±30 phút quanh chuyến và không có vị trí | `TripService` | CT5, CT9 |
| P7 | `tripsNeeded` chia cho 0,9 × ghế (quyết định đã hết hợp, không phải lỗi) | `ForecastService.java:491` | CT1 |
| P8 | Hai chiều MĐ↔NN gợi ý hai loại xe khác nhau (22 / 40) | tuyến 1, 4 | Chỉ ghi lại; hai tuyến này ngoài phạm vi (QĐ-19) |

---

## 4. Việc của từng giai đoạn

> **Quy tắc chung cho mọi giai đoạn:**
> - Bắt đầu bằng một **vòng thảo luận ngắn**: đối chiếu với code, chốt các mục CM của giai đoạn, ghi vào `route_pairs_decisions.md`. Sau đó mới làm.
> - Mỗi việc xong thì đánh `[x]`, kèm ngày và chỗ kiểm chứng (lệnh, số đo, file:dòng).
> - Mỗi giai đoạn xong thì app vẫn chạy được.
> - Kiểm chứng bằng `mvnw test` **và** chạy app thật (skill `verify`).
> - Chủ dự án tự test rồi mới commit.

### CT0 — Khung theo dõi, ghi quyết định, dọn việc tồn (chỉ tài liệu)

**Mục tiêu:** có kế hoạch được duyệt và một chỗ duy nhất ghi quyết định, trước khi đụng vào code.

- [x] Tạo `route_pairs_decisions.md` (QĐ-18…QĐ-27, CM-1…CM-15). 2026-11-16.
- [x] Tạo file này. 2026-11-16.
- [x] `THESIS_ROADMAP.md`: thêm một mục ở §7 trỏ về đây, một mục ở §8. 2026-11-16.
- [x] Chủ dự án duyệt kế hoạch. 2026-11-16 (*"oke. chốt hãy thực hiện đi"*).
- [x] **CM-1:** bỏ diff format của `ForecastService.java`. 2026-11-16.
  - Đã lưu bản sao ra scratchpad của phiên, rồi `git checkout -- <file>`.
  - Sau đó file giống hệt HEAD: `tripsNeeded` ở dòng 491, đúng số mà bài 14 trích (`FS:491-496`).
- [x] **CM-11:** ghi vào `docs/todo/current_bugs_found.md`, mục "Ghi khi lập kế hoạch đợt cặp tuyến (2026-11-16)". 2026-11-16.
  - Đã grep cả `project_report.md`: không mục nào trùng.
  - **P1 thành lỗi #34 (sai sót thật).** SQL trên DB thật đếm được 41 chuyến lái qua nửa đêm: 37 chuyến tăng cường Đà Lạt 18:30 (mỗi chuyến 0,5 giờ rơi sang ngày sau); chuyến #4 (3,88 giờ); chuyến #14 (17,48 giờ).
  - **P2 không phải lỗi:** roadmap §9 gọi đây là "per-trip share cap".
  - **P3, P7:** quyết định đã hết hợp. **P4, P5, P6:** có hướng sửa ở CT5–CT9. **P8:** dữ liệu ngoài phạm vi.
  - **Sửa một câu sai ở QĐ-26:** bản đầu ghi "lỗi giờ lái theo ngày khởi hành không phát sinh". Thật ra nó vẫn phát sinh với chuyến tăng cường +30 phút của job trên tuyến Đà Lạt.
- [x] Commit `docs:` khi chủ dự án bảo. 2026-11-17: commit `docs:` ngay sau `ed38ce6`, đã push.

**Xong khi:** kế hoạch được duyệt, CM-1 và CM-11 đã chốt.

### CT1 — Công thức số chuyến (QĐ-18)

**Mục tiêu:** khung 40 khách / xe 22 chỗ cần 2 chuyến, không phải 3. Định nghĩa "nóng" giữ nguyên.

**Cần trước:** CM-1 (vì CT1 sửa đúng file `ForecastService.java`).

- [x] `ForecastService.tripsNeeded` thành `Math.max(2, (int) Math.ceil(demand / standardSeats))` khi nóng (dòng 495). 2026-11-16.
  - Đã sửa javadoc của lớp (dòng 56–58), của hàm (dòng 486–490), và tiêu đề khối (dòng 478).
  - **Số dòng giữ nguyên** (883 dòng như HEAD), để không lệch các trích dẫn trong bài học.
  - Hằng `REINFORCEMENT_THRESHOLD` vẫn dùng cho `isHot`.
- [x] Các chỗ khác nêu công thức cũ, cũng giữ nguyên số dòng. 2026-11-16.
  - Javadoc `ForecastPointDto.tripsNeeded` (dòng 46).
  - Chú thích trên màn Dự báo (`demand-forecast.html:200`): *"khi đó số chuyến cần = ⌈nhu cầu ÷ ghế xe chuẩn⌉, ít nhất 2"*.
  - `WhatIfSimulationService` và `RecommendationService` không nêu công thức, nên không phải sửa.
- [x] `ForecastServiceRulesTest`. 2026-11-16.
  - Phép quét 0–300 khách × xe 22/40/50 chỗ giờ so với công thức mới.
  - Chú thích về cái bẫy làm tròn viết lại cho công thức mới.
  - Các ca GĐ5 không đổi kết quả.
  - Thêm 5 ca QĐ-18: 40/22 → 2, 44/22 → 2, 44,01/22 → 3, 21/22 → 2, 45/22 → 3.
- [x] `RecommendationServiceTest.scarceBus_goesToTheMostProfitableExtraTrip_notTheEarliest` **đỏ ở lần chạy đầu, đúng như dự kiến**. 2026-11-16.
  - Khung 07:00 có khoảng 40 khách / 22 chỗ: công thức cũ ra 3 chuyến, mới ra 2. Tổng thẻ 21 → 14, thẻ lỗ 14 → 7.
  - Ý của test (xe khan hiếm về chuyến lãi nhất, không phải chuyến sớm nhất) vẫn kiểm được. Đã sửa số và chú thích.
  - Chú thích "⌈45 ÷ 19,8⌉" ở test này và `WhatIfSimulationServiceTest` đổi thành "⌈45 ÷ 22⌉" (vẫn ra 3).
- [x] `mvnw test`: **217/217**, 0 lỗi. 2026-11-16.
  - **Thử làm hỏng:** đặt lại mẫu số `0,9 × ghế` thì đúng 2 test đỏ: `reinforcementFlag_andTripsNeeded_areOneDefinition` và `scarceBus_…`. Sau đó đã khôi phục.
- [x] `verify` trên DB thật, chỉ đọc. 2026-11-16. Chạy cùng app ở cổng 8099, trước khi sửa (PID 27560) và sau khi sửa (PID 8228).
  - **Ở nhu cầu 100%, mọi con số giống hệt:** 31 khung nóng, 31 thẻ, 3 lãi / 28 lỗ, What-if 31 chuyến thêm.
    - Lý do: hôm nay không khung nào cần ≥ 3 chuyến theo công thức cũ. Cả 31 khung đều "cần 2 chuyến".
    - Số khung nóng không đổi (đúng yêu cầu), số thẻ không tăng.
  - **What-if khi tăng nhu cầu:** 130%: 55 → 54 chuyến thêm; 150%: 63 → 62; 200%: 103 → 85.
  - Màn Dự báo hiện chú thích mới. Log không có `TemplateProcessingException`.
  - Số dòng 6 bảng và MD5 của `trips`, `buses` giống hệt trước và sau.
- [x] Tài liệu. 2026-11-16.
  - `forecast_decisions.md`: dòng QĐ-16 ở mục 1, G5-6, thêm dòng lịch sử.
  - `current_functional_spec.md`, `Proj_functions_summary.md`, `02_project_context.md`, `docs/testing/test_case.md`.
  - Roadmap §5 Phase 6 (Output); §9 ghi chú GĐ5 được đánh dấu "superseded in part", không viết lại.
  - Bài 14: mục 3 tóm tắt, ví dụ 39,9 khách (3 → 2), bảng 8.4 (thêm dòng 40 khách), cái bẫy, 8.7, bài tập (a). Build lại HTML bằng `_build/build.mjs`; diff HTML chỉ chạm đúng các đoạn đã sửa.
  - **Giữ nguyên, vì là bản ghi lịch sử:** các mục nhật ký roadmap §7/§8 của GĐ5, `forecast_progress.md` sự thật 1, M-4.

**Xong khi:** test xanh; ba màn hiển thị đúng công thức mới; tài liệu khớp code.

### CT2 — Cấu hình tuyến, loại xe và dữ liệu mẫu (QĐ-19, QĐ-20)

**Mục tiêu:** DB thật có đúng 3 cặp với loại xe đã chốt, trước khi sinh dữ liệu. Lý do: bộ sinh tính nhu cầu theo số ghế xe chuẩn của tuyến.

**Cần chốt:** CM-13.

- [x] **Sao lưu** DB thật trước khi ghi: `E:\Y4 S2\PRETHESIS\ProjectBackup\db\busmanagement_2026-11-16_truoc-CT2.sql` (3,7 MB, kết thúc bằng "Dump completed"). 2026-11-16.
- [x] **Đổi loại xe gợi ý của tuyến 10 và 11 sang Limousine 22** qua chính form tuyến. 2026-11-16.
  - App ở cổng 8099, PID 15052. Gửi `POST /admin/routes/edit/{id}` với đủ giá trị cũ, chỉ đổi `suitableBusType=1`; mỗi lần một phiên cookie riêng.
  - Cả hai trả 302 kèm "Cập nhật tuyến đường thành công!".
  - Diff bảng `routes` + `route_stations` trước/sau: chỉ `suitable_bus_type_id` của tuyến 10, 11 đổi từ 3 sang 1. Km, thời gian, hướng dòng khách, lộ trình giữ nguyên.
  - Số dòng 6 bảng và MD5 của `trips`, `buses` giống hệt đầu phiên. Log không có lỗi template.
- [x] **SQL chỉ đọc kiểm 3 cặp.** 2026-11-16. Cặp 2/12, 10/11, 13/5 đều:
  - đảo đúng bến;
  - cùng km, cùng thời gian, cùng loại xe (Giường nằm 40 / Limousine 22 / Ghế ngồi 50);
  - hướng dòng khách `LEAVING_HUB/TOWARD_HUB`;
  - mỗi tuyến đúng 2 bến.
- [x] **CM-13: sửa `DataInitializer`** cho khớp DB thật. 2026-11-16.
  - Thêm bến Vũng Tàu (dòng 173).
  - Đổi loại xe gợi ý: Miền Đông→Đà Lạt sang Giường nằm, Cần Thơ→Miền Đông sang Ghế ngồi.
  - Thêm 4 tuyến chiều ngược (dòng 196–202), đúng thứ tự tạo trên DB thật.
  - **Giữ nguyên 6 tuyến cũ**, đúng QĐ-19 ("không xoá tuyến cũ"). Các chuyến mẫu không đổi. Không test nào dùng profile `demo`.
- [x] **Chạy thử dữ liệu mẫu an toàn.** 2026-11-16.
  - Dùng một lớp test tạm với `@ActiveProfiles("demo")`. Cấu hình test luôn đè datasource sang `busmanagement_test`, nên không thể chạm DB thật.
  - Kết quả: 8 bến, 10 tuyến, 3 cặp quanh Miền Đông khớp đủ các điều kiện trên; 1/1 test xanh.
  - DB thật trước/sau vẫn 21.211 chuyến, 12 tuyến.
  - **Đã xoá lớp test tạm** (cùng báo cáo surefire của nó). Không giữ lâu dài, vì seed ghi vào DB test sẽ làm sai các test khác.
- [x] `mvnw test` **217/217** sau khi xoá lớp tạm. 2026-11-16.
- [x] **Tài liệu.** 2026-11-16.
  - `setup_guide.md` và `database_schema.md` chỉ mô tả seed chung chung, không phải sửa.
  - `test_case.md`: TC_STA_001 đổi 7 → 8 bến; ca seed đổi 7 bến / 6 tuyến → 8 / 10.
  - `Proj_functions_summary.md` §4.
  - **Sửa luôn các trích dẫn đã lệch từ GĐ1 (`79ba96c`) mà GĐ7 rà sót:** bài 02 (`:166-173`, 8 bến, DB thật 12 bến), bài 03 (`:175-202`, `:249-262`, `:256`, `:258-259`, `:194`, 10 tuyến), bài 04 (`:304-314`), bài 05 (`:346`). Build lại HTML bài 02–05; diff HTML chỉ chạm đúng các dòng đã sửa.

**Xong khi:** 6 tuyến trên DB thật đúng QĐ-19, QĐ-20; seed khớp (nếu CM-13 chọn sửa).

### CT5a — Định nghĩa vị trí và bến nhà (QĐ-23; tách ra ngày 2026-11-17)

**Mục tiêu:** luật "xe hay tài xế đang ở bến nào" được viết **một lần**, để bộ sinh (CT3), script kiểm (PART C) và app (CT5b) cùng dùng. Mỗi xe, mỗi tài xế có bến nhà. **Hành vi chọn xe của app chưa đổi.**

**Cần trước:** CT2. **Đã chốt 2026-11-17:** CM-5 (cách B: cứng cho máy, mềm cho người, áp cả tài xế), CM-12 (cách (a): cột riêng trên xe và tài xế).

- [x] **Bến nhà:** cột `home_station_id` trên `buses` và `drivers`. Hibernate tự thêm khi khởi động (`ddl-auto=update`), cho phép null, có khoá ngoại tới `stations`. 2026-11-17.
  - Field `Bus.homeStation` và `Driver.homeStation` (LAZY, `@ToString.Exclude`) đặt **ở cuối lớp**. Với `Driver`, tên annotation viết đầy đủ để không thêm dòng import. Nhờ vậy không dòng nào đang được trích bị xê dịch (tiền lệ `Route.holidayFlow`).
  - **Bắt buộc:**
    - `BusService.validate()` gọi `requireHomeStation()`, nên áp cho cả tạo lẫn sửa; `updateBus()` chép trường ngay trước `validate()`.
    - `DriverService.createDriver()` kiểm **trước mọi lần ghi**; `updateDriver()` chép qua `requireHomeStation()`.
  - **Form:** ô "Bến Nhà" `required` ở cuối form xe và form tài xế. Danh sách bến lấy từ `@ModelAttribute("stations")` ở cuối `AdminBusController` / `AdminDriverController`, tiêm `StationService`, vẫn đúng mẫu "chỉ tiêm Service".
  - **Danh sách:** thêm cột "Bến Nhà" ngay trước cột "Hành Động".
  - **Phát hiện khi kiểm:** `th:field` làm mất `selected` của dòng nhắc, nên xe mới âm thầm nhận bến đầu danh sách (Mỹ Đình). Đã sửa ô bến nhà bằng `name` + `th:selected`. Ô loại xe có sẵn cùng lỗi, đã ghi vào sổ lỗi (mục nhỏ 2026-11-17), chưa sửa.
- [x] **Luật vị trí viết ở đúng một chỗ:** `domain/LocationRule`. 2026-11-17.
  - Gồm `Leg`, `legOf(Trip)`, `lastLegArrivedBy`, `stationAt`, và `canDepart` (hai bản: nhận cả danh sách lượt, hoặc nhận sẵn lượt gần nhất cho bộ sinh).
  - Thời gian nghỉ do nơi gọi truyền vào, không khai lại hằng số của `TripService`.
  - **Chưa** gắn vào `findBestAvailableBus` hay `findBestAvailableDriver`.
- [x] **Test**, 2026-11-17:
  - `LocationRuleTest`: 6 test.
  - `BusServiceTest` +3 (tạo thiếu bến nhà, sửa thiếu bến nhà, đổi bến nhà); helper form có bến nhà.
  - `DriverServiceHomeStationTest`: 4 test, mới.
  - Test "sửa thiếu bến nhà" của xe lần đầu đỏ, vì đọc bản trong bộ nhớ của chính transaction. Đã sửa để đọc lại từ DB (`flush` + `clear`), đúng điều app thật đảm bảo (transaction bị huỷ).
  - **Thử làm hỏng:** bỏ kiểm ở `validate()`, bỏ kiểm ở `createDriver()`, bỏ điều kiện "nghỉ đủ" trong `canDepart` thì đúng 4 test mong đợi đỏ. Đã khôi phục.
- [x] **Ghi DB thật**, 2026-11-17.
  - Sao lưu `ProjectBackup\db\busmanagement_2026-11-17_truoc-CT5a.sql` (3,7 MB, đầy đủ).
  - App mới tự thêm 2 cột; sau đó `UPDATE … SET home_station_id = 3` đổi đúng 20 xe và 36 tài xế.
  - So với ảnh chụp trước CT5a: số dòng 7 bảng và MD5 của `trips`, `buses` (trừ cột mới), `drivers` **giống hệt**; chỉ có thêm 2 cột.
- [x] **`verify` chỉ đọc trên DB thật** (cổng 8099, PID 10300), 2026-11-17:
  - Danh sách xe và tài xế hiện "Bến xe Miền Đông" 20 và 36 lần.
  - Form sửa chọn sẵn đúng một bến. Form tạo hiện dòng nhắc có `selected`.
  - **Việc chọn xe y như trước:** so với mốc chụp lúc code chưa đổi (PID 27848), giống hệt từng byte ở danh sách xe / tài xế khả dụng của hai khung giờ, màn Đề xuất, What-if, ô chọn xe của màn Duyệt (chuyến #8). Màn Dự báo chỉ khác dòng "Tính lúc … trong … ms".
  - Log không có `TemplateProcessingException` hay `LazyInitializationException`.
- [x] **`verify` có ghi, trên bản sao** (`busmanagement_test` sao từ DB thật, khớp số dòng và MD5; cổng 8098, PID 21796, 10 kết nối, tất cả vào bản sao), 2026-11-17:
  - xe: sửa sang Đà Lạt → lưu; sửa thiếu bến nhà → từ chối, giữ Đà Lạt; tạo thiếu → từ chối, số xe không đổi; tạo có → lưu;
  - tài xế: sửa sang Cần Thơ → lưu, các trường khác giống DB thật tới MD5; sửa thiếu → từ chối, giữ Cần Thơ; tạo thiếu → từ chối, **không** sinh tài khoản (37 → 37); tạo có → lưu.
- [x] **Tài liệu**, 2026-11-17:
  - `database_schema.md` (2 cột, quan hệ, sơ đồ ER), spec, summary, `test_case.md` (TC_BUS_012, TC_BUS_013), sổ lỗi (mục nhỏ ô loại xe).
  - Bài 01 và 04: bảng thành phần, mục điều cốt lõi.
  - **Trỏ lại 72 trích dẫn số dòng** bằng script so dòng với HEAD, cộng 6 chỗ sửa tay. Ba ghi chép có ngày ở `Proj_functions_summary.md` (dòng 332, 342, 343, "kiểm 2026-09-22") **cố ý giữ nguyên**, vì đó là số của code ngày ấy.
  - Build lại HTML bài 00a, 00b, 01, 03, 04, 05.

**Xong khi:** mọi xe và tài xế có bến nhà; luật vị trí có test; app chọn xe y như cũ.

### CT3 — Bộ sinh mới, chạy trên bản sao (QĐ-21)

**Mục tiêu:** lịch sử 3 cặp Sài Gòn có quy luật cặp, khách về sau lễ ±15%, và xe chạy theo vòng. Kiểm chứng đầy đủ trên bản sao trước khi đụng DB thật.

**Cần trước:** CT2, CT5a. **Đã chốt 2026-11-17:** CM-2 (H1 "về ngay", QĐ-21 (d)), CM-3 (tâm 100%, QĐ-21 (b)). Bến nhà và luật vị trí lấy từ CT5a.

- [ ] **Danh sách tuyến:** thêm thuộc tính kiểu `backfill.route-ids` thay cho việc lọc ≤ 480 phút ở `eligibleRoutes()`. Vẫn từ chối tuyến > 8 giờ.
- [ ] **`HistorySimulation`, chung mức theo cặp:** các luồng `LEVEL`, `WALK`, `JUMP`, `JITTER` rút theo khoá cặp. Hình dạng theo chiều (`WEEK`, `addTet`, `addSmallHoliday`) giữ riêng.
- [ ] **Khách về sau lễ:** chuẩn hoá đợt về sao cho tổng phần tăng thêm = tổng phần tăng thêm đợt đi × r, với r đều trong [0,85; 1,15]. Dùng **luồng ngẫu nhiên mới**, để không xê dịch số của các luồng cũ.
- [ ] **`HistoricalDataBackfill.planDay`, xe chạy vòng:**
  - theo dõi vị trí và giờ rảnh của từng xe, từng tài xế;
  - xếp chuyến theo giờ đi;
  - chọn xe đang ở đúng bến, đã nghỉ ≥ 1 giờ, ưu tiên đúng loại;
  - chọn tài xế ở đúng bến, đã nghỉ ≥ 30 phút, lái ≤ 8 giờ trong ngày lịch;
  - khung thiếu xe vẫn bỏ trống và được đếm;
  - chuyến tăng cường lấy từ xe rảnh tại bến;
  - xe tăng cường "về ngay" theo QĐ-21 (d): đi cùng chuyến thường lệ chiều về sớm nhất mà xe và tài xế kịp, chở khách tràn của khung đó (thường 0 vé). Cần xe ở bến xa thì gửi xe từ bến nhà lên trước theo cách tương tự;
  - ngày đầu lịch sử, mọi xe và tài xế ở **bến nhà đọc từ DB** (CT5a);
  - mọi phép "xe hay tài xế đang ở đâu, chạy tiếp được chưa" gọi **luật vị trí của CT5a**, không viết lại.
- [ ] **Chuyến có sẵn của chủ dự án** (#33): vẫn giữ xe và người của chuyến đó trong ngày nó chạy. Chốt cách cập nhật vị trí sau các chuyến ấy (chúng nằm trên tuyến ngoài phạm vi).
- [ ] Viết lại chú thích đầu hai lớp cho đúng bất biến mới.
- [ ] **Test:**
  - `HistorySimulationTest`: chung mức theo cặp, r trong khoảng, tính tất định;
  - `HistoricalDataBackfillPlanTest`: vị trí liên tục, ≤ 8 giờ/ngày, nghỉ ≥ 1 giờ / 30 phút, không trùng lịch;
  - `HistoricalDataBackfillGenerateTest`.
- [ ] **Script** (thư mục mới `docs/development/route_pairs_research/`):
  - PART A: đếm dữ liệu sắp xoá;
  - PART B: xoá lịch sử bộ sinh GĐ3. Xoá chuyến tăng cường trước, vì chúng tham chiếu `original_trip_id`. Trừ km khỏi **cả** `odometer` lẫn `last_maintenance_odometer`, như QĐ-14;
  - PART C: kiểm các bất biến mới, kể cả vị trí liên tục.
- [ ] **Tổng duyệt trên bản sao:** làm mới `busmanagement_gd2` (hoặc tạo bản sao mới) từ một bản sao lưu mới, rồi chạy A → B → bộ sinh → C. Xuất file nhu cầu thật.
- [ ] **Đo trên bản sao:**
  - tự chấm của màn Dự báo (mốc hiện tại 16,06%);
  - số khung nóng, tỉ lệ khung bán hết, số khung thiếu xe, số chuyến tăng cường;
  - màn Dự báo chỉ còn 18 chuỗi (6 tuyến × 3 giờ).
- [ ] `mvnw test`.
- [ ] Tài liệu: ghi chú ở `forecast_decisions.md` QĐ-10 (phần bị thay), README của thư mục script mới.

**Xong khi:** bản sao có lịch sử mới, PART C báo 0 vi phạm, các số đo được ghi lại, test xanh. Code commit khi chủ dự án bảo, vì CT4 cần code đã commit.

### CT4 — Thay lịch sử trên DB thật (QĐ-21; làm như GĐ3/QĐ-14)

**Mục tiêu:** DB thật mang lịch sử mới, giống hệt bản tổng duyệt tới từng chuyến.

**Điều kiện trước khi chạy:** code CT3 đã commit; app đã tắt; DB thật vẫn giống lúc sao lưu; **chủ dự án xác nhận lần cuối**.

- [ ] Sao lưu bằng `mysqldump` ra `E:\Y4 S2\PRETHESIS\ProjectBackup\db\`. Khôi phục thử vào một bản sao và so số dòng + `CHECKSUM TABLE`.
- [ ] Chủ dự án xác nhận lần cuối.
- [ ] Chạy PART A → PART B → bộ sinh → PART C trên DB thật. So từng chuyến với bản tổng duyệt.
- [ ] Để file nhu cầu thật cạnh file sao lưu, ngoài git.
- [ ] `verify`:
  - các trang chính trả 200;
  - màn Dự báo, Đề xuất, What-if chỉ còn 6 tuyến;
  - ghi lại số liệu.
- [ ] G3-7: xoá bản sao cũ khi chủ dự án đồng ý.
- [ ] Ghi kết quả vào file này và roadmap §7, §8.

**Xong khi:** PART C báo 0 vi phạm trên DB thật, kết quả giống tổng duyệt, các trang chạy.

### CT5b — Bật vị trí vào việc chọn xe và tài xế (QĐ-23)

**Mục tiêu:** khi máy chọn xe hay tài xế, nó chỉ chọn người và xe đang ở bến xuất phát.

**Cần trước:** CT4 (lịch sử mới, vị trí có nghĩa). Bến nhà, luật vị trí, CM-5 và CM-12 đã có từ CT5a.

- [ ] **AI:** thêm bộ lọc "đang ở bến xuất phát" vào `findBestAvailableBus` và `findBestAvailableDriver`, ở cả bản truy vấn DB lẫn bản dùng `AvailabilityContext`.
  - Nạp sẵn chuyến gần nhất của mỗi xe trước khoảng thời gian xét.
  - Có test chứng minh hai bản cho cùng kết quả.
- [ ] **Luồng thủ công** (dropdown Duyệt / Sửa / Tạo): làm theo CM-5 (đề xuất: xe cùng bến lên đầu, kèm ghi chú). `validateBusForTrip` giữ nguyên, trừ khi CM-5 chọn áp cứng.
- [ ] **Test:** test mới cho vị trí; `TripServiceAvailabilityContextTest`; các test chọn xe hiện có. Chạy `mvnw test`.
- [ ] `verify`.
- [ ] **Tài liệu:** spec, summary, `database_schema.md`, bài 06 (luật nghiệp vụ), `test_case.md`.

**Lưu ý:** roadmap §3 yêu cầu sửa `TripService` ít nhất có thể. Vị trí là một bộ lọc mới chen vào dòng ứng viên sẵn có, đúng mẫu "luật và khả dụng đan xen". Không dựng thêm tầng service song song.

**Xong khi:** máy không còn chọn xe hay tài xế đang ở bến khác; test xanh; DB thật có bến nhà.

### CT6 — Cặp tuyến và lịch thường lệ (QĐ-22; Hidden Cost #10c)

**Mục tiêu:** app biết tuyến nào đi cặp với tuyến nào, có lịch 06/12/18, và tạo được chuyến thường lệ tương lai theo vòng xe.

**Cần chốt:** CM-4, CM-6, CM-14.

- [ ] **Suy cặp:** viết hàm tìm tuyến ngược trong `RouteService` (đảo bến đầu/cuối, phải có đúng một tuyến khớp), kèm test.
- [ ] **Nguồn giờ chạy thường lệ** theo CM-4, dùng chung cho bộ sinh và app. Bỏ hằng số trùng lặp.
- [ ] **Tạo chuyến thường lệ cho N ngày tới:**
  - xếp theo vòng, xe và tài xế ở đúng bến (CT5);
  - đi qua cổng `validateBusForTrip` / `validateStaffForTrip`;
  - không tạo trùng (khung đã có chuyến thường lệ thì bỏ qua);
  - trạng thái theo CM-14;
  - kích hoạt bằng nút cho Admin.
- [ ] Controller mới theo mẫu `AdminBusController` (chỉ tiêm Service).
- [ ] Test, `verify` (tạo lịch trên DB thật cho một tuần, có sao lưu).
- [ ] Tài liệu; roadmap §6 Hidden Cost #10c.

**Xong khi:** DB thật có chuyến thường lệ tương lai cho 3 cặp, mỗi xe chạy đúng vòng.

### CT7 — Màn "Kế hoạch cặp tuyến" (M2), lãi cả vòng (QĐ-22)

**Mục tiêu:** nhìn vào là thấy chiều nào được tăng cường, xe lấy từ đâu, đi rồi về thế nào, và lãi cả vòng.

**Cần chốt:** CM-7, CM-9.

- [ ] **Lãi cả vòng:** tính chi phí chiều về và tiền vé của khách tràn chiều về (dùng cùng `extraTripPassengers`). Không còn để `estimateCost` chỉ tính một chiều (P3).
- [ ] **Màn Đề xuất đếm chuyến đã có trong khung** (thường lệ, tăng cường, tạo tay): `seatsBefore` = tổng ghế đã có, và chỉ đề xuất phần còn thiếu (P4).
- [ ] **Màn mới theo CM-9:**
  - mỗi cặp một bảng, hai chiều cạnh nhau;
  - mỗi xe một hàng theo vòng;
  - chuyến thêm có dấu ★, kèm nguồn xe, lý do, lãi cả vòng;
  - dòng tóm tắt: km rỗng, số xe dùng.
- [ ] Màn Dự báo gom chuỗi theo cặp (phần M1).
- [ ] What-if theo CM-7.
- [ ] **Test:** `RecommendationServiceTest`, `WhatIfSimulationServiceTest`, `WhatIfCoverageTest`, cùng test mới. Chạy `verify`.
- [ ] **Tài liệu:** spec, summary, bài 14 (phần Đề xuất).

**Xong khi:** màn mới chạy trên DB thật; số liệu khớp phép tính tay ở một vài thẻ.

### CT8 — Tự tạo chuyến chờ duyệt mỗi đêm; sửa job 10 giây (QĐ-24)

**Mục tiêu:** dự báo tự đề xuất bằng chuyến chờ duyệt thật; job 10 giây chỉ còn lo phần bất ngờ.

**Cần chốt:** CM-10, CM-15. **Cần chủ dự án duyệt** việc sửa roadmap §4.

- [ ] Sửa câu chữ roadmap §4 "No daily cron job" (có chủ dự án duyệt). Ghi chú QĐ-17 và Phase 7 rằng "Đề xuất chỉ đọc" đã được QĐ-24 thay.
- [ ] **Job đêm** (`@Scheduled(cron = …)`) gọi một service mới:
  - lấy các chuyến thêm có lãi cả vòng > 0 cho D+3…D+7;
  - trừ đi chuyến đã có trong khung;
  - tạo chuyến `PENDING_APPROVAL` gắn với chuyến thường lệ của khung (`originalTrip`);
  - tự phân công qua đường có sẵn trong `TripService`.
- [ ] Khi dự báo giảm: tự rút chuyến tự tạo còn chờ duyệt.
- [ ] **Job 10 giây:** so vé thật với tổng ghế của cả khung; không lấy chuyến tăng cường làm chuyến gốc (P5). Giữ nguyên các điều kiện 72 giờ, 48 giờ, 95%.
- [ ] Nối việc tạo chuyến thường lệ (CT6) vào job đêm, nếu CM-14 chọn vậy.
- [ ] **Test:** `TripServiceScannerHotGateTest` cùng test mới. `verify`, lưu ý khi bật app thì cả job thật cũng chạy.
- [ ] **Tài liệu:** spec, summary, `trip_lifecycle_fsm.md` nếu luồng trạng thái đổi.

**Xong khi:** sau một đêm (hoặc một lần chạy tay), DB có đúng các chuyến chờ duyệt mong đợi, không trùng; job 10 giây không còn nối chuỗi.

### CT9 — Luật giữ–về, tiền chờ, kíp lái ở lại cùng xe (QĐ-25, QĐ-26)

**Mục tiêu:** xe tăng cường ở bến xa được giữ hay cho về theo đúng một phép so; kíp lái ở lại cùng xe.

**Cần chốt:** CM-8.

- [ ] **`CostParameters`:** thêm lương chờ (người/ngày) và phí xe nằm bến (ngày). Sửa form `cost-parameters.html`, `CostParameterService` (giá trị mặc định), `CostParameterServiceTest`.
- [ ] **Service quyết định giữ/về** cho từng xe tăng cường đang ở bến xa:
  - dự báo 35 ngày;
  - lợi ích = min(tiền vé chiều về kỳ vọng, 2 × chi phí một chiều), so với tiền chờ còn lại;
  - các bước gọi về / bỏ chuyến tăng cường;
  - xe tới trước về trước;
  - kiểm lại mỗi đêm (job của CT8).
- [ ] Giữ kíp lái ở bến xa cùng xe: tài xế bận tới khi xe về (P6).
- [ ] Màn "Kế hoạch cặp tuyến" hiện xe đang được giữ, lý do, ngày dự kiến về, và phép so tiền.
- [ ] **Cho luận văn:** mô phỏng "về ngay" so với "giữ–về" trên một dịp lễ trong lịch sử. So km rỗng, số xe phải dùng, lãi.
- [ ] Test, `verify`, tài liệu.

**Xong khi:** quyết định giữ/về của từng xe khớp phép tính tay trên vài ca; có bảng mô phỏng cho luận văn.

### CT10 — Tài liệu, bài học, chuẩn bị bảo vệ

- [ ] Rà mọi tài liệu đang tracked theo code, như GĐ7:
  - roadmap §5, §6 (#10c), §7, §9;
  - spec, summary, `02_project_context.md`, `database_schema.md`, `setup_guide.md`, `test_case.md`;
  - `docs/ai`, `README.md`;
  - hai sổ lỗi.
- [ ] Bài học: cập nhật số liệu bài 14 sau khi sinh lại (NB size, các ví dụ); bài mới về cặp tuyến và điều độ nếu chủ dự án muốn. Build HTML.
- [ ] Ghi phần giới hạn và hướng phát triển: cặp dài + L1, M3, tăng tần suất B1/B2, tối ưu kíp lái K2.

---

## 5. Nhật ký phiên

| Ngày | Phiên | Việc | Kết quả |
|---|---|---|---|
| 2026-11-10 → 13 | `62ada296`, `66af0a5d`, `e84dbb86` | Thảo luận: 3 cặp tuyến, công thức số chuyến, tài xế tuyến dài, job và dự báo, ý thầy về vòng đi–về (M1/M2/M3), tuyến dài, luật giữ–về | Chỉ thảo luận. Chốt "tăng xe = thêm chuyến cùng khung" |
| 2026-11-16 | `d7e11adb` | Chốt các mục A và B; kiểm DB thật (4 tuyến mới); rà code, test, tài liệu; lập kế hoạch CT0–CT10 | Tạo `route_pairs_decisions.md` và file này; thêm mục roadmap §7, §8. Không đổi code. Chưa commit |
| 2026-11-16 (sau) | `d7e11adb` | Chủ dự án duyệt kế hoạch. Làm CT0 (CM-1, CM-11) và CT1 | Bỏ diff format; ghi lỗi #34 và 7 mục; sửa câu sai ở QĐ-26. CT1: code, test 217/217, thử làm hỏng, `verify` (DB giống hệt), tài liệu, bài 14. **Chưa commit**, chờ chủ dự án test |
| 2026-11-16 (sau nữa) | `d7e11adb` | *"làm CT02 luôn đi"*: làm CT2 | Sao lưu; tuyến 10, 11 sang Limousine 22 qua form (chỉ cột đó đổi); 3 cặp kiểm bằng SQL; `DataInitializer` khớp DB thật, chạy thử trên DB test; test 217/217; sửa trích dẫn lệch ở bài 02–05. **Chưa commit** |
| 2026-11-17 | `d7e11adb` | *"oke, hãy commit và push đi"* | `verify` lại trước khi commit (11 trang 200). Commit `963aa71` (CT1), `bbedfd8` (CT2), `ed38ce6` (bài học), rồi commit `docs:`; push lên `origin/temp` |
| 2026-11-17 (sau) | `d7e11adb` | Giải thích CM-2, CM-3. Chủ dự án hỏi *"sao k thêm vị trí rồi làm bộ sinh"*; đo trên DB thật: 11/20 xe sẽ "kẹt" ở bến ngoài 3 cặp | Chốt CM-2, CM-3; tách CT5 thành CT5a / CT5b. Chỉ sửa tài liệu, chưa commit |
| 2026-11-17 (CT5a) | `d7e11adb` | *"chốt CM-5 cách B, CM-12 cách (a). xong thì hãy kiểm tra lại 1 lần nữa, ổn và ok thì commit và push"* | CT5a: luật vị trí, bến nhà (code, form, danh sách), test (thử làm hỏng ok), sao lưu + đặt bến nhà trên DB thật, `verify` chỉ đọc (chọn xe giống hệt) và có ghi (bản sao), tài liệu + trỏ lại 78 trích dẫn. Kiểm lại lần cuối (test 230/230, 16 trang 200, chọn xe giống hệt), rồi commit `4c3ce51`, `d39c17e`, commit `docs:` và push |

---

## 6. Quy tắc làm việc cho đợt này

- **Mỗi giai đoạn đi theo vòng:** thảo luận ngắn và chốt CM → ghi `route_pairs_decisions.md` → làm → `mvnw test` + `verify` → cập nhật file này và roadmap → chủ dự án tự test → **chỉ commit khi chủ dự án bảo**.
  - Commit không mang trailer Co-Authored-By.
  - Tách commit theo loại: feature / fix / docs / refactor.
- **Ghi vào DB thật** (CT2, CT4, CT5, CT6, CT8, CT9) luôn phải có sao lưu trước. Thay lịch sử (CT4) còn cần chủ dự án xác nhận lần cuối.
- **Khi bật app:** job 10 giây (`TripService.java:76`) chạy thật. Từ CT8 có thêm job đêm.
- **Quyết định mới hay thay đổi quyết định:** ghi vào `route_pairs_decisions.md` mục 5. Không đổi im lặng trong code.
- **Câu hỏi không phải lệnh làm.** "Nếu làm X thì sao?" nghĩa là giải thích X, không phải bắt tay làm X.
- **Số dòng** trích trong tài liệu phải được dò lại trên code hiện tại trước khi dùng.
