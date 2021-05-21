package org.matsim.velbert.prepare;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;

@CommandLine.Command(
        name="clean-population",
        description = "remove network references, route information and facility ids from plans. Also keep only selected plans",
        showDefaultValues = true
)
public class CleanPopulation implements MATSimAppCommand {

    private static final Logger log = Logger.getLogger(CleanPopulation.class);
    private static final String populationFile = "projects/matsim-velbert/matsim-input/matsim-velbert-1.0/tmp-25pct.plans.xml.gz";

    @CommandLine.Option(names = "--sharedSvn", description = "path to shared svn root folder")
    private String sharedSvn;

    public static void main(String[] args) {

        System.exit(new CommandLine(new CreateNetwork()).execute(args));
    }

    @Override
    public Integer call() {
        var scenario = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());

        log.info("loading population");
        var populationPath = Paths.get(sharedSvn).resolve(populationFile);
        new PopulationReader(scenario).readFile(populationPath.toString());

        log.info("Removing route information and setting all legs to 'walk'");
        scenario.getPopulation().getPersons().values().parallelStream()
                .forEach(person -> {

                    var newPlan = PopulationUtils.createPlan();
                    var trips = TripStructureUtils.getTrips(person.getSelectedPlan());

                    var firstActivity = (Activity) person.getSelectedPlan().getPlanElements().get(0);
                    firstActivity.setLinkId(null);
                    firstActivity.setFacilityId(null);
                    newPlan.addActivity(firstActivity); // copy the first activity

                    // clear plans
                    person.setSelectedPlan(null);
                    person.getPlans().clear();

                    // add more activities and legs if there is more than one activity
                    for (var trip : trips) {

                        // put in all walk legs since we have to re-calibrate anyway
                        newPlan.addLeg(PopulationUtils.createLeg(TransportMode.walk));
                        var activity = trip.getDestinationActivity();
                        activity.setLinkId(null);
                        activity.setFacilityId(null);
                        newPlan.addActivity(trip.getDestinationActivity());
                    }

                    person.addPlan(newPlan);

                    // add subpopulation attribute
                    person.getAttributes().putAttribute("subpopulation", "person");
                });

        new PopulationWriter(scenario.getPopulation()).write(populationPath.toString());

        return 0;
    }
}
