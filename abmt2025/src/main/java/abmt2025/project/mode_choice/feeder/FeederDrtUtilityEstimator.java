package abmt2025.project.mode_choice.feeder;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

import abmt2025.project.mode_choice.AstraModeParameters_DRT;

/**
 * Utility estimator for feeder DRT trips.
 *
 * Calculates utility directly from leg travel times rather than delegating
 * to other estimators, since those expect complete trip data.
 *
 * The utility formula combines:
 * - ASC (alternative specific constant) for feeder_drt
 * - DRT in-vehicle time and waiting time
 * - PT in-vehicle time
 * - Walk access/egress time
 * - Cost (simplified)
 */
public class FeederDrtUtilityEstimator implements UtilityEstimator {
    public static final String NAME = "FeederDrtUtilityEstimator";
    private static final Logger log = LogManager.getLogger(FeederDrtUtilityEstimator.class);

    private final AstraModeParameters_DRT parameters;

    // Diagnostic counters
    private static final AtomicInteger callCount = new AtomicInteger(0);
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failCount = new AtomicInteger(0);

    @Inject
    public FeederDrtUtilityEstimator(AstraModeParameters_DRT parameters) {
        this.parameters = parameters;
        log.info("FeederDrtUtilityEstimator initialized with parameters: ASC={}, betaInVehicleTime={}, betaWaitingTime={}",
                parameters.astraDRT.betaASC, parameters.astraDRT.betaInVehicleTime, parameters.astraDRT.betaWaitingTime);
    }

    @Override
    public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
        int currentCall = callCount.incrementAndGet();

        // Log first few calls for debugging
        if (currentCall <= 5) {
            log.info("FeederDRT estimateUtility called #{} for person {}, {} elements: {}",
                    currentCall, person.getId(), elements.size(), describePlanElements(elements));
        }

        try {
            // Extract times from legs
            double drtInVehicleTime_min = 0.0;
            double drtWaitingTime_min = 0.0;
            double ptInVehicleTime_min = 0.0;
            double walkTime_min = 0.0;

            boolean hasDrt = false;
            boolean hasPt = false;

            for (PlanElement element : elements) {
                if (element instanceof Leg) {
                    Leg leg = (Leg) element;
                    String mode = leg.getMode();

                    // Handle undefined travel times - use route distance/speed or default
                    double travelTime_min;
                    if (leg.getTravelTime().isDefined()) {
                        travelTime_min = leg.getTravelTime().seconds() / 60.0;
                    } else if (leg.getRoute() != null && leg.getRoute().getDistance() > 0) {
                        // Estimate from distance: assume 30 km/h for DRT, 40 km/h for PT, 5 km/h for walk
                        double distance_km = leg.getRoute().getDistance() / 1000.0;
                        if (mode.equals(TransportMode.drt)) {
                            travelTime_min = distance_km / 30.0 * 60.0;
                        } else if (mode.equals(TransportMode.pt)) {
                            travelTime_min = distance_km / 40.0 * 60.0;
                        } else {
                            travelTime_min = distance_km / 5.0 * 60.0; // walk
                        }
                    } else {
                        // Default fallback: 5 minutes
                        travelTime_min = 5.0;
                    }

                    if (mode.equals(TransportMode.drt)) {
                        hasDrt = true;
                        drtInVehicleTime_min += travelTime_min;
                        // Estimate waiting time as 20% of travel time (simplified)
                        // In reality this comes from the DRT simulation
                        drtWaitingTime_min += Math.max(5.0, travelTime_min * 0.2); // min 5 min wait
                    } else if (mode.equals(TransportMode.pt)) {
                        hasPt = true;
                        ptInVehicleTime_min += travelTime_min;
                    } else if (mode.equals(TransportMode.walk) || mode.equals("walk")) {
                        walkTime_min += travelTime_min;
                    } else if (mode.equals("access_walk") || mode.equals("egress_walk") ||
                               mode.equals("transit_walk") || mode.contains("walk")) {
                        walkTime_min += travelTime_min;
                    }
                }
            }

            // Validate trip structure
            if (!hasDrt && !hasPt) {
                if (currentCall <= 10 || currentCall % 1000 == 0) {
                    log.warn("Feeder DRT trip for person {} has no DRT or PT legs - elements: {}",
                            person.getId(), describePlanElements(elements));
                }
                failCount.incrementAndGet();
                return Double.NEGATIVE_INFINITY;
            }

            // Calculate utility components
            double utility = 0.0;

            // ASC for feeder_drt (use same as DRT but slightly better to encourage intermodal)
            double asc = parameters.astraDRT.betaASC + 0.5; // Small bonus for intermodal
            utility += asc;

            // DRT components
            utility += parameters.astraDRT.betaInVehicleTime * drtInVehicleTime_min;
            utility += parameters.astraDRT.betaWaitingTime * drtWaitingTime_min;

            // PT in-vehicle time (use rail travel time parameter as approximation)
            utility += parameters.astraPt.betaRailTravelTime_u_min * ptInVehicleTime_min;

            // Walk/access time
            utility += parameters.astraDRT.betaAccessEgressTime * walkTime_min;

            // Simplified cost calculation (DRT base fare + PT fare estimate)
            double estimatedCost = 5.0 + (drtInVehicleTime_min * 0.5) + (ptInVehicleTime_min * 0.1);
            utility += parameters.betaCost_u_MU * estimatedCost;

            successCount.incrementAndGet();

            // Diagnostic logging - log first 20 and then every 500
            if (currentCall <= 20 || currentCall % 500 == 0) {
                log.info("FeederDRT utility for person {}: utility={} (ASC={}, DRT_time={}min, " +
                        "DRT_wait={}min, PT_time={}min, walk={}min, cost={}) [call #{}, success rate: {}/{}]",
                        person.getId(), String.format("%.3f", utility), String.format("%.3f", asc),
                        String.format("%.1f", drtInVehicleTime_min), String.format("%.1f", drtWaitingTime_min),
                        String.format("%.1f", ptInVehicleTime_min), String.format("%.1f", walkTime_min),
                        String.format("%.2f", estimatedCost),
                        currentCall, successCount.get(), currentCall);
            }

            return utility;

        } catch (Exception e) {
            failCount.incrementAndGet();
            // Log first 50 exceptions and then every 100 to catch issues
            if (failCount.get() <= 50 || failCount.get() % 100 == 0) {
                log.warn("Exception in feeder DRT utility calculation for person {} (call #{}, fail #{}): {} - {}",
                        person.getId(), currentCall, failCount.get(), e.getClass().getSimpleName(), e.getMessage());
                if (failCount.get() <= 5) {
                    e.printStackTrace();
                }
            }
            return Double.NEGATIVE_INFINITY;
        }
    }

    private String describePlanElements(List<? extends PlanElement> elements) {
        StringBuilder sb = new StringBuilder();
        for (PlanElement pe : elements) {
            if (pe instanceof Leg) {
                Leg leg = (Leg) pe;
                String timeStr = leg.getTravelTime().isDefined()
                    ? String.format("%.1f", leg.getTravelTime().seconds()/60.0)
                    : "undef";
                sb.append(leg.getMode()).append("(").append(timeStr).append("min) ");
            }
        }
        return sb.toString();
    }
}
