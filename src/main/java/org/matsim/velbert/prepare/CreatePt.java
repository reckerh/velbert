package org.matsim.velbert.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.gtfs.GtfsConverter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.utils.CreatePseudoNetwork;
import org.matsim.pt.utils.CreateVehiclesForSchedule;
import org.matsim.vehicles.MatsimVehicleWriter;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@CommandLine.Command(
        name = "pt", description = "Create pt-schedule, pt-vehicles and a merged network",
        showDefaultValues = true
)
public class CreatePt implements MATSimAppCommand {
    private static final String schedule = "projects/matsim-velbert/raw-input/gtfs/2021_04_29_vvr_gtfs.zip";
    private static final String transitSchedule = "projects/matsim-velbert/matsim-input/matsim-velbert-v1.0/matsim-velbert-v1.0.transit-schedule.xml.gz";
    private static final String transitVehicles = "projects/matsim-velbert/matsim-input/matsim-velbert-v1.0/matsim-velbert-v1.0.transit-vehicles.xml.gz";
    private static final String inputNetwork = "projects/matsim-velbert/matsim-input/matsim-velbert-v1.0/matsim-velbert-v1.0.network.xml.gz";

    @CommandLine.Option(names = "--sharedSvn", description = "path to shared svn root folder")
    private String sharedSvn = "bla";

    public static void main(String[] args) {

        System.exit(new CommandLine(new CreateNetwork()).execute(args));
    }

    @Override
    public Integer call() {

        var sharedSvn = Paths.get(this.sharedSvn);
        var scenario = ScenarioUtils.createMutableScenario(ConfigUtils.createConfig());
        var network = NetworkUtils.readNetwork(sharedSvn.resolve(inputNetwork).toString());
        scenario.setNetwork(network);

        GtfsConverter.newBuilder()
                .setScenario(scenario)
                .setTransform(TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:25832"))
                .setDate(LocalDate.now())
                .setFeed(sharedSvn.resolve(schedule))
                .build()
                .convert();

        //Create simple transit vehicles with a pcu of 0
        new CreateVehiclesForSchedule(scenario.getTransitSchedule(), scenario.getTransitVehicles()).run();
        scenario.getTransitVehicles().getVehicleTypes().forEach((id, type) -> type.setPcuEquivalents(0));

        new CreatePseudoNetwork(scenario.getTransitSchedule(), scenario.getNetwork(), "pt_").createNetwork();

        writeScheduleVehiclesAndNetwork(scenario, sharedSvn);

        return 0;
    }

    private static void writeScheduleVehiclesAndNetwork(Scenario scenario, Path svn) {

        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(svn.resolve(transitSchedule).toString());
        new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(svn.resolve(transitVehicles).toString());
        new NetworkWriter(scenario.getNetwork()).write(svn.resolve(inputNetwork).toString());
    }
}
