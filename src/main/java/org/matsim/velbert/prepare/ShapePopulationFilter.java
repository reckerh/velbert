package org.matsim.velbert.prepare;

import org.apache.commons.configuration.ConfigurationUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/**
 * Removes agents which have any activity outside filter shape. This is used for the matsim class to make the model
 * a little smaller
 */
@CommandLine.Command(
        name="filter-population",
        description = "Filter population of NRW",
        showDefaultValues = true
)
public class ShapePopulationFilter implements MATSimAppCommand {

    private static final Logger log = LogManager.getLogger(ShapePopulationFilter.class);

    private static final String populationFile = "projects/matsim-velbert/matsim-input/matsim-velbert-v1.0/tmp-25pct.plans.xml.gz";
    private static final String filterShapeFile = "projects/matsim-velbert/raw-input/germany-federal-states-shp/VG250_LAN.shp";

    @CommandLine.Option(names = "--sharedSvn", description = "path to shared svn root folder")
    private String sharedSvn;

    public static void main(String[] args) {
        System.exit(new CommandLine(new ShapePopulationFilter()).execute(args));
    }
    @Override
    public Integer call() {

        var svnPath = Paths.get(sharedSvn);
        var populationPath = svnPath.resolve(populationFile);

        var scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(populationPath.toString());

        var filterGeometry = getNordrheinWestfalen(svnPath.resolve(filterShapeFile));

        var personsToRemove = scenario.getPopulation().getPersons().values().parallelStream()
                .filter(person -> {
                    var activities = TripStructureUtils.getActivities(person.getSelectedPlan(), TripStructureUtils.StageActivityHandling.ExcludeStageActivities);
                    return activities.stream().anyMatch(activity -> !filterGeometry.covers(MGC.coord2Point(activity.getCoord())));
                })
                .collect(Collectors.toList());

        log.info("Filter " + personsToRemove.size() + " of " + scenario.getPopulation().getPersons().size() + " persons.");
        for (var person : personsToRemove) {
            scenario.getPopulation().removePerson(person.getId());
        }

        new PopulationWriter(scenario.getPopulation()).write(populationPath.toString());

        return 0;
    }

    private PreparedGeometry getNordrheinWestfalen(Path shapeFile) {

        // this assumes that there is only one feature which belongs to Nordrhein-Westfalen
        return ShapeFileReader.getAllFeatures(shapeFile.toString()).stream()
                .filter(feature -> feature.getAttribute("AGS").equals("05"))
                .map(feature -> (Geometry)feature.getDefaultGeometry())
                .map(geometry -> new PreparedGeometryFactory().create(geometry))
                .findAny()
                .orElseThrow();
    }
}
