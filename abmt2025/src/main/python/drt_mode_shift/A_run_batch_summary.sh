#!/bin/bash
#
# Batch wrapper for A_batch_summary.py. Runs the Logic A aggregation
# twice: once on the *_fixed scenario folder, once on the raw scenario
# folder. Each run writes a single summary CSV (3 metrics x N sims) into
# a configured output base directory.

set -e

OS_TYPE=$(uname)
USER_NAME=$(whoami)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_SCRIPT="$SCRIPT_DIR/A_batch_summary.py"

# ---------- conditional environment + paths ----------

if [[ ( "$OS_TYPE" == "MINGW"* || "$OS_TYPE" == "CYGWIN"* || "$OS_TYPE" == "MSYS"* ) && "$USER_NAME" == "corra" ]]; then
    ENV_ACTIVATE="/c/Users/corra/Documents/1_GitHub/PythonEnvironments/ThurgauAnalysisEnv/Scripts/activate"
    BASELINE_FILE="/c/Users/corra/Desktop/Sims_Problema/DRT_00_ShapeFile_00_drt_1_8_Baseline_Final_PhD_trips_all_activities_inside_sim.csv"
    INPUT_DIR_FX="/c/Users/corra/OneDrive - ZHAW/000_Paper2/2024_Paper2_Data/MATSim_Thurgau/2_trips_all_activities_for_Comparison_fixed"
    INPUT_DIR_RAW="/c/Users/corra/OneDrive - ZHAW/000_Paper2/2024_Paper2_Data/MATSim_Thurgau/2_trips_all_activities_for_Comparison"
    OUTPUT_BASE="/c/Users/corra/Desktop/Sims_Problema"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    ENV_ACTIVATE="$HOME/PythonEnvironments/ThurgauAnalysisEnv/bin/activate"
    BASELINE_FILE="$HOME/DATA_ABM/Sims_Problema/DRT_00_ShapeFile_00_drt_1_8_Baseline_Final_PhD_trips_all_activities_inside_sim.csv"
    INPUT_DIR_FX="$HOME/DATA_ABM/2_trips_all_activities_for_Comparison_fixed"
    INPUT_DIR_RAW="$HOME/DATA_ABM/2_trips_all_activities_for_Comparison"
    OUTPUT_BASE="$HOME/DATA_ABM/Sims_Problema"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    ENV_ACTIVATE="$HOME/PythonEnvironments/ThurgauAnalysisEnv/bin/activate"
    BASELINE_FILE="$HOME/data/Sims_Problema/DRT_00_ShapeFile_00_drt_1_8_Baseline_Final_PhD_trips_all_activities_inside_sim.csv"
    INPUT_DIR_FX="$HOME/data/2_trips_all_activities_for_Comparison_fixed"
    INPUT_DIR_RAW="$HOME/data/2_trips_all_activities_for_Comparison"
    OUTPUT_BASE="$HOME/data/Sims_Problema"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "gsangiovanni" ]]; then
    ENV_ACTIVATE="$HOME/PythonEnvironments/ThurgauAnalysisEnv/bin/activate"
    BASELINE_FILE="$HOME/Rado/Sims_Problema/DRT_00_ShapeFile_00_drt_1_8_Baseline_Final_PhD_trips_all_activities_inside_sim.csv"
    INPUT_DIR_FX="$HOME/Rado/2_trips_all_activities_for_Comparison_fixed"
    INPUT_DIR_RAW="$HOME/Rado/2_trips_all_activities_for_Comparison"
    OUTPUT_BASE="$HOME/Rado/Sims_Problema"
else
    echo "ERROR: unsupported system - OS=$OS_TYPE USER=$USER_NAME"
    echo "       extend the conditional block in $0."
    exit 1
fi

OUTPUT_CSV_FX="$OUTPUT_BASE/mode_shift_fx_summary.csv"
OUTPUT_CSV_RAW="$OUTPUT_BASE/mode_shift_summary.csv"

echo "OS:               $OS_TYPE"
echo "User:             $USER_NAME"
echo "Env:              $ENV_ACTIVATE"
echo "Baseline:         $BASELINE_FILE"
echo "Input dir (fx):   $INPUT_DIR_FX"
echo "Input dir (raw):  $INPUT_DIR_RAW"
echo "Output (fx):      $OUTPUT_CSV_FX"
echo "Output (raw):     $OUTPUT_CSV_RAW"
echo "Python:           $PYTHON_SCRIPT"
echo

if [ ! -f "$BASELINE_FILE" ]; then
    echo "ERROR: baseline file not found: $BASELINE_FILE"
    exit 2
fi

mkdir -p "$OUTPUT_BASE"

if [ -f "$ENV_ACTIVATE" ]; then
    # shellcheck disable=SC1090
    source "$ENV_ACTIVATE"
    echo "Activated: $(which python)"
else
    echo "WARNING: activation file not found at $ENV_ACTIVATE"
    echo "         falling back to system Python: $(which python || which python3)"
fi
echo

PY_BIN="$(command -v python || command -v python3)"
if [ -z "$PY_BIN" ]; then
    echo "ERROR: no python interpreter on PATH"
    exit 3
fi

FAILED=0

if [ -d "$INPUT_DIR_FX" ]; then
    echo "==========================================="
    echo "Batch 1/2: _fixed scenarios"
    echo "==========================================="
    if "$PY_BIN" "$PYTHON_SCRIPT" \
        --baseline   "$BASELINE_FILE" \
        --input-dir  "$INPUT_DIR_FX" \
        --output-csv "$OUTPUT_CSV_FX"; then
        echo "  -> OK"
    else
        echo "  -> FAILED"
        FAILED=$((FAILED + 1))
    fi
    echo
else
    echo "SKIP: input dir not found: $INPUT_DIR_FX"
    echo
fi

if [ -d "$INPUT_DIR_RAW" ]; then
    echo "==========================================="
    echo "Batch 2/2: raw scenarios"
    echo "==========================================="
    if "$PY_BIN" "$PYTHON_SCRIPT" \
        --baseline   "$BASELINE_FILE" \
        --input-dir  "$INPUT_DIR_RAW" \
        --output-csv "$OUTPUT_CSV_RAW"; then
        echo "  -> OK"
    else
        echo "  -> FAILED"
        FAILED=$((FAILED + 1))
    fi
    echo
else
    echo "SKIP: input dir not found: $INPUT_DIR_RAW"
    echo
fi

echo "==========================================="
echo "Done. Failures: $FAILED"
echo "Summaries in: $OUTPUT_BASE"
