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
import java.util.concurrent.atomic.AtomicLong;

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

    // Statistics (thread-safe)
    private static final AtomicLong routingAttempts = new AtomicLong(0);
    private static final AtomicLong routingAllowed = new AtomicLong(0);
    private static final AtomicLong routingFiltered = new AtomicLong(0);
    private static final AtomicLong routingFromInsideOnly = new AtomicLong(0);
    private static final AtomicLong routingToInsideOnly = new AtomicLong(0);
    private static final AtomicLong routingBothInside = new AtomicLong(0);

    public FilteredDrtIntermodalRoutingModule(RoutingModule delegate, DrtServiceAreaFilter serviceAreaFilter) {
        this.delegate = delegate;
        this.serviceAreaFilter = serviceAreaFilter;
        log.info("FilteredDrtIntermodalRoutingModule initialized with service area filter");
        log.info("  Filter active: {}", serviceAreaFilter != null && serviceAreaFilter.isInitialized());
    }

    @Override
    public List<? extends PlanElement> calcRoute(RoutingRequest request) {
        long attempts = routingAttempts.incrementAndGet();

        // Periodic logging every 5000 attempts
        if (attempts % 5000 == 0) {
            log.info("FilteredDrtIntermodalRoutingModule: {} attempts, {} allowed, {} filtered ({}% saved)",
                    attempts, routingAllowed.get(), routingFiltered.get(),
                    attempts > 0 ? String.format("%.1f", 100.0 * routingFiltered.get() / attempts) : "0.0");
        }

        // If no filter, just delegate
        if (serviceAreaFilter == null || !serviceAreaFilter.isInitialized()) {
            routingAllowed.incrementAndGet();
            return delegate.calcRoute(request);
        }

        Facility fromFacility = request.getFromFacility();
        Facility toFacility = request.getToFacility();

        Coord fromCoord = fromFacility.getCoord();
        Coord toCoord = toFacility.getCoord();

        // Check if either the origin or destination is in the DRT service area
        boolean fromInside = serviceAreaFilter.isInsideServiceArea(fromCoord);
        boolean toInside = serviceAreaFilter.isInsideServiceArea(toCoord);

        if (!fromInside && !toInside) {
            // Neither endpoint is in the service area - skip DRT routing
            routingFiltered.incrementAndGet();

            if (log.isDebugEnabled()) {
                log.debug("DRT routing FILTERED: neither endpoint in service area. " +
                        "From: ({}, {}), To: ({}, {})",
                        fromCoord.getX(), fromCoord.getY(),
                        toCoord.getX(), toCoord.getY());
            }

            return null; // Return null to indicate no route found
        }

        // At least one endpoint is in the service area - proceed with DRT routing
        routingAllowed.incrementAndGet();

        // Track which endpoint(s) are inside
        if (fromInside && toInside) {
            routingBothInside.incrementAndGet();
        } else if (fromInside) {
            routingFromInsideOnly.incrementAndGet();
        } else {
            routingToInsideOnly.incrementAndGet();
        }

        if (log.isDebugEnabled()) {
            log.debug("DRT routing ALLOWED: fromInside={}, toInside={}. " +
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
    public static void logStatistics() {
        long attempts = routingAttempts.get();
        long allowed = routingAllowed.get();
        long filtered = routingFiltered.get();

        log.info("=== FilteredDrtIntermodalRoutingModule Statistics ===");
        log.info("  Total DRT routing attempts: {}", attempts);
        log.info("  Routing ALLOWED (at least one endpoint in area): {}", allowed);
        log.info("    - Both endpoints inside: {}", routingBothInside.get());
        log.info("    - Only FROM inside (access leg): {}", routingFromInsideOnly.get());
        log.info("    - Only TO inside (egress leg): {}", routingToInsideOnly.get());
        log.info("  Routing FILTERED (neither endpoint in area): {}", filtered);
        log.info("  Computation saved: {}%",
                attempts > 0 ? String.format("%.1f", 100.0 * filtered / attempts) : "0.0");
    }

    public static void resetStatistics() {
        routingAttempts.set(0);
        routingAllowed.set(0);
        routingFiltered.set(0);
        routingFromInsideOnly.set(0);
        routingToInsideOnly.set(0);
        routingBothInside.set(0);
    }
}
