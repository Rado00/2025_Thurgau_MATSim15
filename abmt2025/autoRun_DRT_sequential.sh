#!/bin/bash

# Get the current operating system and user name
OS_TYPE=$(uname)
USER_NAME=$(whoami)


########################## CHECK AUTORUN SETTING ###########################################

LAST_ITERATION=4
DRT_CONFIG="Thurgau_config_DRT_M15_10.xml"

RUN_ANALYSIS=true
CLEAN_ITERATIONS=true
DELETE_EVENTS_FILE=true

BASELINE_PCT="1pct"

########################## SEQUENTIAL SIMULATION ARRAYS ###########################################
# Define arrays for SIM_ID, FLEET_FILE, SHAPE_FILE
# Position i in each array defines one simulation run.
# Simulations are submitted one after another (each waits for the previous to finish).

SIM_IDS=(
    "Test_seq_01"
    "Test_seq_02"
    "Test_seq_03"
)

FLEET_FILES=(
    "25_drt_20_8.xml"
    "25_drt_59_8.xml"
    "25_drt_119_8.xml"
)

SHAPE_FILES=(
    "25_ShapeFile.shp"
    "25_ShapeFile.shp"
    "25_ShapeFile.shp"
)

########################## DRT PARAMETERS (for swissRail_08 and 10 config) ###########################################
# DRT Fare (passed to Java as system properties)
DRT_FARE_CHF="10"           # Fixed constant price DRT (CHF)
DRT_FARE_CHF_KM="0"        # Per-km price DRT (CHF/km)

# DRT Operational Constraints (substituted in config XML)
REJECT_IF_CONSTRAINTS_VIOLATED="true"   # true = hard constraints, false = soft constraints
MAX_WAIT_TIME="1800.0"                    # Max wait time in seconds
MAX_TRAVEL_TIME_ALPHA="3"                # maxTravelTime = alpha * unsharedRideTime + beta
MAX_TRAVEL_TIME_BETA="450.0"             # maxTravelTime shift in seconds

# Modal Split Calibration (passed to Java as system properties)
ALPHA_WALK="2.5"
ALPHA_BIKE="2.3"
ALPHA_PT="0"
ALPHA_CAR="1.7"
BETA_CAR_CITY="-0.459"


########################## PATHS ###########################################

# MAVEN PATH
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    MAVEN_PATH="/home/muaa/2025_Thurgau_MATSim15/abmt2025/"
    source ~/use-java17.sh
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    MAVEN_PATH="/home/comura/2025_Thurgau_MATSim15/abmt2025/"
    source ~/use-java17.sh
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "gsangiovanni" ]]; then
    MAVEN_PATH="/lustre/home/gsangiovanni/Rado/2025_Thurgau_MATSim15/abmt2025/"
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
    DATA_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario_Fixed/${BASELINE_PCT}"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    DATA_PATH="/home/comura/data/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario_Fixed/${BASELINE_PCT}"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "gsangiovanni" ]]; then
    DATA_PATH="/lustre/home/gsangiovanni/Rado/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario_Fixed/${BASELINE_PCT}"
elif [[ "$OS_TYPE" == "MINGW"* || "$OS_TYPE" == "CYGWIN"* || "$OS_TYPE" == "MSYS"* ]] && [[ "$USER_NAME" == "muaa" ]]; then
    DATA_PATH="C:/Users/${USER_NAME}/Documents/3_MIEI/2025_ABMT_Data/Zurich"
else
    echo "Unsupported system configuration"
    exit 1
fi

echo "Data folder is set to: $DATA_PATH"

# Navigate to Maven repository
cd "$MAVEN_PATH" || { echo "Maven path not found"; exit 1; }

# Package the Maven project (SKIP for comura - will build inside sbatch job)
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    mvn package || { echo "Maven build failed"; exit 1; }
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    echo "[INFO] Skipping Maven build on login node (will build inside sbatch job)"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "gsangiovanni" ]]; then
    mvn package || { echo "Maven build failed"; exit 1; }
else
    echo "Unsupported system configuration"
    exit 1
fi

