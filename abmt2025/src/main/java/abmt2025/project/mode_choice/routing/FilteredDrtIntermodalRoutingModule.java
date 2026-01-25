package abmt2025.project.mode_choice.routing;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.RoutingRequest;
import org.matsim.facilities.Facility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * A routing module wrapper that filters DRT routing based on a service area.
 *
 * For intermodal PT+DRT trips:
 * - DRT access leg (origin → PT stop): allowed if ORIGIN is in service area
 * - DRT egress leg (PT stop → destination): allowed if DESTINATION is in service area
 *
 * This prevents wasted computation by not attempting DRT routing for trips
 * where neither endpoint is in the DRT service area.
 */
public class FilteredDrtIntermodalRoutingModule implements RoutingModule {

    private static final Logger log = LogManager.getLogger(FilteredDrtIntermodalRoutingModule.class);

    private final RoutingModule delegate;
    private final DrtServiceAreaFilter serviceAreaFilter;

    // Statistics
    private long routingAttempts = 0;
    private long routingAllowed = 0;
    private long routingFiltered = 0;

    public FilteredDrtIntermodalRoutingModule(RoutingModule delegate, DrtServiceAreaFilter serviceAreaFilter) {
        this.delegate = delegate;
        this.serviceAreaFilter = serviceAreaFilter;
        log.info("FilteredDrtIntermodalRoutingModule initialized with service area filter");
    }

    @Override
    public List<? extends PlanElement> calcRoute(RoutingRequest request) {
        routingAttempts++;

        Facility fromFacility = request.getFromFacility();
        Facility toFacility = request.getToFacility();

        Coord fromCoord = fromFacility.getCoord();
        Coord toCoord = toFacility.getCoord();

        // Check if either the origin or destination is in the DRT service area
        boolean fromInside = serviceAreaFilter.isInsideServiceArea(fromCoord);
        boolean toInside = serviceAreaFilter.isInsideServiceArea(toCoord);

        if (!fromInside && !toInside) {
            // Neither endpoint is in the service area - skip DRT routing
            routingFiltered++;

            if (log.isDebugEnabled()) {
                log.debug("DRT routing filtered: neither endpoint in service area. " +
                        "From: ({}, {}), To: ({}, {})",
                        fromCoord.getX(), fromCoord.getY(),
                        toCoord.getX(), toCoord.getY());
            }

            return null; // Return null to indicate no route found
        }

        // At least one endpoint is in the service area - proceed with DRT routing
        routingAllowed++;

        if (log.isDebugEnabled()) {
            log.debug("DRT routing allowed: fromInside={}, toInside={}. " +
                    "From: ({}, {}), To: ({}, {})",
                    fromInside, toInside,
                    fromCoord.getX(), fromCoord.getY(),
                    toCoord.getX(), toCoord.getY());
        }

        return delegate.calcRoute(request);
    }

    /**
     * Log statistics about routing filter effectiveness.
     */
    public void logStatistics() {
        log.info("DRT Intermodal Routing Filter Statistics:");
        log.info("  Total routing attempts: {}", routingAttempts);
        log.info("  Routing allowed (in service area): {} ({:.1f}%)", routingAllowed,
                routingAttempts > 0 ? (100.0 * routingAllowed / routingAttempts) : 0.0);
        log.info("  Routing filtered (outside service area): {} ({:.1f}%)", routingFiltered,
                routingAttempts > 0 ? (100.0 * routingFiltered / routingAttempts) : 0.0);
        log.info("  Computation saved: {:.1f}%",
                routingAttempts > 0 ? (100.0 * routingFiltered / routingAttempts) : 0.0);
    }

    public long getRoutingAttempts() {
        return routingAttempts;
    }

    public long getRoutingAllowed() {
        return routingAllowed;
    }

    public long getRoutingFiltered() {
        return routingFiltered;
    }
}
