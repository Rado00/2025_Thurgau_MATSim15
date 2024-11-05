package abmt2023.project.utils.headway;

import java.util.List;

// *** Added necessary imports ***
import org.matsim.api.core.v01.population.Person;  // <--- Added this line ToChange MATSim 15
import org.matsim.facilities.Facility;

import ch.sbb.matsim.routing.pt.raptor.RaptorRoute;
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute.RoutePart;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;

public class HeadwayCalculator {
	private final SwissRailRaptor raptor;

	private final double beforeDepartureOffset;
	private final double afterDepartureOffset;

	public HeadwayCalculator(SwissRailRaptor raptor, double beforeDepartureOffset, double afterDepartureOffset) {
		this.raptor = raptor;
		this.beforeDepartureOffset = beforeDepartureOffset;
		this.afterDepartureOffset = afterDepartureOffset;
	}

    // *** Modified this method signature to accept a Person object ***
    public double calculateHeadway_min(Facility originFacility, Facility destinationFacility, double departureTime, Person person) {  // <--- Changed signature ToChange MATSim 15
        double earliestDepartureTime = departureTime - beforeDepartureOffset;
        double latestDepartureTime = departureTime + afterDepartureOffset;

		// *** Modified the calcRoutes call to include person and attributes ***
		List<RaptorRoute> routes = raptor.calcRoutes(originFacility, destinationFacility, earliestDepartureTime,
				departureTime, latestDepartureTime, person, null);  // <--- Changed call to include person, attributes ToChange MATSim 15

		int numberOfPtRoutes = 0;

		for (RaptorRoute route : routes) {
			for (RoutePart part : route.getParts()) {
				if (part.line != null) {
					numberOfPtRoutes++;
					break;
				}
			}
		}
		
		if (numberOfPtRoutes == 0) {
			return Double.POSITIVE_INFINITY;
		} else {
			return  ((beforeDepartureOffset + afterDepartureOffset) / numberOfPtRoutes) / 60.0;
		}		
	}
}