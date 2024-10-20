package io.smallrye.stork.serviceregistration.eureka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import jakarta.inject.Inject;

import org.jboss.weld.junit5.ExplicitParamInjection;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.Metadata;
import io.smallrye.stork.api.ServiceRegistrar;
import io.smallrye.stork.impl.EurekaMetadataKey;
import io.smallrye.stork.test.StorkTestUtils;
import io.smallrye.stork.test.TestConfigProviderBean;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;

@Testcontainers
@DisabledOnOs(OS.WINDOWS)
@ExtendWith(WeldJunit5Extension.class)
@ExplicitParamInjection
public class EurekaRegistrationCDITest {

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(TestConfigProviderBean.class,
            EurekaServiceRegistrarProviderLoader.class);

    public static final int EUREKA_PORT = 8761;

    @Container
    public GenericContainer<?> eureka = new GenericContainer<>(DockerImageName.parse("quay.io/amunozhe/eureka-server:0.3"))
            .withExposedPorts(EUREKA_PORT);

    @Inject
    TestConfigProviderBean config;

    private static Vertx vertx = Vertx.vertx();;
    private WebClient client;

    public int port;
    public String host;

    @BeforeEach
    public void init() {
        client = WebClient.create(vertx, new WebClientOptions()
                .setDefaultHost(eureka.getHost())
                .setDefaultPort(eureka.getMappedPort(EUREKA_PORT)));
        port = eureka.getMappedPort(EUREKA_PORT);
        host = eureka.getHost();
    }

    @AfterEach
    public void cleanup() {
        client.close();
        config.clear();
    }

    @Test
    public void testRegistrationServiceInstances(TestInfo info) {
        String serviceName = "my-service";
        config.addServiceConfig(serviceName, null, null, "eureka", null,
                null,
                Map.of("eureka-host", eureka.getHost(), "eureka-port", String.valueOf(port)));

        Stork stork = StorkTestUtils.getNewStorkInstance();

        ServiceRegistrar<EurekaMetadataKey> eurekaServiceRegistrar = stork.getService(serviceName).getServiceRegistrar();

        CountDownLatch registrationLatch = new CountDownLatch(1);
        eurekaServiceRegistrar.registerServiceInstance(serviceName, Metadata.of(EurekaMetadataKey.class)
                .with(EurekaMetadataKey.META_EUREKA_SERVICE_ID, serviceName), "localhost", 8406).subscribe()
                .with(success -> registrationLatch.countDown(), failure -> fail(""));

        await().atMost(Duration.ofSeconds(10))
                .until(() -> registrationLatch.getCount() == 0L);

        Uni<HttpResponse<Buffer>> response = client.get("/eureka/apps/my-service")
                .putHeader("Accept", "application/json;charset=UTF-8").send();

        UniAssertSubscriber<HttpResponse<Buffer>> subscriber = response
                .subscribe().withSubscriber(UniAssertSubscriber.create());

        HttpResponse<Buffer> httpResponse = subscriber.awaitItem().getItem();
        assertThat(httpResponse).isNotNull();
        assertThat(httpResponse.statusCode()).isEqualTo(200);

        JsonObject jsonResponse = httpResponse.bodyAsJsonObject();
        JsonObject application = jsonResponse.getJsonObject("application");
        JsonObject jsonServiceInstance = application.getJsonArray("instance").getJsonObject(0);

        assertThat(jsonServiceInstance.getString("instanceId")).isEqualTo("my-service");
        assertThat(jsonServiceInstance.getString("ipAddr")).isEqualTo("localhost");
        assertThat(jsonServiceInstance.getJsonObject("port").getInteger("$")).isEqualTo(8406);

    }

}
