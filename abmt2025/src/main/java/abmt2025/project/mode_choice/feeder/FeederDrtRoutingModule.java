package abmt2025.project.mode_choice.feeder;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.matsim.facilities.Facility;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

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

    // Counters for debugging
    private int routingAttempts = 0;
    private int successfulRoutes = 0;

    private final RoutingModule drtRoutingModule;
    private final RoutingModule ptRoutingModule;
    private final RoutingModule walkRoutingModule;
    private final PopulationFactory populationFactory;
    private final TransitSchedule transitSchedule;
    private final Network network;
    private final double maxAccessEgressDistance;

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

        log.info("FeederDrtRoutingModule initialized with maxAccessEgressDistance={} m, {} transit stops available",
                maxAccessEgressDistance, transitSchedule.getFacilities().size());
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
            log.info("Feeder DRT routing: {} attempts, {} successful routes ({} %)",
                    routingAttempts, successfulRoutes,
                    routingAttempts > 0 ? (100.0 * successfulRoutes / routingAttempts) : 0);
        }

        List<PlanElement> route = new ArrayList<>();

        try {
            Coord originCoord = getCoord(fromFacility);
            Coord destCoord = getCoord(toFacility);

            // Find nearest PT stops to origin and destination
            TransitStopFacility accessStop = findNearestStop(originCoord);
            TransitStopFacility egressStop = findNearestStop(destCoord);

            if (accessStop == null || egressStop == null) {
                log.debug("Could not find PT stops for feeder DRT routing, falling back to PT");
                return fallbackToPt(fromFacility, toFacility, departureTime, person);
            }

            // Check if origin is close enough for walk access instead of DRT
            double distanceToAccessStop = CoordUtils.calcEuclideanDistance(originCoord, accessStop.getCoord());
            double distanceFromEgressStop = CoordUtils.calcEuclideanDistance(egressStop.getCoord(), destCoord);

            double currentTime = departureTime;

            // Maximum walk distance for fallback - don't create unrealistic routes
            final double MAX_WALK_FALLBACK_DISTANCE = 2000.0; // 2km max walk

            // ACCESS LEG: DRT or walk from origin to access stop
            if (distanceToAccessStop > 500) { // Use DRT if > 500m from stop
                // Try DRT access
                List<? extends PlanElement> accessLeg = routeDrtLeg(fromFacility, accessStop, currentTime, person);
                if (accessLeg != null && !accessLeg.isEmpty()) {
                    route.addAll(accessLeg);
                    currentTime = getArrivalTime(accessLeg, currentTime);
                } else {
                    // DRT failed - check if walk is realistic
                    if (distanceToAccessStop > MAX_WALK_FALLBACK_DISTANCE) {
                        // Too far to walk and DRT not available - feeder_drt not viable
                        log.debug("Access too far ({} m) and DRT not available, returning null", distanceToAccessStop);
                        return null;
                    }
                    // Fall back to walk (within reasonable distance)
                    List<? extends PlanElement> walkAccess = walkRoutingModule.calcRoute(
                            DefaultRoutingRequest.withoutAttributes(fromFacility, createFacility(accessStop), currentTime, person));
                    if (walkAccess != null) {
                        route.addAll(walkAccess);
                        currentTime = getArrivalTime(walkAccess, currentTime);
                    }
                }
            } else {
                // Walk access (short distance, always OK)
                List<? extends PlanElement> walkAccess = walkRoutingModule.calcRoute(
                        DefaultRoutingRequest.withoutAttributes(fromFacility, createFacility(accessStop), currentTime, person));
                if (walkAccess != null) {
                    route.addAll(walkAccess);
                    currentTime = getArrivalTime(walkAccess, currentTime);
                }
            }

            // Add PT interaction activity
            Activity ptInteractionAccess = populationFactory.createActivityFromCoord(
                    "pt interaction", accessStop.getCoord());
            ptInteractionAccess.setMaximumDuration(0);
            route.add(ptInteractionAccess);

            // PT LEG: from access stop to egress stop
            List<? extends PlanElement> ptLeg = null;
            try {
                ptLeg = ptRoutingModule.calcRoute(
                        DefaultRoutingRequest.withoutAttributes(createFacility(accessStop), createFacility(egressStop), currentTime, person));
            } catch (Exception e) {
                log.debug("PT routing threw exception: {}", e.getMessage());
                return null;
            }

            if (ptLeg == null || ptLeg.isEmpty()) {
                log.debug("PT routing returned null or empty, returning null");
                return null;
            }

            // Validate PT route has required elements
            boolean hasValidPtLeg = ptLeg.stream()
                    .filter(e -> e instanceof Leg)
                    .map(e -> (Leg) e)
                    .anyMatch(l -> l.getMode().equals(TransportMode.pt) || l.getMode().equals(TransportMode.transit_walk));

            if (!hasValidPtLeg) {
                log.debug("PT routing returned route without PT legs");
                return null;
            }

            route.addAll(ptLeg);
            currentTime = getArrivalTime(ptLeg, currentTime);

            // Add PT interaction activity
            Activity ptInteractionEgress = populationFactory.createActivityFromCoord(
                    "pt interaction", egressStop.getCoord());
            ptInteractionEgress.setMaximumDuration(0);
            route.add(ptInteractionEgress);

            // EGRESS LEG: DRT or walk from egress stop to destination
            if (distanceFromEgressStop > 500) { // Use DRT if > 500m from stop
                // Try DRT egress
                List<? extends PlanElement> egressLeg = routeDrtLeg(egressStop, toFacility, currentTime, person);
                if (egressLeg != null && !egressLeg.isEmpty()) {
                    route.addAll(egressLeg);
                } else {
                    // DRT failed - check if walk is realistic
                    if (distanceFromEgressStop > MAX_WALK_FALLBACK_DISTANCE) {
                        // Too far to walk and DRT not available - feeder_drt not viable
                        log.debug("Egress too far ({} m) and DRT not available, returning null", distanceFromEgressStop);
                        return null;
                    }
                    // Fall back to walk (within reasonable distance)
                    List<? extends PlanElement> walkEgress = walkRoutingModule.calcRoute(
                            DefaultRoutingRequest.withoutAttributes(createFacility(egressStop), toFacility, currentTime, person));
                    if (walkEgress != null) {
                        route.addAll(walkEgress);
                    }
                }
            } else {
                // Walk egress (short distance, always OK)
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
                log.debug("Feeder route has no DRT legs, returning null");
                return null;
            }

            successfulRoutes++;
            // Log first few successful routes at INFO level for visibility
            if (successfulRoutes <= 5) {
                log.info("SUCCESS: Feeder DRT route created for person {} (route #{}) - {} legs",
                        person.getId(), successfulRoutes, route.size());
            }

            return route;

        } catch (Exception e) {
            // Log at debug level to avoid flooding logs - these failures are expected
            // when PT routing can't find a valid route
            log.debug("Error in feeder DRT routing: {}", e.getMessage());
            // Don't fall back to PT here since the PT router likely failed too
            return null;
        }
    }

    /**
     * Route a DRT leg between two locations.
     */
    private List<? extends PlanElement> routeDrtLeg(Object from, Object to, double departureTime, Person person) {
        try {
            Facility fromFac = (from instanceof Facility) ? (Facility) from : createFacility((TransitStopFacility) from);
            Facility toFac = (to instanceof Facility) ? (Facility) to : createFacility((TransitStopFacility) to);

            return drtRoutingModule.calcRoute(DefaultRoutingRequest.withoutAttributes(fromFac, toFac, departureTime, person));
        } catch (Exception e) {
            log.debug("DRT routing failed: {}", e.getMessage());
            return null;
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
