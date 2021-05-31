package org.matsim.velbert.analysis;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.*;

public class TripEventHandler implements ActivityEndEventHandler, ActivityStartEventHandler, PersonDepartureEventHandler, TransitDriverStartsEventHandler {

    private final Set<Id<Person>> transitDrivers = new HashSet<>();
    private final Map<Id<Person>, List<Trip>> tripsByPerson = new HashMap<>();

    private final Network network;

    public Map<Id<Person>, List<Trip>> getTripsByPerson() {
        return tripsByPerson;
    }

    public TripEventHandler(Network network) {
        this.network = network;
    }

    @Override
    public void handleEvent(ActivityEndEvent event) {

        if (isInteraction(event.getActType()) || isTransitDriver(event.getPersonId())) return;

        var trip = new Trip();
        trip.startCoord = getStartCoord(event);
        tripsByPerson.computeIfAbsent(event.getPersonId(), id -> new ArrayList<>()).add(trip);
    }

    @Override
    public void handleEvent(ActivityStartEvent event) {

        if (isInteraction(event.getActType()) || isTransitDriver(event.getPersonId())) return;

        var currentTrip = getCurrentTrip(event.getPersonId());
        currentTrip.endCoord = getEndCoord(event);
    }

    @Override
    public void handleEvent(PersonDepartureEvent event) {

        if (isTransitDriver(event.getPersonId())) return;

        var currentTrip = getCurrentTrip(event.getPersonId());
        currentTrip.mode = getMainMode(currentTrip.mode, event.getLegMode());

    }

    @Override
    public void handleEvent(TransitDriverStartsEvent event) {
        transitDrivers.add(event.getDriverId());
    }

    private boolean isInteraction(String actType) {
        return actType.endsWith(" interaction");
    }

    private boolean isTransitDriver(Id<Person> id) {
        return transitDrivers.contains(id);
    }

    private Coord getStartCoord(ActivityEndEvent event) {
        return event.getCoord() == null ? network.getLinks().get(event.getLinkId()).getCoord() : event.getCoord();
    }

    private Coord getEndCoord(ActivityStartEvent event) {
        return event.getCoord() == null ? network.getLinks().get(event.getLinkId()).getCoord() : event.getCoord();
    }

    private Trip getCurrentTrip(Id<Person> id) {
        var trips = tripsByPerson.get(id);
        return trips.get(trips.size() - 1);
    }

    private String getMainMode(String mode1, String mode2) {

        return getRank(mode1) > getRank(mode2) ? mode1 : mode2;
    }

    private int getRank(String mode) {
        if (TransportMode.walk.equals(mode)) return 0;
        if (TransportMode.bike.equals(mode)) return 1;
        if (TransportMode.ride.equals(mode)) return 2;
        if (TransportMode.car.equals(mode)) return 3;
        if (TransportMode.pt.equals(mode)) return 4;
        return -1; // can't tell
    }

    public static class Trip {

        private Coord startCoord;
        private Coord endCoord;
        private String mode;

        public double getDistance() {
            return CoordUtils.calcEuclideanDistance(startCoord, endCoord);
        }

        public String getMode() {
            return mode;
        }
    }
}
