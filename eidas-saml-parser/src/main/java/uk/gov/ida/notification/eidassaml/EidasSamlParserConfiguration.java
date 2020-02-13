package uk.gov.ida.notification.eidassaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import engineering.reliability.gds.metrics.config.PrometheusConfiguration;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;
import uk.gov.ida.notification.configuration.ReplayCheckerConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.net.URI;

public class EidasSamlParserConfiguration extends Configuration implements PrometheusConfiguration {

    @JsonProperty
    @Valid
    @NotNull
    private URI proxyNodeAuthnRequestUrl;

    @JsonProperty
    @Valid
    private final ReplayCheckerConfiguration replayChecker = new ReplayCheckerConfiguration();

    @Valid
    @NotNull
    @JsonProperty
    private final JerseyClientConfiguration clientConfig = new JerseyClientConfiguration();

    @Valid
    @NotNull
    @JsonProperty
    private URI metatronUri;

    public URI getMetatronUrl() {
        return metatronUri;
    }

    public JerseyClientConfiguration getClientConfig() {
        return clientConfig;
    }

    public ReplayCheckerConfiguration getReplayChecker() {
        return replayChecker;
    }

    public URI getProxyNodeAuthnRequestUrl() {
        return proxyNodeAuthnRequestUrl;
    }

    @Override
    public boolean isPrometheusEnabled() {
        return true;
    }

}
