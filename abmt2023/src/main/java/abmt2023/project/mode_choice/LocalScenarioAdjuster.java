package abmt2023.project.mode_choice;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.households.Household;

public class LocalScenarioAdjuster {

    public void adjustScenario(Scenario scenario) {
        // Use parallel stream to process households concurrently
        scenario.getHouseholds().getHouseholds().values().parallelStream().forEach(household -> {
            // For each member in the household, get their ID and check if they exist in the population
            for (Id<Person> memberId : household.getMemberIds()) {
                Person person = scenario.getPopulation().getPersons().get(memberId);

                // If the person exists in the population, copy attributes from the household to the person
                if (person != null) {
                    copyAttribute(household, person, "bikeAvailability");
                    copyAttribute(household, person, "spRegion");
                }
            }
        });
    }

    // Helper method to copy a specific attribute from household to person
    private void copyAttribute(Household household, Person person, String attribute) {
        Object householdAttribute = household.getAttributes().getAttribute(attribute);

        if (householdAttribute != null) {
            person.getAttributes().putAttribute(attribute, householdAttribute);
        }
    }
}
