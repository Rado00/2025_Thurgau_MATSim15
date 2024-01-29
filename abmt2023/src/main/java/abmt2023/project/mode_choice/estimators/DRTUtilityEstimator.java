package abmt2023.project.mode_choice.estimators;

import java.util.List;

import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.EstimatorUtils;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.CarPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.variables.CarVariables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

import abmt2023.project.mode_choice.AstraModeParameters;
import abmt2023.project.mode_choice.costs.DRTCostModel;
import abmt2023.project.mode_choice.predictors.AstraPersonPredictor;
import abmt2023.project.mode_choice.predictors.AstraTripPredictor;
import abmt2023.project.mode_choice.predictors.DRTPredictor;
import abmt2023.project.mode_choice.variables.AstraBikeVariables;
import abmt2023.project.mode_choice.variables.AstraPersonVariables;
import abmt2023.project.mode_choice.variables.AstraTripVariables;
import abmt2023.project.mode_choice.variables.DRTVariables;

public class DRTUtilityEstimator implements UtilityEstimator {
	
	static public final String NAME = "DRTUtilityEstimator";

	private final AstraModeParameters parameters; //TODO: do not forget to add appropriate parameters to this class
	private final AstraPersonPredictor personPredictor;
	private final AstraTripPredictor tripPredictor;
	private final DRTPredictor drtpredictor;

	
	@Inject
	public DRTUtilityEstimator(AstraModeParameters parameters, DRTPredictor drtpredictor,
			AstraPersonPredictor personPredictor, AstraTripPredictor tripPredictor, DRTCostModel costPredictor) {
		

		this.parameters = parameters;
		this.personPredictor = personPredictor;
		this.tripPredictor = tripPredictor;
		this.drtpredictor = drtpredictor;
	}
	
	protected double estimateConstantUtility() {
		return parameters.astraDRT.betaASC;
	}
	
	protected double estimateTravelTimeUtility(DRTVariables variables) {
		return parameters.astraDRT.betaInVehicleTime * variables.invehicletime_min;
	}
	
	protected double estimateAccessEgressTimeUtility(DRTVariables variables) {
		return parameters.astraDRT.betaAccessEgressTime * variables.accestime_min;
	}
	
	protected double estimateWaitinfTimeUtility(DRTVariables variables) {
		return parameters.astraDRT.betaWaitingTime * variables.waitingtime_min;
	}
	
	protected double estimateWorkUtility(AstraTripVariables variables) {
	    return variables.isWork ? parameters.astraDRT.betaWork : 0.0;
	}
	
	protected double estimateAgeUtility(AstraPersonVariables variables) {
		return variables.age_a >= 60 ? parameters.astraDRT.betaAgeOver60 : 0.0;
	}
	
	protected double estimateCostUtility(DRTVariables variables) {
		return parameters.betaCost_u_MU * variables.cost;
	}
	
	// Cost?
	
	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		// TODO calculate the utility of this trip
		
		//AstraTripVariables tripVariables = this.tripPredictor.predictVariables(person, trip, elements);
	
		DRTVariables variables = drtpredictor.predictVariables(person, trip, elements);
		AstraPersonVariables personVariables = personPredictor.predictVariables(person, trip, elements);
		AstraTripVariables tripVariables = tripPredictor.predictVariables(person, trip, elements);
		
		double utility = 0.0;

		utility += estimateConstantUtility();
		utility += estimateTravelTimeUtility(variables);
		// utility += estimateAccessEgressTimeUtility(variables);
		utility += estimateWaitinfTimeUtility(variables);
		utility += estimateWorkUtility(tripVariables);
		// utility += estimateAgeUtility(personVariables);
		utility += estimateCostUtility(variables);

		return utility;
	}

}
