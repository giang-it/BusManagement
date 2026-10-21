# Step 0: one summary table per experiment for forecast_discussion.md section 12.
# WAPE % by horizon and on each lunar-new-year window (horizon 1-35), storm days left out (nobody can
# forecast them; report.py scores them as their own segment).
# Usage: python -I summary.py <forecasts csv[,more csv]> <target> <method[,method...]>
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pandas as pd
from report import LABEL


def wape(d, target):
    d = d.dropna(subset=["yhat", target])
    return 100 * (d[target] - d["yhat"]).abs().sum() / d[target].sum() if len(d) else float("nan")


def main():
    df = pd.concat([pd.read_csv(p) for p in sys.argv[1].split(",")], ignore_index=True)
    target, methods = sys.argv[2], sys.argv[3].split(",")
    if "storm" in df.columns:
        df = df[df["storm"] != 1]
    cols = [("1-7 ngay", lambda d: d[d["h"] <= 7]), ("15-35 ngay", lambda d: d[d["h"] >= 15]),
            ("1-7, ngay thuong", lambda d: d[(d["h"] <= 7) & (d["seg"] == "normal")]),
            ("Tet 2025, 1-35", lambda d: d[d["seg"] == "cny2025"]), ("Tet 2026, 1-35", lambda d: d[d["seg"] == "cny2026"])]
    print("| Phuong phap | " + " | ".join(c for c, _ in cols) + " |")
    print("|---|" + "---|" * len(cols))
    for m in methods:
        dm = df[df["method"] == m]
        print(f"| {LABEL.get(m, m)} | " + " | ".join(f"{wape(f(dm), target):.2f}" for _, f in cols) + " |")


if __name__ == "__main__":
    main()
