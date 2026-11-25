# CT9 — "Về ngay" so với "giữ–về" trên lịch sử (phụ lục cho luận văn)

> Route pairs CT9 (QĐ-25, QĐ-26; CM-8, CM-29…CM-32 trong `docs/development/route_pairs_decisions.md`). Kết quả của
> `ct9_hold_return_simulation.py`, chạy **chỉ đọc** trên DB thật ngày 2026-11-25. Lịch sử là dữ liệu **mô phỏng**
> (QĐ-21). Đây là phép so ở **mức kế hoạch** — không chọn lại xe hay tài xế.

## Phương pháp

**Đầu vào:** các chuyến tăng cường CÓ KHÁCH (`tickets_sold > 0`) của ba cặp quanh Bến xe Miền Đông trong một khoảng
ngày. Hai cách chạy cùng tập chuyến đó được tính bằng đúng các luật của app (`service/LoopEconomics`):

- **A. Về ngay.** Luật H1 của bộ sinh lịch sử, cũng là luật của màn Đề xuất trước CT9: mỗi chuyến thêm là một vòng
  riêng, lượt đi kèm (về ngay, hay đưa xe lên trước) tính là **rỗng**.
- **B. Giữ–về (CT9).** Ghép một chuyến thêm đi với một chuyến thêm về sau đó của cùng cặp thành một vòng giữ xe:
  - lãi tăng thêm phải > 0;
  - chờ thêm (so với lần về ngay sớm nhất) không quá 7 ngày;
  - tiền chờ = (lương chờ × 1 người + phí xe nằm bến) × giờ ÷ 24;
  - đôi tăng lãi nhiều nhất được ghép trước.

  Chuyến không ghép được chạy như A.

**Tham số:** lấy từ bảng `cost_parameters`. Nếu bảng chưa có hai cột tiền chờ thì dùng mặc định 333.000đ và 330.000đ.

**Doanh thu:** vé đã bán × giá vé của chính chuyến đó trong lịch sử.

**"Xe tăng cường cùng lúc":** đỉnh số vòng chồng nhau theo thời gian. Vòng giữ chiếm xe lâu hơn, nên chỉ số này cho
thấy giữ xe có làm phải dùng thêm xe hay không.

**Lưu ý khi đọc:**
- A là luật của app, không phải bản đếm lại từng lượt của lịch sử. Bộ sinh lịch sử còn chạy những lượt đưa xe
  0 vé khác, nên số chuyến 0 vé của lịch sử lớn hơn số lượt rỗng của A.
- Phí xe nằm bến 330.000đ/ngày là số tạm, chưa có nguồn (CM-8). Phí này cao hơn thì sẽ có ít vòng giữ hơn.

**Chạy lại** (mật khẩu MySQL qua biến môi trường):

```bash
MYSQL_PWD=<mật khẩu> python -I ct9_hold_return_simulation.py busmanagement 2026-02-07 2026-03-01 --md ct9_hold_return_results.md
```

## Kết quả

| Khoảng | Vòng giữ | Lượt rỗng bớt | Km rỗng bớt | Giờ chờ thêm | Lãi tăng | Xe cùng lúc |
|---|---:|---:|---:|---:|---:|---|
| Tết 2026 (07/02 → 28/02) | 5 | 10 | 2.520 | 380,5 | +7,1 tr (+2,9 %) | 10 → 10 |
| Giỗ Tổ + 30/4–1/5 2026 (22/04 → 06/05) | 5 | 10 | 2.520 | 200,5 | +12,1 tr (0,9 → 13,0 tr) | 5 → 5 |
| Tháng 10/2026 (ngày thường) | 0 | 0 | 0 | 0 | 0 | 1 → 1 |
| Cả năm 2025 | 11 | 22 | 5.640 | 755,7 | +18,6 tr (+10,2 %) | 10 → 10 |

**Đọc kết quả:**

