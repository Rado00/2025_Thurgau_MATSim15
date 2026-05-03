#!/usr/bin/env python3
"""Logic C - Person-day aggregate comparison for DRT adopters.

Identify the set of "DRT users" = persons who use DRT at least once in the
scenario (either as standalone or as a leg in a multimodal trip). For that
subset of persons, compare the modal split (counts and total distance) of
ALL their daily trips between baseline and scenario.

Rationale: matching trip-by-trip (Logic A or B) only captures direct
substitutions. A person might keep their car commute but cancel a separate
shopping trip thanks to DRT availability, or shift the order of activities.
Aggregating at the person-day level captures these indirect behavioural
shifts and (unlike Logic A) is guaranteed to surface non-zero deltas on
the modes that DRT users used to take.

Output (per pair):

    persona_user_<short_baseline>_<short_scenario>.csv
        Aggregate table: rows = main_mode, columns = baseline_count,
        scenario_count, delta_count, baseline_distance_km,
        scenario_distance_km, delta_distance_km, baseline_share_count,
        scenario_share_count, delta_share_count.

    persona_user_<short_baseline>_<short_scenario>_per_person.csv
        For each DRT-user person, baseline modal counts and scenario modal
        counts side by side. Useful for further analysis.

Definition of "DRT user": person whose scenario file has at least one row
where main_mode == 'drt' OR modes contains the 'drt' token.
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
    "distance", "travel_time", "dep_time",
]
REQUIRED_COLUMNS = {"person", "main_mode", "modes"}


def get_short_name(path: Path) -> str:
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


def effective_mode(df: pd.DataFrame) -> pd.Series:
    """Return one mode label per trip:
       - 'drt_standalone' if the trip is DRT-only
       - 'pt_with_drt'    if the trip is PT + drt access/egress
       - 'pt_no_drt'      if PT without drt
       - main_mode otherwise (car, bike, walk, ...)
    Useful to keep PT trips that gain a DRT-feeder distinguishable from
    pure-PT trips.
    """
    drt_mask = has_drt(df)
    pt_mask = has_pt(df)

    out = df["main_mode"].astype(str).copy()
    out = out.where(~(drt_mask & ~pt_mask), "drt_standalone")
    out = out.where(~(drt_mask & pt_mask), "pt_with_drt")
    out = out.where(~(pt_mask & ~drt_mask), "pt_no_drt")
    return out


def aggregate(
    baseline_df: pd.DataFrame,
    scenario_df: pd.DataFrame,
    drt_users: set,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Return (aggregate_df, per_person_df) for the DRT-user subset."""

    b = baseline_df.loc[baseline_df["person"].isin(drt_users)].copy()
    s = scenario_df.loc[scenario_df["person"].isin(drt_users)].copy()

    b["mode_eff"] = effective_mode(b)
    s["mode_eff"] = effective_mode(s)

    if "distance" in b.columns:
        b["distance"] = pd.to_numeric(b["distance"], errors="coerce").fillna(0.0)
    else:
        b["distance"] = 0.0
    if "distance" in s.columns:
        s["distance"] = pd.to_numeric(s["distance"], errors="coerce").fillna(0.0)
    else:
        s["distance"] = 0.0

    def agg_modes(df: pd.DataFrame, suffix: str) -> pd.DataFrame:
        g = df.groupby("mode_eff").agg(
            count=("person", "size"),
            distance=("distance", "sum"),
        )
        g.columns = [f"{suffix}_{c}" for c in g.columns]
        return g

    b_agg = agg_modes(b, "baseline")
    s_agg = agg_modes(s, "scenario")
    table = b_agg.join(s_agg, how="outer").fillna(0)

    for col in [c for c in table.columns if c.endswith("_count")]:
        table[col] = table[col].astype("int64")

    table["delta_count"] = table["scenario_count"] - table["baseline_count"]
    table["baseline_distance_km"] = table["baseline_distance"] / 1000.0
    table["scenario_distance_km"] = table["scenario_distance"] / 1000.0
    table["delta_distance_km"] = table["scenario_distance_km"] - table["baseline_distance_km"]

    total_b = table["baseline_count"].sum()
    total_s = table["scenario_count"].sum()
    table["baseline_share_count"] = (
        table["baseline_count"] / total_b * 100.0 if total_b else 0.0
    )
    table["scenario_share_count"] = (
        table["scenario_count"] / total_s * 100.0 if total_s else 0.0
    )
    table["delta_share_count"] = table["scenario_share_count"] - table["baseline_share_count"]

    cols_order = [
        "baseline_count", "scenario_count", "delta_count",
        "baseline_distance_km", "scenario_distance_km", "delta_distance_km",
        "baseline_share_count", "scenario_share_count", "delta_share_count",
    ]
    table = table[cols_order].sort_index()
    table.index.name = "mode"
    table = table.reset_index()

    # Per-person view: counts per mode, baseline vs scenario, wide format
    bp = b.groupby(["person", "mode_eff"]).size().unstack(fill_value=0).add_prefix("baseline_")
    sp = s.groupby(["person", "mode_eff"]).size().unstack(fill_value=0).add_prefix("scenario_")
    per_person = bp.join(sp, how="outer").fillna(0).astype("int64").reset_index()

    return table, per_person


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
    out_path = args.output_dir / f"persona_user_{short_b}_{short_s}.csv"
    out_pp_path = args.output_dir / f"persona_user_{short_b}_{short_s}_per_person.csv"

    print(f"[1/3] Loading baseline: {args.baseline}")
    t0 = time.time()
    baseline_df = load_trips(args.baseline)
    print(f"      {len(baseline_df)} rows in {time.time() - t0:.1f}s")

    print(f"[2/3] Loading scenario: {args.scenario}")
    t0 = time.time()
    scenario_df = load_trips(args.scenario)
    print(f"      {len(scenario_df)} rows in {time.time() - t0:.1f}s")

    print("[3/3] Identifying DRT users + aggregating...")
    t0 = time.time()
    drt_users = set(scenario_df.loc[has_drt(scenario_df), "person"].unique())
    print(f"      DRT users in scenario: {len(drt_users)}")
    table, per_person = aggregate(baseline_df, scenario_df, drt_users)
    print(f"      done in {time.time() - t0:.1f}s")
    print()

    print(f"Writing: {out_path}")
    table.to_csv(out_path, index=False)
    print(f"Writing: {out_pp_path}")
    per_person.to_csv(out_pp_path, index=False)
    print()

    print("Aggregate (DRT users only, ALL their daily trips):")
    print(table.to_string(index=False))


if __name__ == "__main__":
    main()
