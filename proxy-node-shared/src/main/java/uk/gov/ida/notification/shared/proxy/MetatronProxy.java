package uk.gov.ida.notification.shared.proxy;

import uk.gov.ida.exceptions.ApplicationException;
import uk.gov.ida.notification.contracts.CountryMetadataResponse;
import uk.gov.ida.notification.exceptions.proxy.MetatronResponseException;

import javax.ws.rs.core.UriBuilder;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MetatronProxy {
    private final ProxyNodeJsonClient metatronClient;
    private final URI metatronUri;

    public MetatronProxy(
            ProxyNodeJsonClient metatronClient,
            URI metatronUri) {
        this.metatronClient = metatronClient;
        this.metatronUri = metatronUri;
    }

    public CountryMetadataResponse getCountryMetadata(String entityId) {
        try {
            return metatronClient.get(
                    UriBuilder
                            .fromUri(metatronUri)
                            .path(URLEncoder.encode(entityId, StandardCharsets.UTF_8.toString()))
                            .build(),
                    CountryMetadataResponse.class
            );
        } catch (ApplicationException e) {
            throw new MetatronResponseException(e, entityId);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
