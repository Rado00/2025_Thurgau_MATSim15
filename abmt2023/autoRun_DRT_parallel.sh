#!/bin/bash

# Get the current operating system and user name
OS_TYPE=$(uname)
USER_NAME=$(whoami)

# MAVEN PATH
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "cmuratori" ]]; then
    MAVEN_PATH="/cluster/home/cmuratori/2023_ABMT_Corrado/abmt2023/"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    MAVEN_PATH="/home/muaa/2023_ABMT_Corrado/abmt2023/"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    MAVEN_PATH="/home/comura/2023_ABMT_Corrado/abmt2023/"
    # Set Maven options for memory management
    export MAVEN_OPTS="-Xmx2G -XX:MaxMetaspaceSize=512M"
    source ~/use-java11.sh
elif [[ "$OS_TYPE" == "MINGW"* || "$OS_TYPE" == "CYGWIN"* || "$OS_TYPE" == "MSYS"* ]] && [[ "$USER_NAME" == "muaa" ]]; then
    MAVEN_PATH="C:/Users/${USER_NAME}/Documents/3_MIEI/2023_ABMT_Corrado_Muratori/abmt2023"
else
    echo "Unsupported system configuration"
    exit 1
fi

echo "Maven folder is set to: $MAVEN_PATH"

# DATA PATH
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "cmuratori" ]]; then
    DATA_PATH="/cluster/scratch/cmuratori/data/scenarios" 
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    # DATA_PATH="/home/muaa/Zurich_Scenarios_ABM_2023"
    DATA_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario/1pct"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    # DATA_PATH="/home/comura/data/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario/100pct"
    DATA_PATH="/home/comura/data/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario/1pct"

elif [[ "$OS_TYPE" == "MINGW"* || "$OS_TYPE" == "CYGWIN"* || "$OS_TYPE" == "MSYS"* ]] && [[ "$USER_NAME" == "muaa" ]]; then
    DATA_PATH="C:/Users/${USER_NAME}/Documents/3_MIEI/2023_ABMT_Data/Zurich"
else
    echo "Unsupported system configuration"
    exit 1
fi

echo "Data folder is set to: $DATA_PATH"


# Navigate to Maven repository
cd "$MAVEN_PATH"

# Package the Maven project
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "cmuratori" ]]; then
    mvn -Pstandalone package
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    mvn -Pstandalone package
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    /home/comura/apache-maven-3.9.6/apache-maven-3.9.6/bin/mvn -Pstandalone package
else
    echo "Unsupported system configuration"
    exit 1
fi

# Set OUTPUT_DIRECTORY_PATH
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "cmuratori" ]]; then
    OUTPUT_DIRECTORY_PATH="$DATA_PATH/Paper2_Outputs/1_ModalSplitCalibration"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    OUTPUT_DIRECTORY_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Paper2_SimsOutputs/2_FleetSizeCalibration"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    OUTPUT_DIRECTORY_PATH="/home/comura/data/2024_Paper2_Data/MATSim_Thurgau/2024_Paper2_SimsOutputs/2_FleetSizeCalibration"
else
    echo "Unsupported system configuration"
    exit 1
fi

# Define DRT Vehicles and Shape File Paths
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "cmuratori" ]]; then
    OUTPUT_DIRECTORY_PATH="$DATA_PATH/Paper2_Outputs/1_ModalSplitCalibration"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    DRT_VEHICLES_PATH="/home/muaa/2023_ABMT_Corrado/abmt2023/src/main/create_vehicle_xml/01_Fleet_files/01_drt_8_105.xml"
    DRT_SHAPE_FILE_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/Paper2_ShapeFiles_CH1903+_LV95/01_Weinfelden_Affeltrangen/01_Weinfelden_Affeltrangen.shp"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    DRT_VEHICLES_PATH="/home/comura/2023_ABMT_Corrado/abmt2023/src/main/create_vehicle_xml/01_Fleet_files/01_drt_8_105.xml"
    DRT_SHAPE_FILE_PATH="/home/comura/data/2024_Paper2_Data/Paper2_ShapeFiles_CH1903+_LV95/01_Weinfelden_Affeltrangen/01_Weinfelden_Affeltrangen.shp"
else
    echo "Unsupported system configuration"
    exit 1
fi



# Extract file names (without extension)
FLEET_FILENAME=$(basename "$DRT_VEHICLES_PATH" .xml)
SHAPE_FILENAME=$(basename "$DRT_SHAPE_FILE_PATH" .shp)

echo "$FLEET_FILENAME"
echo "$SHAPE_FILENAME"

# Generate unique config file name
CONFIG_FILE_PATH="$DATA_PATH/Thurgau_config_${FLEET_FILENAME}.xml"
if [[ ! -f "$DATA_PATH/Thurgau_config_DRT_03_queue.xml" ]]; then
    echo "ERROR: Base config file missing!"
    exit 1
fi

# Substitute variables into the new config file
sed -e "s|\${DRT_VEHICLES_PATH}|$DRT_VEHICLES_PATH|g" \
    -e "s|\${DRT_SHAPE_FILE_PATH}|$DRT_SHAPE_FILE_PATH|g" \
    "$DATA_PATH/Thurgau_config_DRT_03_queue.xml" > "$CONFIG_FILE_PATH"

# DEBUG
if [[ ! -f "$CONFIG_FILE_PATH" ]]; then
    echo "ERROR: Config file was not created."
    exit 1
fi
echo "Created config file: $CONFIG_FILE_PATH"

# Generate unique JAR file name
JAR_FILE="abmt2024-${FLEET_FILENAME}.jar"
cp "$MAVEN_PATH/target/abmt2023-0.0.1-SNAPSHOT.jar" "$DATA_PATH/$JAR_FILE"

# Navigate to the scenario directory
cd "$DATA_PATH"

if [[ ! -f "$CONFIG_FILE_PATH" ]]; then
    echo "ERROR: Config file does not exist at $CONFIG_FILE_PATH"
    exit 1
fi

# DEBUG
echo "Submitting job with config file: ${CONFIG_FILE_PATH}"
ls -l "${CONFIG_FILE_PATH}"  # Ensure the file exists
echo "Java command: java -Xmx128G -cp ${JAR_FILE} abmt2023.project.mode_choice.RunSimulation_DRT --config-path ${CONFIG_FILE_PATH} --output-directory ${OUTPUT_DIRECTORY_PATH} --output-sim-name Prova_DRT_${FLEET_FILENAME}"

    # Submit the job
    sbatch -n 1 \
    --cpus-per-task=4 \
    --time=100:00:00 \
    --job-name="abmt2024_${FLEET_FILENAME}" \
    --mem-per-cpu=64G \
    --mail-type=END,FAIL --mail-user=muaa@zhaw.ch \
    --wrap="/bin/bash -c 'java -Xmx128G -cp ${JAR_FILE} abmt2023.project.mode_choice.RunSimulation_DRT --config-path ${CONFIG_FILE_PATH} --output-directory ${OUTPUT_DIRECTORY_PATH} --output-sim-name Prova_DRT_${FLEET_FILENAME}'"
    # --wrap="java -Xmx128G -cp ${JAR_FILE} abmt2023.project.mode_choice.RunSimulation_DRT --config-path ${CONFIG_FILE_PATH} --output-directory ${OUTPUT_DIRECTORY_PATH} --output-sim-name Prova_DRT_${FLEET_FILENAME}"

# DEBUG
if [[ $? -ne 0 ]]; then
    echo "ERROR: Job submission failed."
    exit 1
fi

echo "Simulation submitted for DRT fleet: $FLEET_FILENAME"