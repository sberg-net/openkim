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

import de.gematik.ws.conn.certificateservice.v6_0_1.ReadCardCertificate;
import de.gematik.ws.conn.certificateservice.v6_0_1.ReadCardCertificateResponse;
import de.gematik.ws.conn.certificateservicecommon.v2.CertRefEnum;
import de.gematik.ws.conn.certificateservicecommon.v2.X509DataInfoListType;
import de.gematik.ws.conn.connectorcontext.ContextType;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@PipelineOperation
public class ReadCardCertificateOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(ReadCardCertificateOperation.class);
    public static final String NAME = "ReadCardCertificate";

    public static final String ENV_CARDHANDLE = "cardHandle";
    public static final String ENV_CERT_REFS = "certRefs";
    public static final String ENV_READ_CARD_CERT_RESPONSE = "readCardCertificateResponse";

    private static final String C_AUT_CERT_REF = "C.AUT";
    private static final String C_ENC_CERT_REF = "C.ENC";
    private static final String C_SIG_CERT_REF = "C.SIG";
    private static final String C_QES_CERT_REF = "C.QES";
    private static final List CERT_REFS = Arrays.asList(C_AUT_CERT_REF, C_ENC_CERT_REF, C_SIG_CERT_REF, C_QES_CERT_REF);

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        return context -> {
            try {
                ReadCardCertificateResponse readCardCertificateResponse = (ReadCardCertificateResponse) context.getEnvironmentValue(NAME, ENV_READ_CARD_CERT_RESPONSE);
                context.getLogger().logLine("Status = " + readCardCertificateResponse.getStatus().getResult());
                context.getLogger().logLine("Anzahl der Zertifikate = " + readCardCertificateResponse.getX509DataInfoList().getX509DataInfo().size());
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                int idx = 1;
                for (Iterator<X509DataInfoListType.X509DataInfo> iterator = readCardCertificateResponse.getX509DataInfoList().getX509DataInfo().iterator(); iterator.hasNext(); ) {
                    context.getLogger().logLine("***********************************");
                    context.getLogger().logLine("Informationen vom Zertifikat = " + idx);
                    X509DataInfoListType.X509DataInfo x509DataInfo = iterator.next();
                    context.getLogger().logLine("CertRef = " + x509DataInfo.getCertRef());
                    X509Certificate cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(x509DataInfo.getX509Data().getX509Certificate()));
                    context.getLogger().logLine("Aussteller = " + cert.getIssuerDN().getName());
                    context.getLogger().logLine("Inhaber = " + cert.getSubjectDN().getName());
                    context.getLogger().logLine("Seriennummer = " + cert.getSerialNumber().toString());
                    context.getLogger().logLine("Version = " + cert.getVersion());
                    context.getLogger().logLine("Gültig von = " + DateTimeFormatter.ofPattern("dd.MM.yyyy hh:mm:ss").format(new Timestamp(cert.getNotBefore().getTime()).toLocalDateTime()));
                    context.getLogger().logLine("Gültig bis = " + DateTimeFormatter.ofPattern("dd.MM.yyyy hh:mm:ss").format(new Timestamp(cert.getNotAfter().getTime()).toLocalDateTime()));
                    context.getLogger().logLine("Inhalt = " + StringUtils.convertToPem(cert));
                    idx++;
                }
            }
            catch (Exception e) {
                log.error("error on consuming ReadCardCertificateResponse", e);
            }
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

            ReadCardCertificate readCardCertificate = new ReadCardCertificate();

            if (!readCardCertificate.getClass().getPackageName().equals(packageName)) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException(
                    "certificate webservice not valid "
                        + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_KONNEKTOR_ID)
                        + " - " + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_WEBSERVICE_ID)
                        + " - packagename not equal "
                        + packageName + " - "
                        + readCardCertificate.getClass().getPackageName()
                ));
            }
            else {
                readCardCertificate.setCardHandle((String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CARDHANDLE));
                readCardCertificate.setContext(contextType);

                ReadCardCertificate.CertRefList certRefList = new ReadCardCertificate.CertRefList();
                String[] certRefArr = ((String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_CERT_REFS)).split(",");
                boolean isValid = true;
                for (int i = 0; i < certRefArr.length; i++) {
                    if (!CERT_REFS.contains(certRefArr[i].trim())) {
                        failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException(
                            "certificate webservice (op = read certificate) not valid "
                                + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_KONNEKTOR_ID)
                                + " - " + defaultPipelineOperationContext.getEnvironmentValue(NAME, DefaultPipelineOperationContext.ENV_WEBSERVICE_ID)
                                + " - certref unknown "
                                + certRefArr[i].trim()
                        ));
                        isValid = false;
                        break;
                    }
                    certRefList.getCertRef().add(CertRefEnum.fromValue(certRefArr[i].trim()));
                }
                if (isValid) {
                    readCardCertificate.setCertRefList(certRefList);

                    timeMetric = metricFactory.timer(NAME);
                    ReadCardCertificateResponse readCardCertificateResponse = (ReadCardCertificateResponse) webserviceConnector.getSoapResponse(readCardCertificate);
                    defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_READ_CARD_CERT_RESPONSE, readCardCertificateResponse);

                    timeMetric.stopAndPublish();

                    okConsumer.accept(defaultPipelineOperationContext);
                }
            }
        }
        catch (Exception e) {
            log.error("error on executing the ReadCardCertificateOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
