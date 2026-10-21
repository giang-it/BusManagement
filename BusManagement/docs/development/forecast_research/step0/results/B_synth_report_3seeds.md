# Q:/out/fc_synth_s7.csv,Q:/out/fc_synth_s11.csv,Q:/out/fc_synth_s23.csv,Q:/out/fc_synth_k0_s7.csv,Q:/out/fc_synth_k0_s11.csv,Q:/out/fc_synth_k0_s23.csv  target=demand  rows=1984500  origins=90  series=15

## WAPE % theo tam du bao (moi loai ngay)

| Phuong phap | h1-7 | h8-14 | h15-28 | h29-35 |
|---|---|---|---|---|
| SN1 cung thu tuan truoc | 31.80 | 32.01 | 32.22 | 31.92 |
| SN4 TB 4 cung thu | 26.66 | 26.81 | 26.81 | 26.30 |
| SN4 sach (bo ngay le) | 25.69 | 25.84 | 25.94 | 25.55 |
| App hien tai (ti le x ghe) | 26.23 | 26.24 | 26.27 | 25.72 |
| LR hien tai (xu huong) | 26.16 | 26.14 | 26.14 | 25.55 |
| Holt-Winters tat dan | 25.12 | 25.10 | 25.23 | 24.82 |
| Prophet | 23.48 | 23.52 | 23.52 | 23.02 |
| G2 nen x he so le hoc | 24.05 | 24.16 | 24.24 | 24.03 |
| G1' GLM lich + lech tung chuoi | 22.99 | 23.17 | 23.74 | 24.19 |
| G1' + EM chan tran | 22.57 | 22.90 | 23.78 | 24.61 |
| G1' + EM, khong biet chieu tuyen | 22.94 | 23.39 | 24.07 | 24.42 |
| G1' + EM, khong mua nam (K=0) | 22.32 | 22.44 | 22.67 | 22.48 |
| LightGBM toan cuc | 25.01 | 25.17 | 25.15 | 24.81 |
| (san: biet ky vong that) | 19.13 | 19.13 | 19.10 | 19.13 |

## WAPE % theo loai ngay, tam 1-7

| Phuong phap | cny2025 | cny2026 | holiday | normal | storm |
|---|---|---|---|---|---|
| SN1 cung thu tuan truoc | 67.17 | 65.94 | 31.51 | 26.12 | 136.66 |
| SN4 TB 4 cung thu | 50.49 | 50.56 | 27.76 | 22.45 | 139.17 |
| SN4 sach (bo ngay le) | 45.37 | 44.95 | 27.90 | 22.03 | 134.02 |
| App hien tai (ti le x ghe) | 45.76 | 45.84 | 29.97 | 22.38 | 133.00 |
| LR hien tai (xu huong) | 46.34 | 46.18 | 29.80 | 22.22 | 137.81 |
| Holt-Winters tat dan | 47.19 | 46.82 | 27.27 | 21.13 | 127.26 |
| Prophet | 36.43 | 28.44 | 30.82 | 20.84 | 126.48 |
| G2 nen x he so le hoc | 32.99 | 27.06 | 29.99 | 22.03 | 128.92 |
| G1' GLM lich + lech tung chuoi | 32.43 | 27.55 | 28.87 | 20.81 | 128.35 |
| G1' + EM chan tran | 32.64 | 26.37 | 28.97 | 20.32 | 135.16 |
| G1' + EM, khong biet chieu tuyen | 38.28 | 30.39 | 26.76 | 20.31 | 138.58 |
| G1' + EM, khong mua nam (K=0) | 32.18 | 25.52 | 28.80 | 20.10 | 137.90 |
| LightGBM toan cuc | 49.57 | 26.93 | 28.10 | 22.28 | 121.79 |
| (san: biet ky vong that) | 18.86 | 18.26 | 19.17 | 19.19 | 24.68 |

## WAPE % theo loai ngay, tam 15-35 (len ke hoach)

