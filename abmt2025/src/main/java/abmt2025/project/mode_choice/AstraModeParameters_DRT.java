package abmt2025.project.mode_choice;

import org.eqasim.switzerland.mode_choice.parameters.SwissModeParameters;

public class AstraModeParameters_DRT extends SwissModeParameters {
	static public class AstraBaseModeParameters {
		public double betaAgeOver60 = 0.0;
		public double betaWork = 0.0;
		public double betaCity = 0.0;
		public double betaASC = 0.0;
		public double betaInVehicleTime = 0.0;
		public double betaWaitingTime = 0.0;
		public double betaAccessEgressTime = 0.0;

		public double travelTimeThreshold_min = 0.0;
	}

	public AstraBaseModeParameters astraWalk = new AstraBaseModeParameters();
	public AstraBaseModeParameters astraBike = new AstraBaseModeParameters();
	public AstraBaseModeParameters astraCar = new AstraBaseModeParameters();
	public AstraBaseModeParameters astraAv = new AstraBaseModeParameters();
	public AstraBaseModeParameters astraDRT = new AstraBaseModeParameters();

	public class AstraPtParameters {
		public double betaRailTravelTime_u_min = 0.0;
		public double betaBusTravelTime_u_min = 0.0;
		public double betaFeederTravelTime_u_min = 0.0;

		public double betaHeadway_u_min = 0.0;
		public double betaOvgkB_u = 0.0;
		public double betaOvgkC_u = 0.0;
		public double betaOvgkD_u = 0.0;
		public double betaOvgkNone_u = 0.0;
	}

	public AstraPtParameters astraPt = new AstraPtParameters();

	public double lambdaTravelTimeEuclideanDistance = 0.0;
	public double lambdaCostHouseholdIncome = 0.0;
	public double referenceHouseholdIncome_MU = 0.0;

	static public AstraModeParameters_DRT buildFrom6Feb2020() {
		AstraModeParameters_DRT parameters = new AstraModeParameters_DRT();

		// General
		parameters.betaCost_u_MU = -0.0888;

		parameters.lambdaCostHouseholdIncome = -0.8169;
		parameters.lambdaCostEuclideanDistance = -0.2209;
		parameters.lambdaTravelTimeEuclideanDistance = 0.1147;

		parameters.referenceEuclideanDistance_km = 39.0;
		parameters.referenceHouseholdIncome_MU = 12260.0;

		//The ones for Modal Split Calibration
		parameters.walk.alpha_u = 1.4; //-------- 0,5903
		parameters.bike.alpha_u = 1.5; //-------- 0,1522
		parameters.pt.alpha_u = 0; //--------
		parameters.car.alpha_u = 1.4; // Original from fb model: 0.2235;Horl 2021 -0.8 in the end
		parameters.astraCar.betaCity = -0.2; //-------- -0.459

		// Public transport
		parameters.pt.betaWaitingTime_u_min = -0.0124;
		parameters.pt.betaAccessEgressTime_u_min = -0.0142;

		parameters.astraPt.betaFeederTravelTime_u_min = -0.0452;
		parameters.astraPt.betaBusTravelTime_u_min = -0.0124;
		parameters.astraPt.betaRailTravelTime_u_min = -0.0072;
		parameters.astraPt.betaHeadway_u_min = -0.0301;

		parameters.astraPt.betaOvgkB_u = -1.7436;
		parameters.astraPt.betaOvgkC_u = -1.6413;
		parameters.astraPt.betaOvgkD_u = -0.9649;
		parameters.astraPt.betaOvgkNone_u = -1.0889;

		// Bicycle
		parameters.bike.betaTravelTime_u_min = -0.1258;

		parameters.astraBike.betaAgeOver60 = -2.6588;

		// Car
		parameters.car.betaTravelTime_u_min = -0.0192;

		parameters.astraCar.betaWork = -1.1606;

		// Walking
		parameters.walk.betaTravelTime_u_min = -0.0457;

		parameters.astraWalk.travelTimeThreshold_min = 120.0;
		
		// DRT
		parameters.astraDRT.betaASC = -0.061;
		parameters.astraDRT.betaInVehicleTime = -0.015;
		parameters.astraDRT.betaAccessEgressTime = -0.014; // Took the one of PT.
		parameters.astraDRT.betaWaitingTime = -0.093;
		parameters.astraDRT.betaWork = -1.938;
		parameters.astraDRT.betaAgeOver60 = 0.0;	// -2.6588; // Took the one of bike.
		

		return parameters;
	}
}
