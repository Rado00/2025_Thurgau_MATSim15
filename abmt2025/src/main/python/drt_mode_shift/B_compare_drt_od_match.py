#!/usr/bin/env python3
"""Logic B - OD-relaxed matching of DRT scenario trips against a baseline.

Same goal as Logic A (classify each DRT-containing scenario trip as
new_trip / already_drt / already_drt_leg / substituted, and report the
substituted_from_mode), but the matching key is relaxed from the strict
(person, trip_number) to (person, OD-key, time-bucket).

Rationale: between MATSim runs the activity chain of a person can be
re-ordered or grow/shrink, so trip_number=k in baseline is often a
different real-world trip than trip_number=k in scenario. This breaks
Logic A and tends to under-count direct substitutions. Logic B matches
on origin/destination identity instead.

Matching key, in order of preference (whichever set of columns the input
CSVs have):

    1. (person, start_link, end_link, dep_hour_bucket)
    2. (person, start_x_round, start_y_round, end_x_round, end_y_round,
        dep_hour_bucket)
    3. (person, start_activity_type, end_activity_type, dep_hour_bucket)

Coordinates are rounded to a configurable grid (default 500 m). The
hour bucket is the integer hour of dep_time by default (configurable;
0 means no time bucketing).

Multiple baseline trips can share the same OD-key for one person (e.g.
two home->work trips at different times). Assignment is greedy: scenario
DRT trips are processed in dep_time order, and each baseline candidate
is consumed at most once. Among multiple candidates we pick the one with
the smallest |dep_time scenario - dep_time baseline|.

Usage:
    python B_compare_drt_od_match.py \\
        --baseline /path/to/baseline_..._trips_all_activities_inside_sim.csv \\
        --scenario /path/to/scenario_..._trips_all_activities_inside_sim.csv \\
        --output-dir /path/to/output_folder \\
        [--coord-grid 500] [--hour-bucket 1]

Output:
    od_match_<short_baseline>_<short_scenario>.csv
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

OPTIONAL_COLUMNS = [
    "person", "trip_number", "main_mode", "modes",
    "start_activity_type", "end_activity_type",
    "distance", "travel_time", "dep_time",
    "start_link", "end_link",
    "start_x", "start_y", "end_x", "end_y",
]
REQUIRED_COLUMNS = {"person", "main_mode", "modes"}


def get_short_name(path: Path) -> str:
    return path.stem.replace(MARKER, "")


def load_trips(path: Path) -> pd.DataFrame:
    available_cols = pd.read_csv(path, nrows=0).columns.tolist()
    use = [c for c in OPTIONAL_COLUMNS if c in available_cols]
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


def parse_dep_seconds(series: pd.Series) -> pd.Series:
    """Convert dep_time to seconds-from-midnight as float.

    Accepts either numeric (already in seconds) or strings like "HH:MM:SS".
    Missing/unparseable values become NaN.
    """
    if series.dtype.kind in "fi":
        return series.astype(float)
    s = series.astype(str)
    parts = s.str.split(":", expand=True)
    if parts.shape[1] >= 3:
        h = pd.to_numeric(parts[0], errors="coerce")
        m = pd.to_numeric(parts[1], errors="coerce")
        sec = pd.to_numeric(parts[2], errors="coerce")
        return h * 3600.0 + m * 60.0 + sec
    return pd.to_numeric(series, errors="coerce")


def pick_od_columns(df: pd.DataFrame) -> tuple[str, list[str]]:
    """Return (key_kind, columns) given which OD columns are present."""
    cols = set(df.columns)
    if {"start_link", "end_link"}.issubset(cols):
        return "link", ["start_link", "end_link"]
    if {"start_x", "start_y", "end_x", "end_y"}.issubset(cols):
        return "coord", ["start_x", "start_y", "end_x", "end_y"]
    if {"start_activity_type", "end_activity_type"}.issubset(cols):
        return "actype", ["start_activity_type", "end_activity_type"]
    raise ValueError(
        "Cannot build an OD key: need start_link/end_link, or start_x/y "
        "+ end_x/y, or start_activity_type/end_activity_type."
    )


def build_od_key(df: pd.DataFrame, key_kind: str, coord_grid: float) -> pd.Series:
    if key_kind == "link":
        a = df["start_link"].astype(str).fillna("")
        b = df["end_link"].astype(str).fillna("")
        return a + "|" + b
    if key_kind == "coord":
        sx = (pd.to_numeric(df["start_x"], errors="coerce")
              / coord_grid).round().astype("Int64")
        sy = (pd.to_numeric(df["start_y"], errors="coerce")
              / coord_grid).round().astype("Int64")
        ex = (pd.to_numeric(df["end_x"], errors="coerce")
              / coord_grid).round().astype("Int64")
        ey = (pd.to_numeric(df["end_y"], errors="coerce")
              / coord_grid).round().astype("Int64")
        return (sx.astype(str) + "," + sy.astype(str) + "|"
                + ex.astype(str) + "," + ey.astype(str))
    # actype
    a = df["start_activity_type"].astype(str).fillna("")
    b = df["end_activity_type"].astype(str).fillna("")
    return a + "|" + b


def build_time_bucket(df: pd.DataFrame, hour_bucket: int) -> pd.Series:
    if hour_bucket <= 0 or "dep_time" not in df.columns:
        return pd.Series(0, index=df.index, dtype="int64")
    sec = parse_dep_seconds(df["dep_time"])
    bucket = (sec // (hour_bucket * 3600.0)).fillna(-1).astype("int64")
    return bucket


def greedy_match(
    s_drt: pd.DataFrame,
    b_subset: pd.DataFrame,
    s_dep_sec: pd.Series,
    b_dep_sec: pd.Series,
) -> pd.Series:
    """Return a Series indexed like s_drt giving the matched baseline index
    (or -1 if unmatched). Each baseline row is consumed at most once.

    Matching is within (person, od_key, time_bucket) groups; within a group
    we pair scenario rows to baseline rows by minimizing the sum of |dep_sec|
    differences via a greedy nearest-neighbour walk after sorting both sides
    by dep_sec. This is O(n log n) per group and good enough in practice.
    """
    matched = pd.Series(-1, index=s_drt.index, dtype="int64")

    # Group both sides by the same key
    key_cols = ["person", "od_key", "time_bucket"]
    s_grp = s_drt.groupby(key_cols, sort=False).indices
    b_grp = b_subset.groupby(key_cols, sort=False).indices

    s_dep = s_dep_sec.to_numpy()
    b_dep = b_dep_sec.to_numpy()

    for key, s_idx_arr in s_grp.items():
        b_idx_arr = b_grp.get(key)
        if b_idx_arr is None or len(b_idx_arr) == 0:
            continue

        s_idx = list(s_idx_arr)
        b_idx = list(b_idx_arr)

        # Sort each side by dep_sec (NaN goes last)
        s_idx.sort(key=lambda i: (np.isnan(s_dep[i]), s_dep[i] if not np.isnan(s_dep[i]) else 0.0))
        b_idx.sort(key=lambda i: (np.isnan(b_dep[i]), b_dep[i] if not np.isnan(b_dep[i]) else 0.0))

        b_used = [False] * len(b_idx)
        # Greedy: for each scenario trip in dep_sec order, take the nearest
        # unused baseline trip (sweep two pointers)
        j = 0
        for si in s_idx:
            # find best unused j by scanning forward; since both lists are
            # sorted, the optimal greedy choice is the closest unused to si
            best_k = -1
            best_d = float("inf")
            sd = s_dep[si]
            for k in range(j, len(b_idx)):
                if b_used[k]:
                    continue
                bd = b_dep[b_idx[k]]
                if np.isnan(sd) or np.isnan(bd):
                    d = 0.0
                else:
                    d = abs(sd - bd)
                if d < best_d:
                    best_d = d
                    best_k = k
                # As list is sorted, once bd > sd and d is already increasing,
                # we can stop. But keep simple O(n*m_per_group) for clarity.
            if best_k >= 0:
                b_used[best_k] = True
                matched.at[si] = int(b_idx[best_k])

    return matched


def classify(
    baseline_df: pd.DataFrame,
    scenario_df: pd.DataFrame,
    coord_grid: float,
    hour_bucket: int,
) -> tuple[pd.DataFrame, dict]:
    # Pick a common OD-key kind: must be supported by both files
    b_kind, _ = pick_od_columns(baseline_df)
    s_kind, _ = pick_od_columns(scenario_df)
    # Use the most precise key both can support
    precedence = {"link": 0, "coord": 1, "actype": 2}
    key_kind = b_kind if precedence[b_kind] >= precedence[s_kind] else s_kind
    # Re-check both can build it
    for df_, label in ((baseline_df, "baseline"), (scenario_df, "scenario")):
        try:
            pick_od_columns(df_)
        except ValueError as e:
            raise ValueError(f"{label}: {e}")
    # Force the chosen key on both
    info = {"key_kind": key_kind, "coord_grid": coord_grid, "hour_bucket": hour_bucket}

    # Build keys
    b = baseline_df.copy()
    s = scenario_df.copy()
    b["od_key"] = build_od_key(b, key_kind if key_kind in {"link", "coord", "actype"} else "actype",
                               coord_grid)
    s["od_key"] = build_od_key(s, key_kind if key_kind in {"link", "coord", "actype"} else "actype",
                               coord_grid)
    b["time_bucket"] = build_time_bucket(b, hour_bucket)
    s["time_bucket"] = build_time_bucket(s, hour_bucket)

    drt_mask = has_drt(s)
    s_drt = s.loc[drt_mask].copy()
    s_drt["drt_subtype"] = np.where(has_pt(s_drt), "feeder_pt", "standalone")

    s_dep_sec = parse_dep_seconds(s_drt["dep_time"]) if "dep_time" in s_drt.columns else pd.Series(
        np.nan, index=s_drt.index
    )
    b_dep_sec = parse_dep_seconds(b["dep_time"]) if "dep_time" in b.columns else pd.Series(
        np.nan, index=b.index
    )

    # Use positional indices so the greedy matcher returns row positions
    s_drt_pos = s_drt.reset_index(drop=True)
    b_pos = b.reset_index(drop=True)
    s_dep_sec_pos = s_dep_sec.reset_index(drop=True)
    b_dep_sec_pos = b_dep_sec.reset_index(drop=True)

    matched_pos = greedy_match(s_drt_pos, b_pos, s_dep_sec_pos, b_dep_sec_pos)

    # Build the result frame
    result = s_drt_pos[
        [c for c in ["person", "trip_number", "drt_subtype", "main_mode", "modes",
                     "dep_time", "distance", "travel_time",
                     "start_activity_type", "end_activity_type",
                     "od_key", "time_bucket"]
         if c in s_drt_pos.columns]
    ].copy()
    rename_s = {c: f"{c}_scenario" for c in result.columns
                if c not in ("person", "trip_number", "drt_subtype", "od_key", "time_bucket")}
    result = result.rename(columns=rename_s)

    matched_arr = matched_pos.to_numpy()
    has_match = matched_arr >= 0

    base_cols = [c for c in ["trip_number", "main_mode", "modes", "dep_time",
                             "distance", "travel_time",
                             "start_activity_type", "end_activity_type"]
                 if c in b_pos.columns]
    base_attached = pd.DataFrame(index=result.index, columns=[f"{c}_baseline" for c in base_cols])
    if has_match.any():
        idxs = matched_arr[has_match]
        slice_b = b_pos.iloc[idxs][base_cols].reset_index(drop=True)
        slice_b.columns = [f"{c}_baseline" for c in base_cols]
        base_attached.loc[has_match, slice_b.columns] = slice_b.values

    result = pd.concat([result, base_attached], axis=1)

    main_baseline = result.get("main_mode_baseline", pd.Series(index=result.index, dtype=object))
    modes_baseline = result.get(
        "modes_baseline", pd.Series(index=result.index, dtype=object)
    ).fillna("").astype(str)

    is_new = ~has_match
    is_already_drt = has_match & (main_baseline.astype(str) == "drt")
    is_already_drt_leg = (
        has_match & (~is_already_drt)
        & modes_baseline.str.contains(DRT_TOKEN_REGEX, regex=True, na=False)
    )
    is_substituted = has_match & ~(is_already_drt | is_already_drt_leg)

    classification = np.select(
        [is_new, is_already_drt, is_already_drt_leg, is_substituted],
        ["new_trip", "already_drt", "already_drt_leg", "substituted"],
        default="unknown",
    )
    result.insert(2, "classification", classification)
    result.insert(3, "substituted_from_mode",
                  np.where(is_substituted, main_baseline.astype(str), ""))

    info["scenario_drt_total"] = int(len(result))
    info["matched"] = int(has_match.sum())
    info["unmatched_new_trip"] = int((~has_match).sum())
    return result, info


def print_summary(result: pd.DataFrame, info: dict) -> None:
    print(f"OD-key kind:           {info['key_kind']}  "
          f"(coord_grid={info['coord_grid']} m, hour_bucket={info['hour_bucket']} h)")
    print(f"Scenario DRT trips:    {info['scenario_drt_total']}")
    print(f"  matched in baseline: {info['matched']}")
    print(f"  no match (new_trip): {info['unmatched_new_trip']}")
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
    p.add_argument("--coord-grid", type=float, default=500.0,
                   help="Grid size in meters when matching by coordinates (default 500)")
    p.add_argument("--hour-bucket", type=int, default=1,
                   help="Hour bucket size for time bucket (0 disables time bucketing)")
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
    out_path = args.output_dir / f"od_match_{short_b}_{short_s}.csv"

    print(f"[1/3] Loading baseline: {args.baseline}")
    t0 = time.time()
    baseline_df = load_trips(args.baseline)
    print(f"      {len(baseline_df)} rows in {time.time() - t0:.1f}s")

    print(f"[2/3] Loading scenario: {args.scenario}")
    t0 = time.time()
    scenario_df = load_trips(args.scenario)
    print(f"      {len(scenario_df)} rows in {time.time() - t0:.1f}s")

    print("[3/3] OD-relaxed matching...")
    t0 = time.time()
    result, info = classify(baseline_df, scenario_df, args.coord_grid, args.hour_bucket)
    print(f"      done in {time.time() - t0:.1f}s")
    print()

    print(f"Writing: {out_path}")
    result.to_csv(out_path, index=False)
    print()

    print_summary(result, info)


if __name__ == "__main__":
    main()
