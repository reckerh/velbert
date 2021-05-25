package org.matsim.velbert.prepare;

import org.matsim.api.core.v01.Id;
import org.matsim.application.MATSimAppCommand;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.VehiclesFactory;
import picocli.CommandLine;

import java.nio.file.Paths;

@CommandLine.Command(
        name="vehicle-types",
        description = "Create matsim vehicle types",
        showDefaultValues = true
)
public class CreateVehicleTypes implements MATSimAppCommand {

    private static final String vehiclesFile = "projects/matsim-velbert/matsim-input/matsim-velbert-v1.0/matsim-velbert-v1.0.vehicles.xml.gz";

    @CommandLine.Option(names = "--sharedSvn", description = "path to shared svn root folder")
    private String sharedSvn;

    public static void main(String[] args) {
        System.exit(new CommandLine(new CreateVehicleTypes()).execute(args));
    }

    @Override
    public Integer call() {

        var container = VehicleUtils.createVehiclesContainer();
        var factory = VehicleUtils.getFactory();
        container.addVehicleType(createVehicleType("car", 7.5, 36.1111111111, 1, factory));
        container.addVehicleType(createVehicleType("ride", 7.5, 36.1111111111, 1, factory));
        container.addVehicleType(createVehicleType("bike", 2.5, 3.5, 0.25, factory));

        new MatsimVehicleWriter(container).writeFile(Paths.get(sharedSvn).resolve(vehiclesFile).toString());
        return 0;
    }

    private static VehicleType createVehicleType(String id, double length, double maxV, double pce, VehiclesFactory factory) {
        var vehicleType = factory.createVehicleType(Id.create(id, VehicleType.class));
        vehicleType.setNetworkMode(id);
        vehicleType.setPcuEquivalents(pce);
        vehicleType.setLength(length);
        vehicleType.setMaximumVelocity(maxV);
        vehicleType.setWidth(1.0);
        return vehicleType;
    }
}
