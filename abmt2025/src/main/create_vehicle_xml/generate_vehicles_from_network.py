import xml.etree.ElementTree as ET
import gzip
import random
from xml.dom import minidom

def parse_links_from_network(network_path):
    with gzip.open(network_path, 'rt') as f:
        tree = ET.parse(f)
        root = tree.getroot()
        links = root.find("links")
        return [link.attrib["id"] for link in links.findall("link")]

def create_vehicle_xml(link_ids, num_vehicles, t_0, t_1, capacity, output_file):
    vehicles = ET.Element("vehicles")

    for i in range(num_vehicles):
        start_link = random.choice(link_ids)
        ET.SubElement(vehicles, "vehicle", {
            "id": f"drt{i}",
            "start_link": start_link,
            "t_0": str(t_0),
            "t_1": str(t_1),
            "capacity": str(capacity)
        })

    # Pretty print
    xml_str = ET.tostring(vehicles, encoding="unicode")
    xml_pretty = minidom.parseString(xml_str).toprettyxml(indent="  ")
    full_doc = '<!DOCTYPE vehicles SYSTEM "http://matsim.org/files/dtd/dvrp_vehicles_v1.dtd">\n' + xml_pretty

    with open(output_file, "w") as f:
        f.write(full_doc)

    print(f"Generated {num_vehicles} vehicles to {output_file}")

# CONFIG (you can replace this part with reading from config.ini if needed)
NETWORK_PATH = "/home/muaa/2025_Thurgau_MATSim15/abmt2025/src/main/create_vehicle_xml/network/01_network.xml.gz"
OUTPUT_FILE = "01_DRT_1000.xml"
NUM_VEHICLES = 1000
T0 = 0.0
T1 = 86400.0
CAPACITY = 4

# Run the script
link_ids = parse_links_from_network(NETWORK_PATH)
create_vehicle_xml(link_ids, NUM_VEHICLES, T0, T1, CAPACITY, OUTPUT_FILE)
