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

## 1. Trạng thái hiện tại (cập nhật 2026-10-21)

- **Đã chốt** 7 quyết định (`forecast_decisions.md` mục 1).
- **Việc tiếp theo:** lập kế hoạch chi tiết cho bước 1–4, bắt đầu từ bước 1. Kế hoạch phải giải quyết các mục M-… liên quan trong `forecast_decisions.md` mục 4, và được chủ dự án duyệt trước khi viết code.
- **Code app:** chưa đổi gì. **DB:** chưa đổi gì.
- **Commit:** toàn bộ tài liệu dự báo từ 14/10 đến 21/10 đã gom thành một commit docs ngày 2026-10-21, **chưa push**. Commit gồm `forecast_*.md`, `forecast_research/` và các thay đổi trong `THESIS_ROADMAP.md`.
  - Không đưa vào commit: transcript `forecast_research/2026-10-20-chưa xong.txt`. Transcript này để ngoài git như mọi transcript khác.
  - Không thuộc đợt này và chưa commit: bài học `docs/study/05` (đã sửa) và `06` (mới), còn từ 2026-10-06.
- **Đang chờ chủ dự án trả lời:**
  - M-10: có ghi lỗi "xu hướng reset" vào sổ lỗi không;
  - M-12: làm đợt này trước hay sau Phase 9.

---

## 2. Các bước

Trạng thái dùng các nhãn: `CHƯA BẮT ĐẦU` / `ĐANG LÀM` / `XONG, CHƯA COMMIT` / `ĐÃ COMMIT`.

| Bước | Nội dung | Trạng thái | Ngày |
|---|---|---|---|
| 0 | Thí nghiệm chọn thuật toán (Python, ngoài app) | ĐÃ COMMIT | 2026-10-20 |
| — | Chốt quyết định | ĐÃ COMMIT | 2026-10-21 |
| KH | Kế hoạch chi tiết bước 1–4 | CHƯA BẮT ĐẦU | |
| 1 | Bộ sinh mới; backup; xóa và sinh lại dữ liệu; kiểm lại K=0 và quy tắc "ít nhất 2 lần" | CHƯA BẮT ĐẦU | |
| 2 | `ForecastService` bằng Java (Poisson + ridge + EM) và unit test | CHƯA BẮT ĐẦU | |
| 3 | Recommendation và What-if chuyển sang đơn vị nhu cầu | CHƯA BẮT ĐẦU | |
| 4 | Cập nhật tài liệu | CHƯA BẮT ĐẦU | |

---

## 3. Việc của từng bước

> **Danh sách sơ bộ**, chỉ gồm những gì đã chốt trong `forecast_decisions.md`. Khi kế hoạch chi tiết được duyệt, thay mục tương ứng bằng kế hoạch đó.
> Mỗi việc xong thì đánh `[x]`, ghi ngày, và ghi chỗ kiểm chứng (lệnh, số đo, file:dòng).

### Bước 1: dữ liệu
- [ ] Kế hoạch chi tiết được duyệt. Phải chốt M-1, M-2, M-3, M-7, M-8, và bước nào thêm thuộc tính chiều tuyến (QĐ-4).
- [ ] Backup DB.
- [ ] Viết lại bộ sinh theo yêu cầu QĐ-7.
- [ ] Chủ dự án xác nhận lần cuối, rồi mới xóa và sinh lại.
- [ ] Kiểm tra dữ liệu mới thỏa mọi bất biến nghiệp vụ, như Phase 5 đã làm.
- [ ] Kiểm lại K=0 và quy tắc "ít nhất 2 lần" trên dữ liệu mới, với seed mới.

### Bước 2: `ForecastService`
- [ ] Kế hoạch chi tiết được duyệt. Phải chốt M-5, M-6, M-9.
- [ ] Mô hình theo QĐ-1. Quy tắc "ít nhất 2 lần" và cửa sổ học phủ ít nhất 2 lần mỗi dịp lễ (QĐ-3). Tầm dự báo theo QĐ-5.
- [ ] Unit test.
- [ ] Verify bằng cách chạy app thật (skill `verify`).

