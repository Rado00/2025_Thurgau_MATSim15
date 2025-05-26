package abmt2025.project.mode_choice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.net.MalformedURLException;

import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.simulation.analysis.EqasimAnalysisModule;
import org.eqasim.core.simulation.mode_choice.EqasimModeChoiceModule;
import org.eqasim.switzerland.SwitzerlandConfigurator;
import org.eqasim.switzerland.mode_choice.SwissModeChoiceModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.routing.DrtRouteFactory;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtModule;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.IterationEndsListener;

import abmt2025.project.config.AstraConfigurator_Baseline;
import abmt2025.project.config.AstraConfigurator_DRT;
import abmt2025.project.config.AstraConfigurator_DRT;
import abmt2025.project.travel_time.SmoothingTravelTimeModule;
import abmt2025.project.mode_choice.CustomEqasimModeChoiceModule;
import abmt2025.project.mode_choice.estimators.DRTUtilityEstimator;
import abmt2025.project.mode_choice.costs.OperatorCostCalculator;
import abmt2025.project.mode_choice.DrtCostParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.matsim.core.router.util.TravelTime;
import org.matsim.contrib.dvrp.run.DvrpMode;
import com.google.inject.Key;
import com.google.inject.name.Names;
import org.matsim.core.controler.AbstractModule;


public class RunSimulation_DRT {
	public RunSimulation_DRT() {
        super(); // Explicitly call the superclass constructor, though this is implicit
    }

	private static final Logger LOG = LogManager.getLogger(RunSimulation_DRT.class);
	static public void main(String[] args) throws ConfigurationException, MalformedURLException, IOException {
		// Some paramters added from AdPT

		
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("config-path","output-directory","output-sim-name") // --config-path "path-to-your-config-file/config.xml" is required
				.allowPrefixes( "mode-parameter", "cost-parameter") //
				.build();
				
		if (!cmd.hasOption("config-path")) {
			throw new RuntimeException("ERROR: No config-path provided! The simulation will now exit.");
		}		
		
		AstraConfigurator_DRT astraConfigurator_DRT = new AstraConfigurator_DRT();
		Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"), astraConfigurator_DRT.getConfigGroups());
		AstraConfigurator_DRT.configure(config);
		cmd.applyConfiguration(config);
		
		// Set output directory to a unique directory
		String outputDirectory = cmd.getOptionStrict("output-directory");
		String outputSimName = cmd.getOptionStrict("output-sim-name");
        Path path = Paths.get(outputDirectory, outputSimName);
        int index = 0;

        while (Files.exists(path)) {
            index++;
            path = Paths.get(outputDirectory, outputSimName + index);
        }

        config.controller().setOutputDirectory(path.toString());
    	// config.controller().setLastIteration(60); // PASSING IT THROUGH THE autoRun SHELL FILE
        
        DvrpConfigGroup dvrpConfig = new DvrpConfigGroup();
        config.addModule(dvrpConfig);

        MultiModeDrtConfigGroup multiModeDrtConfig = new MultiModeDrtConfigGroup();
        config.addModule(multiModeDrtConfig);
		
		Scenario scenario = ScenarioUtils.createScenario(config);  
		
		SwitzerlandConfigurator switzerlandConfigurator = new SwitzerlandConfigurator();

		switzerlandConfigurator.configureScenario(scenario);
		ScenarioUtils.loadScenario(scenario);
		switzerlandConfigurator.adjustScenario(scenario);
		astraConfigurator_DRT.adjustScenario(scenario);
		
		// 🔍 Debugging: Log all loaded DRT vehicles in the log file
		scenario.getVehicles().getVehicles().values().stream()
			.filter(v -> v.getId().toString().contains("drt"))
			.forEach(v -> LOG.info("Loaded DRT vehicle: " + v.getId()));

		EqasimConfigGroup eqasimConfig = EqasimConfigGroup.get(config);

		for (Link link : scenario.getNetwork().getLinks().values()) {
			double maximumSpeed = link.getFreespeed();
			boolean isMajor = true;

			for (Link other : link.getToNode().getInLinks().values()) {
				if (other.getCapacity() >= link.getCapacity()) {
					isMajor = false;
				}
			}

			if (!isMajor && link.getToNode().getInLinks().size() > 1) {
				double travelTime = link.getLength() / maximumSpeed;
				travelTime += eqasimConfig.getCrossingPenalty();
				link.setFreespeed(link.getLength() / travelTime);
			}
		}

		 // Add DRT route factory
        scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DrtRoute.class,
                new DrtRouteFactory());
		
		// EqasimLinkSpeedCalcilator deactivated!

		Controler controller = new Controler(scenario); // add something to run DRT controllers
		switzerlandConfigurator.configureController(controller);
		// controller.addOverridingModule(new CustomEqasimModeChoiceModule());
		controller.addOverridingModule(new EqasimModeChoiceModule());
		controller.addOverridingModule(new SwissModeChoiceModule(cmd));
		controller.addOverridingModule(new AstraModule_DRT(cmd));

		AstraConfigurator_DRT.configureController(controller, cmd);

		controller.addOverridingModule(new SmoothingTravelTimeModule());
		
		//Create drt controller object 
        
		// // Instantiate OperatorCostCalculator
		DrtCostParameters drtCostParams = DrtCostParameters.buildDefault(); 
		OperatorCostCalculator costCalculator = new OperatorCostCalculator(scenario, drtCostParams);
		// Add Cost Calculation at the End of the Simulation
		controller.addControlerListener(new IterationEndsListener() {
			@Override
			public void notifyIterationEnds(IterationEndsEvent event) {
				if (event.getIteration() == event.getServices().getConfig().controller().getLastIteration()) {
					costCalculator.calculateAndWriteOperatorCosts();
				}
			}
		});
		

		controller.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bind(TravelTime.class)
					.annotatedWith(Names.named("drt"))
					.to(Key.get(TravelTime.class, Names.named("dvrp_estimated")));
			}
		});



		controller.configureQSimComponents(components -> {
            DvrpQSimComponents.activateAllModes(MultiModeDrtConfigGroup.get(config)).configure(components);
        });

		controller.run();
	}
}