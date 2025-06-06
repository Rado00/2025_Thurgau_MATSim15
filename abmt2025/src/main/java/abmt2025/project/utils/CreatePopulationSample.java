package abmt2025.project.utils;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;

public class CreatePopulationSample {

	public static void main(String[] args) {
		MutableScenario sc = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());

		PopulationReader reader = new PopulationReader(sc);
		
		double sample = 0.5;

		// input population file
		reader.readFile("/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario/100pct/population.xml.gz");

		MutableScenario sc2 = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());

		for (Person person : sc.getPopulation().getPersons().values()) {

			if (MatsimRandom.getRandom().nextDouble() < sample)	
				sc2.getPopulation().addPerson(person);
		}

		// output population file
		new PopulationWriter(sc2.getPopulation()).write("/home/muaa/DATA_ABM/2024_Paper2_Data/MATSim_Thurgau/Baseline_Scenario/50pct/population.xml.gz");

	}

}
