# DRT Substitution Analysis

Classifies each scenario trip that contains DRT against a baseline trip
of the same person, to identify whether the DRT trip is **new** or whether
it **replaced a trip with another mode**.

## Files

- `compare_drt_substitution.py` - the comparison script (single pair).
- `run_compare_drt_substitution.sh` - bash launcher that activates the
  Python environment and iterates over `pairs.txt`. Conditional on the
  current host (laptop / cluster).
- `pairs.txt` - one comparison per line, two paths separated by a single
  comma: `<baseline_csv>,<scenario_csv>`. Empty lines and `#` comments
  are ignored.

## Output

For each pair, one CSV is produced in the configured output directory:

    mode_changes_<short_baseline>_<short_scenario>.csv

where `<short_X>` is the file stem with `_trips_all_activities_inside_sim`
removed.

Each row corresponds to one scenario trip that contains DRT (standalone
or as a leg in a multimodal PT+DRT-feeder trip), with the following key
columns:

| column | meaning |
|---|---|
| `person`, `trip_number` | match key |
| `drt_subtype` | `standalone` or `feeder_pt` |
| `classification` | `new_trip`, `already_drt`, `already_drt_leg`, or `substituted` |
| `substituted_from_mode` | for `substituted` rows, the baseline `main_mode` |
| `main_mode_scenario`, `modes_scenario` | scenario trip details |
| `main_mode_baseline`, `modes_baseline` | baseline trip details |
| (`distance`, `travel_time`, `dep_time`, `*_activity_type`) | scenario + baseline |

## Classification logic

A scenario trip is "DRT-containing" if `main_mode == 'drt'` or `modes`
contains the `drt` token (covers `drt`, `drt_access`, `drt_egress`).

`drt_subtype`:
- `feeder_pt` if the trip also has a `pt` token in `modes` (or
  `main_mode == 'pt'`).
- `standalone` otherwise.

Match by `(person, trip_number)`. The classification of the matched
baseline trip:

- `new_trip` - no baseline trip with that `(person, trip_number)`.
- `already_drt` - baseline `main_mode == 'drt'`.
- `already_drt_leg` - baseline had a `drt`/`drt_access` leg already.
- `substituted` - baseline used a different mode; the original mode is
  reported in `substituted_from_mode`.

## Usage

### Single pair (Python directly)

```
python compare_drt_substitution.py \
    --baseline /path/to/baseline_..._trips_all_activities_inside_sim.csv \
    --scenario /path/to/scenario_..._trips_all_activities_inside_sim.csv \
    --output-dir /path/to/output_folder
```

### Many pairs (bash launcher)

1. Edit `pairs.txt`, one `<baseline>,<scenario>` per line.
2. From Git Bash on Windows or bash on Linux:

```
bash run_compare_drt_substitution.sh
```

The launcher activates the local Python environment based on
`uname` + `whoami`. To add a new host, extend the conditional block at
the top of the script.

## Requirements

- Python 3.8+
- pandas
- numpy
- pyarrow (optional, faster CSV reads)

## Notes

- The script is standalone: no dependency on `config.ini`, shapefiles, or
  the rest of the analysis pipeline.
- Matching is strict on `(person, trip_number)`. If the activity chain of
  a person changes between scenarios, the trip_number index can shift; in
  that case some scenario DRT trips may be classified as `new_trip` even
  if a similar trip exists in baseline. To estimate the impact, check the
  fraction of `new_trip` rows in the summary - if it is large, an
  activity-based fallback match may be warranted.
- The output filename strips `_trips_all_activities_inside_sim` from both
  input names. Any suffix after that marker (e.g. `_fx_B42`) is preserved,
  which keeps reassigned vs raw outputs distinguishable in the same
  output directory.
