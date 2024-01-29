""" import gzip
import xml.etree.ElementTree as ET
from collections import Counter

# Function to parse the XML and extract information
def extract_population_data(xml_content):
    # Parse the XML content
    tree = ET.ElementTree(ET.fromstring(xml_content))
    root = tree.getroot()

    # Data structures for storing various information
    person_ids = set()
    trip_type_distribution = Counter()
    activity_chain_distribution = Counter()
    activity_types = set()

    # Iterate through each person in the XML
    for person in root.iter('person'):
        person_id = person.get('id')
        person_ids.add(person_id)

        activities = []
        for activity in person.iter('activity'):
            activity_type = activity.get('type')
            activities.append(activity_type)
            activity_types.add(activity_type)  # Collect the unique activity types

        # Convert activities to a chain string and count the distribution
        activity_chain = '-'.join(activities)
        activity_chain_distribution[activity_chain] += 1

        # Count the trip types (leg modes)
        for leg in person.iter('leg'):
            trip_type_distribution[leg.get('mode')] += 1

    return person_ids, trip_type_distribution, activity_chain_distribution, activity_types

# Function to process the .gz file and extract the XML
def process_gz_xml_file(filepath):
    # Open the gzip file
    with gzip.open(filepath, 'rt', encoding='utf-8') as f:
        xml_content = f.read()

    # Extract data
    person_ids, trip_type_distribution, activity_chain_distribution, activity_types = extract_population_data(xml_content)

    # Output the results
    print(f"Total number of person IDs: {len(person_ids)}")
    print("Trip type distribution:")
    for trip_type, count in trip_type_distribution.items():
        print(f"  {trip_type}: {count}")

    print("Activity chain distribution:")
    for chain, count in activity_chain_distribution.items():
        print(f"  {chain}: {count}")

    print("List of all different activities:")
    for activity in sorted(activity_types):  # Sort the activities for readability
        print(f"  {activity}")


# Replace 'path_to_your_file.xml.gz' with the actual file path
process_gz_xml_file('C:/Users/muaa/Documents/3_MIEI/2023_ABMT_Data/Zurich/10pct/zurich_population_10pct.xml.gz') """


import gzip
import xml.etree.ElementTree as ET
from collections import Counter
import matplotlib.pyplot as plt

# Define a mapping for activity type substitution
ACTIVITY_CODE_MAPPING = {
    'home': 'H',
    'work': 'W',
    'outside': 'OU',
    'other': 'O',
    'leisure': 'L',
    'education': 'E',
    'freight_loading': 'FL',
    'freight_unloading': 'FU'
}

def extract_population_data(xml_content):
    tree = ET.ElementTree(ET.fromstring(xml_content))
    root = tree.getroot()

    person_ids = set()
    trip_type_distribution = Counter()
    activity_chain_distribution = Counter()

    for person in root.iter('person'):
        person_id = person.get('id')
        person_ids.add(person_id)

        activities = []
        for activity in person.iter('activity'):
            activity_type = activity.get('type')
            if activity_type != 'pt interaction':  # Exclude 'pt interaction'
                # Substitute activity types
                activities.append(ACTIVITY_CODE_MAPPING.get(activity_type, activity_type))

        activity_chain = '-'.join(activities)
        activity_chain_distribution[activity_chain] += 1

        for leg in person.iter('leg'):
            trip_type_distribution[leg.get('mode')] += 1

    return person_ids, trip_type_distribution, activity_chain_distribution

def process_gz_xml_file(filepath):
    with gzip.open(filepath, 'rt', encoding='utf-8') as f:
        xml_content = f.read()

    person_ids, trip_type_distribution, activity_chain_distribution = extract_population_data(xml_content)

    # Generate a bar graph for the activity chain distribution
    activity_chains = sorted(activity_chain_distribution.items(), key=lambda x: x[1], reverse=True)
    chains, counts = zip(*activity_chains)

    plt.figure(figsize=(10, 8))
    plt.bar(chains, counts, color='skyblue')
    plt.xlabel('Activity Chains')
    plt.ylabel('Frequency')
    plt.title('Distribution of Activity Chains')
    plt.xticks(rotation=90)  # Rotate x-axis labels to show them better
    plt.tight_layout()  # Adjust layout to fit all x-axis labels
    plt.show()

process_gz_xml_file('C:/Users/muaa/Documents/3_MIEI/2023_ABMT_Data/Zurich/10pct/zurich_population_10pct.xml.gz')

