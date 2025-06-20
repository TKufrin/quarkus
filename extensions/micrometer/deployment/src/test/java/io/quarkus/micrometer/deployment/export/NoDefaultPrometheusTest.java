package io.quarkus.micrometer.deployment.export;

import java.util.Set;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.quarkus.micrometer.test.Util;
import io.quarkus.test.QuarkusUnitTest;

public class NoDefaultPrometheusTest {
    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setFlatClassPath(true)
            .withConfigurationResource("test-logging.properties")
            .overrideConfigKey("quarkus.micrometer.binder-enabled-default", "false")
            .overrideConfigKey("quarkus.micrometer.binder.jvm", "true")
            .overrideConfigKey("quarkus.micrometer.export.prometheus.enabled", "true")
            .overrideConfigKey("quarkus.micrometer.export.prometheus.default-registry", "false")
            .overrideConfigKey("quarkus.micrometer.registry-enabled-default", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .withApplicationRoot((jar) -> jar
                    .addClasses(Util.class,
                            PrometheusRegistryProcessor.REGISTRY_CLASS,
                            SecondPrometheusProvider.class));

    @Inject
    MeterRegistry registry;

    @Inject
    PrometheusMeterRegistry promRegistry;

    @Test
    public void testMeterRegistryPresent() {
        // Prometheus is enabled (only registry)
        Assertions.assertNotNull(registry, "A registry should be configured");
        Set<MeterRegistry> subRegistries = ((CompositeMeterRegistry) registry).getRegistries();
        Assertions.assertEquals(1, subRegistries.size(),
                "There should be only one configured subregistry. Found " + subRegistries);

        PrometheusMeterRegistry subPromRegistry = (PrometheusMeterRegistry) subRegistries.iterator().next();
        Assertions.assertEquals(PrometheusMeterRegistry.class, subPromRegistry.getClass(),
                "Should be PrometheusMeterRegistry");
        Assertions.assertEquals(subPromRegistry, promRegistry,
                "Should be the same bean as the PrometheusMeterRegistry. Found " + subRegistries);

        Assertions.assertNotNull(registry.find("jvm.info").counter(),
                "JVM Info counter should be present, found: " + registry.getMeters());

        String result = promRegistry.scrape();
        Assertions.assertTrue(result.contains("customKey=\"customValue\""),
                "Scrape result should contain common tags from the custom registry configuration. Found\n" + result);

    }
}
