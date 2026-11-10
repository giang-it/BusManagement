# GĐ7: score the final model next to LightGBM and Prophet with step0/report.py (WAPE; on the app's data also the
# decision metrics: reinforcement flags, trips, passengers left behind). Research only.
# Usage (the step 0 venv):
#   app's generated data, against the TRUE demand:
#     python -I -B report_gd7.py <gd4 run_final forecasts.csv>,<run_ml_baselines forecasts.csv>
#   real Hong Kong data (no seats, so no decision metrics), against the counts:
#     python -I -B report_gd7.py <gd2 hk_rules.csv>,<pick_methods hk_ml.csv> y
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "step0"))
import report as R

R.ORDER = ["sn4c", "app", "hw", "prophet", "lgb", "glm_final", "hk_rule_b", "oracle"]
R.LABEL.update({"glm_final": "Cau hinh cuoi trong app: G1' + EM 5 vong, K=0, quy tac (b)",
                "hk_rule_b": "Cau hinh cuoi tren du lieu HK: G1', K=0, quy tac (b) (khong co ghe nen khong EM)"})

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.argv = [sys.argv[0], sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "demand"]
    R.main()
