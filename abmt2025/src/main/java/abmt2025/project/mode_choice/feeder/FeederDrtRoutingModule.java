package abmt2025.project.mode_choice.feeder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.RoutingRequest;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.facilities.Facility;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.opengis.feature.simple.SimpleFeature;

import com.google.inject.Inject;

/**
 * Routing module for feeder DRT trips.
 *
 * Routes trips as: [DRT access] -> PT interaction -> [PT leg(s)] -> PT interaction -> [DRT/Walk egress]
 *
 * Uses the existing DRT and PT routing modules to construct the complete trip.
 * Falls back to PT-only if DRT routing fails or if origin/destination is close to PT stop.
 */
public class FeederDrtRoutingModule implements RoutingModule {
    private static final Logger log = LogManager.getLogger(FeederDrtRoutingModule.class);

    public static final String FEEDER_DRT_MODE = "feeder_drt";

    // Maximum distance for DRT legs to prevent memory issues with long-distance routing
    private static final double MAX_DRT_LEG_DISTANCE = 5000.0; // 5km max for DRT leg

    // Train station category threshold (stopCategory <= this value is considered a train station)
    private static final int TRAIN_STATION_CATEGORY_THRESHOLD = 3;

    // Counters for debugging
    private int routingAttempts = 0;
    private int successfulRoutes = 0;
    private int drtDistanceSkips = 0;
    private int trainStationRoutesChosen = 0;
    private int drtServiceAreaSkips = 0;

    // DRT service area geometry (for checking if points are inside service area)
    private final Geometry drtServiceArea;

    private final RoutingModule drtRoutingModule;
    private final RoutingModule ptRoutingModule;
    private final RoutingModule walkRoutingModule;
    private final PopulationFactory populationFactory;
    private final TransitSchedule transitSchedule;
    private final Network network;
    private final double maxAccessEgressDistance;
    private final boolean useTrainStationRouting;

    @Inject
    public FeederDrtRoutingModule(
            RoutingModule drtRoutingModule,
            RoutingModule ptRoutingModule,
            RoutingModule walkRoutingModule,
            PopulationFactory populationFactory,
            TransitSchedule transitSchedule,
            Network network,
            FeederDrtConfigGroup config) {
        this.drtRoutingModule = drtRoutingModule;
        this.ptRoutingModule = ptRoutingModule;
        this.walkRoutingModule = walkRoutingModule;
        this.populationFactory = populationFactory;
        this.transitSchedule = transitSchedule;
        this.network = network;
        this.maxAccessEgressDistance = config.getMaxAccessEgressDistance_m();

        // Load DRT service area shape file if configured
        String shapeFilePath = config.getDrtServiceAreaShapeFile();
        Geometry loadedServiceArea = null;
        if (shapeFilePath != null && !shapeFilePath.isEmpty()) {
            try {
                Collection<SimpleFeature> features = ShapeFileReader.getAllFeatures(shapeFilePath);
                loadedServiceArea = (Geometry) features.iterator().next().getDefaultGeometry();
                log.info("Loaded DRT service area from: {}", shapeFilePath);
            } catch (Exception e) {
                log.warn("Could not load DRT service area shape file: {}. Service area check disabled.", e.getMessage());
            }
        } else {
            log.info("No DRT service area shape file configured. Service area check disabled.");
        }
        this.drtServiceArea = loadedServiceArea;
        this.useTrainStationRouting = config.isUseTrainStationRouting();

        log.info("FeederDrtRoutingModule initialized with maxAccessEgressDistance={} m, {} transit stops available, serviceAreaCheck={}, trainStationRouting={}",
                maxAccessEgressDistance, transitSchedule.getFacilities().size(), drtServiceArea != null, useTrainStationRouting);
    }

    @Override
    public List<? extends PlanElement> calcRoute(RoutingRequest request) {
        return calcRoute(request.getFromFacility(), request.getToFacility(),
                request.getDepartureTime(), request.getPerson());
    }

