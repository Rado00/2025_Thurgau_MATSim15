#!/usr/bin/env python3
"""
Script per analizzare i viaggi DRT dagli output MATSim.

Questo script analizza:
1. output_legs.csv.gz - per capire quali leg sono DRT e se sono parte di viaggi intermodali
2. output_trips.csv.gz - per vedere i viaggi completi

USO:
    python analyze_drt_trips.py \
        --legs /path/to/output_legs.csv.gz \
        --trips /path/to/output_trips.csv.gz \
        --output /path/to/analysis_report.txt
"""

import argparse
import gzip
import csv
from collections import defaultdict


def read_csv_gz(filepath):
    """Legge un file CSV (compresso o no)."""
    if filepath.endswith('.gz'):
        f = gzip.open(filepath, 'rt', encoding='utf-8')
    else:
        f = open(filepath, 'r', encoding='utf-8')

    reader = csv.DictReader(f, delimiter=';')
    data = list(reader)
    f.close()
    return data


def analyze_legs(legs_data):
    """Analizza i leg per trovare pattern DRT."""

    drt_legs = [l for l in legs_data if l.get('mode') in ['drt', 'drt_access', 'drt_egress']]

    print(f"\n=== ANALISI LEGS ===")
    print(f"Totale legs nel file: {len(legs_data)}")
    print(f"Legs DRT (drt/drt_access/drt_egress): {len(drt_legs)}")

    # Raggruppa per person_id e trip_id per capire i pattern
    trips_with_drt = defaultdict(list)
    for leg in drt_legs:
        key = (leg.get('person'), leg.get('trip_id'))
        trips_with_drt[key].append(leg)

    print(f"Viaggi unici con almeno un leg DRT: {len(trips_with_drt)}")

    # Analizza i modi per ogni viaggio con DRT
    mode_patterns = defaultdict(int)
    for key, legs in trips_with_drt.items():
        modes = [l.get('mode') for l in legs]
        pattern = ' -> '.join(modes)
        mode_patterns[pattern] += 1

    print("\nPattern di viaggi con DRT:")
    for pattern, count in sorted(mode_patterns.items(), key=lambda x: -x[1]):
        print(f"  {pattern}: {count}")

    return trips_with_drt


def analyze_trips(trips_data):
    """Analizza i viaggi completi."""

    print(f"\n=== ANALISI TRIPS ===")
    print(f"Totale trips nel file: {len(trips_data)}")

    # Raggruppa per modalità principale
    mode_counts = defaultdict(int)
    for trip in trips_data:
        mode = trip.get('main_mode', trip.get('longest_distance_mode', 'unknown'))
        mode_counts[mode] += 1

    print("\nTrips per modalità principale:")
    for mode, count in sorted(mode_counts.items(), key=lambda x: -x[1]):
        print(f"  {mode}: {count}")

    # Cerca viaggi intermodali
    multimodal_trips = [t for t in trips_data if
                        'drt' in str(t.get('modes', '')).lower() or
                        'drt_access' in str(t.get('modes', '')).lower()]

    print(f"\nTrips con componente DRT: {len(multimodal_trips)}")

    return multimodal_trips


def find_intermodal_patterns(legs_data):
    """Trova pattern intermodali specifici (DRT + PT)."""

    print(f"\n=== PATTERN INTERMODALI DRT + PT ===")

    # Raggruppa legs per person e trip
    trip_legs = defaultdict(list)
    for leg in legs_data:
        key = (leg.get('person'), leg.get('trip_id'))
        trip_legs[key].append(leg)

    # Cerca pattern DRT + PT
    drt_pt_patterns = {
        'drt_access_pt': 0,  # DRT -> PT
        'pt_drt_egress': 0,  # PT -> DRT
        'drt_pt_drt': 0,     # DRT -> PT -> DRT
        'drt_only': 0,       # Solo DRT
        'other': 0
    }

    example_trips = defaultdict(list)

    for key, legs in trip_legs.items():
        modes = [l.get('mode') for l in legs]

        if 'drt' not in modes and 'drt_access' not in modes and 'drt_egress' not in modes:
            continue

        has_pt = 'pt' in modes
        has_drt = 'drt' in modes
        has_drt_access = 'drt_access' in modes
        has_drt_egress = 'drt_egress' in modes

        if has_drt_access and has_pt and has_drt_egress:
            drt_pt_patterns['drt_pt_drt'] += 1
            if len(example_trips['drt_pt_drt']) < 3:
                example_trips['drt_pt_drt'].append((key, modes))
        elif has_drt_access and has_pt:
            drt_pt_patterns['drt_access_pt'] += 1
            if len(example_trips['drt_access_pt']) < 3:
                example_trips['drt_access_pt'].append((key, modes))
        elif has_pt and has_drt_egress:
            drt_pt_patterns['pt_drt_egress'] += 1
            if len(example_trips['pt_drt_egress']) < 3:
                example_trips['pt_drt_egress'].append((key, modes))
        elif has_drt and not has_pt:
            drt_pt_patterns['drt_only'] += 1
        else:
            drt_pt_patterns['other'] += 1

    print("\nPattern trovati:")
    for pattern, count in drt_pt_patterns.items():
        print(f"  {pattern}: {count}")

    print("\nEsempi di viaggi intermodali:")
    for pattern, examples in example_trips.items():
        if examples:
            print(f"\n  {pattern}:")
            for (person, trip_id), modes in examples:
                print(f"    Person {person}, Trip {trip_id}: {' -> '.join(modes)}")

    return drt_pt_patterns


def main():
    parser = argparse.ArgumentParser(description='Analizza viaggi DRT da output MATSim')
    parser.add_argument('--legs', help='Path a output_legs.csv.gz')
    parser.add_argument('--trips', help='Path a output_trips.csv.gz')
    parser.add_argument('--output', help='Path per il report di output')

    args = parser.parse_args()

    if not args.legs and not args.trips:
        print("Specifica almeno --legs o --trips")
        print("\nFile tipici da MATSim output:")
        print("  - output_legs.csv.gz (dettaglio di ogni leg)")
        print("  - output_trips.csv.gz (viaggi aggregati)")
        print("  - drt_legs_drt.csv (leg DRT specifici)")
        return

    report_lines = []

    if args.legs:
        print(f"Caricamento legs da: {args.legs}")
        legs = read_csv_gz(args.legs)
        analyze_legs(legs)
        find_intermodal_patterns(legs)

    if args.trips:
        print(f"Caricamento trips da: {args.trips}")
        trips = read_csv_gz(args.trips)
        analyze_trips(trips)


if __name__ == '__main__':
    main()
