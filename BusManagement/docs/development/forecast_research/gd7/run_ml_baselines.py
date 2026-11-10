# GĐ7 of the Demand Forecast redesign (docs and defence; QĐ-6 keeps LightGBM and Prophet as Python-only
# references, never in the app): the two outside methods backtested on the app's own generated data, on exactly
# the dataset and the 72 origins that gd4/run_final.py used for the final configuration (§14.1 of
# ../../forecast_discussion.md). Research only — nothing here touches the app or the database.
#
# Both methods are step 0's own code, unchanged (step0/methods.py through step0/harness.py): Prophet with the app's
# holiday windows as its holiday table, one fit per series and origin on log sales; LightGBM, one global model on
# log(sales / clean SN4), retrained every 4th origin. Like every method in the GĐ2 runs, they learn from SALES —
# what the app records, capped by seats — and are scored against the TRUE demand the generator exported.
#
# Windows starts pool workers by re-importing this file, so the command line is read only under __main__.
#
# Usage (the step 0 venv; dataset.csv from gd2/build_dataset.py):
#   python -I -B run_ml_baselines.py <dataset.csv> <forecasts_ml.csv>
#   python -I -B report_gd7.py <run_final forecasts.csv>,<forecasts_ml.csv>
#   python -I -B ../step0/compare.py <run_final forecasts.csv>,<forecasts_ml.csv> demand 1 7 glm_final:lgb,glm_final:prophet
import os
import sys
import time
from pathlib import Path

RESEARCH = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RESEARCH / "gd2"))
sys.path.insert(0, str(RESEARCH / "step0"))
import pandas as pd

import harness
import run_gd2 as R

H = R.H


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    wide, groups = R.load(sys.argv[1])
    out_path = sys.argv[2]
    Y = wide["sales"].mask(wide["storm"] == 1)
    end = Y.index.max()
    origins = list(pd.date_range("2023-12-24", end - H * R.DAY, freq="14D"))
    wins = R.windows("current")
    hol = R.M.holiday_frame(wins, Y.index.min(), end + pd.Timedelta(days=60))
    print(f"series={Y.shape[1]} days={len(Y)} {Y.index.min().date()}..{end.date()} origins={len(origins)} "
          f"{origins[0].date()}..{origins[-1].date()}", flush=True)
    t = time.time()
    results = harness.run_shared(Y, groups, wins, hol, origins, ["prophet", "lgb"], H, R.TRAIN_START)
    print(f"done {time.time() - t:.0f}s", flush=True)
    harness.write_long(results, Y, groups, harness.segments(hol), H, out_path,
                       extra={c: wide[c] for c in ("sales", "demand", "cap", "seats", "storm")})


if __name__ == "__main__":
    os.environ.setdefault("OPENBLAS_NUM_THREADS", "1")
    main()
