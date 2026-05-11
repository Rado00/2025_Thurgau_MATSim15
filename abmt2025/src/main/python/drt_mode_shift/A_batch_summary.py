#!/usr/bin/env python3
"""Batch-run Logic A and aggregate three metrics per scenario into a CSV.

For each *trips_all_activities_inside*.csv in --input-dir, runs the same
classification as A_compare_drt_substitution.py against a single
--baseline file, and aggregates the following three counts per scenario
into a single wide CSV:

    feeder_pt substituted_from_mode bike
    feeder_pt substituted_from_mode walk
    feeder_pt new_trip

Output layout (one column per simulation, in the user-supplied order):

    Nome_sim, <sim_1>, <sim_2>, ...
    feeder_pt substituted_from_mode bike, n, n, ...
    feeder_pt substituted_from_mode walk, n, n, ...
    feeder_pt new_trip,                   n, n, ...

Simulation name extraction (from file name):

    20260415_174340_DRT_25_ShapeFile_25_drt_70_8_Service_Zone_25_Fvero
        _trips_all_activities_inside_sim[_fx].csv
    -> 25_drt_70_8_Service_Zone_25_Fvero

i.e. everything between the literal '_ShapeFile_' and the trailing
'_trips_all_activities_inside_sim' (optionally followed by '_fx').

Simulations whose extracted name is NOT in the order list (hardcoded in
this script) are silently skipped.
"""

import argparse
import sys
import time
from pathlib import Path

import pandas as pd

# Import the existing classification logic from A_compare_drt_substitution.
SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))
from A_compare_drt_substitution import classify, load_trips  # noqa: E402


SIM_ORDER = [
    "01_drt_5_8_01_1", "01_drt_3_8_01_2", "01_drt_2_8_01_3", "01_drt_1_8_01_4",
    "02_drt_3_8_02_1", "02_drt_2_8_02_2", "02_drt_1_8_02_3",
    "03_drt_7_8_03_1", "03_drt_4_8_03_2", "03_drt_3_8_03_3", "03_drt_2_8_03_4", "03_drt_1_8_03_5",
    "04_drt_3_8_04_1", "04_drt_2_8_04_2", "04_drt_1_8_04_3",
    "05_drt_12_8_05_1", "05_drt_8_8_05_2", "05_drt_5_8_05_3", "05_drt_3_8_05_4", "05_drt_2_8_05_5",
    "06_drt_8_8_06_1", "06_drt_5_8_06_2", "06_drt_3_8_06_3", "06_drt_2_8_06_4", "06_drt_1_8_06_5",
    "07_drt_5_8_07_1", "07_drt_3_8_07_2", "07_drt_2_8_07_3", "07_drt_1_8_07_4",
    "08_drt_7_8_08_1", "08_drt_4_8_08_2", "08_drt_3_8_08_3", "08_drt_2_8_08_4", "08_drt_1_8_08_5",
    "09_drt_4_8_09_1", "09_drt_2_8_09_2", "09_drt_1_8_09_4",
    "10_drt_1_8_10_1",
    "11_drt_7_8_11_1", "11_drt_4_8_11_2", "11_drt_3_8_11_3", "11_drt_2_8_11_4", "11_drt_1_8_11_5",
    "12_drt_5_8_12_1", "12_drt_3_8_12_2", "12_drt_2_8_12_3", "12_drt_1_8_12_4",
    "13_drt_2_8_13_1", "13_drt_1_8_13_2",
    "14_drt_8_8_14_1", "14_drt_5_8_14_2", "14_drt_3_8_14_3", "14_drt_2_8_14_4", "14_drt_1_8_14_5",
    "15_drt_23_8_15_1", "15_drt_14_8_15_2", "15_drt_9_8_15_3", "15_drt_6_8_15_4", "15_drt_4_8_15_5",
    "16_drt_5_8_16_1", "16_drt_3_8_16_2", "16_drt_2_8_16_3", "16_drt_1_8_16_4",
    "17_drt_2_8_17_1", "17_drt_1_8_17_2",
    "18_drt_4_8_18_1", "18_drt_3_8_18_2", "18_drt_2_8_18_3", "18_drt_1_8_18_4",
    "19_drt_33_8_19_1", "19_drt_21_8_19_2", "19_drt_13_8_19_3", "19_drt_8_8_19_4", "19_drt_6_8_19_5",
    "20_drt_57_8_20_1", "20_drt_36_8_20_2", "20_drt_22_8_20_3", "20_drt_14_8_20_4", "20_drt_10_8_20_5",
    "21_drt_32_8_21_1", "21_drt_20_8_21_2", "21_drt_12_8_21_3", "21_drt_8_8_21_4", "21_drt_5_8_21_5",
    "22_drt_16_8_22_1", "22_drt_10_8_22_2", "22_drt_6_8_22_3", "22_drt_4_8_22_4", "22_drt_3_8_22_5",
    "23_drt_55_8_23_1", "23_drt_35_8_23_2", "23_drt_21_8_23_3", "23_drt_14_8_23_4", "23_drt_9_8_23_5",
    "24_drt_36_8_24_1", "24_drt_23_8_24_2", "24_drt_14_8_24_3", "24_drt_9_8_24_4", "24_drt_6_8_24_5",
    "25_drt_112_8_25_1", "25_drt_70_8_25_2", "25_drt_43_8_25_3", "25_drt_28_8_25_4", "25_drt_19_8_25_5",
    "25_drt_70_8_Price_Zone_25_0", "25_drt_70_8_Price_Zone_25_NH",
    "25_drt_70_8_Price_Zone_25_5", "25_drt_70_8_Price_Zone_25_10",
    "25_drt_70_8_Service_Zone_25_Fvero", "25_drt_70_8_Service_Zone_25_S",
]

