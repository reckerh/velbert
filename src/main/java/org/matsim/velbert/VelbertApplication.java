package org.matsim.velbert;

import org.matsim.application.MATSimApplication;
import org.matsim.application.prepare.population.ResolveGridCoordinates;
import org.matsim.application.prepare.population.TrajectoryToPlans;
import org.matsim.velbert.prepare.CreateNetwork;
import org.matsim.velbert.prepare.CreatePt;
import picocli.CommandLine;

@CommandLine.Command(header = ":: Open Velbert Scenario ::", version="1.0")
@MATSimApplication.Prepare({
        CreateNetwork.class, CreatePt.class, TrajectoryToPlans.class, ResolveGridCoordinates.class
})
public class VelbertApplication extends MATSimApplication {

    public static void main(String[] args) {
        MATSimApplication.run(VelbertApplication.class, args);
    }
}
