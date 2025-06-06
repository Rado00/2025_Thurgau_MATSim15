package abmt2025.project.config;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.components.raptor.EqasimRaptorConfigGroup; // ADD IN WHEN I WANT TO USE SWISS RAIL RAPTOR
import org.eqasim.core.components.transit.EqasimTransitQSimModule;
import org.eqasim.core.simulation.EqasimConfigurator;
// import org.eqasim.core.simulation.calibration.CalibrationConfigGroup; TODO SUBSTITUTE IF NEEEDED
import org.eqasim.core.simulation.termination.EqasimTerminationConfigGroup; //Added for MATSim 15
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contribs.discrete_mode_choice.modules.DiscreteModeChoiceModule;
import org.matsim.contribs.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.groups.ReplanningConfigGroup.StrategySettings;
import org.matsim.core.controler.Controler;
import org.matsim.households.Household;

import abmt2025.project.mode_choice.AstraModeAvailability_DRT;
import abmt2025.project.mode_choice.InfiniteHeadwayConstraint;
import abmt2025.project.mode_choice.estimators.AstraBikeUtilityEstimator_DRT;
import abmt2025.project.mode_choice.estimators.AstraCarUtilityEstimator_DRT;
import abmt2025.project.mode_choice.estimators.AstraPtUtilityEstimator_DRT;
import abmt2025.project.mode_choice.estimators.AstraWalkUtilityEstimator_DRT;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;

public class AstraConfigurator_DRT extends EqasimConfigurator {
	public AstraConfigurator_DRT() {
	}

	public ConfigGroup[] getConfigGroups() {
		return new ConfigGroup[] { //
				new SwissRailRaptorConfigGroup(), //
				new EqasimConfigGroup(), //
				new DiscreteModeChoiceConfigGroup(), //
				// new CalibrationConfigGroup(), //
				new AstraConfigGroup(), //
				new EqasimTerminationConfigGroup(),
				new EqasimRaptorConfigGroup()
		};
	}

	static public void configure(Config config) {
		EqasimConfigGroup eqasimConfig = EqasimConfigGroup.get(config);
		
		config.qsim().setNumberOfThreads(Math.min(12, Runtime.getRuntime().availableProcessors()));
		config.global().setNumberOfThreads(Runtime.getRuntime().availableProcessors());

		for (StrategySettings strategy : config.replanning().getStrategySettings()) {
			if (strategy.getStrategyName().equals(DiscreteModeChoiceModule.STRATEGY_NAME)) {
				strategy.setWeight(0.05);
			} else {
				strategy.setWeight(0.95);
			}
		}

		// General eqasim
		eqasimConfig.setAnalysisInterval(config.controller().getWriteEventsInterval());

		// Estimators
		eqasimConfig.setEstimator(TransportMode.car, AstraCarUtilityEstimator_DRT.NAME);
		eqasimConfig.setEstimator(TransportMode.pt, AstraPtUtilityEstimator_DRT.NAME);
		eqasimConfig.setEstimator(TransportMode.bike, AstraBikeUtilityEstimator_DRT.NAME);
		eqasimConfig.setEstimator(TransportMode.walk, AstraWalkUtilityEstimator_DRT.NAME);

		DiscreteModeChoiceConfigGroup dmcConfig = (DiscreteModeChoiceConfigGroup) config.getModules()
				.get(DiscreteModeChoiceConfigGroup.GROUP_NAME);

		Set<String> tripConstraints = new HashSet<>(dmcConfig.getTripConstraints());
		tripConstraints.add(InfiniteHeadwayConstraint.NAME);
		dmcConfig.setTripConstraints(tripConstraints);

		dmcConfig.setModeAvailability(AstraModeAvailability_DRT.NAME);		
	}	

	public void adjustScenario(Scenario scenario) {
		for (Household household : scenario.getHouseholds().getHouseholds().values()) {
			for (Id<Person> memberId : household.getMemberIds()) {
				Person person = scenario.getPopulation().getPersons().get(memberId);

				if (person != null) {
					person.getAttributes().putAttribute("householdIncome", household.getIncome().getIncome());
				}
			}
		}		
		adjustBikeAvailability(scenario);
	}

	static private void adjustBikeAvailability(Scenario scenario) {
		Random random = new Random(scenario.getConfig().global().getRandomSeed());
		AstraConfigGroup astraConfig = AstraConfigGroup.get(scenario.getConfig());

		for (Person person : scenario.getPopulation().getPersons().values()) {
			if (!person.getId().toString().contains("freight")) {
				if (!person.getAttributes().getAttribute("bikeAvailability").equals("FOR_NONE")) {
					if (random.nextDouble() > astraConfig.getBikeAvailability()) {
						person.getAttributes().putAttribute("bikeAvailability", "FOR_NONE");
					}
				}
			}
		}
	}

	static public void configureController(Controler controller, CommandLine commandLine) {		

		controller.configureQSimComponents(configurator -> {
			EqasimTransitQSimModule.configure(configurator, controller.getConfig());
		});
	}
}