ROW_LABELS = [
    "feeder_pt substituted_from_mode bike",
    "feeder_pt substituted_from_mode walk",
    "feeder_pt new_trip",
]


def extract_sim_name(path: Path) -> str | None:
    name = path.name
    if name.endswith(".csv.gz"):
        name = name[:-7]
    elif name.endswith(".csv"):
        name = name[:-4]
    else:
        return None

    marker = "_ShapeFile_"
    idx = name.find(marker)
    if idx < 0:
        return None
    name = name[idx + len(marker):]

    for suffix in ("_trips_all_activities_inside_sim_fx",
                   "_trips_all_activities_inside_sim"):
        if name.endswith(suffix):
            return name[:-len(suffix)]
    return None


def compute_metrics(baseline_df: pd.DataFrame, scenario_path: Path) -> dict[str, int]:
    scenario_df = load_trips(scenario_path)
    result = classify(baseline_df, scenario_df)

    feeder = result["drt_subtype"] == "feeder_pt"
    substituted = result["classification"] == "substituted"
    new_trip = result["classification"] == "new_trip"

    sub_mode = result["substituted_from_mode"].astype(str)

    return {
        ROW_LABELS[0]: int((feeder & substituted & (sub_mode == "bike")).sum()),
        ROW_LABELS[1]: int((feeder & substituted & (sub_mode == "walk")).sum()),
        ROW_LABELS[2]: int((feeder & new_trip).sum()),
    }


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--baseline", required=True, type=Path)
    p.add_argument("--input-dir", required=True, type=Path)
    p.add_argument("--output-csv", required=True, type=Path)
    args = p.parse_args()

    if not args.baseline.is_file():
        print(f"ERROR: baseline not found: {args.baseline}", file=sys.stderr)
        sys.exit(2)
    if not args.input_dir.is_dir():
        print(f"ERROR: input directory not found: {args.input_dir}", file=sys.stderr)
        sys.exit(2)

    print(f"Loading baseline: {args.baseline}")
    t0 = time.time()
    baseline_df = load_trips(args.baseline)
    print(f"  {len(baseline_df)} rows in {time.time() - t0:.1f}s")
    print()

    # Map sim_name -> path for files present in the input dir
    found: dict[str, Path] = {}
    skipped_unparseable: list[str] = []
    for path in sorted(args.input_dir.glob("*trips_all_activities_inside*.csv")):
        sim = extract_sim_name(path)
        if sim is None:
            skipped_unparseable.append(path.name)
            continue
        if sim in found:
            print(f"WARNING: duplicate sim '{sim}': keeping {found[sim].name}, "
                  f"ignoring {path.name}")
            continue
        found[sim] = path

    in_order = [s for s in SIM_ORDER if s in found]
    out_of_order = sorted(set(found.keys()) - set(SIM_ORDER))

    print(f"Found {len(found)} scenario files in {args.input_dir}")
    print(f"  in SIM_ORDER (kept):       {len(in_order)}")
    print(f"  not in SIM_ORDER (ignored):{len(out_of_order)}")
    if out_of_order:
        for s in out_of_order:
            print(f"    - {s}")
    if skipped_unparseable:
        print(f"  unparseable names ({len(skipped_unparseable)}):")
        for n in skipped_unparseable:
            print(f"    - {n}")
    print()

    if not in_order:
        print("ERROR: no scenario file matched SIM_ORDER. Nothing to write.",
              file=sys.stderr)
        sys.exit(3)

    columns: dict[str, dict[str, int]] = {}
    for i, sim in enumerate(in_order, 1):
        path = found[sim]
        print(f"[{i}/{len(in_order)}] {sim}")
        t0 = time.time()
        try:
            columns[sim] = compute_metrics(baseline_df, path)
            print(f"    bike={columns[sim][ROW_LABELS[0]]:>6d}  "
                  f"walk={columns[sim][ROW_LABELS[1]]:>6d}  "
                  f"new ={columns[sim][ROW_LABELS[2]]:>6d}  "
                  f"({time.time() - t0:.1f}s)")
        except Exception as e:
            print(f"    FAILED: {e}")
            columns[sim] = {lbl: -1 for lbl in ROW_LABELS}

    # Build the wide table: rows = metrics, columns = sims (in SIM_ORDER)
    data = {sim: [columns[sim][lbl] for lbl in ROW_LABELS] for sim in in_order}
    table = pd.DataFrame(data, index=ROW_LABELS)
    table.index.name = "Nome_sim"

    args.output_csv.parent.mkdir(parents=True, exist_ok=True)
    table.to_csv(args.output_csv)
    print()
    print(f"Wrote: {args.output_csv}")
    print(f"Shape: {table.shape[0]} rows x {table.shape[1]} sims")


if __name__ == "__main__":
    main()
