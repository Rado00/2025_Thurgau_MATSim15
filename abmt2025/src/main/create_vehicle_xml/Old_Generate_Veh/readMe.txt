1) Run create_network_links.py
	It generates a csv with all the links in it

2) Run create_network_dict.py 
	generates a dictionary of all links. Generates a Json file

3) Run read_rail_links_txt_file.py
	It EXCLUDES links with railways. Generates a Json file
	
4) Change Config file with:
	[DEFAULT]
	number_of_vehicles = 105
	t_0 = 0.0						Starting time (seconds)
	t_1 = 86400.0					Endiing time (seconds)
	capacity = 8
	file_name = 01_drt_8_105.xml	
	zone_name = c01					See structure of shape files in the folder

5) Run generate_vehicles_xml.py
