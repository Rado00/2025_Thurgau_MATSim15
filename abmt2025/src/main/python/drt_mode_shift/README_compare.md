# DRT Substitution Analysis

Four alternative comparison logics that, given a baseline-trips CSV and a
DRT-scenario-trips CSV, try to identify how DRT trips substitute trips of
other modes (or appear as new trips). Each logic uses a different
matching/aggregation strategy; using more than one helps reveal the
sensitivity of the conclusions to the matching assumption.

All four read pairs from the same `pairs.txt` and write to the same
host-conditional output directory (laptop / cluster).

## Files

| Logic | Python script | Bash launcher | Matching strategy |
|---|---|---|---|
| A | `A_compare_drt_substitution.py` | `A_run_compare_drt_substitution.sh` | strict `(person, trip_number)` |
| B | `B_compare_drt_od_match.py`     | `B_run_compare_drt_od_match.sh`     | `(person, OD-key, time-bucket)`, greedy |
| C | `C_compare_drt_user_persona.py` | `C_run_compare_drt_user_persona.sh` | none - aggregate over DRT-user persons |
| D | `D_compare_drt_od_aggregate.py` | `D_run_compare_drt_od_aggregate.sh` | none - aggregate per OD-cell |

Common files:

- `pairs.txt` - one comparison per line, `<baseline_csv>,<scenario_csv>`.
  Empty lines and `#` comments are ignored. All four launchers read the
  same file.

## When to use which logic

- **A (strict)** - the cleanest comparison when activity chains are
  stable between baseline and scenario. Tends to under-count direct
  substitutions when activity chains shift (which is common in MATSim
  re-scoring).
- **B (OD-relaxed)** - relaxes Logic A by matching on origin/destination
  identity instead of trip index, while keeping person identity. Best
  attempt at recovering substitutions hidden by trip-chain re-ordering.
- **C (DRT-user persona)** - drops trip-level matching entirely. Sums
  modal counts and total distance for the subset of persons who use DRT
  at least once in the scenario, baseline vs scenario. Captures indirect
  effects (cancelled/added trips, re-ordered chains) but is descriptive,
  not causal.
- **D (OD-cell aggregate)** - drops person identity. For every OD cell
  counts trips per mode in baseline and scenario, and reports the
  per-mode delta on cells where DRT appears in the scenario. Closest to
  what aggregate modal-split tables show, but cannot tell who switched
  what.

A natural workflow: run A and B as the strict/relaxed pair; if both give
zero or near-zero car substitution, fall back to C and D as the
behavioural-shift / aggregate-flow narrative.

## Output filenames

All output goes to the host-specific `OUTPUT_DIR`. For each pair the
following files are written (with `<short_X>` = file stem with
`_trips_all_activities_inside_sim` removed):

| Logic | Files |
|---|---|
| A | `mode_changes_<short_b>_<short_s>.csv` |
| B | `od_match_<short_b>_<short_s>.csv` |
| C | `persona_user_<short_b>_<short_s>.csv` + `..._per_person.csv` |
| D | `od_aggregate_<short_b>_<short_s>.csv` + `..._summary.csv` + `..._global.csv` |

## DRT-trip definition

Common to all four: a scenario trip "contains DRT" if `main_mode == 'drt'`
or the `modes` column contains the `drt` token (covers `drt`,
`drt_access`, `drt_egress`).

`drt_subtype`:
- `feeder_pt` if the trip also has a `pt` token in `modes` (or
  `main_mode == 'pt'`).
- `standalone` otherwise.

For Logic C and D the modes are further split into:
- `drt_standalone` - DRT-only trips
- `pt_with_drt`    - PT trips that also have a DRT leg
- `pt_no_drt`      - pure PT trips
- otherwise the original `main_mode` (`car`, `bike`, `walk`, ...)

## Logic-specific notes

### Logic A
Match strictly by `(person, trip_number)`. Each scenario DRT trip is
classified as `new_trip`, `already_drt`, `already_drt_leg`, or
`substituted` (with `substituted_from_mode`).

### Logic B
OD-key precedence (whichever the input columns support):
1. `(start_link, end_link)`
2. coordinates rounded to a grid (default 500 m): `(start_x, start_y, end_x, end_y)`
3. `(start_activity_type, end_activity_type)` as fallback

Time bucket: integer hour of `dep_time` by default (set `--hour-bucket 0`
to disable). Within each `(person, OD-key, time-bucket)` group the
matching is greedy nearest-neighbour by `dep_time` so that each baseline
trip is consumed at most once.

CLI knobs: `--coord-grid 500`, `--hour-bucket 1`.

### Logic C
"DRT users" = persons with at least one DRT-containing trip in the
scenario. The aggregate table reports per-mode counts, distance (km),
and shares for those persons in baseline and scenario. The per-person
file gives the same counts in wide format for downstream analysis.

### Logic D
OD-cell key precedence as in B. Three outputs:
- per-cell wide table with baseline/scenario/delta for every mode
- summary aggregated over OD cells where DRT appears in the scenario
- global summary aggregated over all OD cells

CLI knob: `--coord-grid 500`.

## Usage

### Single pair (Python directly)

```
python A_compare_drt_substitution.py \
    --baseline /path/to/baseline_..._trips_all_activities_inside_sim.csv \
    --scenario /path/to/scenario_..._trips_all_activities_inside_sim.csv \
    --output-dir /path/to/output_folder
```

Replace `A_compare_drt_substitution.py` with `B_...`, `C_...`, or `D_...`
as needed. B and D accept `--coord-grid`; B also accepts `--hour-bucket`.

### Many pairs (bash launcher)

1. Edit `pairs.txt`, one `<baseline>,<scenario>` per line.
2. From Git Bash on Windows or bash on Linux:

```
bash A_run_compare_drt_substitution.sh
bash B_run_compare_drt_od_match.sh
bash C_run_compare_drt_user_persona.sh
bash D_run_compare_drt_od_aggregate.sh
```

The launchers all read the same `pairs.txt` and write to the same
`OUTPUT_DIR` (host-specific). The output filenames have logic-specific
prefixes so they do not collide.

To add a new host, extend the conditional block at the top of any
launcher and copy it to the others.

## Requirements

- Python 3.8+
- pandas
- numpy
- pyarrow (optional, faster CSV reads)

## Notes

- All scripts are standalone: no dependency on `config.ini`, shapefiles,
  or the rest of the analysis pipeline.
- The output filenames strip `_trips_all_activities_inside_sim` from both
  input names. Any suffix after that marker (e.g. `_fx_B42`) is preserved,
  which keeps reassigned vs raw outputs distinguishable in the same
  output directory.
- Logic B chooses the OD-key kind by intersecting what the two input
  files support, then picking the most precise. If you want to force a
  coarser key (to surface more candidate matches), drop the relevant
  columns from the input or extend the script.
