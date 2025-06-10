#!/bin/bash
set -e

echo "Starting the Python analysis pipeline..."

# Detect user and OS
USER_NAME=$(whoami)
OS_TYPE=$(uname)

########################## CHECK  SETTING ###########################################


ZONE_ID="10"
NUM_VEHICLES=1

T0=0
T1=86400
CAPACITY=8
OUTPUT_FILE="01_Fleet_files/${ZONE_ID}_drt_${NUM_VEHICLES}_${CAPACITY}.xml"



##########################  PATHS ###########################################


# Set up conda and paths
if [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "muaa" ]]; then
    echo "Running on ZHAW or local Linux as muaa"
    source /home/muaa/miniconda3/etc/profile.d/conda.sh
    conda activate ThurgauAnalysisEnv
    SCRIPTS_PATH="/home/muaa/ThurgauPaperAnalysisAM/scripts"
    PYTHON_SCRIPT="/home/muaa/2025_Thurgau_MATSim15/abmt2025/src/main/create_vehicle_xml/generate_vehicles_from_network.py"
    NETWORK_FILE="/home/muaa/2025_Thurgau_MATSim15/abmt2025/src/main/create_vehicle_xml/small_networks/${ZONE_ID}_network.xml.gz"

elif [[ "$OS_TYPE" == "Linux" && "$USER_NAME" == "comura" ]]; then
    echo "Running on UZH Linux as comura"
    # module load anaconda3/2024.02-1
    source /apps/opt/spack/linux-ubuntu20.04-x86_64/gcc-9.3.0/anaconda3-2024.02-1-whphrx3ledrvyrcnibu7lezfvvqltgt5/etc/profile.d/conda.sh
    conda activate ThurgauAnalysisEnv
    SCRIPTS_PATH="/home/comura/ThurgauPaperAnalysisAM/scripts"
    PYTHON_SCRIPT="/home/comura/2025_Thurgau_MATSim15/abmt2025/src/main/create_vehicle_xml/generate_vehicles_from_network.py"
    NETWORK_FILE="/home/comura/2025_Thurgau_MATSim15/abmt2025/src/main/create_vehicle_xml/small_networks/${ZONE_ID}_network.xml.gz"

elif [[ "$OS_TYPE" == "MINGW"* || "$OS_TYPE" == "CYGWIN"* || "$OS_TYPE" == "MSYS"* ]] && [[ "$USER_NAME" == "muaa" ]]; then
    echo "Running on Windows as muaa"
    SCRIPTS_PATH="C:/Users/${USER_NAME}/Documents/3_MIEI/ThurgauPaperAnalysisAM/scripts"
    # Windows: Activate manually if needed or ensure conda is in PATH
    conda activate ThurgauAnalysisEnv
else
    echo "Unsupported system configuration"
    exit 1
fi

python 03_generate_vehicles_from_network.py "$NETWORK_FILE" "$NUM_VEHICLES" "$T0" "$T1" "$CAPACITY" "$OUTPUT_FILE"