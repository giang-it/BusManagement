# M-15 point 1, follow-up (2026-10-22): split the forecast origins into those where rule (a) changes nothing
# (every holiday already seen in full >= 2 times — the situation the live app is in, with history from 2023)
# and those where rule (a) is active (short history), so rules (a) and (b) can be compared in each situation.
# Rule (a) counts as inactive at an origin when its forecasts equal the main model's (to 1e-9) on every row.
# Writes <out prefix>_inactive.csv and <out prefix>_active.csv; score them with ../step0/compare.py. Research only.
# Usage: python -I split_by_rule_a.py <forecasts csv> <main> <rule a> <rule b> <out prefix>
#   synthetic: python -I split_by_rule_a.py <run_gd2.py out csv> glm_main glm_rule_a glm_rule_b synth
#   Hong Kong: python -I split_by_rule_a.py <hk_rules.py out csv> glm_dev_k0 hk_rule_a hk_rule_b hk
# Then e.g.: python -I ../step0/compare.py synth_inactive.csv demand 1 7 glm_rule_a:glm_main,glm_rule_b:glm_main
import sys

import pandas as pd


def main():
    path, main_name, rule_a, rule_b, out = sys.argv[1:6]
    df = pd.read_csv(path)
    df = df[df["method"].isin([main_name, rule_a, rule_b])]
    key = ["origin", "series", "h"]
    m = df[df["method"] == main_name].set_index(key)["yhat"]
    a = df[df["method"] == rule_a].set_index(key)["yhat"]
    same = (m - a.reindex(m.index)).abs().groupby(level="origin").max() < 1e-9
    inactive, active = sorted(same[same].index), sorted(same[~same].index)
    print(f"{path}: {len(same)} origins; rule (a) inactive in {len(inactive)}, active in {len(active)}")
    print(f"  active:   {active[0]} .. {active[-1]}" if active else "  active:   none")
    print(f"  inactive: {inactive[0]} .. {inactive[-1]}" if inactive else "  inactive: none")
    df[df["origin"].isin(inactive)].to_csv(out + "_inactive.csv", index=False)
    df[df["origin"].isin(active)].to_csv(out + "_active.csv", index=False)


if __name__ == "__main__":
    main()
