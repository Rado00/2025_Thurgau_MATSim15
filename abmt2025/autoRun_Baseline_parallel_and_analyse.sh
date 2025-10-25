#!/bin/bash

# Get the current operating system and user name
OS_TYPE=$(uname)
USER_NAME=$(whoami)

########################## CHECK AUTORUN SETTING ###########################################

LAST_ITERATION=100 # Set number of iterations dynamically (can also do: LAST_ITERATION=$1)

SIM_ID="28_onlyUse_100iter_s1234" # TO RUN PARALLEL SIMS AND CHANGE OUTPUT FOLDER

RUN_ANALYSIS=false
CLEAN_ITERATIONS=false

OUTPUT_SIM_NAME=BaselineCalibration_${SIM_ID}

BASELINE_PCT="1pct"

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
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "sarf" ]]; then
    MAVEN_PATH="$HOME/projects/corrado_matsim/2025_Thurgau_MATSim15/abmt2025"
    MAVEN_BIN="/usr/bin/mvn"  
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
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "sarf" ]]; then
    DATA_PATH="$HOME/projects/corrado_matsim/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario/${BASELINE_PCT}"
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
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "sarf" ]]; then
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
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "sarf" ]]; then
    DATA_PATH="$HOME/projects/corrado_matsim/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Paper2_SimsOutputs/1_ModalSplitCalibration"
else
    echo "Unsupported system configuration"
    exit 1
fi

# PYTHON ANALYSIS PATH (you can set to false the RUN_ANALYSIS option later in this code)
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    ANALYSIS_SCRIPT="/home/comura/ThurgauPaperAnalysisAM/scripts/run_all_scripts.sh"
    CONFIG_INI_PATH="/home/comura/ThurgauPaperAnalysisAM/config/config.ini"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    ANALYSIS_SCRIPT="/home/muaa/ThurgauPaperAnalysisAM/scripts/run_all_scripts.sh"
    CONFIG_INI_PATH="/home/muaa/ThurgauPaperAnalysisAM/config/config.ini"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "sarf" ]]; then
    ANALYSIS_SCRIPT="$HOME/projects/corrado_paper/ThurgauPaperAnalysisAM/scripts/run_all_analysis.sh"
    CONFIG_INI_PATH="$HOME/projects/corrado_paper/ThurgauPaperAnalysisAM/config/config.ini"
else
    echo "Unsupported system configuration for analysis script/config path"
    exit 1
fi


########################## CREATE UNIQUE CONFIG AND JAR ###########################################

# Define the path for the new temporary config file
CONFIG_FILE_PATH="$DATA_PATH/Thurgau_config_base.xml"

# Create config by replacing LAST_ITERATION placeholder in the template
sed -e "s|\${LAST_ITERATION}|$LAST_ITERATION|g" \
    "$DATA_PATH/Thurgau_config_base_M15_06.xml" > "$CONFIG_FILE_PATH" || { echo "Config file creation failed"; exit 1; }

echo "Created config file with $LAST_ITERATION iterations: $CONFIG_FILE_PATH"

# USE YOUR JAR NAME IN THE FIRST STRING. CHANGE THE NUMBER OF THE SECOND STRING IF RUNNING SIMULATIONS IN PARALLEL 
cp "$MAVEN_PATH/target/abmt2025-1.0-SNAPSHOT.jar" "$DATA_PATH/abmt2025-Baseline${SIM_ID}.jar" 

# Navigate to the scenario directory
cd "$DATA_PATH"


########################## SUBMIT THE JOB ###########################################

sbatch -n 1 \
    --cpus-per-task=4 \
    --time=200:00:00 \
    --job-name="${SIM_ID}" \
    --mem-per-cpu=64G \
    --mail-type=END,FAIL \
    --mail-user=muaa@zhaw.ch \
    --wrap=" \
    java -Xmx128G -cp abmt2025-Baseline${SIM_ID}.jar abmt2025.project.mode_choice.RunSimulation_Baseline \
    --config-path $CONFIG_FILE_PATH \
    --output-directory $OUTPUT_DIRECTORY_PATH \
    --output-sim-name ${OUTPUT_SIM_NAME} \
    $(if $CLEAN_ITERATIONS; then echo "&& for i in \$(seq 0 $((LAST_ITERATION - 1))); do rm -rf $OUTPUT_DIRECTORY_PATH/${OUTPUT_SIM_NAME}/ITERS/it.\$i; done"; fi) \
    && sed -i 's|^sim_output_folder *=.*|sim_output_folder = Paper2_SimsOutputs/1_ModalSplitCalibration/${OUTPUT_SIM_NAME}|' $CONFIG_INI_PATH \
    $(if $RUN_ANALYSIS; then echo "&& bash $ANALYSIS_SCRIPT"; fi)
    "

echo "Simulation submitted"