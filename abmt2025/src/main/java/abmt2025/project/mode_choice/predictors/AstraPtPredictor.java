package abmt2025.project.mode_choice.predictors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.eqasim.core.simulation.mode_choice.utilities.predictors.CachedVariablePredictor;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.PtPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.variables.PtVariables;
import org.eqasim.switzerland.ovgk.OVGK;
import org.eqasim.switzerland.ovgk.OVGKCalculator;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.core.population.PopulationUtils;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import com.google.inject.Inject;

import abmt2025.project.mode_choice.routing.DrtServiceAreaFilter;
import abmt2025.project.mode_choice.variables.AstraPtVariables;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AstraPtPredictor extends CachedVariablePredictor<AstraPtVariables> {
	private static final Logger log = LogManager.getLogger(AstraPtPredictor.class);

	public final PtPredictor delegate;
	private final TransitSchedule schedule;
	private final OVGKCalculator ovgkCalculator;
	private DrtServiceAreaFilter serviceAreaFilter; // Not final - set via optional injection

	// Statistics for logging (thread-safe)
	private static final AtomicLong ptTripsProcessed = new AtomicLong(0);
	private static final AtomicLong ptTripsWithDrt = new AtomicLong(0);
	private static final AtomicLong ptTripsWithDrtInServiceArea = new AtomicLong(0);
	private static final AtomicLong ptTripsWithDrtOutsideServiceArea = new AtomicLong(0);

	@Inject
	public AstraPtPredictor(PtPredictor delegate, TransitSchedule schedule, OVGKCalculator ovgkCalculator) {
		this.delegate = delegate;
		this.schedule = schedule;
		this.ovgkCalculator = ovgkCalculator;
		this.serviceAreaFilter = null;
		log.info("AstraPtPredictor initialized (filter will be set via setter if available)");
	}

	/**
	 * Optional injection of the DRT service area filter.
	 * This is called by Guice after construction if the filter is available.
	 */
	@com.google.inject.Inject(optional = true)
	public void setServiceAreaFilter(@com.google.inject.name.Named("drtServiceAreaFilter") DrtServiceAreaFilter filter) {
		this.serviceAreaFilter = filter;
		if (filter != null && filter.isInitialized()) {
			log.info("DRT service area filter injected into AstraPtPredictor");
		}
	}

	/**
	 * Log statistics about PT+DRT trips processed.
	 * Call this at the end of simulation to see summary.
	 */
	public static void logStatistics() {
		log.info("=== AstraPtPredictor DRT Statistics ===");
		log.info("  Total PT trips processed: {}", ptTripsProcessed.get());
		log.info("  PT trips with DRT access/egress: {} ({}%)", ptTripsWithDrt.get(),
				ptTripsProcessed.get() > 0 ? String.format("%.1f", 100.0 * ptTripsWithDrt.get() / ptTripsProcessed.get()) : "0.0");
		log.info("  PT+DRT trips with origin/dest in service area: {}", ptTripsWithDrtInServiceArea.get());
		log.info("  PT+DRT trips with origin/dest outside service area: {}", ptTripsWithDrtOutsideServiceArea.get());
	}

	@Override
	protected AstraPtVariables predict(Person person, DiscreteModeChoiceTrip trip,
			List<? extends PlanElement> elements) {

		ptTripsProcessed.incrementAndGet();

		// Get trip origin and destination for service area checking
		Coord originCoord = trip.getOriginActivity().getCoord();
		Coord destCoord = trip.getDestinationActivity().getCoord();

		// Check for DRT legs and handle them separately
		double drtTravelTime_min = 0.0;
		double drtWaitingTime_min = 0.0;
		boolean hasDrtAccess = false;
		int drtLegCount = 0;

		// Create filtered elements list, replacing DRT legs with walk legs for the delegate
		List<PlanElement> filteredElements = new ArrayList<>();

		for (PlanElement element : elements) {
			if (element instanceof Leg) {
				Leg leg = (Leg) element;

				if (leg.getMode().equals(TransportMode.drt)) {
					// This is a DRT access/egress leg
					hasDrtAccess = true;
					drtLegCount++;

					// Extract DRT-specific times
					if (leg.getRoute() instanceof DrtRoute) {
						DrtRoute drtRoute = (DrtRoute) leg.getRoute();
						// DrtRoute provides max wait time and max travel time estimates
						drtWaitingTime_min += drtRoute.getMaxWaitTime() / 60.0;
						drtTravelTime_min += drtRoute.getMaxTravelTime() / 60.0;
					} else {
						// Fallback: use leg travel time
						drtTravelTime_min += leg.getTravelTime().seconds() / 60.0;
					}

					// Replace DRT leg with a walk leg for the delegate predictor
					// This allows the base PtPredictor to process the trip
					Leg walkLeg = PopulationUtils.createLeg(TransportMode.walk);
					walkLeg.setTravelTime(leg.getTravelTime().seconds());
					walkLeg.setRoute(leg.getRoute()); // Keep route for distance calculation
					filteredElements.add(walkLeg);
				} else {
					filteredElements.add(element);
				}
			} else {
				filteredElements.add(element);
			}
		}

		// Log DRT access/egress statistics
		if (hasDrtAccess) {
			ptTripsWithDrt.incrementAndGet();

			// Check if origin or destination is in service area
			boolean originInServiceArea = false;
			boolean destInServiceArea = false;

			if (serviceAreaFilter != null && serviceAreaFilter.isInitialized()) {
				originInServiceArea = serviceAreaFilter.isInsideServiceArea(originCoord);
				destInServiceArea = serviceAreaFilter.isInsideServiceArea(destCoord);

				if (originInServiceArea || destInServiceArea) {
					ptTripsWithDrtInServiceArea.incrementAndGet();
				} else {
					ptTripsWithDrtOutsideServiceArea.incrementAndGet();
				}
			}

			// Debug logging for DRT intermodal trips
			if (log.isDebugEnabled()) {
				log.debug("PT+DRT trip for person {}: drtLegs={}, drtTime={}min, drtWait={}min, " +
						"originInArea={}, destInArea={}, origin=({},{}), dest=({},{})",
						person.getId(), drtLegCount,
						String.format("%.1f", drtTravelTime_min),
						String.format("%.1f", drtWaitingTime_min),
						originInServiceArea, destInServiceArea,
						String.format("%.0f", originCoord.getX()),
						String.format("%.0f", originCoord.getY()),
						String.format("%.0f", destCoord.getX()),
						String.format("%.0f", destCoord.getY()));
			}

			// Periodic info logging (every 1000 PT+DRT trips)
			long drtCount = ptTripsWithDrt.get();
			if (drtCount % 1000 == 0) {
				log.info("PT+DRT trips processed: {}, inServiceArea: {}, outsideServiceArea: {}",
						drtCount, ptTripsWithDrtInServiceArea.get(), ptTripsWithDrtOutsideServiceArea.get());
			}
		}

		// Call delegate with filtered elements (DRT legs replaced with walk legs)
		PtVariables delegateVariables = delegate.predictVariables(person, trip, filteredElements);

		double railTravelTime_min = 0.0;
		double busTravelTime_min = 0.0;

		for (PlanElement element : elements) {
			if (element instanceof Leg) {
				Leg leg = (Leg) element;

				if (leg.getMode().equals(TransportMode.pt)) {
					TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
					TransitRoute transitRoute = schedule.getTransitLines().get(route.getLineId()).getRoutes()
							.get(route.getRouteId());

					if (transitRoute.getTransportMode().equals("rail")) {
						railTravelTime_min += route.getTravelTime().seconds() / 60.0;
					} else {
						busTravelTime_min += route.getTravelTime().seconds() / 60.0;
					}
				}
			}
		}

		Double headwayRaw = (Double) trip.getOriginActivity().getAttributes().getAttribute("headway_min");
		double headway_min = headwayRaw == null ? 0.0 : headwayRaw;

		OVGK originOvgk = ovgkCalculator.calculateOVGK(trip.getOriginActivity().getCoord());
		OVGK destinationOvgk = ovgkCalculator.calculateOVGK(trip.getDestinationActivity().getCoord());
		OVGK worstOvgk = originOvgk.ordinal() > destinationOvgk.ordinal() ? originOvgk : destinationOvgk;

		return new AstraPtVariables(delegateVariables, railTravelTime_min, busTravelTime_min, headway_min, worstOvgk,
				drtTravelTime_min, drtWaitingTime_min, hasDrtAccess);
	}
}
