def plot_shapefile(file_path):
    import geopandas as gpd
    import plotly.express as px


    gdf = gpd.read_file(file_path)

    # Step 2: Check and convert the coordinate reference system to EPSG:4326 if it's not
    if gdf.crs != "EPSG:4326":
        gdf = gdf.to_crs("EPSG:4326")

    # Step 3: Create a unique identifier for each feature/row, this will be used for coloring
    gdf['id'] = gdf.index.astype(str)  # Here we are just using the index, but you can use any unique column

    # Step 4: Create the plot with Plotly
    fig = px.choropleth_mapbox(gdf, 
                                geojson=gdf.geometry,  # the column with geometrical data
                                locations='id',  # column with identifiers
                                hover_name='id',  # column to add to hover information
                                title='Zurich Districts',
                                mapbox_style="open-street-map",
                                center={"lat": gdf.geometry.centroid.y.mean(), "lon": gdf.geometry.centroid.x.mean()},
                                zoom=10,  # you might want to adjust this based on your specific shapefile
                                opacity=0.5,  # adjust for plot aesthetics
                                )

    fig.update_layout(margin={"r":0,"t":0,"l":0,"b":0})
    fig.show()

def decompress_gz_file(input_file_path: str, output_file_path: str):
    """
    Decompress a gzipped file.

    Parameters:
    input_file_path (str): Path to the gzipped file.
    output_file_path (str): Path to save the decompressed file.
    """
    with gzip.open(input_file_path, 'rb') as compressed_file:
        with open(output_file_path, 'wb') as decompressed_file:
            shutil.copyfileobj(compressed_file, decompressed_file)
            print(f"Decompressed file saved at: {output_file_path}")