    public List<? extends PlanElement> calcRoute(Facility fromFacility, Facility toFacility,
            double departureTime, Person person) {

        routingAttempts++;
        // Log every 100 attempts to show progress
        if (routingAttempts % 100 == 0) {
            log.info("Feeder DRT routing: {} attempts, {} successful ({}%), {} via train station, {} distance skips, {} service area skips",
                    routingAttempts, successfulRoutes,
                    String.format("%.1f", routingAttempts > 0 ? (100.0 * successfulRoutes / routingAttempts) : 0),
                    trainStationRoutesChosen, drtDistanceSkips, drtServiceAreaSkips);
        }

        try {
            Coord originCoord = getCoord(fromFacility);
            Coord destCoord = getCoord(toFacility);

            // Find both nearest stop (any type) and nearest train station
            TransitStopFacility nearestAccessStop = findNearestStop(originCoord);
            TransitStopFacility nearestEgressStop = findNearestStop(destCoord);
            TransitStopFacility nearestAccessTrainStation = findNearestTrainStation(originCoord);
            TransitStopFacility nearestEgressTrainStation = findNearestTrainStation(destCoord);

            // DEBUG: Log stop finding results
            if (routingAttempts <= 20 || routingAttempts % 500 == 0) {
                log.info("Stop search for person {}: origin=({},{}), dest=({},{})",
                        person.getId(),
                        String.format("%.0f", originCoord.getX()), String.format("%.0f", originCoord.getY()),
                        String.format("%.0f", destCoord.getX()), String.format("%.0f", destCoord.getY()));
                log.info("  nearestAccessStop={} at ({},{})",
                        nearestAccessStop != null ? nearestAccessStop.getId() : "null",
                        nearestAccessStop != null ? String.format("%.0f", nearestAccessStop.getCoord().getX()) : "-",
                        nearestAccessStop != null ? String.format("%.0f", nearestAccessStop.getCoord().getY()) : "-");
                log.info("  nearestEgressStop={} at ({},{})",
                        nearestEgressStop != null ? nearestEgressStop.getId() : "null",
                        nearestEgressStop != null ? String.format("%.0f", nearestEgressStop.getCoord().getX()) : "-",
                        nearestEgressStop != null ? String.format("%.0f", nearestEgressStop.getCoord().getY()) : "-");
                log.info("  nearestAccessTrainStation={} at ({},{})",
                        nearestAccessTrainStation != null ? nearestAccessTrainStation.getId() : "null",
                        nearestAccessTrainStation != null ? String.format("%.0f", nearestAccessTrainStation.getCoord().getX()) : "-",
                        nearestAccessTrainStation != null ? String.format("%.0f", nearestAccessTrainStation.getCoord().getY()) : "-");
                log.info("  nearestEgressTrainStation={} at ({},{})",
                        nearestEgressTrainStation != null ? nearestEgressTrainStation.getId() : "null",
                        nearestEgressTrainStation != null ? String.format("%.0f", nearestEgressTrainStation.getCoord().getX()) : "-",
                        nearestEgressTrainStation != null ? String.format("%.0f", nearestEgressTrainStation.getCoord().getY()) : "-");
            }

            // Try routing via nearest stops (any type)
            List<? extends PlanElement> routeViaNearest = null;
            double travelTimeNearest = Double.MAX_VALUE;
            if (nearestAccessStop != null && nearestEgressStop != null) {
                routeViaNearest = computeFeederRoute(fromFacility, toFacility, departureTime, person,
                        nearestAccessStop, nearestEgressStop);
                if (routeViaNearest != null) {
                    travelTimeNearest = getTotalTravelTime(routeViaNearest);
                }
            }

            // Try routing via train stations (if enabled)
            List<? extends PlanElement> routeViaTrain = null;
            double travelTimeTrain = Double.MAX_VALUE;
            if (useTrainStationRouting && nearestAccessTrainStation != null && nearestEgressTrainStation != null) {
                // Only try if train stations are different from nearest stops
                boolean accessIsDifferent = nearestAccessStop == null ||
                        !nearestAccessTrainStation.getId().equals(nearestAccessStop.getId());
                boolean egressIsDifferent = nearestEgressStop == null ||
                        !nearestEgressTrainStation.getId().equals(nearestEgressStop.getId());

                if (accessIsDifferent || egressIsDifferent) {
                    routeViaTrain = computeFeederRoute(fromFacility, toFacility, departureTime, person,
                            nearestAccessTrainStation, nearestEgressTrainStation);
                    if (routeViaTrain != null) {
                        travelTimeTrain = getTotalTravelTime(routeViaTrain);
                    }
                }
            }

            // Choose the best route (shorter travel time)
            List<? extends PlanElement> bestRoute = null;
            boolean usedTrainStation = false;

            if (routeViaNearest != null && routeViaTrain != null) {
                if (travelTimeTrain < travelTimeNearest) {
                    bestRoute = routeViaTrain;
                    usedTrainStation = true;
                } else {
                    bestRoute = routeViaNearest;
                }
            } else if (routeViaTrain != null) {
                bestRoute = routeViaTrain;
                usedTrainStation = true;
            } else if (routeViaNearest != null) {
                bestRoute = routeViaNearest;
            }

            if (bestRoute == null) {
                log.debug("Could not compute any feeder route");
                return null;
            }

            successfulRoutes++;
            if (usedTrainStation) {
                trainStationRoutesChosen++;
            }

            // Log first few successful routes at INFO level for visibility
            if (successfulRoutes <= 5) {
                log.info("SUCCESS: Feeder DRT route created for person {} (route #{}) - {} legs, via {}",
                        person.getId(), successfulRoutes, bestRoute.size(),
                        usedTrainStation ? "train station" : "nearest stop");
            }

            return bestRoute;

        } catch (Exception e) {
            log.debug("Error in feeder DRT routing: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Compute a feeder route via specified access and egress stops.
     * Returns null if route cannot be computed or is invalid.
     */
    private List<? extends PlanElement> computeFeederRoute(Facility fromFacility, Facility toFacility,
            double departureTime, Person person,
            TransitStopFacility accessStop, TransitStopFacility egressStop) {

        List<PlanElement> route = new ArrayList<>();
        Coord originCoord = getCoord(fromFacility);
        Coord destCoord = getCoord(toFacility);

        double distanceToAccessStop = CoordUtils.calcEuclideanDistance(originCoord, accessStop.getCoord());
        double distanceFromEgressStop = CoordUtils.calcEuclideanDistance(egressStop.getCoord(), destCoord);

        double currentTime = departureTime;
        final double MAX_WALK_FALLBACK_DISTANCE = 2000.0; // 2km max walk

        // ACCESS LEG: DRT or walk from origin to access stop
        if (distanceToAccessStop > 500) {
            List<? extends PlanElement> accessLeg = routeDrtLeg(fromFacility, accessStop, currentTime, person);
            if (accessLeg != null && !accessLeg.isEmpty()) {
                route.addAll(accessLeg);
                currentTime = getArrivalTime(accessLeg, currentTime);
            } else {
                if (distanceToAccessStop > MAX_WALK_FALLBACK_DISTANCE) {
                    return null;
                }
                List<? extends PlanElement> walkAccess = walkRoutingModule.calcRoute(
                        DefaultRoutingRequest.withoutAttributes(fromFacility, createFacility(accessStop), currentTime, person));
                if (walkAccess != null) {
                    route.addAll(walkAccess);
                    currentTime = getArrivalTime(walkAccess, currentTime);
                }
            }
        } else {
            List<? extends PlanElement> walkAccess = walkRoutingModule.calcRoute(
                    DefaultRoutingRequest.withoutAttributes(fromFacility, createFacility(accessStop), currentTime, person));
            if (walkAccess != null) {
                route.addAll(walkAccess);
                currentTime = getArrivalTime(walkAccess, currentTime);
            }
        }

        // Add feeder interaction activity
        Activity feederInteractionAccess = populationFactory.createActivityFromCoord(
                "feeder interaction", accessStop.getCoord());
        feederInteractionAccess.setMaximumDuration(0);
        route.add(feederInteractionAccess);

        // PT LEG: from access stop to egress stop
        List<? extends PlanElement> ptLeg = null;
        try {
            ptLeg = ptRoutingModule.calcRoute(
                    DefaultRoutingRequest.withoutAttributes(createFacility(accessStop), createFacility(egressStop), currentTime, person));
        } catch (Exception e) {
            return null;
        }

        if (ptLeg == null || ptLeg.isEmpty()) {
            return null;
        }

        boolean hasValidPtLeg = ptLeg.stream()
                .filter(e -> e instanceof Leg)
                .map(e -> (Leg) e)
                .anyMatch(l -> l.getMode().equals(TransportMode.pt) || l.getMode().equals(TransportMode.transit_walk));

        if (!hasValidPtLeg) {
            return null;
        }

        route.addAll(ptLeg);
        currentTime = getArrivalTime(ptLeg, currentTime);

        // Add feeder interaction activity
        Activity feederInteractionEgress = populationFactory.createActivityFromCoord(
                "feeder interaction", egressStop.getCoord());
        feederInteractionEgress.setMaximumDuration(0);
        route.add(feederInteractionEgress);

        // EGRESS LEG: DRT or walk from egress stop to destination
        if (distanceFromEgressStop > 500) {
            List<? extends PlanElement> egressLeg = routeDrtLeg(egressStop, toFacility, currentTime, person);
            if (egressLeg != null && !egressLeg.isEmpty()) {
                route.addAll(egressLeg);
            } else {
                if (distanceFromEgressStop > MAX_WALK_FALLBACK_DISTANCE) {
                    return null;
                }
                List<? extends PlanElement> walkEgress = walkRoutingModule.calcRoute(
                        DefaultRoutingRequest.withoutAttributes(createFacility(egressStop), toFacility, currentTime, person));
                if (walkEgress != null) {
                    route.addAll(walkEgress);
                }
            }
        } else {
            List<? extends PlanElement> walkEgress = walkRoutingModule.calcRoute(
                    DefaultRoutingRequest.withoutAttributes(createFacility(egressStop), toFacility, currentTime, person));
            if (walkEgress != null) {
                route.addAll(walkEgress);
            }
        }

        // Check if we actually have a DRT leg in the route
        boolean hasDrt = route.stream()
                .filter(e -> e instanceof Leg)
                .map(e -> (Leg) e)
                .anyMatch(l -> l.getMode().equals(TransportMode.drt));

        if (!hasDrt) {
            return null;
        }

        return route;
    }

    /**
     * Get total travel time from a list of plan elements.
     */
    private double getTotalTravelTime(List<? extends PlanElement> elements) {
        double totalTime = 0;
        for (PlanElement element : elements) {
            if (element instanceof Leg) {
                Leg leg = (Leg) element;
                if (leg.getTravelTime().isDefined()) {
                    totalTime += leg.getTravelTime().seconds();
                }
            }
        }
        return totalTime;
    }

    /**
     * Route a DRT leg between two locations.
     * Checks distance and service area before attempting routing to prevent memory issues.
     */
    private List<? extends PlanElement> routeDrtLeg(Object from, Object to, double departureTime, Person person) {
        try {
            Facility fromFac = (from instanceof Facility) ? (Facility) from : createFacility((TransitStopFacility) from);
            Facility toFac = (to instanceof Facility) ? (Facility) to : createFacility((TransitStopFacility) to);

            // Check distance before attempting DRT routing to prevent OOM
            Coord fromCoord = getCoord(fromFac);
            Coord toCoord = getCoord(toFac);
            double distance = CoordUtils.calcEuclideanDistance(fromCoord, toCoord);

            if (distance > MAX_DRT_LEG_DISTANCE) {
                drtDistanceSkips++;
                if (drtDistanceSkips <= 10 || drtDistanceSkips % 100 == 0) {
                    log.debug("DRT leg distance {} m exceeds max {} m, skipping (skip count: {})",
                            String.format("%.0f", distance), MAX_DRT_LEG_DISTANCE, drtDistanceSkips);
                }
                return null;
            }

            // Check if both points are inside DRT service area (if shape file is configured)
            if (drtServiceArea != null) {
                boolean fromInside = isInsideDrtServiceArea(fromCoord);
                boolean toInside = isInsideDrtServiceArea(toCoord);
                if (!fromInside || !toInside) {
                    drtServiceAreaSkips++;
                    if (drtServiceAreaSkips <= 10 || drtServiceAreaSkips % 100 == 0) {
                        log.debug("DRT leg outside service area: from={}, to={}, skipping (skip count: {})",
                                fromInside ? "inside" : "OUTSIDE",
                                toInside ? "inside" : "OUTSIDE",
                                drtServiceAreaSkips);
                    }
                    return null;
                }
            }

            // DEBUG: Log before DRT routing to identify problematic calls
            log.info("DRT routing: person={}, from=({},{}), to=({},{}), dist={}m",
                    person != null ? person.getId() : "null",
                    String.format("%.0f", fromCoord.getX()), String.format("%.0f", fromCoord.getY()),
                    String.format("%.0f", toCoord.getX()), String.format("%.0f", toCoord.getY()),
                    String.format("%.0f", distance));

            List<? extends PlanElement> result = drtRoutingModule.calcRoute(DefaultRoutingRequest.withoutAttributes(fromFac, toFac, departureTime, person));

            log.info("DRT routing completed: person={}, result={}",
                    person != null ? person.getId() : "null",
                    result != null ? result.size() + " elements" : "null");

            return result;
        } catch (Exception e) {
            log.warn("DRT routing failed for person {}: {}",
                    person != null ? person.getId() : "null", e.getMessage());
            return null;
        }
    }

    /**
     * Check if a coordinate is inside the DRT service area.
     */
    private boolean isInsideDrtServiceArea(Coord coord) {
        if (drtServiceArea == null) {
            return true; // No service area check if not configured
        }
        try {
            return drtServiceArea.contains(MGC.coord2Point(coord));
        } catch (Exception e) {
            log.debug("Error checking service area: {}", e.getMessage());
            return false; // Be conservative - don't route if check fails
        }
    }

    /**
     * Fall back to regular PT routing.
     */
    private List<? extends PlanElement> fallbackToPt(Facility from, Facility to, double departureTime, Person person) {
        try {
            return ptRoutingModule.calcRoute(DefaultRoutingRequest.withoutAttributes(from, to, departureTime, person));
        } catch (Exception e) {
            log.debug("PT fallback routing failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Find the nearest transit stop to a coordinate.
     */
    private TransitStopFacility findNearestStop(Coord coord) {
        TransitStopFacility nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (TransitStopFacility stop : transitSchedule.getFacilities().values()) {
            double distance = CoordUtils.calcEuclideanDistance(coord, stop.getCoord());
            if (distance < minDistance && distance <= maxAccessEgressDistance) {
                minDistance = distance;
                nearest = stop;
            }
        }

        return nearest;
    }

    /**
     * Find the nearest train station to a coordinate.
     * Train stations are identified by stopCategory attribute <= TRAIN_STATION_CATEGORY_THRESHOLD (1-3).
     * Category 1 = Major stations (Hbf), 2 = Important stations, 3 = Regional stations.
     */
    private TransitStopFacility findNearestTrainStation(Coord coord) {
        TransitStopFacility nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (TransitStopFacility stop : transitSchedule.getFacilities().values()) {
            // Check if this is a train station based on stopCategory attribute
            Object categoryAttr = stop.getAttributes().getAttribute("stopCategory");
            if (categoryAttr == null) {
                continue; // Skip stops without category
            }

            int category;
            if (categoryAttr instanceof Integer) {
                category = (Integer) categoryAttr;
            } else {
                try {
                    category = Integer.parseInt(categoryAttr.toString());
                } catch (NumberFormatException e) {
                    continue; // Skip if category is not a valid number
                }
            }

            // Only consider train stations (category 1-3)
            if (category > TRAIN_STATION_CATEGORY_THRESHOLD) {
                continue;
            }

            double distance = CoordUtils.calcEuclideanDistance(coord, stop.getCoord());
            if (distance < minDistance && distance <= maxAccessEgressDistance) {
                minDistance = distance;
                nearest = stop;
            }
        }

        return nearest;
    }

    /**
     * Get coordinate from a facility.
     */
    private Coord getCoord(Facility facility) {
        if (facility.getCoord() != null) {
            return facility.getCoord();
        }
        if (facility.getLinkId() != null && network.getLinks().containsKey(facility.getLinkId())) {
            return network.getLinks().get(facility.getLinkId()).getCoord();
        }
        throw new RuntimeException("Cannot determine coordinate for facility");
    }

    /**
     * Create a facility wrapper for a transit stop.
     */
    private Facility createFacility(TransitStopFacility stop) {
        return new Facility() {
            @Override
            public Coord getCoord() {
                return stop.getCoord();
            }

            @Override
            public org.matsim.api.core.v01.Id getLinkId() {
                return stop.getLinkId();
            }

            @Override
            public java.util.Map<String, Object> getCustomAttributes() {
                return java.util.Collections.emptyMap();
            }
        };
    }

    /**
     * Get arrival time from a list of plan elements.
     */
    private double getArrivalTime(List<? extends PlanElement> elements, double departureTime) {
        double time = departureTime;
        for (PlanElement element : elements) {
            if (element instanceof Leg) {
                Leg leg = (Leg) element;
                if (leg.getTravelTime().isDefined()) {
                    time += leg.getTravelTime().seconds();
                }
            }
        }
        return time;
    }
}
