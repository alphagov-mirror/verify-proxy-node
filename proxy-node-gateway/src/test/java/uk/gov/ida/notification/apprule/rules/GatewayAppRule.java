package uk.gov.ida.notification.apprule.rules;

import com.nimbusds.jose.JOSEException;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.glassfish.jersey.client.ClientProperties;
import uk.gov.ida.notification.GatewayApplication;
import uk.gov.ida.notification.GatewayConfiguration;
import uk.gov.ida.notification.session.JWKSetGenerator;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;

public class GatewayAppRule extends DropwizardAppRule<GatewayConfiguration> {

    public static final String ERROR_PAGE_REDIRECT_URL = "https://proxy-node-error-page";

    private Client client;
    private Client noRedirectClient;

    public GatewayAppRule(ConfigOverride... configOverrides) {
        super(
                GatewayApplication.class,
                resourceFilePath("config.yml"),
                getConfigOverrides(configOverrides)
        );
    }

    private static ConfigOverride[] getConfigOverrides(ConfigOverride... configOverrides) {
        List<ConfigOverride> configOverridesList = new ArrayList<>(Arrays.asList(configOverrides));
        configOverridesList.add(ConfigOverride.config("server.applicationConnectors[0].port", "0"));
        configOverridesList.add(ConfigOverride.config("server.adminConnectors[0].port", "0"));
        configOverridesList.add(ConfigOverride.config("server.adminConnectors[0].port", "0"));
        configOverridesList.add(ConfigOverride.config("logging.appenders[0].type", "console"));
        configOverridesList.add(ConfigOverride.config("errorPageRedirectUrl", ERROR_PAGE_REDIRECT_URL));
        configOverridesList.add(ConfigOverride.config("sessionCookieConfiguration.jwkSetConfiguration.jwkSet", createJWKSet()));
        configOverridesList.add(ConfigOverride.config("sessionCookieConfiguration.domain", "apprule-test-proxy-node.london.verify.govsvc.uk"));
        return configOverridesList.toArray(new ConfigOverride[0]);
    }

    public WebTarget target(String path) throws URISyntaxException {
        return target(path, getLocalPort());
    }

    public WebTarget target(String path, int port) throws URISyntaxException {
        if (client == null) {
            client = buildClient();
        }

        return target(client, path, port);
    }

    public WebTarget target(String path, boolean followRedirects) throws URISyntaxException {
        if (!followRedirects) {
            return target(path);
        }

        if (noRedirectClient == null) {
            noRedirectClient = buildClient(false, "test client - no redirects");
        }

        return target(noRedirectClient, path, getLocalPort());
    }

    private WebTarget target(Client client, String path, int port) throws URISyntaxException {
        return client.target(new URI("http://localhost:" + port).resolve(path));
    }

    private Client buildClient() {
        return buildClient(true, "test client");
    }

    private Client buildClient(boolean followRedirects, String clientName) {
        final Client client = new JerseyClientBuilder(getEnvironment())
                .withProperty(ClientProperties.CONNECT_TIMEOUT, 10000)
                .withProperty(ClientProperties.READ_TIMEOUT, 10000)
                .build(clientName);

        if (followRedirects) {
            client.property(ClientProperties.FOLLOW_REDIRECTS, false);
        }

        return client;
    }

    private static String createJWKSet() {
        try {
            return JWKSetGenerator.createJWKSet();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

}
