# Thảo luận thiết kế lại Demand Forecast (2026-10-14 → 2026-10-20)

> **Trạng thái: CHỈ THẢO LUẬN VÀ THÍ NGHIỆM, CHƯA SỬA CODE APP.**
> **Mới nhất (2026-10-21, GĐ2):** đo lại trên dữ liệu của bộ sinh mới, xem mục 13. Có hai đề xuất đổi so với QĐ-3 và QĐ-1, chờ chủ dự án.
> **2026-10-21: chủ dự án đã duyệt 7 quyết định.** Xem [`forecast_decisions.md`](forecast_decisions.md); tiến độ ở [`forecast_progress.md`](forecast_progress.md). File này giờ chỉ là hồ sơ lý do và số đo; chỗ nào nói "chưa chốt" là đúng tại thời điểm viết.
> 2026-10-20: chủ dự án nghiêng về B1 + C2 (mục 11), và bước 0, tức thí nghiệm chọn thuật toán, đã xong (mục 12).
>
> Mục 0–10 ghi ba lượt thảo luận trong phiên 2026-10-14 → 16. Mục 11–12 ghi phiên 2026-10-20.
> Thí nghiệm và dữ liệu chạy lại được nằm ở `docs/development/forecast_research/` (mục 10 và `step0/README.md`).
> Mọi truy vấn DB đều chỉ `SELECT`; phiên 2026-10-20 không truy vấn DB. App không được bật trong suốt các phiên.

---

## 0. Đọc phần này trước khi làm tiếp

### 0.1 Bối cảnh và kết luận ngắn

Chủ dự án yêu cầu xem kỹ phần dự báo trước khi làm tiếp. Thầy hướng dẫn nhận xét: *"nếu dùng hồi quy tuyến tính thì khả năng cao là đang sai"*.

Năm kết luận chính:

1. **Mô hình hiện tại là hồi quy tuyến tính theo thời gian, kéo thẳng ra tương lai.** Cụ thể: hệ số thứ trong tuần × đường thẳng khớp trên dữ liệu đã khử mùa. Thầy đúng về hướng. Thí nghiệm backtest ở 8 thời điểm cho thấy mô hình **thua cả cách "trung bình 4 lần cùng thứ gần nhất"** (4,94% so với 4,68%) và thua Holt-Winters (4,56%). Nó chỉ thắng baseline đang hiện trên màn hình (8,11%), và baseline đó bỏ qua chu kỳ tuần. Xem mục 8.
2. **Dữ liệu 99,8% là mô phỏng, sinh đúng bằng công thức "đường thẳng × hệ số tuần".** Mọi đánh giá trên dữ liệu này đều thiên vị cho mô hình hiện tại. Mô hình chỉ "tìm lại" được những gì bộ sinh đã cài.
3. **Bộ sinh có lỗi: xu hướng bị reset sau mỗi lần chạy bổ sung**, tạo hình răng cưa. Lỗi này **chưa được ghi** vào `current_bugs_found.md`, `project_report.md` hay roadmap (đã grep ngày 2026-10-14). Xem mục 2.
4. **Dữ liệu không có lễ, Tết hay mùa trong năm.** Ngày 30/4–1/5 và 2/9 có trong lịch sử nhưng không có hiệu ứng nào, tỉ số chỉ 0,97–1,00.
5. **Đơn vị dự báo hiện tại là tỉ lệ lấp đầy của từng chuyến.** Đơn vị này phụ thuộc chính quyết định nó phục vụ (đổi xe, thêm chuyến). Đề xuất xem xét đơn vị "nhu cầu của khung giờ". Xem mục 5.

### 0.2 Năm câu hỏi chờ chủ dự án chốt

| # | Câu hỏi | Ghi chú |
|---|---|---|
| 1 | **Thuật toán nền:** có thay hồi quy tuyến tính bằng Holt-Winters (xu hướng tắt dần) và đổi mốc so sánh sang seasonal naive không? | Holt-Winters viết tay bằng Java được (khoảng 60 dòng), nằm trong khuôn Non-Goal nếu duyệt đổi phương pháp |
| 2 | **Đơn vị:** có đổi từ "tỉ lệ lấp đầy từng chuyến" sang "nhu cầu của khung giờ" không? | Đi ngược Developer Note *"Do not 'improve' this into a ticket-count forecast"* (roadmap §9). **Phải sửa bộ sinh dữ liệu cùng lúc**, nếu không dự báo sẽ tệ hơn |
| 3 | **Lễ / mùa:** làm Hướng 1 ngay rồi chuyển dần sang Hướng 3, hay đầu tư Hướng 2 và chấp nhận giải trình chuyện "dữ liệu tự chứng minh"? | Xem mục 6 |
| 4 | **Non-Goal:** có nới ra để dùng thư viện hoặc một dịch vụ Python (Prophet, SARIMAX) không? | Hiện cấm thư viện ML (roadmap §4) |
| 5 | **Tầm dự báo:** giữ 7 ngày hay nhìn xa hơn? | Tết cần chuẩn bị xe và tài xế trước vài tuần, nhưng nhìn càng xa thì xu hướng thẳng càng nguy hiểm |

**Đề xuất của Claude ngày 2026-10-16** (chưa được duyệt; **đã được thay** bằng đề xuất ở mục 11 và kết quả đo ở mục 12, trong đó Holt-Winters thua hồi quy Poisson theo lịch ở cả hai thí nghiệm):
- Câu 1: chọn Holt-Winters.
- Câu 2: chốt riêng và chốt trước, vì nó ảnh hưởng mọi thứ phía sau.
- Câu 3: Hướng 1 trước, cộng bước bắt buộc là đánh dấu ngày lễ để loại khỏi phần huấn luyện.
- Câu 4: giữ Non-Goal, chỉ nhắc Prophet/SARIMAX khi bảo vệ.
- Câu 5: chưa có đề xuất cụ thể.

### 0.3 Ràng buộc phải nhớ

- **Roadmap §4 Non-Goal** (dòng 44): *"No real ML/deep learning model. Demand Forecast uses simple statistical methods (moving average / linear regression) written in plain Java — no new ML dependency."*
- **Phạm vi Phase 6** (roadmap dòng 154). Hai phần mở rộng đã được duyệt ngày 2026-07-22 (dòng 164): hệ số thứ trong tuần và backtest.
- **Developer Note §9** (mục "Why Demand Forecast predicts the occupancy RATE…"): lý do dự báo tỉ lệ thay vì số vé; câu chốt *"Do not 'improve' this into a ticket-count forecast."*
- **Mọi thay đổi phạm vi đều cần chủ dự án duyệt.** Truy cập DB chỉ đọc. Không commit khi chưa được bảo; commit không mang trailer Co-Authored-By.

### 0.4 Việc đã đề nghị nhưng chưa làm (chỉ làm khi được yêu cầu)

- Ghi lỗi "xu hướng bị reset" (mục 2, điểm 1–2) vào `current_bugs_found.md` theo cách phân loại ba hướng.
- Viết bài học 14 (Demand Forecast) theo `docs/ai/03_defense_study_prompt.md`.
- Chạy lại thí nghiệm với cấu hình khác: tầm dự báo 14 hoặc 30 ngày, thêm SARIMA, hoặc chạy trên một bộ dữ liệu khách thật công khai.

---

## 1. Hiện trạng `ForecastService` (đã đọc code, số dòng kiểm lại ngày 2026-10-16)

**Màn hình:** `/admin/analytics/forecast` (một tab trong `dashboard-analytics.html`).

**Dữ liệu đầu vào:**
- `TripRepository.findDemandHistoryByStatus` (`TripRepository.java:442`), gọi với `COMPLETED` (`ForecastService.java:211`):
  ```java
  @Query("SELECT t.route.id, t.departureTime, t.ticketsSold, t.totalSeats FROM Trip t " +
         "WHERE t.status = :status AND t.totalSeats > 0 ORDER BY t.departureTime")
  ```
- Chuyến `CANCELLED` bị loại vì chúng vẫn giữ số vé đã bán trước khi hủy.
- Trục thời gian là `departureTime`, không phải `createdAt`.

**Hằng số:**

| Hằng | Giá trị | Dòng |
|---|---|---|
| `MIN_OBSERVATIONS` | 28 | `:106` |
| `FORECAST_HORIZON_DAYS` | 7 | `:109` |
| `MOVING_AVERAGE_DAYS` | 7 | `:118` |
| `BACKTEST_DAYS` | 14 | `:121` |
| `MIN_TRAINING_OBSERVATIONS` | 14 | `:124` |
| `REINFORCEMENT_THRESHOLD` | 0.90 | `:134` |

**Đại lượng dự báo:** tỉ lệ lấp đầy = vé ÷ ghế, trong khoảng 0–1, theo từng (tuyến × giờ khởi hành × ngày).

**Mô hình.** Mỗi chuỗi là một tuyến × một giờ khởi hành, cần ít nhất 28 quan sát.
1. **Hệ số theo thứ** (`dayOfWeekFactors`, `:256`): trung bình của từng thứ chia cho trung bình chung, **tính gộp mọi chuỗi**. Hiện tại T2–T6 khoảng 0,94–0,96, T7/CN khoảng 1,13 (đầy đủ: 0,943 / 0,957 / 0,951 / 0,943 / 0,949 / 1,128 / 1,132).
2. **Khử mùa:** chia mỗi quan sát cho hệ số thứ của ngày đó.
3. **Hồi quy tuyến tính** bình phương tối thiểu trên phần đã khử mùa (`fitTrend`, `:346`), ra mức nền `a` và độ dốc `b` mỗi ngày.
4. **Dự báo** (`predict`, `:373`): `ŷ(D) = kẹp[0,1]((a + b·x_D) × hệ_số_thứ(D))`.
   - `x_D` là số ngày từ đầu chuỗi đến ngày D.
   - Dự báo 7 ngày, bắt đầu từ ngày mai.
   - Ngày nào vượt 0,90 thì gắn cờ `needsReinforcement`.
5. **Backtest** (`:414`):
   - Giấu 14 ngày cuối, huấn luyện trên phần còn lại.
   - Hệ số thứ được tính lại chỉ từ phần huấn luyện (`:424`), tránh để dữ liệu kiểm tra lọt vào.
   - So MAPE với baseline: trung bình 7 ngày gần nhất, giữ phẳng (`movingAverage`, `:386`).
   - Cột "Mức nền" trên màn hình chỉ để hiển thị và làm mốc so sánh, không tham gia dự báo.

**Ai dùng kết quả dự báo.** Mỗi lần mở trang, mỗi nơi gọi `buildForecast()` đúng một lần và không ghi gì vào DB.
- **Đề xuất tăng cường** (`RecommendationService.buildCard`, `:167`):
  - Mỗi ngày bị gắn cờ thành một chuyến ứng viên tạm **ở cùng giờ**.
  - Chọn xe và tài xế bằng `selectBestAvailableBus/Driver` (có `AvailabilityContext`), rồi tính chi phí, doanh thu, lợi nhuận và cho qua bộ kiểm tra luật dry-run.
  - Kết quả là `RECOMMENDED`, `NO_RESOURCE` hoặc `STALE`.
  - Engine chỉ đọc cờ, không biết con số 0,90.
- **What-if** (`WhatIfSimulationService.computeOutcome`, `:215`):
  - Nhân tỉ lệ dự báo với hệ số nhu cầu, rồi đếm lại số khung vượt 0,90.
  - So với năng lực phục vụ tính theo vòng quay xe, **theo từng ngày** (`coverableSlots`, `:320`).

**Ngưỡng 0,90 gốc:** `Trip.needsReinforcement()` (`Trip.java:137`): `getOccupancyRate() > 0.90`.

**Bộ quét thời gian thực, độc lập với dự báo** (`TripService.scanAndSuggestExtraTrips`, `:78`, chạy mỗi 10 giây):
- Điều kiện chọn chuyến:
  - chuyến `ACTIVE`, đã bán trên 90% ghế;
  - còn ít nhất 72 giờ nữa mới chạy (`:46`);
  - đã mở bán ít nhất 48 giờ (`:52`), bỏ qua điều kiện này nếu đã đầy từ 95% (`:58`).
- Khi đủ điều kiện, tạo chuyến tăng cường lùi 30 phút (`createExtraTrip`, `:151` / `:156`) ở trạng thái `PENDING_APPROVAL`, tự chọn xe và tài xế.

