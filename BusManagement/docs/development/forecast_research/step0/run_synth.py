# Step 0, experiment B: synthetic slot demand from synth_generator.py, censored by the seats offered.
# Series = route x departure hour (15, as in the app). Weekly origins 2024-12-29 .. 2026-09-13, horizon 35 days.
# Every method trains on SALES, which is all the app records; score against the true DEMAND:
#   python -I report.py <out csv> demand
# Storm days are known only after the fact (the Incident ROAD_ISSUE analogue): dropped from training,
# scored as their own segment.
# Usage: python -I run_synth.py <synth csv> <out forecasts csv> [methods comma list] [every n-th origin]
# Methods: harness.SHARED + app (ForecastService on occupancy, x standard seats), glm_dev (slot GLM),
#          glm_em (+ EM unconstraining), glm_em1 (+ EM, one pooling group = no route direction attribute),
#          glm_em_k0 (+ EM, no yearly Fourier term: the 28-day level correction carries the season),
#          oracle (the generator's true expected demand: the noise floor, not a method).
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

TRAIN_START = pd.Timestamp("2023-07-01")
H = 35
EM_ITERS = 15

T = pd.Timestamp
YEARS = [2023, 2024, 2025, 2026]
# What the forecaster is told: the calendar date of each holiday. The generator anchors its surges on the
# official break instead, which moves relative to these dates from year to year.
WINDOWS = [
    ("cny", [T("2024-02-10"), T("2025-01-29"), T("2026-02-17")], -14, 21),
    ("tet_dl", [T(f"{y + 1}-01-01") for y in YEARS], -3, 4),
    ("gio_to", [T("2024-04-18"), T("2025-04-07"), T("2026-04-26")], -3, 2),
    ("le_30_4", [T(f"{y}-04-30") for y in YEARS], -4, 5),
    ("quoc_khanh", [T(f"{y}-09-02") for y in YEARS], -4, 2),
]
ALL = harness.SHARED + ["app", "glm_dev", "glm_em", "glm_em1", "glm_em_k0", "oracle"]


def load(path):
    df = pd.read_csv(path, parse_dates=["date"])
    wide = {c: df.pivot(index="date", columns="series", values=c).astype(float)
            for c in ("sales", "seats", "demand", "cap", "storm", "mu")}
    groups = df.drop_duplicates("series").set_index("series").loc[wide["sales"].columns, "group"].tolist()
    return wide, groups


def main():
    wide, groups = load(sys.argv[1])
    out_path = sys.argv[2]
    wanted = sys.argv[3].split(",") if len(sys.argv) > 3 and sys.argv[3] != "all" else ALL
    Y = wide["sales"].mask(wide["storm"] == 1)          # training view: storm days removed
    end = Y.index.max()
    origins = list(pd.date_range("2024-12-29", end - H * DAY, freq="7D"))
    if len(sys.argv) > 4:  # smoke test: only every n-th origin
        origins = origins[::int(sys.argv[4])]
    hol = M.holiday_frame(WINDOWS, Y.index.min(), end + pd.Timedelta(days=60))
    keys = M.active_keys(hol)
    print(f"series={Y.shape[1]} days={len(Y)} {Y.index.min().date()}..{end.date()} origins={len(origins)} "
          f"{origins[0].date()}..{origins[-1].date()} censored={(Y >= wide['seats']).sum().sum() / Y.count().sum():.1%}",
          flush=True)

    results = harness.run_shared(Y, groups, WINDOWS, hol, origins, wanted, H, TRAIN_START)

    if "oracle" in wanted:  # the generator's own expected demand: the error floor no method can beat
        for o in origins:
            p = Y.index.get_loc(o)
            results[("oracle", o)] = wide["mu"].to_numpy(float)[p + 1:p + 1 + H]

    if "app" in wanted:
        R = (Y / wide["seats"]).clip(0, 1)
        cap = wide["cap"].iloc[0].to_numpy()
        for o in origins:
            results[("app", o)] = M.app_rate_current(R, o, H, TRAIN_START) * cap

    one = [0] * len(groups)
    variants = [v for v in [("glm_dev", 0, groups, 3), ("glm_em", EM_ITERS, groups, 3), ("glm_em1", EM_ITERS, one, 3),
                            ("glm_em_k0", EM_ITERS, groups, 0)] if v[0] in wanted]
    if variants:
        t = time.time()
        Yv, Cv = Y.to_numpy(float), wide["seats"].to_numpy(float)
        tasks = [(Yv, Cv, Y.index, g, keys, Y.index.get_loc(o), H, 0, em, 2.0, K)
                 for _, em, g, K in variants for o in origins]
        with Pool(8) as pool:
            res = pool.map(M.slot_glm_task, tasks)
        for i, (name, *_) in enumerate(variants):
            for j, o in enumerate(origins):
                results[(name, o)] = res[i * len(origins) + j]
        print(f"{','.join(v[0] for v in variants)} done {time.time() - t:.0f}s", flush=True)

    harness.write_long(results, Y, groups, harness.segments(hol), H, out_path,
                       extra={c: wide[c] for c in ("sales", "demand", "cap", "seats", "storm")})


DAY = M.DAY
if __name__ == "__main__":
    main()
