# Quyết định thiết kế lại Demand Forecast (chốt 2026-10-21)

> **File này là nguồn chuẩn cho các quyết định của đợt thiết kế lại.** Đọc trước khi làm bất kỳ bước nào.
> - Tiến độ từng bước và nhật ký phiên: [`forecast_progress.md`](forecast_progress.md).
> - Lý do, số đo và lịch sử thảo luận: [`forecast_discussion.md`](forecast_discussion.md). Mục 11–12 là phần dẫn thẳng tới các quyết định này.
>
> **Cách duyệt.** Ngày 2026-10-21, Claude đưa bảng 7 điểm (mục 1) cho chủ dự án chốt. Trong cùng phiên, chủ dự án hỏi và được giải thích hai chỗ: G1' + EM, K=0 có phải hồi quy Poisson không, và quy tắc "ít nhất 2 lần". Sau đó chủ dự án trả lời: *"oke, tôi hiểu rồi. tôi duyệt."*
>
> **Phạm vi của chữ "duyệt".** Chỉ những điểm ở mục 1 được duyệt:
> - QĐ-1…QĐ-7: vòng đầu;
> - QĐ-8, QĐ-9: chốt cùng ngày, khi thảo luận GĐ1. Chủ dự án nói *"oke. tôi chọn các ý kiến bạn đề xuất"*, và yêu cầu giá trị cột bằng tiếng Anh.
> - QĐ-10…QĐ-13: chốt cùng ngày, khi thảo luận GĐ2. Chủ dự án nói *"oke. tôi duyệt tất cả"*.
>
> Những dòng ở mục 4 còn ghi "chưa chốt" sẽ được quyết khi thảo luận giai đoạn tương ứng.
>
> **Sửa file này** chỉ khi chủ dự án duyệt thay đổi. Mỗi thay đổi ghi vào mục 6; không xóa im lặng dòng cũ.

---

## 1. Các quyết định