**Bộ sinh dữ liệu** (`HistoricalDataBackfill.java`):
- Công thức (`occupancy()`, `:361–378`):
  ```
  occupancy = kẹp[0,15..1]( base × slot × weekend × trend × noise )
    base    = 0,52 + (route.id % 4) × 0,07
    slot    = 06h: 1,05 | 12h: 0,85 | 18h: 1,12
    weekend = T7/CN: 1,20, còn lại 1,0
    trend   = 1 + 0,15 × dayIndex / 84          (:374)
    noise   = 1 ± 0,08 (phân phối đều)          (:314)
  ticketsSold = round(seats × occupancy)        (:344)  ← sinh tỉ lệ TRƯỚC rồi mới nhân số ghế
  ```
- 6% chuyến bị `CANCELLED` (`:144`).
- Giá = max(round(km × 1000 / 10.000) × 10.000, 50.000) (`:383`), nên cố định theo tuyến.
- Cửa sổ: `firstDay = today.minusDays(84)` (`:188`). `dayIndex` đếm từ ngày này của **mỗi lần chạy**.

**Số liệu tại 2026-09-21** (bản sao Python khớp đúng số ghi trong roadmap):
- 19 nhóm, trong đó 15 chuỗi đủ điều kiện; 2.061 quan sát;
- backtest MAPE **4,29%** so với baseline **7,43%** (194 điểm);
- **17** tín hiệu tăng cường.

Dự báo tính lại ngày 14/10 vẫn ra 15 chuỗi và 17 tín hiệu:
- tuyến 3 lúc 18:00 vượt 90% cả 7 ngày;
- các ngày T7/CN của tuyến 1, 2, 3, 5 chạm 93–100%.

Bảng đầy đủ ở phụ lục A.6.

---

## 2. Những điểm phát hiện khi kiểm tra (chưa sửa gì)

1. **Xu hướng tăng 15% cài sẵn không còn là một đường thẳng** (chưa có ghi chép nào trong các register).
   - Nguyên nhân: `trend = 1 + 0,15·dayIndex/84`, với `dayIndex` đếm từ "hôm nay − 84" của mỗi lần chạy (`:188`, `:374`). Mỗi lần chạy bổ sung, xu hướng bị đặt lại về mức thấp hơn.
   - Lấp đầy trung bình theo tuần (phụ lục A.2):
     - tăng đều từ 0,663 (tuần 27/04) đến 0,747 (13/07);
     - đi ngang;
     - **tụt xuống 0,714** (tuần 03/08), do lần chạy ngày 11/09 bắt đầu lại ở hệ số khoảng 1,08;
     - rồi lên lại 0,747 (14/09).
   - Hệ quả: độ dốc mô hình tìm ra hiện chỉ còn **+0,26 đến +0,54 điểm %/tuần**.
     - Roadmap (entry Phase 6 ngày 2026-07-22 trong §8, điểm 4) ghi "0,58–1,07 … khớp xu hướng cài sẵn"; câu đó chỉ đúng với dữ liệu tháng 7.
     - Nếu hội đồng hỏi "mô hình có tìm lại được xu hướng 15% không", câu trả lời không còn đơn giản là "có".
2. **Dữ liệu đã cũ:** kết thúc 20/09, tức cũ 24 ngày tính đến 14/10.
   - Dự báo đang kéo dài xu hướng qua cả khoảng trống này.
   - Chạy lại `backfill` sẽ tạo thêm một chỗ tụt khoảng 3–4% (hệ số khởi đầu khoảng 1,11 thay vì 1,15), cùng nguyên nhân với điểm 1.
3. **`ForecastService` không có unit test nào.** Phase 6 được kiểm chứng bằng cách đối chiếu với SQL viết độc lập.
4. **Bị chặn trần ở 100%.** Số vé không thể vượt số ghế, nên một ô "100%" chỉ có nghĩa là "ít nhất đầy xe". Nên nói rõ điểm này khi bảo vệ.
5. **Lập luận "tỉ lệ lấp đầy không đổi theo cỡ xe" (0,700 / 0,705 / 0,702 trong roadmap §9) là do bộ sinh tạo ra.** Bộ sinh tạo tỉ lệ trước rồi mới nhân với số ghế (`:344`), nên dữ liệu tự chứng minh cho chính nó. Phần lập luận kiến trúc (dùng lại ngưỡng 0,90) thì vẫn đúng.

---

## 3. Vòng 1 — Hiện trạng: dự báo cái gì, có gì, dùng gì, cần gì

### 3.1 Dữ liệu dự báo là gì

Phải tách ba tầng:

| Tầng | Là gì | Hệ thống có không |
|---|---|---|
| Thứ ta thật sự muốn biết | **Nhu cầu:** bao nhiêu người muốn đi tuyến r, khung giờ h, ngày D | Không đo trực tiếp được |
| Thứ quan sát được | Số vé đã bán (`ticketsSold`) của từng chuyến | Có |
| Thứ mô hình dự báo | Tỉ lệ lấp đầy = vé ÷ ghế, theo tuyến × giờ × ngày, 7 ngày tới, so với ngưỡng 0,90 | Có |

Ba khoảng hở giữa các tầng:

- **Bị chặn trần.** Xe đầy thì không biết bao nhiêu khách bị từ chối.
  - Toàn bộ dữ liệu: 94/2.065 chuyến (4,6%) chạm 100%.
  - Nhưng chúng dồn vào các chuỗi đông: tuyến 3 lúc 18:00 là 49/141 chuyến (35%), tuyến 3 lúc 06:00 là 26/136 (19%), tuyến 2 lúc 18:00 là 16/144 (11%).
  - Tức là chuỗi ta quan tâm nhất lại là chuỗi ta nhìn thấy kém nhất.
- **Tỉ lệ lấp đầy phụ thuộc xe được xếp.**
  - Cùng 30 khách: xe 22 chỗ ra 100% (8 người bị từ chối, không ghi lại), xe 50 chỗ ra 60%.
  - Đội xe có 3 cỡ (22/40/50), cả 3 cỡ đều xuất hiện trong cả 15 chuỗi.
- **Đơn vị là chuyến, không phải khung giờ.**
  - Chuyến tăng cường lùi 30 phút nên rơi vào cùng giờ, tức cùng chuỗi. Nhu cầu của khung khi đó chia cho hai chuyến.
  - Mô hình sẽ thấy hai chuyến vơi một nửa và "kết luận" nhu cầu giảm. Đây là vòng phản hồi.
  - Hiện có 0 chuyến tăng cường đã hoàn thành, nên chưa xảy ra.

### 3.2 Đang có gì

**Số lượng:** 2.065 chuyến `COMPLETED`, từ 27/04 đến 20/09/2026 (147 ngày, khoảng 21 tuần). Trong đó **2.061 chuyến (99,8%) do bộ sinh tạo ra**, chỉ 4 chuyến khác (phụ lục A.4).

| Dữ liệu | Có | Đang dùng | Ghi chú |
|---|---|---|---|
| `departureTime` | ✓ | ✓ | Suy ra ngày, thứ, giờ, chỉ số ngày cho xu hướng |
| `route` (tuyến có chiều) | ✓ | ✓ | Tuyến 1 = Mỹ Đình → Niệm Nghĩa, tuyến 4 = chiều ngược lại (phụ lục A.3) |
| `ticketsSold`, `totalSeats` | ✓ | ✓ | Ra tỉ lệ lấp đầy |
| `status` | ✓ | dùng để lọc | Chỉ lấy `COMPLETED` |
| `price` | ✓ | ✗ | Cố định theo tuyến (tuyến 1: 415 chuyến đều 120.000đ), không học được tác động của giá |
| Loại xe / sức chứa | ✓ | ✗ | Xem ý thứ hai ở mục 3.1 |
| `isExtraTrip`, `originalTrip` | ✓ | ✗ | Chưa có dữ liệu (0 chuyến tăng cường đã hoàn thành) |
| `saleOpenedAt`, `createdAt` | ✓ | ✗ | Với dữ liệu mô phỏng, đây là giá trị tự đặt hoặc ngày chạy script |
| Sự cố (`Incident`) | ✓ | ✗ | Gắn với xe; chuyến thì không bắt buộc. Muốn biết tuyến và ngày phải đi qua chuyến |
| Thời điểm đặt từng vé | ✗ | — | Chưa có bảng Booking (Phase 9); `ticketsSold` chỉ là một con số sửa tay |
| Lịch lễ, sự kiện, thời tiết | ✗ | — | Không có bảng nào |

**Mô hình chỉ tìm được những gì có trong dữ liệu, mà dữ liệu chính là bộ sinh.**
- Bộ sinh cài 5 thành phần: mức nền theo tuyến, hệ số khung giờ, cuối tuần ×1,20, xu hướng, nhiễu ±8%.
- Mô hình bắt đủ cả 4 thành phần có quy luật:
  - tách chuỗi theo tuyến × giờ bắt được mức nền và khung giờ;
  - hệ số thứ bắt được cuối tuần;
  - hồi quy bắt được xu hướng.
- Sai số 4,29% gần bằng mức nhiễu (nhiễu đều ±8% có sai lệch tuyệt đối trung bình 4%, cộng sai số làm tròn số vé).
- Nghĩa là **trên dữ liệu hiện tại không còn gì để học thêm.**

**Dùng để dự đoán:** đúng 4 cột (tuyến, giờ khởi hành, số vé, số ghế) của các chuyến `COMPLETED`.

### 3.3 Để dự đoán cần những thông tin gì

Năm thứ cần có:
1. **Mục tiêu được định nghĩa rõ:** đại lượng gì, đơn vị gì, chi tiết đến đâu, nhìn xa bao lâu.
2. **Lịch sử của mục tiêu**, đủ dài để thấy mỗi quy luật **nhiều lần**.
3. **Yếu tố giải thích biết trước được vào ngày cần dự báo.**
   - Lịch lễ thì biết trước.
   - Thời tiết 2 tuần sau thì không.
   - Sự cố đã qua giải thích được quá khứ, không dự báo được tương lai.
4. **Thông tin cung ứng** (sức chứa, số chuyến) để đổi qua lại giữa nhu cầu và tỉ lệ lấp đầy.
5. **Cách kiểm chứng:** backtest, mốc so sánh, thước đo sai số.

| Quy luật | Cần thấy | Đang có |
|---|---|---|
| Chu kỳ tuần | vài tuần | 21 tuần ✓ |
| Xu hướng | vài tháng | 5 tháng ✓ (nhưng là xu hướng tự cài, và bị răng cưa) |
| Mùa trong năm | ít nhất 2 năm | 0,4 năm ✗ |
| Tết | ít nhất 2 lần | 0 lần ✗ |
| 30/4, 2/9 | ít nhất 2 lần | 1 lần, và không có hiệu ứng |

### 3.4 "Hiện tại không có mùa, lễ, Tết đúng không?"

Đúng một nửa:
- **Có** mùa vụ theo tuần (T7/CN khoảng ×1,13, ngày thường khoảng ×0,95) và xu hướng tuyến tính.
- **Không có** mùa trong năm (hè, tựu trường, mùa bão), ngày lễ (Tết, 30/4, 2/9, Giỗ Tổ), sự kiện, thời tiết, giá.

Không chỉ mô hình thiếu, **dữ liệu cũng không có**. So với cùng thứ trong ±2 tuần (phụ lục A.7), các dịp lễ chỉ là ngày thường, tỉ số 0,97–1,00. Ngoài đời đây là những ngày cao điểm nhất năm của xe khách.

Nếu đem mô hình hiện tại chạy trên dữ liệu thật:
- sẽ **dự báo thiếu** ngay trước lễ (chiều về quê);
- sẽ **dự báo dư** những ngày vắng trong kỳ nghỉ;
- ngày lễ trong lịch sử sẽ làm méo hệ số thứ và đường xu hướng.

Nên kể cả khi không dự báo lễ, vẫn phải **nhận diện ngày lễ để loại khỏi phần huấn luyện**.

---

## 4. Vòng 2 — Nếu muốn thêm mùa, lễ, sự kiện

### 4.1 Dữ liệu dự báo phải đổi gì

- **Đơn vị nên đổi thành "nhu cầu của khung giờ"** (giải thích kỹ ở mục 5).
  - Đi ngược Developer Note *"Do not improve this into a ticket-count forecast"*, nên cần chủ dự án quyết.
- **Tầm dự báo phải xa hơn 7 ngày cho dịp lễ.**
  - Tết cần chuẩn bị xe và tài xế trước vài tuần.
  - Nhưng nhìn càng xa, xu hướng thẳng càng nguy hiểm: +0,4 điểm %/tuần × 52 tuần ≈ +20 điểm % mỗi năm. Cần chặn trần hoặc cho xu hướng tắt dần.
