package abmt2025.project.mode_choice.feeder;

import java.util.ArrayList;
import java.util.List;

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

import abmt2025.project.mode_choice.estimators.AstraPtUtilityEstimator_DRT;
import abmt2025.project.mode_choice.estimators.DRTUtilityEstimator;

/**
 * Utility estimator for feeder DRT trips.
 * Calculates utility by summing DRT utility (for access/egress) and PT utility.
 *
 * The trip structure can be:
 * - DRT -> PT -> Walk
 * - Walk -> PT -> DRT
 * - DRT -> PT -> DRT
 *
 * The utility is calculated by segmenting the trip at "pt interaction" activities
 * and delegating to the appropriate estimator (DRT or PT).
 */
public class FeederDrtUtilityEstimator implements UtilityEstimator {
    public static final String NAME = "FeederDrtUtilityEstimator";
    private static final Logger log = LogManager.getLogger(FeederDrtUtilityEstimator.class);

    private final DRTUtilityEstimator drtEstimator;
    private final AstraPtUtilityEstimator_DRT ptEstimator;

    @Inject
    public FeederDrtUtilityEstimator(DRTUtilityEstimator drtEstimator, AstraPtUtilityEstimator_DRT ptEstimator) {
        this.drtEstimator = drtEstimator;
        this.ptEstimator = ptEstimator;
    }

    @Override
    public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
        try {
            double totalUtility = 0.0;

            // Segment the trip into DRT and PT parts
            List<PlanElement> currentSegment = new ArrayList<>();
            String currentMode = null;

            boolean hasDrt = false;
            boolean hasPt = false;

            for (PlanElement element : elements) {
                if (element instanceof Activity) {
                    Activity activity = (Activity) element;
                    String actType = activity.getType();

                    // Check for interaction activities that mark segment boundaries
                    if (actType.contains("interaction")) {
                        // End the current segment and calculate its utility
                        if (!currentSegment.isEmpty() && currentMode != null) {
                            totalUtility += calculateSegmentUtility(person, trip, currentSegment, currentMode);
                        }
                        currentSegment = new ArrayList<>();
                        currentMode = null;
                    }
                    currentSegment.add(element);
                } else if (element instanceof Leg) {
                    Leg leg = (Leg) element;
                    String legMode = leg.getMode();

                    // Track which modes are used
                    if (legMode.equals(TransportMode.drt)) {
                        hasDrt = true;
                        currentMode = TransportMode.drt;
                    } else if (legMode.equals(TransportMode.pt)) {
                        hasPt = true;
                        currentMode = TransportMode.pt;
                    } else if (legMode.equals(TransportMode.walk)) {
                        // Walk legs are handled within their parent mode segment
                        if (currentMode == null) {
                            currentMode = TransportMode.walk;
                        }
                    }

                    currentSegment.add(element);
                }
            }

            // Process the last segment
            if (!currentSegment.isEmpty() && currentMode != null) {
                totalUtility += calculateSegmentUtility(person, trip, currentSegment, currentMode);
            }

            // Log warning if trip doesn't have expected structure
            if (!hasDrt) {
                log.debug("Feeder DRT trip for person {} has no DRT leg - treating as PT only", person.getId());
            }
            if (!hasPt) {
                log.debug("Feeder DRT trip for person {} has no PT leg - treating as DRT only", person.getId());
            }

            return totalUtility;

        } catch (Exception e) {
            log.warn("Exception in feeder DRT utility calculation for person {}: {}", person.getId(), e.getMessage());
            return Double.NEGATIVE_INFINITY;
        }
    }

    /**
     * Calculate utility for a segment of the trip based on its mode.
     */
    private double calculateSegmentUtility(Person person, DiscreteModeChoiceTrip trip,
            List<PlanElement> segment, String mode) {

        if (segment.isEmpty()) {
            return 0.0;
        }

        try {
            if (mode.equals(TransportMode.drt)) {
                return drtEstimator.estimateUtility(person, trip, segment);
            } else if (mode.equals(TransportMode.pt)) {
                return ptEstimator.estimateUtility(person, trip, segment);
            } else if (mode.equals(TransportMode.walk)) {
                // Walk access/egress - minimal utility contribution
                // The walk time is already accounted for in PT access/egress time
                return 0.0;
            }
        } catch (Exception e) {
            log.debug("Could not calculate {} segment utility: {}", mode, e.getMessage());
        }

        return 0.0;
    }
}
