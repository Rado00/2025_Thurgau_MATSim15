package abmt2025.project.utils;

import org.matsim.api.core.v01.network.*;
import org.matsim.api.core.v01.*;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.opengis.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;

import java.util.Collection;

public class cutNetwork {

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: java cutNetwork <inputNetwork> <shapefile> <outputNetwork>");
            return;
        }

        String inputNetwork = args[0];
        String shapefile = args[1];
        String outputNetwork = args[2];
        // Load the scenario and original network
        Scenario scenario = ScenarioUtils.createScenario(org.matsim.core.config.ConfigUtils.createConfig());
        Network originalNetwork = NetworkUtils.readNetwork(inputNetwork);

        // Load shape file
        Collection<SimpleFeature> features = ShapeFileReader.getAllFeatures(shapefile);
        Geometry shape = (Geometry) features.iterator().next().getDefaultGeometry();

        // Create new clipped network
        Network clippedNetwork = NetworkUtils.createNetwork();
        NetworkFactory factory = clippedNetwork.getFactory();

        // Filter and add nodes inside shape
        for (Node node : originalNetwork.getNodes().values()) {
            Coord coord = node.getCoord();
            if (shape.contains(MGC.coord2Point(coord))) {
                Node newNode = factory.createNode(node.getId(), coord);
                clippedNetwork.addNode(newNode);
            }
        }

        // Filter and add links connecting kept nodes
        for (Link link : originalNetwork.getLinks().values()) {
            Id<Node> fromId = link.getFromNode().getId();
            Id<Node> toId = link.getToNode().getId();

            if (clippedNetwork.getNodes().containsKey(fromId) && clippedNetwork.getNodes().containsKey(toId)) {
                Link newLink = factory.createLink(link.getId(),
                        clippedNetwork.getNodes().get(fromId),
                        clippedNetwork.getNodes().get(toId));
                newLink.setLength(link.getLength());
                newLink.setFreespeed(link.getFreespeed());
                newLink.setCapacity(link.getCapacity());
                newLink.setNumberOfLanes(link.getNumberOfLanes());
                newLink.setAllowedModes(link.getAllowedModes());

                clippedNetwork.addLink(newLink);
            }
        }

        // Write the clipped network
        new NetworkWriter(clippedNetwork).write(outputNetwork);
        System.out.println("Clipped network written to: " + outputNetwork);
    }
}