| # | Chủ đề | Quyết định |
|---|---|---|
| QĐ-1 | Thuật toán | **G1' + EM, K=0**: hồi quy Poisson theo lịch, có phạt ridge, học gộp nhiều chuỗi, có EM khôi phục nhu cầu bị chặn trần. Viết tay bằng Java, không thêm thư viện |
| QĐ-2 | Đơn vị dự báo | **B1**: nhu cầu của khung giờ (số khách theo tuyến × giờ khởi hành × ngày), thay cho tỉ lệ lấp đầy từng chuyến |
| QĐ-3 | Lễ và mùa | **C2**: hệ thống tự học từ dữ liệu, không có hệ số nhập tay. Hệ số lễ **luôn được dùng nhưng co về ×1 bằng ridge (λ = 2)**: ít dữ liệu thì kéo mạnh, nhiều dữ liệu thì kéo nhẹ. Đây là quy tắc (b), chốt 2026-10-22, thay quy tắc "ít nhất 2 lần" |
| QĐ-4 | Chiều tuyến | **Thêm thuộc tính "chiều" cho `Route`**. Đây là đổi schema |
| QĐ-5 | Tầm dự báo | **7 ngày** cho thẻ Recommendation và What-if; **đến 35 ngày** chỉ để xem trước |
| QĐ-6 | Non-Goal §4 | **Chỉ sửa chữ**: "moving average / linear regression" → "mô hình thống kê (hồi quy Poisson theo lịch) viết bằng Java". Vẫn không có mô hình ML, không thêm thư viện |
| QĐ-7 | Dữ liệu | **Viết lại bộ sinh, rồi xóa và sinh lại dữ liệu lịch sử** trong DB thật. Phải backup trước |
| QĐ-8 | Hướng dòng khách dịp lễ (chi tiết của QĐ-4) | Trường `Route.holidayFlow`, kiểu enum `HolidayFlow { LEAVING_HUB, TOWARD_HUB }`. Để trống nghĩa là không theo chiều. Admin chọn, không bắt buộc. **Chỉ gán khi biết chắc chiều đông; không chắc thì để trống** |
| QĐ-9 | Lịch ngày lễ | 5 dịp theo Điều 112 Bộ luật Lao động 2019, neo vào ngày lễ theo luật hoặc âm lịch. Lưu trong một lớp Java dùng chung, kèm bảng ngày âm lịch tra sẵn có ghi nguồn (2023–2032) |
| QĐ-10 | Bộ sinh dữ liệu mới | Từ 01/01/2023, tất định theo ngày; nhu cầu theo xe chuẩn; kỳ nghỉ sinh theo quy tắc; mỗi xe/tài xế 1 chuyến/ngày, bằng lái còn hạn; chuyến thêm chỉ trong số xe rảnh; bão là đóng tuyến; xuất nhu cầu thật ra CSV khi bật tham số |
| QĐ-11 | Chạy thử và tổng duyệt GĐ3 | Bản sao riêng `busmanagement_gd2`; hai script xoá dữ liệu cũ và trừ km ở cả hai cột odometer |
| QĐ-12 | Màn danh sách chuyến | Chuyến chưa kết thúc luôn hiện; chuyến đã kết thúc chỉ trong 7/30/90 ngày (mặc định 30) |
| QĐ-13 | Đo lại bằng Python | Đo lại K, quy tắc "ít nhất 2 lần", λ, EM, cửa sổ lễ, chiều tuyến (kể cả trên dữ liệu Hồng Kông) trên dữ liệu mới |
| QĐ-14 | Cách làm GĐ3 | Sửa bộ sinh để né xe/người của chuyến có sẵn (lỗi #33); sao lưu và khôi phục thử; tổng duyệt lại trên bản sao; làm ngay; xuất nhu cầu thật; sửa tài liệu; giữ bản sao tới hết GĐ5 |

Thứ tự thực hiện (mục 3) đi kèm các quyết định này.

---

## 2. Chi tiết từng quyết định

### QĐ-1. Thuật toán: G1' + EM, K=0

**Là gì.** Bản tham chiếu là code Python của bước 0: `forecast_research/step0/methods.py:378-468` (hàm `slot_glm_task`), lõi IRLS ở `methods.py:126` (hàm `poisson_irls`).
- Với mỗi chuỗi (tuyến × giờ khởi hành) và mỗi ngày:
  ```
  log(nhu cầu kỳ vọng) = mức của chuỗi
                       + hệ số thứ trong tuần (riêng từng chuỗi)
                       + xu hướng gấp khúc (mỗi 182 ngày một khúc)
                       + hiệu ứng (dịp lễ, ngày lệch so với lễ), gộp theo chiều tuyến, kéo về ×1 bằng ridge (λ = 2; QĐ-3)
                       + phần lệch lễ riêng của chuỗi, kéo về mức chung bằng ridge (λ = 2)
  ```
- Mọi chuỗi khớp chung một lần bằng IRLS, trên số đếm đã chia cho trung bình của chuỗi.
- **K = 0**: không có thành phần sin/cos theo năm.
- **Lúc dự báo:**
  - xu hướng giữ nguyên ở giá trị tại ngày dự báo, không kéo ra tương lai;
  - nhân thêm hệ số chỉnh mức = Σ thực tế / Σ mô hình trên 28 ngày gần nhất không có lễ.
- **EM**, 5 vòng (chủ dự án chốt ngày 2026-10-22, ý 2 của M-15; ban đầu là 15 vòng):
  - Một khung bán hết vé chỉ cho biết "nhu cầu ≥ số ghế".
  - Bước E thay số đó bằng E[nhu cầu | nhu cầu ≥ số ghế], tính theo phân phối nhị thức âm.
  - Bước M khớp lại mô hình Poisson.
  - Đây là EM dạng thực dụng, không phải EM chính xác (`forecast_discussion.md` §12.7).

**Cài đặt.**
- Viết tay bằng Java 17 (`pom.xml:30`), không thêm thư viện. Smile có Poisson GLM, nhưng bản đó cần Java 21 (§11).
- Python chỉ dùng để nghiên cứu và đối chứng, nằm ngoài app.

**Vì sao chọn** (§12.3–12.6):
- **Dữ liệu mô phỏng có chặn trần:** tốt nhất ở cả 3 seed. Thắng app hiện tại 3,7–4,2 điểm WAPE; thắng Prophet, G2 và LightGBM 1,2–2,8 điểm.
- **Dữ liệu thật (Hồng Kông):** nhóm hai. Thắng seasonal naive, Holt-Winters và LR; ngang Prophet; thua LightGBM.
- **Giải thích được:** hệ số đọc thẳng ra thành bảng, ví dụ "Tết D−3, chiều về ×1,9".

**Phải kiểm lại ở bước 1, trên dữ liệu sinh mới với seed mới:**
- K = 0 được chọn *sau khi* xem kết quả (§12.5).
- λ = 2 và 15 vòng EM chưa tinh chỉnh (§12.7).
- **Đã kiểm ở GĐ2** (`forecast_discussion.md` §13): K = 0 và λ = 2 giữ nguyên. 5 vòng EM cho kết quả như 15 vòng nên chốt 5 vòng; số lần khớp giảm từ 16 xuống 6.

**Hệ quả đã biết.**
- Tính toán nặng: bản Python mất 10–25 giây cho mỗi lần khớp.
- `buildForecast()` hiện được gọi từ 3 nơi: `ForecastController.java:44`, `RecommendationService.java:100` và `WhatIfSimulationService.java:147`.
- Vì vậy nhiều khả năng phải tính một lần mỗi ngày rồi cache. Cách làm chưa chốt (M-5).

### QĐ-2. Đơn vị: nhu cầu của khung giờ (B1)

**Là gì.** Dự báo số khách *muốn* đi theo tuyến × giờ khởi hành × ngày, thay cho tỉ lệ lấp đầy của từng chuyến.

**Vì sao** (§11, §12.4):
- Tỉ lệ lấp đầy bị kẹp ở 100%, nên không thấy được nhu cầu vượt quá một xe. Ở thí nghiệm mô phỏng, app hiện tại có recall cờ tăng cường 0,09 và bỏ lại 18,4% khách dịp Tết.
- Chỉ đơn vị nhu cầu mới trả lời được câu "cần mấy chuyến".
- Doanh thu chuyến tăng cường đúng ra là phần khách tràn. Hiện code tính bằng tỉ lệ × sức chứa (`RecommendationService.java:213-215`).
- Đã chọn C2 thì bắt buộc phải B1: Tết làm nhu cầu gấp đôi cũng chỉ hiện ra là 100%.

**Hệ quả.**
- **Đảo ngược Developer Note** ở roadmap §9: *"Why Demand Forecast predicts the occupancy RATE and not `ticketsSold`"* (câu chốt *"Do not 'improve' this into a ticket-count forecast"*).
  - Lập luận chính của note đó: ngưỡng 0,90 (`Trip.needsReinforcement()`) phải là định nghĩa **duy nhất** của "chuyến nóng".
  - **Bước 3 phải trả lời lập luận này**, không được tạo thêm ngưỡng thứ hai (M-4).
  - Note được giữ nguyên tới khi bước 2–3 xong, vì nó vẫn đúng với code hiện tại.
- Số vé ghi trong DB = min(nhu cầu, số ghế), nên bắt buộc phải có EM (QĐ-1).
- Phải viết lại bộ sinh cùng lúc (QĐ-7). Nếu không, dự báo trên dữ liệu hiện tại sẽ tệ hơn (§0.2).

### QĐ-3. Lễ và mùa: tự học từ dữ liệu (C2), hệ số lễ co về ×1 (quy tắc (b), chốt 2026-10-22)

**Là gì.**
- Mô hình tự học **độ lớn** hiệu ứng lễ. Không ai nhập hệ số, tức không làm C1 hay C3.
- **Lịch ngày lễ** (ngày nào là Tết, 30/4, 2/9…) vẫn là đầu vào biết trước. Chi tiết lịch đã chốt ở QĐ-9.
- Hệ số tính cho từng cặp (dịp lễ, ngày lệch so với lễ), ví dụ "Tết D−3". Mỗi hệ số gồm phần gộp theo chiều tuyến cộng phần lệch riêng của tuyến (QĐ-1).

**Quy tắc (b): co hệ số lễ về ×1.** Chủ dự án chốt ngày 2026-10-22 (M-15 ý 1, *"oke tôi chọn (b), ghi lại đi"*). Quy tắc này thay quy tắc (a) "ít nhất 2 lần".
- Mọi hệ số (dịp lễ, ngày lệch) **luôn được học và dùng**, kể cả dịp mới thấy 1 lần. Không đếm số lần, không bỏ dữ liệu nào.
- Phần gộp theo chiều tuyến bị ridge kéo về ×1 (tức 0 trên thang log), với **λ = 2**. Đây đúng là con số đã dùng để kéo phần lệch riêng của chuỗi về mức chung (QĐ-1), nên cả hai phần dùng chung một cơ chế. λ không được chỉnh riêng cho phần này.
- **Mức kéo tự thay đổi, không có công tắc:**
  - phạt ridge có độ mạnh cố định, còn sức của dữ liệu tăng theo số quan sát;
  - dịp mới thấy 1 lần thì bị kéo mạnh; dịp đã thấy nhiều lần thì gần như giữ nguyên số đo.
  - Ví dụ minh họa (không phải số đo): dữ liệu cho ×2,5 thì 1 lần → khoảng ×2,2; 3 lần → khoảng ×2,4; 10 lần → gần ×2,5.
- **Cơ sở** (`forecast_discussion.md` §13.2, §13.3, §13.7):
  - **Lịch sử ngắn:** (b) tốt hơn (a) gần như ở mọi chỗ. (a) hỏng nặng hai lần: Tết trên dữ liệu mô phỏng (+6,2 điểm) và ngày thường trên dữ liệu Hồng Kông (+3,3).
  - **Lịch sử đủ dài, như app thật:** (a) không đổi gì. (b) tốt hơn ở lễ nhỏ 0,65–0,83 điểm trên cả hai bộ dữ liệu, ở Tết Hồng Kông 0,68 điểm; ngày thường không đổi.
  - Chỗ (b) kém hơn: Tết trên dữ liệu mô phỏng, +0,15 đến +0,30 điểm.
- **Điều phải nói thật:** (b) được nghĩ ra **sau khi** thấy (a) thất bại trên dữ liệu mô phỏng, giống K = 0 ở bước 0. Bù lại, λ không chỉnh riêng, và (b) thắng trên hai bộ dữ liệu độc lập.
- **Câu nói khi bảo vệ:** "Dịp lễ nào càng ít dữ liệu thì hệ số càng được kéo về ×1; càng nhiều dữ liệu thì càng tin số đo."
- **Tác động trong đồ án:** dữ liệu có ít nhất 3 năm, nên khi app chạy, cái kéo luôn bật nhưng nhẹ. Nó tạo khác biệt lớn nhất ở các mốc backtest đầu tiên (ví dụ Tết 2024 trên dữ liệu mô phỏng) và ở tình huống "triển khai thật mà dữ liệu còn ít".
- Đóng M-6, vì không còn phải đếm số lần.

**Quy tắc cũ (a) "ít nhất 2 lần", đã thay ngày 2026-10-22.**
- Nội dung: dịp mới thấy 0 hoặc 1 lần thì coi là ngày thường (×1), cả lúc học lẫn lúc dự báo; từ 2 lần trở lên thì dùng hệ số.
- Được chọn ngày 2026-10-21 dựa trên dữ liệu thật Hồng Kông: ở Tết 2025, G1', K=0 kém SN4 sạch 2,7 điểm (§12.3).
- Bị thay vì ở GĐ2 nó cho kết quả trái ngược giữa hai bộ dữ liệu (§13).

**Ràng buộc kéo theo:** dùng toàn bộ lịch sử làm dữ liệu học. **Không được cắt ngắn dữ liệu học để chạy nhanh hơn**: càng ít lần lễ thì hệ số càng bị kéo về ×1.

### QĐ-4. Thuộc tính chiều tuyến

**Là gì.** Thêm vào `Route` một thuộc tính cho biết chiều tuyến, ví dụ đi tỉnh / về thành phố. Hiện `Route` không có trường nào như vậy (`domain/Route.java`). Đây là **đổi schema**.

**Vì sao** (§12.4):
- Ở ngày thường, thuộc tính này gần như không đổi gì.
- Ở Tết, nó giảm WAPE 3,8 điểm (Tết 2026) và 5,8 điểm (Tết 2025).
- Trong bảng lên kế hoạch Tết, khách bị bỏ lại là 2,7% so với 4,7% khi không có thuộc tính.

**Chi tiết** (tên trường, kiểu và giá trị, có bắt buộc hay không, nhãn cho các tuyến đang có): **đã chốt ở QĐ-8**.

### QĐ-5. Tầm dự báo

- **7 ngày** cho thẻ Recommendation và What-if, giống hiện tại.
- **Đến 35 ngày** chỉ để xem trước trên màn hình dự báo.
- **Cơ sở** (§12.6, bảng theo tầm của `report.py`): sai số của G1', K=0 tăng ít khi nhìn xa.
  - Dữ liệu thật (A): từ 10,40 (ngày 1–7) lên 12,28 (ngày 29–35).
  - Dữ liệu mô phỏng (B): từ 22,32 lên 22,48 (bảng này tính cả ngày bão).

### QĐ-6. Non-Goal §4

- Câu cũ: *"Demand Forecast uses simple statistical methods (moving average / linear regression) written in plain Java — no new ML dependency."*
- Sửa thành: mô hình thống kê (hồi quy Poisson theo lịch) viết bằng Java. **Vẫn** không có mô hình ML hay deep learning, **vẫn** không thêm thư viện.
- Đã sửa trong `THESIS_ROADMAP.md` §4 ngày 2026-10-21, kèm ghi chú rằng code vẫn dùng mô hình cũ cho tới khi bước 2 xong.
- LightGBM và Prophet chỉ dùng làm đối chứng bằng Python khi bảo vệ, không đưa vào app.

### QĐ-7. Dữ liệu: bộ sinh mới, xóa và sinh lại

**Là gì.** Viết lại bộ sinh `config/HistoricalDataBackfill.java`, rồi xóa và sinh lại dữ liệu lịch sử trong DB thật.

**Yêu cầu với bộ sinh mới** (§11, §12.2):
- Sinh **nhu cầu** trước, rồi cắt theo số ghế: vé bán = min(nhu cầu, số ghế).
- Có **ít nhất 3 năm** và có Tết, để có 2 lần Tết trước lần Tết dùng kiểm tra.
- **Cố ý khác cấu trúc mô hình**, để tránh bẫy "dữ liệu tự chứng minh" (§4.4):
  - lễ neo theo kỳ nghỉ chính thức, còn mô hình chỉ biết ngày lễ;
  - độ lớn hiệu ứng thay đổi theo năm;
  - mùa có dạng "bướu";
  - mức nhu cầu trôi và nhảy bậc;
  - nhiễu nhị thức âm, có ngày bão.
  - Bản tham khảo: `forecast_research/step0/synth_generator.py`.
- **Không lặp lỗi "xu hướng reset sau mỗi lần chạy bổ sung"** của bộ sinh hiện tại (số dòng dưới đây là của bản trước GĐ2):
  - `HistoricalDataBackfill.java:187` lấy mốc là `today − BACKFILL_DAYS` mỗi lần chạy;
  - `:374` tính xu hướng từ `dayIndex` theo mốc đó;
  - xem `forecast_discussion.md` §2.

**Điều kiện thực hiện:**
- Phải backup DB trước.
- Chủ dự án xác nhận lần cuối **ngay trước** thao tác xóa. Ngày 2026-10-21 chỉ duyệt về nguyên tắc.

**Chưa chốt:** độ lớn các hiệu ứng, số năm cụ thể, phạm vi xóa, cách giữ dữ liệu không do bộ sinh tạo ra (M-8).

### QĐ-8. Hướng dòng khách dịp lễ trên `Route` (chốt 2026-10-21 khi thảo luận GĐ1; đóng M-2)

**Là gì.**
- Thêm trường `Route.holidayFlow`, kiểu enum `HolidayFlow`, lưu bằng `@Enumerated(EnumType.STRING)` vào cột `holiday_flow`. Cột **cho phép null**.
- **Giá trị lưu trong DB là tên hằng tiếng Anh**, theo quy ước chung của mọi enum trong dự án (`IncidentType`, `BusStatus`, `TripStatus`). Chữ tiếng Việt chỉ xuất hiện trên giao diện.

| Giá trị trong DB | Nhãn trên giao diện | Nghĩa |
|---|---|---|
| `LEAVING_HUB` | Rời TP lớn | Tuyến chở khách rời một trung tâm tập trung lao động (Hà Nội, TP.HCM) |
| `TOWARD_HUB` | Về TP lớn | Tuyến chở khách về một trung tâm như vậy |
| `NULL` | Không theo chiều | Hai đầu tuyến cùng cỡ, hoặc tuyến chưa được phân loại |

- Trên form tuyến, ô này tên là "Hướng dòng khách dịp lễ", **không bắt buộc**, có một dòng giải thích.
- **"TP lớn" là phán đoán nghiệp vụ của Admin**, dựa trên nơi tập trung người lao động. Không suy ra từ cấp hành chính: Hải Phòng, Cần Thơ và Huế cũng là thành phố trực thuộc trung ương.

**Cách chọn nhãn** (bổ sung 2026-10-21, chủ dự án chốt):
- **Nhãn thật ra hỏi: tuyến đông vào đầu hay cuối dịp lễ?**
  - "Rời TP lớn" là tuyến đông ở **đầu** dịp lễ, khi người ta rời nơi làm việc để về quê hoặc đi chơi.
  - "Về TP lớn" là tuyến đông ở **cuối** dịp lễ, khi người ta quay lại.
  - Với tuyến giữa hai TP lớn (ví dụ Hà Nội ↔ TP.HCM), chọn theo dòng khách chính nếu biết.
- **Quy tắc: chỉ gán nhãn khi biết chắc chiều đông. Không chắc thì để trống.** Lý do: **nhãn đúng tốt hơn để trống, để trống tốt hơn nhãn sai.**
  - Nhãn quyết định tuyến học chung hệ số lễ với nhóm nào. Hệ số lễ của một tuyến gồm phần chung của nhóm, cộng phần lệch riêng của tuyến (bị kéo về 0).
  - **Nhãn đúng:** tuyến mượn được hình dạng lễ của các tuyến cùng kiểu, nên dự báo tốt ngay cả khi lịch sử riêng còn ít. Các tuyến cùng nhóm cũng có thêm dữ liệu.
  - **Nhãn sai:** tuyến bị kéo về hình dạng ngược, và còn làm nhiễu hệ số chung của cả nhóm.
  - **Để trống:** tuyến học chung với nhóm "Không theo chiều". Mất một ít độ chính xác, nhưng ít hơn đoán sai.
- **Phạm vi ảnh hưởng.**
  - Nhãn chỉ tác động tới những ngày nằm trong cửa sổ lễ, khoảng 67 ngày/năm với cửa sổ hiện tại. Ngày thường không đổi, vì hệ số thứ trong tuần và mức nhu cầu là riêng của từng tuyến.
  - Nhãn quan trọng nhất ở những năm đầu và với tuyến ít dữ liệu. Khi tuyến đã có riêng 2–3 lần Tết, phần lệch riêng sửa lại được phần lớn sai lệch.
  - Mức độ chưa được đo cho trường hợp một tuyến gán sai. Bước 0 mới đo trường hợp nặng nhất (gộp mọi tuyến làm một nhóm): sai số dịp Tết tăng 3,8–5,8 điểm, trên dữ liệu mô phỏng.
- Không đặt tên `direction`, vì trong ngành xe khách "chiều đi / chiều về" thường chỉ lượt đi và lượt về của cùng một tuyến.
- Trường này **chỉ dùng cho dự báo**. Không đụng phân công, validation hay giá vé.

**Dùng thế nào.**
- Mô hình gộp hệ số lễ theo 3 nhóm: `LEAVING_HUB`, `TOWARD_HUB`, và một nhóm chung cho mọi tuyến để trống (giống nhóm "cross" ở bước 0).
- Bộ sinh mới (GĐ2) **đọc nhãn từ `Route`**, không giữ bảng riêng như script Python.
  - Hệ quả: nếu nhãn sai, dữ liệu mô phỏng vẫn "khớp", vì nó được sinh theo chính nhãn đó.
  - Với dữ liệu thật, nhãn sai sẽ làm dự báo kém đi.
- Đổi nhãn có hiệu lực ngay ở lần tính dự báo kế tiếp, vì mô hình khớp lại từ đầu. Không cần chuyển đổi dữ liệu.

**Ràng buộc khi cài.**
- `RouteService.updateRoute()` chép từng field một (`RouteService.java:135-137`). Phải thêm field mới vào đó, kèm test; nếu không, mỗi lần sửa tuyến giá trị sẽ mất im lặng (cùng kiểu lỗi #16).
- Cột cho phép null, để `ddl-auto=update` thêm cột an toàn vào bảng đã có dữ liệu. Tiền lệ: `Trip.createdAt`, `Route.suitableBusType`.
- `DataInitializer` (profile `demo`) gán nhãn cho các tuyến mẫu.

**Nhãn cho 8 tuyến hiện có.** Chủ dự án tự gán qua form thật.

| # | Tuyến | Nhãn | Vì sao |
|---|---|---|---|
| 1 | Mỹ Đình → Niệm Nghĩa | `LEAVING_HUB` | Khách rời Hà Nội |
| 2 | Miền Đông → Đà Lạt | `LEAVING_HUB` | Khách rời TP.HCM |
| 3 | Đà Nẵng → Huế | để trống | Hai đầu cùng cỡ; bước 0 cũng xếp là "cross" |
| 4 | Niệm Nghĩa → Mỹ Đình | `TOWARD_HUB` | Chiều ngược của tuyến 1 |
| 5 | Cần Thơ → Miền Đông | `TOWARD_HUB` | Khách về TP.HCM |
| 6 | Mỹ Đình → Miền Đông (30h) | để trống | Hai đầu đều là TP lớn. Nhãn "Về TP lớn" (đông sau Tết khi người lao động quay lại TP.HCM) có lý nhưng chưa chắc, nên **để trống theo quy tắc trên** (chủ dự án chốt 2026-10-21). Tuyến có 1 chuyến, 0 chuyến hoàn thành, nên nhãn chưa ảnh hưởng gì |
| 8 | Mỹ Đình → Cần Thơ (50h) | `LEAVING_HUB` | Tuyến > 8h chưa có lịch sử, nên nhãn chưa ảnh hưởng gì |
| 9 | A → d | để trống | Dữ liệu thử |

**Đã cài ở GĐ1 (2026-10-21):** enum `domain/HolidayFlow`, trường `Route.holidayFlow`, chép trong `RouteService.updateRoute()`, ô chọn trên form tuyến, cột trên danh sách tuyến, nhãn cho tuyến mẫu trong `DataInitializer`, test `RouteServiceHolidayFlowTest`. Nhãn cho 8 tuyến trên DB thật do chủ dự án tự gán qua form. Đã gán ngày 2026-10-21; kiểm bằng `SELECT`, khớp đúng bảng dưới đây.

**Phải kiểm thêm ở GĐ2.** Lợi ích của nhãn chiều mới chỉ được đo trên bộ sinh do Claude viết (thí nghiệm B), tức là có nguy cơ rơi vào bẫy "dữ liệu tự chứng minh". Ở GĐ2 cần chạy thêm biến thể "không có chiều" trên dữ liệu thật Hồng Kông, vốn có sẵn hai chiều Arrival/Departure.

### QĐ-9. Lịch ngày lễ (chốt 2026-10-21 khi thảo luận GĐ1; đóng M-1)

- **Những dịp nào.** 5 dịp theo Điều 112 Bộ luật Lao động 2019:
  - Tết Dương lịch (1/1);
  - Tết Âm lịch (mùng 1);
  - Giỗ Tổ Hùng Vương (10/3 âm lịch);
  - 30/4 và 1/5, gộp thành một dịp;
  - Quốc khánh (2/9).

  Không thêm Trung thu hay Noel: chưa có căn cứ rằng các dịp này làm tăng khách xe khách, mà lại tốn thêm hệ số.
- **Mốc neo.** Neo vào chính ngày lễ theo luật hoặc âm lịch, không neo vào kỳ nghỉ chính thức (vốn đổi theo từng năm).
  - Bước 0 đã kiểm đúng tình huống này: bộ sinh neo theo kỳ nghỉ, mô hình chỉ biết ngày lễ.
  - Neo theo kỳ nghỉ chính thức để lại làm hướng phát triển: cần nhập lại mỗi năm, và phải tra nguồn cho các năm cũ.
- **Lưu ở đâu.** Một lớp Java dùng chung cho bộ sinh (GĐ2) và bộ dự báo (GĐ5). Không đổi schema, không thêm màn hình.
  - Các lễ dương lịch: tính theo quy tắc ngày cố định.
  - Tết Âm lịch và Giỗ Tổ: lấy từ bảng ngày tra sẵn, **nguồn ghi rõ trong code**.
    - Lịch phủ trọn 01/01/2023 → 31/12/2032. Ngoài phạm vi thì từ chối trả lời (ném lỗi), không trả "ngày thường".
    - Bảng có thêm hai năm biên 2022 và 2033, chỉ để một ngày sát đầu hoặc cuối phạm vi vẫn biết được cửa sổ của năm bên cạnh. (Lúc chốt ghi là "bảng 2023–2032"; khi cài thì thấy cần hai năm biên để phạm vi 2023–2032 đúng tuyệt đối.)
    - Đối chiếu ngày 2026-10-21 với hai nguồn, đủ 24 ngày, khớp hoàn toàn:
      1. thuật toán âm lịch Việt Nam của Hồ Ngọc Đức, tính theo GMT+7, cài lại độc lập ở `forecast_research/holiday_calendar_check.py`;
      2. công cụ lịch âm của Báo Hà Tĩnh (`baohatinh.vn/cong-cu/lich-am/{dd-MM-yyyy}`).
  - Màn dự báo **cảnh báo** khi phải dự báo cho ngày nằm ngoài phạm vi bảng.
  - **Đã cài ở GĐ1 (2026-10-21):** `domain/Holiday` (5 dịp và cửa sổ), `domain/HolidayCalendar` (`covers`, `anchorOf`, `holidaysOn`), test `HolidayCalendarTest`.
- **Vì sao không cho Admin cấu hình.** Ngày lễ là sự thật khách quan, như thứ trong tuần. Nó không phải tham số nghiệp vụ như `CostParameters` (Hidden Cost #5).
- **Không chọn:**
  - tự viết thuật toán đổi lịch âm: dễ sai, khó bảo vệ;
  - bảng trong DB + màn hình Admin: thêm entity và màn hình, mà Admin vẫn phải tự tra ngày Tết;
  - thư viện ICU4J: thêm dependency, và lịch âm Trung Quốc có năm lệch lịch Việt Nam một ngày do múi giờ. Ví dụ nằm ngay trong phạm vi lịch: Tết 2030 theo Trung Quốc (GMT+8) là 03/02, theo Việt Nam (GMT+7) là 02/02.
- **Cửa sổ quanh mỗi dịp.** Tạm dùng số của bước 0 (`step0/run_synth.py:35-41`). Chốt ở GĐ2 bằng số đo.
- **Sự kiện chỉ xảy ra một lần** (ví dụ đại lễ 30/4/2025) không đưa vào lịch. Dù có đưa vào cũng không giúp gì: sự kiện không lặp lại nên không ngày nào trong tương lai dùng tới hệ số đó, và chỉ có 1 lần dữ liệu nên hệ số bị kéo mạnh về ×1 (QĐ-3). Đây là giới hạn cần nói rõ khi bảo vệ.

### QĐ-10. Bộ sinh dữ liệu mới (chốt 2026-10-21 khi thảo luận GĐ2, G2-1…G2-6; đóng M-7, M-8, M-13)

Chủ dự án duyệt toàn bộ đề xuất GĐ2: *"oke. tôi duyệt tất cả"*.

- **Khoảng thời gian (G2-1).** Từ **01/01/2023** tới hôm qua, tức có 4 lần Tết (2023–2026).
  - Mọi con số tất định theo từng ngày: seed trộn theo (tuyến, ngày, khung), mức trôi tính từ mốc cố định. Vì vậy chạy bổ sung nối tiếp liền mạch, và lỗi răng cưa #31 mất từ gốc.
  - Chạy lại thì bỏ qua cả ngày đã có. Bộ sinh **từ chối** chạy khi DB còn dữ liệu của bộ sinh cũ, nhận ra vì ngày 01/01/2023 trống.
  - Không lùi về 2020: COVID, bẫy "dữ liệu tự chứng minh", dữ liệu cũ kém liên quan (thảo luận ngày 2026-10-21).
- **Mức nhu cầu (G2-2).** Tính theo **xe chuẩn** của tuyến (`Route.suitableBusType`).
  - Mức tải ngày thường: 0,72 / 0,58 / 0,77 số ghế ở các khung 06 / 12 / 18 giờ (trung bình 5 tuyến của bước 0), nhân hệ số riêng của tuyến 0,9–1,1.
  - Trên bản sao sinh lại, mức tải trung bình cả năm (tính cả dịp lễ) là 0,73–0,82 số ghế xe chuẩn.
- **Hình dạng (G2-3).** Giữ cấu trúc bộ sinh của bước 0, tổng quát hoá từ "theo id tuyến" sang "theo `Route.holidayFlow`".
  - Gồm: thứ trong tuần theo hướng, khung giờ, mùa dạng bướu, tăng 4%/năm kèm bước ngẫu nhiên, nhảy bậc, nhiễu nhị thức âm (size 40), Tết khoảng ×3.
  - **Kỳ nghỉ chính thức sinh theo quy tắc có dao động**, không phải lịch nghỉ thật:
    - Tết nghỉ từ 2–4 ngày trước mùng 1, dài 7–9 ngày;
    - các dịp khác là ngày nghỉ theo luật, lùi hoặc nới thêm 0–1 ngày, và kéo sang cuối tuần nếu bắt đầu đúng thứ Hai hoặc kết thúc đúng thứ Sáu.
  - **Mọi độ lớn là giả định.**
- **Nhà xe quá khứ (G2-4, đóng M-13).** Mỗi khung một chuyến thường.
  - Xe ưu tiên đúng loại của tuyến, thiếu thì loại khác (giống thứ tự của AI phân công, #26).
  - **Mỗi xe và mỗi tài xế tối đa 1 chuyến/ngày; tuyến ≤ 8h; bằng lái còn hạn vào ngày chạy** (`Driver.isLicenseValid`, đóng gốc lỗi #32).
  - Chuyến tăng cường theo kinh nghiệm quanh các kỳ nghỉ (chép từ bước 0), **chỉ trong số xe và tài xế còn rảnh**: 20 − 15 = 5 xe/ngày.
    - Ưu tiên khung có nhu cầu kỳ vọng cao nhất; dùng xe lớn nhất còn rảnh.
    - Khởi hành sau chuyến gốc 30/40/50 phút, nên vẫn cùng khung giờ; `isExtraTrip = true`, trỏ về chuyến gốc.
  - Vé lấp chuyến thường trước, phần tràn sang chuyến thêm.
- **Bão và hủy chuyến (G2-5, đóng M-7).** Bão là **đóng tuyến cả ngày**: mọi chuyến của tuyến hôm đó `CANCELLED`.
  - Mỗi tuyến mỗi năm có tối đa một đợt, dài 1–3 ngày, xác suất 0,8, phần lớn rơi vào tháng 7–11.
  - Thêm **6%** khung bị hủy ngẫu nhiên, như Phase 5.
  - Khung bị hủy giữ số vé đã bán trước khi hủy, và không có chuyến thêm.
  - Bộ dự báo vốn chỉ học từ chuyến `COMPLETED`, nên ngày bão tự rơi khỏi dữ liệu học, không cần cơ chế mới.
- **Nhu cầu thật (G2-6).** Tham số `-Dbackfill.truth-csv=<đường dẫn>` ghi nhu cầu thật của từng khung ra CSV. File chỉ phục vụ nghiên cứu; không ghi vào DB.
- **Đã cài ở GĐ2 (2026-10-21):**
  - `config/HistorySimulation` (mô phỏng) và `config/HistoricalDataBackfill` (xếp xe, ghi DB) viết lại.
  - `TripRepository.findDepartureTimesByRouteIdsBetween` thay `existsByRouteIdAndDepartureTime`.
  - Test: `HistorySimulationTest` (7), `HistoricalDataBackfillPlanTest` (6, kiểm đủ 1.389 ngày), `HistoricalDataBackfillGenerateTest` (5, chạy trên DB test).
  - **Kết quả trên bản sao:**
    - 20.835 khung, 21.177 chuyến (342 chuyến tăng cường, 1.360 chuyến bị hủy);
    - **21,0% khung bán hết vé**, 5,1% nhu cầu không được phục vụ;
    - **0** vi phạm bất biến;
    - DB và nhu cầu thật mà bộ sinh lên kế hoạch khớp ở **19.475/19.475** khung có chuyến chạy.
- **Bổ sung ở GĐ3 (2026-10-22, G3-1, lỗi #33).** Chuyến **có sẵn** trong DB — Admin tự tạo, hay chuyến thật về sau — giữ xe, tài xế, phụ xe và tài xế phụ của nó trong mọi ngày lịch mà nó chạm tới; hôm đó bộ sinh không xếp thêm chuyến cho họ.
  - "Có sẵn" là mọi trạng thái trừ `CANCELLED`, kể cả `COMPLETED`: với lịch sử, chuyến đã chạy là đã dùng xe. Vì vậy không dùng luật "bận" của `isBusBusy` (chỉ cho chuyến tương lai).
  - Chuyến dạng bộ sinh không bị tính, để lần chạy bổ sung ra đúng như một lần chạy liền.
  - Cài bằng `TripRepository.findResourceUseBetween` (một truy vấn scalar) và tham số `Occupied` của `planDay()`. Thêm 4 test; `mvnw test` 170/170.
  - Trước khi sửa, bản sao có 12 cặp chuyến chạy cùng lúc dùng chung xe hoặc người với chuyến của chủ dự án (4 cặp cùng xe). Sau khi sửa: **0**.

### QĐ-11. Chạy trên bản sao riêng, tổng duyệt GĐ3 (chốt 2026-10-21, G2-7)

- GĐ2 chạy trên bản sao riêng **`busmanagement_gd2`**, không dùng `busmanagement_test`, vì `mvnw test` xoá sạch schema đó.
- **Lần chạy trên bản sao chính là buổi tổng duyệt GĐ3**, bằng hai script trong `forecast_research/`:
  1. `gd3_check_old_history.sql`: chỉ đọc. Kiểm số chuyến cũ, ngày tạo, tham chiếu, odometer, và số km sẽ trừ.
  2. `gd3_delete_old_history.sql`: chạy trong một transaction. Trừ km của các chuyến `COMPLETED` cũ khỏi **cả hai** cột odometer, rồi xoá cứng chúng.
- **Tập bị xoá** được nhận theo hình dạng của bộ sinh cũ **và** ngày tạo thuộc 6 lần chạy bộ sinh cũ: đúng **2.206** chuyến. 19 chuyến của chủ dự án không đụng tới.
- **Kết quả tổng duyệt trên bản sao:**
  - 2.225 → 19 chuyến;
  - tổng odometer giảm đúng **337.860 km**;
  - tổng km kể từ lần bảo dưỡng cuối giữ nguyên **18.355**;
  - sau đó bộ sinh mới chạy khoảng 1 phút.
- **Ở GĐ3 trên DB thật:** backup, rồi chủ dự án xác nhận lần cuối, rồi chạy hai script, rồi chạy bộ sinh.

### QĐ-12. Màn danh sách chuyến có cửa sổ ngày (chốt 2026-10-21, G2-8)

- Màn `/admin/trip-management/trips` hiện **mọi chuyến chưa kết thúc**, bất kể ngày, cộng **chuyến đã kết thúc khởi hành trong N ngày gần nhất**.
  - "Đã kết thúc" suy từ FSM (`canTransition`): hiện là `COMPLETED` và `CANCELLED`.
  - N chọn được 7 / 30 / 90 ngày, mặc định 30. Không có lựa chọn "tất cả".
- Bộ lọc trạng thái giữ cùng cửa sổ. Mới nhất lên đầu.
- **Lý do:** trang cũ nạp toàn bộ chuyến. Hôm nay đã là 8,4 MB; với khoảng 21 nghìn chuyến sẽ lên khoảng 80 MB.
- **Đã cài ở GĐ2 (2026-10-21):**
  - `TripService.getTripsForList` và `TripRepository.findForTripList`; xoá `findAllWithDetails` vì không còn ai dùng.
  - Controller, template, và test `TripServiceTripListTest` (5).
  - Trên bản sao sinh lại: trang mặc định **1,76 MB** (462 chuyến), lọc COMPLETED trong 90 ngày ra 4,7 MB.
- Đây là một thay đổi riêng của Phase 1 (quản lý chuyến), làm trước GĐ3 theo đề xuất G2-8.

### QĐ-13. Đo lại bằng Python trên dữ liệu mới (chốt 2026-10-21, G2-9)

- **Đo lại các lựa chọn của bước 0** trên dữ liệu do bộ sinh mới ghi vào bản sao, chấm theo nhu cầu thật, mỗi lần đổi một yếu tố quanh mô hình đã chọn:
  - K = 0 so với K = 3;
  - quy tắc "ít nhất 2 lần", cả ba biến thể a/b/c;
  - λ (0,5 / 2 / 8);
  - số vòng EM (0 / 5 / 15);
  - cửa sổ lễ (hẹp / hiện tại / rộng);
  - có hay không thuộc tính chiều tuyến.
- **Thêm biến thể "không có chiều" trên dữ liệu thật Hồng Kông** (việc QĐ-8 để lại).
- **Script:** `forecast_research/gd2/` (`build_dataset.py`, `run_gd2.py`, `report_gd2.py`, `hk_direction.py`).
- **Kết quả:** `forecast_discussion.md` §13. Thay đổi nào so với QĐ-1, QĐ-3 hay QĐ-9 mà kết quả gợi ý đều **phải được chủ dự án duyệt**, không tự áp.
- **Đã chạy ngày 2026-10-21.** Tóm tắt:
  - mô hình chính thắng SN4 sạch và app hiện tại khoảng 2,6–2,9 điểm, thắng 14–16 điểm ở Tết 2025, 2026;
  - **K = 0, λ = 2 và EM được xác nhận**;
  - quy tắc (a) cho kết quả trái ngược giữa dữ liệu thật và dữ liệu mô phỏng, **quy tắc (b) tốt hơn trên cả hai**;
  - 5 vòng EM cho kết quả như 15 vòng;
  - chiều tuyến có ích nhưng nhỏ: 0,2–1 điểm ở Tết;
  - cửa sổ hẹp nhỉnh hơn 0,3 điểm, chỉ trên dữ liệu mô phỏng.
- Hai đề xuất đổi quyết định (**M-15**), chủ dự án đã chốt cả hai ngày 2026-10-22: EM 5 vòng (vào QĐ-1) và quy tắc (b) (vào QĐ-3). Số đo bổ sung cho quy tắc (b) ở §13.7.

### QĐ-14. Thay dữ liệu trên DB thật — cách làm GĐ3 (chốt 2026-10-22, G3-1…G3-7)

Chủ dự án duyệt cả bảy đề xuất (*"oke. tôi chốt và hãy bắt đầu làm đi từ G3-1 đến G3-7"*).

| # | Quyết định |
|---|---|
| G3-1 | Sửa bộ sinh **trước** GĐ3: chuyến có sẵn giữ xe và người của nó (lỗi #33). Chi tiết ở QĐ-10, mục "Bổ sung ở GĐ3" |
| G3-2 | **Sao lưu** bằng `mysqldump` ra `E:\Y4 S2\PRETHESIS\ProjectBackup\db\`, ngoài git, giữ tới khi bảo vệ xong. **Khôi phục thử** file đó vào một bản sao `busmanagement_gd2` mới và so từng bảng với DB thật (số dòng và `CHECKSUM TABLE`) |
| G3-3 | **Tổng duyệt lại** trên bản sao mới đó bằng code đã sửa: phần A, phần B, bộ sinh, rồi script kiểm sau khi sinh `forecast_research/gd3_check_new_history.sql`. Khi chạy trên DB thật, so DB thật với bản sao **từng chuyến** — bộ sinh tất định nên phải giống hệt |
| G3-4 | Làm GĐ3 **ngay**, đúng thứ tự kế hoạch, không dời sau GĐ5 |
| G3-5 | Khi chạy trên DB thật, xuất **file nhu cầu thật** (`-Dbackfill.truth-csv`) để cạnh file sao lưu, không đưa vào git |
| G3-6 | Tài liệu: sửa phần GĐ2 để sót (số dòng trong các bài `docs/study` và câu còn nhắc `findAllWithDetails`, "12 tuần"; `project_report.md` Warn #4) rồi build lại HTML; sau khi chạy: đóng #31, #32, #33 và cập nhật roadmap, QĐ-11, `setup_guide.md` |
| G3-7 | Giữ bản sao `busmanagement_gd2` tới hết GĐ5 làm chỗ thử các thao tác ghi, rồi xoá |

- **Điều kiện trước phần B trên DB thật:** code bản sửa đã commit (dữ liệu sinh ra từ một commit cụ thể); app đang chạy đã tắt; ảnh chụp DB thật vẫn giống lúc sao lưu (nếu khác thì sao lưu lại); **chủ dự án xác nhận lần cuối** ngay trước đó.
- **Quay lại nếu cần:** tạo lại schema `busmanagement` rỗng rồi nạp file sao lưu (`mysql.exe -uroot -p busmanagement < <file sao lưu>`). File không chứa `CREATE DATABASE` hay `USE`, nên nạp vào schema nào là đúng schema đó.
- **Đã làm ngày 2026-10-22 (trước phần B trên DB thật):**
  - G3-1: code, 4 test, 5 lần thử làm hỏng; `mvnw test` 170/170.
  - G3-2: file `busmanagement_2026-10-22_truoc-GD3.sql` (404 KB). Khôi phục vào bản sao mới: cả 11 bảng giống DB thật cả số dòng lẫn checksum.
  - G3-3 trên bản sao: phần A đúng số dự kiến (2.206 chuyến cũ, 2.061 `COMPLETED`, 337.860 km, 0 tham chiếu); phần B 2.225 → 19 chuyến, odometer 356.245 → 18.385 km, km kể từ bảo dưỡng giữ 18.355; bộ sinh chạy 74 giây: **21.192 chuyến** (342 tăng cường, 1.363 hủy), 01/01/2023 → 21/10/2026, 20,9% khung bán hết; script kiểm sau khi sinh: **0 vi phạm** ở mọi mục. Chạy cùng script trên dữ liệu cũ của DB thật thì bắt đúng các lỗi đã biết (69 chuyến của #32, 8 xe + 12 người của #33), nên script không đậu suông.
  - G3-6 phần trước: trong 7 bài học và `Proj_functions_summary.md`, 121 chỗ trích số dòng được ánh xạ lại theo code mới (từng chỗ đã soát với dòng code cũ), 9 chỗ sửa tay; build lại 7 trang HTML.

---

## 3. Thứ tự thực hiện

Theo `forecast_discussion.md` §11, duyệt cùng 7 quyết định. **Mỗi bước phải có kế hoạch chi tiết được chủ dự án duyệt trước khi viết code.**

| Bước | Nội dung |
|---|---|
| 0 | Nghiên cứu bằng Python, ngoài app. **XONG 2026-10-20** |
| 1 | Bộ sinh mới; backup; xóa và sinh lại dữ liệu; kiểm lại K=0 và quy tắc "ít nhất 2 lần" trên dữ liệu mới |
| 2 | `ForecastService` bằng Java (Poisson + ridge + EM) và unit test |
| 3 | Recommendation và What-if chuyển sang đơn vị nhu cầu: số chuyến cần, doanh thu từ phần khách tràn |
| 4 | Cập nhật tài liệu |

Từ 2026-10-21, bước 1–4 được chia thành 7 giai đoạn GĐ1–GĐ7. Xem `forecast_progress.md` mục 2–3.

Các nguyên tắc kiến trúc ở roadmap §3 vẫn giữ nguyên:
- Demand Forecast độc lập với Business Rule Validation.
- Recommendation Engine là bộ điều phối, không tự dự báo.
- What-if chỉ tính ở mức tổng hợp.

---

## 4. Chưa chốt: quyết khi lập kế hoạch

| Mã | Câu hỏi | Ghi chú |
|---|---|---|
| M-1 | ~~Lịch lễ trong app~~ | **ĐÃ CHỐT 2026-10-21, thành QĐ-9.** Cửa sổ trước/sau mỗi dịp đã đo ở QĐ-13: cửa sổ hẹp nhỉnh hơn 0,3 điểm, nhưng chỉ trên bộ sinh của chính ta. Đề xuất **giữ cửa sổ hiện tại** (`forecast_discussion.md` §13.5) |
| M-2 | ~~Chiều tuyến: chi tiết của QĐ-4~~ | **ĐÃ CHỐT 2026-10-21, thành QĐ-8** |
| M-3 | **"Nhu cầu khung giờ" và "bán hết vé" đo thế nào trên DB:** gộp các chuyến cùng (tuyến, giờ, ngày), kể cả chuyến tăng cường; tính những trạng thái nào; xử lý chuyến `CANCELLED` ra sao | Hiện Phase 6 chỉ tính `COMPLETED` (roadmap §9). **Dữ liệu GĐ2 đã sinh theo cách hiểu này**, và lần đo lại QĐ-13 dùng đúng nó: cộng các chuyến `COMPLETED` cùng (tuyến, ngày, HOUR); chuyến thêm ở phút 30/40/50 nên tự rơi vào đúng khung; bán hết khi vé ≥ tổng số ghế; khung bị hủy là không quan sát được. Định nghĩa chính thức cho `ForecastService` chốt ở GĐ5 |
| M-4 | **Ngưỡng 0,90 và số chuyến cần:** giữ một định nghĩa "chuyến nóng" duy nhất như thế nào | Bước 0 đo bằng: số chuyến cần = max(1, ⌈nhu cầu / (0,9 × số ghế xe chuẩn)⌉) (§12.2) |
| M-5 | **Tính toán và cache:** tính một lần mỗi ngày? Lưu ở đâu? Ai kích hoạt? | QĐ-1, hệ quả |
| M-6 | ~~Đếm "ít nhất 2 lần" theo toàn hệ thống hay theo từng tuyến~~ | **ĐÃ ĐÓNG 2026-10-22:** không còn đếm số lần, vì QĐ-3 đổi sang quy tắc (b) |
| M-7 | ~~Ngày bão, lũ~~ | **ĐÃ CHỐT 2026-10-21, thành QĐ-10:** bão là đóng tuyến, mọi chuyến hôm đó `CANCELLED`; không dùng Incident |
| M-8 | ~~Bộ sinh: độ lớn, số năm, phạm vi xóa~~ | **ĐÃ CHỐT 2026-10-21, thành QĐ-10 và QĐ-11** |
| M-9 | **Màn hình dự báo:** hiển thị gì (bảng hệ số lễ, xem trước 35 ngày), backtest so với mốc nào, đo bằng WAPE hay MAPE | |
| M-10 | ~~Ghi lỗi "xu hướng reset"~~ | **ĐÃ CHỐT 2026-10-21:** ghi thành #31 trong `current_bugs_found.md`, cùng lỗi #32 (69 chuyến tài xế hết hạn bằng). Gốc ở code đã bỏ ở GĐ2; đóng khi GĐ3 xoá dữ liệu cũ |
| M-11 | **Vé đặt trước (câu F)** | Đề xuất ngày 20/10: không làm trong đợt này; khi thiết kế Booking ở Phase 9 thì lưu thời điểm đặt vé. Chưa duyệt riêng |
| M-12 | **Thứ tự so với Phase 9** (Customer Portal) | Chưa quyết |
| M-13 | ~~Thiếu xe dịp Tết trong bộ sinh~~ | **ĐÃ CHỐT 2026-10-21, thành QĐ-10:** giữ "mỗi xe 1 chuyến/ngày"; chuyến thêm chỉ trong số xe rảnh (5/ngày) |
| M-14 | **Nhiều chuyến tăng cường cho cùng một khung.** Recommendation đánh giá từng thẻ độc lập, nên các thẻ cùng khung sẽ đề xuất cùng một xe. Giữ chỗ xe và tài xế đã chọn giữa các thẻ, hay mỗi khung chỉ một thẻ ghi "cần N chuyến"? | Thuộc GĐ6. Phát hiện ngày 2026-10-21 |
| M-15 | ~~Hai đề xuất sau lần đo lại GĐ2~~ (`forecast_discussion.md` §13.5): (1) quy tắc (b) thay quy tắc (a) "ít nhất 2 lần"; (2) EM 5 vòng thay vì 15 | **ĐÃ CHỐT CẢ HAI NGÀY 2026-10-22.** Ý 2 vào QĐ-1, ý 1 vào QĐ-3 (viết lại); đóng M-6. Số đo bổ sung cho ý 1: §13.7 |
| M-16 | **Chạy bổ sung lịch sử về sau:** chuyến Admin tạo tay đúng 06:00/12:00/18:00 (phút 0, 30, 40, 50) trên một tuyến ≤ 8 giờ sẽ bị bộ sinh coi là chuyến của nó, nên ngày đó bị bỏ qua cả ngày và lịch sử ngày đó chỉ còn chuyến tay. Đánh dấu chuyến bộ sinh (cột mới) hay nhận diện chặt hơn? | Phát hiện khi rà GĐ3 (2026-10-22). GĐ3 không bị: 19 chuyến của chủ dự án không có giờ nào như vậy. Chỉ cần khi chạy bổ sung trước buổi demo sau này |

---

## 5. Đã cân nhắc nhưng không chọn

| Phương án | Vì sao không chọn |
|---|---|
| Giữ LR + hệ số thứ trong tuần (code hiện tại) | Trên dữ liệu thật thua seasonal naive 3,4 điểm WAPE (§12.3). Trên dữ liệu có chặn trần gần như không bao giờ gắn cờ (§12.4) |
| Holt-Winters | Không nhận được biến ngày lễ. Thua G1' ở cả hai thí nghiệm |
| LightGBM | Cần thư viện ML, trái Non-Goal. Không xử lý được chặn trần. Sụp khi mới có 1 Tết. Thua 2,8 điểm ở B. Là hộp đen. Giữ làm đối chứng Python |
| Prophet | Là thư viện Python. Ngang G1' ở A, thua 1,2 điểm ở B. Giữ làm đối chứng Python |
| C1 / C3 (Admin nhập hệ số lễ) | Chủ dự án chọn C2 |
| Giữ đơn vị tỉ lệ lấp đầy | Xem QĐ-2 |

---

## 6. Lịch sử thay đổi

| Ngày | Thay đổi |
|---|---|
| 2026-10-21 | Tạo file. Ghi 7 quyết định chủ dự án duyệt trong phiên 2026-10-21 |
| 2026-10-21 (sau) | Thảo luận GĐ1: thêm QĐ-8 (hướng dòng khách, giá trị cột bằng tiếng Anh theo yêu cầu của chủ dự án) và QĐ-9 (lịch lễ); đóng M-1, M-2; thêm M-13, M-14 |
| 2026-10-21 (cài GĐ1) | QĐ-9: bảng âm lịch thêm hai năm biên 2022 và 2033 để phạm vi 2023–2032 đúng tuyệt đối; ví dụ lệch lịch Trung Quốc đổi sang Tết 2030 (nằm trong phạm vi). Ghi nguồn đối chiếu. QĐ-8, QĐ-9: thêm dòng "Đã cài". Các quyết định khác giữ nguyên |
| 2026-10-21 (sau GĐ1) | QĐ-8: thêm mục "Cách chọn nhãn". Nhãn hỏi tuyến đông vào đầu hay cuối dịp lễ; tuyến giữa hai TP lớn chọn theo dòng khách chính; **chỉ gán khi biết chắc, không chắc thì để trống**. Tuyến 6 giữ để trống theo quy tắc này. Chủ dự án chốt, không thêm giá trị mới cho enum |
| 2026-10-21 (GĐ2) | Thảo luận GĐ2, chủ dự án duyệt tất cả: thêm QĐ-10 (bộ sinh mới), QĐ-11 (bản sao, tổng duyệt GĐ3), QĐ-12 (màn danh sách chuyến), QĐ-13 (đo lại bằng Python). Đóng M-7, M-8, M-10, M-13; M-3 có cách hiểu dùng cho dữ liệu và lần đo lại, chính thức chốt ở GĐ5; M-1 (cửa sổ) chờ kết quả QĐ-13 |
| 2026-10-21 (GĐ2 xong) | QĐ-10, QĐ-11, QĐ-12: thêm phần "Đã cài" kèm số đo trên bản sao. QĐ-13: tóm tắt kết quả. M-1: đề xuất giữ cửa sổ hiện tại. Thêm M-15 (quy tắc (b) thay (a); EM 5 vòng), chờ chủ dự án |
| 2026-10-22 | M-15 ý 2: chủ dự án duyệt EM 5 vòng; sửa QĐ-1. M-15 ý 1 (quy tắc (b)) còn chờ; thêm số đo tách theo tình huống (`forecast_discussion.md` §13.7) |
| 2026-10-22 (sau) | M-15 ý 1: chủ dự án chọn quy tắc (b). Viết lại QĐ-3 (giữ mô tả quy tắc cũ (a) và lý do thay); sửa bảng mục 1, công thức QĐ-1, ghi chú "sự kiện một lần" ở QĐ-9, tóm tắt QĐ-13. Đóng M-6 và M-15 |
| 2026-10-22 (GĐ3) | Thảo luận GĐ3, chủ dự án duyệt G3-1…G3-7: thêm QĐ-14 và dòng của nó ở mục 1; QĐ-10 thêm "Bổ sung ở GĐ3" (lỗi #33, chuyến có sẵn giữ xe và người); QĐ-7 ghi rõ số dòng là của bản trước GĐ2. Thêm M-16 (nhận diện chuyến bộ sinh khi chạy bổ sung) |
