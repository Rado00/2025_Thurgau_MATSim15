#!/bin/bash

# Get the current operating system and user name
OS_TYPE=$(uname)
USER_NAME=$(whoami)


########################## CHECK AUTORUN SETTING ###########################################

LAST_ITERATION=4 # Set number of iterations dynamically (can also do: LAST_ITERATION=$1)
DRT_CONFIG="Thurgau_config_DRT_M15_06.xml"

RUN_ANALYSIS=false
CLEAN_ITERATIONS=true
DELETE_EVENTS_FILE=true

BASELINE_PCT="1pct"


SIM_ID="TESTclean" # CHANGE TO RUN PARALLEL SIMS WITH DIFFERENT SETTINGS
FLEET_FILE="10_drt_1_8.xml"
SHAPE_FILE="10_ShapeFile.shp"



########################## PATHS ###########################################

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
    DATA_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario/${BASELINE_PCT}"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    DATA_PATH="/home/comura/data/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario/${BASELINE_PCT}"
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


# Define DRT Vehicles and Shape File Paths
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    DRT_VEHICLES_PATH="/home/muaa/2025_Thurgau_MATSim15/abmt2025/src/main/create_vehicle_xml/01_Fleet_files/${FLEET_FILE}"
    DRT_SHAPE_FILE_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/Paper2_ShapeFiles_CH1903+_LV95_easyNames/${SHAPE_FILE}"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    DRT_VEHICLES_PATH="/home/comura/2025_Thurgau_MATSim15/abmt2025/src/main/create_vehicle_xml/01_Fleet_files/${FLEET_FILE}"
    DRT_SHAPE_FILE_PATH="/home/comura/data/2024_Paper2_Data/Paper2_ShapeFiles_CH1903+_LV95_easyNames/${SHAPE_FILE}"
else
    echo "Unsupported system configuration"
    exit 1
fi

# Extract filenames for substitution
FLEET_FILENAME=$(basename "$DRT_VEHICLES_PATH" .xml)
SHAPE_FILENAME=$(basename "$DRT_SHAPE_FILE_PATH" .shp)
echo "$FLEET_FILENAME"
echo "$SHAPE_FILENAME"
OUTPUT_SIM_NAME=DRT_${SHAPE_FILENAME}_${FLEET_FILENAME}_${SIM_ID}


# Set OUTPUT_DIRECTORY_PATH
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    OUTPUT_DIRECTORY_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Paper2_SimsOutputs/2_FleetSizeCalibration"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    OUTPUT_DIRECTORY_PATH="/home/comura/data/2024_Paper2_Data/MATSim_Thurgau/Paper2_SimsOutputs/2_FleetSizeCalibration"
else
    echo "Unsupported system configuration"
    exit 1
fi

# Extract relative path automatically (everything after "MATSim_Thurgau/")
RELATIVE_OUTPUT_PATH="${OUTPUT_DIRECTORY_PATH#*MATSim_Thurgau/}"

# PYTHON ANALYSIS PATH
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    ANALYSIS_SCRIPT="/home/comura/ThurgauPaperAnalysisAM/scripts/run_all_scripts.sh"
    CONFIG_INI_PATH="/home/comura/ThurgauPaperAnalysisAM/config/config.ini"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    ANALYSIS_SCRIPT="/home/muaa/ThurgauPaperAnalysisAM/scripts/run_all_scripts.sh"
    CONFIG_INI_PATH="/home/muaa/ThurgauPaperAnalysisAM/config/config.ini"
else
    echo "Unsupported system configuration for analysis script/config path"
    exit 1
fi

########################## CREATE UNIQUE CONFIG AND JAR ###########################################

# Define the path for the new temporary config file
CONFIG_FILE_PATH="$DATA_PATH/Thurgau_config_${FLEET_FILENAME}.xml"

# Create config by replacing LAST_ITERATION placeholder in the template
sed -e "s|\${LAST_ITERATION}|$LAST_ITERATION|g" -e "s|\${DRT_VEHICLES_PATH}|$DRT_VEHICLES_PATH|g" -e "s|\${DRT_SHAPE_FILE_PATH}|$DRT_SHAPE_FILE_PATH|g" \
    "$DATA_PATH/${DRT_CONFIG}" > "$CONFIG_FILE_PATH" || { echo "Config file creation failed"; exit 1; }

