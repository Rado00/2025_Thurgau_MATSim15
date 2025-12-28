package abmt2025.project.mode_choice.feeder;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

import abmt2025.project.mode_choice.estimators.DRTUtilityEstimator;
import abmt2025.project.mode_choice.estimators.AstraPtUtilityEstimator_DRT;

/**
 * Utility estimator for feeder DRT trips.
 *
 * Following Tarek's approach: delegates utility calculation to the existing
 * DRT and PT utility estimators. The total utility is simply the sum of
 * utilities from each segment (DRT leg + PT leg).
 *
 * The trip is split at "feeder interaction" or "pt interaction" activities,
 * and each segment is evaluated by the appropriate estimator.
 */
public class FeederDrtUtilityEstimator implements UtilityEstimator {
    public static final String NAME = "FeederDrtUtilityEstimator";
    private static final Logger log = LogManager.getLogger(FeederDrtUtilityEstimator.class);

    // Use a very low but finite utility instead of NEGATIVE_INFINITY
    private static final double INVALID_ROUTE_UTILITY = -1000.0;

    private final DRTUtilityEstimator drtEstimator;
    private final AstraPtUtilityEstimator_DRT ptEstimator;

    // Diagnostic counters
    private static final AtomicInteger callCount = new AtomicInteger(0);
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failCount = new AtomicInteger(0);

    @Inject
    public FeederDrtUtilityEstimator(DRTUtilityEstimator drtEstimator, AstraPtUtilityEstimator_DRT ptEstimator) {
        this.drtEstimator = drtEstimator;
        this.ptEstimator = ptEstimator;
        log.info("FeederDrtUtilityEstimator initialized - delegating to DRTUtilityEstimator and AstraPtUtilityEstimator_DRT");
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
            String lastMode = "";
            List<PlanElement> currentSegment = new LinkedList<>();
            double totalUtility = 0.0;

            boolean hasDrt = false;
            boolean hasPt = false;

            for (PlanElement element : elements) {
                // Check if this is an interaction activity (segment boundary)
                if (element instanceof Activity) {
                    Activity activity = (Activity) element;
                    String actType = activity.getType();

                    if (actType.equals("feeder interaction") || actType.equals("pt interaction") ||
                        actType.contains("interaction")) {

                        // Process the current segment
                        if (!currentSegment.isEmpty() && !lastMode.isEmpty()) {
                            double segmentUtility = estimateSegmentUtility(person, trip, currentSegment, lastMode);

                            if (currentCall <= 10) {
                                log.debug("Segment utility for mode {}: {}", lastMode, segmentUtility);
                            }

                            // Check for invalid utility
                            if (Double.isInfinite(segmentUtility) || Double.isNaN(segmentUtility)) {
                                if (currentCall <= 20 || currentCall % 500 == 0) {
                                    log.warn("Invalid segment utility {} for mode {} - person {}",
                                            segmentUtility, lastMode, person.getId());
                                }
                                failCount.incrementAndGet();
                                return INVALID_ROUTE_UTILITY;
                            }

                            totalUtility += segmentUtility;
                        }
                        currentSegment.clear();
                        lastMode = "";
                        continue; // Don't add interaction activities to segments
                    }
                }

                // Add element to current segment
                currentSegment.add(element);

                // Track the main mode of this segment (non-walk mode)
                if (element instanceof Leg) {
                    Leg leg = (Leg) element;
                    String mode = leg.getMode();

                    if (mode.equals(TransportMode.drt)) {
                        lastMode = TransportMode.drt;
                        hasDrt = true;
                    } else if (mode.equals(TransportMode.pt) || mode.equals("pt")) {
                        lastMode = TransportMode.pt;
                        hasPt = true;
                    } else if (!mode.equals(TransportMode.walk) && !mode.contains("walk")) {
                        // Other non-walk modes (transit_walk is handled by PT estimator)
                        if (mode.equals("transit_walk")) {
                            hasPt = true;
                        }
                    }
                }
            }

            // Process the last segment
            if (!currentSegment.isEmpty() && !lastMode.isEmpty()) {
                double segmentUtility = estimateSegmentUtility(person, trip, currentSegment, lastMode);

                if (Double.isInfinite(segmentUtility) || Double.isNaN(segmentUtility)) {
                    failCount.incrementAndGet();
                    return INVALID_ROUTE_UTILITY;
                }

                totalUtility += segmentUtility;
            }

            // Validate trip structure - must have at least DRT or PT
            if (!hasDrt && !hasPt) {
                if (currentCall <= 10 || currentCall % 1000 == 0) {
                    log.warn("Feeder DRT trip for person {} has no DRT or PT legs - elements: {}",
                            person.getId(), describePlanElements(elements));
                }
                failCount.incrementAndGet();
                return INVALID_ROUTE_UTILITY;
            }

            successCount.incrementAndGet();

            // Diagnostic logging
            if (currentCall <= 20 || currentCall % 500 == 0) {
                log.info("FeederDRT utility for person {}: totalUtility={} (hasDrt={}, hasPt={}) [call #{}, success rate: {}/{}]",
                        person.getId(), String.format("%.3f", totalUtility), hasDrt, hasPt,
                        currentCall, successCount.get(), currentCall);
            }

            return totalUtility;

        } catch (Exception e) {
            failCount.incrementAndGet();
            if (failCount.get() <= 50 || failCount.get() % 100 == 0) {
                log.warn("Exception in feeder DRT utility calculation for person {} (call #{}, fail #{}): {} - {}",
                        person.getId(), currentCall, failCount.get(), e.getClass().getSimpleName(), e.getMessage());
                if (failCount.get() <= 5) {
                    e.printStackTrace();
                }
            }
            return INVALID_ROUTE_UTILITY;
        }
    }

    /**
     * Estimate utility for a segment using the appropriate estimator.
     */
    private double estimateSegmentUtility(Person person, DiscreteModeChoiceTrip trip,
            List<PlanElement> segment, String mode) {

        if (mode.equals(TransportMode.drt)) {
            return drtEstimator.estimateUtility(person, trip, segment);
        } else if (mode.equals(TransportMode.pt)) {
            return ptEstimator.estimateUtility(person, trip, segment);
        } else {
            // Unknown mode - return 0 (no contribution to utility)
            log.debug("Unknown mode {} in feeder segment, returning 0 utility", mode);
            return 0.0;
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
            } else if (pe instanceof Activity) {
                Activity act = (Activity) pe;
                sb.append("[").append(act.getType()).append("] ");
            }
        }
        return sb.toString();
    }
}