- **Hiệu ứng lễ có hình dạng, không chỉ là một hệ số.**
  - Vài ngày trước lễ, chiều về quê tăng mạnh; sau lễ, chiều quay lại thành phố tăng.
  - Tuyến đã có chiều (1 và 4 là một cặp), nhưng chưa có thông tin "chiều nào là về quê".

### 4.2 Cần những thông tin gì

| Yếu tố | Ví dụ | Dữ liệu cần | Biết trước? | Hệ thống có? |
|---|---|---|---|---|
| Lễ dương lịch | 1/1, 30/4–1/5, 2/9 | Lịch nghỉ chính thức **của từng năm** (nghỉ bù và hoán đổi ngày quyết định kỳ nghỉ dài mấy ngày) | ✓ | ✗ |
| Lễ âm lịch | Tết, Giỗ Tổ 10/3 ÂL | Bảng quy đổi âm → dương theo năm. Tết 2026 là 17/02, Tết 2027 là 06/02, nên chia theo "tháng trong năm" không bắt được Tết | ✓ | ✗ |
| Mùa trong năm | Hè, tựu trường, mùa bão miền Trung (tuyến 3 Đà Nẵng → Huế) | Ít nhất 2 năm lịch sử, hoặc hệ số do người vận hành nhập | ✓ | ✗ |
| Sự kiện | Lễ hội, thi THPT, đại nhạc hội | Bảng sự kiện: ngày, bến/tuyến, mức ảnh hưởng | một phần | ✗ |
| Bất thường | Bão, cấm đường | Đánh dấu ngày bất thường để làm sạch lịch sử, không dùng để dự báo xa | ✗ | một phần (qua `Incident`) |
| **Vé đã đặt trước** | Đã bán bao nhiêu vé cho chuyến 2 tuần tới | Bảng Booking có thời điểm đặt (Phase 9) | ✓ (thời gian thực) | ✗ |
| Giá, khuyến mãi | Tăng giá dịp Tết | Giá thay đổi theo thời gian | ✓ | ✗ (giá cố định) |

"Vé đã đặt trước" là tín hiệu mạnh nhất dịp lễ: nhà xe nhìn vé Tết bán nhanh hay chậm để quyết định tăng chuyến. Nó gắn trực tiếp với Phase 9.

### 4.3 Đang có gì cho mục tiêu này

Gần như chưa có gì:
- Lịch sử 147 ngày: 0 lần Tết, chưa trọn năm nào, mỗi lễ dương lịch xuất hiện 1 lần.
- 99,8% là mô phỏng, và bộ sinh không cài lễ hay mùa. Thêm biến lễ bây giờ thì hệ số ước lượng ra khoảng 1,0.
- Chưa có bảng lịch lễ, bảng sự kiện, bảng Booking.

### 4.4 Cái bẫy "dữ liệu tự chứng minh"

**Nếu cài hiệu ứng Tết vào bộ sinh rồi để mô hình "phát hiện" ra, ta chỉ chứng minh được code chạy đúng.** Ta không chứng minh được Tết làm tăng nhu cầu, vì chính ta viết ra hiệu ứng đó. Hội đồng có thể hỏi đúng câu này.

---

## 5. "Nhu cầu của một khung giờ" là gì, để làm gì, vì sao

**Định nghĩa.**
- **Khung giờ** = một tuyến + một giờ khởi hành + một ngày. Ví dụ: tuyến 3 Đà Nẵng → Huế, 18:00, thứ Bảy 17/10.
- **Nhu cầu của khung** = số người muốn đi trong khung đó, cộng gộp nếu khung chạy nhiều chuyến. Con số này không phụ thuộc xe to hay nhỏ, chạy một hay hai chuyến.
- Hệ thống hiện đo thứ khác: tỉ lệ lấp đầy của từng chuyến (vé ÷ ghế).

**Nguyên tắc.** Thứ đem đi dự báo không được phụ thuộc vào chính quyết định mà dự báo phục vụ. Dự báo dùng để quyết định "có thêm chuyến không, dùng xe nào", mà tỉ lệ lấp đầy lại thay đổi theo đúng hai quyết định đó.

**Ví dụ 1 — đổi xe.** Hai thứ Bảy liền nhau, cùng khung:

| | Xe | Vé bán | Tỉ lệ lấp đầy | Nhu cầu thật |
|---|---|---|---|---|
| Tuần trước | 22 chỗ | 22 | 100% | ít nhất 22 (khách bị từ chối không được ghi lại) |
| Tuần này | 40 chỗ | 30 | 75% | 30 |

Nhìn tỉ lệ lấp đầy thì thấy "giảm từ 100% xuống 75%". Thật ra nhu cầu tăng.

**Ví dụ 2 — thêm chuyến tạo vòng lặp.**
- Tuần 1: một chuyến 40 chỗ bán 38 vé (95%). Hệ thống đề xuất thêm chuyến.
- Tuần 2: hai chuyến, mỗi chuyến 19 vé (48%). Mô hình thấy hai điểm 48%, hạ dự báo và thôi đề xuất.
- Tuần 3: quay về một chuyến, lại 95%, lại đề xuất.
- Hệ thống dao động mãi, trong khi nhu cầu khung vẫn đứng yên ở 38 khách.

**Ví dụ 3 — trả lời được "cần bao nhiêu chuyến".**
- Nhu cầu dự báo 56 khách, xe 40 chỗ, ngưỡng 90% tương đương 36 khách mỗi xe.
- `ceil(56 / (0,9 × 40))` = `ceil(1,56)` = 2 chuyến, tức thêm 1 chuyến.
- Tỉ lệ lấp đầy chỉ trả lời được có hay không. Nhân tỉ lệ với hệ số lễ còn ra số vô nghĩa kiểu "lấp đầy 140%".

**Vẫn giữ ngưỡng 0,90 của hệ thống:**
- tỉ lệ = nhu cầu dự báo ÷ tổng số ghế dự kiến, rồi so với 0,90 như cũ;
- số chuyến cần = `ceil(nhu_cầu / (0,9 × sức_chứa_xe))` − số chuyến hiện có.

**Ba điều phải nói thật:**
1. **Đổi đơn vị không xóa chuyện chặn trần, chỉ làm nó lộ ra.** Khung bán 40/40 vẫn chỉ biết "ít nhất 40".
2. **Trên dữ liệu hiện tại, đổi đơn vị sẽ làm dự báo tệ hơn** nếu không sửa bộ sinh cùng lúc.
   - Lý do: bộ sinh tạo tỉ lệ trước rồi mới nhân với số ghế của xe được quay vòng.
   - Vì vậy số vé dao động gấp khoảng 3 lần tỉ lệ (độ biến thiên 0,32–0,35 so với 0,08–0,11).
   - Muốn đổi thì bộ sinh phải sinh "số người muốn đi" trước, rồi mới cắt theo số ghế.
3. **Nếu mỗi khung luôn chạy đúng một chuyến cùng cỡ xe, hai cách là như nhau.** Khác biệt chỉ xuất hiện khi vận hành thật: đổi xe, thêm chuyến.

---

## 6. Ba hướng thêm lễ / mùa (có ví dụ)

**Ví dụ chung:**
- Tuyến 1 Mỹ Đình → Niệm Nghĩa, 18:00, thứ Tư 03/02/2027.
- Tết 2027 rơi vào thứ Bảy 06/02, nên đây là ngày thứ 3 trước Tết.
- Dự báo nền cho ngày thường khoảng 78%, tức khoảng 31 khách trên xe 40 chỗ.

### Hướng 1 — Người vận hành nhập hệ số

- **Cách làm:** Admin có một bảng "lịch điều chỉnh", mỗi dòng gồm: tên dịp, từ ngày, đến ngày, tuyến/chiều, hệ số.
- **Ví dụ:** Admin nhập "Tết 2027 – chiều về quê – tuyến 1 – 01/02 đến 05/02 – ×1,8".
  - Ngày 03/02: 31 × 1,8 ≈ 56 khách → cần 2 chuyến → đề xuất thêm 1.
  - Chiều ngược (tuyến 4) nhập riêng cho mấy ngày sau Tết.
- **Hệ số lấy từ đâu:** kinh nghiệm nhà xe, số vé Tết năm trước, hoặc số liệu bến xe công bố. Giống `CostParameters`: số do nhà vận hành cung cấp, không phải lập trình viên tự đặt.
- **Ưu điểm:** không cần lịch sử, minh bạch, làm được ngay.
- **Nhược điểm:** không tự học, người nhập dễ lạc quan quá tay.

### Hướng 2 — Hệ thống tự học hệ số từ dữ liệu

- **Cách làm:** mô hình có thêm biến "ngày này là D−3 trước Tết, chiều về quê". Hệ số tính từ lịch sử, ví dụ hồi quy nhiều biến với biến 0/1 cho mỗi dịp; vẫn viết tay bằng Java được.
- **Ví dụ:**

  | | D−3, tuyến 1, 18:00 | Ngày thường cùng thứ | Tỉ số |
  |---|---|---|---|
  | Tết 2025 | 60 khách | 30 khách | 2,0 |
  | Tết 2026 | 54 khách | 30 khách | 1,8 |

  Mô hình học được khoảng ×1,9 và tự áp cho 03/02/2027. Cần bảng quy đổi âm → dương lịch vì Tết mỗi năm rơi vào một ngày dương lịch khác.
- **Cần có:**
  - lịch lễ;
  - mỗi dịp xuất hiện ít nhất 2 lần (học một lần, kiểm tra lần kia);
  - dữ liệu thật sự có hiệu ứng.
- **Thực tế đang có:** 0 lần Tết; 30/4 và 2/9 có nhưng không có hiệu ứng.
- **Nếu dùng dữ liệu mô phỏng thì phải:**
  - kéo bộ sinh ra 1–2 năm;
  - cài hiệu ứng lễ dựa trên số liệu bến xe công bố;
  - nói rõ vòng tự chứng minh;
  - backtest riêng trên các kỳ lễ.
- **Bẫy:** tự viết "×2,0" vào bộ sinh rồi mô hình tìm ra 1,97 thì chỉ chứng minh được code đúng, không chứng minh được Tết làm tăng nhu cầu.

### Hướng 3 — Kết hợp: số nhập tay làm điểm xuất phát, dữ liệu dần thay thế

- **Ví dụ:**
  - Năm đầu, Admin nhập ×1,8 như Hướng 1.
  - Tết 2027 qua đi, hệ thống đo được ×2,2.
  - Cho Tết 2028, hệ thống gợi ý (1,8 + 2,2) ÷ 2 = ×2,0 để Admin duyệt.
  - Càng nhiều năm thì càng tin dữ liệu hơn số nhập tay.
- **Ưu điểm:** dùng được ngay và tự tốt dần lên.
- **Nhược điểm:** phần "tự tốt dần" chỉ có nghĩa khi có dữ liệu thật, tức nằm ngoài thời gian làm đồ án.

---

## 7. Phản biện: có cách tốt hơn không

**Cả ba hướng có chung một điểm yếu.** Chúng chỉ thêm lớp lễ/mùa lên trên mô hình nền, và mặc định mô hình nền ổn. Nhưng điều thầy chê chính là mô hình nền, và thí nghiệm ở mục 8 cho thấy thầy có cơ sở.

**Đề xuất: làm ba tầng, mỗi tầng giải một việc.**

1. **Tầng nền (ngày thường).**
   - Thay hồi quy tuyến tính bằng phương pháp tự thích nghi. Đề xuất Holt-Winters với xu hướng tắt dần (damped).
   - Chọn phương pháp bằng backtest ở nhiều thời điểm (rolling-origin), so với mốc công bằng là seasonal naive.
2. **Tầng lễ / sự kiện.**
   - Hướng 1 ngay, chuyển dần sang Hướng 3 khi có dữ liệu thật.
   - **Bước bắt buộc kể cả khi chưa dự báo lễ:** đánh dấu ngày lễ để loại khỏi dữ liệu huấn luyện.
   - Điều chỉnh bằng phán đoán cho sự kiện đặc biệt (*judgmental adjustment*) là cách làm có tên trong ngành dự báo, có thể trình bày đàng hoàng khi bảo vệ.
   - Về sau có thể dùng cách so với cùng kỳ năm trước (*holiday analog*).
