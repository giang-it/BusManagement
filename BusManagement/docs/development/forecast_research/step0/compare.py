# Step 0: is method A really better than method B, or is it one lucky stretch of origins?
# Per origin: WAPE over the chosen horizon; then how many origins A wins / ties / loses, and a bootstrap 95% interval
# (resampling origins) for the pooled WAPE difference A - B. Negative = A better.
# Usage: python -I compare.py <forecasts csv[,more csv]> <target> <hmin> <hmax> <A:B[,A:B...]> [segment prefix]
import sys

import numpy as np
import pandas as pd


def main():
    df = pd.concat([pd.read_csv(p) for p in sys.argv[1].split(",")], ignore_index=True)
    target, hmin, hmax = sys.argv[2], int(sys.argv[3]), int(sys.argv[4])
    pairs = [p.split(":") for p in sys.argv[5].split(",")]
    if "storm" in df.columns:
        df = df[df["storm"] != 1]
    df = df[(df["h"] >= hmin) & (df["h"] <= hmax)].dropna(subset=["yhat", target])
    if len(sys.argv) > 6:
        df = df[df["seg"].str.startswith(sys.argv[6])]
    df = df.assign(err=(df[target] - df["yhat"]).abs())
    per = df.groupby(["method", "origin"])[["err", target]].sum()
    rng = np.random.default_rng(0)
    seg = sys.argv[6] if len(sys.argv) > 6 else "all"
    print(f"h{hmin}-{hmax} seg={seg} target={target}\n")
    print("| A | B | WAPE A | WAPE B | A thang / hoa / thua | A - B (diem %) | KTC 95% |")
    print("|---|---|---|---|---|---|---|")
    for a, b in pairs:
        pa, pb = per.loc[a], per.loc[b]
        common = pa.index.intersection(pb.index)
        ea, eb, yy = pa.loc[common, "err"].to_numpy(), pb.loc[common, "err"].to_numpy(), pa.loc[common, target].to_numpy()
        gap = (ea - eb) / yy
        tie = np.abs(gap) < 1e-9          # e.g. G2 = clean SN4 when no holiday falls in the horizon
        wins, ties = int(np.sum((gap < 0) & ~tie)), int(np.sum(tie))
        diff = 100 * (ea.sum() - eb.sum()) / yy.sum()
        boot = []
        for _ in range(2000):
            i = rng.integers(0, len(common), len(common))
            boot.append(100 * (ea[i].sum() - eb[i].sum()) / yy[i].sum())
        lo, hi = np.percentile(boot, [2.5, 97.5])
        print(f"| {a} | {b} | {100 * ea.sum() / yy.sum():.2f} | {100 * eb.sum() / yy.sum():.2f} | "
              f"{wins} / {ties} / {len(common) - wins - ties} | "
              f"{diff:+.2f} | [{lo:+.2f}, {hi:+.2f}] |")


if __name__ == "__main__":
    main()