echo "Created config file with $LAST_ITERATION iterations: $CONFIG_FILE_PATH"

# USE YOUR JAR NAME IN THE FIRST STRING. CHANGE THE NUMBER OF THE SECOND STRING IF RUNNING SIMULATIONS IN PARALLEL 
cp "$MAVEN_PATH/target/abmt2025-1.0-SNAPSHOT.jar" "$DATA_PATH/abmt2025-DRT${SIM_ID}.jar" 

# Navigate to the scenario directory
cd "$DATA_PATH"


########################## SUBMIT THE JOB ###########################################
# SENDS SIMS
# CLEAN_ITERATIONS: Progressive waiting strategy to safely delete intermediate iteration folders
# Phase 1 (T=0-90s): Initial wait after simulation completion
# Phase 2 (T=90s): First check - files modified in last minute?
# Phase 3 (T=90-210s): Wait 2 more minutes if files still being modified
# Phase 4 (T=210s): Second check - files still being modified?
# Phase 5 (T=210-390s): Wait 3 more minutes as final safety buffer
# Phase 6: Delete all iteration folders except the last one
# Phase 7 Delete large event files if DELETE_EVENTS_FILE=true - non funziona se il nome SIM SIM_ID${1}

sbatch -n 1 \
    --cpus-per-task=4 \
    --time=100:00:00 \
    --job-name="${SIM_ID}" \
    --mem-per-cpu=64G \
    --mail-type=END,FAIL \
    --mail-user=muaa@zhaw.ch \
    --wrap=" \
    java -Xmx128G -cp abmt2025-DRT${SIM_ID}.jar abmt2025.project.mode_choice.RunSimulation_DRT \
    --config-path $CONFIG_FILE_PATH \
    --output-directory $OUTPUT_DIRECTORY_PATH \
    --output-sim-name $OUTPUT_SIM_NAME \
    $(if $CLEAN_ITERATIONS; then echo "&& echo '[CLEANUP] Waiting 90 seconds before first check...' && sleep 90 && (find $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME -type f -mmin -1 2>/dev/null | grep -q . && echo '[CLEANUP] Files modified in last minute detected. Waiting 2 more minutes...' && sleep 120 && (find $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME -type f -mmin -1 2>/dev/null | grep -q . && echo '[CLEANUP] Files still being modified. Waiting 3 more minutes for safety...' && sleep 180 && echo '[CLEANUP] Final wait complete. Proceeding with deletion.' || echo '[CLEANUP] No recent modifications detected at second check. Proceeding with deletion.') || echo '[CLEANUP] No recent modifications detected at first check. Proceeding with deletion.') && echo '[CLEANUP] Deleting iteration folders (keeping only iteration $LAST_ITERATION)...' && for i in \$(seq 0 $((LAST_ITERATION - 1))); do echo \"[CLEANUP] Removing iteration \$i...\" && rm -rf $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME/ITERS/it.\$i; done && echo '[CLEANUP] Iteration folders deleted.' $(if $DELETE_EVENTS_FILE; then echo "&& echo '[CLEANUP] DELETE_EVENTS_FILE=true, removing large event files...' && [ -f $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME/ITERS/it.$LAST_ITERATION/$LAST_ITERATION.events.xml.gz ] && echo '[CLEANUP] Deleting it.$LAST_ITERATION/$LAST_ITERATION.events.xml.gz...' && rm -f $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME/ITERS/it.$LAST_ITERATION/$LAST_ITERATION.events.xml.gz || echo '[CLEANUP] File it.$LAST_ITERATION/$LAST_ITERATION.events.xml.gz not found, skipping.' && [ -f $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME/output_events.xml.gz ] && echo '[CLEANUP] Deleting output_events.xml.gz...' && rm -f $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME/output_events.xml.gz || echo '[CLEANUP] File output_events.xml.gz not found, skipping.'"; else echo "&& echo '[CLEANUP] DELETE_EVENTS_FILE=false, keeping event files.'"; fi) && echo '[CLEANUP] Cleanup complete!'"; fi) \
    && sed -i 's|^sim_output_folder *=.*|sim_output_folder = $RELATIVE_OUTPUT_PATH/$OUTPUT_SIM_NAME|' $CONFIG_INI_PATH \
    $(if $RUN_ANALYSIS; then echo "&& bash $ANALYSIS_SCRIPT"; fi)
    "

echo "Simulation submitted"