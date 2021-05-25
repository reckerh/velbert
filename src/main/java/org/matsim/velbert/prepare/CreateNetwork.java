package org.matsim.velbert.prepare;

import com.google.common.collect.Streams;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.osm.networkReader.LinkProperties;
import org.matsim.contrib.osm.networkReader.OsmBicycleReader;
import org.matsim.contrib.osm.networkReader.OsmTags;
import org.matsim.contrib.osm.networkReader.SupersonicOsmNetworkReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
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
import java.util.Set;
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
    private static final Set<String> carRideBike = Set.of(TransportMode.car, TransportMode.ride, TransportMode.bike);
    private static final Set<String> carRide = Set.of(TransportMode.car, TransportMode.ride);

    private static final String nrwOsmFile = "projects/matsim-velbert/raw-input/osm/nordrhein-westfalen-20210521.osm.pbf";
    private static final String germanyOsmFile = "projects/matsim-velbert/raw-input/osm/germany-20210521.osm.pbf";
    private static final String scenarioRegionShapeFile ="projects/matsim-velbert/matsim-input/matsim-velbert-snz-original/dilutionArea.shp";

    private static final String outputFile = "projects/matsim-velbert/matsim-input/matsim-velbert-v1.0/matsim-velbert-v1.0.network.xml.gz";

    @CommandLine.Option(names = "--sharedSvn", description = "path to shared svn root folder")
    private String sharedSvn;

    public static void main(String[] args) {
        System.exit(new CommandLine(new CreateNetwork()).execute(args));
    }

    @Override
    public Integer call() {

        var svn = Paths.get(sharedSvn);

        /*
       If used for real studies this model should probably also include a coarse network of germany. Becuase some agents
       Live outside NRW. For the matsim class those agents are filtered out to achieve shorter simulation runs
        var coarseLinkProperties = LinkProperties.createLinkProperties().entrySet().stream()
                .filter(entry -> entry.getValue().getHierarchyLevel() <= LinkProperties.LEVEL_PRIMARY)
                .collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue));

       log.info("reading in coarse network");
        var coarseNetwork = new SupersonicOsmNetworkReader.Builder()
                .setCoordinateTransformation(transformation)
                .setLinkProperties(coarseLinkProperties)
                .setAfterLinkCreated((link, tags, direction) -> setAllowedMode(link, tags))
                .build()
                .read(svn.resolve(germanyOsmFile));

        log.info("done reading coarse network");

        */

        // include only links which were not read from the corse network already
        var fineLinkProperties = LinkProperties.createLinkProperties().entrySet().stream()
                .filter(entry -> entry.getValue().getHierarchyLevel() > LinkProperties.LEVEL_PRIMARY)
                .collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue));

        log.info("Loading shape file for diluation area");
        var dilutionArea = getDilutionArea(svn);
        var veryDetailedArea = getBox(dilutionArea.getCentroid(), 20000);

        log.info("Start to parse network. This might not output anything for a while");
        var network = new OsmBicycleReader.Builder()
                .setCoordinateTransformation(transformation)
                .setLinkProperties(new ConcurrentHashMap<>(LinkProperties.createLinkProperties()))
                .setIncludeLinkAtCoordWithHierarchy((coord, level) -> {
                    if (level <= LinkProperties.LEVEL_SECONDARY) return true;
                    return veryDetailedArea.covers(MGC.coord2Point(coord));
                })
                .setAfterLinkCreated((link, tags, direction) -> setAllowedMode(link, tags))
                .build()
                .read(svn.resolve(nrwOsmFile));

  //      log.info("merge networks");
  //      var network = Streams.concat(coarseNetwork.getLinks().values().stream(), fineNetwork.getLinks().values().stream())
        //.collect(NetworkUtils.getCollector());

        log.info("Finished parsing network. Start Network cleaner.");
        new MultimodalNetworkCleaner(network).run(Set.of(TransportMode.car));
        new MultimodalNetworkCleaner(network).run(Set.of(TransportMode.ride));
        new MultimodalNetworkCleaner(network).run(Set.of(TransportMode.bike));

        log.info("Finished cleaning network. Write network");
        new NetworkWriter(network).write(svn.resolve(outputFile).toString());

        log.info("Finished CreateNetwork. Exiting.");
        return 0;
    }

    private Geometry getDilutionArea(Path svn) {

        return ShapeFileReader.getAllFeatures(svn.resolve(scenarioRegionShapeFile).toString()).stream()
                .map(simpleFeature -> (Geometry)simpleFeature.getDefaultGeometry())
                .findFirst()
                .orElseThrow();
    }

    private PreparedGeometry getBox(Point center, double diameter) {

        var left = center.getX() - diameter / 2;
        var right = center.getX() + diameter / 2;
        var top = center.getY() + diameter / 2;
        var bottom = center.getY() - diameter / 2;

        var geometry = new GeometryFactory().createPolygon(new Coordinate[]{
                new Coordinate(left, top), new Coordinate(right, top), new Coordinate(right, bottom), new Coordinate(left, bottom), new Coordinate(left, top)
        });

        return new PreparedGeometryFactory().create(geometry);
    }

    private void setAllowedMode(Link link, Map<String, String> tags) {

        if (isCarOnly(tags)) {
            link.setAllowedModes(carRide);
        } else {
            link.setAllowedModes(carRideBike);
        }
    }

    private boolean isCarOnly (Map<String, String> tags) {

        var highwayType = tags.get(OsmTags.HIGHWAY);
        return highwayType == null || highwayType.equals(OsmTags.MOTORWAY) || highwayType.equals(OsmTags.MOTORWAY_LINK) || highwayType.equals(OsmTags.TRUNK) || highwayType.equals(OsmTags.TRUNK_LINK);
    }
}
