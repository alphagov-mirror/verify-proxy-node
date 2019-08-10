package uk.gov.ida.notification.apprule;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.dropwizard.testing.ConfigOverride;
import io.dropwizard.testing.junit.DropwizardClientRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import uk.gov.ida.notification.apprule.base.GatewayAppRuleTestBase;
import uk.gov.ida.notification.apprule.rules.GatewayAppRule;
import uk.gov.ida.notification.apprule.rules.RedisTestRule;
import uk.gov.ida.notification.apprule.rules.TestEidasSamlResource;
import uk.gov.ida.notification.apprule.rules.TestTranslatorClientErrorResource;
import uk.gov.ida.notification.apprule.rules.TestTranslatorResource;
import uk.gov.ida.notification.apprule.rules.TestTranslatorServerErrorResource;
import uk.gov.ida.notification.apprule.rules.TestVerifyServiceProviderResource;
import uk.gov.ida.notification.contracts.HubResponseTranslatorRequest;
import uk.gov.ida.notification.helpers.HtmlHelpers;
import uk.gov.ida.notification.helpers.ValidationTestDataUtils;
import uk.gov.ida.notification.saml.SamlFormMessageType;
import uk.gov.ida.notification.shared.Urls;
import uk.gov.ida.notification.shared.logging.ProxyNodeLogger;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static uk.gov.ida.notification.apprule.rules.TestTranslatorClientErrorResource.SAML_ERROR_BLOB;
import static uk.gov.ida.notification.helpers.ValidationTestDataUtils.sample_destinationUrl;
import static uk.gov.ida.notification.shared.logging.ProxyNodeLoggingFilter.MESSAGE_EGRESS;
import static uk.gov.ida.notification.shared.logging.ProxyNodeLoggingFilter.MESSAGE_INGRESS;
import static uk.gov.ida.saml.core.test.TestCertificateStrings.STUB_COUNTRY_PUBLIC_PRIMARY_CERT;

@RunWith(MockitoJUnitRunner.class)
public class HubResponseAppRuleTests extends GatewayAppRuleTestBase {

    private static final int EMBEDDED_REDIS_PORT = 6380;

    private static final String errorPageRedirectUrl = "https://proxy-node-error-page";
    private static final TestTranslatorResource testTranslatorResource = new TestTranslatorResource();

    @ClassRule
    public static final DropwizardClientRule translatorClientRule = new DropwizardClientRule(testTranslatorResource);

    @ClassRule
    public static final DropwizardClientRule translatorClientServerErrorRule = new DropwizardClientRule(new TestTranslatorServerErrorResource());

    @ClassRule
    public static final DropwizardClientRule translatorClientClientErrorRule = new DropwizardClientRule(new TestTranslatorClientErrorResource());

    @ClassRule
    public static final DropwizardClientRule espClientRule = new DropwizardClientRule(new TestEidasSamlResource());

    @ClassRule
    public static final DropwizardClientRule vspClientRule = new DropwizardClientRule(new TestVerifyServiceProviderResource());

    @ClassRule
    public static final RedisTestRule embeddedRedis = new RedisTestRule(EMBEDDED_REDIS_PORT);

    @Mock
    Appender<ILoggingEvent> appender;

    @Captor
    ArgumentCaptor<ILoggingEvent> loggingEventArgumentCaptor;

    private final String redisMockURI = this.setupTestRedis();

    @Rule
    public GatewayAppRule proxyNodeAppRule = new GatewayAppRule(
            ConfigOverride.config("eidasSamlParserService.url", espClientRule.baseUri().toString()),
            ConfigOverride.config("verifyServiceProviderService.url", vspClientRule.baseUri().toString()),
            ConfigOverride.config("translatorService.url", translatorClientRule.baseUri().toString()),
            ConfigOverride.config("redisService.url", redisMockURI),
            ConfigOverride.config("errorPageRedirectUrl", errorPageRedirectUrl),
            ConfigOverride.config("metadataPublishingConfiguration.metadataFilePath", ""),
            ConfigOverride.config("metadataPublishingConfiguration.metadataPublishPath", "")
    );

