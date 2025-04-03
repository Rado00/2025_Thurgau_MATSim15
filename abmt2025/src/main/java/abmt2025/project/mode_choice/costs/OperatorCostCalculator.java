package abmt2025.project.mode_choice.costs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import org.matsim.api.core.v01.Scenario;
import abmt2025.project.mode_choice.DrtCostParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OperatorCostCalculator {
    private static final Logger LOG = LogManager.getLogger(OperatorCostCalculator.class);
    private DrtCostParameters costParameters;
    private Scenario scenario;

    public OperatorCostCalculator(Scenario scenario, DrtCostParameters costParameters) {
        this.scenario = scenario;
        this.costParameters = costParameters;
    }

    public void calculateAndWriteOperatorCosts() {
        try {
            // Calculate values
            double totalDistance = calculateTotalDRTDistance();
            LOG.info("Total distance is " + totalDistance);

            int totalTrips = calculateTotalDRTTrips();
            LOG.info("Total Trips is " + totalTrips);

            int totalVehicles = getTotalDRTVehicles();
            LOG.info("Total Vehicles is " + totalVehicles);

            double distanceCost = totalDistance * costParameters.DRTFare_CHF_km;
            double tripCost = totalTrips * costParameters.DRTCost_CHF_trip;
            double vehicleCost = totalVehicles * costParameters.DRTCost_CHF_vehicle;
            LOG.info("Vehicle Cost is " + vehicleCost);

            double totalCost = distanceCost + tripCost + vehicleCost;
            LOG.info("Total Cost is " + totalCost);
            LOG.info("Directory Path is " + scenario.getConfig().controller().getOutputDirectory());

            // Write the total cost to a file in the output directory
            try (BufferedWriter writer = new BufferedWriter(
                    new FileWriter(new File(scenario.getConfig().controller().getOutputDirectory(), "operator_costs.txt")))) {
                writer.write("Total Operator Cost: " + totalCost);
            } catch (IOException e) {
                LOG.warn("Error writing to file: ", e);
            }

            LOG.info("Operator cost calculation completed successfully.");

        } catch (Exception e) {
            LOG.error("Unexpected error occurred: ", e);
        }
    }

    private double calculateTotalDRTDistance() {
        return scenario.getPopulation().getPersons().values().stream()
            .flatMap(person -> person.getSelectedPlan().getPlanElements().stream())
            .filter(element -> element instanceof org.matsim.api.core.v01.population.Leg)
            .map(element -> (org.matsim.api.core.v01.population.Leg) element)
            .filter(leg -> leg.getMode().equals("drt"))
            .mapToDouble(leg -> leg.getRoute().getDistance())
            .sum();
    }

    private int calculateTotalDRTTrips() {
        return (int) scenario.getPopulation().getPersons().values().stream()
            .flatMap(person -> person.getSelectedPlan().getPlanElements().stream())
            .filter(element -> element instanceof org.matsim.api.core.v01.population.Leg)
            .map(element -> (org.matsim.api.core.v01.population.Leg) element)
            .filter(leg -> leg.getMode().equals("drt"))
            .count();
    }   

    private int getTotalDRTVehicles() {
        return (int) scenario.getPopulation().getPersons().values().stream()
            .flatMap(person -> person.getSelectedPlan().getPlanElements().stream())
            .filter(element -> element instanceof org.matsim.api.core.v01.population.Leg)
            .map(element -> (org.matsim.api.core.v01.population.Leg) element)
            .filter(leg -> leg.getMode().equals("drt"))
            .map(leg -> leg.getRoute().getStartLinkId()) // Assuming one vehicle per unique start point
            .distinct()
            .count();
    }
}
