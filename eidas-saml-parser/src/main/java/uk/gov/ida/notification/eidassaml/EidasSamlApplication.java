package uk.gov.ida.notification.eidassaml;

import engineering.reliability.gds.metrics.bundle.PrometheusBundle;
import io.dropwizard.Application;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import se.litsec.opensaml.saml2.common.response.MessageReplayChecker;
import uk.gov.ida.dropwizard.logstash.LogstashBundle;
import uk.gov.ida.jerseyclient.ErrorHandlingClient;
import uk.gov.ida.jerseyclient.JsonResponseProcessor;
import uk.gov.ida.notification.VerifySamlInitializer;
import uk.gov.ida.notification.eidassaml.saml.validation.EidasAuthnRequestValidator;
import uk.gov.ida.notification.eidassaml.saml.validation.components.AssertionConsumerServiceValidator;
import uk.gov.ida.notification.eidassaml.saml.validation.components.ComparisonValidator;
import uk.gov.ida.notification.eidassaml.saml.validation.components.RequestIssuerValidator;
import uk.gov.ida.notification.eidassaml.saml.validation.components.RequestedAttributesValidator;
import uk.gov.ida.notification.eidassaml.saml.validation.components.SpTypeValidator;
import uk.gov.ida.notification.exceptions.mappers.CatchAllExceptionMapper;
import uk.gov.ida.notification.exceptions.mappers.JsonErrorResponseRuntimeExceptionMapper;
import uk.gov.ida.notification.exceptions.mappers.JsonErrorResponseValidationExceptionMapper;
import uk.gov.ida.notification.exceptions.mappers.SamlTransformationErrorExceptionMapper;
import uk.gov.ida.notification.healthcheck.ProxyNodeHealthCheck;
import uk.gov.ida.notification.saml.deprecate.DestinationValidator;
import uk.gov.ida.notification.saml.validation.components.LoaValidator;
import uk.gov.ida.notification.shared.istio.IstioHeaderMapperFilter;
import uk.gov.ida.notification.shared.istio.IstioHeaderStorage;
import uk.gov.ida.notification.shared.logging.ProxyNodeLoggingFilter;
import uk.gov.ida.notification.shared.proxy.MetatronProxy;
import uk.gov.ida.notification.shared.proxy.ProxyNodeJsonClient;

import javax.ws.rs.client.Client;

public class EidasSamlApplication extends Application<EidasSamlParserConfiguration> {

    public static void main(final String[] args) throws Exception {
        if (args == null || args.length == 0) {
            String configFile = System.getenv("CONFIG_FILE");

            if (configFile == null) {
                throw new RuntimeException("CONFIG_FILE environment variable should be set with path to configuration file");
            }
            new EidasSamlApplication().run("server", configFile);
        } else {
            new EidasSamlApplication().run(args);
        }
    }

    public void initialize(final Bootstrap<EidasSamlParserConfiguration> bootstrap) {

        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                        new EnvironmentVariableSubstitutor(false)
                )
        );

        try {
            InitializationService.initialize();
        } catch (InitializationException e) {
            throw new RuntimeException(e);
        }

        VerifySamlInitializer.init();

        bootstrap.addBundle(new LogstashBundle());
        bootstrap.addBundle(new PrometheusBundle());
    }

    @Override
    public void run(EidasSamlParserConfiguration configuration, Environment environment) throws Exception {

        ProxyNodeHealthCheck proxyNodeHealthCheck = new ProxyNodeHealthCheck("parser");
        environment.healthChecks().register(proxyNodeHealthCheck.getName(), proxyNodeHealthCheck);
        MetatronProxy metatronProxy = createMetatronProxy(configuration, environment);
        EidasAuthnRequestValidator eidasAuthnRequestValidator = createEidasAuthnRequestValidator(configuration, metatronProxy);

        environment.jersey().register(IstioHeaderMapperFilter.class);
        environment.jersey().register(ProxyNodeLoggingFilter.class);

        environment.jersey().register(
                new EidasSamlResource(
                        eidasAuthnRequestValidator,
                        metatronProxy
                        )
        );
        registerExceptionMappers(environment);
        registerInjections(environment);
    }

    private void registerExceptionMappers(Environment environment) {
        environment.jersey().register(new SamlTransformationErrorExceptionMapper());
        environment.jersey().register(new JsonErrorResponseRuntimeExceptionMapper());
        environment.jersey().register(new JsonErrorResponseValidationExceptionMapper());
        environment.jersey().register(new CatchAllExceptionMapper());
    }

    private MetatronProxy createMetatronProxy(EidasSamlParserConfiguration configuration, Environment environment) {
        return this.buildMetatronProxy(configuration, environment);
    }

    private EidasAuthnRequestValidator createEidasAuthnRequestValidator(EidasSamlParserConfiguration configuration, MetatronProxy metatronProxy) throws Exception {
        MessageReplayChecker replayChecker = configuration.getReplayChecker().createMessageReplayChecker("eidas-saml-parser");
        DestinationValidator destinationValidator = new DestinationValidator(
                configuration.getProxyNodeAuthnRequestUrl(), configuration.getProxyNodeAuthnRequestUrl().getPath());

        return new EidasAuthnRequestValidator(
                new RequestIssuerValidator(),
                new SpTypeValidator(),
                new LoaValidator(),
                new RequestedAttributesValidator(),
                replayChecker,
                new ComparisonValidator(),
                destinationValidator,
                new AssertionConsumerServiceValidator(metatronProxy)
        );
    }

    private void registerInjections(Environment environment) {
        environment.jersey().register(new AbstractBinder() {
            @Override
            protected void configure() {
                bindAsContract(IstioHeaderStorage.class);
            }
        });
    }

    private MetatronProxy buildMetatronProxy(EidasSamlParserConfiguration configuration, Environment environment) {
        Client client = new JerseyClientBuilder(environment).using(environment).build("metatron-client");
        ProxyNodeJsonClient jsonClient = new ProxyNodeJsonClient(
                new ErrorHandlingClient(client),
                new JsonResponseProcessor(environment.getObjectMapper()),
                new IstioHeaderStorage()
        );
        return new MetatronProxy(jsonClient, configuration.getMetatronUrl());
    }
}
