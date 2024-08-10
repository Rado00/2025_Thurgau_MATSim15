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
    DATA_PATH="/home/muaa/DATA_ABM/Thurgau/Thurgau_Scenario/100pct"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    # DATA_PATH="/home/comura/data/DATA_ABM/Weinfelden/WeinfeldenScenario"
    # DATA_PATH="/home/comura/data/DATA_ABM/Frauenfeld/FrauenfeldScenario"
    DATA_PATH="/home/comura/data/DATA_ABM/Thurgau/ThurgauScenario/1pct"
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


# Array of DRT vehicles paths (assuming you have different shape files for each simulation)
DRT_VEHICLES_PATH_ARRAY=(
    "/home/comura/data/DATA_ABM/Thurgau/ThurgauScenario/1pct/FrauenfeldDRT/drt_100_6.xml"
    "/home/comura/data/DATA_ABM/Thurgau/ThurgauScenario/1pct/FrauenfeldDRT/drt_1000_6.xml"
)

# Loop through each combination of DRT vehicles and shape files
for i in "${!DRT_VEHICLES_PATH_ARRAY[@]}"; do
    DRT_VEHICLES_PATH=${DRT_VEHICLES_PATH_ARRAY[$i]}

    # Extract the filename without extension
    removedBasename="${DRT_VEHICLES_PATH_ARRAY[$i]##*/}"   # Remove everything before the last /
    NUM_DRT_VEHICLES="${removedBasename%.xml}"  # Remove the .xml extension

    # Output the results
    echo "$NUM_DRT_VEHICLES"  # Outputs: drt_100_6

    # Create a unique config file for this run by replacing placeholders
    CONFIG_FILE_PATH="$DATA_PATH/Thurgau_config_${NUM_DRT_VEHICLES}_queue.xml"
    sed "s|\${DRT_VEHICLES_PATH}|$DRT_VEHICLES_PATH|g" \
        "$DATA_PATH/Thurgau_config_base_02_DRT_queue.xml" > "$CONFIG_FILE_PATH"


    echo "Running simulation with $DRT_VEHICLES_PATH_ARRAY DRT vehicles and shape file SHAPE_FILE_PATH"

    cp "$MAVEN_PATH/target/abmt2023-0.0.1-SNAPSHOT.jar" "$DATA_PATH"

    # Navigate to the 10pct scenario directory
    cd "$DATA_PATH"

    # Submit the job
    sbatch -n 1 \
    --cpus-per-task=12 \
    --time=100:00:00 \
    --job-name="abmt2024_$DRT_VEHICLES_PATH_ARRAY" \
    --mem-per-cpu=10000 \
    --wrap="java -Xmx128G -cp abmt2023-0.0.1-SNAPSHOT.jar abmt2023.project.mode_choice.RunSimulation_DRT --config-path $CONFIG_FILE_PATH --output-directory /home/comura/data/OUTPUTS/Thurgau --output-sim-name Thurgau_DRT_${NUM_DRT_VEHICLES}"

    echo "Sent Simulation $DRT_VEHICLES_PATH_ARRAY "

    # Wait for the job to complete before starting the next one
    # Assuming you're using SLURM, you can wait for job completion
    JOB_ID=$(squeue --name="abmt2024_$DRT_VEHICLES_PATH_ARRAY" --noheader --format=%i)
    echo "$JOB_ID"
    while [[ -n "$(sacct -j "$JOB_ID" --noheader --format=State --parsable2 | grep -vE '^COMPLETED$|^CANCELLED$|^FAILED$')" ]]; do
        echo "Job $JOB_ID is still running"
        sleep 10
    done
    echo "Job $JOB_ID has finished."

done