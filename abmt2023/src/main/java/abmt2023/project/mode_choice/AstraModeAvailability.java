package abmt2023.project.mode_choice;

import java.util.Collection;
import java.util.List;

import org.eqasim.switzerland.mode_choice.SwissModeAvailability;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.mode_availability.ModeAvailability;


public class AstraModeAvailability implements ModeAvailability {
	public static final String NAME = "AstraModeAvailability";

	private final SwissModeAvailability delegate;

	public AstraModeAvailability(SwissModeAvailability delegate) {
		this.delegate = delegate;
	}

	@Override
	public Collection<String> getAvailableModes(Person person, List<DiscreteModeChoiceTrip> trips) {
		Collection<String> modes = delegate.getAvailableModes(person, trips);
		// TODO: modes.add(NAME)
		// Comment out the following line to remove the DRT mode
		if (modes.contains(TransportMode.walk)) {
			modes.add(TransportMode.drt);
		}
		// modes.add("drt");
		return modes;
	}
}
