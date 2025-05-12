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
import abmt2025.project.travel_time.SmoothingTravelTimeModule;
import org.eqasim.core.simulation.mode_choice.EqasimModeChoiceModule;

public class RunSimulation_Baseline {
	public RunSimulation_Baseline() {
        super(); // Explicitly call the superclass constructor, though this is implicit
    }
	static public void main(String[] args) throws ConfigurationException, MalformedURLException, IOException {
		// Some paramters added from AdPT

		
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("config-path","output-directory","output-sim-name") // --config-path "path-to-your-config-file/config.xml" is required
				.allowPrefixes( "mode-parameter", "cost-parameter") //
				.build();

		AstraConfigurator_Baseline astraConfigurator_Baseline = new AstraConfigurator_Baseline();
		Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"), astraConfigurator_Baseline.getConfigGroups());
		AstraConfigurator_Baseline.configure(config);
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
        		
		Scenario scenario = ScenarioUtils.createScenario(config);  
		
		SwitzerlandConfigurator switzerlandConfigurator = new SwitzerlandConfigurator();

		switzerlandConfigurator.configureScenario(scenario);
		ScenarioUtils.loadScenario(scenario);
		switzerlandConfigurator.adjustScenario(scenario);
		astraConfigurator_Baseline.adjustScenario(scenario);

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

		// EqasimLinkSpeedCalcilator deactivated!

		Controler controller = new Controler(scenario); // add something to run DRT controllers
		switzerlandConfigurator.configureController(controller);
		//controller.addOverridingModule(new EqasimAnalysisModule());
		// controller.addOverridingModule(new CustomEqasimModeChoiceModule());
		controller.addOverridingModule(new EqasimModeChoiceModule());
		controller.addOverridingModule(new SwissModeChoiceModule(cmd));
		controller.addOverridingModule(new AstraModule_Baseline(cmd));

		AstraConfigurator_Baseline.configureController(controller, cmd);

		controller.addOverridingModule(new SmoothingTravelTimeModule());
		
		controller.run();
	}
}