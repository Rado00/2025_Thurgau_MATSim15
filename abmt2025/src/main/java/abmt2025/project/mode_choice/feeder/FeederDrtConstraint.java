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
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.RoutedTripCandidate;

/**
 * Constraint for feeder DRT trips.
 *
 * Ensures that:
 * 1. Trips to/from "outside" activities don't use feeder DRT
 * 2. Routes must contain both DRT and PT legs (not walk-only)
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

        // Validate that the route contains both DRT and PT legs
        // This catches cases where the routing module returned null and a fallback was used
        if (!(candidate instanceof RoutedTripCandidate)) {
            return false;
        }
        List<? extends PlanElement> routeElements = ((RoutedTripCandidate) candidate).getRoutedPlanElements();
        if (routeElements == null || routeElements.isEmpty()) {
            return false;
        }

        boolean hasDrt = false;
        boolean hasPt = false;

        for (PlanElement element : routeElements) {
            if (element instanceof Leg) {
                Leg leg = (Leg) element;
                String mode = leg.getMode();
                if (mode.equals(TransportMode.drt)) {
                    hasDrt = true;
                } else if (mode.equals(TransportMode.pt) || mode.equals("transit_walk")) {
                    hasPt = true;
                }
            }
        }

        // Feeder DRT must have both DRT and PT legs
        // This ensures we don't choose walk-only fallback routes
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
