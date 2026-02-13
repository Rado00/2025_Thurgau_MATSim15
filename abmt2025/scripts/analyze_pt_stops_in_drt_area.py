#!/usr/bin/env python3
"""
Script per verificare le fermate PT rispetto alla zona DRT.

Questo script:
1. Carica lo shapefile della zona DRT
2. Carica il transit_schedule.xml.gz
3. Verifica quali fermate PT sono:
   - DENTRO la zona DRT
   - Entro un certo raggio dalla zona DRT (es. 5km come da config intermodale)
4. Genera un report

USO:
    python analyze_pt_stops_in_drt_area.py \
        --schedule /path/to/transit_schedule.xml.gz \
        --shapefile /path/to/drt_zone.shp \
        --radius 5000 \
        --output /path/to/output_report.csv
"""

import argparse
import gzip
import xml.etree.ElementTree as ET
from pathlib import Path

try:
    import geopandas as gpd
    from shapely.geometry import Point
    from shapely.ops import unary_union
    HAS_GEO = True
except ImportError:
    HAS_GEO = False
    print("ATTENZIONE: geopandas/shapely non installati.")
    print("Installa con: pip install geopandas shapely")


def parse_transit_schedule(schedule_path):
    """Parse transit_schedule.xml.gz e estrae le fermate."""
    stops = []

    # Apri file compresso o normale
    if schedule_path.endswith('.gz'):
        f = gzip.open(schedule_path, 'rt', encoding='utf-8')
    else:
        f = open(schedule_path, 'r', encoding='utf-8')

    try:
        tree = ET.parse(f)
        root = tree.getroot()

        # Cerca stopFacility elementi
        for stop in root.iter('stopFacility'):
            stop_id = stop.get('id')
            x = float(stop.get('x'))
            y = float(stop.get('y'))
            name = stop.get('name', 'N/A')

            # Cerca attributi aggiuntivi (es. stopCategory)
            stop_category = None
            for attr in stop.iter('attribute'):
                if attr.get('name') == 'stopCategory':
                    stop_category = attr.text
                    break

            stops.append({
                'id': stop_id,
                'x': x,
                'y': y,
                'name': name,
                'stopCategory': stop_category
            })
    finally:
        f.close()

    return stops


def analyze_stops_vs_drt_area(stops, shapefile_path, radius_m=5000):
    """Analizza le fermate rispetto alla zona DRT."""

    if not HAS_GEO:
        print("Impossibile eseguire analisi spaziale senza geopandas")
        return None

    # Carica shapefile
    gdf_drt = gpd.read_file(shapefile_path)
    drt_area = unary_union(gdf_drt.geometry)

    # Buffer per il raggio di ricerca intermodale
    drt_area_buffered = drt_area.buffer(radius_m)

    results = []
    for stop in stops:
        point = Point(stop['x'], stop['y'])

        inside_drt = drt_area.contains(point)
        inside_buffer = drt_area_buffered.contains(point)
        distance_to_drt = point.distance(drt_area) if not inside_drt else 0

        results.append({
            **stop,
            'inside_drt_area': inside_drt,
            'within_intermodal_radius': inside_buffer,
            'distance_to_drt_area_m': round(distance_to_drt, 1)
        })

    return results


def print_summary(results, radius_m):
    """Stampa un riepilogo dei risultati."""

    total = len(results)
    inside = sum(1 for r in results if r['inside_drt_area'])
    within_radius = sum(1 for r in results if r['within_intermodal_radius'])
    outside = total - within_radius

    print("\n" + "="*60)
    print("RIEPILOGO FERMATE PT vs ZONA DRT")
    print("="*60)
    print(f"Totale fermate PT nel transit_schedule: {total}")
    print(f"Fermate DENTRO la zona DRT: {inside}")
    print(f"Fermate entro {radius_m}m dalla zona DRT: {within_radius}")
    print(f"Fermate oltre {radius_m}m dalla zona DRT: {outside}")
    print("="*60)

    if inside == 0:
        print("\n⚠️  ATTENZIONE: Nessuna fermata PT dentro la zona DRT!")
        print("   I viaggi multi-modali usano fermate FUORI dalla zona.")

    # Mostra le fermate più vicine fuori dalla zona
    outside_stops = [r for r in results if not r['inside_drt_area'] and r['within_intermodal_radius']]
    if outside_stops:
        print(f"\nFermate PT raggiungibili con DRT (entro {radius_m}m):")
        outside_stops.sort(key=lambda x: x['distance_to_drt_area_m'])
        for stop in outside_stops[:10]:
            print(f"  - {stop['name']} ({stop['id']}): {stop['distance_to_drt_area_m']:.0f}m dalla zona")

    # Mostra le fermate dentro la zona
    inside_stops = [r for r in results if r['inside_drt_area']]
    if inside_stops:
        print(f"\nFermate PT DENTRO la zona DRT:")
        for stop in inside_stops:
            cat = f" (cat: {stop['stopCategory']})" if stop['stopCategory'] else ""
            print(f"  - {stop['name']} ({stop['id']}){cat}")


def save_to_csv(results, output_path):
    """Salva i risultati in CSV."""
    import csv

    with open(output_path, 'w', newline='', encoding='utf-8') as f:
        if results:
            writer = csv.DictWriter(f, fieldnames=results[0].keys())
            writer.writeheader()
            writer.writerows(results)

    print(f"\nRisultati salvati in: {output_path}")


def main():
    parser = argparse.ArgumentParser(description='Analizza fermate PT vs zona DRT')
    parser.add_argument('--schedule', required=True, help='Path al transit_schedule.xml.gz')
    parser.add_argument('--shapefile', required=True, help='Path allo shapefile della zona DRT')
    parser.add_argument('--radius', type=int, default=5000,
                        help='Raggio di ricerca intermodale in metri (default: 5000)')
    parser.add_argument('--output', help='Path per il file CSV di output')

    args = parser.parse_args()

    print(f"Caricamento transit_schedule da: {args.schedule}")
    stops = parse_transit_schedule(args.schedule)
    print(f"Trovate {len(stops)} fermate PT")

    if HAS_GEO:
        print(f"\nAnalisi spaziale con shapefile: {args.shapefile}")
        results = analyze_stops_vs_drt_area(stops, args.shapefile, args.radius)

        if results:
            print_summary(results, args.radius)

            if args.output:
                save_to_csv(results, args.output)
    else:
        print("\nSolo lista fermate (senza analisi spaziale):")
        for stop in stops[:20]:
            print(f"  {stop['id']}: {stop['name']} ({stop['x']}, {stop['y']})")
        if len(stops) > 20:
            print(f"  ... e altre {len(stops) - 20} fermate")


if __name__ == '__main__':
    main()
