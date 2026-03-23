package abmt2025.project.mode_choice.costs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.matsim.api.core.v01.Scenario;
import abmt2025.project.mode_choice.DrtCostParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Calculates DRT operator costs and revenues based on MATSim DRT output files.
 *
 * Cost model based on Bösch et al. 2018 "Cost-based Analysis of Autonomous Mobility Services"
 *
 * Reads from:
 * - drt_vehicle_stats_drt.csv: vehicle-km, vehicle-hours, empty ratio
 * - drt_legs_drt.csv: passenger trips, passenger-km
 */
public class OperatorCostCalculator {
    private static final Logger LOG = LogManager.getLogger(OperatorCostCalculator.class);
    private DrtCostParameters costParameters;
    private Scenario scenario;
    private String outputDirectory;

    // Statistics from CSV files
    private int fleetSize = 0;
    private double totalVehicleKm = 0.0;
    private double totalVehicleHours = 0.0;
    private double emptyRatio = 0.0;
    private int totalTrips = 0;
    private double totalPassengerKm = 0.0;

    public OperatorCostCalculator(Scenario scenario, DrtCostParameters costParameters) {
        this.scenario = scenario;
        this.costParameters = costParameters;
        this.outputDirectory = scenario.getConfig().controller().getOutputDirectory();
    }

    public void calculateAndWriteOperatorCosts() {
        try {
            LOG.info("Starting operator cost calculation...");
            LOG.info("Output directory: " + outputDirectory);

            // Read statistics from DRT output files
            readVehicleStats();
            readLegStats();

            // Calculate costs (Bösch 2018)
            double distanceCost = totalVehicleKm * costParameters.DRTOperatorCost_CHF_vehicleKm;
            double driverCost = totalVehicleHours * costParameters.DRTOperatorCost_CHF_driverHour;
            double fixedCost = fleetSize * costParameters.DRTOperatorCost_CHF_vehicleDay;
            double totalCost = distanceCost + driverCost + fixedCost;

            // Calculate revenues
            double fixedFareRevenue = totalTrips * costParameters.DRTFare_CHF;
            double distanceFareRevenue = totalPassengerKm * costParameters.DRTFare_CHF_km;
            double totalRevenue = fixedFareRevenue + distanceFareRevenue;

            // Calculate summary metrics
            double costPerTrip = totalTrips > 0 ? totalCost / totalTrips : 0.0;
            double revenuePerTrip = totalTrips > 0 ? totalRevenue / totalTrips : 0.0;
            double subsidyPerTrip = costPerTrip - revenuePerTrip;
            double costRecoveryRatio = totalCost > 0 ? (totalRevenue / totalCost) * 100 : 0.0;

            // Write comprehensive report
            writeReport(distanceCost, driverCost, fixedCost, totalCost,
                       fixedFareRevenue, distanceFareRevenue, totalRevenue,
                       costPerTrip, revenuePerTrip, subsidyPerTrip, costRecoveryRatio);

            LOG.info("Operator cost calculation completed successfully.");

        } catch (Exception e) {
            LOG.error("Error in operator cost calculation: ", e);
            writeFallbackReport();
        }
    }

    private void readVehicleStats() {
        File vehicleStatsFile = new File(outputDirectory, "drt_vehicle_stats_drt.csv");

        if (!vehicleStatsFile.exists()) {
            LOG.warn("Vehicle stats file not found: " + vehicleStatsFile.getAbsolutePath());
            LOG.warn("Falling back to population-based calculation");
            calculateFromPopulation();
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(vehicleStatsFile))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                LOG.warn("Empty vehicle stats file");
                return;
            }

