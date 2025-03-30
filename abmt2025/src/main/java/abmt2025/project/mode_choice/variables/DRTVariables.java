package abmt2025.project.mode_choice.variables;

import org.eqasim.core.simulation.mode_choice.utilities.variables.BaseVariables;

import abmt2025.project.mode_choice.variables.AstraTripVariables;

public class DRTVariables implements BaseVariables {
	
	public final double cost;
	public final double invehicletime_min;
	public final double accestime_min;
	public final double waitingtime_min;
	
	public DRTVariables(double cost, double invehicletime_min, double accestime_min, double waitingtime_min) {
		this.cost=cost;
		this.invehicletime_min=invehicletime_min;
		this.accestime_min=accestime_min;
		this.waitingtime_min=waitingtime_min;
	}

	// TODO: you have to define all variables that are used in the utility function
}
