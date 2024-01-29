package abmt2023.week9.lectures.drt;

import org.matsim.api.core.v01.Scenario;
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
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

public class RunDrtCustom {

    public static void main(String[] args) {
        // Variable for the percentage part of the paths
        String pctValue = "10pct"; // Change this to "25pct", "100pct", etc. as needed

        // Construct the configPath and outputDirectory using the pctValue
        String configPath = "/cluster/scratch/salathem/data/scenarios/Zurich/" + pctValue + "/zurich_config_drt.xml";
        String outputDirectory = "/cluster/scratch/salathem/data/output/output_zurich_drt_" + pctValue;

        // Load the config object
        Config config = ConfigUtils.loadConfig(configPath);

        // Configure DVRP and DRT module
        DvrpConfigGroup dvrpConfig = new DvrpConfigGroup();
        config.addModule(dvrpConfig);

		MultiModeDrtConfigGroup multiModeDrtConfig = new MultiModeDrtConfigGroup();
		config.addModule(multiModeDrtConfig);

		config.controler().setOutputDirectory(outputDirectory);
		config.controler().setLastIteration(0);

        // Load the scenario object
        Scenario scenario = ScenarioUtils.loadScenario(config);

        // Add DRT route factory
        scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DrtRoute.class,
                new DrtRouteFactory());

        // Create the controller object
        Controler controler = new Controler(scenario);

        controler.addOverridingModule(new DvrpModule());
        controler.addOverridingModule(new MultiModeDrtModule());
        controler.configureQSimComponents(components -> {
            DvrpQSimComponents.activateAllModes(multiModeDrtConfig).configure(components);
        });

        controler.run();
    }
}