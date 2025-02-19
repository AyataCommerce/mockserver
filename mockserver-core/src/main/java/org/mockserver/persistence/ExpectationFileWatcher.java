package org.mockserver.persistence;

import org.apache.commons.io.FileUtils;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerMatcherNotifier;
import org.mockserver.serialization.ExpectationSerializer;
import org.slf4j.event.Level;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.slf4j.event.Level.*;

public class ExpectationFileWatcher {

    private final ExpectationSerializer expectationSerializer;
    private final MockServerLogger mockServerLogger;
    private final RequestMatchers requestMatchers;
    private FileWatcher fileWatcher;

    public ExpectationFileWatcher(MockServerLogger mockServerLogger, RequestMatchers requestMatchers) {
        if (ConfigurationProperties.watchInitializationJson()) {
            this.expectationSerializer = new ExpectationSerializer(mockServerLogger);
            this.mockServerLogger = mockServerLogger;
            this.requestMatchers = requestMatchers;
            String filePath = isNotBlank(ConfigurationProperties.bulkInitializationJsonPath()) ? ConfigurationProperties.bulkInitializationJsonPath() : ConfigurationProperties.initializationJsonPath();
            try {
                fileWatcher = new FileWatcher(filePath, () -> {
                    if (MockServerLogger.isEnabled(DEBUG)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(DEBUG)
                                .setMessageFormat("expectation file watcher updating expectations as modification detected on file{}")
                                .setArguments(filePath)
                        );
                    }
                    addExpectationsFromInitializer();
                }, throwable -> {
                    if (MockServerLogger.isEnabled(WARN)) {
                        mockServerLogger.logEvent(
                            new LogEntry()
                                .setLogLevel(WARN)
                                .setMessageFormat("exception while processing expectation file update " + throwable.getMessage())
                                .setThrowable(throwable)
                        );
                    }
                });
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception creating file watcher for{}")
                        .setArguments(ConfigurationProperties.initializationJsonPath())
                        .setThrowable(throwable)
                );
            }
            if (MockServerLogger.isEnabled(INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(INFO)
                        .setMessageFormat("created expectation file watcher for{}")
                        .setArguments(ConfigurationProperties.initializationJsonPath())
                );
            }
        } else {
            this.expectationSerializer = null;
            this.mockServerLogger = null;
            this.requestMatchers = null;
        }
    }

    private void addExpectationsFromInitializer() {
        Expectation[] expectations = retrieveExpectationsFromJson();
        if (MockServerLogger.isEnabled(TRACE)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(TRACE)
                    .setMessageFormat("updating expectations{}from{}")
                    .setArguments(ConfigurationProperties.initializationJsonPath(), Arrays.asList(expectations))
            );
        }
        requestMatchers.update(expectations, MockServerMatcherNotifier.Cause.FILE_WATCHER);
    }

    private Expectation[] retrieveExpectationsFromJson() {
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        String bulkInitializationJsonPath = ConfigurationProperties.bulkInitializationJsonPath();
        if (isNotBlank(bulkInitializationJsonPath)) {
            String[] extensions = {"json"};
            Collection<File> jsonExpectationFiles = FileUtils.listFiles(new File(bulkInitializationJsonPath), extensions, true);
            List<Expectation> expectationList = new ArrayList<>();
            for (File expectationFile : jsonExpectationFiles) {
                String jsonExpectations = FileReader.readFileFromClassPathOrPath(expectationFile.getPath());
                if (isNotBlank(jsonExpectations)) {
                    expectationList.addAll(Arrays.asList(expectationSerializer.deserializeArray(jsonExpectations, true)));
                }
            }
            return expectationList.stream().toArray(n -> new Expectation[n]);
        } else if (isNotBlank(initializationJsonPath)) {
            try {
                String jsonExpectations = FileReader.readFileFromClassPathOrPath(initializationJsonPath);
                if (isNotBlank(jsonExpectations)) {
                    return expectationSerializer.deserializeArray(jsonExpectations, true);
                } else {
                    return new Expectation[0];
                }
            } catch (Throwable throwable) {
                if (MockServerLogger.isEnabled(WARN)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(SERVER_CONFIGURATION)
                            .setLogLevel(WARN)
                            .setMessageFormat("exception while loading JSON initialization file with file watcher, ignoring file")
                            .setThrowable(throwable)
                    );
                }
            }
        }
        return new Expectation[0];
    }

    public void stop() {
        if (fileWatcher != null) {
            fileWatcher.setRunning(false);
        }
    }
}
