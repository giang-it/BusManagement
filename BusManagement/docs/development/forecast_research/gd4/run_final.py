# GĐ4 of the Demand Forecast redesign (decision QĐ-15, ../../forecast_discussion.md §14.1): the FINAL
# configuration — EM 5 rounds (QĐ-1), ridge 2 on the pooled holiday effects (rule (b), QĐ-3), K = 0,
# ridge 2 on the per-route deviations — backtested next to the GĐ2 main model on the GĐ2 dataset, through
# gd2/run_gd2.py unchanged. Until then the two changes had only been measured one at a time. Research only.
#
# Windows starts pool workers by re-importing this file, so only the import path is set at module level and
# everything that reads the command line stays under the __main__ guard. A top-level argv read made every
# worker die and respawn for half an hour on 2026-10-22.
#
# Usage (the step 0 venv; dataset.csv from gd2/build_dataset.py; GD2_PROCESSES sets the workers, default 12):
#   python -I run_final.py <dataset.csv> <forecasts.csv>
#   python -I ../step0/compare.py <forecasts.csv> demand 1 7 glm_final:glm_main,glm_final:sn4c,glm_final:app
#   python -I ../step0/compare.py <forecasts.csv> demand 15 35 glm_final:glm_main,glm_final:sn4c,glm_final:app
#   python -I ../step0/compare.py <forecasts.csv> demand 1 35 glm_final:glm_main <cny2024|cny2025|cny2026|holiday|normal>
import sys
from pathlib import Path

RESEARCH = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RESEARCH / "gd2"))
sys.path.insert(0, str(RESEARCH / "step0"))
import run_gd2 as R

R.VARIANTS = {"glm_main": R.MAIN, "glm_final": (5, 2.0, 0, 2.0, 0, "current", False)}

if __name__ == "__main__":
    dataset, out = sys.argv[1:3]
    sys.argv = ["run_gd2.py", dataset, out]
    R.main()
