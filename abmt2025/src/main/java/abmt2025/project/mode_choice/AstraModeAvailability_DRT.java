package abmt2025.project.mode_choice;

import java.util.Collection;
import java.util.List;

import org.eqasim.switzerland.mode_choice.SwissModeAvailability;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.mode_availability.ModeAvailability;


public class AstraModeAvailability_DRT implements ModeAvailability {
	public static final String NAME = "AstraModeAvailability";

	private final SwissModeAvailability delegate;

	public AstraModeAvailability_DRT(SwissModeAvailability delegate) {
		this.delegate = delegate;
	}

	@Override
	public Collection<String> getAvailableModes(Person person, List<DiscreteModeChoiceTrip> trips) {
		Collection<String> modes = delegate.getAvailableModes(person, trips);
		// TODO: modes.add(NAME)
		// Comment out the following line to remove the DRT mode

		// This was mine
		// if (modes.contains(TransportMode.walk)) {
		// 	modes.add(TransportMode.drt);
		// }
		// modes.add(TransportMode.drt);
		// modes.add("drt");
		// return modes;

		// This is from GPT
		// Convert to a mutable list GPT

		List<String> mutableModes = new java.util.ArrayList<>(modes);

		if (modes.contains(TransportMode.walk)) {
			mutableModes.add(TransportMode.drt);
		}
		mutableModes.add("drt");

		return mutableModes;


	}
}
