# C:/Users/nguye/AppData/Local/Temp/claude/E--Y4-S2-PRETHESIS-BusManagement-BusManagement/0e46b959-dd21-460c-a8dc-b0fdfab59548/scratchpad/g4/forecasts_final_repo.csv,C:/Users/nguye/AppData/Local/Temp/claude/E--Y4-S2-PRETHESIS-BusManagement-BusManagement/c17313ff-88f5-4beb-a3ef-7127691160ca/scratchpad/gd7/forecasts_ml.csv  target=demand  rows=264600  origins=72  series=15

## WAPE % theo tam du bao (moi loai ngay)

| Phuong phap | h1-7 | h8-14 | h15-28 | h29-35 |
|---|---|---|---|---|
| SN4 sach (bo ngay le) | 25.29 | 25.15 | 25.33 | 25.30 |
| App hien tai (ti le x ghe) | 25.47 | 25.31 | 25.38 | 25.46 |
| Prophet | 23.53 | 23.78 | 23.64 | 23.46 |
| LightGBM toan cuc | 24.85 | 25.03 | 24.89 | 24.61 |
| Cau hinh cuoi trong app: G1' + EM 5 vong, K=0, quy tac (b) | 22.35 | 22.71 | 22.59 | 22.54 |
| (san: biet ky vong that) | 20.26 | 20.38 | 20.31 | 20.18 |

## WAPE % theo loai ngay, tam 1-7

| Phuong phap | cny2024 | cny2025 | cny2026 | holiday | normal | storm |
|---|---|---|---|---|---|---|
| SN4 sach (bo ngay le) | 46.96 | 35.91 | 40.94 | 27.91 | 22.99 | 25.87 |
| App hien tai (ti le x ghe) | 46.56 | 36.54 | 40.34 | 28.11 | 23.19 | 26.81 |
| Prophet | 38.74 | 28.10 | 24.77 | 30.14 | 21.96 | 23.93 |
| LightGBM toan cuc | 48.27 | 28.75 | 26.75 | 27.94 | 23.42 | 26.89 |
| Cau hinh cuoi trong app: G1' + EM 5 vong, K=0, quy tac (b) | 33.66 | 23.70 | 25.19 | 26.71 | 21.26 | 21.40 |
| (san: biet ky vong that) | 20.93 | 19.83 | 19.30 | 20.08 | 20.32 | 21.34 |

## WAPE % theo loai ngay, tam 15-35 (len ke hoach)

| Phuong phap | cny2024 | cny2025 | cny2026 | holiday | normal | storm |
|---|---|---|---|---|---|---|
| SN4 sach (bo ngay le) | 42.18 | 38.88 | 40.97 | 27.73 | 23.08 | 24.53 |
| App hien tai (ti le x ghe) | 42.33 | 38.59 | 40.02 | 27.64 | 23.25 | 24.94 |
| Prophet | 38.09 | 27.62 | 26.51 | 29.31 | 22.10 | 22.64 |
| LightGBM toan cuc | 44.09 | 30.21 | 28.96 | 27.72 | 23.32 | 24.94 |
| Cau hinh cuoi trong app: G1' + EM 5 vong, K=0, quy tac (b) | 33.25 | 24.88 | 24.51 | 26.40 | 21.57 | 22.22 |
| (san: biet ky vong that) | 20.51 | 20.11 | 19.58 | 20.03 | 20.31 | 21.83 |

## MAPE % tam 1-7 (de so voi con so cua ForecastService)

| Phuong phap | moi ngay | ngay thuong | ngay le |
|---|---|---|---|
| SN4 sach (bo ngay le) | 31.47 | 25.96 | 53.20 |
| App hien tai (ti le x ghe) | 30.68 | 25.27 | 52.02 |
| Prophet | 26.54 | 24.13 | 36.06 |
| LightGBM toan cuc | 29.13 | 26.02 | 41.39 |
| Cau hinh cuoi trong app: G1' + EM 5 vong, K=0, quy tac (b) | 27.27 | 25.00 | 36.22 |
| (san: biet ky vong that) | 24.62 | 24.29 | 25.93 |

## Quyet dinh: tam 1-7 (Recommendation / What-if), moi ngay

| Phuong phap | co: precision | co: recall | co: F1 | so chuyen dung % | khach bi bo lai % | chuyen thua / 100 khung |
|---|---|---|---|---|---|---|
| SN4 sach (bo ngay le) | 0.63 | 0.37 | 0.47 | 69.4 | 3.74 | 7.8 |
| App hien tai (ti le x ghe) | 0.70 | 0.13 | 0.22 | 67.0 | 5.42 | 2.0 |
| Prophet | 0.69 | 0.37 | 0.48 | 70.9 | 2.76 | 6.0 |
| LightGBM toan cuc | 0.64 | 0.36 | 0.46 | 69.4 | 3.39 | 7.1 |
| Cau hinh cuoi trong app: G1' + EM 5 vong, K=0, quy tac (b) | 0.66 | 0.52 | 0.58 | 72.9 | 1.72 | 9.4 |
| (san: biet ky vong that) | 0.70 | 0.57 | 0.63 | 75.2 | 1.19 | 9.1 |

## Quyet dinh: tam 1-7, chi ngay le/Tet

| Phuong phap | co: precision | co: recall | co: F1 | so chuyen dung % | khach bi bo lai % | chuyen thua / 100 khung |
|---|---|---|---|---|---|---|
| SN4 sach (bo ngay le) | 0.59 | 0.30 | 0.40 | 66.3 | 8.36 | 7.4 |
| App hien tai (ti le x ghe) | 0.71 | 0.12 | 0.20 | 66.1 | 9.75 | 1.7 |
| Prophet | 0.64 | 0.49 | 0.56 | 68.9 | 3.28 | 11.5 |
| LightGBM toan cuc | 0.67 | 0.38 | 0.48 | 68.3 | 5.39 | 7.2 |
| Cau hinh cuoi trong app: G1' + EM 5 vong, K=0, quy tac (b) | 0.65 | 0.60 | 0.62 | 71.9 | 1.96 | 12.2 |
| (san: biet ky vong that) | 0.77 | 0.71 | 0.74 | 79.4 | 0.70 | 9.1 |

## Quyet dinh: tam 15-35, Tet (len ke hoach truoc)

| Phuong phap | co: precision | co: recall | co: F1 | so chuyen dung % | khach bi bo lai % | chuyen thua / 100 khung |
|---|---|---|---|---|---|---|
| SN4 sach (bo ngay le) | 0.48 | 0.30 | 0.37 | 66.1 | 10.87 | 10.2 |
| App hien tai (ti le x ghe) | 0.66 | 0.14 | 0.23 | 70.0 | 12.25 | 2.2 |
| Prophet | 0.68 | 0.56 | 0.61 | 73.1 | 3.49 | 9.5 |
| LightGBM toan cuc | 0.70 | 0.36 | 0.47 | 71.6 | 7.47 | 5.1 |
| Cau hinh cuoi trong app: G1' + EM 5 vong, K=0, quy tac (b) | 0.67 | 0.67 | 0.67 | 75.1 | 1.74 | 12.0 |
| (san: biet ky vong that) | 0.79 | 0.66 | 0.72 | 80.6 | 0.80 | 7.4 |
