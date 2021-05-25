package org.matsim.velbert;

import org.matsim.application.MATSimApplication;
import org.matsim.application.prepare.population.DownSamplePopulation;
import org.matsim.application.prepare.population.ResolveGridCoordinates;
import org.matsim.application.prepare.population.TrajectoryToPlans;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.velbert.prepare.*;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(header = ":: Open Velbert Scenario ::", version="1.0")
@MATSimApplication.Prepare({
        CreateNetwork.class, CreatePt.class, TrajectoryToPlans.class, ResolveGridCoordinates.class,
        DownSamplePopulation.class, CleanPopulation.class, CreateVehicleTypes.class, ShapePopulationFilter.class
})
public class VelbertApplication extends MATSimApplication {

    public static void main(String[] args) {
        MATSimApplication.run(VelbertApplication.class, args);
    }

    @Override
    protected Config prepareConfig(Config config) {

        for (long ii = 600; ii <= 97200; ii += 600) {

            for (String act : List.of("business", "educ_higher", "educ_kiga", "educ_other", "educ_primary", "exuc_secondary",
                    "educ_tertiary", "errands", "home", "leasure", "shop_daily", "shop_other", "visit", "word")) {
                config.planCalcScore()
                        .addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams(act + "_" + ii + ".0").setTypicalDuration(ii));
            }

            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("work_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("business_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(6. * 3600.).setClosingTime(20. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("leisure_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(9. * 3600.).setClosingTime(27. * 3600.));
            config.planCalcScore().addActivityParams(new PlanCalcScoreConfigGroup.ActivityParams("shopping_" + ii + ".0").setTypicalDuration(ii)
                    .setOpeningTime(8. * 3600.).setClosingTime(20. * 3600.));
        }

        config.plansCalcRoute().setAccessEgressType(PlansCalcRouteConfigGroup.AccessEgressType.accessEgressModeToLink);
        config.qsim().setUsingTravelTimeCheckInTeleportation(true);
        config.qsim().setUsePersonIdForMissingVehicleId(false);

        return config;
    }
}