### Bước 3: Recommendation và What-if
- [ ] Kế hoạch chi tiết được duyệt. Phải chốt M-4: giữ một định nghĩa "chuyến nóng" duy nhất.
- [ ] Recommendation: số chuyến cần; doanh thu tính từ phần khách tràn (thay `RecommendationService.java:213-215`).
- [ ] What-if dùng đơn vị nhu cầu.
- [ ] Verify bằng cách chạy app thật.

### Bước 4: tài liệu
- [ ] `THESIS_ROADMAP.md`:
  - §5: phần "As delivered" của Phase 5, 6, 7, 8;
  - §9: đánh dấu note "occupancy RATE" là đã bị thay thế, và thêm các note mới.
- [ ] Các file đang nhắc tới phần dự báo:
  - `docs/development/current_functional_spec.md`
  - `docs/development/Proj_functions_summary.md`
  - `docs/architecture/setup_guide.md`
  - `docs/todo/current_bugs_found.md`
  - `docs/ai/02_feature_doc_prompt.md`
  - `docs/ai/03_defense_study_prompt.md`
  - Danh sách lấy bằng grep ngày 2026-10-21. Lúc làm bước 4 phải grep lại, vì có thể còn file khác.

---

## 4. Nhật ký phiên

Thêm một dòng mới vào cuối bảng sau mỗi phiên; không sửa các dòng cũ.

| Ngày | Đã làm | Code app / DB | Commit |
|---|---|---|---|
| 2026-10-14 → 16 | Đọc kỹ `ForecastService`; thảo luận không viết code; backtest cho thấy LR thua Holt-Winters và seasonal naive (`forecast_discussion.md` §0–10) | Không đổi; DB chỉ `SELECT` | Chưa |
| 2026-10-20 | Thảo luận vòng 3 (chủ dự án nghiêng về B1 + C2). Làm bước 0 trên dữ liệu thật Hồng Kông và bộ sinh mô phỏng có chặn trần (§11–12) | Không đổi; không truy vấn DB | Chưa |
| 2026-10-21 | Giải thích G1' + EM, K=0 (hồi quy Poisson) và quy tắc "ít nhất 2 lần". Chủ dự án duyệt 7 quyết định. Tạo `forecast_decisions.md` và file này. Cập nhật roadmap §4, §5 (Phase 5, 6), §7, §8, §9 và phần đầu `forecast_discussion.md`. Chủ dự án bảo commit (*"commit đi bro"*) | Không đổi | Đã commit cả tài liệu 14/10 → 21/10 thành một commit; chưa push |

---

## 5. Quy tắc làm việc cho đợt này

- **Mỗi bước đi theo vòng:** lập kế hoạch → chủ dự án duyệt → làm → verify → cập nhật file này và roadmap → chủ dự án tự test → **chỉ commit khi chủ dự án bảo**.
  - Commit không mang trailer Co-Authored-By.
  - Tách commit theo loại: feature / fix / docs / refactor.
- **Ghi vào DB thật** chỉ xảy ra ở bước 1: phải có backup và chủ dự án xác nhận lần cuối.
- **Lưu ý khi bật app:** `TripService.java:76` có `@Scheduled(fixedRate = 10_000)`. Các phiên nghiên cứu đã không bật app.
- **Quyết định mới hay thay đổi quyết định:** ghi vào `forecast_decisions.md` (mục 6, lịch sử). Không đổi im lặng trong code.
- **Chạy lại thí nghiệm Python:** xem `forecast_research/step0/README.md`.
  - Cần venv ở đường dẫn ngắn (`subst`) và shim `pydoc` trong `step0/env/`.
  - Không nạp code tải từ ngoài vào venv.
  - File kết quả (khoảng 50 MB mỗi file) ghi ra ngoài repo.
