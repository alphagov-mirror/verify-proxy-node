package uk.gov.ida.notification.translator.saml;

import org.joda.time.DateTime;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.security.x509.BasicX509Credential;
import se.litsec.eidas.opensaml.common.EidasConstants;
import se.litsec.eidas.opensaml.ext.attributes.AttributeConstants;
import se.litsec.eidas.opensaml.ext.attributes.CurrentFamilyNameType;
import se.litsec.eidas.opensaml.ext.attributes.CurrentGivenNameType;
import se.litsec.eidas.opensaml.ext.attributes.DateOfBirthType;
import se.litsec.eidas.opensaml.ext.attributes.PersonIdentifierType;
import uk.gov.ida.common.shared.security.X509CertificateFactory;
import uk.gov.ida.notification.contracts.CountryMetadataResponse;
import uk.gov.ida.notification.contracts.verifyserviceprovider.Attributes;
import uk.gov.ida.notification.contracts.verifyserviceprovider.VspLevelOfAssurance;
import uk.gov.ida.notification.contracts.verifyserviceprovider.VspScenario;
import uk.gov.ida.notification.exceptions.hubresponse.HubResponseTranslationException;
import uk.gov.ida.notification.saml.EidasAttributeBuilder;
import uk.gov.ida.notification.saml.EidasResponseBuilder;
import uk.gov.ida.notification.saml.ResponseAssertionEncrypter;
import uk.gov.ida.notification.shared.proxy.MetatronProxy;
import uk.gov.ida.saml.core.domain.NonMatchingVerifiableAttribute;

