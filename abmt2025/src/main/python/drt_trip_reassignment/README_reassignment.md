# DRT Rejected Trip Reassignment

Post-hoc tool to patch `trips_all_activities_inside.csv` produced by the
Thurgau analysis pipeline, recovering trips lost because of DRT request
rejection ("stuck agents") by copying the corresponding rows from a
baseline run.

## Logic

For each `person`:

1. If DRT has **>=** the number of trips found in baseline, keep DRT rows
   unchanged.
2. If DRT has **strictly fewer** trips than baseline, take all DRT rows
   and then append baseline rows whose `trip_number` is not already
   present in the DRT rows for that person.
3. If a person is present only in baseline, add all their baseline rows.
4. If a person is present only in DRT, keep their DRT rows.

Matching is strictly by `(person, trip_number)`. No coordinate, no time,
no mode comparison is performed. Added rows carry over the baseline
values for every column (distance, travel_time, coordinates, mode, ...).

## Usage

```bash
python reassign_rejected_trips.py \
    --baseline /path/to/baseline/trips_all_activities_inside.csv \
    --drt      /path/to/drt_case/trips_all_activities_inside.csv
```

Output defaults:

- Patched CSV: next to the DRT input, same name with `_fx` appended
  before the extension (e.g. `trips_all_activities_inside_fx.csv`).
- Report:      `reassignment_report.txt` next to the output CSV.

Optional flags:

- `--output <path>` override output CSV path.
- `--report <path>` override report path.
- `--engine pyarrow|c` force pandas CSV engine.

## Requirements

- Python 3.8+
- `pandas`
- `pyarrow` (optional, faster CSV loading).

## Batch over many cases

Use `run_reassign_batch.sh` (next to this README). It iterates every
`*trips_all_activities_inside*.csv` in a configured `INPUT_DIR`, runs
`reassign_rejected_trips.py` against a single `BASELINE_FILE`, and writes
the `*_fx.csv` plus a per-run `*_fx_report.txt` into a separate
`OUTPUT_DIR`. The baseline file itself is auto-skipped if it sits inside
`INPUT_DIR`, and any pre-existing `*_fx.csv` is also skipped.

Edit the host-conditional block at the top of the script to set:

- `BASELINE_FILE`
- `INPUT_DIR`
- `OUTPUT_DIR`

Run:

```bash
bash run_reassign_batch.sh
```

Sequential runtime: ~30-60 s per 287 MB file pair on a modern laptop
with pyarrow installed.



SU LAPTOP
cd C:\Users\corra\Documents\1_GitHub\2025_Thurgau_MATSim15\abmt2025\src\main\python\drt_trip_reassignment
python reassign_rejected_trips.py --baseline C:\Users\corra\Desktop\Sims_Problema\DRT_00_ShapeFile_00_drt_1_8_Baseline_Final_PhD_trips_all_activities_inside_sim.csv --drt C:\Users\corra\Desktop\Sims_Problema\DRT_25_ShapeFile_25_drt_19_8_25_5_PhD_trips_all_activities_inside_sim.csv

