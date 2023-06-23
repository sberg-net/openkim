/*
 * Copyright 2023 sberg it-systeme GmbH
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package net.sberg.openkim.pipeline.operation.konnektor.webservice;

import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import de.gematik.ws.conn.signatureservice.v7.VerifyDocument;
import de.gematik.ws.conn.signatureservice.v7.VerifyDocumentResponse;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import oasis.names.tc.dss._1_0.core.schema.Base64Signature;
import oasis.names.tc.dss._1_0.core.schema.SignatureObject;
import oasis.names.tc.dss_x._1_0.profiles.verificationreport.schema_.ReturnVerificationReport;
import org.apache.commons.codec.binary.Base64;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class VerifySignedDocumentOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(VerifySignedDocumentOperation.class);
    public static final String NAME = "VerifySignedDocument";

    public static final String ENV_SIGNED_CONTENT = "signedData";
    public static final String ENV_SIGNED_DATA_AS_BASE64 = "signedDataAsBase64";
    public static final String ENV_VERIFY_DOCUMENT_RESPONSE = "verifyDocumentResponse";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            VerifyDocumentResponse verifyDocumentResponse = (VerifyDocumentResponse) context.getEnvironmentValue(NAME, ENV_VERIFY_DOCUMENT_RESPONSE);
            context.getLogger().logLine("Status = " + verifyDocumentResponse.getStatus().getResult());
            context.getLogger().logLine("Result = " + verifyDocumentResponse.getVerificationResult().getHighLevelResult());
        };
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.SignatureService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction("VerifyDocument"),
                    logger
            );

            VerifyDocument verifyDocument = new VerifyDocument();
            if (!verifyDocument.getClass().getPackageName().equals(packageName)) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException(
                    "signature webservice not valid "
                        + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_KONNEKTOR_ID)
                        + " - " + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_WEBSERVICE_ID)
                        + " - packagename not equal "
                        + packageName
                        + " - "
                        + verifyDocument.getClass().getPackageName()
                ));
            }
            else {

                verifyDocument.setContext(contextType);

                boolean signedDataAsBase64 = (boolean) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_SIGNED_DATA_AS_BASE64);
                byte[] signedData = ((String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_SIGNED_CONTENT)).getBytes(StandardCharsets.UTF_8);

                SignatureObject signatureObject = new SignatureObject();
                Base64Signature base64Signature = new Base64Signature();
                base64Signature.setValue(signedDataAsBase64 ? Base64.decodeBase64(signedData) : signedData);
                base64Signature.setType("urn:ietf:rfc:5652");
                signatureObject.setBase64Signature(base64Signature);
                verifyDocument.setSignatureObject(signatureObject);

                VerifyDocument.OptionalInputs optionalInputs = new VerifyDocument.OptionalInputs();
                ReturnVerificationReport returnVerificationReport = new ReturnVerificationReport();
                returnVerificationReport.setExpandBinaryValues(false);
                returnVerificationReport.setIncludeCertificateValues(true);
                returnVerificationReport.setIncludeRevocationValues(true);
                returnVerificationReport.setIncludeVerifier(false);
                returnVerificationReport.setReportDetailLevel("urn:oasis:names:tc:dss:1.0:profiles:verificationreport:reportdetail:allDetails");
                optionalInputs.setReturnVerificationReport(returnVerificationReport);
                verifyDocument.setOptionalInputs(optionalInputs);

                verifyDocument.setIncludeRevocationInfo(false);

                timeMetric = metricFactory.timer(NAME);
                VerifyDocumentResponse verifyDocumentResponse = (VerifyDocumentResponse) webserviceConnector.getSoapResponse(verifyDocument);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_VERIFY_DOCUMENT_RESPONSE, verifyDocumentResponse);
                timeMetric.stopAndPublish();

                okConsumer.accept(defaultPipelineOperationContext);
            }
        }
        catch (Exception e) {
            log.error("error on executing the VerifySignedDocumentOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
