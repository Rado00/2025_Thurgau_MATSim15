#!/bin/bash

########################## ANALYSIS ONLY SCRIPT (SUBMIT AS JOB) ###########################################

# ============== VARIABLES TO SET ==============
SIM_OUTPUT_FOLDER_FULL_PATH="/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Paper2_SimsOutputs/2_FleetSizeCalibration/DRT_10_ShapeFile_10_drt_1_8_CalTest_10_MILOS_FIX"
THURGAU_ANALYSIS_PATH="/home/muaa/ThurgauPaperAnalysisAM"
TARGET_AREA="10_ShapeFile.shp"

# Job settings
JOB_NAME="Analysis_DRT"
TIME_LIMIT="10:00:00"  # 10 hours, adjust as needed
CPUS=4
MEM_PER_CPU="16G"
EMAIL="muaa@zhaw.ch"
# ============================================

# Get the current user name
USER_NAME=$(whoami)

# Set paths
CONFIG_INI_PATH="$THURGAU_ANALYSIS_PATH/config/config.ini"
ANALYSIS_SCRIPT="$THURGAU_ANALYSIS_PATH/scripts/run_all_scripts.sh"

# Extract data_path (everything up to and including "2024_Paper2_Data")
DATA_PATH=$(echo "$SIM_OUTPUT_FOLDER_FULL_PATH" | sed 's|\(.*2024_Paper2_Data\).*|\1|')

# Extract sim_output_folder (everything after "MATSim_Thurgau/")
SIM_OUTPUT_FOLDER="${SIM_OUTPUT_FOLDER_FULL_PATH#*MATSim_Thurgau/}"

echo "========================================"
echo "Configuration:"
echo "Data Path: $DATA_PATH"
echo "Sim Output Folder: $SIM_OUTPUT_FOLDER"
echo "Target Area: $TARGET_AREA"
echo "Config INI Path: $CONFIG_INI_PATH"
echo "========================================"

# Check if config.ini exists
if [ ! -f "$CONFIG_INI_PATH" ]; then
    echo "Error: config.ini not found at $CONFIG_INI_PATH"
    exit 1
fi

# Check if analysis script exists
if [ ! -f "$ANALYSIS_SCRIPT" ]; then
    echo "Error: Analysis script not found at $ANALYSIS_SCRIPT"
    exit 1
fi

# Submit the job
echo "Submitting analysis job..."

sbatch -n 1 \
    --cpus-per-task=$CPUS \
    --time=$TIME_LIMIT \
    --job-name="$JOB_NAME" \
    --mem-per-cpu=$MEM_PER_CPU \
    --mail-type=END,FAIL \
    --mail-user=$EMAIL \
    --wrap=" \
    echo '========================================' && \
    echo 'Job started at:' \$(date) && \
    echo '========================================' && \
    echo 'Updating config.ini...' && \
    sed -i 's|^data_path *=.*|data_path = $DATA_PATH|' $CONFIG_INI_PATH && \
    sed -i 's|^simulation_zone_name *=.*|simulation_zone_name = MATSim_Thurgau|' $CONFIG_INI_PATH && \
    sed -i 's|^analysis_zone_name *=.*|analysis_zone_name = MATSim_Thurgau|' $CONFIG_INI_PATH && \
    sed -i 's|^sim_output_folder *=.*|sim_output_folder = $SIM_OUTPUT_FOLDER|' $CONFIG_INI_PATH && \
    sed -i 's|^target_area *=.*|target_area = $TARGET_AREA|' $CONFIG_INI_PATH && \
    echo 'Config.ini updated successfully!' && \
    echo '========================================' && \
    echo 'Verification - Updated values in config.ini:' && \
    grep '^data_path' $CONFIG_INI_PATH && \
    grep '^simulation_zone_name' $CONFIG_INI_PATH && \
    grep '^analysis_zone_name' $CONFIG_INI_PATH && \
    grep '^sim_output_folder' $CONFIG_INI_PATH && \
    grep '^target_area' $CONFIG_INI_PATH && \
    echo '========================================' && \
    echo 'Starting Python analysis...' && \
    bash $ANALYSIS_SCRIPT && \
    echo '========================================' && \
    echo 'Analysis complete!' && \
    echo 'Job finished at:' \$(date) && \
    echo '========================================' \
    "

echo "Job submitted successfully!"
echo "Check job status with: squeue -u $USER_NAME"
echo "Check job output in slurm-*.out files"