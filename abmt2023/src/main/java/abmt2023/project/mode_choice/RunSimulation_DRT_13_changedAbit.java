// package abmt2023.project.mode_choice;

// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.io.IOException;
// import java.net.MalformedURLException;

// import org.eqasim.core.components.config.EqasimConfigGroup;
// import org.eqasim.core.simulation.analysis.EqasimAnalysisModule;
// import org.eqasim.core.simulation.mode_choice.EqasimModeChoiceModule;
// import org.eqasim.switzerland.SwitzerlandConfigurator;
// import org.eqasim.switzerland.mode_choice.SwissModeChoiceModule;
// import org.matsim.api.core.v01.Scenario;
// import org.matsim.api.core.v01.network.Link;
// import org.matsim.api.core.v01.population.Leg;
// import org.matsim.api.core.v01.population.Person;
// import org.matsim.api.core.v01.population.Plan;
// import org.matsim.api.core.v01.population.PlanElement;
// import org.matsim.contrib.drt.routing.DrtRoute;
// import org.matsim.contrib.drt.routing.DrtRouteFactory;
// import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
// import org.matsim.contrib.drt.run.MultiModeDrtModule;
// import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
// import org.matsim.contrib.dvrp.run.DvrpModule;
// import org.matsim.contrib.dvrp.run.DvrpQSimComponents;
// import org.matsim.core.config.CommandLine;
// import org.matsim.core.config.CommandLine.ConfigurationException;
// import org.matsim.core.config.Config;
// import org.matsim.core.config.ConfigUtils;
// import org.matsim.core.controler.Controler;
// import org.matsim.core.router.TripStructureUtils;
// import org.matsim.core.scenario.ScenarioUtils;
// import org.matsim.core.controler.events.IterationEndsEvent;
// import org.matsim.core.controler.listener.IterationEndsListener;

// import abmt2023.project.config.AstraConfigurator_DRT;
// import abmt2023.project.utils.OutputPathConfigurator;
// import abmt2023.project.mode_choice.LocalScenarioAdjuster; // Import my new class

// import abmt2023.project.travel_time.SmoothingTravelTimeModule;
// import abmt2023.project.mode_choice.CustomEqasimModeChoiceModule;
// import abmt2023.project.mode_choice.estimators.DRTUtilityEstimator;
// import abmt2023.project.mode_choice.costs.OperatorCostCalculator;
// import abmt2023.project.mode_choice.DrtCostParameters;


// public class RunSimulation_DRT {
// 	public RunSimulation_DRT() {
//         super(); // Explicitly call the superclass constructor, though this is implicit
//     }
// 	static public void main(String[] args) throws ConfigurationException, MalformedURLException, IOException {
// 		// Some paramters added from AdPT

		
// 		CommandLine cmd = new CommandLine.Builder(args) //
// 				.requireOptions("config-path","output-directory","output-sim-name") // --config-path "path-to-your-config-file/config.xml" is required
// 				.allowPrefixes( "mode-parameter", "cost-parameter") //
// 				.build();
		
// 		// Load and configure MATSim config
// 		Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"), AstraConfigurator_DRT.getConfigGroups());
// 		AstraConfigurator_DRT.configure(config);
// 		cmd.applyConfiguration(config);
		
// 		// Set output directory
// 		String outputDirectory = cmd.getOptionStrict("output-directory");
// 		String outputSimName = cmd.getOptionStrict("output-sim-name");
//         Path path = Paths.get(outputDirectory, outputSimName);
//         int index = 0;
//         while (Files.exists(path)) {
//             index++;
//             path = Paths.get(outputDirectory, outputSimName + index);
//         }
//         config.controler().setOutputDirectory(path.toString());
//     	config.controler().setLastIteration(3); // Taking value from config file when commented out
        
// 		// Add DRT config groups
//         DvrpConfigGroup dvrpConfig = new DvrpConfigGroup();
//         config.addModule(dvrpConfig);
//         MultiModeDrtConfigGroup multiModeDrtConfig = new MultiModeDrtConfigGroup();
//         config.addModule(multiModeDrtConfig);
		
// 		// Load scenario
// 		Scenario scenario = ScenarioUtils.createScenario(config);  
// 		SwitzerlandConfigurator.configureScenario(scenario);
// 		ScenarioUtils.loadScenario(scenario);

// 		// Replace adjustScenario call with your local implementation
// 		LocalScenarioAdjuster scenarioAdjuster = new LocalScenarioAdjuster(); // Create an instance of your adjuster
// 		scenarioAdjuster.adjustScenario(scenario); // Use your local adjustScenario method - To solve below line
// 		// SwitzerlandConfigurator.adjustScenario(scenario); It was not working with MATSim 15
//         AstraConfigurator_DRT.adjustScenario(scenario); // Keep existing adjustments if needed

// 		EqasimConfigGroup eqasimConfig = EqasimConfigGroup.get(config);

// 		// Adjust link speeds based on eqasimConfig settings
// 		for (Link link : scenario.getNetwork().getLinks().values()) {
// 			double maximumSpeed = link.getFreespeed();
// 			boolean isMajor = true;

// 			for (Link other : link.getToNode().getInLinks().values()) {
// 				if (other.getCapacity() >= link.getCapacity()) {
// 					isMajor = false;
// 				}
// 			}

// 			if (!isMajor && link.getToNode().getInLinks().size() > 1) {
// 				double travelTime = link.getLength() / maximumSpeed;
// 				travelTime += eqasimConfig.getCrossingPenalty();
// 				link.setFreespeed(link.getLength() / travelTime);
// 			}
// 		}

// 		// Set up DRT route factory
//         scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DrtRoute.class,
//                 new DrtRouteFactory());

//         // Create MATSim controller
//         Controler controller = new Controler(scenario);
//         SwitzerlandConfigurator.configureController(controller);

//         // Add EQASIM mode choice and analysis modules
//         controller.addOverridingModule(new EqasimModeChoiceModule());
//         controller.addOverridingModule(new SwissModeChoiceModule(cmd));
//         controller.addOverridingModule(new AstraModule_DRT(cmd));
//         AstraConfigurator_DRT.configureController(controller, cmd);

//         // Add smoothing travel time module
//         controller.addOverridingModule(new SmoothingTravelTimeModule());

//         // Set up DRT modules
//         controller.addOverridingModule(new DvrpModule());
//         controller.addOverridingModule(new MultiModeDrtModule());
//         controller.configureQSimComponents(components -> {
//             DvrpQSimComponents.activateAllModes(MultiModeDrtConfigGroup.get(config)).configure(components);
//         });

// 		controller.run();
// 	}
// }