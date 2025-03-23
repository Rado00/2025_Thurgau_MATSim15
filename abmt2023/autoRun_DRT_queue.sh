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
    DATA_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario/100pct"
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


# Array of DRT vehicles paths (assuming you have different shape files for each simulation) - METTERE A CAPO SENZA virgola o altro
DRT_VEHICLES_PATH_ARRAY=(
    "/home/comura/2023_ABMT_Corrado/abmt2023/src/main/create_vehicle_xml/01_Fleet_files/01_drt_8_60.xml"
)
# CHECK!!! that DRT Vehicles and DRT Shape files have the same number of elements
DRT_SHAPE_FILE_PATH_ARRAY=(
    "/home/comura/data/2024_Paper2_Data/Paper2_ShapeFiles_CH1903+_LV95/01_Weinfelden_Affeltrangen/01_Weinfelden_Affeltrangen.shp"
)

# Loop through each combination of DRT vehicles and shape files
for i in "${!DRT_VEHICLES_PATH_ARRAY[@]}"; do
    DRT_VEHICLES_PATH=${DRT_VEHICLES_PATH_ARRAY[$i]}
    DRT_SHAPE_FILE_PATH=${DRT_SHAPE_FILE_PATH_ARRAY[$i]}

    # Extract the filename without extension
    removedBasename="${DRT_VEHICLES_PATH_ARRAY[$i]##*/}"   # Remove everything before the last /
    NUM_DRT_VEHICLES="${removedBasename%.xml}"  # Remove the .xml extension

    # Extract the shape file name
    removedShapeBasename="${DRT_SHAPE_FILE_PATH_ARRAY[$i]##*/}"   # Remove everything before the last /
    SHAPE_FILE_NAME="${removedShapeBasename%.shp}"  # Remove the .xml extension

    # Output the results
    echo "$NUM_DRT_VEHICLES"  # Outputs: drt_100_6
    echo "$SHAPE_FILE_NAME"  # Outputs: Frauenfeld_proj


    # Create a unique config file for this run by replacing placeholders
    CONFIG_FILE_PATH="$DATA_PATH/Thurgau_config_${NUM_DRT_VEHICLES}_queue.xml"
sed -e "s|\${DRT_VEHICLES_PATH}|$DRT_VEHICLES_PATH|g" \
    -e "s|\${DRT_SHAPE_FILE_PATH}|$DRT_SHAPE_FILE_PATH|g" \
    "$DATA_PATH/Thurgau_config_DRT_03_queue.xml" > "$CONFIG_FILE_PATH"



    echo "Running simulation with $NUM_DRT_VEHICLES DRT vehicles and shape file $DRT_SHAPE_FILE_PATH"

    #CHANGE JAR NAME HERE TO PARALLEL SIMULATIONS
    cp "$MAVEN_PATH/target/abmt2023-0.0.1-SNAPSHOT.jar" "$DATA_PATH/abmt2023-DRT_01_1.jar"

    # Navigate to the 10pct scenario directory
    cd "$DATA_PATH"

    # Submit the job
    sbatch -n 1 \
    --cpus-per-task=4 \
    --time=100:00:00 \
    --job-name="abmt2024_$DRT_VEHICLES_PATH_ARRAY" \
    --mem-per-cpu=10G \
    --mail-type=END,FAIL --mail-user=muaa@zhaw.ch \
    --wrap="java -Xmx128G -cp abmt2023-DRT_01_1.jar abmt2023.project.mode_choice.RunSimulation_DRT --config-path $CONFIG_FILE_PATH --output-directory $OUTPUT_DIRECTORY_PATH --output-sim-name Prova_DRT_${SHAPE_FILE_NAME}_${NUM_DRT_VEHICLES}"
    #CHANGE JAR NAME HERE TO PARALLEL SIMULATIONS

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