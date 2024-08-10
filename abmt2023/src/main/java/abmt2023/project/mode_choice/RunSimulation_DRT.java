package abmt2023.project.mode_choice;

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

import abmt2023.project.config.AstraConfigurator_DRT;
import abmt2023.project.config.AstraConfigurator_DRT;
import abmt2023.project.travel_time.SmoothingTravelTimeModule;
import abmt2023.project.utils.OutputPathConfigurator;
import abmt2023.project.mode_choice.CustomEqasimModeChoiceModule;
import abmt2023.project.mode_choice.estimators.DRTUtilityEstimator;
import abmt2023.project.mode_choice.costs.OperatorCostCalculator;
import abmt2023.project.mode_choice.DrtCostParameters;


public class RunSimulation_DRT {
	public RunSimulation_DRT() {
        super(); // Explicitly call the superclass constructor, though this is implicit
    }
	static public void main(String[] args) throws ConfigurationException, MalformedURLException, IOException {
		// Some paramters added from AdPT

		
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("config-path","output-directory","output-sim-name") // --config-path "path-to-your-config-file/config.xml" is required
				.allowPrefixes( "mode-parameter", "cost-parameter") //
				.build();
				
		Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"), AstraConfigurator_DRT.getConfigGroups());
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

        config.controler().setOutputDirectory(path.toString());
    	config.controler().setLastIteration(3); // Taking value from config file when commented out
        
        DvrpConfigGroup dvrpConfig = new DvrpConfigGroup();
        config.addModule(dvrpConfig);

        MultiModeDrtConfigGroup multiModeDrtConfig = new MultiModeDrtConfigGroup();
        config.addModule(multiModeDrtConfig);
		
		Scenario scenario = ScenarioUtils.createScenario(config);  
		
		
		SwitzerlandConfigurator.configureScenario(scenario);
		ScenarioUtils.loadScenario(scenario);
		SwitzerlandConfigurator.adjustScenario(scenario);
		AstraConfigurator_DRT.adjustScenario(scenario);

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
		SwitzerlandConfigurator.configureController(controller);
		//controller.addOverridingModule(new EqasimAnalysisModule());
		// controller.addOverridingModule(new CustomEqasimModeChoiceModule());
		controller.addOverridingModule(new EqasimModeChoiceModule());
		controller.addOverridingModule(new SwissModeChoiceModule(cmd));
		controller.addOverridingModule(new AstraModule_DRT(cmd));

		AstraConfigurator_DRT.configureController(controller, cmd);

		controller.addOverridingModule(new SmoothingTravelTimeModule());
		
		//Create drt controller object 
        
		// // Instantiate OperatorCostCalculator
		// DrtCostParameters drtCostParams = DrtCostParameters.buildDefault(); // Modify as needed
		// OperatorCostCalculator costCalculator = new OperatorCostCalculator(scenario, drtCostParams);

		// // Add an IterationEndsListener to calculate costs at the end of the simulation
		// controller.addControlerListener(new IterationEndsListener() {
		// 	@Override
		// 	public void notifyIterationEnds(IterationEndsEvent event) {
		// 		if (event.getIteration() == event.getServices().getConfig().controler().getLastIteration()) {
		// 			costCalculator.calculateAndWriteOperatorCosts();
		// 		}
		// 	}
		// });

		controller.addOverridingModule(new DvrpModule());
		controller.addOverridingModule(new MultiModeDrtModule());
		controller.configureQSimComponents(components -> {
            DvrpQSimComponents.activateAllModes(MultiModeDrtConfigGroup.get(config)).configure(components);
        });

		controller.run();
	}
}