            // Parse header to find column indices
            String[] headers = headerLine.split(";");
            Map<String, Integer> columnIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                columnIndex.put(headers[i].trim(), i);
            }

            // Read all lines, keep only the last one (last iteration)
            String line;
            String lastLine = null;
            while ((line = reader.readLine()) != null) {
                lastLine = line;
            }

            if (lastLine == null) {
                LOG.warn("No data rows in vehicle stats file");
                return;
            }

            String[] values = lastLine.split(";");

            // Fleet size from "vehicles" column
            if (columnIndex.containsKey("vehicles")) {
                fleetSize = (int) parseDouble(values, columnIndex.get("vehicles"));
            }

            // Total distance from last iteration
            if (columnIndex.containsKey("totalDistance")) {
                totalVehicleKm = parseDouble(values, columnIndex.get("totalDistance")) / 1000.0;
            }

            // Vehicle-hours = fleet size × operating hours per day
            totalVehicleHours = fleetSize * costParameters.operatingHoursPerDay;

            // Empty ratio from last iteration
            if (columnIndex.containsKey("emptyRatio")) {
                emptyRatio = parseDouble(values, columnIndex.get("emptyRatio")) * 100.0;
            }

            LOG.info("Read vehicle stats (last iteration): {} vehicles, {:.2f} km, {:.2f} hours ({}h/day), {:.1f}% empty",
                    fleetSize, totalVehicleKm, totalVehicleHours, costParameters.operatingHoursPerDay, emptyRatio);

        } catch (IOException e) {
            LOG.error("Error reading vehicle stats file: ", e);
            calculateFromPopulation();
        }
    }


    private void readLegStats() {
        File legStatsFile = new File(outputDirectory, "drt_legs_drt.csv");

        if (!legStatsFile.exists()) {
            LOG.warn("Leg stats file not found: " + legStatsFile.getAbsolutePath());
            LOG.warn("Falling back to population-based calculation for trips");
            if (totalTrips == 0) {
                calculateTripsFromPopulation();
            }
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(legStatsFile))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                LOG.warn("Empty leg stats file");
                return;
            }

            // Parse header to find column indices
            String[] headers = headerLine.split(";");
            Map<String, Integer> columnIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                columnIndex.put(headers[i].trim(), i);
            }

            String line;
            int tripCount = 0;
            double passengerDistance = 0.0;

            while ((line = reader.readLine()) != null) {
                String[] values = line.split(";");
                tripCount++;

                // Try different possible column names for distance
                if (columnIndex.containsKey("travelDistance_m")) {
                    passengerDistance += parseDouble(values, columnIndex.get("travelDistance_m"));
                } else if (columnIndex.containsKey("travelDistance")) {
                    passengerDistance += parseDouble(values, columnIndex.get("travelDistance"));
                } else if (columnIndex.containsKey("distance")) {
                    passengerDistance += parseDouble(values, columnIndex.get("distance"));
                }
            }

            totalTrips = tripCount;
            totalPassengerKm = passengerDistance / 1000.0; // Convert m to km

            LOG.info("Read leg stats: {} trips, {:.2f} passenger-km", totalTrips, totalPassengerKm);

        } catch (IOException e) {
            LOG.error("Error reading leg stats file: ", e);
            calculateTripsFromPopulation();
        }
    }

    private double parseDouble(String[] values, int index) {
        try {
            if (index < values.length) {
                return Double.parseDouble(values[index].trim());
            }
        } catch (NumberFormatException e) {
            // Ignore parsing errors
        }
        return 0.0;
    }

    private void calculateFromPopulation() {
        LOG.info("Calculating statistics from population (fallback method)");

        // Count DRT vehicles from scenario
        fleetSize = (int) scenario.getVehicles().getVehicles().values().stream()
            .filter(v -> v.getId().toString().contains("drt"))
            .count();

        calculateTripsFromPopulation();

        // Estimate vehicle-km from passenger-km (assuming ~50% empty driving)
        totalVehicleKm = totalPassengerKm * 1.5;
        // Estimate hours assuming average speed of 30 km/h
        totalVehicleHours = totalVehicleKm / 30.0;
        emptyRatio = 33.0; // Estimated
    }

    private void calculateTripsFromPopulation() {
        totalTrips = 0;
        totalPassengerKm = 0.0;

        scenario.getPopulation().getPersons().values().forEach(person -> {
            person.getSelectedPlan().getPlanElements().stream()
                .filter(element -> element instanceof org.matsim.api.core.v01.population.Leg)
                .map(element -> (org.matsim.api.core.v01.population.Leg) element)
                .filter(leg -> leg.getMode().equals("drt"))
                .forEach(leg -> {
                    totalTrips++;
                    if (leg.getRoute() != null) {
                        totalPassengerKm += leg.getRoute().getDistance() / 1000.0;
                    }
                });
        });

        LOG.info("Calculated from population: {} trips, {:.2f} passenger-km", totalTrips, totalPassengerKm);
    }

    private void writeReport(double distanceCost, double driverCost, double fixedCost, double totalCost,
                            double fixedFareRevenue, double distanceFareRevenue, double totalRevenue,
                            double costPerTrip, double revenuePerTrip, double subsidyPerTrip, double costRecoveryRatio) {

        File outputFile = new File(outputDirectory, "operator_costs.txt");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write("================= DRT OPERATOR FINANCIAL REPORT =================\n");
            writer.write("\n");

            writer.write("FLEET INFORMATION\n");
            writer.write(String.format("  Number of vehicles:           %d\n", fleetSize));
            writer.write(String.format("  Total vehicle-km:             %.2f km\n", totalVehicleKm));
            writer.write(String.format("  Total vehicle-hours (%.0fh/day):  %.2f h\n", costParameters.operatingHoursPerDay, totalVehicleHours));
            writer.write(String.format("  Empty ratio:                  %.1f%%\n", emptyRatio));
            writer.write("\n");

            writer.write("PASSENGER INFORMATION\n");
            writer.write(String.format("  Total trips:                  %d\n", totalTrips));
            writer.write(String.format("  Total passenger-km:           %.2f km\n", totalPassengerKm));
            writer.write(String.format("  Avg trip distance:            %.2f km\n", totalTrips > 0 ? totalPassengerKm / totalTrips : 0));
            writer.write("\n");

            writer.write("OPERATOR COSTS (Bösch 2018)\n");
            writer.write(String.format("  Distance costs (%.2f CHF/km): %.2f CHF\n",
                costParameters.DRTOperatorCost_CHF_vehicleKm, distanceCost));
            writer.write(String.format("  Driver costs (%.2f CHF/h):    %.2f CHF\n",
                costParameters.DRTOperatorCost_CHF_driverHour, driverCost));
            writer.write(String.format("  Fixed costs (%.2f CHF/veh):   %.2f CHF\n",
                costParameters.DRTOperatorCost_CHF_vehicleDay, fixedCost));
            writer.write("  ─────────────────────────────────────────\n");
            writer.write(String.format("  TOTAL COSTS:                  %.2f CHF\n", totalCost));
            writer.write("\n");

            writer.write("REVENUES\n");
            writer.write(String.format("  Fixed fare (%.2f CHF × %d):   %.2f CHF\n",
                costParameters.DRTFare_CHF, totalTrips, fixedFareRevenue));
            writer.write(String.format("  Distance fare (%.2f × %.2f):  %.2f CHF\n",
                costParameters.DRTFare_CHF_km, totalPassengerKm, distanceFareRevenue));
            writer.write("  ─────────────────────────────────────────\n");
            writer.write(String.format("  TOTAL REVENUES:               %.2f CHF\n", totalRevenue));
            writer.write("\n");

            writer.write("FINANCIAL SUMMARY\n");
            writer.write(String.format("  Cost per trip:                %.2f CHF\n", costPerTrip));
            writer.write(String.format("  Revenue per trip:             %.2f CHF\n", revenuePerTrip));
            writer.write(String.format("  Subsidy required per trip:    %.2f CHF\n", subsidyPerTrip));
            writer.write(String.format("  Cost recovery ratio:          %.1f%%\n", costRecoveryRatio));
            writer.write("================================================================\n");

            LOG.info("Operator cost report written to: " + outputFile.getAbsolutePath());

        } catch (IOException e) {
            LOG.error("Error writing operator cost report: ", e);
        }
    }

    private void writeFallbackReport() {
        File outputFile = new File(outputDirectory, "operator_costs.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write("ERROR: Could not calculate operator costs.\n");
            writer.write("Please check the log files for details.\n");
        } catch (IOException e) {
            LOG.error("Error writing fallback report: ", e);
        }
    }
}
