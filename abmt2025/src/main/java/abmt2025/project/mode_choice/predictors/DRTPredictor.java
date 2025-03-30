package abmt2023.project.mode_choice.predictors;

import java.util.List;

import org.eqasim.core.simulation.mode_choice.cost.CostModel;
import org.eqasim.core.simulation.mode_choice.parameters.ModeParameters;
import org.eqasim.core.simulation.mode_choice.utilities.predictors.CachedVariablePredictor;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import abmt2023.project.mode_choice.variables.DRTVariables;

public class DRTPredictor extends CachedVariablePredictor<DRTVariables> {

	
	//private double totalWaitingTime = 0.0;
    //private int numIterations = 0;
	private double waitingtime_min;

	private final CostModel costModel;
	private final ModeParameters parameters;
	
	@Inject
	public DRTPredictor(ModeParameters parameters, @Named("drt") CostModel costModel) {
		this.costModel = costModel;
		this.parameters = parameters;
	}
	
	@Override
	protected DRTVariables predict(Person person, DiscreteModeChoiceTrip trip, List<? extends PlanElement> elements) {
		
		double invehicletime_min = 0.0;
		double accesstime_min = 0.0;
		
		
		for (PlanElement pe : elements) {

			if (pe instanceof Leg) {
				Leg leg = (Leg) pe;
				if (leg.getMode().equals("drt")) {
					DrtRoute route = (DrtRoute)leg.getRoute();
					route.getMaxWaitTime();
					route.getMaxTravelTime();
					
					waitingtime_min = route.getMaxWaitTime()/60;
					invehicletime_min = route.getMaxTravelTime()/60;
					

				}
			}

		}
			
		double cost_CHF = costModel.calculateCost_MU(person, trip, elements);
		accesstime_min =0;
		
		// Calcola il nuovo waiting time come la media delle iterazioni precedenti
       // double avgWaitingTime_min = numIterations > 0 ? totalWaitingTime / numIterations : 0.0;
		
		// Use the new waiting time
        //double waitingtime_min = avgWaitingTime_min;

        // Update the state variables for the next iteration 
        //totalWaitingTime += waitingtime_min;
        //numIterations++;
        
        
		//waitingtime_min =5;
		
		
		return new DRTVariables(cost_CHF, invehicletime_min, accesstime_min, waitingtime_min);
	}

}

