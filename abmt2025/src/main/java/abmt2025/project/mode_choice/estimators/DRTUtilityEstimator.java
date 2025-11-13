package abmt2025.project.mode_choice.estimators;

import java.util.List;

import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.EstimatorUtils;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.CarPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.variables.CarVariables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

import abmt2025.project.mode_choice.AstraModeParameters_DRT;
import abmt2025.project.mode_choice.costs.DRTCostModel;
import abmt2025.project.mode_choice.predictors.AstraPersonPredictor;
import abmt2025.project.mode_choice.predictors.AstraTripPredictor;
import abmt2025.project.mode_choice.predictors.DRTPredictor;
import abmt2025.project.mode_choice.variables.AstraBikeVariables;
import abmt2025.project.mode_choice.variables.AstraPersonVariables;
import abmt2025.project.mode_choice.variables.AstraTripVariables;
import abmt2025.project.mode_choice.variables.DRTVariables;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DRTUtilityEstimator implements UtilityEstimator {
	
	static public final String NAME = "DRTUtilityEstimator";
	private static final Logger log = LogManager.getLogger(DRTUtilityEstimator.class);

	private final AstraModeParameters_DRT parameters; //TODO: do not forget to add appropriate parameters to this class
	private final AstraPersonPredictor personPredictor;
	private final AstraTripPredictor tripPredictor;
	private final DRTPredictor drtpredictor;

	
	@Inject
	public DRTUtilityEstimator(AstraModeParameters_DRT parameters, DRTPredictor drtpredictor,
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
	
	protected double estimateWaitingTimeUtility(DRTVariables variables) {
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
    // Add the missing try block here
    try {
        // TODO calculate the utility of this trip
        
        //AstraTripVariables tripVariables = this.tripPredictor.predictVariables(person, trip, elements);
    
        DRTVariables variables = drtpredictor.predictVariables(person, trip, elements);

        // Add safety check here
        if (variables == null) {
            log.warn("DRT variables prediction failed for person {} at iteration", person.getId());
            return Double.NEGATIVE_INFINITY;
        }
        
        // Check for invalid values
        if (Double.isNaN(variables.invehicletime_min) || Double.isInfinite(variables.invehicletime_min) ||
            Double.isNaN(variables.waitingtime_min) || Double.isInfinite(variables.waitingtime_min) ||
            Double.isNaN(variables.cost) || Double.isInfinite(variables.cost)) {
            log.warn("DRT variables contain invalid values for person {}: invehicletime={}, waitingtime={}, cost={}", 
                     person.getId(), variables.invehicletime_min, variables.waitingtime_min, variables.cost);
            return Double.NEGATIVE_INFINITY;
        }
        AstraPersonVariables personVariables = personPredictor.predictVariables(person, trip, elements);
        AstraTripVariables tripVariables = tripPredictor.predictVariables(person, trip, elements);
        
        double utility = 0.0;

        utility += estimateConstantUtility();
        utility += estimateTravelTimeUtility(variables);
        // utility += estimateAccessEgressTimeUtility(variables);
        utility += estimateWaitingTimeUtility(variables);
        utility += estimateWorkUtility(tripVariables);
        // utility += estimateAgeUtility(personVariables);
        utility += estimateCostUtility(variables);

        return utility;
    } catch (Exception e) {
        // If anything goes wrong, make DRT unavailable rather than crashing
        log.warn("Exception in DRT utility calculation for person {}: {}", person.getId(), e.getMessage());
        return Double.NEGATIVE_INFINITY;
    }
}