package org.matsim.velbert.prepare;

import com.google.common.collect.Streams;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.osm.networkReader.LinkProperties;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@CommandLine.Command(
        name="network",
        description = "Create matsim network from osm data",
        showDefaultValues = true
)
public class CreateNetwork implements MATSimAppCommand {

    private static final Logger log = LogManager.getLogger(CreateNetwork.class);
    private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation("EPSG:4326", "EPSG:25832");
    private static final String nrwOsmFile = "projects/matsim-velbert/raw-input/osm/nordrhein-westfalen-20210521.osm.pbf";
    private static final String germanyOsmFile = "projects/matsim-velbert/raw-input/osm/germany-20210521.osm.pbf";
    private static final String scenarioRegionShapeFile ="projects/matsim-velbert/matsim-input/matsim-velbert-snz-original/dilutionArea.shp";

    private static final String outputFile = "projects/matsim-velbert/matsim-input/matsim-velbert-1.0/matsim-verlbert-1.0.network.xml.gz";

    @CommandLine.Option(names = "--sharedSvn", description = "path to shared svn root folder")
    private String sharedSvn;

    public static void main(String[] args) {
        System.exit(new CommandLine(new CreateNetwork()).execute(args));
    }

    @Override
    public Integer call() {

        var svn = Paths.get(sharedSvn);

        var coarseLinkProperties = LinkProperties.createLinkProperties().entrySet().stream()
                .filter(entry -> entry.getValue().getHierarchyLevel() <= LinkProperties.LEVEL_PRIMARY)
                .collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue));

        log.info("reading in coarse network");
        var coarseNetwork = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .setLinkProperties(coarseLinkProperties)
                .build()
                .read(svn.resolve(germanyOsmFile));

        log.info("done reading coarse network");

        log.info("Loading shape file for diluation area");
        var geometry = getGeometry(svn);
        // include only links which were not read from the corse network already
        var fineLinkProperties = LinkProperties.createLinkProperties().entrySet().stream()
                .filter(entry -> entry.getValue().getHierarchyLevel() > LinkProperties.LEVEL_PRIMARY)
                .collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue));

        log.info("Start to parse network. This might not output anything for a while");
        var fineNetwork = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .setLinkProperties(fineLinkProperties)
                .setIncludeLinkAtCoordWithHierarchy((coord, level) -> {
                    if (level <= LinkProperties.LEVEL_TERTIARY) return true;

                    return geometry.covers(MGC.coord2Point(coord));
                })
                .build()
                .read(svn.resolve(nrwOsmFile));

        log.info("merge networks");
        var network = Streams.concat(coarseNetwork.getLinks().values().stream(), fineNetwork.getLinks().values().stream())
                .collect(NetworkUtils.getCollector());

        log.info("Finished parsing network. Start Network cleaner.");
        new NetworkCleaner().run(network);

        log.info("Finished cleaning network. Write network");
        new NetworkWriter(network).write(svn.resolve(outputFile).toString());

        log.info("Finished CreateNetwork. Exiting.");
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
