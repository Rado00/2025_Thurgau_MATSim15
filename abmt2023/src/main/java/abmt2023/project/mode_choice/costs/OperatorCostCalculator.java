package abmt2023.project.mode_choice.costs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.matsim.api.core.v01.Scenario;
import abmt2023.project.mode_choice.DrtCostParameters;

public class OperatorCostCalculator {
    private DrtCostParameters costParameters;
    private Scenario scenario;

    public OperatorCostCalculator(Scenario scenario, DrtCostParameters costParameters) {
        this.scenario = scenario;
        this.costParameters = costParameters;
    }

    public void calculateAndWriteOperatorCosts() {
        // Here you need to implement the logic to calculate the total distance, number of trips, and number of vehicles
        // For demonstration, these are set to example values
        double totalDistance = calculateTotalDRTDistance(); // Implement this method
        int totalTrips = calculateTotalDRTTrips(); // Implement this method
        int totalVehicles = getTotalDRTVehicles(); // Implement this method

        double distanceCost = totalDistance * costParameters.DRTCost_CHF_km;
        double tripCost = totalTrips * costParameters.DRTCost_CHF_trip;
        double vehicleCost = totalVehicles * costParameters.DRTCost_CHF_vehicle;

        double totalCost = distanceCost + tripCost + vehicleCost;

        // Write the total cost to a file in the output directory
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(scenario.getConfig().controler().getOutputDirectory(), "operator_costs.txt")))) {
            writer.write("Total Operator Cost: " + totalCost);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private double calculateTotalDRTDistance() {
        // Implement the logic to calculate total DRT distance
        return 0.0; // Placeholder value
    }

    private int calculateTotalDRTTrips() {
        // Implement the logic to calculate total number of DRT trips
        return 0; // Placeholder value
    }

    private int getTotalDRTVehicles() {
        // Implement the logic to get the total number of DRT vehicles
        return 0; // Placeholder value
    }
}
