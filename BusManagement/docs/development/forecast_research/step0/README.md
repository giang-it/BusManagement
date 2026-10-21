# Step 0: chọn thuật toán dự báo bằng thí nghiệm (XONG 2026-10-20)

Bước 0 trong `../../forecast_discussion.md` (mục 11). Chủ dự án đã nêu ý kiến ban đầu B1 (nhu cầu khung giờ) + C2 (học lễ/mùa từ dữ liệu), nhưng chưa chốt.
Chỉ nghiên cứu: **không đụng code app, không đụng DB.**

**Kết quả và đề xuất:** `../../forecast_discussion.md` mục 12. Bảng số đầy đủ: `results/`.

## Hai thí nghiệm

- **A, dữ liệu thật** (`run_hk.py`):
  - nguồn `../step0_data/hk_daily_passenger_traffic_2026-10-20.csv`, số lượt xuất nhập cảnh hằng ngày của Sở Di trú Hồng Kông (immd.gov.hk open data), 62.726 dòng từ 01/01/2021 đến 19/10/2026;
  - dùng từ 01/04/2023, gồm 20 chuỗi (10 cửa khẩu × 2 chiều);
  - có 3 lần Tết: 10/02/2024, 29/01/2025, 17/02/2026.
- **B, dữ liệu mô phỏng** (`synth_generator.py` + `run_synth.py`):
  - 15 chuỗi khung giờ (5 tuyến × 06/12/18 giờ) như app, từ 01/07/2023 đến 19/10/2026;
  - nhu cầu sinh trước, rồi cắt theo số ghế (vé bán = min(nhu cầu, ghế));
  - có lễ VN theo chiều, neo vào kỳ nghỉ chính thức; có mùa, mức nhu cầu trôi và nhảy bậc, ngày bão;
  - chạy 3 seed: 7, 11, 23.
- Cả hai: 90 thời điểm dự báo mỗi tuần, từ 29/12/2024 đến 13/09/2026, tầm 35 ngày.

## File

| File | Làm gì |
|---|---|
| `methods.py` | Các phương pháp: seasonal naive, LR hiện tại, Holt-Winters, Prophet, G2 (`LiftModel`), G1 (`calendar_glm_task`), **G1' / G1' + EM (`slot_glm_task`)**, app hiện tại trên tỉ lệ (`app_rate_current`), LightGBM |
| `harness.py` | Vòng lặp rolling-origin dùng chung cho các phương pháp chỉ cần chuỗi và lịch lễ; ghi CSV dạng dài |
| `run_hk.py` | Thí nghiệm A. Phương pháp: `harness.SHARED` + `glm`, `glm_dev`, `glm_dev_k0` |
| `synth_generator.py` | Bộ sinh của thí nghiệm B. Ghi ra cả nhu cầu thật (`demand`) và kỳ vọng thật (`mu`) |
| `run_synth.py` | Thí nghiệm B. Phương pháp: `harness.SHARED` + `app`, `glm_dev`, `glm_em`, `glm_em1`, `glm_em_k0`, `oracle` |
| `report.py` | WAPE/MAPE theo tầm và theo loại ngày. Với file của B còn chấm thêm thước đo quyết định (cờ tăng cường, số chuyến, khách bị bỏ lại) |
| `compare.py` | So từng cặp phương pháp theo thời điểm: thắng / hòa / thua, khoảng tin cậy bootstrap 95% |
| `summary.py` | Bảng tóm tắt dùng trong mục 12 |
| `results/` | Kết quả `report.py`, `compare.py` và `summary.py` của các lần chạy ngày 2026-10-20 |
| `env/` | Shim `pydoc` cho venv (xem bên dưới) |

Ghi chú: ba lần chạy B đầu tiên đặt tên G1' chưa EM là `glm`. Sau đó tên này được đổi thành `glm_dev` cho khớp với A, và đã đổi luôn trong file kết quả; mô hình giữ nguyên. Hai lần chạy `*_k0` được thêm sau, cũng ngày 2026-10-20.

## Dựng lại môi trường

Máy có 2 vấn đề:

**(a) Đường dẫn dài.** Windows chưa bật long path, nên cài statsmodels/Prophet vào thư mục sâu sẽ lỗi. Tạo venv ở đường dẫn ngắn, ví dụ ánh xạ ổ ảo `subst Q: <thư mục tạm>` rồi `python -m venv Q:\v`.

**(b) Thư viện chuẩn bị hỏng.** File `C:\Users\nguye\AppData\Local\Programs\Python\Python311\Lib\pydoc.py` đã bị ghi đè (17 byte, nội dung `help([1, 2, 3])`, từ 04/09/2023), nên `import scipy` lỗi. Cách sửa gốc là cài lại hoặc Repair Python 3.11 (việc của chủ máy). Cách vá chỉ trong venv:
- chép `env/_pydocfix.py` và `env/zz_pydocfix.pth` vào `site-packages` của venv;
- `_pydocfix.py` do Claude viết, chỉ đăng ký một module `pydoc` thay thế có đúng hàm `getdoc` (scipy chỉ gọi hàm này);
- không tải code ngoài về. Bản vá tải `pydoc.py` từ GitHub của phiên trước đã bị bỏ.

Gói đã dùng (Python 3.11): numpy 2.4.6, pandas 3.0.6, scipy 1.17.1, statsmodels 0.15.0, prophet 1.5.0, cmdstanpy 1.3.0, lightgbm 4.7.0.

## Chạy lại

Chạy trong `forecast_research/`. Ghi file kết quả (mỗi file khoảng 50 MB) ra **ngoài repo**:

```
# A: đầy đủ ~25-40 phút trên 16 nhân; thêm "<methods> 20" để smoke test (cứ 20 thời điểm lấy 1)
python -I step0/run_hk.py step0_data/hk_daily_passenger_traffic_2026-10-20.csv <hk.csv>
python -I step0/report.py <hk.csv>

# B: sinh dữ liệu rồi dự báo, mỗi seed ~20 phút
python -I step0/synth_generator.py <synth_s7.csv> 7
python -I step0/run_synth.py <synth_s7.csv> <fc_s7.csv>
python -I step0/report.py <fc_s7.csv>,<fc_s11.csv>,<fc_s23.csv> demand

# so từng cặp, ví dụ G1' + EM, K=0 với app hiện tại ở tầm 1-7
python -I step0/compare.py <fc_s7.csv> demand 1 7 glm_em_k0:app
```

Lưu ý:
- Script tự đặt 1 luồng BLAS cho mỗi tiến trình và dùng Pool(6–8). Nếu để nhiều tiến trình × 16 luồng BLAS thì hết page file.
- Các phương pháp tất định cho kết quả lặp lại được. Bản `run_hk.py` sau khi tách `harness.py` đã được đối chiếu với lần chạy trước khi tách: 24.500/24.500 dòng, sai lệch 0 trên 7 phương pháp.
