package org.matsim.velbert.analysis;

import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.util.HashMap;
import java.util.Map;

public class TripEventHandlerHW2 implements LinkLeaveEventHandler {

    //variable definition
    private final Map<Id<Link>, Integer> linkLeaveCounts = new HashMap<>();
    private final Map<Id<Link>, Double> linkFreeSpeeds = new HashMap<>();
    private final Network network;
    private final Geometry shpVelbert;

    //constructor
    public TripEventHandlerHW2(Network network, Geometry shpVelbert){
        this.network = network;
        this.shpVelbert = shpVelbert;
    }

    //getter methods
    public Map<Id<Link>, Integer> getLinkLeaveCounts() {return linkLeaveCounts;}
    public Map<Id<Link>, Double> getLinkFreeSpeeds() {return linkFreeSpeeds;}

    //event handling
    @Override
    public void handleEvent(LinkLeaveEvent event) {

        //get the Id of the link that is being left
        Id<Link> linkId = event.getLinkId();

        //get the coordinates of the link that is being left
        Coord coord = network.getLinks().get(linkId).getCoord();

        //if the link being left is no pt link and its coords lie in Velbert
        if (!isPtLink(linkId, network) & isInGeometry(coord, shpVelbert)){

            //add a value of one if the link already exists in the map or set a value of one if it does not yet exist
            if(linkLeaveCounts.containsKey(linkId)){
                linkLeaveCounts.put(linkId, linkLeaveCounts.get(linkId)+1);
            } else {
                linkLeaveCounts.put(linkId, 1);
            }

            //add the freespeed of the link (the network should be the output network) to the freespeed-hashmap
            linkFreeSpeeds.put(linkId, network.getLinks().get(linkId).getFreespeed());

        }



    }

    //functions
    private boolean isInGeometry(Coord coord, Geometry shpVelbert){
        return shpVelbert.covers(MGC.coord2Point(coord));
    }

    private boolean isPtLink(Id<Link> linkId, Network network){
        return network.getLinks().get(linkId).getAllowedModes().equals(CollectionUtils.stringArrayToSet(new String[]{"pt"}));
    }
}
