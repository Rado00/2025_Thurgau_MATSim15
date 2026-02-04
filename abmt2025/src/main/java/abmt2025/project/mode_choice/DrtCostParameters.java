package abmt2025.project.mode_choice;

import org.eqasim.switzerland.mode_choice.parameters.SwissCostParameters;

public class DrtCostParameters extends SwissCostParameters {

	private static double getRequiredDoubleProperty(String name) {
		String value = System.getProperty(name);
		if (value == null) {
			throw new IllegalStateException("Required system property '" + name + "' is not set. " +
				"Please provide it via -D" + name + "=<value> in the java command.");
		}
		return Double.parseDouble(value);
	}

	public double carCost_CHF_km = 0.0;

	public double ptCost_CHF_km = 0.0;
	public double ptMinimumCost_CHF = 0.0;

	public double ptRegionalRadius_km = 0.0;

	// Legacy cost parameters (used in utility estimation)
	public double DRTCost_CHF_distance = 0.0;
	public double DRTCost_CHF_trip = 0.0;
	public double DRTCost_CHF_vehicle = 0.0;

	// Passenger fare parameters (for revenue calculation)
	public double DRTFare_CHF = 2;
	public double DRTFare_CHF_km = 0.5;

	// Bösch 2018 operator cost parameters (from Bösch et al. 2018, Table 2)
	// "Cost-based Analysis of Autonomous Mobility Services"
	// Values for conventional DRT with driver in Swiss context
	// Vehicle distance-based costs (fuel, maintenance, depreciation): ~0.15-0.20 CHF/km
	public double DRTOperatorCost_CHF_vehicleKm = 0.20;
	// Driver hourly cost (Swiss wages): ~30-40 CHF/hour
	public double DRTOperatorCost_CHF_driverHour = 35.0;
	// Fixed daily cost per vehicle (insurance, cleaning, parking): ~20-40 CHF/vehicle/day
	public double DRTOperatorCost_CHF_vehicleDay = 30.0;
	
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
		// Must be set via system properties: -DDRT_FARE_CHF=2.0 -DDRT_FARE_CHF_KM=0.7
		parameters.DRTFare_CHF = getRequiredDoubleProperty("DRT_FARE_CHF"); // fixed constant price DRT
		parameters.DRTFare_CHF_km = getRequiredDoubleProperty("DRT_FARE_CHF_KM"); // km price DRT

		// Bösch 2018 operator cost parameters are already initialized with defaults
		// (see field declarations above with values from Bösch et al. 2018)

		return parameters;
	}
}
