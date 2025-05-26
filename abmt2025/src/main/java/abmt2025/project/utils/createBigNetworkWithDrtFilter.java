package abmt2025.project.utils;


import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;

import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;

import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureIterator;

import java.util.HashSet;
import java.util.Set;

import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;

public class createBigNetworkWithDrtFilter {

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java createBigNetworkWithDrtFilter <inputNetwork.xml.gz> <shapefile.shp> <outputNetwork.xml.gz>");
            System.exit(1);
        }

        String inputNetworkFile = args[0];
        String shapeFile = args[1];
        String outputNetworkFile = args[2];

        // Load MATSim config and scenario
        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(inputNetworkFile);
        Network network = scenario.getNetwork();

        // Read shapefile and extract merged geometry
        Geometry targetArea = null;
        try {
            SimpleFeatureSource featureSource = ShapeFileReader.readDataFile(shapeFile);
            SimpleFeatureCollection featureCollection = featureSource.getFeatures();
            try (FeatureIterator<SimpleFeature> features = featureCollection.features()) {
                while (features.hasNext()) {
                    SimpleFeature feature = features.next();
                    Geometry geometry = (Geometry) feature.getDefaultGeometry();
                    if (geometry != null) {
                        if (targetArea == null) {
                            targetArea = geometry;
                        } else {
                            targetArea = targetArea.union(geometry);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to read shapefile: " + shapeFile);
            System.exit(1);
        }

        if (targetArea == null) {
            System.err.println("No valid geometries found in shapefile.");
            System.exit(1);
        }

        // Iterate through all links and add 'drt' mode where appropriate
        for (Link link : network.getLinks().values()) {
            Coord coord = link.getToNode().getCoord(); // or link.getFromNode().getCoord()
            Point point = MGC.coord2Point(coord);

            if (targetArea.contains(point)) {
                Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
                if (allowedModes.contains("car") && !allowedModes.contains("drt")) {
                    allowedModes.add("drt");
                    link.setAllowedModes(allowedModes);
                }
            }
        }

                // Step 1: Create a new subnetwork with only drt links
        Network drtSubnetwork = NetworkUtils.createNetwork();

        // First, add all nodes referenced by drt links to the subnetwork
        network.getLinks().values().stream()
            .filter(link -> link.getAllowedModes().contains("drt"))
            .forEach(link -> {
                if (!drtSubnetwork.getNodes().containsKey(link.getFromNode().getId())) {
                    drtSubnetwork.addNode(link.getFromNode());
                }
                if (!drtSubnetwork.getNodes().containsKey(link.getToNode().getId())) {
                    drtSubnetwork.addNode(link.getToNode());
                }
            });

        for (Link link : network.getLinks().values()) {
            if (link.getAllowedModes().contains("drt")) {
                NetworkUtils.createAndAddLink(
                    drtSubnetwork,
                    link.getId(),
                    link.getFromNode(),
                    link.getToNode(),
                    link.getLength(),
                    link.getFreespeed(),
                    link.getCapacity(),
                    link.getNumberOfLanes()
                ).setAllowedModes(Set.of("drt"));
            }
        }

        // Step 2: Clean the DRT subnetwork (keep only largest connected component)
        new NetworkCleaner().run(drtSubnetwork);

        // Step 3: Collect IDs of all valid drt links
        Set<String> connectedDrtLinkIds = new HashSet<>();
        for (Link link : drtSubnetwork.getLinks().values()) {
            connectedDrtLinkIds.add(link.getId().toString());
        }

        // Step 4: Apply the clean result back to the original network
        for (Link link : network.getLinks().values()) {
            if (link.getAllowedModes().contains("drt")
                && !connectedDrtLinkIds.contains(link.getId().toString())) {
                // Remove drt mode from disconnected links
                Set<String> newModes = new HashSet<>(link.getAllowedModes());
                newModes.remove("drt");
                link.setAllowedModes(newModes);
            }
        }
        // Write the updated network to file
        new NetworkWriter(network).write(outputNetworkFile);
        System.out.println("Modified network written to: " + outputNetworkFile);
    }
}
