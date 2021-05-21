package org.matsim.velbert.prepare;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.osm.networkReader.LinkProperties;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@CommandLine.Command(
        name="network",
        description = "Create matsim network from osm data",
        showDefaultValues = true
)
public class CreateNetwork implements MATSimAppCommand {

    private static final Logger log = LogManager.getLogger(CreateNetwork.class);
    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25832");
    private static final String osmFile = "projects\\matsim-velbert\\raw-input\\osm\\nordrhein-westfalen-latest.osm.pbf";
    private static final String scenarioRegionShapeFile ="projects\\matsim-velbert\\matsim-input\\matsim-velbert-snz-original\\dilutionArea.shp";

    private static final String outputFile = "projects\\matsim-velbert\\matsim-input\\matsim-verlbert-1.0\\matsim-verlbert-1.0.network.xml.gz";

    @CommandLine.Option(names = "--sharedSvn", description = "path to shared svn root folder")
    private String sharedSvn;

    public static void main(String[] args) {
        System.exit(new CommandLine(new CreateNetwork()).execute(args));
    }

    @Override
    public Integer call() {

        var svn = Paths.get(sharedSvn);

        var geometry = getGeometry(svn);

        var network = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .setIncludeLinkAtCoordWithHierarchy((coord, level) -> {
                    if (level <= LinkProperties.LEVEL_TERTIARY) return true;

                    return geometry.covers(MGC.coord2Point(coord));
                })
                .build()
                .read(svn.resolve(osmFile));

        new NetworkCleaner().run(network);

        new NetworkWriter(network).write(svn.resolve(outputFile).toString());

        return 0;
    }

    private PreparedGeometry getGeometry(Path svn) {

        var geometry = ShapeFileReader.getAllFeatures(svn.resolve(scenarioRegionShapeFile).toString()).stream()
                .map(simpleFeature -> (Geometry)simpleFeature.getDefaultGeometry())
                .findFirst()
                .orElseThrow();

        var factory = new PreparedGeometryFactory();
        return factory.create(geometry);

    }
}
