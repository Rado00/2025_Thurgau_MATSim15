package abmt2023.project.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.DvrpVehicleSpecification;
import org.matsim.contrib.dvrp.fleet.FleetWriter;
import org.matsim.contrib.dvrp.fleet.ImmutableDvrpVehicleSpecification;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;

public class GenerateDrtVehiclesCustom {

    public static void main(String[] args) throws IOException {
       // Variable for percentage part of the path
       String pctValue = "10pct"; // You can change this to "10pct", "100pct", etc.

       // Construct file paths using the pctValue variable
    //    String networkfile = "/cluster/scratch/cmuratori/data/scenarios/" + pctValue + "/zurich_network.xml.gz";
       String networkfile = "C:/Users/muaa/Documents/3_MIEI/2023_ABMT_Data/Zurich/" + pctValue + "/zurich_network.xml.gz";
    //    String outputPath = "/cluster/scratch/cmuratori/data/scenarios/" + pctValue;
    String outputPath = "C:/Users/muaa/Documents/3_MIEI/2023_ABMT_Data/Zurich/" + pctValue;


       String nameSuffix = "drt_vehicles";
       int numberOfVehicles = 1200;
       double operationStartTime = 0.0; // t0
       double operationEndTime = 86400.0; // t1 (24 hours)
       int seats = 12; // Number of seats per vehicle: (Make shure there is no pooling seats = 1)

        String taxisFile = outputPath + "/" + nameSuffix + "_" + numberOfVehicles + "_" + seats + ".xml";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Network network = scenario.getNetwork();
        new MatsimNetworkReader(network).readFile(networkfile);

        List<DvrpVehicleSpecification> vehicles = new ArrayList<>();
        Random random = MatsimRandom.getLocalInstance();

        List<Id<Link>> allLinks = new ArrayList<>(network.getLinks().keySet());

        for (int i = 0; i < numberOfVehicles; i++) {
            Link startLink;
            do {
                // Randomly select link ID as start link from list of all links
                Id<Link> linkId = allLinks.get(random.nextInt(allLinks.size()));
                startLink = network.getLinks().get(linkId);
            } while (!startLink.getAllowedModes().contains(TransportMode.car));

            // For multi-modal networks: Only links where cars can ride should be used.
            vehicles.add(ImmutableDvrpVehicleSpecification.newBuilder()
                    .id(Id.create("drt" + i, DvrpVehicle.class))
                    .startLinkId(startLink.getId())
                    .capacity(seats)
                    .serviceBeginTime(operationStartTime)
                    .serviceEndTime(operationEndTime)
                    .build());
        }

        new FleetWriter(vehicles.stream()).write(taxisFile);

        System.out.println("Drt vehicle generation completed");
    }
}
