#!/usr/bin/env python3
"""Reassign rejected DRT trips using the baseline as fallback.

For each person, compares the number of trips (rows) between baseline and
DRT scenario. If DRT has strictly fewer trips than baseline, it assumes the
missing trips are due to stuck agents after a DRT rejection and recovers
those missing trip_numbers from the baseline file.

Matching is done on (person, trip_number). The logic is intentionally
simple: no coordinate check, no mode check - trip_number correspondence
only.

Usage:
    python reassign_rejected_trips.py \\
        --baseline /path/to/baseline/trips_all_activities_inside.csv \\
        --drt      /path/to/drt_case/trips_all_activities_inside.csv

Output defaults to <drt_filename>_fx.<ext> next to the DRT input.
Report defaults to reassignment_report.txt next to the output.
"""

import argparse
import sys
import time
from pathlib import Path
from typing import Optional

import pandas as pd

try:
    import pyarrow  # noqa: F401
    _HAS_PYARROW = True
except ImportError:
    _HAS_PYARROW = False


REQUIRED_COLUMNS = {"person", "trip_number", "main_mode"}


def load_trips(path: Path, engine: str) -> pd.DataFrame:
    if engine == "pyarrow" and _HAS_PYARROW:
        try:
            return pd.read_csv(path, engine="pyarrow")
        except Exception:
            pass
    return pd.read_csv(path, engine="c")


def compute_output_path(drt_path: Path, override: Optional[Path]) -> Path:
    if override is not None:
        return override
    name = drt_path.name
    if name.endswith(".csv.gz"):
        stem, ext = name[:-7], ".csv.gz"
    elif name.endswith(".csv"):
        stem, ext = name[:-4], ".csv"
    else:
        stem, ext = drt_path.stem, drt_path.suffix
    return drt_path.parent / f"{stem}_fx{ext}"


def reassign(baseline_df: pd.DataFrame, drt_df: pd.DataFrame):
    baseline_counts = baseline_df.groupby("person").size().rename("b_count")
    drt_counts = drt_df.groupby("person").size().rename("d_count")
    counts = pd.concat([baseline_counts, drt_counts], axis=1).fillna(0).astype("int64")

    b = counts["b_count"]
    d = counts["d_count"]

    persons_only_baseline = counts.index[(d == 0) & (b > 0)]
    persons_only_drt = counts.index[(b == 0) & (d > 0)]
    persons_complete = counts.index[(b > 0) & (d >= b)]
    persons_patched = counts.index[(b > 0) & (d > 0) & (d < b)]
    persons_needing_rows = counts.index[(b > d)]

    candidate_rows = baseline_df[baseline_df["person"].isin(persons_needing_rows)]
    cand_keys = candidate_rows[["person", "trip_number"]]

    existing_drt_keys = drt_df.loc[
        drt_df["person"].isin(persons_needing_rows), ["person", "trip_number"]
    ]

    merged = cand_keys.merge(
        existing_drt_keys, on=["person", "trip_number"], how="left", indicator=True
    )
    missing_keys = merged.loc[merged["_merge"] == "left_only", ["person", "trip_number"]]

    rows_to_add = baseline_df.merge(missing_keys, on=["person", "trip_number"], how="inner")

    if len(rows_to_add) > 0:
        out = pd.concat([drt_df, rows_to_add], ignore_index=True)
    else:
        out = drt_df.copy()

    out = out.sort_values(["person", "trip_number"], kind="stable").reset_index(drop=True)

    added_modes = (
        rows_to_add["main_mode"].value_counts().to_dict() if len(rows_to_add) else {}
    )

    stats = {
        "persons_baseline": int((b > 0).sum()),
        "persons_drt": int((d > 0).sum()),
        "persons_only_baseline": int(len(persons_only_baseline)),
        "persons_only_drt": int(len(persons_only_drt)),
        "persons_complete": int(len(persons_complete)),
        "persons_patched": int(len(persons_patched)),
        "trips_baseline_total": int(len(baseline_df)),
        "trips_drt_total": int(len(drt_df)),
        "trips_added": int(len(rows_to_add)),
        "trips_output_total": int(len(out)),
        "added_modes": added_modes,
    }
    return out, stats


