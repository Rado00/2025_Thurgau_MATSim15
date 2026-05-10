#!/bin/bash
#
# Batch wrapper for reassign_rejected_trips.py.
#
# For each *trips_all_activities_inside*.csv file in INPUT_DIR, runs the
# reassignment script with the same BASELINE_FILE and writes the *_fx.csv
# (and per-run report) into OUTPUT_DIR. The baseline file itself is
# skipped if it sits inside INPUT_DIR.
#
# Configure paths in the conditional block below per host.

set -e

OS_TYPE=$(uname)
USER_NAME=$(whoami)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_SCRIPT="$SCRIPT_DIR/reassign_rejected_trips.py"

# ---------- conditional environment + paths ----------

if [[ ( "$OS_TYPE" == "MINGW"* || "$OS_TYPE" == "CYGWIN"* || "$OS_TYPE" == "MSYS"* ) && "$USER_NAME" == "corra" ]]; then
    ENV_ACTIVATE="/c/Users/corra/Documents/1_GitHub/PythonEnvironments/ThurgauAnalysisEnv/Scripts/activate"
    BASELINE_FILE="/c/Users/corra/OneDrive - ZHAW/000_Paper2/2024_Paper2_Data/MATSim_Thurgau/2_trips_all_activities_for_Comparison/DRT_00_ShapeFile_00_drt_1_8_Baseline_Final_PhD_trips_all_activities_inside_sim.csv"
    INPUT_DIR="/c/Users/corra/OneDrive - ZHAW/000_Paper2/2024_Paper2_Data/MATSim_Thurgau/2_trips_all_activities_for_Comparison"
    OUTPUT_DIR="/c/Users/corra/OneDrive - ZHAW/000_Paper2/2024_Paper2_Data/MATSim_Thurgau/2_trips_all_activities_for_Comparison_fixed"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    ENV_ACTIVATE="$HOME/PythonEnvironments/ThurgauAnalysisEnv/bin/activate"
    BASELINE_FILE="$HOME/DATA_ABM/2_trips_all_activities_for_Comparison/DRT_00_ShapeFile_00_drt_1_8_Baseline_Final_PhD_trips_all_activities_inside_sim.csv"
    INPUT_DIR="$HOME/DATA_ABM/2_trips_all_activities_for_Comparison"
    OUTPUT_DIR="$HOME/DATA_ABM/2_trips_all_activities_for_Comparison_fixed"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    ENV_ACTIVATE="$HOME/PythonEnvironments/ThurgauAnalysisEnv/bin/activate"
    BASELINE_FILE="$HOME/data/2_trips_all_activities_for_Comparison/DRT_00_ShapeFile_00_drt_1_8_Baseline_Final_PhD_trips_all_activities_inside_sim.csv"
    INPUT_DIR="$HOME/data/2_trips_all_activities_for_Comparison"
    OUTPUT_DIR="$HOME/data/2_trips_all_activities_for_Comparison_fixed"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "gsangiovanni" ]]; then
    ENV_ACTIVATE="$HOME/PythonEnvironments/ThurgauAnalysisEnv/bin/activate"
    BASELINE_FILE="$HOME/Rado/2_trips_all_activities_for_Comparison/DRT_00_ShapeFile_00_drt_1_8_Baseline_Final_PhD_trips_all_activities_inside_sim.csv"
    INPUT_DIR="$HOME/Rado/2_trips_all_activities_for_Comparison"
    OUTPUT_DIR="$HOME/Rado/2_trips_all_activities_for_Comparison_fixed"
else
    echo "ERROR: unsupported system - OS=$OS_TYPE USER=$USER_NAME"
    echo "       extend the conditional block in $0."
    exit 1
fi

echo "OS:           $OS_TYPE"
echo "User:         $USER_NAME"
echo "Env:          $ENV_ACTIVATE"
echo "Baseline:     $BASELINE_FILE"
echo "Input dir:    $INPUT_DIR"
echo "Output dir:   $OUTPUT_DIR"
echo "Python:       $PYTHON_SCRIPT"
echo

if [ ! -f "$BASELINE_FILE" ]; then
    echo "ERROR: baseline file not found: $BASELINE_FILE"
    exit 2
fi
if [ ! -d "$INPUT_DIR" ]; then
    echo "ERROR: input directory not found: $INPUT_DIR"
    exit 2
fi
mkdir -p "$OUTPUT_DIR"

# ---------- activate environment ----------

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

BASELINE_REAL="$(realpath "$BASELINE_FILE" 2>/dev/null || echo "$BASELINE_FILE")"

# ---------- iterate over DRT files ----------

PROCESSED=0
SKIPPED=0
FAILED=0

shopt -s nullglob

for drt_file in "$INPUT_DIR"/*trips_all_activities_inside*.csv; do
    drt_real="$(realpath "$drt_file" 2>/dev/null || echo "$drt_file")"

    # skip the baseline if it lives inside INPUT_DIR
    if [ "$drt_real" = "$BASELINE_REAL" ]; then
        echo "SKIP (baseline): $drt_file"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    # skip files already produced by this script (defensive)
    case "$(basename "$drt_file")" in
        *_fx.csv|*_fx.csv.gz) echo "SKIP (already _fx): $drt_file"; SKIPPED=$((SKIPPED + 1)); continue ;;
    esac

    base_name="$(basename "$drt_file")"
    if [[ "$base_name" == *.csv.gz ]]; then
        stem="${base_name%.csv.gz}"
        out_path="$OUTPUT_DIR/${stem}_fx.csv.gz"
    else
        stem="${base_name%.csv}"
        out_path="$OUTPUT_DIR/${stem}_fx.csv"
    fi
    report_path="$OUTPUT_DIR/${stem}_fx_report.txt"

    PROCESSED=$((PROCESSED + 1))
    echo "==========================================="
    echo "[$PROCESSED] $base_name"
    echo "  -> $out_path"
    echo "==========================================="

    if "$PY_BIN" "$PYTHON_SCRIPT" \
        --baseline "$BASELINE_FILE" \
        --drt      "$drt_file" \
        --output   "$out_path" \
        --report   "$report_path"; then
        echo "  -> OK"
    else
        echo "  -> FAILED (continuing)"
        FAILED=$((FAILED + 1))
    fi
    echo
done

shopt -u nullglob

echo "==========================================="
echo "Done: $PROCESSED processed, $SKIPPED skipped, $FAILED failure(s)."
echo "Outputs in: $OUTPUT_DIR"
