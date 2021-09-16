package org.matsim.velbert.analysis;

import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.gis.ShapeFileReader;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

public class RunTripEventHandlerHW2 {

    //set static variables
    private static final String shapefilePath = "C:\\path\\to\\theFile.shp";
    private static final String networkPath = "C:\\path\\to\\the\\velbert-Sensitivity-10pct.output_network.xml.gz";
    private static final String eventsPath = "C:\\path\\to\\the\\velbert-Sensitivity-10pct.output_events.xml.gz";

    public static void main(String[] args){

        //read in the shapefile and transform it to a Geometry
        //(the shapefile should already be in EPSG:25832)
        var featureVelbert = ShapeFileReader.getAllFeatures(shapefilePath);
        var shpVelbert = featureVelbert.stream()
                .map(feature -> (Geometry) feature.getDefaultGeometry())
                .findAny()
                .orElseThrow();

        //read in the network
        Network network = NetworkUtils.readNetwork(networkPath);

        //create the event manager and handler; add the handler to the manager
        var manager = EventsUtils.createEventsManager();
        var handler = new TripEventHandlerHW2(network, shpVelbert);
        manager.addHandler(handler);

        //read in the events file
        EventsUtils.readEvents(manager, eventsPath);

        //get the results
        var linkLeaveCounts = handler.getLinkLeaveCounts();
        var linkFreeSpeeds = handler.getLinkFreeSpeeds();

        //write out the results
        String eol = System.getProperty("line.separator");

        //linkLeaveCounts
        try (Writer writer = new FileWriter("C:\\path\\to\\the\\linkLeaveCounts.csv")){
            for (Map.Entry<Id<Link>,Integer> entry : linkLeaveCounts.entrySet()) {
                writer.append(entry.getKey().toString())
                        .append(',')
                        .append(entry.getValue().toString())
                        .append(eol);
            }
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }

        //linkFreeSpeeds
        try (Writer writer = new FileWriter("C:\\path\\to\\the\\linkFreeSpeeds.csv")){
            for (Map.Entry<Id<Link>,Double> entry : linkFreeSpeeds.entrySet()) {
                writer.append(entry.getKey().toString())
                        .append(',')
                        .append(entry.getValue().toString())
                        .append(eol);
            }
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }


    }

}
