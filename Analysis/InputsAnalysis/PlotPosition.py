import xml.etree.ElementTree as ET
import matplotlib.pyplot as plt

# Parse the XML file
tree = ET.parse('C:/Users/muaa/Documents/3_MIEI/2023_ABMT_Corrado_Muratori/2023_ABMT_Corrado_Muratori/scenarios/equil/facilities.xml')
root = tree.getroot()

# Create empty lists to store facility coordinates and types
x_coords = []
y_coords = []
activity_types = []

# Iterate through each <facility> element in the XML
for facility in root.findall('.//facility'):
    x = float(facility.get('x'))
    y = float(facility.get('y'))

    # Append coordinates to the lists
    x_coords.append(x)
    y_coords.append(y)

    # Find the activity type within the facility element
    activity_element = facility.find('.//activity')
    if activity_element is not None:
        activity_type = activity_element.get('type')
        activity_types.append(activity_type)
    else:
        activity_types.append("Unknown")

# Create a scatter plot
plt.figure(figsize=(10, 6))
plt.scatter(x_coords, y_coords, c='blue', marker='o', label='Facility')

# Add labels to the points based on activity type
for i, txt in enumerate(activity_types):
    plt.annotate(txt, (x_coords[i], y_coords[i]), textcoords="offset points", xytext=(0, 10), ha='center')

# Set plot title and labels
plt.title('Facilities Plot')
plt.xlabel('X Coordinates')
plt.ylabel('Y Coordinates')

# Show legend
plt.legend()

# Show the plot
plt.grid()
plt.show()
