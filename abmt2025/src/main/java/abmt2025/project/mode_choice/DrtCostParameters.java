package abmt2023.project.mode_choice;

import org.eqasim.switzerland.mode_choice.parameters.SwissCostParameters;

public class DrtCostParameters extends SwissCostParameters {

	public double carCost_CHF_km = 0.0;

	public double ptCost_CHF_km = 0.0;
	public double ptMinimumCost_CHF = 0.0;

	public double ptRegionalRadius_km = 0.0;
	
	public double DRTCost_CHF_distance = 0.0;
	public double DRTCost_CHF_trip = 0.0;
	public double DRTCost_CHF_vehicle = 0.0;
	public double DRTFare_CHF = 0.0;
	public double DRTFare_CHF_km = 0.0;
	
	//TODO: add your own cost parameters
	public static DrtCostParameters buildDefault() {
		DrtCostParameters parameters = new DrtCostParameters();

		parameters.carCost_CHF_km = 0.26;	

		parameters.ptCost_CHF_km = 0.6;

		parameters.ptMinimumCost_CHF = 2.7;

		parameters.ptRegionalRadius_km = 15.0;
		
		parameters.DRTCost_CHF_distance = 0.098;
		parameters.DRTCost_CHF_trip = 0.375;
		parameters.DRTCost_CHF_vehicle = 33.30;

		// price = 0.6 CHF/km * invehicle_distance + 2 CHF
		parameters.DRTFare_CHF = 0; // fixed constant price DRT 2.0
		parameters.DRTFare_CHF_km = 0; // km price DRT  0.7
		

		return parameters;
	}
}
