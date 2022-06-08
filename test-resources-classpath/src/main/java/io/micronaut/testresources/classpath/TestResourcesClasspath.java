/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.testresources.classpath;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class which deduces the server classpath from the user classpath.
 * For example, the application may use Micronaut Data + Mysql, in which
 * case we need to put on the test resources classpath the Micronaut
 * test resources modules for JDBC and the MySQL driver.
 */
public final class TestResourcesClasspath {
    private static final String TEST_RESOURCES_GROUP = "io.micronaut.testresources";
    private static final String TEST_RESOURCES_ARTIFACT_PREFIX = "micronaut-test-resources-";

    private static final List<String> CORE_SUPPORT = Arrays.asList(
        "server",
        "testcontainers"
    );
    private static final String MICRONAUT_DATA_PREFIX = "micronaut-data-";
    private static final String MICRONAUT_KAFKA = "micronaut-kafka";
    private static final String MICRONAUT_MQTT = "micronaut-mqtt";

    private static final String MICRONAUT_NEO4J = "micronaut-neo4j";
    private static final String MICRONAUT_DATA_MONGODB = "micronaut-data-mongodb";
    private static final String MYSQL_CONNECTOR_JAVA = "mysql-connector-java";

    private static final String MYSQL_MYSQL_CONNECTOR_JAVA = "mysql:mysql-connector-java";
    private static final String POSTGRESQL = "org.postgresql:postgresql";
    private static final String MARIADB_JAVA_CLIENT = "org.mariadb.jdbc:mariadb-java-client";
    private static final String MONGODB_DRIVER_ASYNC = "org.mongodb:mongodb-driver-async";
    private static final String MONGODB_DRIVER_SYNC = "org.mongodb:mongodb-driver-sync";
    private static final String MONGODB_DRIVER_REACTIVESTREAMS = "org.mongodb:mongodb-driver-reactivestreams";
    private static final String KAFKA_MODULE = "kafka";
    private static final String HIVEMQ_MODULE = "hivemq";
    private static final String MONGODB_MODULE = "mongodb";
    private static final String MYSQL_MODULE = "jdbc-mysql";
    private static final String NEO4J_MODULE = "neo4j";
    private static final String POSTGRESQL_MODULE = "jdbc-postgresql";
    private static final String MARIADB_MODULE = "jdbc-mariadb";

    private TestResourcesClasspath() {

    }

    /**
     * Determines a list of dependencies which should be added to the test
     * resources classpath, given a user supplied classpath. In general the
     * result will consist of modules from the test-resources project, but
     * it may consist of additional entries, for example database drivers.
     *
     * @param input the user classpath
     * @return the inferred test resources classpath
     */
    public static List<MavenDependency> inferTestResourcesClasspath(List<MavenDependency> input) {
        return inferTestResourcesClasspath(input, VersionInfo.getVersion());
    }

    /**
     * Determines a list of dependencies which should be added to the test
     * resources classpath, given a user supplied classpath. In general the
     * result will consist of modules from the test-resources project, but
     * it may consist of additional entries, for example database drivers.
     *
     * @param input the user classpath
     * @param testResourcesVersion the version of the test resources libraries
     * @return the inferred test resources classpath
     */
    public static List<MavenDependency> inferTestResourcesClasspath(List<MavenDependency> input, String testResourcesVersion) {
        return Stream.concat(
            CORE_SUPPORT.stream().flatMap(m -> singleTestResourceModule(m, testResourcesVersion)),
            input.stream().flatMap(current -> inferSingle(current, input, testResourcesVersion))
        ).collect(Collectors.toList());
    }

