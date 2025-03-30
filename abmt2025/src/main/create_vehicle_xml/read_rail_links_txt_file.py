import json
file_name = ('rails.txt')

with open(file_name, 'r') as f:
    lines = [line.strip() for line in f]

# read json file
with open('zones_with_link_id.json') as f:
    zones_with_link_id = json.load(f)

# eliminate the lines that are in the zones_with_link_id.json
new_zones_with_link_id = {}
for key, value in zones_with_link_id.items():
    new_zones_with_link_id[key] = [x for x in value if x not in lines]

# save the dictionary to a JSON file
with open('zones_with_link_id_without_rail.json', 'w') as f:
    json.dump(new_zones_with_link_id, f, indent=4)