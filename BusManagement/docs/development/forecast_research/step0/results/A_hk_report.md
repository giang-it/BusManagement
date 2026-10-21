# Q:/out/hk_full.csv,Q:/out/hk_glmdev.csv,Q:/out/hk_glmdev_k0.csv  target=y  rows=693000  origins=90  series=20

## WAPE % theo tam du bao (moi loai ngay)

| Phuong phap | h1-7 | h8-14 | h15-28 | h29-35 |
|---|---|---|---|---|
| SN1 cung thu tuan truoc | 13.78 | 14.51 | 16.00 | 16.13 |
| SN4 TB 4 cung thu | 12.53 | 13.08 | 13.45 | 13.42 |
| SN4 sach (bo ngay le) | 11.30 | 11.89 | 12.59 | 12.81 |
| LR hien tai (xu huong) | 14.70 | 14.81 | 14.96 | 15.10 |
| Holt-Winters tat dan | 12.09 | 13.60 | 14.75 | 14.94 |
| Prophet | 10.91 | 11.87 | 12.58 | 12.63 |
| G2 nen x he so le hoc | 10.80 | 11.51 | 12.56 | 13.05 |
| G1 GLM lich, le chi gop theo chieu | 11.22 | 12.47 | 14.37 | 16.31 |
| G1' GLM lich + lech tung chuoi | 10.32 | 11.54 | 13.31 | 15.13 |
| G1' khong mua nam (K=0) | 10.40 | 11.13 | 11.88 | 12.28 |
| LightGBM toan cuc | 9.92 | 10.27 | 10.77 | 11.28 |

## WAPE % theo loai ngay, tam 1-7

| Phuong phap | cny2025 | cny2026 | holiday | normal |
|---|---|---|---|---|
| SN1 cung thu tuan truoc | 25.89 | 25.47 | 23.50 | 10.85 |
| SN4 TB 4 cung thu | 20.19 | 19.97 | 19.47 | 10.54 |
| SN4 sach (bo ngay le) | 18.14 | 19.28 | 19.77 | 9.06 |
| LR hien tai (xu huong) | 19.23 | 19.22 | 18.36 | 13.59 |
| Holt-Winters tat dan | 18.05 | 18.99 | 20.89 | 9.89 |
| Prophet | 22.96 | 11.25 | 16.88 | 9.26 |
| G2 nen x he so le hoc | 19.59 | 13.62 | 17.47 | 9.06 |
| G1 GLM lich, le chi gop theo chieu | 21.23 | 20.27 | 17.60 | 9.13 |
| G1' GLM lich + lech tung chuoi | 18.49 | 15.65 | 15.46 | 8.73 |
| G1' khong mua nam (K=0) | 21.18 | 16.36 | 15.31 | 8.69 |
| LightGBM toan cuc | 18.87 | 10.82 | 15.48 | 8.47 |

## WAPE % theo loai ngay, tam 15-35 (len ke hoach)

| Phuong phap | cny2025 | cny2026 | holiday | normal |
|---|---|---|---|---|
| SN1 cung thu tuan truoc | 22.08 | 21.91 | 19.96 | 14.67 |
| SN4 TB 4 cung thu | 19.41 | 19.09 | 17.71 | 12.01 |
| SN4 sach (bo ngay le) | 19.53 | 19.85 | 17.79 | 10.94 |
| LR hien tai (xu huong) | 20.35 | 19.23 | 17.98 | 13.95 |
| Holt-Winters tat dan | 21.07 | 20.59 | 18.41 | 13.50 |
| Prophet | 34.64 | 11.67 | 16.72 | 10.86 |
| G2 nen x he so le hoc | 21.52 | 13.30 | 19.61 | 10.94 |
| G1 GLM lich, le chi gop theo chieu | 20.97 | 18.60 | 20.32 | 13.50 |
| G1' GLM lich + lech tung chuoi | 18.32 | 15.07 | 18.15 | 12.81 |
| G1' khong mua nam (K=0) | 22.17 | 13.69 | 15.72 | 10.73 |
| LightGBM toan cuc | 21.90 | 10.84 | 15.47 | 9.56 |

## MAPE % tam 1-7 (de so voi con so cua ForecastService)

| Phuong phap | moi ngay | ngay thuong | ngay le |
|---|---|---|---|
| SN1 cung thu tuan truoc | 31.96 | 32.63 | 29.37 |
| SN4 TB 4 cung thu | 30.90 | 33.08 | 22.38 |
| SN4 sach (bo ngay le) | 28.41 | 30.25 | 21.22 |
| LR hien tai (xu huong) | 34.49 | 37.91 | 21.08 |
| Holt-Winters tat dan | 29.83 | 31.57 | 23.02 |
| Prophet | 42.45 | 39.41 | 54.37 |
| G2 nen x he so le hoc | 27.85 | 30.25 | 18.46 |
| G1 GLM lich, le chi gop theo chieu | 27.82 | 29.34 | 21.87 |
| G1' GLM lich + lech tung chuoi | 26.75 | 29.14 | 17.40 |
| G1' khong mua nam (K=0) | 27.43 | 29.64 | 18.80 |
| LightGBM toan cuc | 26.90 | 29.16 | 18.05 |