- **Giữ–về có lợi ở dịp lễ.** Sóng khách rời thành phố trước lễ và sóng quay về sau lễ đi ngược chiều nhau. Theo
  luật "về ngay", mỗi xe tăng cường chạy một lượt rỗng ở mỗi sóng.
  - Ví dụ Tết 2026, cặp Đà Lạt: xe đi chuyến thêm Miền Đông → Đà Lạt 06:30 ngày 14/02 (50 vé), chờ ở Đà Lạt 120 giờ
    (3,3 triệu tiền chờ), rồi chở chuyến thêm về 06:30 ngày 20/02 (40 vé).
  - Cách đó thay cho hai lượt rỗng, mỗi lượt 2,1 triệu.
- **Ngày thường hầu như không có gì để ghép.** Tháng 10 có 0 vòng giữ. Cặp ngắn cũng tự giới hạn, đúng như CM-31:
  - Vũng Tàu chỉ đáng chờ khoảng 2 ngày, Cần Thơ khoảng 3,8 ngày, Đà Lạt khoảng 6,3 ngày;
  - vòng giữ của Cần Thơ trong bảng chỉ chờ thêm khoảng nửa giờ.
- **Không phải dùng thêm xe** ở cả bốn khoảng. Xe giữ ở Đà Lạt chính là xe mà luật "về ngay" phải đưa lên lại vào
  đầu sóng về.
- **Mức lợi phụ thuộc tiền chờ.** Với 663.000đ/ngày, vòng giữ Tết 120 giờ lãi hơn hai lượt rỗng khoảng 0,9 triệu
  (4,2 tr − 3,3 tr). Tiền chờ tăng lên khoảng 840.000đ/ngày thì vòng đó hết lãi.

## Chi tiết từng khoảng

### 2026-02-07 → 2026-03-01 (schema `busmanagement`)

Tham số: nhiên liệu 6.000đ/km, lương 50.000đ/giờ, lương chờ 333.000đ/người/ngày, phí nằm bến 330.000đ/ngày. Chuyến thêm có khách trong khoảng: 79 (lịch sử có 224 chuyến thêm, 145 chuyến 0 vé).

| | A. Về ngay | B. Giữ–về | B − A |
|---|---:|---:|---:|
| Vòng (xe tăng cường chạy) | 79 | 74 | -5 |
| Lượt chạy | 158 | 148 | -10 |
| Lượt rỗng | 79 | 69 | -10 |
| Km rỗng | 16.340 | 13.820 | -2.520 |
| Giờ chờ thêm ở bến xa | 0.0 | 380.5 | 380.5 |
| Tiền chờ (đ) | 0 | 10.511.312 | 10.511.312 |
| Doanh thu (đ) | 473.460.000 | 473.460.000 | 0 |
| Chi phí (đ) | 228.480.000 | 221.371.312 | -7.108.688 |
| Lãi (đ) | 244.980.000 | 252.088.688 | 7.108.688 |
| Xe tăng cường cùng lúc (đỉnh) | 10 | 10 | 0 |

Vòng giữ xe: 5.

| Chuyến thêm đi | Chuyến thêm về | Chờ thêm (giờ) | Tiền chờ (đ) | Lãi vòng giữ (đ) |
|---|---|---:|---:|---:|
| #46060 Miền Đông → Cần Thơ 06:30 10/02 (40 vé) | #46051 Cần Thơ → Miền Đông 12:30 10/02 (3 vé) | 0.5 | 13.812 | 5.216.188 |
| #46188 Miền Đông → Đà Lạt 06:30 14/02 (50 vé) | #46371 Đà Lạt → Miền Đông 06:30 20/02 (40 vé) | 120.5 | 3.328.812 | 19.471.188 |
| #46189 Miền Đông → Đà Lạt 12:30 14/02 (22 vé) | #46373 Đà Lạt → Miền Đông 12:30 20/02 (22 vé) | 126.5 | 3.494.562 | 5.505.438 |
| #46190 Miền Đông → Đà Lạt 18:30 14/02 (40 vé) | #46374 Đà Lạt → Miền Đông 18:30 20/02 (22 vé) | 132.5 | 3.660.312 | 10.739.688 |
| #46411 Miền Đông → Cần Thơ 06:30 21/02 (21 vé) | #46400 Cần Thơ → Miền Đông 12:30 21/02 (40 vé) | 0.5 | 13.812 | 8.456.188 |

