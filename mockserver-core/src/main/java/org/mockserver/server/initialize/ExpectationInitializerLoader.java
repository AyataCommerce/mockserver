package org.mockserver.server.initialize;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.file.FileReader;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause;
import org.mockserver.serialization.ExpectationSerializer;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.SERVER_CONFIGURATION;
import static org.slf4j.event.Level.*;

/**
 * @author jamesdbloom
 */
public class ExpectationInitializerLoader {

    private final ExpectationSerializer expectationSerializer;
    private final MockServerLogger mockServerLogger;
    private final RequestMatchers requestMatchers;

    public ExpectationInitializerLoader(MockServerLogger mockServerLogger, RequestMatchers requestMatchers) {
        this.expectationSerializer = new ExpectationSerializer(mockServerLogger);
        this.mockServerLogger = mockServerLogger;
        this.requestMatchers = requestMatchers;
        addExpectationsFromInitializer();
    }

    private void addExpectationsFromInitializer() {
        for (Expectation expectation : loadExpectations()) {
            requestMatchers.add(expectation, Cause.INITIALISER);
        }
    }

    private Expectation[] retrieveExpectationsFromInitializerClass() {
        try {
            String initializationClass = ConfigurationProperties.initializationClass();
            if (isNotBlank(initializationClass)) {
                if (MockServerLogger.isEnabled(INFO)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(SERVER_CONFIGURATION)
                            .setLogLevel(INFO)
                            .setMessageFormat("loading class initialization file:{}")
                            .setArguments(initializationClass)
                    );
                }
                ClassLoader contextClassLoader = ExpectationInitializerLoader.class.getClassLoader();
                if (contextClassLoader != null && isNotBlank(initializationClass)) {
                    Constructor<?> initializerClassConstructor = contextClassLoader.loadClass(initializationClass).getDeclaredConstructor();
                    Object expectationInitializer = initializerClassConstructor.newInstance();
                    if (expectationInitializer instanceof ExpectationInitializer) {
                        return ((ExpectationInitializer) expectationInitializer).initializeExpectations();
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new Expectation[0];
    }

    private Expectation[] retrieveExpectationsFromJson() {
        String initializationJsonPath = ConfigurationProperties.initializationJsonPath();
        String bulkInitializationJsonPath = ConfigurationProperties.bulkInitializationJsonPath();
        if (isNotBlank(bulkInitializationJsonPath)) {
            if (MockServerLogger.isEnabled(INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(SERVER_CONFIGURATION)
                        .setLogLevel(INFO)
                        .setMessageFormat("loading JSON initialization file:{}")
                        .setArguments(initializationJsonPath)
                );
            }

            String[] extensions = {"json"};
            Collection<File> jsonExpectationFiles = FileUtils.listFiles(new File(bulkInitializationJsonPath), extensions, true);
            if (jsonExpectationFiles == null || jsonExpectationFiles.isEmpty()) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(SERVER_CONFIGURATION)
                        .setLogLevel(ERROR)
                        .setMessageFormat("Directory for Bulk JSON PATH empty. {}}")
                        .setArguments(bulkInitializationJsonPath)
                );
                return new Expectation[0];
            }
            List<Expectation> expectationList = new ArrayList<>();
            for (File expectationFile : jsonExpectationFiles) {
                String jsonExpectations = FileReader.readFileFromClassPathOrPath(expectationFile.getPath());
                if (MockServerLogger.isEnabled(DEBUG)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(SERVER_CONFIGURATION)
                            .setLogLevel(DEBUG)
                            .setMessageFormat("Loaded JSON initialization file:{}content:{}")
                            .setArguments(expectationFile.getPath(), StringUtils.abbreviate(jsonExpectations, 1000))
                    );
                }
                if (isNotBlank(jsonExpectations)) {
                    expectationList.addAll(Arrays.asList(expectationSerializer.deserializeArray(jsonExpectations, true)));
                }
            }
            return expectationList.stream().toArray(n -> new Expectation[n]);

        } else if (isNotBlank(initializationJsonPath)) {
            if (MockServerLogger.isEnabled(INFO)) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(SERVER_CONFIGURATION)
                        .setLogLevel(INFO)
                        .setMessageFormat("loading JSON initialization file:{}")
                        .setArguments(initializationJsonPath)
                );
            }

            try {
                String jsonExpectations = FileReader.readFileFromClassPathOrPath(initializationJsonPath);
                if (MockServerLogger.isEnabled(DEBUG)) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setType(SERVER_CONFIGURATION)
                            .setLogLevel(DEBUG)
                            .setMessageFormat("loaded JSON initialization file:{}content:{}")
                            .setArguments(initializationJsonPath, StringUtils.abbreviate(jsonExpectations, 1000))
                    );
                }
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
                            .setMessageFormat("exception while loading JSON initialization file, ignoring file")
                            .setThrowable(throwable)
                    );
                }
            }
        }
        return new Expectation[0];
    }

    public Expectation[] loadExpectations() {
        final Expectation[] expectationsFromInitializerClass = retrieveExpectationsFromInitializerClass();
        final Expectation[] expectationsFromJson = retrieveExpectationsFromJson();
        return ArrayUtils.addAll(expectationsFromInitializerClass, expectationsFromJson);
    }
}
