package abmt2025.project.mode_choice;

import java.util.Collection;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eqasim.switzerland.mode_choice.SwissModeAvailability;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.mode_availability.ModeAvailability;


public class AstraModeAvailability_DRT implements ModeAvailability {
	private static final Logger log = LogManager.getLogger(AstraModeAvailability_DRT.class);
	public static final String NAME = "AstraModeAvailability";

	// Counter for diagnostic logging
	private int feederDrtOfferedCount = 0;

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

		// Convert to a mutable list
		List<String> mutableModes = new java.util.ArrayList<>(modes);

		if (modes.contains(TransportMode.walk)) {
			// Add standalone DRT mode
			mutableModes.add(TransportMode.drt);

			// Add feeder_drt mode (DRT + PT combination)
			// Only available if both walk and pt are available (as these are prerequisites)
			if (modes.contains(TransportMode.pt)) {
				mutableModes.add("feeder_drt");
				feederDrtOfferedCount++;
				// Log every 1000 times feeder_drt is offered
				if (feederDrtOfferedCount == 1 || feederDrtOfferedCount % 1000 == 0) {
					log.info("feeder_drt mode offered {} times (person: {})", feederDrtOfferedCount, person.getId());
				}
			}
		}

		return mutableModes;


	}
}