### 2026-04-22 → 2026-05-07 (schema `busmanagement`)

Tham số: nhiên liệu 6.000đ/km, lương 50.000đ/giờ, lương chờ 333.000đ/người/ngày, phí nằm bến 330.000đ/ngày. Chuyến thêm có khách trong khoảng: 18 (lịch sử có 59 chuyến thêm, 41 chuyến 0 vé).

| | A. Về ngay | B. Giữ–về | B − A |
|---|---:|---:|---:|
| Vòng (xe tăng cường chạy) | 18 | 13 | -5 |
| Lượt chạy | 36 | 26 | -10 |
| Lượt rỗng | 18 | 8 | -10 |
| Km rỗng | 4.360 | 1.840 | -2.520 |
| Giờ chờ thêm ở bến xa | 0.0 | 200.5 | 200.5 |
| Tiền chờ (đ) | 0 | 5.538.812 | 5.538.812 |
| Doanh thu (đ) | 61.860.000 | 61.860.000 | 0 |
| Chi phí (đ) | 60.970.000 | 48.888.812 | -12.081.188 |
| Lãi (đ) | 890.000 | 12.971.188 | 12.081.188 |
| Xe tăng cường cùng lúc (đỉnh) | 5 | 5 | 0 |

Vòng giữ xe: 5.

| Chuyến thêm đi | Chuyến thêm về | Chờ thêm (giờ) | Tiền chờ (đ) | Lãi vòng giữ (đ) |
|---|---|---:|---:|---:|
| #47564 Miền Đông → Đà Lạt 18:30 24/04 (22 vé) | #47614 Đà Lạt → Miền Đông 18:30 26/04 (19 vé) | 36.5 | 1.008.312 | 7.091.688 |
| #47569 Miền Đông → Cần Thơ 18:30 24/04 (32 vé) | #47633 Cần Thơ → Miền Đông 12:30 27/04 (9 vé) | 54.5 | 1.505.562 | 3.364.438 |
| #47679 Miền Đông → Đà Lạt 12:30 29/04 (12 vé) | #47755 Đà Lạt → Miền Đông 06:30 02/05 (7 vé) | 48.5 | 1.339.812 | 160.188 |
| #47680 Miền Đông → Đà Lạt 18:30 29/04 (20 vé) | #47757 Đà Lạt → Miền Đông 18:30 02/05 (19 vé) | 60.5 | 1.671.312 | 5.828.688 |
| #47728 Miền Đông → Cần Thơ 18:30 01/05 (3 vé) | #47748 Cần Thơ → Miền Đông 06:30 02/05 (18 vé) | 0.5 | 13.812 | 1.256.188 |

### 2026-10-01 → 2026-11-01 (schema `busmanagement`)

Tham số: nhiên liệu 6.000đ/km, lương 50.000đ/giờ, lương chờ 333.000đ/người/ngày, phí nằm bến 330.000đ/ngày. Chuyến thêm có khách trong khoảng: 2 (lịch sử có 15 chuyến thêm, 13 chuyến 0 vé).

| | A. Về ngay | B. Giữ–về | B − A |
|---|---:|---:|---:|
| Vòng (xe tăng cường chạy) | 2 | 2 | 0 |
| Lượt chạy | 4 | 4 | 0 |
| Lượt rỗng | 2 | 2 | 0 |
| Km rỗng | 600 | 600 | 0 |
| Giờ chờ thêm ở bến xa | 0.0 | 0.0 | 0.0 |
| Tiền chờ (đ) | 0 | 0 | 0 |
| Doanh thu (đ) | 3.900.000 | 3.900.000 | 0 |
| Chi phí (đ) | 8.400.000 | 8.400.000 | 0 |
| Lãi (đ) | -4.500.000 | -4.500.000 | 0 |
| Xe tăng cường cùng lúc (đỉnh) | 1 | 1 | 0 |

Vòng giữ xe: 0.

