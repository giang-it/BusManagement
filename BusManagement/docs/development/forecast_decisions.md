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
| QĐ-3 | Lễ và mùa | **C2**: hệ thống tự học từ dữ liệu, không có hệ số nhập tay. Chỉ dùng hệ số của một dịp lễ khi dịp đó đã xuất hiện **ít nhất 2 lần** trong dữ liệu huấn luyện |
| QĐ-4 | Chiều tuyến | **Thêm thuộc tính "chiều" cho `Route`**. Đây là đổi schema |
| QĐ-5 | Tầm dự báo | **7 ngày** cho thẻ Recommendation và What-if; **đến 35 ngày** chỉ để xem trước |
| QĐ-6 | Non-Goal §4 | **Chỉ sửa chữ**: "moving average / linear regression" → "mô hình thống kê (hồi quy Poisson theo lịch) viết bằng Java". Vẫn không có mô hình ML, không thêm thư viện |
| QĐ-7 | Dữ liệu | **Viết lại bộ sinh, rồi xóa và sinh lại dữ liệu lịch sử** trong DB thật. Phải backup trước |
| QĐ-8 | Hướng dòng khách dịp lễ (chi tiết của QĐ-4) | Trường `Route.holidayFlow`, kiểu enum `HolidayFlow { LEAVING_HUB, TOWARD_HUB }`. Để trống nghĩa là không theo chiều. Admin chọn, không bắt buộc. **Chỉ gán khi biết chắc chiều đông; không chắc thì để trống** |
| QĐ-9 | Lịch ngày lễ | 5 dịp theo Điều 112 Bộ luật Lao động 2019, neo vào ngày lễ theo luật hoặc âm lịch. Lưu trong một lớp Java dùng chung, kèm bảng ngày âm lịch tra sẵn có ghi nguồn (2023–2032) |

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
                       + hiệu ứng (dịp lễ, ngày lệch so với lễ), gộp theo chiều tuyến
                       + phần lệch lễ riêng của chuỗi, kéo về mức chung bằng ridge (λ = 2)
  ```
- Mọi chuỗi khớp chung một lần bằng IRLS, trên số đếm đã chia cho trung bình của chuỗi.
- **K = 0**: không có thành phần sin/cos theo năm.
- **Lúc dự báo:**
  - xu hướng giữ nguyên ở giá trị tại ngày dự báo, không kéo ra tương lai;
  - nhân thêm hệ số chỉnh mức = Σ thực tế / Σ mô hình trên 28 ngày gần nhất không có lễ.
- **EM**, 15 vòng:
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

### QĐ-3. Lễ và mùa: tự học từ dữ liệu (C2), quy tắc "ít nhất 2 lần"

**Là gì.**
- Mô hình tự học **độ lớn** hiệu ứng lễ. Không ai nhập hệ số, tức không làm C1 hay C3.
- **Lịch ngày lễ** (ngày nào là Tết, 30/4, 2/9…) vẫn là đầu vào biết trước. Chi tiết lịch đã chốt ở QĐ-9.
- Hệ số tính cho từng cặp (dịp lễ, ngày lệch so với lễ), ví dụ "Tết D−3". Mỗi hệ số gồm phần gộp theo chiều tuyến cộng phần lệch riêng của tuyến (QĐ-1).

**Quy tắc "ít nhất 2 lần"** (phương án (a) trong phiên 2026-10-21):
- Khi dự báo một ngày trong cửa sổ lễ, đếm số lần dịp lễ đó có trong dữ liệu huấn luyện.
- **Từ 2 lần trở lên:** dùng hệ số đã học.
- **0 hoặc 1 lần:** coi là ngày thường (×1).
- **Cơ sở** (dữ liệu thật Hồng Kông, §12.3):
  - Mới thấy 1 Tết thì học còn hại: ở Tết 2025, G1', K=0 kém SN4 sạch 2,7 điểm.
  - Đã thấy 2 Tết thì có lợi rõ: ở Tết 2026, các mô hình có hệ số lễ riêng từng chuỗi thắng SN4 sạch 4,7–8,8 điểm.
- **Chưa thử trực tiếp.** Con số 2 được chọn sau khi xem kết quả, nên kiểm lại ở bước 1 cùng với K=0.
- **Tác động trong đồ án:** dữ liệu có ít nhất 3 năm, nên khi app dự báo bình thường quy tắc không bao giờ kích hoạt. Nó chỉ ảnh hưởng backtest (ví dụ Tết 2025) và lập luận "nếu triển khai thật mà dữ liệu còn ít thì sao".

**Ràng buộc kéo theo:** cửa sổ dữ liệu học của `ForecastService` phải phủ ít nhất 2 lần mỗi dịp lễ, tức khoảng 2 năm trở lên. **Không được cắt ngắn dữ liệu học để chạy nhanh hơn.**

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
- **Không lặp lỗi "xu hướng reset sau mỗi lần chạy bổ sung"** của bộ sinh hiện tại:
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
- **Sự kiện chỉ xảy ra một lần** (ví dụ đại lễ 30/4/2025) không đưa vào lịch. Dù có đưa vào, quy tắc "ít nhất 2 lần" cũng sẽ bỏ qua. Đây là giới hạn cần nói rõ khi bảo vệ.

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
| M-1 | ~~Lịch lễ trong app~~ | **ĐÃ CHỐT 2026-10-21, thành QĐ-9.** Chỉ còn mở phần cửa sổ trước/sau mỗi dịp, sẽ chốt ở GĐ2 |
| M-2 | ~~Chiều tuyến: chi tiết của QĐ-4~~ | **ĐÃ CHỐT 2026-10-21, thành QĐ-8** |
| M-3 | **"Nhu cầu khung giờ" và "bán hết vé" đo thế nào trên DB:** gộp các chuyến cùng (tuyến, giờ, ngày), kể cả chuyến tăng cường; tính những trạng thái nào; xử lý chuyến `CANCELLED` ra sao | Hiện Phase 6 chỉ tính `COMPLETED` (roadmap §9) |
| M-4 | **Ngưỡng 0,90 và số chuyến cần:** giữ một định nghĩa "chuyến nóng" duy nhất như thế nào | Bước 0 đo bằng: số chuyến cần = max(1, ⌈nhu cầu / (0,9 × số ghế xe chuẩn)⌉) (§12.2) |
| M-5 | **Tính toán và cache:** tính một lần mỗi ngày? Lưu ở đâu? Ai kích hoạt? | QĐ-1, hệ quả |
| M-6 | **Đếm "ít nhất 2 lần"** theo toàn hệ thống hay theo từng tuyến | Claude nghiêng về toàn hệ thống: tuyến mới vẫn dùng được hệ số gộp theo chiều |
| M-7 | **Ngày bão, lũ** | Đề xuất ngày 20/10 (§11): đánh dấu và loại khỏi dữ liệu huấn luyện, dựa vào Incident `ROAD_ISSUE`; thời tiết từng ngày không làm ở bản đầu. Chưa duyệt riêng |
| M-8 | **Bộ sinh:** độ lớn hiệu ứng, số năm, phạm vi xóa, giữ dữ liệu không do bộ sinh tạo ra | Đỉnh Tết ×3–3,5 là giả định theo báo chí (§12.2). 99,8% lịch sử hiện tại là dữ liệu sinh (§0.1) |
| M-9 | **Màn hình dự báo:** hiển thị gì (bảng hệ số lễ, xem trước 35 ngày), backtest so với mốc nào, đo bằng WAPE hay MAPE | |
| M-10 | **Ghi lỗi "xu hướng reset"** vào `current_bugs_found.md` / `project_report.md` hay không | Claude hỏi ngày 2026-10-21, chủ dự án chưa trả lời |
| M-11 | **Vé đặt trước (câu F)** | Đề xuất ngày 20/10: không làm trong đợt này; khi thiết kế Booking ở Phase 9 thì lưu thời điểm đặt vé. Chưa duyệt riêng |
| M-12 | **Thứ tự so với Phase 9** (Customer Portal) | Chưa quyết |
| M-13 | **Thiếu xe dịp Tết trong bộ sinh.** Bộ sinh hiện giữ bất biến "mỗi xe tối đa 1 chuyến/ngày" (15 chuyến/ngày trên 20 xe). Tết gấp 3 lần thì cần khoảng 45 chuyến/ngày. Cho mỗi xe chạy 2 chuyến/ngày (phải kiểm không chồng giờ, thời gian chuẩn bị xe, giờ lái), hay giới hạn số chuyến thêm theo số xe? | Thuộc GĐ2. Phát hiện ngày 2026-10-21 |
| M-14 | **Nhiều chuyến tăng cường cho cùng một khung.** Recommendation đánh giá từng thẻ độc lập, nên các thẻ cùng khung sẽ đề xuất cùng một xe. Giữ chỗ xe và tài xế đã chọn giữa các thẻ, hay mỗi khung chỉ một thẻ ghi "cần N chuyến"? | Thuộc GĐ6. Phát hiện ngày 2026-10-21 |

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
