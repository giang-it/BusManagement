# C:/Users/nguye/AppData/Local/Temp/claude/E--Y4-S2-PRETHESIS-BusManagement-BusManagement/0e46b959-dd21-460c-a8dc-b0fdfab59548/scratchpad/gd2/hk_rules.csv,C:/Users/nguye/AppData/Local/Temp/claude/E--Y4-S2-PRETHESIS-BusManagement-BusManagement/c17313ff-88f5-4beb-a3ef-7127691160ca/scratchpad/gd7/hk_ml.csv  target=y  rows=504000  origins=90  series=20

## WAPE % theo tam du bao (moi loai ngay)

| Phuong phap | h1-7 | h8-14 | h15-28 | h29-35 |
|---|---|---|---|---|
| SN4 sach (bo ngay le) | 11.30 | 11.89 | 12.59 | 12.81 |
| Holt-Winters tat dan | 12.09 | 13.60 | 14.75 | 14.94 |
| Prophet | 10.91 | 11.87 | 12.58 | 12.63 |
| LightGBM toan cuc | 9.92 | 10.27 | 10.77 | 11.28 |
| Cau hinh cuoi tren du lieu HK: G1', K=0, quy tac (b) (khong co ghe nen khong EM) | 10.18 | 10.92 | 11.67 | 12.08 |

## WAPE % theo loai ngay, tam 1-7

| Phuong phap | cny2025 | cny2026 | holiday | normal |
|---|---|---|---|---|
| SN4 sach (bo ngay le) | 18.14 | 19.28 | 19.77 | 9.06 |
| Holt-Winters tat dan | 18.05 | 18.99 | 20.89 | 9.89 |
| Prophet | 22.96 | 11.25 | 16.88 | 9.26 |
| LightGBM toan cuc | 18.87 | 10.82 | 15.48 | 8.47 |
| Cau hinh cuoi tren du lieu HK: G1', K=0, quy tac (b) (khong co ghe nen khong EM) | 18.54 | 15.40 | 14.73 | 8.70 |

## WAPE % theo loai ngay, tam 15-35 (len ke hoach)

| Phuong phap | cny2025 | cny2026 | holiday | normal |
|---|---|---|---|---|
| SN4 sach (bo ngay le) | 19.53 | 19.85 | 17.79 | 10.94 |
| Holt-Winters tat dan | 21.07 | 20.59 | 18.41 | 13.50 |
| Prophet | 34.64 | 11.67 | 16.72 | 10.86 |
| LightGBM toan cuc | 21.90 | 10.84 | 15.47 | 9.56 |
| Cau hinh cuoi tren du lieu HK: G1', K=0, quy tac (b) (khong co ghe nen khong EM) | 19.34 | 13.14 | 15.05 | 10.76 |

## MAPE % tam 1-7 (de so voi con so cua ForecastService)

| Phuong phap | moi ngay | ngay thuong | ngay le |
|---|---|---|---|
| SN4 sach (bo ngay le) | 28.41 | 30.25 | 21.22 |
| Holt-Winters tat dan | 29.83 | 31.57 | 23.02 |
| Prophet | 42.45 | 39.41 | 54.37 |
| LightGBM toan cuc | 26.90 | 29.16 | 18.05 |
| Cau hinh cuoi tren du lieu HK: G1', K=0, quy tac (b) (khong co ghe nen khong EM) | 27.25 | 29.63 | 17.92 |