### 2025-01-01 → 2026-01-01 (schema `busmanagement`)

Tham số: nhiên liệu 6.000đ/km, lương 50.000đ/giờ, lương chờ 333.000đ/người/ngày, phí nằm bến 330.000đ/ngày. Chuyến thêm có khách trong khoảng: 126 (lịch sử có 482 chuyến thêm, 356 chuyến 0 vé).

| | A. Về ngay | B. Giữ–về | B − A |
|---|---:|---:|---:|
| Vòng (xe tăng cường chạy) | 126 | 115 | -11 |
| Lượt chạy | 252 | 230 | -22 |
| Lượt rỗng | 126 | 104 | -22 |
| Km rỗng | 27.120 | 21.480 | -5.640 |
| Giờ chờ thêm ở bến xa | 0.0 | 755.7 | 755.7 |
| Tiền chờ (đ) | 0 | 20.875.292 | 20.875.292 |
| Doanh thu (đ) | 560.800.000 | 560.800.000 | 0 |
| Chi phí (đ) | 379.190.000 | 360.625.292 | -18.564.708 |
| Lãi (đ) | 181.610.000 | 200.174.708 | 18.564.708 |
| Xe tăng cường cùng lúc (đỉnh) | 10 | 10 | 0 |

Vòng giữ xe: 11.

| Chuyến thêm đi | Chuyến thêm về | Chờ thêm (giờ) | Tiền chờ (đ) | Lãi vòng giữ (đ) |
|---|---|---:|---:|---:|
| #38877 Miền Đông → Cần Thơ 12:30 22/01 (22 vé) | #38864 Cần Thơ → Miền Đông 18:30 22/01 (1 vé) | 0.5 | 13.812 | 1.616.188 |
| #39009 Miền Đông → Đà Lạt 06:30 26/01 (40 vé) | #39191 Đà Lạt → Miền Đông 06:30 01/02 (40 vé) | 120.5 | 3.328.812 | 16.471.188 |
| #39010 Miền Đông → Đà Lạt 12:30 26/01 (22 vé) | #39192 Đà Lạt → Miền Đông 06:40 01/02 (22 vé) | 120.7 | 3.333.417 | 5.666.583 |
| #39011 Miền Đông → Đà Lạt 18:30 26/01 (22 vé) | #39226 Đà Lạt → Miền Đông 06:30 02/02 (40 vé) | 144.5 | 3.991.812 | 10.408.188 |
| #40357 Miền Đông → Cần Thơ 06:30 04/04 (21 vé) | #40425 Cần Thơ → Miền Đông 12:30 07/04 (18 vé) | 72.5 | 2.002.812 | 2.507.188 |
| #40358 Miền Đông → Cần Thơ 12:30 04/04 (12 vé) | #40426 Cần Thơ → Miền Đông 18:30 07/04 (17 vé) | 72.5 | 2.002.812 | 707.188 |
| #40354 Miền Đông → Đà Lạt 18:30 04/04 (22 vé) | #40434 Đà Lạt → Miền Đông 18:30 07/04 (21 vé) | 60.5 | 1.671.312 | 7.028.688 |
| #40359 Miền Đông → Cần Thơ 18:30 04/04 (20 vé) | #40424 Cần Thơ → Miền Đông 06:30 07/04 (16 vé) | 48.5 | 1.339.812 | 2.630.188 |
| #40802 Miền Đông → Đà Lạt 06:30 28/04 (23 vé) | #40878 Đà Lạt → Miền Đông 18:30 01/05 (17 vé) | 60.5 | 1.671.312 | 6.128.688 |
| #43086 Miền Đông → Đà Lạt 06:30 01/09 (15 vé) | #43165 Đà Lạt → Miền Đông 06:30 04/09 (11 vé) | 48.5 | 1.339.812 | 2.260.188 |
| #43111 Miền Đông → Đà Lạt 18:30 02/09 (16 vé) | #43145 Đà Lạt → Miền Đông 12:30 03/09 (29 vé) | 6.5 | 179.562 | 9.120.438 |

