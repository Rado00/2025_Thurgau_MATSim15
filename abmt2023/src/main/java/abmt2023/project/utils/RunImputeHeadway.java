package abmt2023.project.utils;

// Import statements
import abmt2023.project.utils.headway.HeadwayImputer;
import abmt2023.project.utils.headway.HeadwayImputerModule;

import org.eqasim.core.misc.InjectorBuilder;
import org.eqasim.core.simulation.EqasimConfigurator;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import com.google.inject.Injector;
import org.matsim.pt.routes.DefaultTransitPassengerRouteFactory;
import org.matsim.pt.routes.TransitPassengerRoute;

public class RunImputeHeadway {
    public static void main(String[] args) throws ConfigurationException, InterruptedException {
        CommandLine cmd = new CommandLine.Builder(args) //
                .requireOptions("config-path", "output-path") //
                .allowOptions("threads", "batch-size") //
                .build();

        // Create an instance of EqasimConfigurator instead of calling static methods
        EqasimConfigurator eqasimConfigurator = new EqasimConfigurator();

        // Load and configure MATSim config
        Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"), eqasimConfigurator.getConfigGroups());
        cmd.applyConfiguration(config);
        config.strategy().clearStrategySettings();

        int batchSize = cmd.getOption("batch-size").map(Integer::parseInt).orElse(100);
        int numberOfThreads = cmd.getOption("threads").map(Integer::parseInt)
                .orElse(Runtime.getRuntime().availableProcessors());

        // Load scenario
        Scenario scenario = ScenarioUtils.createScenario(config);
        scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(TransitPassengerRoute.class,
                new DefaultTransitPassengerRouteFactory());
        ScenarioUtils.loadScenario(scenario);

        // Build injector using the instance of EqasimConfigurator
        Injector injector = new InjectorBuilder(scenario) //
                .addOverridingModules(eqasimConfigurator.getModules()) //
                .addOverridingModule(new HeadwayImputerModule(numberOfThreads, batchSize, true, 2.0 * 3600.0)) //
                .build();

        // Run headway imputation
        HeadwayImputer headwayImputer = injector.getInstance(HeadwayImputer.class);
        headwayImputer.run(scenario.getPopulation());

        // Write the updated population to the output path
        new PopulationWriter(scenario.getPopulation()).write(cmd.getOptionStrict("output-path"));
    }
}
