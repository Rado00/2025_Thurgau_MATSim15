import json
import random
import configparser
import xml.etree.ElementTree as ET


def create_vehicle_xml(num_vehicles, t_0, t_1, capacity, output_file, zone_name):

    # read JSON file
    with open('zones_with_link_id_without_rail.json') as f:
        zones_with_link_id = json.load(f)

    links_id_list = zones_with_link_id[zone_name]
    # Define the XML structure
    vehicles = ET.Element("vehicles")

    for i in range(num_vehicles):
        # Generate a random start_link
        start_link = random.choice(links_id_list)

        # Create a vehicle element
        vehicle = ET.SubElement(vehicles, "vehicle")
        vehicle.set("id", f"drt{i}")
        vehicle.set("start_link", str(start_link))
        vehicle.set("t_0", str(t_0))
        vehicle.set("t_1", str(t_1))
        vehicle.set("capacity", str(capacity))


    # Add the DOCTYPE
    doctype = "<!DOCTYPE vehicles SYSTEM \"http://matsim.org/files/dtd/dvrp_vehicles_v1.dtd\">\n"

    # Convert to a string and include the DOCTYPE
    xml_string = ET.tostring(vehicles, encoding="unicode", method="xml")
    xml_content = f"{doctype}{xml_string}"

    # Write to file
    with open(output_file, "w") as f:
        f.write(xml_content)

    print(f"XML file successfully written to {output_file}")


# Read inputs from config.ini
config = configparser.ConfigParser()
config.read("config.ini")

number_of_vehicles = int(config["DEFAULT"]["number_of_vehicles"])
t_0 = float(config["DEFAULT"]["t_0"])
t_1 = float(config["DEFAULT"]["t_1"])
capacity = int(config["DEFAULT"]["capacity"])
file_name = config["DEFAULT"]["file_name"]
zone_name = config["DEFAULT"]["zone_name"]

# Generate the XML file
create_vehicle_xml(number_of_vehicles, t_0, t_1, capacity, file_name, zone_name)
