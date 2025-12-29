package abmt2025.project.mode_choice.feeder;

import java.util.LinkedList;
import java.util.List;

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
 * This is a direct copy of Tarek's FeederUtilityEstimator logic:
 * https://github.com/tkchouaki/eqasim-java/blob/develop/ile_de_france/src/main/java/org/eqasim/ile_de_france/feeder/FeederUtilityEstimator.java
 *
 * Splits the route at "feeder interaction" activities and delegates
 * each segment to the appropriate estimator (DRT or PT).
 */
public class FeederDrtUtilityEstimator implements UtilityEstimator {
    public static final String NAME = "FeederDrtUtilityEstimator";

    private final DRTUtilityEstimator drtEstimator;
    private final AstraPtUtilityEstimator_DRT ptEstimator;

    // TODO: Remove this after testing - temporary bonus to make feeder_drt competitive
    private static final double FEEDER_ASC_BONUS = 3.0;  // Bonus to make feeder_drt competitive with car

    @Inject
    public FeederDrtUtilityEstimator(DRTUtilityEstimator drtEstimator, AstraPtUtilityEstimator_DRT ptEstimator) {
        this.drtEstimator = drtEstimator;
        this.ptEstimator = ptEstimator;
    }

    @Override
    public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
        // Direct copy of Tarek's logic
        String lastMode = "";
        List<PlanElement> currentTrip = new LinkedList<>();
        double totalUtility = 0;

        for (PlanElement element : elements) {
            if (element instanceof Activity && ((Activity) element).getType().equals("feeder interaction")) {
                // Process the accumulated segment
                if (lastMode.equals(TransportMode.pt)) {
                    totalUtility += ptEstimator.estimateUtility(person, trip, currentTrip);
                } else if (lastMode.equals(TransportMode.drt)) {
                    totalUtility += drtEstimator.estimateUtility(person, trip, currentTrip);
                }
                currentTrip.clear();
            } else {
                // Add element to current segment
                currentTrip.add(element);
                if (element instanceof Leg) {
                    Leg leg = (Leg) element;
                    if (!leg.getMode().equals(TransportMode.walk)) {
                        lastMode = leg.getMode();
                    }
                }
            }
        }

        // Process the last segment
        if (currentTrip.size() > 0) {
            if (lastMode.equals(TransportMode.pt)) {
                totalUtility += ptEstimator.estimateUtility(person, trip, currentTrip);
            } else if (lastMode.equals(TransportMode.drt)) {
                totalUtility += drtEstimator.estimateUtility(person, trip, currentTrip);
            }
            currentTrip.clear();
        }

        // TODO: Remove FEEDER_ASC_BONUS after calibration
        return totalUtility + FEEDER_ASC_BONUS;
    }
}
