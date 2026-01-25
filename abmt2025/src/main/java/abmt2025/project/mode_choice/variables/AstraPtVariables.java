package abmt2025.project.mode_choice.variables;

import org.eqasim.core.simulation.mode_choice.utilities.variables.PtVariables;
import org.eqasim.switzerland.ovgk.OVGK;

public class AstraPtVariables extends PtVariables {
	public final double railTravelTime_min;
	public final double busTravelTime_min;
	public final double headway_min;
	public final OVGK ovgk;

	// DRT access/egress variables for intermodal PT+DRT trips
	public final double drtAccessEgressTime_min;
	public final double drtWaitingTime_min;
	public final boolean hasDrtAccess;

	public AstraPtVariables(PtVariables delegate, double railTravelTime_min, double busTravelTime_min,
			double headway_min, OVGK ovgk) {
		this(delegate, railTravelTime_min, busTravelTime_min, headway_min, ovgk, 0.0, 0.0, false);
	}

	public AstraPtVariables(PtVariables delegate, double railTravelTime_min, double busTravelTime_min,
			double headway_min, OVGK ovgk, double drtAccessEgressTime_min, double drtWaitingTime_min, boolean hasDrtAccess) {
		super(delegate.inVehicleTime_min, delegate.waitingTime_min, delegate.accessEgressTime_min,
				delegate.numberOfLineSwitches, delegate.cost_MU, delegate.euclideanDistance_km);

		this.busTravelTime_min = busTravelTime_min;
		this.railTravelTime_min = railTravelTime_min;
		this.headway_min = headway_min;
		this.ovgk = ovgk;
		this.drtAccessEgressTime_min = drtAccessEgressTime_min;
		this.drtWaitingTime_min = drtWaitingTime_min;
		this.hasDrtAccess = hasDrtAccess;
	}
}
