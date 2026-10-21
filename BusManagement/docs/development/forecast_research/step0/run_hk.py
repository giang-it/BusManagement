# Step 0, experiment A: real data with a lunar-new-year effect (Hong Kong Immigration daily passenger traffic).
# Series = control point x direction (20 series). Weekly origins 2024-12-29 .. 2026-09-13, horizon 35 days.
# Usage: python -I run_hk.py <hk csv> <out forecasts csv> [methods comma list] [every n-th origin]
# Methods: harness.SHARED + glm (holiday effects pooled per direction only) + glm_dev (methods.slot_glm_task:
# pooled + a shrunk per-series deviation, no censoring here) + glm_dev_k0 (the same without the yearly
# Fourier term). Default: all. Takes ~25-40 min on 16 cores.
import os
import sys
import time
from multiprocessing import Pool

# one BLAS thread per process: many worker processes each reserving 16-thread BLAS buffers exhaust the page file
for _v in ("OPENBLAS_NUM_THREADS", "OMP_NUM_THREADS", "MKL_NUM_THREADS"):
    os.environ.setdefault(_v, "1")

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import numpy as np
import pandas as pd
import harness
import methods as M

TRAIN_START = pd.Timestamp("2023-04-01")   # border fully reopened 2023-02-06; skip the reopening ramp
H = 35
CONTROL_POINTS = ["Airport", "Express Rail Link West Kowloon", "Heung Yuen Wai", "Hong Kong-Zhuhai-Macao Bridge",
                  "Lo Wu", "Lok Ma Chau Spur Line", "Shenzhen Bay", "Macau Ferry Terminal",
                  "China Ferry Terminal", "Man Kam To"]

T = pd.Timestamp
CNY = [T("2024-02-10"), T("2025-01-29"), T("2026-02-17")]
EASTER = [T("2023-04-09"), T("2024-03-31"), T("2025-04-20"), T("2026-04-05")]
CHING_MING = [T("2023-04-05"), T("2024-04-04"), T("2025-04-04"), T("2026-04-05")]
DRAGON_BOAT = [T("2023-06-22"), T("2024-06-10"), T("2025-05-31"), T("2026-06-19")]
MID_AUTUMN = [T("2023-09-29"), T("2024-09-17"), T("2025-10-06"), T("2026-09-25")]
YEARS = [2023, 2024, 2025, 2026]
WINDOWS = [
    ("cny", CNY, -10, 14),
    ("national_day", [T(f"{y}-10-01") for y in YEARS], -3, 9),
    ("labour_day", [T(f"{y}-05-01") for y in YEARS], -3, 6),
    ("easter", EASTER, -3, 2),
    ("christmas", [T(f"{y}-12-25") for y in YEARS], -3, 9),
    ("ching_ming", CHING_MING, -1, 1),
    ("dragon_boat", DRAGON_BOAT, -1, 1),
    ("mid_autumn", MID_AUTUMN, -1, 2),
    ("hksar_day", [T(f"{y}-07-01") for y in YEARS], -1, 1),
]


def load(path):
    df = pd.read_csv(path, encoding="utf-8-sig")
    df = df.loc[:, ~df.columns.str.startswith("Unnamed")]
    df.columns = ["date", "cp", "dir", "hkr", "mlv", "oth", "total"]
    df["date"] = pd.to_datetime(df["date"], format="%d-%m-%Y")
    df["cp"] = df["cp"].replace({"Macao Ferry Terminal": "Macau Ferry Terminal"})
    df = df[df["cp"].isin(CONTROL_POINTS)]
    Y = df.pivot_table(index="date", columns=["cp", "dir"], values="total", aggfunc="sum")
    Y = Y.reindex(pd.date_range(TRAIN_START, df["date"].max())).astype(float)
    Y.columns = [f"{c}|{d}" for c, d in Y.columns]
    groups = [0 if c.endswith("Arrival") else 1 for c in Y.columns]
    return Y, groups


def main():
    Y, groups = load(sys.argv[1])
    out_path = sys.argv[2]
    wanted = (sys.argv[3].split(",") if len(sys.argv) > 3 and sys.argv[3] != "all"
              else harness.SHARED + ["glm", "glm_dev", "glm_dev_k0"])
    end = Y.index.max()
    origins = [o for o in pd.date_range("2024-12-29", end - H * DAY, freq="7D")]
    if len(sys.argv) > 4:  # smoke test: only every n-th origin
        origins = origins[::int(sys.argv[4])]
    hol = M.holiday_frame(WINDOWS, Y.index.min(), end + pd.Timedelta(days=60))
    keys = M.active_keys(hol)
    print(f"series={Y.shape[1]} days={len(Y)} {Y.index.min().date()}..{end.date()} origins={len(origins)} "
          f"{origins[0].date()}..{origins[-1].date()}", flush=True)

    results = harness.run_shared(Y, groups, WINDOWS, hol, origins, wanted, H, TRAIN_START)
    Yv = Y.to_numpy(float)
    uncapped = np.full_like(Yv, np.inf)   # border crossings have no seat limit: nothing is censored
    glms = {"glm": (M.calendar_glm_task, lambda p: (Yv, Y.index, groups, keys, p, H, 0)),
            "glm_dev": (M.slot_glm_task, lambda p: (Yv, uncapped, Y.index, groups, keys, p, H, 0, 0, 2.0, 3)),
            "glm_dev_k0": (M.slot_glm_task, lambda p: (Yv, uncapped, Y.index, groups, keys, p, H, 0, 0, 2.0, 0))}
    for name, (task, args) in glms.items():
        if name not in wanted:
            continue
        t = time.time()
        with Pool(6) as pool:
            res = pool.map(task, [args(Y.index.get_loc(o)) for o in origins])
        for o, F in zip(origins, res):
            results[(name, o)] = F
        print(f"{name} done {time.time() - t:.0f}s", flush=True)
    harness.write_long(results, Y, groups, harness.segments(hol), H, out_path)


DAY = M.DAY
if __name__ == "__main__":
    main()
