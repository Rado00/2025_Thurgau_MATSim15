#!/bin/bash

# Get the current operating system and user name
OS_TYPE=$(uname)
USER_NAME=$(whoami)

# MAVEN PATH
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "cmuratori" ]]; then
    MAVEN_PATH="/cluster/home/cmuratori/2025_Thurgau_MATSim15/abmt2025/"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
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
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "cmuratori" ]]; then
    DATA_PATH="/cluster/scratch/cmuratori/data/scenarios" 
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    # DATA_PATH="/home/muaa/Zurich_Scenarios_ABM_2025"
    # DATA_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario/1pct"
    DATA_PATH= "/home/muaa/DATA_ABM/Thurgau/Thurgau_Scenario/1pct"
    # DATA_PATH="/home/muaa/DATA_ABM/Thurgau/Thurgau_Scenario/100pct"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    # DATA_PATH="/home/comura/data/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario/100pct"
    DATA_PATH="/home/comura/data/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario/100pct"
elif [[ "$OS_TYPE" == "MINGW"* || "$OS_TYPE" == "CYGWIN"* || "$OS_TYPE" == "MSYS"* ]] && [[ "$USER_NAME" == "muaa" ]]; then
    DATA_PATH="C:/Users/${USER_NAME}/Documents/3_MIEI/2025_ABMT_Data/Zurich"
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
    # OUTPUT_DIRECTORY_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Paper2_SimsOutputs/2_FleetSizeCalibration"
    OUTPUT_DIRECTORY_PATH="/home/muaa/DATA_ABM/Thurgau/Thurgau_Scenario/100pct"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    OUTPUT_DIRECTORY_PATH="/home/comura/data/2024_Paper2_Data/MATSim_Thurgau/2024_Paper2_SimsOutputs/2_FleetSizeCalibration"
else
    echo "Unsupported system configuration"
    exit 1
fi

CONFIG_FILE_PATH="/home/muaa/DATA_ABM/Thurgau/Thurgau_Scenario/100pct/Thurgau_config_base_02_DRT.xml"

# CONFIG_FILE_PATH="/home/muaa/DATA_ABM/Thurgau/Thurgau_Scenario/1pct/Thurgau_config_01_drt_8_105.xml"


# Generate unique JAR file name
JAR_FILE="abmt2024-prova.jar"
cp "$MAVEN_PATH/target/abmt2025-0.0.1-SNAPSHOT.jar" "$DATA_PATH/$JAR_FILE"

# Navigate to the scenario directory
cd "$DATA_PATH"

if [[ ! -f "$CONFIG_FILE_PATH" ]]; then
    echo "ERROR: Config file does not exist at $CONFIG_FILE_PATH"
    exit 1
fi

# DEBUG
echo "Submitting job with config file: ${CONFIG_FILE_PATH}"
ls -l "${CONFIG_FILE_PATH}"  # Ensure the file exists
echo "Java command: java -Xmx128G -cp ${JAR_FILE} abmt2025.project.mode_choice.RunSimulation_DRT --config-path ${CONFIG_FILE_PATH} --output-directory ${OUTPUT_DIRECTORY_PATH} --output-sim-name Prova_DRT_prova"

    # Submit the job
    sbatch -n 1 \
    --cpus-per-task=12 \
    --time=100:00:00 \
    --job-name="abmt2024_Prova" \
    --mem-per-cpu=10G \
    --mail-type=END,FAIL --mail-user=muaa@zhaw.ch \
    --wrap="java -Xmx128G -cp ${JAR_FILE} abmt2025.project.mode_choice.RunSimulation_DRT --config-path ${CONFIG_FILE_PATH} --output-directory ${OUTPUT_DIRECTORY_PATH} --output-sim-name Prova_DRT_$Prova"

    # GPT version
    # --wrap="/bin/bash -c 'java -Xmx128G -cp ${JAR_FILE} abmt2025.project.mode_choice.RunSimulation_DRT --config-path ${CONFIG_FILE_PATH} --output-directory ${OUTPUT_DIRECTORY_PATH} --output-sim-name Prova_DRT_${FLEET_FILENAME}'"

# DEBUG
if [[ $? -ne 0 ]]; then
    echo "ERROR: Job submission failed."
    exit 1
fi

echo "Simulation submitted for DRT fleet: $FLEET_FILENAME"