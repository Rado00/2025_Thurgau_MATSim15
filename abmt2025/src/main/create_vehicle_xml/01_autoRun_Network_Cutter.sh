#!/bin/bash

# Get the current operating system and user name
OS_TYPE=$(uname)
USER_NAME=$(whoami)

########################## CHECK PATHS ###########################################

# MAVEN PATH
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    MAVEN_PATH="/home/muaa/2025_Thurgau_MATSim15/abmt2025/"
    source ~/use-java17.sh 
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    MAVEN_PATH="/home/comura/2025_Thurgau_MATSim15/abmt2025/"
    # Set Maven options for memory management
    export MAVEN_OPTS="-Xmx2G -XX:MaxMetaspaceSize=512M"
    source ~/use-java17.sh 
elif [[ "$OS_TYPE" == "MINGW"* || "$OS_TYPE" == "CYGWIN"* || "$OS_TYPE" == "MSYS"* ]] && [[ "$USER_NAME" == "muaa" ]]; then
    MAVEN_PATH="C:/Users/${USER_NAME}/Documents/3_MIEI/2025_Thurgau_MATSim15_Muratori/abmt2025"
else
    echo "Unsupported system configuration"
    exit 1
fi

echo "Maven folder is set to: $MAVEN_PATH"

# DATA PATH
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    DATA_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    DATA_PATH="/home/comura/data/2024_Paper2_Data"
elif [[ "$OS_TYPE" == "MINGW"* || "$OS_TYPE" == "CYGWIN"* || "$OS_TYPE" == "MSYS"* ]] && [[ "$USER_NAME" == "muaa" ]]; then
    DATA_PATH="C:/Users/${USER_NAME}/Documents/3_MIEI/2025_ABMT_Data/Zurich"
else
    echo "Unsupported system configuration"
    exit 1
fi

echo "Data folder is set to: $DATA_PATH"

# Navigate to Maven repository
cd "$MAVEN_PATH" || { echo "Maven path not found"; exit 1; }

# Package the Maven project
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    mvn package || { echo "Maven build failed"; exit 1; }
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    /home/comura/apache-maven-3.9.6/apache-maven-3.9.6/bin/mvn package || { echo "Maven build failed"; exit 1; }
else
    echo "Unsupported system configuration"
    exit 1
fi

# Set paths
SHAPEFILE_PATH="$DATA_PATH/Paper2_ShapeFiles_CH1903+_LV95_easyNames/10_ShapeFile.shp"
INPUT_NETWORK="$DATA_PATH/MATSim_Thurgau/Baseline_Scenario/100pct/network.xml.gz"
OUTPUT_NETWORK="$MAVEN_PATH/src/main/create_vehicle_xml/small_networks/10_network.xml.gz"

# Compile the Java clipping tool
javac -cp "$MAVEN_PATH/target/abmt2025-1.0-SNAPSHOT.jar" -d "$MAVEN_PATH/target" "$MAVEN_PATH/src/main/java/abmt2025/project/utils/cutNetwork.java" || { echo "Compilation failed"; exit 1; }

# Run the clipping tool
java -cp "$MAVEN_PATH/target/abmt2025-1.0-SNAPSHOT.jar:$MAVEN_PATH/target" abmt2025.project.utils.cutNetwork "$INPUT_NETWORK" "$SHAPEFILE_PATH" "$OUTPUT_NETWORK" || { echo "Java clipping failed"; exit 1; }

echo "Clipped network written to: $OUTPUT_NETWORK"


echo "Cut Network"