import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class HubResponseTranslator {

    private String proxyNodeMetadataForConnectorNodeUrl;
    private final MetatronProxy metatronProxy;
    private Supplier<EidasResponseBuilder> eidasResponseBuilderSupplier;
    private String pidPrefix;
    private static final X509CertificateFactory X_509_CERTIFICATE_FACTORY = new X509CertificateFactory();


    public HubResponseTranslator(
            MetatronProxy metatronProxy,
            Supplier<EidasResponseBuilder> eidasResponseBuilderSupplier,
            String proxyNodeMetadataForConnectorNodeUrl) {
        this.metatronProxy = metatronProxy;
        this.eidasResponseBuilderSupplier = eidasResponseBuilderSupplier;
        this.proxyNodeMetadataForConnectorNodeUrl = proxyNodeMetadataForConnectorNodeUrl;
        this.pidPrefix = String.format("GB/%s/", "TODO country code");
    }

    Response getTranslatedHubResponse(HubResponseContainer hubResponseContainer) {
        final List<EidasAttributeBuilder> eidasAttributeBuilders = new ArrayList<>();

        final String pid = hubResponseContainer.getPid()
                .map(p -> pidPrefix + p)
                .orElse(null);

        if (hubResponseContainer.getVspScenario().equals(VspScenario.IDENTITY_VERIFIED)) {
            var attributes = hubResponseContainer
                    .getAttributes()
                    .orElseThrow(
                            () -> new HubResponseTranslationException("Attributes are null for VSP scenario: " + hubResponseContainer.getVspScenario())
                    );

            eidasAttributeBuilders.add(new EidasAttributeBuilder(
                    AttributeConstants.EIDAS_CURRENT_GIVEN_NAME_ATTRIBUTE_NAME, AttributeConstants.EIDAS_CURRENT_GIVEN_NAME_ATTRIBUTE_FRIENDLY_NAME, CurrentGivenNameType.TYPE_NAME,
                    getCombineFirstAndMiddleNames(attributes)
            ));

            eidasAttributeBuilders.add(new EidasAttributeBuilder(
                    AttributeConstants.EIDAS_CURRENT_FAMILY_NAME_ATTRIBUTE_NAME, AttributeConstants.EIDAS_CURRENT_FAMILY_NAME_ATTRIBUTE_FRIENDLY_NAME, CurrentFamilyNameType.TYPE_NAME,
                    getCombinedSurnames(attributes)
            ));

            eidasAttributeBuilders.add(new EidasAttributeBuilder(
                    AttributeConstants.EIDAS_DATE_OF_BIRTH_ATTRIBUTE_NAME, AttributeConstants.EIDAS_DATE_OF_BIRTH_ATTRIBUTE_FRIENDLY_NAME, DateOfBirthType.TYPE_NAME,
                    getLatestValidDateOfBirth(attributes)
            ));

            eidasAttributeBuilders.add(new EidasAttributeBuilder(AttributeConstants.EIDAS_PERSON_IDENTIFIER_ATTRIBUTE_NAME,
                    AttributeConstants.EIDAS_PERSON_IDENTIFIER_ATTRIBUTE_FRIENDLY_NAME,
                    PersonIdentifierType.TYPE_NAME,
                    pid
            ));
        }

        final DateTime now = DateTime.now();
        final List<org.opensaml.saml.saml2.core.Attribute> eidasAttributes = eidasAttributeBuilders
                .stream()
                .map(EidasAttributeBuilder::build)
                .collect(Collectors.toList());

        // isser matches

        String entityId = hubResponseContainer.getIssuer().toString();
        CountryMetadataResponse countryMetadata = metatronProxy.getCountryMetadata(entityId);
        // todo matches
        countryMetadata.getEntityId().equals(hubResponseContainer.getIssuer());

        Response response = eidasResponseBuilderSupplier.get()
                .withIssuer(proxyNodeMetadataForConnectorNodeUrl)
                .withStatus(getMappedStatusCode(hubResponseContainer.getVspScenario()))
                .withInResponseTo(hubResponseContainer.getEidasRequestId())
                .withIssueInstant(now)
                .withDestination(hubResponseContainer.getDestinationURL())
                .withAssertionSubject(pid)
                .withAssertionConditions(countryMetadata.getEntityId())
                .withLoa(getMappedLoa(hubResponseContainer.getLevelOfAssurance()), now)
                .addAssertionAttributeStatement(eidasAttributes)
                .build();
        String samlEncryptionCertX509 = countryMetadata.getSamlEncryptionCertX509();
        X509Certificate certificate = X_509_CERTIFICATE_FACTORY.createCertificate(samlEncryptionCertX509);
        return encryptAssertions(response, certificate);
    }

    private Response encryptAssertions(Response eidasResponse, X509Certificate encryptionCertificate) {
        final BasicX509Credential encryptionCredential = new BasicX509Credential(encryptionCertificate);
        final ResponseAssertionEncrypter assertionEncrypter = new ResponseAssertionEncrypter(encryptionCredential);
        return assertionEncrypter.encrypt(eidasResponse);
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    private static String getMappedLoa(Optional<VspLevelOfAssurance> vspLoa) {
        if (vspLoa.isEmpty()) {
            return null;
        }
        switch (vspLoa.get()) {
            case LEVEL_2:
                return EidasConstants.EIDAS_LOA_SUBSTANTIAL;
            default:
                throw new HubResponseTranslationException("Received unsupported LOA from VSP: " + vspLoa);
        }
    }

    private static String getMappedStatusCode(VspScenario vspScenario) {
        switch (vspScenario) {
            case IDENTITY_VERIFIED:
                return StatusCode.SUCCESS;
            case CANCELLATION:
                return StatusCode.RESPONDER;
            case AUTHENTICATION_FAILED:
                return StatusCode.AUTHN_FAILED;
            case REQUEST_ERROR:
                throw new HubResponseTranslationException("Received error status from VSP: " + vspScenario);
            default:
                throw new HubResponseTranslationException("Received unknown status from VSP: " + vspScenario);
        }
    }

    private static String getCombineFirstAndMiddleNames(Attributes attributes) {
        var firstNames = attributes.getFirstNamesAttributesList();
        var validFirstNames = firstNames.getValidAttributes();
        if (validFirstNames.isEmpty()) {
            throw new HubResponseTranslationException("No verified current first name present: " + firstNames.createAttributesMessage());
        }
        var validMiddleNames = attributes.getMiddleNamesAttributesList().getValidAttributes();
        validFirstNames.addAll(validMiddleNames);

        return combineStringAttributeValues(validFirstNames);
    }

    private static String getCombinedSurnames(Attributes attributes) {
        var surnames = attributes.getSurnamesAttributesList();
        var validSurnames = surnames.getValidAttributes();
        if (validSurnames.isEmpty()) {
            throw new HubResponseTranslationException("No verified current surname present: " + surnames.createAttributesMessage());
        }
        return combineStringAttributeValues(surnames.getValidAttributes());
    }

    private static String getLatestValidDateOfBirth(Attributes attributes) {
        var datesOfBirth = attributes.getDatesOfBirthAttributesList();
        var validDatesOfBirth = datesOfBirth.getValidAttributes();
        if (validDatesOfBirth.isEmpty()) {
            throw new HubResponseTranslationException("No verified current date of birth present: " + datesOfBirth.createAttributesMessage());
        }

        return validDatesOfBirth.stream()
                .map(NonMatchingVerifiableAttribute::getValue)
                .reduce(BinaryOperator.maxBy(Comparator.comparing(LocalDate::toEpochDay)))
                .map(LocalDate::toString).get();
    }

    private static String combineStringAttributeValues(List<NonMatchingVerifiableAttribute<String>> attributeStream) {
        return attributeStream.stream().map(NonMatchingVerifiableAttribute::getValue).filter(s -> !s.isEmpty()).collect(Collectors.joining(" "));
    }
}