    @Rule
    public GatewayAppRule proxyNodeAppRuleNoErrorPageUrl = new GatewayAppRule(
            ConfigOverride.config("eidasSamlParserService.url", espClientRule.baseUri().toString()),
            ConfigOverride.config("verifyServiceProviderService.url", vspClientRule.baseUri().toString()),
            ConfigOverride.config("translatorService.url", translatorClientRule.baseUri().toString()),
            ConfigOverride.config("redisService.url", redisMockURI),
            ConfigOverride.config("metadataPublishingConfiguration.metadataFilePath", ""),
            ConfigOverride.config("metadataPublishingConfiguration.metadataPublishPath", "")
    );

    @Rule
    public GatewayAppRule proxyNodeAppRuleEmbeddedRedis = new GatewayAppRule(
            ConfigOverride.config("eidasSamlParserService.url", espClientRule.baseUri().toString()),
            ConfigOverride.config("verifyServiceProviderService.url", vspClientRule.baseUri().toString()),
            ConfigOverride.config("translatorService.url", translatorClientRule.baseUri().toString()),
            ConfigOverride.config("redisService.url", "redis://localhost:" + EMBEDDED_REDIS_PORT),
            ConfigOverride.config("metadataPublishingConfiguration.metadataFilePath", ""),
            ConfigOverride.config("metadataPublishingConfiguration.metadataPublishPath", "")
    );

    @Rule
    public GatewayAppRule proxyNodeServerErrorAppRule = new GatewayAppRule(
            ConfigOverride.config("eidasSamlParserService.url", espClientRule.baseUri().toString()),
            ConfigOverride.config("verifyServiceProviderService.url", vspClientRule.baseUri().toString()),
            ConfigOverride.config("translatorService.url", translatorClientServerErrorRule.baseUri().toString()),
            ConfigOverride.config("redisService.url", redisMockURI),
            ConfigOverride.config("metadataPublishingConfiguration.metadataFilePath", ""),
            ConfigOverride.config("metadataPublishingConfiguration.metadataPublishPath", "")
    );

    @Rule
    public GatewayAppRule proxyNodeClientErrorAppRule = new GatewayAppRule(
            ConfigOverride.config("eidasSamlParserService.url", espClientRule.baseUri().toString()),
            ConfigOverride.config("verifyServiceProviderService.url", vspClientRule.baseUri().toString()),
            ConfigOverride.config("translatorService.url", translatorClientClientErrorRule.baseUri().toString()),
            ConfigOverride.config("redisService.url", redisMockURI),
            ConfigOverride.config("metadataPublishingConfiguration.metadataFilePath", ""),
            ConfigOverride.config("metadataPublishingConfiguration.metadataPublishPath", "")
    );

    private final Form postForm = new Form()
            .param(SamlFormMessageType.SAML_RESPONSE, ValidationTestDataUtils.sample_hubSamlResponse)
            .param("RelayState", "relay-state");

    @Test
    public void hubResponseReturnsHtmlFormWithSamlBlob() throws Exception {
        Collection<NewCookie> cookies = postEidasAuthnRequest(proxyNodeAppRule);
        assertThat(cookies.size()).isEqualTo(2);
        Logger logger = (Logger) LoggerFactory.getLogger(ProxyNodeLogger.class);
        logger.addAppender(appender);

        Invocation.Builder builder = createGatewayResponseBuilder(cookies, proxyNodeAppRule);
        Entity<Form> form = Entity.form(postForm);
        Response response = builder.buildPost(form).invoke();

        assertThat(response.getStatus()).isEqualTo(200);
        assertLogsIngressEgress();

        final String htmlString = response.readEntity(String.class);
        HtmlHelpers.assertXPath(
                htmlString,
                String.format(
                        "//form[@action='%s']/input[@name='SAMLResponse'][@value='%s']",
                        sample_destinationUrl,
                        TestTranslatorResource.SAML_SUCCESS_BLOB));

        HtmlHelpers.assertXPath(
                htmlString,
                String.format(
                        "//form[@action='%s']/input[@name='RelayState'][@value='relay-state']",
                        sample_destinationUrl));
    }

