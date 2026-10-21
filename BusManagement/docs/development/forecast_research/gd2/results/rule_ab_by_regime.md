# Rules (a) and (b) against the main model, split by whether rule (a) is active (gd2/split_by_rule_a.py, 2026-10-22)

```
forecasts.csv: 72 origins; rule (a) inactive in 45, active in 27
  active:   2023-12-24 .. 2024-12-22
  inactive: 2025-01-05 .. 2026-09-13
hk_rules.csv: 90 origins; rule (a) inactive in 83, active in 7
  active:   2024-12-29 .. 2025-02-09
  inactive: 2025-02-16 .. 2026-09-13
```

## Synthetic (B') — rule (a) inactive

h1-7 seg=all target=demand

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| glm_rule_a | glm_main | 22.17 | 22.17 | 0 / 45 / 0 | +0.00 | [+0.00, +0.00] |
| glm_rule_b | glm_main | 22.06 | 22.17 | 30 / 0 / 15 | -0.11 | [-0.26, +0.04] |

h15-35 seg=all target=demand

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| glm_rule_a | glm_main | 22.24 | 22.24 | 0 / 45 / 0 | +0.00 | [+0.00, +0.00] |
| glm_rule_b | glm_main | 22.17 | 22.24 | 30 / 0 / 15 | -0.07 | [-0.13, -0.00] |

h1-35 seg=holiday target=demand

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| glm_rule_a | glm_main | 27.76 | 27.76 | 0 / 17 / 0 | +0.00 | [+0.00, +0.00] |
| glm_rule_b | glm_main | 26.93 | 27.76 | 16 / 0 / 1 | -0.83 | [-1.19, -0.52] |

h1-35 seg=cny target=demand

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| glm_rule_a | glm_main | 24.90 | 24.90 | 0 / 9 / 0 | +0.00 | [+0.00, +0.00] |
| glm_rule_b | glm_main | 25.05 | 24.90 | 4 / 0 / 5 | +0.15 | [-0.19, +0.39] |

h1-35 seg=normal target=demand

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| glm_rule_a | glm_main | 21.31 | 21.31 | 0 / 45 / 0 | +0.00 | [+0.00, +0.00] |
| glm_rule_b | glm_main | 21.31 | 21.31 | 25 / 0 / 20 | -0.00 | [-0.00, +0.00] |

## Synthetic (B') — rule (a) active

h1-7 seg=all target=demand

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| glm_rule_a | glm_main | 24.09 | 23.29 | 8 / 0 / 19 | +0.81 | [-0.86, +3.75] |
| glm_rule_b | glm_main | 22.91 | 23.29 | 17 / 0 / 10 | -0.38 | [-1.00, +0.13] |

h15-35 seg=all target=demand

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| glm_rule_a | glm_main | 24.25 | 23.69 | 12 / 0 / 15 | +0.56 | [-0.29, +1.87] |
| glm_rule_b | glm_main | 23.34 | 23.69 | 19 / 0 / 8 | -0.36 | [-0.78, -0.06] |

h1-35 seg=holiday target=demand

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| glm_rule_a | glm_main | 25.28 | 27.51 | 7 / 0 / 4 | -2.24 | [-4.35, -0.54] |
| glm_rule_b | glm_main | 25.98 | 27.51 | 11 / 0 / 0 | -1.53 | [-2.28, -0.93] |

h1-35 seg=cny target=demand

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| glm_rule_a | glm_main | 40.51 | 34.29 | 3 / 0 / 3 | +6.22 | [+0.27, +12.24] |
| glm_rule_b | glm_main | 32.00 | 34.29 | 5 / 0 / 1 | -2.29 | [-3.62, -0.42] |

h1-35 seg=normal target=demand

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| glm_rule_a | glm_main | 21.85 | 21.79 | 5 / 0 / 22 | +0.05 | [-0.02, +0.12] |
| glm_rule_b | glm_main | 21.79 | 21.79 | 18 / 0 / 9 | -0.00 | [-0.01, +0.00] |

## Hong Kong (A) — rule (a) inactive

h1-7 seg=all target=y

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| hk_rule_a | glm_dev_k0 | 10.05 | 10.05 | 0 / 83 / 0 | +0.00 | [+0.00, +0.00] |
| hk_rule_b | glm_dev_k0 | 9.93 | 10.05 | 43 / 0 / 40 | -0.12 | [-0.21, -0.04] |

h15-35 seg=all target=y

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| hk_rule_a | glm_dev_k0 | 11.85 | 11.85 | 0 / 83 / 0 | +0.00 | [+0.00, +0.00] |
| hk_rule_b | glm_dev_k0 | 11.73 | 11.85 | 39 / 0 / 44 | -0.12 | [-0.19, -0.07] |

h1-35 seg=holiday target=y

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| hk_rule_a | glm_dev_k0 | 15.61 | 15.61 | 0 / 52 / 0 | +0.00 | [+0.00, +0.00] |
| hk_rule_b | glm_dev_k0 | 14.96 | 15.61 | 39 / 0 / 13 | -0.65 | [-0.85, -0.46] |

h1-35 seg=cny target=y

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| hk_rule_a | glm_dev_k0 | 14.56 | 14.56 | 0 / 9 / 0 | +0.00 | [+0.00, +0.00] |
| hk_rule_b | glm_dev_k0 | 13.88 | 14.56 | 8 / 0 / 1 | -0.68 | [-0.84, -0.48] |

h1-35 seg=normal target=y

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| hk_rule_a | glm_dev_k0 | 10.27 | 10.27 | 0 / 83 / 0 | +0.00 | [+0.00, +0.00] |
| hk_rule_b | glm_dev_k0 | 10.28 | 10.27 | 27 / 0 / 56 | +0.01 | [+0.01, +0.02] |

## Hong Kong (A) — rule (a) active

h1-7 seg=all target=y

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| hk_rule_a | glm_dev_k0 | 14.63 | 14.77 | 2 / 0 / 5 | -0.14 | [-3.75, +3.05] |
| hk_rule_b | glm_dev_k0 | 13.34 | 14.77 | 5 / 0 / 2 | -1.43 | [-2.73, -0.32] |

h15-35 seg=all target=y

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| hk_rule_a | glm_dev_k0 | 14.42 | 14.07 | 3 / 0 / 4 | +0.35 | [-2.60, +3.20] |
| hk_rule_b | glm_dev_k0 | 12.84 | 14.07 | 4 / 0 / 3 | -1.24 | [-2.26, -0.16] |

h1-35 seg=holiday target=y

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| hk_rule_a | glm_dev_k0 | 13.73 | 10.80 | 0 / 0 / 1 | +2.93 | [+2.93, +2.93] |
| hk_rule_b | glm_dev_k0 | 9.98 | 10.80 | 1 / 0 / 0 | -0.82 | [-0.82, -0.82] |

h1-35 seg=cny target=y

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| hk_rule_a | glm_dev_k0 | 19.06 | 21.66 | 5 / 0 / 2 | -2.60 | [-4.58, -0.55] |
| hk_rule_b | glm_dev_k0 | 18.90 | 21.66 | 6 / 0 / 1 | -2.76 | [-3.16, -2.21] |

h1-35 seg=normal target=y

| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |
|---|---|---|---|---|---|---|
| hk_rule_a | glm_dev_k0 | 10.24 | 6.95 | 1 / 0 / 6 | +3.29 | [+1.11, +4.69] |
| hk_rule_b | glm_dev_k0 | 7.07 | 6.95 | 2 / 0 / 5 | +0.12 | [+0.02, +0.20] |
