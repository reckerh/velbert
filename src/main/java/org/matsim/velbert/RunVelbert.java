package org.matsim.velbert;

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.velbert.analysis.TripAnalyzerModule;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RunVelbert {

    private static final String shapeFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/velbert/velbert-v1.0/shapes/Postleitzahlengebiete-shp/OSM_PLZ_072019.shp";
    private static final Set<String> zipCodes = Set.of("42551", "42549", "42555", "42553");

    public static void main(String[] args) throws MalformedURLException, FactoryException {

        var config = ConfigUtils.loadConfig(args);

        config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink);

        for (long ii = 600; ii <= 97200; ii += 600) {

            for (String act : List.of("educ_higher", "educ_kiga", "educ_other", "educ_primary", "educ_secondary",
                    "educ_tertiary", "errands", "home", "visit")) {
                config.planCalcScore()
                        .addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams(act + "_" + ii + ".0").setTypicalDuration(ii));
            }

            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("work_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("business_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("leisure_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(9. * 3600.).setClosingTime(27. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("shop_daily_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("shop_other_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
        }

        var scenario = ScenarioUtils.loadScenario(config);

        var controler = new Controler(scenario);

        // use the (congested) car travel time for the teleported ride mode
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addTravelTimeBinding(TransportMode.ride).to(networkTravelTime());
                addTravelDisutilityFactoryBinding(TransportMode.ride).to(carTravelDisutilityFactoryKey());
            }
        });

        config.controler().setLastIteration(0);

        var dilutionArea = getDilutionArea();
        var numberOfPersonsInFilter = scenario.getPopulation().getPersons().values().stream()
                .map(HasPlansAndId::getSelectedPlan)
                .map(plan -> TripStructureUtils.getActivities(plan, TripStructureUtils.StageActivityHandling.ExcludeStageActivities).get(0))
                .filter(home -> dilutionArea.stream().anyMatch(geometry -> geometry.covers(MGC.coord2Point(home.getCoord()))))
                .collect(Collectors.toSet()).size();


        var analyzerModule = new TripAnalyzerModule(personId -> {
            var person = scenario.getPopulation().getPersons().get(personId);
            var firstActivity = TripStructureUtils.getActivities(person.getSelectedPlan(), TripStructureUtils.StageActivityHandling.ExcludeStageActivities).get(0);
            return dilutionArea.stream().anyMatch(geometry -> geometry.covers(MGC.coord2Point(firstActivity.getCoord())));
        });
        controler.addOverridingModule(analyzerModule);

        controler.run();
    }

    private static Collection<PreparedGeometry> getDilutionArea() throws FactoryException, MalformedURLException {

        var factory = new PreparedGeometryFactory();
        var fromCRS = CRS.decode("EPSG:3857");
        var toCRS = CRS.decode("EPSG:25832");
        var transformation = CRS.findMathTransform(fromCRS, toCRS);

        var uri = URI.create(shapeFile);
        return ShapeFileReader.getAllFeatures(uri.toURL()).stream()
                .filter(simpleFeature -> zipCodes.contains((String) simpleFeature.getAttribute("plz")))
                .map(simpleFeature -> (Geometry) simpleFeature.getDefaultGeometry())
                .map(geometry -> transform(geometry, transformation))
                .map(factory::create)
                .collect(Collectors.toSet());

    }

    private static Geometry transform(Geometry geometry, MathTransform transform) {
        try {
            return JTS.transform(geometry, transform);
        } catch (TransformException e) {
            throw new RuntimeException(e);
        }
    }
}