3. **Tầng vé đặt trước, khi có Phase 9.**
   - Chuyến 10 ngày nữa đã bán bao nhiêu vé là tín hiệu mạnh nhất trong ngành vận tải (*pickup / booking curve*).
   - Nó tự bắt được Tết mà không cần bảng lễ, vì vé Tết bán sớm và nhanh.
   - Cần bảng Booking có thời điểm đặt vé.

**Về dữ liệu (quan trọng ngang thuật toán).** Dữ liệu mô phỏng sinh bằng đúng công thức "đường thẳng × hệ số tuần", nên mọi đánh giá đều thiên vị cho hồi quy tuyến tính. Có hai cách gỡ:
- sửa bộ sinh theo kiểu "sinh số người muốn đi trước", và cài hiệu ứng lễ dựa trên số liệu bến xe công bố;
- hoặc dùng một bộ dữ liệu khách thật công khai để chọn thuật toán, rồi mới áp vào hệ thống. Ví dụ Chicago (CTA) công bố số khách bus theo ngày và theo tuyến; **chưa kiểm tra lại**, và đó là xe buýt nội đô, không phải xe khách liên tỉnh.

**Không nên làm:** mạng nơ-ron (LSTM, Transformer). Dữ liệu quá ít, lại là mô phỏng, và vượt phạm vi đồ án.

---

## 8. Vì sao "dùng hồi quy tuyến tính thì khả năng cao là sai", và thí nghiệm kiểm chứng

**Nói cho chính xác:** hồi quy không bị cấm trong dự báo; hồi quy động hay hồi quy có biến mùa vẫn có trong giáo trình, ví dụ Hyndman, *Forecasting: Principles and Practice*. Cái hay sai là **hồi quy theo thời gian rồi kéo thẳng đường đó ra tương lai**. Mô hình hiện tại đúng là kiểu đó (về bản chất là phân rã cổ điển dạng nhân). Năm lý do:

1. **Đường thẳng kéo dài mãi.** Nhu cầu thật không tăng đều mãi mãi.
2. **Mọi ngày trong lịch sử nặng như nhau.** Ngày cách đây 5 tháng nặng bằng hôm qua, nên khi mức nhu cầu đổi đột ngột, mô hình phản ứng rất chậm.
3. **Dữ liệu theo thời gian không độc lập.** Hôm nay giống hôm qua, trái giả định của hồi quy, làm sai khoảng tin cậy. Hệ thống chưa tính khoảng tin cậy nên chưa lộ.
4. **Tỉ lệ bị kẹp trong 0–1 và bị chặn trần ở 100%.** Đường thẳng không biết điều đó, phải kẹp tay.
5. **Lễ × chiều đi là quan hệ không tuyến tính.** Sẽ thành vấn đề khi mở rộng.

**Thí nghiệm (rolling-origin backtest):**
- 8 thời điểm dự báo, mỗi tuần một lần, từ 26/07 đến 13/09/2026;
- mỗi lần dự báo 7 ngày tiếp theo;
- 15 chuỗi, 783 điểm kiểm tra;
- thước đo MAPE (bỏ qua điểm có giá trị thật bằng 0).

| Phương pháp | MAPE |
|---|---|
| Holt-Winters cộng, xu hướng tắt dần | **4,56%** |
| Seasonal naive: trung bình 4 lần cùng thứ gần nhất | 4,68% |
| **Hồi quy tuyến tính + hệ số thứ (hiện tại)** | 4,94% |
| Seasonal naive: đúng cùng thứ tuần trước | 5,68% |
| Trung bình 7 ngày, giữ phẳng (baseline đang hiện trên màn hình) | 8,11% |

**Theo từng thời điểm dự báo:**

| Thời điểm → tuần kiểm tra | Hồi quy (hiện tại) | Holt-Winters | TB 4 lần cùng thứ | TB 7 ngày phẳng |
|---|---|---|---|---|
| 26/07 → 27/07–02/08 | 4,19% | 4,09% | 3,86% | 7,74% |
| 02/08 → 03/08–09/08 | **7,33%** | 6,19% | 5,88% | 10,00% |
| 09/08 → 10/08–16/08 | **6,04%** | 4,83% | 5,09% | 8,02% |
| 16/08 → 17/08–23/08 | **5,10%** | 4,32% | 4,48% | 7,98% |
| 23/08 → 24/08–30/08 | 3,97% | 4,37% | 4,70% | 7,53% |
| 30/08 → 31/08–06/09 | 4,45% | 4,53% | 4,74% | 8,70% |
| 06/09 → 07/09–13/09 | 4,22% | 4,04% | 4,12% | 7,35% |
| 13/09 → 14/09–20/09 | 4,29% | 4,14% | 4,57% | 7,66% |

**Rút ra:**
- **Mô hình hiện tại thua cả cách "trung bình 4 lần cùng thứ gần nhất"**, một cách gần như không cần tính toán. Nó chỉ thắng baseline đang hiện trên màn hình, mà baseline đó bỏ qua chu kỳ tuần, nên thắng nó không chứng minh được gì. Hội đồng dễ bắt đúng chỗ này.
- **Mô hình thua nặng nhất ngay sau cú tụt mức đầu tháng 8** (03/08–23/08). Ở các tuần ổn định, nó ngang ngửa các cách khác, vì dữ liệu được sinh bằng đường thẳng.
- **Cú tụt tháng 8 do bộ sinh** (mục 2, điểm 1). Nhưng cơ chế "mức nhu cầu đổi đột ngột" ngoài đời gặp thường xuyên (đối thủ mới, đổi giá, sửa đường), nên thí nghiệm vẫn minh họa đúng điểm yếu.
- **Ngay cả trên dữ liệu sinh đúng dạng của mô hình** (đánh giá vòng tròn), mô hình vẫn thua.

---

## 9. Các thuật toán phù hợp

| Thuật toán | Ý tưởng dễ hiểu | Cần gì | Viết tay bằng Java? | Đánh giá cho dự án |
|---|---|---|---|---|
| Naive / Seasonal naive (cùng thứ tuần trước, TB 4 tuần) / Trung bình trượt | "Thứ Bảy tới giống các thứ Bảy gần đây" | Vài tuần dữ liệu | Rất dễ | **Bắt buộc có làm mốc so sánh** |
| SES / Holt / **Holt-Winters / ETS, xu hướng tắt dần** | Giữ 3 con số (mức hiện tại, xu hướng, hệ số từng thứ) và chỉnh sau mỗi ngày; ngày gần nặng hơn ngày xa; xu hướng nhạt dần thay vì kéo thẳng | Vài tháng | Được, khoảng 60 dòng | **Đề xuất làm tầng nền.** Không có sẵn khái niệm lễ, phải dùng tầng điều chỉnh |
| ARIMA / SARIMA / SARIMAX | Mô hình kinh điển cho chuỗi thời gian; phần X cho phép thêm biến lễ | Vài tháng; mỗi dịp lễ ít nhất 2 lần | Khó, nên dùng thư viện (Python statsmodels) | Đúng sách giáo khoa, nhưng phải chọn nhiều tham số và xử lý ngày trống do chuyến bị hủy |
| Prophet (Meta) | Xu hướng gãy khúc được (changepoint) + mùa tuần/năm + bảng lễ có cửa sổ trước/sau | Ít nhất 1 năm nếu muốn mùa năm | Không, là thư viện Python | **Thiết kế khớp bài toán Tết nhất**, nhưng phải dựng dịch vụ Python riêng |
| Hồi quy đếm (Poisson / nhị thức âm) với biến lịch | Hồi quy đúng kiểu cho số khách: không âm, có biến lễ/thứ/mùa | Dữ liệu có hiệu ứng lễ | Được, nhưng phức tạp hơn | Hợp nếu đổi sang dự báo số khách |
| Gradient boosting (LightGBM, XGBoost) với đặc trưng lag + lịch | Cây quyết định học từ: tuần trước, TB 4 tuần, thứ, số ngày tới lễ, tuyến | Nhiều dữ liệu (nhiều năm, nhiều chuỗi) | Không, cần thư viện | Rất mạnh trong các cuộc thi dự báo (thắng M5), nhưng dữ liệu quá ít và không ngoại suy được xu hướng |
| Pickup / booking curve | Nhu cầu cuối = vé đã bán đến hôm nay + lượng vé thường bán thêm từ giờ đến ngày chạy | Bảng Booking có thời điểm đặt (Phase 9) | Được | **Chuẩn ngành vận tải**, mạnh nhất cho dịp lễ; chờ Phase 9 |
| Khôi phục nhu cầu bị chặn trần (unconstraining: EM, detruncation) | Ước lượng số khách bị từ chối ở chuyến đầy 100% | Biết chuyến nào chạm trần | Được | Phụ trợ, rất đáng làm cho tuyến 3 (35% chuyến 18:00 chạm trần) |
| LSTM / N-BEATS / Transformer (TFT) | Mạng nơ-ron cho chuỗi thời gian | Rất nhiều dữ liệu | Không | Không phù hợp |

**Đối chiếu với Non-Goal:**
- Seasonal naive, Holt-Winters, hồi quy đếm, pickup và unconstraining viết tay được, nên nằm trong khuôn nếu chủ dự án duyệt đổi phương pháp.
- SARIMA(X), Prophet, LightGBM/XGBoost cần thư viện hoặc dịch vụ Python riêng, tức đổi kiến trúc; cần nới Non-Goal (câu hỏi 4).

---

## 10. Thí nghiệm: cách chạy lại

**Thư mục:** `docs/development/forecast_research/`. Python 3 thuần, không cần numpy. Chạy trong thư mục đó:

| File | Làm gì | Lệnh | Kết quả mong đợi |
|---|---|---|---|
| `hist_snapshot_2026-10-14.tsv` | Ảnh chụp dữ liệu ngày 2026-10-14: 2.065 dòng (route_id, "YYYY-MM-DD HH", tickets_sold, total_seats), các chuyến `COMPLETED` từ 27/04 đến 20/09 | — | — |
| `forecast_replica.py` | Bản sao chính xác của `ForecastService.buildForecast()` + backtest | `python forecast_replica.py hist_snapshot_2026-10-14.tsv 2026-09-21` | 15 chuỗi, 2.061 quan sát, MAPE 4,29% / 7,43%, 17 tín hiệu (khớp roadmap) |
| `compare_methods.py` | So 5 phương pháp, rolling-origin (mục 8) | `python compare_methods.py hist_snapshot_2026-10-14.tsv` | 4,94 / 8,11 / 5,68 / 4,68 / 4,56% (n=783) |
| `per_origin.py` | Chia kết quả theo từng thời điểm dự báo | `python per_origin.py hist_snapshot_2026-10-14.tsv` | Bảng thứ hai ở mục 8 |
| `queries.sql` | Mọi truy vấn chỉ đọc đã chạy, kể cả câu export dữ liệu (Q0) | xem đầu file | Phụ lục A |

Đã chạy lại cả ba script từ thư mục này ngày 2026-10-16, ra đúng các con số trên. `queries.sql` cũng đã chạy lại trên DB cùng ngày; dữ liệu không đổi so với 14/10.

**Muốn chạy trên dữ liệu mới:** export lại bằng Q0 trong `queries.sql` ra một file `.tsv` khác, rồi truyền tên file đó cho script. Không ghi đè file snapshot.

**Chi tiết phương pháp trong `compare_methods.py`:**
- **Chuỗi:** tuyến × giờ; giá trị ngày = trung bình tỉ lệ lấp đầy (kẹp 0–1) các chuyến trong ngày đó; chỉ giữ chuỗi có ít nhất 28 ngày.
- **Thời điểm dự báo:** `LAST − 7k − 7` với k = 0..7, trong đó LAST = 20/09; tập kiểm tra là 7 ngày ngay sau mỗi thời điểm.
- **LR + mùa tuần:** hệ số thứ gộp mọi chuỗi tính trên phần huấn luyện; hồi quy trên phần đã khử mùa; giống `ForecastService`.
- **TB 7 ngày phẳng:** giống baseline của `ForecastService`.
- **Seasonal naive k tuần:** trung bình của k lần cùng thứ gần nhất có dữ liệu (lùi tối đa 19 tuần).
- **Holt-Winters:**
  - dạng cộng, xu hướng tắt dần, chu kỳ 7; chịu được ngày trống;
  - khởi tạo từ 2 tuần đầu;
  - chọn tham số bằng grid search tối thiểu SSE một bước trên phần huấn luyện: α ∈ {0,05; 0,1; 0,2; 0,3; 0,5}, β ∈ {0,01; 0,05; 0,1}, γ ∈ {0,05; 0,1; 0,2; 0,3}, φ ∈ {0,8; 0,9; 0,98}.

