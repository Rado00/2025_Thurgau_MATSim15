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
    DATA_PATH="/home/muaa/Thurgau_Scenario"
elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    # DATA_PATH="/home/comura/data/DATA_ABM/Weinfelden/WeinfeldenScenario"
    # DATA_PATH="/home/comura/data/DATA_ABM/Frauenfeld/FrauenfeldScenario"
    DATA_PATH="/home/comura/data/DATA_ABM/Thurgau/ThurgauScenario/100pct"
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

# Copy the .jar file to the 10pct scenario directory
cp "$MAVEN_PATH/target/abmt2023-0.0.1-SNAPSHOT.jar" "$DATA_PATH"

# Navigate to the 10pct scenario directory
cd "$DATA_PATH"
# cd /cluster/scratch/cmuratori/data/scenarios/10pct/

# Run the simulation
./runSimulation_DRT_Frauenfeld

