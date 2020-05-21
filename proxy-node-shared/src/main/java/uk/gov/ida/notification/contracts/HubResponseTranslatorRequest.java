package uk.gov.ida.notification.contracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotBlank;
import uk.gov.ida.notification.validations.ValidBase64Xml;
import uk.gov.ida.notification.validations.ValidDestinationUri;
import uk.gov.ida.notification.validations.ValidLOA;
import uk.gov.ida.notification.validations.ValidSamlId;

import javax.validation.constraints.NotNull;
import java.net.URI;

public class HubResponseTranslatorRequest {

    @NotBlank
    @JsonProperty
    @ValidBase64Xml
    private String samlResponse;

    @NotBlank
    @ValidSamlId
    @JsonProperty
    private String requestId;

    @NotBlank
    @ValidSamlId
    @JsonProperty
    private String eidasRequestId;

    @NotBlank
    @ValidLOA
    @JsonProperty
    private String levelOfAssurance;

    @NotNull
    @ValidDestinationUri
    @JsonProperty
    private URI destinationUrl;

    @NotNull
    @JsonProperty
    private URI eidasIssuerEntityId;

    @JsonProperty
    private boolean transientPid;

    @SuppressWarnings("Needed for JSON serialisation")
    public HubResponseTranslatorRequest() {
    }

    public HubResponseTranslatorRequest(
            String samlResponse,
            String requestId,
            String eidasRequestId,
            String levelOfAssurance,
            URI destinationUrl,
            URI eidasIssuerEntityId,
            boolean transientPid) {
        this.samlResponse = samlResponse;
        this.requestId = requestId;
        this.eidasRequestId = eidasRequestId;
        this.levelOfAssurance = levelOfAssurance;
        this.destinationUrl = destinationUrl;
        this.eidasIssuerEntityId = eidasIssuerEntityId;
        this.transientPid = transientPid;
    }

    public String getSamlResponse() {
        return samlResponse;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getEidasRequestId() {
        return eidasRequestId;
    }

    public URI getEidasIssuerEntityId() { 
        return eidasIssuerEntityId; 
    }

    public String getLevelOfAssurance() {
        return levelOfAssurance;
    }

    public URI getDestinationUrl() {
        return destinationUrl;
    }

    public boolean isTransient() {
        return transientPid;
    }
}