    private static Stream<MavenDependency> inferSingle(MavenDependency input, List<MavenDependency> allDependencies, String testResourcesVersion) {
        return Matcher.match(input, allDependencies, testResourcesVersion, m -> {
            m.onArtifact(MICRONAUT_KAFKA, KAFKA_MODULE);
            m.onArtifact(MICRONAUT_MQTT, HIVEMQ_MODULE);
            m.onArtifact(MICRONAUT_DATA_MONGODB, MONGODB_MODULE);
            m.onArtifact(name -> name.startsWith(MICRONAUT_NEO4J), deps -> true, NEO4J_MODULE);
            m.onArtifact(name -> name.startsWith(MICRONAUT_DATA_PREFIX), deps -> deps.anyMatch(artifactEquals(MYSQL_CONNECTOR_JAVA)), MYSQL_MODULE);
            m.onArtifact(name -> name.startsWith(MICRONAUT_DATA_PREFIX), deps -> deps.anyMatch(moduleEquals(POSTGRESQL)), POSTGRESQL_MODULE);
            m.onArtifact(name -> name.startsWith(MICRONAUT_DATA_PREFIX), deps -> deps.anyMatch(moduleEquals(MARIADB_JAVA_CLIENT)), MARIADB_MODULE);
            m.passthroughModules(MYSQL_MYSQL_CONNECTOR_JAVA,
                POSTGRESQL,
                MARIADB_JAVA_CLIENT,
                MONGODB_DRIVER_ASYNC,
                MONGODB_DRIVER_SYNC,
                MONGODB_DRIVER_REACTIVESTREAMS
            );
        });
    }

    private static Predicate<MavenDependency> artifactEquals(String artifactId) {
        return d -> artifactId.equals(d.getArtifact());
    }

    private static Predicate<MavenDependency> moduleEquals(String module) {
        return d -> module.equals(d.getModule());
    }

    private static Stream<MavenDependency> singleTestResourceModule(String id, String testResourcesVersion) {
        return Stream.of(testResources(id, testResourcesVersion));
    }

    private static MavenDependency testResources(String id, String testResourcesVersion) {
        return new MavenDependency(
            TEST_RESOURCES_GROUP,
            TEST_RESOURCES_ARTIFACT_PREFIX + id,
            testResourcesVersion
        );
    }

    private static final class Matcher {
        private final MavenDependency input;
        private final List<MavenDependency> allDependencies;

        private final String testResourcesVersion;

        private Stream<MavenDependency> output = Stream.empty();

        private Matcher(MavenDependency input, List<MavenDependency> allDependencies, String testResourcesVersion) {
            this.input = input;
            this.allDependencies = allDependencies;
            this.testResourcesVersion = testResourcesVersion;
        }

        public Supplier<Stream<MavenDependency>> testResource(String id) {
            return () -> singleTestResourceModule(id, testResourcesVersion);
        }

        public void onArtifact(String name, String moduleId) {
            onArtifact(name, testResource(moduleId));
        }

        public void onArtifact(String name, Supplier<Stream<MavenDependency>> supplier) {
            if (input.getArtifact().equals(name)) {
                output = Stream.concat(output, supplier.get());
            }
        }

        public void onArtifact(Predicate<? super String> name, Predicate<Stream<MavenDependency>> allDependenciesPredicate, String moduleId) {
            onArtifact(name, allDependenciesPredicate, testResource(moduleId));
        }

        public void onArtifact(Predicate<? super String> name, Predicate<Stream<MavenDependency>> allDependenciesPredicate, Supplier<Stream<MavenDependency>> supplier) {
            if (name.test(input.getArtifact()) && allDependenciesPredicate.test(allDependencies.stream())) {
                output = Stream.concat(output, supplier.get());
            }
        }

        public void passthroughModules(String... artifactIds) {
            if (Arrays.stream(artifactIds).anyMatch(aid -> aid.equals(input.getModule()))) {
                output = Stream.concat(output, Stream.of(input));
            }
        }

        public static Stream<MavenDependency> match(MavenDependency input, List<MavenDependency> allDependencies, String testResourcesVersion, Consumer<? super Matcher> consumer) {
            Matcher matcher = new Matcher(input, allDependencies, testResourcesVersion);
            consumer.accept(matcher);
            return matcher.output.distinct();
        }
    }

}