---

## 11. Vòng 3 (2026-10-20): ý kiến ban đầu của chủ dự án và đề xuất mới

Bản ghi đầy đủ của buổi này là file chủ dự án tự lưu từ terminal, `forecast_research/2026-10-20-chưa xong.txt`. Mục này chỉ tóm tắt.

Buổi này đặt các câu hỏi theo chữ cái: **A** thuật toán (câu 1 ở mục 0.2), **B** đơn vị (câu 2), **C** lễ/mùa (câu 3), **D** dữ liệu (mới), **E** tầm dự báo (câu 5), **F** vé đặt trước (mới). Câu 4 (Non-Goal) gắn với A, vì chọn Java hay Python quyết định có phải nới Non-Goal không.

**Ý kiến của chủ dự án (nói rõ là chưa chốt):**
- **B1**: dùng "nhu cầu của khung giờ" làm đơn vị, vì hợp với thứ dự án sắp phát triển.
- **C2**: hệ thống phải tự học lễ và mùa từ dữ liệu, không dùng số điều chỉnh nhập tay.
- A, D, E, F: chưa biết. Ưu tiên Java, cho phép dùng Python.

**Claude trả lời (thay cho đề xuất ở mục 0.2):**
- **B1: đồng ý.** Hơn nữa, đã chọn C2 thì bắt buộc phải B1:
  1. Tỉ lệ lấp đầy bị kẹp ở 100%, nên Tết làm nhu cầu gấp đôi cũng chỉ hiện ra là 100%.
  2. Chỉ đơn vị nhu cầu mới trả lời được câu "cần mấy chuyến".
  3. Doanh thu chuyến tăng cường đúng ra là phần khách tràn. Hiện code tính bằng tỉ lệ × sức chứa (`RecommendationService.java:215`).
  - Điều kiện đi kèm: phải xử lý chặn trần (EM unconstraining).
- **C2: đồng ý, kèm 3 điều kiện.**
  1. Dữ liệu phải thật sự có hiệu ứng lễ.
  2. Phải học gộp nhiều chuỗi.
  3. "Thời tiết" phải tách làm 3 loại:
     - mùa trong năm: học được;
     - sự kiện bão, lũ: không dự báo được, chỉ đánh dấu để loại khỏi dữ liệu huấn luyện (đã có Incident `ROAD_ISSUE`);
     - thời tiết từng ngày: không làm ở bản đầu.
- **A: đổi ý so với mục 0.2.** Holt-Winters không nhận được biến lễ, nên chỉ còn dùng làm mốc so sánh. Đề xuất mới:
  - hồi quy Poisson theo lịch, học gộp, có EM, viết tay bằng Java;
  - không dùng thư viện Smile, vì bản có Poisson GLM cần Java 21 trở lên, trong khi dự án dùng Java 17 (`pom.xml:30`);
  - Python chỉ để đối chứng, nằm ngoài app.
- **D: D2 + D3.** D2 là viết lại bộ sinh: sinh nhu cầu trước rồi cắt theo số ghế, ít nhất 3 năm, có Tết. D3 là kiểm chứng trên một bộ dữ liệu thật có Tết âm lịch: số lượt xuất nhập cảnh hằng ngày của Hồng Kông.
- **E:** 7 ngày cho Recommendation và What-if; khoảng 4–5 tuần chỉ để xem trước.
- **F:** chưa làm. Chỉ cần lưu thời điểm đặt vé khi thiết kế Booking ở Phase 9.
- **Thứ tự đề xuất:**
  0. Nghiên cứu bằng Python.
  1. Viết lại bộ sinh, xóa và sinh lại dữ liệu (cần backup và chủ dự án duyệt).
  2. Viết `ForecastService` bằng Java (Poisson + EM + unit test).
  3. Sửa Recommendation và What-if.
  4. Cập nhật tài liệu.

Chủ dự án: *"làm bước 0 đi"*. Kết quả bước 0 ở mục 12.

---

## 12. Bước 0: kết quả thí nghiệm chọn thuật toán (2026-10-20)

> **Chỉ nghiên cứu.** Không đụng code app, không đụng DB, app không được bật.
> Code và cách chạy lại: `forecast_research/step0/README.md`. Bảng số đầy đủ: `forecast_research/step0/results/`.
> **Đây chưa phải quyết định.** Các đề xuất ở 12.6 chờ chủ dự án chốt.

### 12.1 Bước 0 trả lời những câu nào

1. Chọn thuật toán nào (câu A), dựa trên số đo chứ không dựa trên ý kiến.
2. Có học được Tết từ dữ liệu không (câu C2), và cần bao nhiêu lần Tết trong lịch sử.
3. Bước EM khôi phục chặn trần (điều kiện của B1) có đáng làm không.
4. Có cần thêm thuộc tính "chiều" (đi tỉnh / về thành phố) cho Route không.
5. Nhìn xa được bao nhiêu ngày (câu E).

### 12.2 Hai thí nghiệm

| | **A: dữ liệu thật** | **B: dữ liệu mô phỏng** |
|---|---|---|
| Nguồn | Sở Di trú Hồng Kông, lượt qua cửa khẩu hằng ngày (data.gov.hk) | `step0/synth_generator.py` (mới) |
| Chuỗi | 10 cửa khẩu × 2 chiều = 20 | 5 tuyến × 3 giờ khởi hành = 15, giống app |
| Lịch sử | 01/04/2023 → 19/10/2026 | 01/07/2023 → 19/10/2026, 3 seed (7, 11, 23) |
| Tết âm lịch | 10/02/2024, 29/01/2025, 17/02/2026 | cùng ba ngày đó |
| Chặn trần | không có | có: vé bán = min(nhu cầu, số ghế), 19,6% khung bán hết vé |
| Chấm theo | số lượt thật | **nhu cầu thật**; mọi phương pháp chỉ được học trên số vé bán, đúng như những gì app ghi lại |

**Chung cho cả hai:**
- 90 thời điểm dự báo, mỗi tuần một lần, từ 29/12/2024 đến 13/09/2026;
- dự báo 35 ngày, và mỗi phương pháp chỉ thấy dữ liệu tính đến thời điểm dự báo;
- ngày lễ được coi là biết trước, vì đó là lịch;
- thước đo chính là WAPE = Σ|thật − dự báo| / Σ thật.

**Bộ sinh B được cố ý làm khác cấu trúc mọi mô hình ứng viên**, để mô hình phải xấp xỉ chứ không chỉ tìm lại công thức (cái bẫy ở mục 4.4):
- Lễ được neo vào **kỳ nghỉ chính thức**, trong khi mô hình chỉ được biết ngày âm lịch. Vì vậy đỉnh Tết lệch theo năm, ví dụ đỉnh chiều đi tỉnh rơi vào D−5 năm 2025 và D−4 năm 2026.
- Hình dạng lễ tùy chiều tuyến, độ lớn thay đổi theo từng lần.
- Mùa trong năm có dạng "bướu", không phải sóng sin/cos.
- Mức nhu cầu trôi ngẫu nhiên và có nhảy bậc.
- Nhiễu là nhị thức âm, và có ngày bão.
- Một "nhà xe trong quá khứ" chạy 1 xe mỗi khung, 8% số ngày đổi cỡ xe, và tự thêm chuyến dịp Tết.
- Đỉnh Tết khoảng ×3–3,5 so với ngày thường, theo cỡ báo chí đưa tin về các bến xe Hà Nội (200–350%). **Độ lớn này là giả định.**

**Thước đo quyết định (chỉ ở B):**
- Số chuyến cần = max(1, ⌈nhu cầu / (0,9 × số ghế xe chuẩn)⌉), đúng dạng nhu cầu của ngưỡng 0,90 hiện có.
- Cờ tăng cường = cần từ 2 chuyến trở lên.
- Đo precision, recall, F1 của cờ; tỉ lệ khách bị bỏ lại; số chuyến thừa trên 100 khung.

**Mốc "sàn" (chỉ ở B):** dự báo bằng chính kỳ vọng thật của bộ sinh. Đây là sai số không phương pháp nào vượt qua được, khoảng 19%, do nhiễu.

**Các phương pháp:**

| Tên trong bảng | Là gì |
|---|---|
| SN1 / SN4 / SN4 sạch | Cùng thứ tuần trước / trung bình 4 lần cùng thứ / như SN4 nhưng bỏ ngày lễ |
| App hiện tại (chỉ B) | Đúng logic `ForecastService` trên tỉ lệ lấp đầy, rồi × số ghế xe chuẩn |
| LR hiện tại | Bản tương tự trên số đếm: hệ số thứ × đường thẳng kéo ra tương lai |
| Holt-Winters | Dạng cộng, xu hướng tắt dần, trên log (statsmodels) |
| Prophet | Từng chuỗi riêng, có bảng lễ với cửa sổ trước/sau |
| G2 | SN4 sạch × hệ số lễ học được (hồi quy Poisson có ridge) |
| G1 (chỉ A) | GLM lịch, hệ số lễ chỉ gộp theo chiều |
| **G1'** | **GLM lịch: mức × thứ (riêng từng chuỗi) × hệ số lễ (gộp theo chiều + phần lệch riêng từng chuỗi, kéo về mức chung bằng ridge), nhân hệ số chỉnh mức theo 28 ngày gần nhất. Không kéo đường xu hướng ra tương lai.** |
| G1' + EM (chỉ B) | G1', trong đó khung bán hết vé chỉ cho biết "nhu cầu ≥ số ghế" và được thay bằng E[nhu cầu \| nhu cầu ≥ ghế] (nhị thức âm), rồi khớp lại, lặp 15 vòng |
| … không biết chiều tuyến | Như trên, nhưng mọi tuyến chung một nhóm, tức không thêm cột cho Route |
| … không mùa năm (K=0) | Bỏ thành phần sin/cos theo năm |
| LightGBM | Mô hình cây toàn cục, đặc trưng lịch + lễ + mức gần đây |

### 12.3 Kết quả A: dữ liệu thật Hồng Kông (WAPE %, thấp hơn là tốt hơn)

| Phương pháp | 1–7 ngày | 15–35 ngày | 1–7, ngày thường | Tết 2025 (đã thấy 1 Tết) | Tết 2026 (đã thấy 2 Tết) |
|---|---|---|---|---|---|
| SN4 sạch | 11,30 | 12,66 | 9,06 | **18,98** | 19,77 |
| LR hiện tại | 14,70 | 15,01 | 13,59 | 19,89 | 19,23 |
| Holt-Winters | 12,09 | 14,81 | 9,89 | 20,80 | 20,44 |
| Prophet | 10,91 | 12,60 | 9,26 | 30,96 | 11,55 |
| G2 | 10,80 | 12,73 | 9,06 | 20,72 | 13,51 |
| G1 (lễ chỉ gộp theo chiều) | 11,22 | 15,02 | 9,13 | 20,94 | 19,00 |
| G1' | 10,32 | 13,92 | 8,73 | 18,24 | 15,07 |
| **G1', K=0** | 10,40 | 12,01 | 8,69 | 21,66 | 14,56 |
| LightGBM | **9,92** | **10,94** | **8,47** | 20,75 | **10,93** |