    @Test
    public void redisCanStoreCertificateInSession() throws Throwable {
        Collection<NewCookie> cookies = postEidasAuthnRequest(proxyNodeAppRuleEmbeddedRedis);
        Invocation.Builder builder = createGatewayResponseBuilder(cookies, proxyNodeAppRuleEmbeddedRedis);
        Response response = builder.post(Entity.form(postForm));

        assertThat(response.getStatus()).isEqualTo(200);

        final List<HubResponseTranslatorRequest> translatorArgs = testTranslatorResource.getTranslatorArgs();
        assertThat(translatorArgs.get(0).getConnectorEncryptionCertificate()).isEqualTo(STUB_COUNTRY_PUBLIC_PRIMARY_CERT);
        assertThat(translatorArgs.size()).isOne();
    }

    @Test
    public void redirectsToErrorPageIfSessionMissingException() throws URISyntaxException {
        Response response = proxyNodeAppRule
                .target(Urls.GatewayUrls.GATEWAY_HUB_RESPONSE_RESOURCE, false)
                .request()
                .post(Entity.form(postForm));

        assertThat(response.getStatus()).isEqualTo(Response.Status.SEE_OTHER.getStatusCode());
        assertThat(response.getHeaderString("Location")).isEqualTo(errorPageRedirectUrl);
    }

    @Test
    public void serverErrorResponseFromTranslatorReturns200SamlErrorResponse() throws Exception {
        Collection<NewCookie> cookies = postEidasAuthnRequest(proxyNodeServerErrorAppRule);
        Invocation.Builder builder = createGatewayResponseBuilder(cookies, proxyNodeServerErrorAppRule);
        Response response = builder.post(Entity.form(postForm));

        assertGoodSamlErrorResponse(response);
    }

    @Test
    public void clientErrorResponseFromTranslatorReturns200SamlErrorResponse() throws Exception {
        Collection<NewCookie> cookies = postEidasAuthnRequest(proxyNodeClientErrorAppRule);
        Invocation.Builder builder = createGatewayResponseBuilder(cookies, proxyNodeClientErrorAppRule);
        Response response = builder.post(Entity.form(postForm));

        assertGoodSamlErrorResponse(response);
    }

    private void assertLogsIngressEgress() {
        verify(appender, atLeastOnce()).doAppend(loggingEventArgumentCaptor.capture());
        final List<ILoggingEvent> logEvents = loggingEventArgumentCaptor.getAllValues();

        assertThat(logEvents).filteredOn(e -> e.getMessage().equals(MESSAGE_INGRESS)).hasSizeGreaterThanOrEqualTo(1);
        assertThat(logEvents).filteredOn(e -> e.getMessage().equals(MESSAGE_EGRESS)).hasSizeGreaterThanOrEqualTo(1);
    }

    private Collection<NewCookie> postEidasAuthnRequest(GatewayAppRule appRule) throws Exception {
        return postEidasAuthnRequest(buildAuthnRequest(), appRule).getCookies().values();
    }

    private Invocation.Builder createGatewayResponseBuilder(Collection<NewCookie> cookies, GatewayAppRule gatewayAppRule) throws URISyntaxException {
        Invocation.Builder builder = gatewayAppRule
                .target(Urls.GatewayUrls.GATEWAY_HUB_RESPONSE_RESOURCE)
                .request();
        for (NewCookie cookie : cookies) { // hack to remove append of ',$Version=1' to cookie value
            if ("gateway-session".equals(cookie.getName())) {
                builder.cookie(cookie.getName(), cookie.getValue());
            } else {
                builder.cookie(cookie);
            }
        }
        return builder;
    }

    private void assertGoodSamlErrorResponse(Response response) throws XPathExpressionException, ParserConfigurationException {
        final String htmlString = response.readEntity(String.class);

        assertThat(response.getStatus()).isEqualTo(200);

        HtmlHelpers.assertXPath(
                htmlString,
                String.format("//form[@action='%s']/input[@name='SAMLResponse'][@value='%s']", sample_destinationUrl, SAML_ERROR_BLOB)
        );

        HtmlHelpers.assertXPath(
                htmlString,
                String.format("//form[@action='%s']/input[@name='RelayState'][@value='relay-state']", sample_destinationUrl)
        );
    }

    @AfterAll
    public void tearDown() {
        this.killTestRedis();
    }
}
