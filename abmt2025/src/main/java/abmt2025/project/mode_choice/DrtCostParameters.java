package abmt2025.project.mode_choice;

import org.eqasim.switzerland.mode_choice.parameters.SwissCostParameters;

public class DrtCostParameters extends SwissCostParameters {

	public double carCost_CHF_km = 0.0;

	public double ptCost_CHF_km = 0.0;
	public double ptMinimumCost_CHF = 0.0;

	public double ptRegionalRadius_km = 0.0;
	
	public double DRTCost_CHF_distance = 0.0;
	public double DRTCost_CHF_trip = 0.0;
	public double DRTCost_CHF_vehicle = 0.0;
	public double DRTFare_CHF = 2;
	public double DRTFare_CHF_km = 0.5;
	
	//TODO: add your own cost parameters
	public static DrtCostParameters buildDefault() {
		DrtCostParameters parameters = new DrtCostParameters();

		parameters.carCost_CHF_km = 0.26;	

		parameters.ptCost_CHF_km = 0.6;

		parameters.ptMinimumCost_CHF = 2.7;

		parameters.ptRegionalRadius_km = 15.0;
		
		parameters.DRTCost_CHF_distance = 0.098; // 
		parameters.DRTCost_CHF_trip = 0.375; // 
		parameters.DRTCost_CHF_vehicle = 33.30; // 

		// price = DRTFare_CHF_km * invehicle_distance + DRTFare_CHF
		// Can be overridden via system properties: -DDRT_FARE_CHF=2.0 -DDRT_FARE_CHF_KM=0.7
		parameters.DRTFare_CHF = Double.parseDouble(System.getProperty("DRT_FARE_CHF", "0")); // fixed constant price DRT
		parameters.DRTFare_CHF_km = Double.parseDouble(System.getProperty("DRT_FARE_CHF_KM", "0")); // km price DRT
		

		return parameters;
	}
}
