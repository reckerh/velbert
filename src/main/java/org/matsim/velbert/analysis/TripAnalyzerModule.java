package org.matsim.velbert.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.AfterMobsimEvent;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.scoring.ExperiencedPlansService;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
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

                var modalSplit = filteredEntries.stream()
                        .map(Map.Entry::getValue)
                        .flatMap(Collection::stream)
                        .map(TripEventHandler.Trip::getMode)
                        .collect(Collectors.toMap(mode -> mode, mode -> 1, Integer::sum));

                var totalNumberOfTrips = modalSplit.values().stream()
                        .mapToInt(value -> value)
                        .sum();

                var filename = Paths.get(outputDirectoryHierarchy.getIterationFilename(event.getIteration(), "modal-share.csv"));

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
        }
    }
}
