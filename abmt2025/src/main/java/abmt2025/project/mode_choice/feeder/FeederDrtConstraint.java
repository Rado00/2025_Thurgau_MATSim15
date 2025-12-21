package abmt2025.project.mode_choice.feeder;

import java.util.Collection;
import java.util.List;

import org.matsim.api.core.v01.population.Person;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripConstraint;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.TripConstraintFactory;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;

/**
 * Constraint for feeder DRT trips.
 *
 * Ensures that:
 * 1. Trips to/from "outside" activities don't use feeder DRT (they should use regular modes)
 *
 * Note: The validation that feeder trips contain both DRT and PT legs is handled
 * by the FeederDrtRoutingModule, which returns null for invalid routes.
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
        // The routing module already ensures valid routes by returning null for
        // routes that don't contain both DRT and PT legs.
        // If we reach here, the route is valid.
        return true;
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
