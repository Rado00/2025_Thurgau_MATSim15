#!/bin/bash
#
# Wrapper that activates the right Python environment and runs
# C_compare_drt_user_persona.py on every (baseline, scenario) pair listed
# in pairs.txt (located next to this script). One pair per line, paths
# separated by a single comma. Empty lines and lines starting with '#' are
# ignored.
#
# Logic C: aggregate per-mode counts and distance for the subset of
# persons who use DRT at least once in the scenario, baseline vs scenario.
#
# Output directory is set conditionally based on host (laptop / cluster).

set -e

OS_TYPE=$(uname)
USER_NAME=$(whoami)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAIRS_FILE="$SCRIPT_DIR/pairs.txt"
PYTHON_SCRIPT="$SCRIPT_DIR/C_compare_drt_user_persona.py"

# ---------- conditional environment activation + output directory ----------

if [[ ( "$OS_TYPE" == "MINGW"* || "$OS_TYPE" == "CYGWIN"* || "$OS_TYPE" == "MSYS"* ) && "$USER_NAME" == "corra" ]]; then
    ENV_ACTIVATE="/c/Users/corra/Documents/1_GitHub/PythonEnvironments/ThurgauAnalysisEnv/Scripts/activate"
    OUTPUT_DIR="/c/Users/corra/Desktop/Sims_Problema/RisultatiConfronto"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    ENV_ACTIVATE="$HOME/PythonEnvironments/ThurgauAnalysisEnv/bin/activate"
    OUTPUT_DIR="$HOME/DATA_ABM/Sims_Problema/RisultatiConfronto"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    ENV_ACTIVATE="$HOME/PythonEnvironments/ThurgauAnalysisEnv/bin/activate"
    OUTPUT_DIR="$HOME/data/Sims_Problema/RisultatiConfronto"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "gsangiovanni" ]]; then
    ENV_ACTIVATE="$HOME/PythonEnvironments/ThurgauAnalysisEnv/bin/activate"
    OUTPUT_DIR="$HOME/Rado/Sims_Problema/RisultatiConfronto"
else
    echo "ERROR: unsupported system - OS=$OS_TYPE USER=$USER_NAME"
    echo "       extend the conditional block in $0 to add this host."
    exit 1
fi

echo "OS:           $OS_TYPE"
echo "User:         $USER_NAME"
echo "Env:          $ENV_ACTIVATE"
echo "Output dir:   $OUTPUT_DIR"
echo "Python:       $PYTHON_SCRIPT"
echo "Pairs file:   $PAIRS_FILE"
echo

mkdir -p "$OUTPUT_DIR"

if [ -f "$ENV_ACTIVATE" ]; then
    # shellcheck disable=SC1090
    source "$ENV_ACTIVATE"
    echo "Activated: $(which python)"
else
    echo "WARNING: activation file not found at $ENV_ACTIVATE"
    echo "         falling back to system Python: $(which python || which python3)"
fi
echo

if [ ! -f "$PAIRS_FILE" ]; then
    echo "ERROR: pairs file not found: $PAIRS_FILE"
    exit 2
fi

PY_BIN="$(command -v python || command -v python3)"
if [ -z "$PY_BIN" ]; then
    echo "ERROR: no python interpreter on PATH"
    exit 3
fi

LINE_NUM=0
PAIR_NUM=0
FAILED=0

while IFS= read -r line || [ -n "$line" ]; do
    LINE_NUM=$((LINE_NUM + 1))

    line="${line%$'\r'}"
    trimmed="$(echo "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"

    if [[ -z "$trimmed" || "$trimmed" =~ ^# ]]; then
        continue
    fi

    BASELINE="${trimmed%%,*}"
    SCENARIO="${trimmed#*,}"
    BASELINE="$(echo "$BASELINE" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
    SCENARIO="$(echo "$SCENARIO" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"

    if [[ -z "$BASELINE" || -z "$SCENARIO" || "$BASELINE" == "$SCENARIO" ]]; then
        echo "WARNING: line $LINE_NUM malformed, skipping: $line"
        continue
    fi

    PAIR_NUM=$((PAIR_NUM + 1))
    echo "==========================================="
    echo "Pair $PAIR_NUM (line $LINE_NUM)"
    echo "  Baseline: $BASELINE"
    echo "  Scenario: $SCENARIO"
    echo "==========================================="

    if "$PY_BIN" "$PYTHON_SCRIPT" \
        --baseline "$BASELINE" \
        --scenario "$SCENARIO" \
        --output-dir "$OUTPUT_DIR"; then
        echo "  -> OK"
    else
        echo "  -> FAILED (continuing with next pair)"
        FAILED=$((FAILED + 1))
    fi
    echo

done < "$PAIRS_FILE"

echo "==========================================="
echo "Done: $PAIR_NUM pair(s) processed, $FAILED failure(s)."
echo "Outputs in: $OUTPUT_DIR"
