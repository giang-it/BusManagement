# GĐ7: copy only some methods out of a long forecasts CSV, so two runs over the SAME origins can be scored
# together without a method (e.g. sn4c) appearing twice. Research only.
# Usage: python -I -B pick_methods.py <in forecasts csv> <out csv> <method[,method...]>
# Example (real Hong Kong data): step 0's hk_full.csv holds LightGBM and Prophet, GĐ2's hk_rules.csv holds the
# final model on that data (hk_rule_b = G1', K=0, rule (b)); both use step 0's 90 origins.
#   python -I -B pick_methods.py hk_full.csv hk_ml.csv lgb,prophet
#   python -I -B ../step0/compare.py hk_rules.csv,hk_ml.csv y 1 7 hk_rule_b:lgb,hk_rule_b:prophet
import sys

import pandas as pd

if __name__ == "__main__":
    src, dst, names = sys.argv[1], sys.argv[2], sys.argv[3].split(",")
    df = pd.read_csv(src)
    df[df["method"].isin(names)].to_csv(dst, index=False)
    print(dst, {m: int((df["method"] == m).sum()) for m in names})