def format_report(stats, baseline_path, drt_path, output_path, elapsed):
    lines = []
    lines.append("DRT Rejected Trip Reassignment Report")
    lines.append("=" * 60)
    lines.append(f"Baseline file : {baseline_path}")
    lines.append(f"DRT file      : {drt_path}")
    lines.append(f"Output file   : {output_path}")
    lines.append(f"Elapsed       : {elapsed:.1f} s")
    lines.append("")
    lines.append("Persons")
    lines.append(f"  In baseline              : {stats['persons_baseline']}")
    lines.append(f"  In DRT                   : {stats['persons_drt']}")
    lines.append(f"  Only in baseline (added) : {stats['persons_only_baseline']}")
    lines.append(f"  Only in DRT (kept as-is) : {stats['persons_only_drt']}")
    lines.append(f"  Complete in DRT          : {stats['persons_complete']}")
    lines.append(f"  Patched (some added)     : {stats['persons_patched']}")
    lines.append("")
    lines.append("Trips")
    lines.append(f"  Baseline total           : {stats['trips_baseline_total']}")
    lines.append(f"  DRT total (original)     : {stats['trips_drt_total']}")
    lines.append(f"  Added from baseline      : {stats['trips_added']}")
    lines.append(f"  Output total             : {stats['trips_output_total']}")
    lines.append("")
    if stats["added_modes"]:
        lines.append("Added trips by main_mode:")
        for mode, count in sorted(stats["added_modes"].items(), key=lambda x: -x[1]):
            lines.append(f"  {str(mode):20s}: {count}")
    else:
        lines.append("No trips added.")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--baseline", required=True, type=Path,
                        help="Path to baseline trips_all_activities_inside.csv")
    parser.add_argument("--drt", required=True, type=Path,
                        help="Path to DRT trips_all_activities_inside.csv")
    parser.add_argument("--output", type=Path, default=None,
                        help="Output CSV path (default: <drt_name>_fx.<ext>)")
    parser.add_argument("--report", type=Path, default=None,
                        help="Report path (default: reassignment_report.txt next to output)")
    parser.add_argument("--engine", choices=["pyarrow", "c"],
                        default="pyarrow" if _HAS_PYARROW else "c",
                        help="Pandas CSV engine")
    args = parser.parse_args()

    if not args.baseline.is_file():
        print(f"ERROR: baseline file not found: {args.baseline}", file=sys.stderr)
        sys.exit(2)
    if not args.drt.is_file():
        print(f"ERROR: DRT file not found: {args.drt}", file=sys.stderr)
        sys.exit(2)

    output_path = compute_output_path(args.drt, args.output)
    report_path = args.report if args.report is not None else (
        output_path.parent / "reassignment_report.txt"
    )

    print(f"[1/4] Loading baseline: {args.baseline}")
    t0 = time.time()
    baseline_df = load_trips(args.baseline, args.engine)
    print(f"      {len(baseline_df)} rows in {time.time() - t0:.1f}s")

    print(f"[2/4] Loading DRT:      {args.drt}")
    t0 = time.time()
    drt_df = load_trips(args.drt, args.engine)
    print(f"      {len(drt_df)} rows in {time.time() - t0:.1f}s")

    missing_b = REQUIRED_COLUMNS - set(baseline_df.columns)
    missing_d = REQUIRED_COLUMNS - set(drt_df.columns)
    if missing_b or missing_d:
        print(
            f"ERROR: missing required columns. "
            f"Baseline lacks {missing_b or '{}'}, DRT lacks {missing_d or '{}'}",
            file=sys.stderr,
        )
        sys.exit(3)

    if list(baseline_df.columns) != list(drt_df.columns):
        print(
            "WARNING: baseline and DRT have different column orders/sets. "
            "Output will use DRT schema; added rows will be aligned by column name.",
            file=sys.stderr,
        )
        baseline_df = baseline_df.reindex(columns=drt_df.columns)

    print("[3/4] Running reassignment...")
    t0 = time.time()
    out_df, stats = reassign(baseline_df, drt_df)
    elapsed = time.time() - t0
    print(f"      done in {elapsed:.1f}s")

    print(f"[4/4] Writing output:   {output_path}")
    t0 = time.time()
    out_df.to_csv(output_path, index=False)
    print(f"      {len(out_df)} rows in {time.time() - t0:.1f}s")

    report_text = format_report(stats, args.baseline, args.drt, output_path, elapsed)
    report_path.write_text(report_text)
    print()
    print(report_text)
    print(f"Report written: {report_path}")


if __name__ == "__main__":
    main()