So từng cặp trên 90 thời điểm, có khoảng tin cậy bootstrap 95% (`results/A_hk_pairs.md`):
- **LR hiện tại thua SN4 sạch 3,4 điểm** ở tầm 1–7 ngày, khoảng tin cậy [+2,6; +4,2]. Trên dữ liệu thật, thầy nhận xét đúng.
- **G1', K=0** thắng SN4 sạch, Holt-Winters và LR ở cả hai tầm (khoảng tin cậy không chứa 0).
- So với G2: G1', K=0 thắng ở tầm 15–35. Ở tầm 1–7 nó tốt hơn 0,4 điểm, nhưng khoảng tin cậy chứa 0.
- So với Prophet: hòa ở cả hai tầm.
- **G1', K=0** thua LightGBM: 0,5 điểm ở tầm 1–7 (khoảng tin cậy chứa 0), 1,1 điểm ở tầm 15–35, và 3,6 điểm ở Tết 2026 (hai khoảng này không chứa 0).
- Ở Tết 2026, G1', K=0 còn thua Prophet 3,0 điểm và thua G2 1,0 điểm. **Trên dữ liệu thật, G1' không phải mô hình bắt Tết tốt nhất.**
- **G1' có phần lệch riêng tốt hơn hẳn G1 chỉ gộp theo chiều**, thắng 72/90 thời điểm. Các cửa khẩu quá khác nhau để dùng chung một hệ số Tết; G1 ở Tết 2026 chỉ hơn SN4 sạch 0,8 điểm.
- **Đã thấy 2 Tết thì học được.** Ở Tết 2026, mọi phương pháp có phần lệch riêng từng chuỗi (G2, G1', Prophet, LightGBM) thắng SN4 sạch 4,7–8,8 điểm.
- **Mới thấy 1 Tết thì học gần như không lợi, thường còn hại.** Ở Tết 2025, G1', K=0 kém SN4 sạch 2,7 điểm, G2 kém 1,7, LightGBM kém 1,8, Prophet kém 12. Chỉ G1' với K=3 tốt hơn SN4 sạch một chút (0,7 điểm).

### 12.4 Kết quả B: dữ liệu mô phỏng có chặn trần, 3 seed gộp lại (WAPE % so với nhu cầu thật, không tính ngày bão)

| Phương pháp | 1–7 ngày | 15–35 ngày | 1–7, ngày thường | Tết 2025 | Tết 2026 |
|---|---|---|---|---|---|
| SN4 sạch | 25,37 | 25,47 | 22,03 | 45,90 | 45,02 |
| **App hiện tại** | 25,92 | 25,76 | 22,38 | 46,28 | 45,95 |
| LR hiện tại | 25,83 | 25,60 | 22,22 | 46,48 | 46,02 |
| Holt-Winters | 24,82 | 24,78 | 21,13 | 46,24 | 45,69 |
| Prophet | 23,18 | 23,04 | 20,84 | 36,59 | 28,56 |
| G2 | 23,74 | 23,84 | 22,03 | 32,80 | 27,15 |
| G1' (chưa EM) | 22,68 | 23,58 | 20,81 | 32,55 | 27,21 |
| G1' + EM | 22,24 | 23,72 | 20,32 | 33,11 | 26,17 |
| G1' + EM, không biết chiều tuyến | 22,60 | 23,85 | 20,31 | 38,90 | 29,99 |
| **G1' + EM, K=0** | **21,98** | **22,25** | **20,10** | **31,72** | **24,78** |
| LightGBM | 24,73 | 24,74 | 22,28 | 52,60 | 27,14 |
| *(sàn: biết kỳ vọng thật)* | *19,11* | *19,10* | *19,19* | *18,86* | *18,26* |

**Quyết định mà dự báo nuôi** (gộp 3 seed; `results/B_synth_report_3seeds.md`):

| Phương pháp | Tầm 1–7, mọi ngày: F1 cờ | recall | khách bị bỏ lại | Tầm 15–35, Tết: F1 cờ | khách bị bỏ lại |
|---|---|---|---|---|---|
| **App hiện tại** | 0,15 | 0,09 | 6,12% | 0,15 | 18,41% |
| SN4 sạch | 0,44 | 0,34 | 4,07% | 0,38 | 15,52% |
| Prophet | 0,45 | 0,33 | 2,82% | 0,64 | 4,77% |
| G2 | 0,48 | 0,38 | 2,69% | 0,66 | 4,43% |
| G1' (chưa EM) | 0,47 | 0,36 | 2,62% | 0,67 | 4,06% |
| G1' + EM | 0,58 | 0,54 | 1,58% | 0,69 | 2,67% |
| G1' + EM, không biết chiều tuyến | 0,58 | 0,54 | 1,87% | 0,62 | 4,66% |
| **G1' + EM, K=0** | **0,58** | **0,54** | **1,57%** | **0,71** | **2,24%** |
| LightGBM | 0,45 | 0,34 | 3,34% | 0,54 | 8,83% |
| *(sàn)* | *0,63* | *0,59* | *1,02%* | *0,78* | *0,72%* |

**Rút ra:**
- **G1' + EM, K=0 tốt nhất ở cả 3 seed** (`results/B_synth_pairs.md`):
  - thắng app hiện tại 3,7–4,2 điểm, ở 76–82/90 thời điểm;
  - thắng G2 1,8 điểm, Prophet 1,2, LightGBM 2,8 (gộp 3 seed, khoảng tin cậy không chứa 0).
- **App hiện tại gần như không bao giờ gắn cờ** (recall 0,09). Nó dự báo tỉ lệ bị kẹp ở 100% rồi × số ghế, nên không bao giờ thấy được nhu cầu vượt một xe. Dịp Tết nó bỏ lại 18% khách, so với 2,2% của G1' + EM, K=0.
- **EM ít tác động đến WAPE** (giảm 0,2–0,7 điểm mỗi seed) **nhưng tác động lớn đến quyết định**: recall tăng từ 0,36 lên 0,54, khách bị bỏ lại giảm từ 2,62% xuống 1,58%. Lý do: những khung bị chặn trần chính là những khung cần tăng cường.
- **Thuộc tính chiều tuyến** gần như không đổi gì ở ngày thường. Nhưng ở Tết nó giảm WAPE 3,8 điểm (Tết 2026) và 5,8 điểm (Tết 2025); ở bảng lên kế hoạch Tết, khách bị bỏ lại là 2,7% so với 4,7%.
- **LightGBM sụp khi mới có 1 Tết** (Tết 2025: 52,6%), và thua ở dữ liệu ít chuỗi.

### 12.5 Bỏ thành phần mùa năm (K=0): phát hiện, nhưng là chọn sau khi xem kết quả

- Trên dữ liệu Hồng Kông, G1' có sóng sin/cos theo năm (K=3) **tệ hơn SN4 sạch ở tầm 15–35** (13,92 so với 12,66). Một thử chẩn đoán cho thấy thủ phạm là thành phần mùa năm, không phải hệ số chỉnh mức.
- Trên dữ liệu mô phỏng, **nơi có mùa thật**, bỏ thành phần đó cũng tốt hơn: tầm 15–35 là 22,25 so với 23,72, thắng 86/90 thời điểm.
- Cách hiểu: với khoảng 3 năm dữ liệu, 6 hệ số sin/cos mỗi chuỗi dễ khớp theo nhiễu và theo các cú đổi mức. Hệ số chỉnh mức 28 ngày đã đủ đuổi theo mùa ở tầm 5 tuần.
- **Giới hạn:** K=0 được chọn *sau khi* xem kết quả trên chính các tập kiểm tra này. Hai bộ dữ liệu độc lập cùng chỉ một hướng, nhưng vẫn phải kiểm lại ở bước 1, trên dữ liệu sinh mới với seed mới.

### 12.6 Trả lời 5 câu, và đề xuất (chờ chủ dự án chốt)

> **Đã duyệt ngày 2026-10-21**, thành QĐ-1…QĐ-7 trong [`forecast_decisions.md`](forecast_decisions.md). Phần dưới giữ nguyên như lúc viết.

1. **Thuật toán (A): G1' + EM, K=0**, tức hồi quy Poisson theo lịch, học gộp, có khôi phục chặn trần. Có thể viết tay bằng Java 17, không cần thư viện:
   - phần lõi là IRLS (bình phương tối thiểu có trọng số, lặp lại);
   - cộng thêm vòng EM;
   - giải hệ phương trình tuyến tính khoảng 1.400 ẩn.
   - Thắng mọi phương án ở dữ liệu dạng xe khách (B).
   - Đứng nhóm hai ở dữ liệu thật (A): sau LightGBM, ngang Prophet, hơn SN4 sạch, Holt-Winters và LR, hơn G2 ở tầm xa. Riêng ở Tết thật, nó kém LightGBM, Prophet và G2.
   - **Vì sao không chọn LightGBM dù nó thắng ở A:**
     - cần thư viện ML, tức phải nới Non-Goal §4;
     - không xử lý được chặn trần;
     - sụp khi mới có 1 Tết;
     - thua 2,8 điểm ở B;
     - là hộp đen.
     Ngược lại, hệ số của G1' đọc thẳng ra thành bảng, ví dụ "Tết D−3, chiều về ×1,9". Nên giữ LightGBM và Prophet làm đối chứng Python khi bảo vệ: *có mô hình mạnh hơn trên dữ liệu thật, và đây là lý do chọn mô hình giải thích được, viết được bằng Java*.
   - **Holt-Winters:** không chọn, vì thua G1' ở cả A lẫn B.
2. **Đơn vị (B1): dữ liệu ủng hộ.** Trên tỉ lệ lấp đầy, app hiện tại không thấy được nhu cầu vượt một xe (recall 0,09).
3. **Lễ/mùa (C2): học được, với điều kiện lịch sử đã có ít nhất 2 lần Tết.**
   - Mới có 1 lần thì trên dữ liệu thật học còn hại.
   - Đề xuất, **chưa thử nghiệm**: chỉ dùng hệ số của một dịp lễ khi dịp đó đã xuất hiện ít nhất 2 lần trong dữ liệu huấn luyện; nếu chưa thì coi như ngày thường.
   - Bộ sinh mới cần ít nhất 3 năm, để có 2 Tết trước Tết dùng kiểm tra.
4. **Thuộc tính chiều tuyến: nên thêm**, vì giúp rõ ở Tết. Đây là đổi schema, cần chủ dự án quyết. Không thêm thì vẫn chạy được, nhưng Tết kém hơn khoảng 4–6 điểm.
5. **Tầm dự báo (E): 7 ngày cho thẻ đề xuất, đến 35 ngày để xem trước.** Sai số của G1', K=0 tăng ít theo tầm (bảng theo tầm của `report.py`): A từ 10,40 (ngày 1–7) lên 12,28 (ngày 29–35); B từ 22,32 lên 22,48 (bảng này tính cả ngày bão).
6. **Non-Goal §4** chỉ cần sửa chữ: *"moving average / linear regression"* → *"mô hình thống kê (hồi quy Poisson theo lịch) viết bằng Java"*. Vẫn không có mô hình ML, không thêm thư viện.

### 12.7 Điều phải nói thật

- **A không phải dữ liệu vé xe khách.** Đó là lượt qua cửa khẩu: không có chặn trần, chỉ giống ở chỗ có Tết âm lịch theo chiều và nhiều chuỗi.
- **B là bộ sinh do Claude viết, độ lớn là giả định.** Dù đã cố ý làm khác cấu trúc, nó vẫn là dạng nhân giống G1', nên có lợi cho G1'. Bằng chứng: ở B học từ 1 Tết đã có lợi, ở A thì không. Tức Tết trong bộ sinh đều đặn hơn ngoài đời. **Kết quả B là cận trên cho G1'.**
- **Ngày bão không phương pháp nào dự báo được** (WAPE trên 120%). Ngày bão được loại khỏi dữ liệu huấn luyện và khỏi các bảng trên; `report.py` chấm riêng chúng.
- **EM ở đây là dạng thực dụng.** Bước E dùng nhị thức âm, bước M khớp Poisson (quasi-likelihood), nên không phải EM chính xác cho nhị thức âm.
- **Chi phí tính toán.** Bản Python mất khoảng 10–25 giây cho mỗi lần khớp (khoảng 1.400 cột, 15 vòng EM). Bản Java không dùng BLAS có thể chậm hơn, mà hiện có 3 màn hình cùng gọi `buildForecast()`. Nhiều khả năng phải tính một lần mỗi ngày rồi cache. Đây là chi phí ẩn của bước 2.
- **Ngưỡng ridge (λ = 2) và số vòng EM (15)** không được tinh chỉnh; dùng nguyên từ đầu.

### 12.8 Chưa làm, chờ chủ dự án

> **Cập nhật 2026-10-21:** các đề xuất đã được chốt. Việc còn lại và trạng thái được theo dõi ở [`forecast_progress.md`](forecast_progress.md). Câu hỏi về lỗi "xu hướng reset" là mục M-10 trong `forecast_decisions.md`.

- Chốt các đề xuất ở 12.6, nhất là thuật toán, thuộc tính chiều tuyến, tầm dự báo và cách sửa Non-Goal.
- Nếu duyệt, làm tiếp theo thứ tự ở mục 11: bước 1 (bộ sinh mới, xóa và sinh lại dữ liệu, có backup), bước 2 (Java + unit test), bước 3 (Recommendation và What-if tính số chuyến cần và doanh thu phần tràn), bước 4 (tài liệu).
- Lỗi "xu hướng bị reset" của bộ sinh hiện tại (mục 2) vẫn **chưa được ghi** vào `current_bugs_found.md` hay `project_report.md`. *(Cập nhật 2026-10-21: đã ghi thành #31.)*

---

## 13. GĐ2: đo lại các lựa chọn trên dữ liệu của bộ sinh mới (2026-10-21)

> **Chỉ nghiên cứu.** Dữ liệu lấy từ bản sao `busmanagement_gd2` sau buổi tổng duyệt GĐ3; DB thật và app không bị đụng.
> Script ở `forecast_research/gd2/`, bảng số đầy đủ ở `forecast_research/gd2/results/`.
> Đây là phần QĐ-13. **Mọi thay đổi mà kết quả gợi ý đều chờ chủ dự án duyệt.**

### 13.1 Dữ liệu và cách đo

**(B') Dữ liệu mô phỏng mới:**
- 15 chuỗi khung giờ, từ 01/01/2023 tới 20/10/2026.
- Mô hình học trên **số vé thật sự ghi trong DB**: đã cộng chuyến thêm, khung bị hủy coi là không quan sát được.
- Chấm theo **nhu cầu thật** mà bộ sinh xuất ra (`-Dbackfill.truth-csv`). DB và kế hoạch của bộ sinh khớp ở 19.475/19.475 khung.
- 21,0% khung bán hết vé.
- 72 thời điểm dự báo, cách nhau 14 ngày, từ 24/12/2023 tới 13/09/2026; tầm 35 ngày.
- Ở Tết 2024 lịch sử mới có 1 lần Tết, Tết 2025 có 2 lần, Tết 2026 có 3 lần. Mỗi Tết có 5 thời điểm dự báo chạm tới, nên khoảng tin cậy ở các cột Tết rộng.

**(A) Dữ liệu thật Hồng Kông:** đúng dữ liệu, cửa sổ lễ và 90 thời điểm như bước 0. Mô hình chính tái hiện **đúng từng số** của §12.3 (10,40 / 12,01 / Tết 2025 21,66 / Tết 2026 14,56), nên lần chạy này nhất quán với bước 0.

Mỗi lần chỉ đổi **một** yếu tố quanh mô hình đã chọn (G1' + EM, K=0, λ=2, không quy tắc, cửa sổ hiện tại, có chiều tuyến). So sánh từng cặp theo từng thời điểm, kèm khoảng tin cậy bootstrap 95% (`step0/compare.py`).

### 13.2 Kết quả trên dữ liệu mô phỏng mới (WAPE % so với nhu cầu thật; âm = tốt hơn mô hình chính)

| Biến thể | Tầm 1–7 | Tầm 15–35 | Tết 2024 (đã thấy 1 lần) | Tết 2025 | Tết 2026 |
|---|---|---|---|---|---|
| **Mô hình chính** (WAPE) | **22,56** | **22,75** | **35,75** | **25,00** | **24,97** |
| SN4 sạch (WAPE) | 25,29 | 25,32 | 41,53 | 39,29 | 40,63 |
| App hiện tại (WAPE) | 25,46 | 25,41 | — | — | — |
| K = 3 | **+0,27** | **+1,28** | | | |
| Quy tắc (a): ≥ 2 lần | +0,28 (KTC chứa 0) | +0,20 (KTC chứa 0) | **+7,37** | −0,05 | 0 |
| Quy tắc (b): co hệ số gộp (ridge 2) | −0,20 (KTC chạm 0) | **−0,17** | **−2,56** | −0,15 (KTC chứa 0) | **+0,30** |
| Không EM | +0,04 (KTC chứa 0) | +0,01 (KTC chứa 0) | **−3,70** | **−1,06** | 0 |
| EM 5 vòng | **−0,06** | **−0,04** | | | |
| λ = 0,5 | **+0,28** | **+0,28** | | | |
| λ = 8 | **−0,12** | **−0,13** | −0,07 | **−1,06** | **−0,97** |
| Cửa sổ lễ hẹp | **−0,31** | **−0,27** | **−1,20** | 0 | −0,32 (KTC chứa 0) |
| Cửa sổ lễ rộng | +0,21 (KTC chứa 0) | +0,15 (KTC chứa 0) | | | |
| Không biết chiều tuyến | −0,07 (KTC chứa 0) | −0,03 (KTC chứa 0) | −0,40 (KTC chứa 0) | +0,56 (KTC chạm 0) | **+0,95** |

Số **đậm** là những chênh lệch có khoảng tin cậy 95% không chứa 0. Ô trống là cặp không được so riêng ở Tết.

**Quyết định mà dự báo nuôi** (`results/gd2_report.md`; "bỏ lại" là phần khách không có chỗ khi chạy đúng số chuyến dự báo):

| | Tầm 1–7, mọi ngày: recall cờ / khách bị bỏ lại | Tầm 15–35, Tết: khách bị bỏ lại / chuyến thừa trên 100 khung |
|---|---|---|
| App hiện tại | 0,13 / 5,42% | 12,25% / 2,2 |
| SN4 sạch | 0,37 / 3,74% | 10,87% / 10,2 |
| **Mô hình chính** | **0,53 / 1,67%** | **1,26% / 15,0** |
| Không EM | 0,39 / 2,54% | 2,04% / 10,1 |
| Quy tắc (b) | 0,52 / 1,71% | 1,71% / 12,1 |
| Không biết chiều tuyến | 0,52 / 1,75% | 2,03% / 14,8 |
| *(sàn)* | *0,57 / 1,19%* | *0,80% / 7,4* |

### 13.3 Kết quả trên dữ liệu thật Hồng Kông (`results/hk_direction_pairs.md`, `results/hk_rules_pairs.md`)

| Biến thể (so với mô hình chính) | Tầm 1–7 | Tầm 15–35 | Tết 2025 (đã thấy 1 lần) | Tết 2026 |
|---|---|---|---|---|
| Mô hình chính so với SN4 sạch | **−0,90** | **−0,65** | **+2,68** | **−5,21** |
| Không biết chiều tuyến | +0,02 (KTC chứa 0) | +0,01 (KTC chứa 0) | **+0,33** | **+0,21** |
| Quy tắc (a): ≥ 2 lần | −0,01 (KTC chứa 0) | +0,03 (KTC chứa 0) | **−2,60** | 0 |
| **Quy tắc (b): co hệ số gộp** | **−0,22** | **−0,21** | **−2,76** | **−0,68** |
| λ = 8 | **+0,38** | **+0,39** | **+2,49** | **+3,27** |

### 13.4 Rút ra

1. **Mô hình chính vẫn đứng vững.** Trên dữ liệu mới, nó thắng SN4 sạch và app hiện tại khoảng 2,6–2,9 điểm ở mọi tầm (khoảng tin cậy không chứa 0), và thắng 14–16 điểm ở Tết 2025, 2026.
   - App hiện tại vẫn gần như không gắn cờ: recall 0,13.
   - Dịp Tết, khi lên kế hoạch trước, app bỏ lại 12,3% khách; mô hình chính bỏ lại 1,3%.
2. **K = 0 được xác nhận** trên cả hai bộ dữ liệu. Thêm sóng mùa năm (K = 3) tệ hơn 1,3 điểm ở tầm xa.
3. **Quy tắc "ít nhất 2 lần" kiểu (a) cho kết quả trái ngược giữa hai bộ dữ liệu.**
   - Dữ liệu thật: tốt hơn 2,6 điểm ở lần Tết thứ hai.
   - Dữ liệu mô phỏng: tệ hơn 7,4 điểm.
   - Lý do: Tết trong bộ sinh đều đặn hơn ngoài đời, nên học từ một lần đã có ích.
   - **Biến thể (b) — không cắt cứng mà co hệ số gộp về ×1 bằng ridge — tốt hơn hoặc gần bằng ở cả hai:**
     - dữ liệu thật: −0,2 ở mọi tầm, −2,8 ở Tết 2025, −0,7 ở Tết 2026;
     - dữ liệu mô phỏng: −0,2 ở mọi tầm, −2,6 ở Tết 2024, chỉ +0,3 ở Tết 2026.
4. **EM gần như không đổi WAPE** (+0,04, khoảng tin cậy chứa 0), và ở Tết bỏ EM còn cho WAPE thấp hơn. Nhưng EM **đổi quyết định**:
   - recall cờ tăng từ 0,39 lên 0,53;
   - khách bị bỏ lại giảm từ 2,54% xuống 1,67%; dịp Tết giảm từ 2,04% xuống 1,26%;
   - đổi lại chạy thừa thêm khoảng 4–5 chuyến trên 100 khung (10,1 so với 6,2 ở mọi ngày; 15,0 so với 10,1 khi lên kế hoạch Tết).

   Đây đúng là lập luận của bước 0: khung bán hết vé chính là khung cần tăng cường. **5 vòng EM cho kết quả như 15 vòng**, mà số lần khớp giảm từ 16 xuống 6 (một lần khớp đầu cộng mỗi vòng một lần), tức nhanh khoảng 2,7 lần.
5. **λ: giữ 2.** λ = 8 tốt hơn chút trên dữ liệu mô phỏng nhưng **tệ hơn rõ trên dữ liệu thật**: +2,5 ở Tết 2025, +3,3 ở Tết 2026. Dữ liệu thật được ưu tiên.
6. **Cửa sổ lễ:** cửa sổ hẹp tốt hơn khoảng 0,3 điểm trên dữ liệu mô phỏng. Nhưng chỉ đo trên bộ sinh do chính ta viết, nơi hình dạng Tết là giả định; chưa đo trên dữ liệu thật.
7. **Thuộc tính chiều tuyến có ích nhưng nhỏ:**
   - dữ liệu thật: 0,2–0,3 điểm ở Tết, không đổi gì ở ngày thường;
   - dữ liệu mô phỏng mới: 0,6–1,0 điểm ở Tết; khách bị bỏ lại dịp Tết là 2,0% khi không có chiều, so với 1,3%.

   Con số 3,8–5,8 điểm của bước 0 bị phóng to bởi bộ sinh cũ. Phần lệch riêng của từng tuyến đã học được phần lớn hình dạng của chính nó. **Khi bảo vệ phải nói con số nhỏ này.** Không đổi gì: thuộc tính đã có từ GĐ1 và tốn rất ít.

### 13.5 Đề xuất (chủ dự án chốt đề xuất 1 và 2 ngày 2026-10-22; chưa áp vào code, `ForecastService` mới chỉ viết ở GĐ5)

| # | Đề xuất | Thay đổi so với |
|---|---|---|
| 1 | **Thay quy tắc (a) bằng quy tắc (b):** không bỏ hệ số của dịp mới thấy 1 lần, mà co mọi hệ số lễ gộp về ×1 bằng ridge (λ gộp = 2), giống cách đã co phần lệch riêng của tuyến. **Chủ dự án duyệt ngày 2026-10-22** | QĐ-3 |
| 2 | **EM 5 vòng** thay vì 15. **Chủ dự án duyệt ngày 2026-10-22** | QĐ-1 (chi tiết) |
| 3 | Giữ K = 0, λ = 2, cửa sổ lễ hiện tại, thuộc tính chiều tuyến | Không đổi |

### 13.6 Điều phải nói thật

- B' vẫn là bộ sinh do Claude viết, với độ lớn giả định. Điều chứng minh được là **mô hình chạy đúng trên dữ liệu có đúng cấu trúc của app**: chặn trần, chuyến thêm, khung bị hủy. Không phải độ chính xác ngoài đời.
- Các cột Tết chỉ có 5 (B') hoặc 7–9 (A) thời điểm dự báo. Khoảng tin cậy là theo thời điểm, không tính được độ bất định giữa các năm.
- Quy tắc (b) và các λ được chọn **sau khi** xem kết quả trên chính các tập này. Hai bộ dữ liệu độc lập cùng chỉ một hướng, nhưng vẫn là chọn sau khi xem, giống K = 0 ở bước 0.

### 13.7 Bổ sung 2026-10-22: tách theo "quy tắc (a) còn tác dụng hay không" (M-15 ý 1)

**Vì sao tách.** App học từ lịch sử bắt đầu 01/01/2023, nên khi chạy thật mọi dịp lễ đã có ít nhất 3 lần (QĐ-3, "Tác động trong đồ án"). Khi đó quy tắc (a) không làm gì cả. Câu hỏi thực tế là: **trong tình huống đó, quy tắc (b) có làm hại không?**

**Cách làm.** Không chạy lại mô hình nào. `gd2/split_by_rule_a.py` chia các thời điểm dự báo đã có thành hai nhóm, tùy dự báo của (a) có trùng với mô hình chính trên mọi dòng hay không, rồi chấm từng nhóm bằng `step0/compare.py`. Bảng đầy đủ ở `gd2/results/rule_ab_by_regime.md`.
- Mô phỏng: (a) hết tác dụng ở 45/72 thời điểm (05/01/2025 → 13/09/2026); còn tác dụng ở 27 (24/12/2023 → 22/12/2024).
- Hồng Kông: hết tác dụng ở 83/90 (16/02/2025 → 13/09/2026); còn tác dụng ở 7 (29/12/2024 → 09/02/2025).

Chênh WAPE so với mô hình chính (điểm %, tầm 1–35 nếu không ghi khác; âm = tốt hơn; **đậm** = khoảng tin cậy 95% không chứa 0). "Lễ nhỏ" là 1/1, Giỗ Tổ, 30/4, 2/9 ở dữ liệu mô phỏng, và các ngày lễ khác Tết ở Hồng Kông.

| Tình huống | Dữ liệu | Quy tắc | Mọi ngày, tầm 1–7 | Mọi ngày, tầm 15–35 | Lễ nhỏ | Tết | Ngày thường |
|---|---|---|---|---|---|---|---|
| **Lịch sử đủ dài** (như app thật) | Mô phỏng | (a) | 0 | 0 | 0 | 0 | 0 |
| | | (b) | −0,11 | −0,07 (KTC chạm 0) | **−0,83** | +0,15 | 0 |
| | Hồng Kông | (a) | 0 | 0 | 0 | 0 | 0 |
| | | (b) | **−0,12** | **−0,12** | **−0,65** | **−0,68** | **+0,01** |
| **Lịch sử ngắn** | Mô phỏng | (a) | +0,81 | +0,56 | **−2,24** | **+6,22** | +0,05 |
| | | (b) | −0,38 | **−0,36** | **−1,53** | **−2,29** | 0 |
| | Hồng Kông | (a) | −0,14 | +0,35 | +2,93 (1 thời điểm) | **−2,60** | **+3,29** |
| | | (b) | **−1,43** | **−1,24** | −0,82 (1 thời điểm) | **−2,76** | **+0,12** |

**Rút ra.**
1. **Khi lịch sử đủ dài, (a) không đổi gì, còn (b) làm tốt hơn các ngày lễ nhỏ 0,65–0,83 điểm trên cả hai bộ dữ liệu**, và Tết Hồng Kông 0,68 điểm. Ngày thường gần như không đổi.
   - Chỗ duy nhất (b) không thắng: Tết trên dữ liệu mô phỏng (+0,15, khoảng tin cậy chứa 0).
   - Cách hiểu hợp lý: mỗi hệ số (dịp lễ, ngày lệch) chỉ có vài lần quan sát, nên co về ×1 bớt được nhiễu. Tết trong bộ sinh rất đều đặn, nên co lại không được lợi gì.
2. **Khi lịch sử ngắn, (b) tốt hơn (a) ở gần như mọi cột.** (a) hỏng nặng hai lần:
   - Tết mô phỏng: +6,22 điểm;
   - ngày thường Hồng Kông: +3,29 điểm. Nhiều khả năng vì (a) coi dịp lễ chưa đủ 2 lần là ngày thường **cả lúc học**, nên lượng khách dồn dịp Tết bị tính vào mức chung và vào hệ số chỉnh mức 28 ngày. Điều này chưa kiểm riêng.

   Chỗ duy nhất (a) hơn (b) là lễ nhỏ trên dữ liệu mô phỏng (−2,24 so với −1,53).
3. Vậy **(b) không chỉ an toàn khi thiếu dữ liệu, mà còn tốt hơn hoặc ngang ở chính tình huống app sẽ chạy.**
4. Lần tách này chỉ là cách nhìn khác trên **cùng các lần chạy** của §13.2–13.3, không chỉnh thêm tham số nào. Điều đã nói ở §13.6 vẫn đúng: (b) được nghĩ ra sau khi xem kết quả.

---

## Phụ lục A — Số liệu đã đo (DB ngày 2026-10-14, kiểm lại 2026-10-16)

### A.1 Các lần chạy `backfill` (Q1)

| Ngày chạy | Ngày khởi hành từ | đến | Số chuyến |
|---|---|---|---|
| 2026-07-20 | 2026-04-27 | 2026-07-19 | 1.260 |
| 2026-07-22 | 2026-07-20 | 2026-07-21 | 30 |
| 2026-07-27 | 2026-07-22 | 2026-07-26 | 75 |
| 2026-08-04 | 2026-07-27 | 2026-08-03 | 120 |
| 2026-09-11 | 2026-08-02 | 2026-09-10 | 571 |
| 2026-09-21 | 2026-09-11 | 2026-09-20 | 150 |

`COMPLETED`: từ 2026-04-27 06:00 đến 2026-09-20 18:00, tổng 2.065 chuyến.

### A.2 Lấp đầy trung bình theo tuần (Q2) — cho thấy hình răng cưa

| Tuần (T2) | n | Lấp đầy | Lần chạy tạo ra |
|---|---|---|---|
| 04-27 | 100 | 0,663 | 07-20 |
| 05-04 | 97 | 0,668 | 07-20 |
| 05-11 | 102 | 0,677 | 07-20 |
| 05-18 | 98 | 0,681 | 07-20 |
| 05-25 | 101 | 0,687 | 07-20 |
| 06-01 | 99 | 0,696 | 07-20 |
| 06-08 | 96 | 0,704 | 07-20 |
| 06-15 | 95 | 0,725 | 07-20 |
| 06-22 | 98 | 0,723 | 07-20 |
| 06-29 | 98 | 0,727 | 07-20 |
| 07-06 | 102 | 0,736 | 07-20 |
| 07-13 | 97 | 0,747 | 07-20 |
| 07-20 | 95 | 0,743 | 07-22, 07-27 |
| 07-27 | 97 | 0,746 | 08-04, 09-11 |
| **08-03** | 94 | **0,714** | 08-04, 09-11 |
| 08-10 | 100 | 0,720 | 09-11 |
| 08-17 | 97 | 0,723 | 09-11 |
| 08-24 | 100 | 0,738 | 09-11 |
| 08-31 | 101 | 0,744 | 09-11 |
| 09-07 | 97 | 0,738 | 09-11, 09-21 |
| 09-14 | 97 | 0,747 | 09-21 |

### A.3 Tuyến (Q3)

| id | km | phút | Loại xe gợi ý | Đi | Đến |
|---|---|---|---|---|---|
| 1 | 120 | 120 | 1 | Bến xe Mỹ Đình | Bến xe Niệm Nghĩa |
| 2 | 300 | 360 | 1 | Bến xe Miền Đông | Bến xe Đà Lạt |
| 3 | 100 | 120 | 2 | Bến xe Đà Nẵng | Bến xe Huế |
| 4 | 120 | 90 | 2 | Bến xe Niệm Nghĩa | Bến xe Mỹ Đình |
| 5 | 180 | 210 | 1 | Bến xe Cần Thơ | Bến xe Miền Đông |
| 6 | 1700 | 1800 | 2 | Bến xe Mỹ Đình | Bến xe Miền Đông |
| 8 | 1800 | 3000 | 2 | Bến xe Mỹ Đình | Bến xe Cần Thơ |
| 9 | 4500 | 4500 | 2 | A | d |

Bộ sinh chỉ dùng tuyến 1–5. Các tuyến trên 8 giờ bị loại bởi `MAX_ROUTE_DURATION_MINUTES`.

### A.4 Trạng thái và nguồn gốc dữ liệu (Q4, Q5)

| Nguồn | Trạng thái | Số chuyến |
|---|---|---|
| backfill | COMPLETED | 2.061 |
| backfill | CANCELLED | 145 |
| khác | COMPLETED | 4 |
| khác | CANCELLED | 1 |
| khác | ACTIVE | 6 |
| khác | DEPARTED | 5 |
| khác | PENDING_APPROVAL | 1 |

### A.5 Chặn trần và chuyến tăng cường (Q6, Q7)

- 2.065 chuyến hoàn thành, 94 chuyến chạm 100% (4,6%), 0 chuyến tăng cường.

| Tuyến | Giờ | n | Chạm 100% | Tỉ lệ |
|---|---|---|---|---|
| 3 | 18 | 141 | 49 | 35% |
| 3 | 06 | 136 | 26 | 19% |
| 2 | 18 | 144 | 16 | 11% |
| 5 | 18 | 134 | 2 | 1,5% |
| 2 | 06 | 128 | 1 | 0,8% |

**Giá (Q8):**
- 6 mức giá trên 5 tuyến; tuyến 2–5 mỗi tuyến 1 mức.
- Tuyến 1 có 3 mức: 120.000đ × 415 chuyến, 250.000đ × 1, 20.000đ × 1.

**Độ biến thiên** (roadmap §9):
- `ticketsSold` 0,32–0,35, tỉ lệ lấp đầy 0,08–0,11;
- lấp đầy trung bình theo cỡ xe 0,700 / 0,705 / 0,702.

### A.6 Dự báo tại 2026-09-21 (`forecast_replica.py`)

- Hệ số thứ T2..CN: 0,943 / 0,957 / 0,951 / 0,943 / 0,949 / 1,128 / 1,132.
- Dấu `*` = vượt 0,90.

| Tuyến | Giờ | n | Xu hướng (điểm %/tuần) | TB 7 ngày | T3 22 | T4 23 | T5 24 | T6 25 | T7 26 | CN 27 | T2 28 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 3 | 06 | 136 | +0,36 | 92% | 88 | 87 | 87 | 87 | *100 | *100 | 87 |
| 2 | 18 | 144 | +0,43 | 89% | 86 | 86 | 85 | 86 | *100 | *100 | 85 |
| 3 | 18 | 141 | +0,43 | 96% | *93 | *92 | *92 | *92 | *100 | *100 | *92 |
| 2 | 06 | 128 | +0,51 | 84% | 82 | 82 | 81 | 81 | *97 | *97 | 81 |
| 5 | 18 | 134 | +0,54 | 79% | 79 | 79 | 78 | 79 | *94 | *94 | 78 |
| 1 | 18 | 130 | +0,46 | 81% | 77 | 77 | 76 | 77 | *92 | *92 | 77 |
| 1 | 06 | 140 | +0,33 | 75% | 72 | 72 | 71 | 72 | 85 | 86 | 72 |
| 5 | 06 | 136 | +0,37 | 74% | 72 | 72 | 71 | 72 | 85 | 86 | 71 |
| 3 | 12 | 137 | +0,38 | 73% | 72 | 71 | 71 | 71 | 85 | 85 | 71 |
| 4 | 18 | 132 | +0,31 | 71% | 68 | 67 | 67 | 67 | 80 | 80 | 67 |
| 2 | 12 | 140 | +0,35 | 67% | 65 | 65 | 64 | 65 | 77 | 78 | 65 |
| 4 | 06 | 139 | +0,35 | 66% | 64 | 64 | 64 | 64 | 76 | 76 | 64 |
| 1 | 12 | 145 | +0,35 | 62% | 59 | 59 | 58 | 59 | 70 | 70 | 59 |
| 5 | 12 | 141 | +0,26 | 58% | 58 | 58 | 57 | 57 | 68 | 69 | 57 |
| 4 | 12 | 138 | +0,27 | 53% | 52 | 52 | 51 | 52 | 61 | 62 | 51 |

17 tín hiệu tăng cường; backtest 194 điểm, MAPE 4,29% so với 7,43%.

### A.7 Ngày lễ so với cùng thứ trong ±14 ngày (Q9)

| Ngày | Thứ | Lấp đầy | n | Cùng thứ lân cận | Tỉ số |
|---|---|---|---|---|---|
| 2026-04-29 (trước lễ) | T4 | 0,638 | 14 | 0,641 | 0,995 |
| 2026-04-30 | T5 | 0,632 | 15 | 0,634 | 0,996 |
| 2026-05-01 | T6 | 0,619 | 15 | 0,638 | 0,970 |
| 2026-05-02 | T7 | 0,749 | 13 | 0,776 | 0,966 |
| 2026-05-03 (chiều quay lại) | CN | 0,745 | 15 | 0,766 | 0,972 |
| 2026-08-31 | T2 | 0,695 | 15 | 0,703 | 0,989 |
| 2026-09-01 | T3 | 0,708 | 15 | 0,708 | 1,000 |
| 2026-09-02 | T4 | 0,687 | 15 | 0,695 | 0,989 |