# Set OUTPUT_DIRECTORY_PATH
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    OUTPUT_DIRECTORY_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Paper2_SimsOutputs/1_ModalSplitCalibration"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    OUTPUT_DIRECTORY_PATH="/home/comura/data/2024_Paper2_Data/MATSim_Thurgau/Paper2_SimsOutputs/1_ModalSplitCalibration"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "gsangiovanni" ]]; then
    OUTPUT_DIRECTORY_PATH="/lustre/home/gsangiovanni/Rado/2024_Paper2_Data/MATSim_Thurgau/Paper2_SimsOutputs/1_ModalSplitCalibration"
else
    echo "Unsupported system configuration"
    exit 1
fi

# PYTHON ANALYSIS PATH
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    ANALYSIS_SCRIPT="/home/comura/ThurgauPaperAnalysisAM/scripts/run_all_scripts.sh"
    CONFIG_INI_PATH="/home/comura/ThurgauPaperAnalysisAM/config/config.ini"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    ANALYSIS_SCRIPT="/home/muaa/ThurgauPaperAnalysisAM/scripts/run_all_scripts.sh"
    CONFIG_INI_PATH="/home/muaa/ThurgauPaperAnalysisAM/config/config.ini"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "gsangiovanni" ]]; then
    ANALYSIS_SCRIPT="/lustre/home/gsangiovanni/Rado/ThurgauPaperAnalysisAM/scripts/run_all_scripts.sh"
    CONFIG_INI_PATH="/lustre/home/gsangiovanni/Rado/ThurgauPaperAnalysisAM/config/config.ini"
else
    echo "Unsupported system configuration for analysis script/config path"
    exit 1
fi


########################## SEQUENTIAL SUBMISSION LOOP ###########################################

PREV_JOB_ID=""  # Will hold the job ID of the previous submission for dependency chaining

