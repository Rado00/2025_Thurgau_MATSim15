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
    DATA_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario/1pct"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    # DATA_PATH="/home/comura/data/DATA_ABM/Weinfelden/WeinfeldenScenario"
    # DATA_PATH="/home/comura/data/DATA_ABM/Frauenfeld/FrauenfeldScenario"
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
cd "$MAVEN_PATH" || { echo "Maven path not found"; exit 1; }

# Package the Maven project
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "cmuratori" ]]; then
    mvn package || { echo "Maven build failed"; exit 1; }
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    mvn package || { echo "Maven build failed"; exit 1; }
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    /home/comura/apache-maven-3.9.6/apache-maven-3.9.6/bin/mvn package || { echo "Maven build failed"; exit 1; }
else
    echo "Unsupported system configuration"
    exit 1
fi

# Set OUTPUT_DIRECTORY_PATH
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "cmuratori" ]]; then
    OUTPUT_DIRECTORY_PATH="$DATA_PATH/Paper2_Outputs/1_ModalSplitCalibration"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    OUTPUT_DIRECTORY_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Paper2_SimsOutputs/1_ModalSplitCalibration"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    OUTPUT_DIRECTORY_PATH="/home/comura/data/2024_Paper2_Data/MATSim_Thurgau/2024_Paper2_SimsOutputs/1_ModalSplitCalibration"
else
    echo "Unsupported system configuration"
    exit 1
fi

# Create a unique config file for this run by replacing placeholders
CONFIG_FILE_PATH="$DATA_PATH/Thurgau_config_base.xml" # DON'T CHANGE WITH YOUR CONFIG NAME

# USE YOUR ACTUAL CONFIG FILE NAME
cp "$DATA_PATH/Thurgau_config_base_M15_03.xml" "$CONFIG_FILE_PATH" || { echo "Config file creation failed"; exit 1; }

echo "Running simulation"

# TO RUN PARALLEL SIMS AND CHANGE OUTPUT FOLDER
SIM_ID="try"

# USE YOUR JAR NAME IN THE FIRST STRING. CHANGE THE NUMBER OF THE SECOND STRING IF RUNNING SIMULATIONS IN PARALLEL 
cp "$MAVEN_PATH/target/abmt2025-1.0-SNAPSHOT.jar" "$DATA_PATH/abmt2025-Baseline${SIM_ID}.jar" 

# Navigate to the scenario directory
cd "$DATA_PATH"

# Submit the job
sbatch -n 1 \
    --cpus-per-task=4 \
    --time=100:00:00 \
    --job-name="${SIM_ID}" \
    --mem-per-cpu=64G \
    --mail-type=END,FAIL \
    --mail-user=muaa@zhaw.ch \
    --wrap="java -Xmx128G -cp abmt2025-Baseline${SIM_ID}.jar abmt2025.project.mode_choice.RunSimulation_Baseline --config-path $CONFIG_FILE_PATH --output-directory $OUTPUT_DIRECTORY_PATH --output-sim-name BaselineCalibration${SIM_ID} && for i in \$(seq 0 59); do rm -rf $OUTPUT_DIRECTORY_PATH/BaselineCalibration${SIM_ID}/ITERS/it.\$i; done"
echo "Simulation submitted"
