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

Example wrapper to loop over multiple DRT scenarios sharing one baseline:

```bash
BASELINE_FILE=/path/to/baseline/trips_all_activities_inside.csv
for drt_dir in /path/to/outputs/DRT_*/; do
    python reassign_rejected_trips.py \
        --baseline "$BASELINE_FILE" \
        --drt      "$drt_dir/trips_all_activities_inside.csv"
done
```

Sequential runtime: ~30-60 s per 287 MB file pair on a modern laptop
with pyarrow installed.
