package abmt2023.project.mode_choice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.net.MalformedURLException;

import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.simulation.mode_choice.EqasimModeChoiceModule;
import org.eqasim.switzerland.SwitzerlandConfigurator;
import org.eqasim.switzerland.mode_choice.SwissModeChoiceModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

import abmt2023.project.config.AstraConfigurator_Baseline;
import abmt2023.project.travel_time.SmoothingTravelTimeModule;

public class RunSimulation_Baseline {

    public static void main(String[] args) throws ConfigurationException, MalformedURLException, IOException {
        CommandLine cmd = new CommandLine.Builder(args) //
                .requireOptions("config-path", "output-directory", "output-sim-name") // --config-path "path-to-your-config-file/config.xml" is required
                .allowPrefixes("mode-parameter", "cost-parameter") //
                .build();

        // Create an instance of AstraConfigurator_Baseline
        AstraConfigurator_Baseline astraConfigurator = new AstraConfigurator_Baseline();

        // Load and configure MATSim config
        Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"), astraConfigurator.getConfigGroups());
        astraConfigurator.configure(config);  
        cmd.applyConfiguration(config);

        // Set output directory
        String outputDirectory = cmd.getOptionStrict("output-directory");
        String outputSimName = cmd.getOptionStrict("output-sim-name");
        Path path = Paths.get(outputDirectory, outputSimName);
        int index = 0;
        while (Files.exists(path)) {
            index++;
            path = Paths.get(outputDirectory, outputSimName + index);
        }
        config.controler().setOutputDirectory(path.toString());

        // Load scenario
        Scenario scenario = ScenarioUtils.createScenario(config);
        
        // Create an instance of SwitzerlandConfigurator and configure the scenario
        SwitzerlandConfigurator switzerlandConfigurator = new SwitzerlandConfigurator();
        switzerlandConfigurator.configureScenario(scenario);  // Non-static method call

        ScenarioUtils.loadScenario(scenario);

        // Adjust the scenario using the instance
        astraConfigurator.adjustScenario(scenario);  

        EqasimConfigGroup eqasimConfig = EqasimConfigGroup.get(config);

        // Adjust link speeds based on eqasimConfig settings
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

        // Create MATSim controller
        Controler controller = new Controler(scenario);

        // Configure the controller using the SwitzerlandConfigurator instance
        switzerlandConfigurator.configureController(controller);  // Non-static method call

        // Add EQASIM mode choice and analysis modules
        controller.addOverridingModule(new EqasimModeChoiceModule());
        controller.addOverridingModule(new SwissModeChoiceModule(cmd));
        controller.addOverridingModule(new AstraModule_Baseline(cmd));

        // Configure controller using the instance of astraConfigurator
        astraConfigurator.configureController(controller, cmd);  

        // Add smoothing travel time module
        controller.addOverridingModule(new SmoothingTravelTimeModule());

        // Run the simulation
        controller.run();
    }
}
