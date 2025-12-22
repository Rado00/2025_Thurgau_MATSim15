package abmt2025.project.mode_choice.feeder;

import java.util.Collection;
import java.util.List;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripConstraint;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripConstraintFactory;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;

/**
 * Constraint for feeder DRT trips.
 *
 * Ensures that:
 * 1. Feeder DRT trips contain at least one DRT leg AND at least one PT leg
 * 2. Trips to/from "outside" activities don't use feeder DRT (they should use regular modes)
 */
public class FeederDrtConstraint implements TripConstraint {
    public static final String NAME = "FeederDrtConstraint";

    private static final String FEEDER_DRT_MODE = "feeder_drt";

    @Override
    public boolean validateBeforeEstimation(DiscreteModeChoiceTrip trip, String mode, List<String> previousModes) {
        // Only validate feeder_drt mode
        if (!mode.equals(FEEDER_DRT_MODE)) {
            return true;
        }

        // Don't allow feeder_drt for trips to/from "outside" activities
        String originType = trip.getOriginActivity().getType();
        String destinationType = trip.getDestinationActivity().getType();

        if (originType.equals("outside") || destinationType.equals("outside")) {
            return false;
        }

        return true;
    }

    @Override
    public boolean validateAfterEstimation(DiscreteModeChoiceTrip trip, TripCandidate candidate,
            List<TripCandidate> previousCandidates) {

        // Only validate feeder_drt mode
        if (!candidate.getMode().equals(FEEDER_DRT_MODE)) {
            return true;
        }

        // Check that the routed trip contains both DRT and PT legs
        List<? extends PlanElement> elements = candidate.getRoutedPlanElements();

        if (elements == null || elements.isEmpty()) {
            return false;
        }

        boolean hasDrt = false;
        boolean hasPt = false;

        for (PlanElement element : elements) {
            if (element instanceof Leg) {
                Leg leg = (Leg) element;
                String legMode = leg.getMode();

                if (legMode.equals(TransportMode.drt)) {
                    hasDrt = true;
                } else if (legMode.equals(TransportMode.pt)) {
                    hasPt = true;
                }
            }
        }

        // A valid feeder trip must have at least one DRT leg AND at least one PT leg
        return hasDrt && hasPt;
    }

    /**
     * Factory for creating FeederDrtConstraint instances.
     */
    public static class Factory implements TripConstraintFactory {
        @Override
        public TripConstraint createConstraint(Person person, List<DiscreteModeChoiceTrip> planTrips,
                Collection<String> availableModes) {
            return new FeederDrtConstraint();
        }
    }
}
