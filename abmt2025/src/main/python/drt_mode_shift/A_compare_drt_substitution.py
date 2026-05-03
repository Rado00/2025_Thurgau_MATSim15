#!/usr/bin/env python3
"""Logic A - Classify DRT-containing scenario trips against a baseline.

Per-trip matching by (person, trip_number). For each trip in the scenario
CSV that contains DRT (standalone or as a leg in a multimodal PT+DRT-feeder
trip), find the corresponding trip of the same person in the baseline CSV
and classify it as:

  - new_trip:        no matching trip in baseline (person had no trip with
                     this trip_number)
  - already_drt:     baseline trip was already DRT (main_mode == 'drt')
  - already_drt_leg: baseline trip already had a 'drt' leg in modes
  - substituted:     baseline trip used a different mode; the original
                     mode is reported in column substituted_from_mode

Matching is by (person, trip_number).

Trips in the scenario are classified by drt_subtype:
  - standalone : main_mode == drt and modes does NOT contain a 'pt' token
  - feeder_pt  : modes contains both 'drt' (or drt_access/drt_egress) and 'pt'

Usage:
    python compare_drt_substitution.py \\
        --baseline /path/to/baseline_..._trips_all_activities_inside_sim.csv \\
        --scenario /path/to/scenario_..._trips_all_activities_inside_sim.csv \\
        --output-dir /path/to/output_folder

Output:
    mode_changes_<short_baseline>_<short_scenario>.csv
    where <short_X> is the file stem with '_trips_all_activities_inside_sim'
    removed.
"""

import argparse
import sys
import time
from pathlib import Path

import numpy as np
import pandas as pd


MARKER = "_trips_all_activities_inside_sim"
DRT_TOKEN_REGEX = r"drt"
PT_TOKEN_REGEX = r"(?:^|[-,])pt(?:$|[-,])"

WANTED_COLUMNS = [
    "person", "trip_number", "main_mode", "modes",
    "start_activity_type", "end_activity_type",
    "distance", "travel_time", "dep_time",
]
REQUIRED_COLUMNS = {"person", "trip_number", "main_mode", "modes"}


def get_short_name(path: Path) -> str:
    """Return file stem with the marker phrase stripped out."""
    return path.stem.replace(MARKER, "")


def load_trips(path: Path) -> pd.DataFrame:
    available_cols = pd.read_csv(path, nrows=0).columns.tolist()
    use = [c for c in WANTED_COLUMNS if c in available_cols]
    missing = REQUIRED_COLUMNS - set(use)
    if missing:
        raise ValueError(f"{path} is missing required columns: {missing}")
    try:
        df = pd.read_csv(path, usecols=use, engine="pyarrow")
    except Exception:
        df = pd.read_csv(path, usecols=use, engine="c")
    return df


def has_drt(df: pd.DataFrame) -> pd.Series:
    main = df["main_mode"].astype(str) == "drt"
    in_modes = df["modes"].fillna("").astype(str).str.contains(
        DRT_TOKEN_REGEX, regex=True, na=False
    )
    return main | in_modes


def has_pt(df: pd.DataFrame) -> pd.Series:
    main = df["main_mode"].astype(str) == "pt"
    in_modes = df["modes"].fillna("").astype(str).str.contains(
        PT_TOKEN_REGEX, regex=True, na=False
    )
    return main | in_modes


