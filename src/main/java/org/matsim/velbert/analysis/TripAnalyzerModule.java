package org.matsim.velbert.analysis;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.core.utils.collections.Tuple;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TripAnalyzerModule extends AbstractModule {

    private static final Logger log = LogManager.getLogger(TripAnalyzerModule.class);

    private final Predicate<Id<Person>> filterPerson;

    public TripAnalyzerModule(Predicate<Id<Person>> filterPerson) {
        this.filterPerson = filterPerson;
    }

    @Override
    public void install() {
        var filter = new PersonFilter() {
            @Override
            public boolean filter(Id<Person> id) {
                return filterPerson.test(id);
            }
        };
        addControlerListenerBinding().to(MobsimHandler.class);
        bind(PersonFilter.class).toInstance(filter);
    }

    interface PersonFilter {
        boolean filter(Id<Person> id);
    }

    private static class MobsimHandler implements BeforeMobsimListener, AfterMobsimListener {

        @Inject
        private EventsManager eventsManager;

        @Inject
        private Network network;

        @Inject
        private OutputDirectoryHierarchy outputDirectoryHierarchy;

        @Inject
        private PersonFilter filter;

        private TripEventHandler handler;

        @Override
        public void notifyBeforeMobsim(BeforeMobsimEvent event) {

            if (event.isLastIteration()) {
                this.handler = new TripEventHandler(network);
                eventsManager.addHandler(this.handler);
            }
        }

        @Override
        public void notifyAfterMobsim(AfterMobsimEvent event) {

            if (event.isLastIteration()) {
                var tripsByPerson = handler.getTripsByPerson();
                var filteredEntries = tripsByPerson.entrySet().stream()
                        .filter(entry -> filter.filter(entry.getKey()))
                        .collect(Collectors.toSet());


                modalShare(filteredEntries, Paths.get(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "modal-share.csv")));
                modalDistanceShare(filteredEntries, Paths.get(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "modal-distance-share.csv")));
            }
        }

        private void modalShare(Set<Map.Entry<Id<Person>, List<TripEventHandler.Trip>>> filteredEntries, Path filename) {

            var modalSplit = filteredEntries.stream()
                    .map(Map.Entry::getValue)
                    .flatMap(Collection::stream)
                    .filter(trip -> trip.getStartCoord() != null && trip.getEndCoord() != null)
                    .map(TripEventHandler.Trip::getMode)
                    .collect(Collectors.toMap(mode -> mode, mode -> 1, Integer::sum));

            var totalNumberOfTrips = modalSplit.values().stream()
                    .mapToInt(value -> value)
                    .sum();

            try (var writer = Files.newBufferedWriter(filename); var printer = CSVFormat.DEFAULT.withDelimiter(';').withHeader("mode", "count", "share").print(writer)) {

                log.info("-------------------------------------------------------------------- Trip Analyzer Module -----------------------------------------------------------------------");
                log.info("Total number of trips analyzed: " + totalNumberOfTrips + " conducted by " + filteredEntries.size());

                for (var entry : modalSplit.entrySet()) {

                    double share = (double)entry.getValue() / totalNumberOfTrips;
                    log.info(entry.getKey() + ": " + entry.getValue() + " (" + share * 100 + "%)");

                    printer.printRecord(entry.getKey(), entry.getValue(), share);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void modalDistanceShare(Set<Map.Entry<Id<Person>, List<TripEventHandler.Trip>>> filteredEntries, Path filename) {

            var distancesByMode = filteredEntries.stream()
                    .map(Map.Entry::getValue)
                    .flatMap(Collection::stream)
                    .filter(trip -> trip.getStartCoord() != null && trip.getEndCoord() != null)
                    .map(trip -> Tuple.of(trip.getMode(), getDistanceKey(trip.getDistance())))
                    .collect(Collectors.groupingBy(Tuple::getFirst, Collectors.toMap(Tuple::getSecond, t -> 1, Integer::sum, Object2IntOpenHashMap::new)));

            var numberOfTripsPerDistanceClass = filteredEntries.stream()
                    .map(Map.Entry::getValue)
                    .flatMap(Collection::stream)
                    .filter(trip -> trip.getStartCoord() != null && trip.getEndCoord() != null)
                    .map(trip -> getDistanceKey(trip.getDistance()))
                    .collect(Collectors.toMap(distance -> distance, distance -> 1, Integer::sum, Object2IntOpenHashMap::new));

            // we want our table to always look the same
            var distanceClasses = List.of("<1", "1 to 3", "3 to 5", "5 to 10", ">10");
            var modes = List.of(TransportMode.car, TransportMode.ride, TransportMode.pt, TransportMode.bike, TransportMode.walk);


            log.info("-------------------------------------------------------------------- Trip Analyzer Module -----------------------------------------------------------------------");

            try (var writer = Files.newBufferedWriter(filename); var printer = CSVFormat.DEFAULT.withDelimiter(';').withHeader("distance", "mode", "value", "shareOfDistance").print(writer)) {

                //print values
                for(var mode : modes) {

                    // get the distanceClasses for mode
                    var distances = distancesByMode.get(mode);

                    for (var distanceClass : distanceClasses) {

                        var totalNumberForDistance = numberOfTripsPerDistanceClass.getInt(distanceClass);
                        var distanceAndModeValue = distances.getInt(distanceClass);
                        var share = (double)distanceAndModeValue/totalNumberForDistance;
                        log.info(mode + ", " + distanceClass + ": " + distanceAndModeValue + ", " + totalNumberForDistance + ", " + share);

                        printer.printRecord(distanceClass, mode, distanceAndModeValue, share);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        private String getDistanceKey(double distance) {
            if (distance < 1000) return "<1";
            if (distance < 3000) return "1 to 3";
            if (distance < 5000) return "3 to 5";
            if (distance < 10000) return "5 to 10";
            return ">10";
        }
    }
}
