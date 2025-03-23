# import json
#
# # read the json file
# with open('links.json') as f:
#     links = json.load(f)
#
# with open('nodes.json') as f:
#     nodes = json.load(f)
#
# all_links = {}
# for link in links['links']:
#     link_id = link['id']
#     from_node = link['from_node']
#     to_node = link['to_node']
#     for node in nodes['nodes']:
#         if node['id'] == from_node:
#             from_node_x = node['x']
#             from_node_y = node['y']
#         if node['id'] == to_node:
#             to_node_x = node['x']
#             to_node_y = node['y']
#
#     if link_id not in all_links:
#         all_links[link_id] = {'from_node': from_node, 'to_node': to_node, 'from_node_x': from_node_x, 'from_node_y': from_node_y, 'to_node_x': to_node_x, 'to_node_y': to_node_y}
#
# # write the json file
# with open('all_links.json', 'w') as f:
#     json.dump(all_links, f, indent=4)
#


import json
import csv

# Read the JSON files
with open('links.json') as f:
    links = json.load(f)

with open('nodes.json') as f:
    nodes = json.load(f)

# Convert nodes to a dictionary for quick lookups
nodes_dict = {node['id']: node for node in nodes['nodes']}

# Prepare the enriched links data
all_links = []
for link in links['links']:
    from_node = link['from_node']
    to_node = link['to_node']
    link_id = link['id']

    # Get coordinates from the node dictionary
    from_node_x = nodes_dict[from_node]['x']
    from_node_y = nodes_dict[from_node]['y']
    to_node_x = nodes_dict[to_node]['x']
    to_node_y = nodes_dict[to_node]['y']

    # Add enriched link data to the result list
    all_links.append({
        'id': link_id,
        'from_node': from_node,
        'to_node': to_node,
        'from_node_x': from_node_x,
        'from_node_y': from_node_y,
        'to_node_x': to_node_x,
        'to_node_y': to_node_y,
    })

# Save the enriched links to a CSV file
csv_file = 'all_links.csv'
csv_headers = ['id', 'from_node', 'to_node', 'from_node_x', 'from_node_y', 'to_node_x', 'to_node_y']

with open(csv_file, mode='w', newline='') as f:
    writer = csv.DictWriter(f, fieldnames=csv_headers)
    writer.writeheader()  # Write the header row
    writer.writerows(all_links)  # Write the rows from the all_links list

print(f"Data successfully saved to {csv_file}")
