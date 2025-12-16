package abmt2025.project.mode_choice.estimators;

import java.util.List;

import org.eqasim.core.simulation.mode_choice.utilities.estimators.CarUtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.EstimatorUtils;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.CarPredictor;
import org.eqasim.core.simulation.mode_choice.utilities.variables.CarVariables;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

import abmt2025.project.mode_choice.AstraModeParameters_Baseline;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import abmt2025.project.mode_choice.predictors.AstraPersonPredictor;
import abmt2025.project.mode_choice.predictors.AstraTripPredictor;
import abmt2025.project.mode_choice.variables.AstraPersonVariables;
import abmt2025.project.mode_choice.variables.AstraTripVariables;

public class AstraCarUtilityEstimator_Baseline extends CarUtilityEstimator {
	static public final String NAME = "AstraCarEstimator";
	private static final Logger log = LogManager.getLogger(AstraCarUtilityEstimator_Baseline.class);
	private static boolean loggedCost = false;

	private final AstraModeParameters_Baseline parameters;
	private final AstraPersonPredictor personPredictor;
	private final AstraTripPredictor tripPredictor;
	private final CarPredictor predictor;

	@Inject
	public AstraCarUtilityEstimator_Baseline(AstraModeParameters_Baseline parameters, CarPredictor predictor,
			AstraPersonPredictor personPredictor, AstraTripPredictor tripPredictor) {
		super(parameters, predictor);

		this.parameters = parameters;
		this.personPredictor = personPredictor;
		this.tripPredictor = tripPredictor;
		this.predictor = predictor;
	}

	protected double estimateTravelTimeUtility(CarVariables variables) {
		return super.estimateTravelTimeUtility(variables) //
				* EstimatorUtils.interaction(variables.euclideanDistance_km, parameters.referenceEuclideanDistance_km,
						parameters.lambdaTravelTimeEuclideanDistance);
	}

	protected double estimateMonetaryCostUtility(CarVariables variables, AstraPersonVariables personVariables) {
		return super.estimateMonetaryCostUtility(variables) //
				* EstimatorUtils.interaction(personVariables.householdIncome_MU, parameters.referenceHouseholdIncome_MU,
						parameters.lambdaCostHouseholdIncome);
	}

	protected double estimateAgeUtility(AstraPersonVariables variables) {
		return variables.age_a >= 60 ? parameters.astraCar.betaAgeOver60 : 0.0;
	}

	protected double estimateWorkUtility(AstraTripVariables variables) {
		return variables.isWork ? parameters.astraCar.betaWork : 0.0;
	}

	protected double estimateCityUtility(AstraTripVariables variables) {
		return variables.isCity ? parameters.astraCar.betaCity : 0.0;
	}

	@Override
	public double estimateUtility(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		CarVariables variables = predictor.predictVariables(person, trip, elements);
		AstraPersonVariables personVariables = personPredictor.predictVariables(person, trip, elements);
		AstraTripVariables tripVariables = tripPredictor.predictVariables(person, trip, elements);

		// Log cost information once for debugging
		if (!loggedCost) {
			log.warn("=== BASELINE CAR COST DEBUG ===");
			log.warn("CarVariables.cost: " + variables.cost);
			log.warn("Distance (km): " + variables.euclideanDistance_km);
			log.warn("Cost per km: " + (variables.cost / Math.max(0.001, variables.euclideanDistance_km)));
			log.warn("betaCost_u_MU: " + parameters.betaCost_u_MU);
			loggedCost = true;
		}

		double utility = 0.0;

		utility += estimateConstantUtility();
		utility += estimateTravelTimeUtility(variables);
		utility += estimateAccessEgressTimeUtility(variables);
		utility += estimateMonetaryCostUtility(variables, personVariables);
		utility += estimateAgeUtility(personVariables);
		utility += estimateWorkUtility(tripVariables);
		utility += estimateCityUtility(tripVariables);

		Leg leg = (Leg) elements.get(0);
		leg.getAttributes().putAttribute("isNew", true);

		return utility;
	}
}