| Phuong phap | cny2025 | cny2026 | holiday | normal | storm |
|---|---|---|---|---|---|
| SN1 cung thu tuan truoc | 54.63 | 52.13 | 32.02 | 28.87 | 139.52 |
| SN4 TB 4 cung thu | 47.57 | 45.73 | 28.99 | 23.26 | 140.57 |
| SN4 sach (bo ngay le) | 46.29 | 45.08 | 28.90 | 22.36 | 140.61 |
| App hien tai (ti le x ghe) | 46.66 | 45.99 | 30.76 | 22.44 | 136.04 |
| LR hien tai (xu huong) | 46.67 | 45.94 | 30.55 | 22.26 | 140.96 |
| Holt-Winters tat dan | 45.54 | 44.96 | 28.29 | 21.62 | 132.08 |
| Prophet | 36.48 | 28.61 | 31.09 | 20.96 | 129.06 |
| G2 nen x he so le hoc | 32.61 | 27.21 | 30.41 | 22.36 | 136.35 |
| G1' GLM lich + lech tung chuoi | 32.58 | 27.17 | 30.15 | 22.07 | 129.54 |
| G1' + EM chan tran | 33.40 | 26.24 | 30.36 | 22.25 | 135.88 |
| G1' + EM, khong biet chieu tuyen | 38.85 | 29.67 | 28.29 | 22.03 | 138.11 |
| G1' + EM, khong mua nam (K=0) | 31.44 | 24.54 | 29.25 | 20.78 | 144.02 |
| LightGBM toan cuc | 54.32 | 27.21 | 28.65 | 22.35 | 127.08 |
| (san: biet ky vong that) | 18.86 | 18.26 | 19.16 | 19.17 | 24.55 |

## MAPE % tam 1-7 (de so voi con so cua ForecastService)

| Phuong phap | moi ngay | ngay thuong | ngay le |
|---|---|---|---|
| SN1 cung thu tuan truoc | 41.16 | 28.47 | 88.08 |
| SN4 TB 4 cung thu | 34.11 | 24.39 | 70.08 |
| SN4 sach (bo ngay le) | 32.68 | 23.90 | 65.18 |
| App hien tai (ti le x ghe) | 33.02 | 23.96 | 66.54 |
| LR hien tai (xu huong) | 33.62 | 24.32 | 68.04 |
| Holt-Winters tat dan | 31.01 | 22.11 | 63.96 |
| Prophet | 28.07 | 22.06 | 50.33 |
| G2 nen x he so le hoc | 29.44 | 23.90 | 49.96 |
| G1' GLM lich + lech tung chuoi | 28.08 | 22.47 | 48.84 |
| G1' + EM chan tran | 28.89 | 23.02 | 50.64 |
| G1' + EM, khong biet chieu tuyen | 29.19 | 23.04 | 51.97 |
| G1' + EM, khong mua nam (K=0) | 28.83 | 22.76 | 51.31 |
| LightGBM toan cuc | 29.76 | 23.73 | 52.10 |
| (san: biet ky vong that) | 22.50 | 22.10 | 24.01 |

## Quyet dinh: tam 1-7 (Recommendation / What-if), moi ngay

| Phuong phap | co: precision | co: recall | co: F1 | so chuyen dung % | khach bi bo lai % | chuyen thua / 100 khung |
|---|---|---|---|---|---|---|
| SN1 cung thu tuan truoc | 0.48 | 0.48 | 0.48 | 67.9 | 3.59 | 16.5 |
| SN4 TB 4 cung thu | 0.57 | 0.36 | 0.44 | 72.1 | 4.08 | 8.2 |
| SN4 sach (bo ngay le) | 0.61 | 0.34 | 0.44 | 73.0 | 4.07 | 6.5 |
| App hien tai (ti le x ghe) | 0.64 | 0.09 | 0.15 | 70.7 | 6.12 | 1.5 |
| LR hien tai (xu huong) | 0.62 | 0.18 | 0.27 | 71.5 | 5.51 | 3.3 |
| Holt-Winters tat dan | 0.71 | 0.23 | 0.35 | 73.5 | 4.58 | 2.9 |
| Prophet | 0.68 | 0.33 | 0.45 | 74.2 | 2.82 | 5.0 |
| G2 nen x he so le hoc | 0.63 | 0.38 | 0.48 | 73.6 | 2.69 | 7.1 |
| G1' GLM lich + lech tung chuoi | 0.69 | 0.36 | 0.47 | 74.7 | 2.62 | 5.2 |
| G1' + EM chan tran | 0.63 | 0.54 | 0.58 | 75.6 | 1.58 | 10.0 |
| G1' + EM, khong biet chieu tuyen | 0.63 | 0.54 | 0.58 | 75.3 | 1.87 | 10.0 |
| G1' + EM, khong mua nam (K=0) | 0.64 | 0.54 | 0.58 | 75.7 | 1.57 | 9.7 |
| LightGBM toan cuc | 0.65 | 0.34 | 0.45 | 73.6 | 3.34 | 5.8 |
| (san: biet ky vong that) | 0.69 | 0.59 | 0.63 | 78.7 | 1.02 | 8.4 |

