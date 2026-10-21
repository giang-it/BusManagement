# Step 0: score a forecasts CSV written by run_hk.py / run_synth.py.
# Usage: python -I report.py <forecasts csv[,more csv]> [target column, default y; run_synth output: demand]
# WAPE = sum|y - yhat| / sum y   (robust to small values; primary metric)
# MAPE = mean |y - yhat| / y over y > 0 (kept for continuity with ForecastService's backtest)
# With the run_synth columns (demand, cap) it also scores the DECISION the forecast feeds.
import sys
import numpy as np
import pandas as pd

ORDER = ["sn1", "sn4", "sn4c", "app", "lr", "hw", "prophet", "lift", "glm", "glm_dev", "glm_dev_k0", "glm_em",
         "glm_em1", "glm_em_k0", "lgb", "oracle"]
LABEL = {"sn1": "SN1 cung thu tuan truoc", "sn4": "SN4 TB 4 cung thu", "sn4c": "SN4 sach (bo ngay le)",
         "app": "App hien tai (ti le x ghe)", "lr": "LR hien tai (xu huong)", "hw": "Holt-Winters tat dan",
         "prophet": "Prophet", "lift": "G2 nen x he so le hoc", "glm": "G1 GLM lich, le chi gop theo chieu",
         "glm_dev": "G1' GLM lich + lech tung chuoi", "glm_em": "G1' + EM chan tran",
         "glm_em1": "G1' + EM, khong biet chieu tuyen", "glm_dev_k0": "G1' khong mua nam (K=0)",
         "glm_em_k0": "G1' + EM, khong mua nam (K=0)",
         "lgb": "LightGBM toan cuc", "oracle": "(san: biet ky vong that)"}
THRESHOLD = 0.90   # the app's reinforcement threshold
BUCKETS = [(1, 7, "h1-7"), (8, 14, "h8-14"), (15, 28, "h15-28"), (29, 35, "h29-35")]


def scores(d, target):
    d = d.dropna(subset=["yhat", target])
    if d.empty:
        return np.nan, np.nan, 0
    err = (d[target] - d["yhat"]).abs()
    pos = d[target] > 0
    return 100 * err.sum() / d[target].sum(), 100 * (err[pos] / d.loc[pos, target]).mean(), len(d)


def table(df, target, by_fn, cols):
    methods = [m for m in ORDER if m in set(df["method"])]
    lines = ["| Phuong phap | " + " | ".join(cols) + " |", "|---|" + "---|" * len(cols)]
    for m in methods:
        dm = df[df["method"] == m]
        cells = []
        for c in cols:
            w, _, n = scores(by_fn(dm, c), target)
            cells.append(f"{w:.2f}" if n else "-")
        lines.append(f"| {LABEL.get(m, m)} | " + " | ".join(cells) + " |")
    return "\n".join(lines)


def decision_table(df, title):
    """Trips needed = max(1, ceil(demand / (0.9 x standard seats))), the slot-demand form of the app's
    0.90 rule; a reinforcement flag = 2 or more trips. Unserved = passengers left over when running the
    forecast's number of trips; extra trips = trips run beyond what the true demand needed."""
    print(f"\n## Quyet dinh: {title}\n")
    print("| Phuong phap | co: precision | co: recall | co: F1 | so chuyen dung % | khach bi bo lai % | chuyen thua / 100 khung |")
    print("|---|---|---|---|---|---|---|")
    for m in [m for m in ORDER if m in set(df["method"])]:
        d = df[df["method"] == m].dropna(subset=["yhat", "demand"])
        if d.empty:
            continue
        per = THRESHOLD * d["cap"]
        need = np.maximum(1, np.ceil(d["demand"] / per))
        plan = np.maximum(1, np.ceil(d["yhat"].clip(lower=0) / per))
        t, p = need >= 2, plan >= 2
        tp = (t & p).sum()
        prec = tp / p.sum() if p.sum() else np.nan
        rec = tp / t.sum() if t.sum() else np.nan
        f1 = 2 * prec * rec / (prec + rec) if prec + rec > 0 else np.nan
        unserved = np.maximum(0, d["demand"] - plan * d["cap"]).sum() / d["demand"].sum()
        extra = np.maximum(0, plan - need).sum() / len(d)
        print(f"| {LABEL.get(m, m)} | {prec:.2f} | {rec:.2f} | {f1:.2f} | {100 * (plan == need).mean():.1f} | "
              f"{100 * unserved:.2f} | {100 * extra:.1f} |")


def main():
    df = pd.concat([pd.read_csv(p) for p in sys.argv[1].split(",")], ignore_index=True)
    target = sys.argv[2] if len(sys.argv) > 2 else "y"
    if "storm" in df.columns:
        df.loc[df["storm"] == 1, "seg"] = "storm"
    segs = sorted(set(df["seg"]))
    print(f"# {sys.argv[1]}  target={target}  rows={len(df)}  origins={df['origin'].nunique()}  series={df['series'].nunique()}\n")
    print("## WAPE % theo tam du bao (moi loai ngay)\n")
    print(table(df, target, lambda d, c: d[(d["h"] >= next(b[0] for b in BUCKETS if b[2] == c))
                                           & (d["h"] <= next(b[1] for b in BUCKETS if b[2] == c))],
                [b[2] for b in BUCKETS]))
    print("\n## WAPE % theo loai ngay, tam 1-7\n")
    print(table(df, target, lambda d, c: d[(d["h"] <= 7) & (d["seg"] == c)], segs))
    print("\n## WAPE % theo loai ngay, tam 15-35 (len ke hoach)\n")
    print(table(df, target, lambda d, c: d[(d["h"] >= 15) & (d["seg"] == c)], segs))
    print("\n## MAPE % tam 1-7 (de so voi con so cua ForecastService)\n")
    methods = [m for m in ORDER if m in set(df["method"])]
    print("| Phuong phap | moi ngay | ngay thuong | ngay le |\n|---|---|---|---|")
    for m in methods:
        dm = df[(df["method"] == m) & (df["h"] <= 7)]
        a = scores(dm, target)[1]
        b = scores(dm[dm["seg"] == "normal"], target)[1]
        c = scores(dm[dm["seg"] != "normal"], target)[1]
        print(f"| {LABEL.get(m, m)} | {a:.2f} | {b:.2f} | {c:.2f} |")
    if {"demand", "cap"} <= set(df.columns):
        decision_table(df[df["h"] <= 7], "tam 1-7 (Recommendation / What-if), moi ngay")
        decision_table(df[(df["h"] <= 7) & (df["seg"] != "normal") & (df["seg"] != "storm")],
                       "tam 1-7, chi ngay le/Tet")
        decision_table(df[(df["h"] >= 15) & df["seg"].str.startswith("cny")], "tam 15-35, Tet (len ke hoach truoc)")


if __name__ == "__main__":
    main()