def classify(baseline_df: pd.DataFrame, scenario_df: pd.DataFrame) -> pd.DataFrame:
    drt_mask = has_drt(scenario_df)
    s_drt = scenario_df.loc[drt_mask].copy()
    s_drt["drt_subtype"] = np.where(has_pt(s_drt), "feeder_pt", "standalone")

    rename_scen = {
        c: f"{c}_scenario" for c in s_drt.columns
        if c not in ("person", "trip_number", "drt_subtype")
    }
    s_drt = s_drt.rename(columns=rename_scen)

    b_use = [c for c in WANTED_COLUMNS if c in baseline_df.columns]
    b_subset = baseline_df[b_use].copy()
    rename_base = {
        c: f"{c}_baseline" for c in b_use if c not in ("person", "trip_number")
    }
    b_subset = b_subset.rename(columns=rename_base)

    joined = s_drt.merge(
        b_subset, on=["person", "trip_number"], how="left", indicator=True
    )

    main_baseline = joined["main_mode_baseline"].astype("object")
    modes_baseline = joined["modes_baseline"].fillna("").astype(str)

    is_new = joined["_merge"].astype(str) == "left_only"
    is_already_drt = main_baseline.astype(str) == "drt"
    is_already_drt_leg = (~is_new) & (~is_already_drt) & modes_baseline.str.contains(
        DRT_TOKEN_REGEX, regex=True, na=False
    )
    is_substituted = ~(is_new | is_already_drt | is_already_drt_leg)

    classification = np.select(
        [is_new, is_already_drt, is_already_drt_leg, is_substituted],
        ["new_trip", "already_drt", "already_drt_leg", "substituted"],
        default="unknown",
    )
    joined["classification"] = classification
    joined["substituted_from_mode"] = np.where(
        is_substituted, main_baseline.astype(str), ""
    )

    joined = joined.drop(columns=["_merge"])
    cols_order = ["person", "trip_number", "drt_subtype", "classification",
                  "substituted_from_mode"]
    cols_order += [c for c in joined.columns if c not in cols_order]
    joined = joined[cols_order]
    return joined


def print_summary(result: pd.DataFrame) -> None:
    total = len(result)
    print(f"Total DRT-containing scenario trips: {total}")
    print()
    print("By drt_subtype x classification:")
    pivot = (
        result.groupby(["drt_subtype", "classification"])
        .size()
        .reset_index(name="count")
        .sort_values(["drt_subtype", "count"], ascending=[True, False])
    )
    print(pivot.to_string(index=False))
    print()

    sub = result.loc[result["classification"] == "substituted"]
    if len(sub) > 0:
        print("Substituted trips by drt_subtype x baseline main_mode:")
        sub_pivot = (
            sub.groupby(["drt_subtype", "substituted_from_mode"])
            .size()
            .reset_index(name="count")
            .sort_values(["drt_subtype", "count"], ascending=[True, False])
        )
        print(sub_pivot.to_string(index=False))
    else:
        print("No substituted trips found.")


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--baseline", required=True, type=Path)
    p.add_argument("--scenario", required=True, type=Path)
    p.add_argument("--output-dir", required=True, type=Path)
    args = p.parse_args()

    if not args.baseline.is_file():
        print(f"ERROR: baseline not found: {args.baseline}", file=sys.stderr)
        sys.exit(2)
    if not args.scenario.is_file():
        print(f"ERROR: scenario not found: {args.scenario}", file=sys.stderr)
        sys.exit(2)

    args.output_dir.mkdir(parents=True, exist_ok=True)

    short_b = get_short_name(args.baseline)
    short_s = get_short_name(args.scenario)
    out_path = args.output_dir / f"mode_changes_{short_b}_{short_s}.csv"

    print(f"[1/3] Loading baseline: {args.baseline}")
    t0 = time.time()
    baseline_df = load_trips(args.baseline)
    print(f"      {len(baseline_df)} rows in {time.time() - t0:.1f}s")

    print(f"[2/3] Loading scenario: {args.scenario}")
    t0 = time.time()
    scenario_df = load_trips(args.scenario)
    print(f"      {len(scenario_df)} rows in {time.time() - t0:.1f}s")

    print("[3/3] Classifying DRT trips...")
    t0 = time.time()
    result = classify(baseline_df, scenario_df)
    print(f"      done in {time.time() - t0:.1f}s")
    print()

    print(f"Writing: {out_path}")
    result.to_csv(out_path, index=False)
    print()

    print_summary(result)


if __name__ == "__main__":
    main()
