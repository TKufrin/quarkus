package io.quarkus.it.micrometer.prometheus;

import static io.quarkus.micrometer.runtime.export.handlers.PrometheusHandler.CONTENT_TYPE_004;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

/**
 * Test functioning prometheus endpoint.
 * Use test execution order to ensure one http server request measurement
 * is present when the endpoint is scraped.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrometheusMetricsRegistryTest {

    @Test
    @Order(1)
    void testRegistryInjection() {
        when().get("/message").then().statusCode(200)
                .body(containsString("io.micrometer.core.instrument.composite.CompositeMeterRegistry"));
    }

    @Test
    @Order(2)
    void testUnknownUrl() {
        when().get("/message/notfound").then().statusCode(404);
    }

    @Test
    @Order(3)
    void testServerError() {
        when().get("/message/fail").then().statusCode(500);
    }

    @Test
    @Order(4)
    void testPathParameter() {
        given().header("foo", "bar").when().get("/message/item/123").then().statusCode(200);
    }

    @Test
    @Order(5)
    void testMultipleParameters() {
        when().get("/message/match/123/1").then().statusCode(200);

        when().get("/message/match/1/123").then().statusCode(200);

        when().get("/message/match/baloney").then().statusCode(200);
    }

    @Test
    @Order(6)
    void testPanacheCalls() {
        when().get("/fruit/create").then().statusCode(204);

        when().get("/fruit/all").then().statusCode(204);
    }

    @Test
    @Order(7)
    void testPrimeEndpointCalls() {
        when().get("/prime/7").then().statusCode(200)
                .body(containsString("is prime"));
    }

    @Test
    @Order(8)
    void testAllTheThings() {
        when().get("/all-the-things").then().statusCode(200)
                .body(containsString("OK"));
    }

    @Test
    @Order(9)
    void testTemplatedPathOnClass() {
        when().get("/template/path/anything").then().statusCode(200)
                .body(containsString("Received: anything"));
    }

    @Test
    @Order(10)
    void testSecuredEndpoint() {
        when().get("/secured/item/123").then().statusCode(401);
        given().auth().preemptive().basic("foo", "bar").when().get("/secured/item/321").then().statusCode(401);
        given().auth().preemptive().basic("scott", "reader").when().get("/secured/item/123").then().statusCode(200);
        given().auth().preemptive().basic("stuart", "writer").when().get("/secured/item/321").then().statusCode(200);
    }

    @Test
    @Order(11)
    void testTemplatedPathOnSubResource() {
        when().get("/root/r1/sub/s2").then().statusCode(200)
                .body(containsString("r1:s2"));
    }

    @Test
    @Order(20)
    void testPrometheusScrapeEndpointTextPlain() {
        RestAssured.given().header("Accept", CONTENT_TYPE_004)
                .when().get("/q/metrics")
                .then().statusCode(200)

                // Prometheus body has ALL THE THINGS in no particular order

                .body(containsString("registry=\"prometheus\""))
                .body(containsString("env=\"test\""))
                .body(containsString("http_server_requests"))

                .body(containsString("status=\"404\""))
                .body(containsString("uri=\"NOT_FOUND\""))
                .body(containsString("outcome=\"CLIENT_ERROR\""))

                .body(containsString("status=\"500\""))
                .body(containsString("uri=\"/message/fail\""))
                .body(containsString("outcome=\"SERVER_ERROR\""))

                .body(containsString("status=\"200\""))
                .body(containsString("uri=\"/message\""))
                .body(containsString("uri=\"/message/item/{id}\""))
                .body(containsString("status=\"200\",uri=\"/message/item/{id}\""))
                .body(containsString("uri=\"/secured/item/{id}\""))
                .body(containsString("status=\"200\",uri=\"/secured/item/{id}\""))
                .body(containsString("status=\"401\",uri=\"/secured/item/{id}\""))
                .body(containsString("outcome=\"SUCCESS\""))
                .body(containsString("dummy="))
                .body(containsString("foo=\"bar\""))
                .body(containsString("foo_response=\"value\""))
                .body(containsString("uri=\"/message/match/{id}/{sub}\""))
                .body(containsString("uri=\"/message/match/{other}\""))

                .body(containsString(
                        "http_server_requests_seconds_count{dummy=\"val-anything\",env=\"test\",env2=\"test\",foo=\"UNSET\",foo_response=\"UNSET\",method=\"GET\",outcome=\"SUCCESS\",registry=\"prometheus\",status=\"200\",uri=\"/template/path/{value}\""))

                .body(containsString(
                        "http_server_requests_seconds_count{dummy=\"value\",env=\"test\",env2=\"test\",foo=\"UNSET\",foo_response=\"UNSET\",method=\"GET\",outcome=\"SUCCESS\",registry=\"prometheus\",status=\"200\",uri=\"/root/{rootParam}/sub/{subParam}\""))

                // Verify Hibernate Metrics
                .body(containsString(
                        "hibernate_sessions_open_total{entityManagerFactory=\"<default>\",env=\"test\",env2=\"test\",registry=\"prometheus\"} 2.0"))
                .body(containsString(
                        "hibernate_sessions_closed_total{entityManagerFactory=\"<default>\",env=\"test\",env2=\"test\",registry=\"prometheus\"} 2.0"))
                .body(containsString(
                        "hibernate_connections_obtained_total{entityManagerFactory=\"<default>\",env=\"test\",env2=\"test\",registry=\"prometheus\"}"))
                .body(containsString(
                        "hibernate_entities_inserts_total{entityManagerFactory=\"<default>\",env=\"test\",env2=\"test\",registry=\"prometheus\"} 3.0"))
                .body(containsString(
                        "hibernate_flushes_total{entityManagerFactory=\"<default>\",env=\"test\",env2=\"test\",registry=\"prometheus\"} 1.0"))

                // Annotated counters
                .body(not(containsString("metric_none")))
                .body(containsString(
                        "metric_all_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"none\",extra=\"tag\",fail=\"false\",method=\"countAllInvocations\",registry=\"prometheus\",result=\"success\"} 1.0"))
                .body(containsString(
                        "metric_all_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"NullPointerException\",extra=\"tag\",fail=\"true\",method=\"countAllInvocations\",registry=\"prometheus\",result=\"failure\"} 1.0"))
                .body(containsString(
                        "method_counted_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"NullPointerException\",fail=\"prefix true\",method=\"emptyMetricName\",registry=\"prometheus\",result=\"failure\"} 1.0"))
                .body(containsString(
                        "method_counted_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"none\",fail=\"prefix false\",method=\"emptyMetricName\",registry=\"prometheus\",result=\"success\"} 1.0"))
                .body(not(containsString("async_none")))
                .body(containsString(
                        "async_all_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",do_fail_call=\"true\",env=\"test\",env2=\"test\",exception=\"NullPointerException\",extra=\"tag\",method=\"countAllAsyncInvocations\",registry=\"prometheus\",result=\"failure\"} 1.0"))
                .body(containsString(
                        "async_all_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",do_fail_call=\"false\",env=\"test\",env2=\"test\",exception=\"none\",extra=\"tag\",method=\"countAllAsyncInvocations\",registry=\"prometheus\",result=\"success\"} 1.0"))
                .body(containsString(
                        "method_counted_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"NullPointerException\",fail=\"42\",method=\"emptyAsyncMetricName\",registry=\"prometheus\",result=\"failure\"} 1.0"))
                .body(containsString(
                        "method_counted_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"none\",fail=\"42\",method=\"emptyAsyncMetricName\",registry=\"prometheus\",result=\"success\"} 1.0"))

                // Annotated Timers
                .body(containsString(
                        "call_seconds_count{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"NullPointerException\",extra=\"tag\",method=\"call\",registry=\"prometheus\"} 1"))
                .body(containsString(
                        "call_seconds_count{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"none\",extra=\"tag\",method=\"call\",registry=\"prometheus\"}"))
                .body(containsString(
                        "async_call_seconds_count{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"NullPointerException\",extra=\"tag\",method=\"asyncCall\",registry=\"prometheus\"} 1"))
                .body(containsString(
                        "async_call_seconds_count{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"none\",extra=\"tag\",method=\"asyncCall\",registry=\"prometheus\"} 1"))
                .body(containsString(
                        "longCall_seconds_count{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",extra=\"tag\",method=\"longCall\",registry=\"prometheus\"}"))
                .body(containsString(
                        "async_longCall_seconds_sum{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",extra=\"tag\",method=\"longAsyncCall\",registry=\"prometheus\"} 0.0"))

                // Configured median, 95th percentile and histogram buckets
                .body(containsString(
                        "prime_number_test_seconds{env=\"test\",env2=\"test\",registry=\"prometheus\",quantile=\"0.5\"}"))
                .body(containsString(
                        "prime_number_test_seconds{env=\"test\",env2=\"test\",registry=\"prometheus\",quantile=\"0.95\"}"))

                // this was defined by a tag to a non-matching registry, and should not be found
                .body(not(containsString("class-should-not-match")))

                // should not find this ignored uri
                .body(not(containsString("uri=\"/fruit/create\"")));
    }

    @Test
    @Order(20)
    void testPrometheusScrapeEndpointOpenMetricsCompatibility() {
        RestAssured.given().header("Accept", "application/openmetrics-text; version=1.0.0; charset=utf-8")
                .when().get("/q/metrics")
                .then().statusCode(200)

                // Prometheus body has ALL THE THINGS in no particular order

                .body(containsString("registry=\"prometheus\""))
                .body(containsString("env=\"test\""))
                .body(containsString("http_server_requests"))

                .body(containsString("status=\"404\""))
                .body(containsString("uri=\"NOT_FOUND\""))
                .body(containsString("outcome=\"CLIENT_ERROR\""))

                .body(containsString("status=\"500\""))
                .body(containsString("uri=\"/message/fail\""))
                .body(containsString("outcome=\"SERVER_ERROR\""))

                .body(containsString("status=\"200\""))
                .body(containsString("uri=\"/message\""))
                .body(containsString("uri=\"/message/item/{id}\""))
                .body(containsString("outcome=\"SUCCESS\""))
                .body(containsString("uri=\"/message/match/{id}/{sub}\""))
                .body(containsString("uri=\"/message/match/{other}\""))

                .body(containsString(
                        "http_server_requests_seconds_count{dummy=\"val-anything\",env=\"test\",env2=\"test\",foo=\"UNSET\",foo_response=\"UNSET\",method=\"GET\",outcome=\"SUCCESS\",registry=\"prometheus\",status=\"200\",uri=\"/template/path/{value}\""))

                // Verify Hibernate Metrics
                .body(containsString(
                        "hibernate_sessions_open_total{entityManagerFactory=\"<default>\",env=\"test\",env2=\"test\",registry=\"prometheus\"} 2.0"))
                .body(containsString(
                        "hibernate_sessions_closed_total{entityManagerFactory=\"<default>\",env=\"test\",env2=\"test\",registry=\"prometheus\"} 2.0"))
                .body(containsString(
                        "hibernate_connections_obtained_total{entityManagerFactory=\"<default>\",env=\"test\",env2=\"test\",registry=\"prometheus\"}"))
                .body(containsString(
                        "hibernate_entities_inserts_total{entityManagerFactory=\"<default>\",env=\"test\",env2=\"test\",registry=\"prometheus\"} 3.0"))
                .body(containsString(
                        "hibernate_flushes_total{entityManagerFactory=\"<default>\",env=\"test\",env2=\"test\",registry=\"prometheus\"} 1.0"))

                // Annotated counters
                .body(not(containsString("metric_none")))
                .body(containsString(
                        "metric_all_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"none\",extra=\"tag\",fail=\"false\",method=\"countAllInvocations\",registry=\"prometheus\",result=\"success\"} 1.0 # {span_id="))
                .body(containsString(
                        "metric_all_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"NullPointerException\",extra=\"tag\",fail=\"true\",method=\"countAllInvocations\",registry=\"prometheus\",result=\"failure\"} 1.0"))
                .body(containsString(
                        "method_counted_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"NullPointerException\",fail=\"prefix true\",method=\"emptyMetricName\",registry=\"prometheus\",result=\"failure\"} 1.0 # {span_id="))
                .body(containsString(
                        "method_counted_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"none\",fail=\"prefix false\",method=\"emptyMetricName\",registry=\"prometheus\",result=\"success\"} 1.0"))
                .body(not(containsString("async_none")))
                .body(containsString(
                        "async_all_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",do_fail_call=\"true\",env=\"test\",env2=\"test\",exception=\"NullPointerException\",extra=\"tag\",method=\"countAllAsyncInvocations\",registry=\"prometheus\",result=\"failure\"} 1.0"))
                .body(containsString(
                        "async_all_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",do_fail_call=\"false\",env=\"test\",env2=\"test\",exception=\"none\",extra=\"tag\",method=\"countAllAsyncInvocations\",registry=\"prometheus\",result=\"success\"} 1.0"))
                .body(containsString(
                        "method_counted_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"NullPointerException\",fail=\"42\",method=\"emptyAsyncMetricName\",registry=\"prometheus\",result=\"failure\"} 1.0"))
                .body(containsString(
                        "method_counted_total{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"none\",fail=\"42\",method=\"emptyAsyncMetricName\",registry=\"prometheus\",result=\"success\"} 1.0"))

                // Annotated Timers
                .body(containsString(
                        "call_seconds_count{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"NullPointerException\",extra=\"tag\",method=\"call\",registry=\"prometheus\"} 1"))
                .body(containsString(
                        "call_seconds_count{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"none\",extra=\"tag\",method=\"call\",registry=\"prometheus\"}"))
                .body(containsString(
                        "async_call_seconds_count{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"NullPointerException\",extra=\"tag\",method=\"asyncCall\",registry=\"prometheus\"} 1"))
                .body(containsString(
                        "async_call_seconds_count{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",exception=\"none\",extra=\"tag\",method=\"asyncCall\",registry=\"prometheus\"} 1"))
                .body(containsString(
                        "longCall_seconds_count{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",extra=\"tag\",method=\"longCall\",registry=\"prometheus\"}"))
                .body(containsString(
                        "async_longCall_seconds_sum{class=\"io.quarkus.it.micrometer.prometheus.AnnotatedResource\",env=\"test\",env2=\"test\",extra=\"tag\",method=\"longAsyncCall\",registry=\"prometheus\"} 0.0"))

                // Configured median, 95th percentile and histogram buckets
                .body(containsString(
                        "prime_number_test_seconds{env=\"test\",env2=\"test\",registry=\"prometheus\",quantile=\"0.5\"}"))
                .body(containsString(
                        "prime_number_test_seconds{env=\"test\",env2=\"test\",registry=\"prometheus\",quantile=\"0.95\"}"))

                // this was defined by a tag to a non-matching registry, and should not be found
                .body(not(containsString("class-should-not-match")))

                // should not find this ignored uri
                .body(not(containsString("uri=\"/fruit/create\"")));
    }
}
