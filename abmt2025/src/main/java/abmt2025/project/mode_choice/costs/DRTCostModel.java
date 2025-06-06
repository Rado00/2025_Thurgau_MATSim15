package abmt2025.project.mode_choice.costs;

import java.util.List;

import org.eqasim.core.simulation.mode_choice.cost.AbstractCostModel;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;

import abmt2025.project.mode_choice.DrtCostParameters;
import abmt2025.project.mode_choice.predictors.AstraPersonPredictor;

public class DRTCostModel extends AbstractCostModel {

	private final DrtCostParameters parameters;
	private final AstraPersonPredictor predictor;

	@Inject
	public DRTCostModel(DrtCostParameters costParameters, AstraPersonPredictor predictor) {
		super("drt");
		this.parameters = costParameters;
		this.predictor = predictor;
	}
	

	@Override
	public double calculateCost_MU(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		double Alpha = 1;
		
	
		return parameters.DRTFare_CHF + parameters.DRTFare_CHF_km * getInVehicleDistance_km(elements) * Alpha;
	}

}
