#!/usr/bin/env python3
"""Logic D - OD-cell aggregate comparison without person matching.

For every (origin, destination) cell, count trips per main_mode in the
baseline and in the scenario, then compute the per-mode delta. For OD
cells where DRT trips appear in the scenario, the per-mode deltas of the
non-DRT modes are interpreted as substitution attributable to DRT.

Rationale: when activity chains reorder between runs, neither (person,
trip_number) (Logic A) nor (person, OD-key, time) (Logic B) recovers all
substitutions. Aggregating to the OD cell level drops the person identity
entirely; what remains is the net flow per mode per OD, which is the
quantity that scenario tables and modal-split summaries actually use.

Origin/destination cell definition, in order of preference (whichever
columns the input CSVs have):

    1. (start_link, end_link)
    2. (start_x_round, start_y_round, end_x_round, end_y_round)
       coordinates rounded to a configurable grid (default 500 m)
    3. (start_activity_type, end_activity_type)

Outputs (per pair):

    od_aggregate_<short_baseline>_<short_scenario>.csv
        Wide table: rows = OD cell, columns = baseline_<mode>,
        scenario_<mode>, delta_<mode> for every mode encountered, plus
        scenario_drt_total (DRT trips in this OD cell, scenario only).

    od_aggregate_<short_baseline>_<short_scenario>_summary.csv
        Aggregate over all OD cells where scenario_drt_total > 0:
        baseline_<mode>, scenario_<mode>, delta_<mode> per mode.

    od_aggregate_<short_baseline>_<short_scenario>_global.csv
        Aggregate over ALL OD cells: same columns as the summary, plus
        total trip counts. Lets you see the global modal delta independent
        of whether DRT appeared in the cell.
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
    "person", "main_mode", "modes",
    "start_activity_type", "end_activity_type",
    "start_link", "end_link",
    "start_x", "start_y", "end_x", "end_y",
    "distance",
]
REQUIRED_COLUMNS = {"main_mode", "modes"}


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
    drt_mask = has_drt(df)
    pt_mask = has_pt(df)
    out = df["main_mode"].astype(str).copy()
    out = out.where(~(drt_mask & ~pt_mask), "drt_standalone")
    out = out.where(~(drt_mask & pt_mask), "pt_with_drt")
    out = out.where(~(pt_mask & ~drt_mask), "pt_no_drt")
    return out


def pick_od_kind(df: pd.DataFrame) -> str:
    cols = set(df.columns)
    if {"start_link", "end_link"}.issubset(cols):
        return "link"
    if {"start_x", "start_y", "end_x", "end_y"}.issubset(cols):
        return "coord"
    if {"start_activity_type", "end_activity_type"}.issubset(cols):
        return "actype"
    raise ValueError(
        "Cannot build an OD cell key: need start_link/end_link, or "
        "start_x/y + end_x/y, or start_activity_type/end_activity_type."
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
    a = df["start_activity_type"].astype(str).fillna("")
    b = df["end_activity_type"].astype(str).fillna("")
    return a + "|" + b


def od_mode_pivot(df: pd.DataFrame, key_kind: str, coord_grid: float, prefix: str) -> pd.DataFrame:
    df = df.copy()
    df["od_key"] = build_od_key(df, key_kind, coord_grid)
    df["mode_eff"] = effective_mode(df)
    g = df.groupby(["od_key", "mode_eff"]).size().unstack(fill_value=0)
    g.columns = [f"{prefix}_{m}" for m in g.columns]
    return g


def aggregate(
    baseline_df: pd.DataFrame,
    scenario_df: pd.DataFrame,
    coord_grid: float,
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, dict]:
    b_kind = pick_od_kind(baseline_df)
    s_kind = pick_od_kind(scenario_df)
    precedence = {"link": 0, "coord": 1, "actype": 2}
    key_kind = b_kind if precedence[b_kind] >= precedence[s_kind] else s_kind

    b_pivot = od_mode_pivot(baseline_df, key_kind, coord_grid, "baseline")
    s_pivot = od_mode_pivot(scenario_df, key_kind, coord_grid, "scenario")
    table = b_pivot.join(s_pivot, how="outer").fillna(0).astype("int64")

    # Compute deltas for every mode that appears on either side
    b_modes = {c[len("baseline_"):] for c in table.columns if c.startswith("baseline_")}
    s_modes = {c[len("scenario_"):] for c in table.columns if c.startswith("scenario_")}
    all_modes = sorted(b_modes | s_modes)
    for m in all_modes:
        bcol = f"baseline_{m}"
        scol = f"scenario_{m}"
        if bcol not in table.columns:
            table[bcol] = 0
        if scol not in table.columns:
            table[scol] = 0
        table[f"delta_{m}"] = table[scol] - table[bcol]

    drt_modes = [m for m in all_modes if "drt" in m]
    if drt_modes:
        table["scenario_drt_total"] = sum(table[f"scenario_{m}"] for m in drt_modes)
    else:
        table["scenario_drt_total"] = 0

    # Order columns
    cols_order = []
    for m in all_modes:
        cols_order.extend([f"baseline_{m}", f"scenario_{m}", f"delta_{m}"])
    cols_order.append("scenario_drt_total")
    table = table[cols_order]
    table.index.name = "od_key"
    table = table.reset_index()

    # Summary on DRT-touched OD cells only
    drt_cells = table.loc[table["scenario_drt_total"] > 0]
    summary = pd.DataFrame({
        "metric": ["od_cells_with_drt", "od_cells_total"],
        "value":  [int(len(drt_cells)), int(len(table))],
    })
    rows_sum = []
    for m in all_modes:
        rows_sum.append({
            "mode": m,
            "baseline_count":  int(drt_cells[f"baseline_{m}"].sum()),
            "scenario_count":  int(drt_cells[f"scenario_{m}"].sum()),
            "delta_count":     int(drt_cells[f"delta_{m}"].sum()),
        })
    summary_modes = pd.DataFrame(rows_sum)

    rows_glob = []
    for m in all_modes:
        rows_glob.append({
            "mode": m,
            "baseline_count":  int(table[f"baseline_{m}"].sum()),
            "scenario_count":  int(table[f"scenario_{m}"].sum()),
            "delta_count":     int(table[f"delta_{m}"].sum()),
        })
    global_modes = pd.DataFrame(rows_glob)

    info = {
        "key_kind": key_kind,
        "coord_grid": coord_grid,
        "od_cells_total": int(len(table)),
        "od_cells_with_drt": int(len(drt_cells)),
        "all_modes": all_modes,
    }
    return table, summary_modes, global_modes, info


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--baseline", required=True, type=Path)
    p.add_argument("--scenario", required=True, type=Path)
    p.add_argument("--output-dir", required=True, type=Path)
    p.add_argument("--coord-grid", type=float, default=500.0,
                   help="Grid size in meters when aggregating by coordinates (default 500)")
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
    out_path = args.output_dir / f"od_aggregate_{short_b}_{short_s}.csv"
    out_summary = args.output_dir / f"od_aggregate_{short_b}_{short_s}_summary.csv"
    out_global = args.output_dir / f"od_aggregate_{short_b}_{short_s}_global.csv"

    print(f"[1/3] Loading baseline: {args.baseline}")
    t0 = time.time()
    baseline_df = load_trips(args.baseline)
    print(f"      {len(baseline_df)} rows in {time.time() - t0:.1f}s")

    print(f"[2/3] Loading scenario: {args.scenario}")
    t0 = time.time()
    scenario_df = load_trips(args.scenario)
    print(f"      {len(scenario_df)} rows in {time.time() - t0:.1f}s")

    print("[3/3] Aggregating per OD cell...")
    t0 = time.time()
    table, summary_modes, global_modes, info = aggregate(
        baseline_df, scenario_df, args.coord_grid
    )
    print(f"      done in {time.time() - t0:.1f}s")
    print()

    print(f"Writing: {out_path}")
    table.to_csv(out_path, index=False)
    print(f"Writing: {out_summary}")
    summary_modes.to_csv(out_summary, index=False)
    print(f"Writing: {out_global}")
    global_modes.to_csv(out_global, index=False)
    print()

    print(f"OD-key kind:           {info['key_kind']} (coord_grid={info['coord_grid']} m)")
    print(f"OD cells total:        {info['od_cells_total']}")
    print(f"OD cells with DRT:     {info['od_cells_with_drt']}")
    print()
    print("Per-mode delta on OD cells where DRT appears in scenario:")
    print(summary_modes.to_string(index=False))
    print()
    print("Per-mode delta on ALL OD cells (global view):")
    print(global_modes.to_string(index=False))


if __name__ == "__main__":
    main()
