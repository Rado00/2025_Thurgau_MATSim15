#!/bin/bash
rclone copy "/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Paper2_SimsOutputs/2_FleetSizeCalibration/DRT_25_ShapeFile_25_drt_20_8_Wein5" \
"zhaw_onedrive:000_Paper2/2024_Paper2_Data/MATSim_Thurgau/Paper2_SimsOutputs/2_Fleet/DRT_25_ShapeFile_25_drt_20_8_Wein5" \
--progress --create-empty-src-dirs
