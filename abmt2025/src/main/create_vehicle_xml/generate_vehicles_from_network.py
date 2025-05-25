import xml.etree.ElementTree as ET
import random
import gzip
import sys

def get_car_links_from_network(network_file):
    with gzip.open(network_file, 'rt', encoding='utf-8') as f:
        tree = ET.parse(f)
    root = tree.getroot()
    link_ids = []
    for link in root.findall(".//link"):
        modes = link.attrib.get("modes", "").replace(",", " ").split()
        if "car" in modes:
            link_ids.append(link.attrib["id"])
    print(f"Found {len(link_ids)} links with mode 'car'.")
    return link_ids

def create_vehicle_xml(link_ids, num_vehicles, t0, t1, capacity, output_file):
    if not link_ids:
        raise ValueError("No links with mode 'car' found in the network file.")
    
    lines = [
        '<?xml version="1.0" ?>',
        '<!DOCTYPE vehicles SYSTEM "http://matsim.org/files/dtd/dvrp_vehicles_v1.dtd">',
        '<vehicles>'
    ]
    
    for i in range(num_vehicles):
        start_link = random.choice(link_ids)
        lines.append(f'  <vehicle id="drt{i}" start_link="{start_link}" t_0="{float(t0)}" t_1="{float(t1)}" capacity="{capacity}"/>')
    
    lines.append('</vehicles>')

    with open(output_file, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    
    print(f"Vehicle XML written to {output_file}")

if __name__ == "__main__":
    if len(sys.argv) != 7:
        print("Usage: python generate_vehicles_from_network.py <network_file> <num_vehicles> <t0> <t1> <capacity> <output_file>")
        sys.exit(1)

    network_file = sys.argv[1]
    num_vehicles = int(sys.argv[2])
    t0 = int(sys.argv[3])
    t1 = int(sys.argv[4])
    capacity = int(sys.argv[5])
    output_file = sys.argv[6]

    link_ids = get_car_links_from_network(network_file)
    create_vehicle_xml(link_ids, num_vehicles, t0, t1, capacity, output_file)
