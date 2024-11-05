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
import org.matsim.core.scenario.ScenarioUtils;

import abmt2023.project.config.AstraConfigurator_DRT;
import abmt2023.project.travel_time.SmoothingTravelTimeModule;

public class RunSimulation_DRT {

    public static void main(String[] args) throws ConfigurationException, MalformedURLException, IOException {
        CommandLine cmd = new CommandLine.Builder(args)
                .requireOptions("config-path", "output-directory", "output-sim-name")
                .allowPrefixes("mode-parameter", "cost-parameter")
                .build();

        AstraConfigurator_DRT astraConfigurator = new AstraConfigurator_DRT();
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

        // Add necessary config groups for DRT and DVRP
        config.addModule(new DvrpConfigGroup());
        config.addModule(new MultiModeDrtConfigGroup());

        // Load and configure scenario
        Scenario scenario = ScenarioUtils.createScenario(config);
        SwitzerlandConfigurator switzerlandConfigurator = new SwitzerlandConfigurator();
        switzerlandConfigurator.configureScenario(scenario);
        ScenarioUtils.loadScenario(scenario);

        // Adjust scenario with Astra configurator
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

        // Set up DRT route factory
        scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DrtRoute.class, new DrtRouteFactory());

        // Create MATSim controller
        Controler controller = new Controler(scenario);
        switzerlandConfigurator.configureController(controller);

        // Add necessary EQASIM and mode choice modules
        controller.addOverridingModule(new EqasimModeChoiceModule());
        controller.addOverridingModule(new SwissModeChoiceModule(cmd));
        controller.addOverridingModule(new AstraModule_DRT(cmd));
        astraConfigurator.configureController(controller, cmd);

        // Add additional modules for smoothing travel time and DRT
        controller.addOverridingModule(new SmoothingTravelTimeModule());
        controller.addOverridingModule(new DvrpModule());
        // controller.addOverridingModule(new MultiModeDrtModule()); ToCheck

        // Configure QSim for DRT
        controller.configureQSimComponents(components -> {
            DvrpQSimComponents.activateAllModes(MultiModeDrtConfigGroup.get(config)).configure(components);
        });

        // Run the simulation
        controller.run();
    }
}
