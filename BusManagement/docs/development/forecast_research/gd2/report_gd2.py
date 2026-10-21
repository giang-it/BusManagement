# GĐ2 re-check: score run_gd2.py output with step0/report.py (WAPE against the TRUE demand, decision
# metrics), adding the GĐ2 variant names to its table order. Research only.
# Usage: python -I report_gd2.py <forecasts csv>
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "step0"))
import report as R

R.ORDER = ["sn4c", "app", "glm_main", "glm_k3", "glm_rule_a", "glm_rule_b", "glm_em0", "glm_em5", "glm_lam05",
           "glm_lam8", "glm_narrow", "glm_wide", "glm_onegroup", "oracle"]
R.LABEL.update({
    "glm_main": "G1' + EM, K=0 (da chon)",
    "glm_k3": "... K=3 (co mua nam)",
    "glm_rule_a": "... quy tac >= 2 lan (a)",
    "glm_rule_b": "... co he so gop (b)",
    "glm_em0": "... khong EM",
    "glm_em5": "... EM 5 vong",
    "glm_lam05": "... lambda 0,5",
    "glm_lam8": "... lambda 8",
    "glm_narrow": "... cua so le hep",
    "glm_wide": "... cua so le rong",
    "glm_onegroup": "... khong biet chieu tuyen",
})

if __name__ == "__main__":
    sys.argv = [sys.argv[0], sys.argv[1], "demand"]
    R.main()
