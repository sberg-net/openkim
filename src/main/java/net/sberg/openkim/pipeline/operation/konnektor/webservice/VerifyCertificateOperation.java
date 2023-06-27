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

import de.gematik.ws.conn.certificateservice.v6.VerifyCertificate;
import de.gematik.ws.conn.certificateservice.v6.VerifyCertificateResponse;
import de.gematik.ws.conn.connectorcontext.v2.ContextType;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.commons.codec.binary.Base64;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
@Component
public class VerifyCertificateOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(VerifyCertificateOperation.class);
    public static final String NAME = "VerifyCertificate";

    public static final String ENV_CERTCONTENT = "certContent";
    public static final String ENV_VERIFY_CERT_RESPONSE = "verifyCertificateResponse";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            VerifyCertificateResponse verifyCertificateResponse = (VerifyCertificateResponse) context.getEnvironmentValue(NAME, ENV_VERIFY_CERT_RESPONSE);
            context.getLogger().logLine("Status = " + verifyCertificateResponse.getStatus().getResult());
            context.getLogger().logLine("Verification-Status = " + verifyCertificateResponse.getVerificationStatus().getVerificationResult().value());
        };
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.CertificateService, true);
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
                    konnektorServiceBean.createSoapAction(NAME),
                    logger
            );

            InputStream targetStream = new ByteArrayInputStream(Base64.decodeBase64((String)defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CERTCONTENT)));
            X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X509").generateCertificate(targetStream);

            VerifyCertificate verifyCertificate = new VerifyCertificate();
            if (!verifyCertificate.getClass().getPackageName().equals(packageName)) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException(
                    "certificate webservice not valid "
                        + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_KONNEKTOR_ID)
                        + " - " + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_WEBSERVICE_ID)
                        + " - packagename not equal "
                        + packageName
                        + " - "
                        + verifyCertificate.getClass().getPackageName()
                ));
            }
            else {
                verifyCertificate.setX509Certificate(cert.getEncoded());
                verifyCertificate.setContext(contextType);

                timeMetric = metricFactory.timer(NAME);
                VerifyCertificateResponse verifyCertificateResponse = (VerifyCertificateResponse) webserviceConnector.getSoapResponse(verifyCertificate);
                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_VERIFY_CERT_RESPONSE, verifyCertificateResponse);
                timeMetric.stopAndPublish();

                okConsumer.accept(defaultPipelineOperationContext);
            }
        }
        catch (Exception e) {
            log.error("error on executing the VerifyCardCertificateOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