## Quyet dinh: tam 1-7, chi ngay le/Tet

| Phuong phap | co: precision | co: recall | co: F1 | so chuyen dung % | khach bi bo lai % | chuyen thua / 100 khung |
|---|---|---|---|---|---|---|
| SN1 cung thu tuan truoc | 0.46 | 0.43 | 0.44 | 61.3 | 10.49 | 21.5 |
| SN4 TB 4 cung thu | 0.49 | 0.34 | 0.40 | 65.2 | 11.33 | 11.4 |
| SN4 sach (bo ngay le) | 0.59 | 0.31 | 0.41 | 68.1 | 11.02 | 7.3 |
| App hien tai (ti le x ghe) | 0.60 | 0.07 | 0.12 | 67.1 | 14.11 | 1.5 |
| LR hien tai (xu huong) | 0.58 | 0.14 | 0.23 | 67.4 | 13.46 | 3.5 |
| Holt-Winters tat dan | 0.61 | 0.18 | 0.27 | 67.6 | 12.23 | 3.8 |
| Prophet | 0.64 | 0.51 | 0.56 | 70.1 | 4.70 | 11.1 |
| G2 nen x he so le hoc | 0.66 | 0.50 | 0.57 | 70.9 | 4.61 | 10.1 |
| G1' GLM lich + lech tung chuoi | 0.68 | 0.49 | 0.57 | 71.5 | 4.39 | 9.2 |
| G1' + EM chan tran | 0.64 | 0.63 | 0.63 | 72.1 | 2.76 | 13.5 |
| G1' + EM, khong biet chieu tuyen | 0.62 | 0.61 | 0.61 | 70.6 | 4.21 | 13.2 |
| G1' + EM, khong mua nam (K=0) | 0.64 | 0.64 | 0.64 | 72.0 | 2.69 | 14.4 |
| LightGBM toan cuc | 0.68 | 0.41 | 0.51 | 70.8 | 7.01 | 7.2 |
| (san: biet ky vong that) | 0.76 | 0.75 | 0.76 | 81.2 | 0.74 | 9.2 |

## Quyet dinh: tam 15-35, Tet (len ke hoach truoc)

| Phuong phap | co: precision | co: recall | co: F1 | so chuyen dung % | khach bi bo lai % | chuyen thua / 100 khung |
|---|---|---|---|---|---|---|
| SN1 cung thu tuan truoc | 0.41 | 0.41 | 0.41 | 60.7 | 13.92 | 19.6 |
| SN4 TB 4 cung thu | 0.48 | 0.31 | 0.38 | 66.5 | 15.31 | 10.1 |
| SN4 sach (bo ngay le) | 0.51 | 0.30 | 0.38 | 67.4 | 15.52 | 8.9 |
| App hien tai (ti le x ghe) | 0.54 | 0.09 | 0.15 | 69.3 | 18.41 | 2.3 |
| LR hien tai (xu huong) | 0.59 | 0.12 | 0.20 | 69.9 | 18.10 | 2.5 |
| Holt-Winters tat dan | 0.56 | 0.20 | 0.29 | 69.2 | 16.58 | 4.6 |
| Prophet | 0.69 | 0.59 | 0.64 | 73.7 | 4.77 | 10.7 |
| G2 nen x he so le hoc | 0.72 | 0.60 | 0.66 | 74.7 | 4.43 | 9.7 |
| G1' GLM lich + lech tung chuoi | 0.73 | 0.61 | 0.67 | 75.3 | 4.06 | 9.3 |
| G1' + EM chan tran | 0.68 | 0.69 | 0.69 | 74.9 | 2.67 | 13.4 |
| G1' + EM, khong biet chieu tuyen | 0.60 | 0.65 | 0.62 | 70.0 | 4.66 | 15.0 |
| G1' + EM, khong mua nam (K=0) | 0.69 | 0.74 | 0.71 | 75.8 | 2.24 | 14.0 |
| LightGBM toan cuc | 0.68 | 0.45 | 0.54 | 71.9 | 8.83 | 8.3 |
| (san: biet ky vong that) | 0.80 | 0.76 | 0.78 | 83.2 | 0.72 | 7.6 |
