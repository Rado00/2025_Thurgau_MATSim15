import json
import os

data_directory = 'network/'

# read JSON files from the data_directory and create a dictionary
network_dict = {}
data_directory = 'network/'
for filename in os.listdir(data_directory):
    if filename.endswith('.geojson'):
        with open(data_directory + filename) as f:
            geojson_data = json.load(f)

        links_id_list = []
        for feature in geojson_data['features']:
            geometry = feature['geometry']
            properties = feature['properties']
            links_id_list.append(properties['id'])

        network_dict[filename.split("_")[0]] = links_id_list

# save the dictionary to a JSON file
with open('zones_with_link_id.json', 'w') as f:
    json.dump(network_dict, f, indent=4)