NUM_SIMS=${#SIM_IDS[@]}
echo "==========================================="
echo "Submitting $NUM_SIMS simulations sequentially"
echo "==========================================="

for (( i=0; i<NUM_SIMS; i++ )); do

    SIM_ID="${SIM_IDS[$i]}"
    FLEET_FILE="${FLEET_FILES[$i]}"
    SHAPE_FILE="${SHAPE_FILES[$i]}"

    echo ""
    echo "-------------------------------------------"
    echo "Preparing simulation $((i+1))/$NUM_SIMS: $SIM_ID"
    echo "  Fleet file : $FLEET_FILE"
    echo "  Shape file : $SHAPE_FILE"
    echo "-------------------------------------------"

    # Define DRT Vehicles and Shape File Paths
    if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
        DRT_VEHICLES_PATH="/home/muaa/2025_Thurgau_MATSim15/abmt2025/src/main/create_vehicle_xml/01_Fleet_files/${FLEET_FILE}"
        DRT_SHAPE_FILE_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/Paper2_ShapeFiles_CH1903+_LV95_easyNames/${SHAPE_FILE}"
    elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
        DRT_VEHICLES_PATH="/home/comura/2025_Thurgau_MATSim15/abmt2025/src/main/create_vehicle_xml/01_Fleet_files/${FLEET_FILE}"
        DRT_SHAPE_FILE_PATH="/home/comura/data/2024_Paper2_Data/Paper2_ShapeFiles_CH1903+_LV95_easyNames/${SHAPE_FILE}"
    elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "gsangiovanni" ]]; then
        DRT_VEHICLES_PATH="/lustre/home/gsangiovanni/Rado/2025_Thurgau_MATSim15/abmt2025/src/main/create_vehicle_xml/01_Fleet_files/${FLEET_FILE}"
        DRT_SHAPE_FILE_PATH="/lustre/home/gsangiovanni/Rado/2024_Paper2_Data/Paper2_ShapeFiles_CH1903+_LV95_easyNames/${SHAPE_FILE}"
    else
        echo "Unsupported system configuration"
        exit 1
    fi

    # Extract filenames for substitution
    FLEET_FILENAME=$(basename "$DRT_VEHICLES_PATH" .xml)
    SHAPE_FILENAME=$(basename "$DRT_SHAPE_FILE_PATH" .shp)
    echo "  Fleet name : $FLEET_FILENAME"
    echo "  Shape name : $SHAPE_FILENAME"

    OUTPUT_SIM_NAME=DRT_${SHAPE_FILENAME}_${FLEET_FILENAME}_${SIM_ID}

    # Extract relative path automatically (everything after "MATSim_Thurgau/")
    RELATIVE_OUTPUT_PATH="${OUTPUT_DIRECTORY_PATH#*MATSim_Thurgau/}"

    # Define the path for the new temporary config file
    CONFIG_FILE_PATH="$DATA_PATH/Thurgau_config_${FLEET_FILENAME}.xml"

    # Create config by replacing placeholders in the template
    sed -e "s|\${LAST_ITERATION}|$LAST_ITERATION|g" \
        -e "s|\${DRT_VEHICLES_PATH}|$DRT_VEHICLES_PATH|g" \
        -e "s|\${DRT_SHAPE_FILE_PATH}|$DRT_SHAPE_FILE_PATH|g" \
        -e "s|\${MAX_WAIT_TIME}|$MAX_WAIT_TIME|g" \
        -e "s|\${MAX_TRAVEL_TIME_ALPHA}|$MAX_TRAVEL_TIME_ALPHA|g" \
        -e "s|\${MAX_TRAVEL_TIME_BETA}|$MAX_TRAVEL_TIME_BETA|g" \
        -e "s|\${REJECT_IF_CONSTRAINTS_VIOLATED}|$REJECT_IF_CONSTRAINTS_VIOLATED|g" \
        "$DATA_PATH/${DRT_CONFIG}" > "$CONFIG_FILE_PATH" || { echo "Config file creation failed for sim $SIM_ID"; exit 1; }

    echo "  Config created: $CONFIG_FILE_PATH"

    # Copy JAR to data directory (SKIP for comura - will build and copy inside sbatch job)
    if [[ "$USER_NAME" != "comura" ]]; then
        cp "$MAVEN_PATH/target/abmt2025-1.0-SNAPSHOT.jar" "$DATA_PATH/abmt2025-DRT${SIM_ID}.jar"
    fi

    # Navigate to the scenario directory
    cd "$DATA_PATH"

    # Build Maven build commands for comura (runs inside sbatch on compute node)
    MAVEN_BUILD_CMD=""
    if [[ "$USER_NAME" == "comura" ]]; then
        MAVEN_BUILD_CMD=". ~/use-java17.sh \
        && echo '[BUILD] Compiling Maven project on compute node...' \
        && cd $MAVEN_PATH \
        && /home/comura/apache-maven-3.9.6/apache-maven-3.9.6/bin/mvn clean package \
        && echo '[BUILD] Maven build complete!' \
        && echo '[BUILD] Copying JAR to data directory...' \
        && cp $MAVEN_PATH/target/abmt2025-1.0-SNAPSHOT.jar $DATA_PATH/abmt2025-DRT${SIM_ID}.jar \
        && cd $DATA_PATH \
        && "
    fi

    # Build dependency flag: if there's a previous job, wait for it
    DEPENDENCY_FLAG=""
    if [[ -n "$PREV_JOB_ID" ]]; then
        DEPENDENCY_FLAG="--dependency=afterok:${PREV_JOB_ID}"
        echo "  Will wait for job $PREV_JOB_ID to finish before starting"
    fi

    # Submit the job and capture the job ID
    JOB_SUBMISSION=$(sbatch -n 1 \
        --cpus-per-task=4 \
        --time=100:00:00 \
        --job-name="${SIM_ID}" \
        --mem-per-cpu=64G \
        --mail-type=END,FAIL \
        --mail-user=muaa@zhaw.ch \
        $DEPENDENCY_FLAG \
        --wrap=" \
        ${MAVEN_BUILD_CMD}java -Xmx128G -Djava.awt.headless=true \
        -DDRT_FARE_CHF=$DRT_FARE_CHF \
        -DDRT_FARE_CHF_KM=$DRT_FARE_CHF_KM \
        -DALPHA_WALK=$ALPHA_WALK \
        -DALPHA_BIKE=$ALPHA_BIKE \
        -DALPHA_PT=$ALPHA_PT \
        -DALPHA_CAR=$ALPHA_CAR \
        -DBETA_CAR_CITY=$BETA_CAR_CITY \
        -cp abmt2025-DRT${SIM_ID}.jar abmt2025.project.mode_choice.RunSimulation_DRT \
        --config-path $CONFIG_FILE_PATH \
        --output-directory $OUTPUT_DIRECTORY_PATH \
        --output-sim-name $OUTPUT_SIM_NAME \
        && sed -i 's|^sim_output_folder *=.*|sim_output_folder = $RELATIVE_OUTPUT_PATH/$OUTPUT_SIM_NAME|' $CONFIG_INI_PATH \
        $(if $RUN_ANALYSIS; then echo "&& echo '[ANALYSIS] Starting Python analysis...' && bash $ANALYSIS_SCRIPT && echo '[ANALYSIS] Analysis complete!'"; fi) \
        $(if $CLEAN_ITERATIONS; then echo "&& echo '[CLEANUP] Waiting 90 seconds before first check...' && sleep 90 && (find $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME -type f -mmin -1 2>/dev/null | grep -q . && echo '[CLEANUP] Files modified in last minute detected. Waiting 2 more minutes...' && sleep 120 && (find $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME -type f -mmin -1 2>/dev/null | grep -q . && echo '[CLEANUP] Files still being modified. Waiting 3 more minutes for safety...' && sleep 180 && echo '[CLEANUP] Final wait complete. Proceeding with deletion.' || echo '[CLEANUP] No recent modifications detected at second check. Proceeding with deletion.') || echo '[CLEANUP] No recent modifications detected at first check. Proceeding with deletion.') && echo '[CLEANUP] Finding last iteration...' && ACTUAL_LAST_ITER=\$(ls -d $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME/ITERS/it.* 2>/dev/null | sed 's/.*it\\.//' | sort -n | tail -1) && echo \"[CLEANUP] Last iteration found: it.\$ACTUAL_LAST_ITER\" && echo '[CLEANUP] Deleting all iteration folders except the last one...' && for iter_dir in $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME/ITERS/it.*; do iter_num=\$(basename \$iter_dir | sed 's/it\\.//' ); if [ \"\$iter_num\" != \"\$ACTUAL_LAST_ITER\" ]; then echo \"[CLEANUP] Removing iteration \$iter_num...\" && rm -rf \$iter_dir; fi; done && echo '[CLEANUP] Iteration folders deleted. Remaining:' && ls $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME/ITERS/ $(if $DELETE_EVENTS_FILE; then echo "&& echo '[CLEANUP] DELETE_EVENTS_FILE=true, removing large event files...' && [ -f $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME/ITERS/it.\$ACTUAL_LAST_ITER/\$ACTUAL_LAST_ITER.events.xml.gz ] && echo \"[CLEANUP] Deleting it.\$ACTUAL_LAST_ITER/\$ACTUAL_LAST_ITER.events.xml.gz...\" && rm -f $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME/ITERS/it.\$ACTUAL_LAST_ITER/\$ACTUAL_LAST_ITER.events.xml.gz || echo '[CLEANUP] File events.xml.gz not found in last iteration, skipping.' && [ -f $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME/output_events.xml.gz ] && echo '[CLEANUP] Deleting output_events.xml.gz...' && rm -f $OUTPUT_DIRECTORY_PATH/$OUTPUT_SIM_NAME/output_events.xml.gz || echo '[CLEANUP] File output_events.xml.gz not found, skipping.'"; else echo "&& echo '[CLEANUP] DELETE_EVENTS_FILE=false, keeping event files.'"; fi) && echo '[CLEANUP] Cleanup complete!'"; fi) \
        ")

    # Extract Job ID from sbatch output ("Submitted batch job XXXXXXX")
    PREV_JOB_ID=$(echo "$JOB_SUBMISSION" | awk '{print $NF}')

    echo "  Submitted job ID: $PREV_JOB_ID  (name: $SIM_ID)"

done

echo ""
echo "==========================================="
echo "All $NUM_SIMS simulations submitted!"
echo "They will run one after another."
echo "Use 'squeue -u $USER_NAME' to monitor progress."
echo "==========================================="
