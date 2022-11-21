/*
 * Copyright 2022 sberg it-systeme GmbH
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
package net.sberg.openkim.konfiguration.konnektor.webservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.ws.conn.certificateservice.v6_0_1.ReadCardCertificate;
import de.gematik.ws.conn.certificateservice.v6_0_1.ReadCardCertificateResponse;
import de.gematik.ws.conn.certificateservice.v6_0_1.VerifyCertificate;
import de.gematik.ws.conn.certificateservice.v6_0_1.VerifyCertificateResponse;
import de.gematik.ws.conn.certificateservicecommon.v2.CertRefEnum;
import de.gematik.ws.conn.certificateservicecommon.v2.X509DataInfoListType;
import de.gematik.ws.conn.connectorcontext.ContextType;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konfiguration.konnektor.*;
import net.sberg.openkim.log.DefaultLogger;
import org.apache.commons.codec.binary.Base64;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private static final String OP_READ = "ReadCertificate";
    private static final String OP_VERIFY = "VerifyCertificate";

    private static final String C_AUT_CERT_REF = "C.AUT";
    private static final String C_ENC_CERT_REF = "C.ENC";
    private static final String C_SIG_CERT_REF = "C.SIG";
    private static final String C_QES_CERT_REF = "C.QES";
    private static final List CERT_REFS = Arrays.asList(C_AUT_CERT_REF, C_ENC_CERT_REF, C_SIG_CERT_REF, C_QES_CERT_REF);

    public String execute(
        DefaultLogger logger,
        WebserviceBean webserviceBean,
        Map serviceBeanMap
    ) throws Exception {
        TimeMetric timeMetric = null;
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();
        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.CertificateService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);

            if (webserviceBean.getOpId().equals(OP_READ)) {

                WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction("ReadCardCertificate"),
                    logger
                );

                CertificateReadWebserviceBean readCertificateWebserviceBean = new ObjectMapper().convertValue(serviceBeanMap, CertificateReadWebserviceBean.class);
                ReadCardCertificate readCardCertificate = new ReadCardCertificate();

                if (!readCardCertificate.getClass().getPackageName().equals(packageName)) {
                    throw new IllegalStateException(
                        "certificate webservice not valid "
                        + webserviceBean.getKonnId()
                        + " - "
                        + webserviceBean.getWsId()
                        + " - packagename not equal "
                        + packageName + " - "
                        + readCardCertificate.getClass().getPackageName()
                    );
                }

                readCardCertificate.setCardHandle(readCertificateWebserviceBean.getCardHandle());
                readCardCertificate.setContext(contextType);

                ReadCardCertificate.CertRefList certRefList = new ReadCardCertificate.CertRefList();
                String[] certRefArr = readCertificateWebserviceBean.getCertRefList().split(",");
                for (int i = 0; i < certRefArr.length; i++) {
                    if (!CERT_REFS.contains(certRefArr[i].trim())) {
                        throw new IllegalStateException(
                            "certificate webservice (op = read certificate) not valid "
                            + webserviceBean.getKonnId()
                            + " - "
                            + webserviceBean.getWsId()
                            + " - certref unknown "
                            + certRefArr[i].trim()
                        );
                    }
                    certRefList.getCertRef().add(CertRefEnum.fromValue(certRefArr[i].trim()));
                }
                readCardCertificate.setCertRefList(certRefList);

                timeMetric = metricFactory.timer("CertificateService:readCardCertificate:execute");
                ReadCardCertificateResponse readCardCertificateResponse = (ReadCardCertificateResponse) webserviceConnector.getSoapResponse(readCardCertificate);
                timeMetric.stopAndPublish();

                logger.logLine("Status = " + readCardCertificateResponse.getStatus().getResult());
                logger.logLine("Anzahl der Zertifikate = " + readCardCertificateResponse.getX509DataInfoList().getX509DataInfo().size());
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                int idx = 1;
                for (Iterator<X509DataInfoListType.X509DataInfo> iterator = readCardCertificateResponse.getX509DataInfoList().getX509DataInfo().iterator(); iterator.hasNext(); ) {
                    logger.logLine("***********************************");
                    logger.logLine("Informationen vom Zertifikat = " + idx);
                    X509DataInfoListType.X509DataInfo x509DataInfo = iterator.next();
                    logger.logLine("CertRef = " + x509DataInfo.getCertRef());
                    X509Certificate cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(x509DataInfo.getX509Data().getX509Certificate()));
                    logger.logLine("Aussteller = " + cert.getIssuerDN().getName());
                    logger.logLine("Inhaber = " + cert.getSubjectDN().getName());
                    logger.logLine("Seriennummer = " + cert.getSerialNumber().toString());
                    logger.logLine("Version = " + cert.getVersion());
                    logger.logLine("Gültig von = " + DateTimeFormatter.ofPattern("dd.MM.yyyy hh:mm:ss").format(new Timestamp(cert.getNotBefore().getTime()).toLocalDateTime()));
                    logger.logLine("Gültig bis = " + DateTimeFormatter.ofPattern("dd.MM.yyyy hh:mm:ss").format(new Timestamp(cert.getNotAfter().getTime()).toLocalDateTime()));
                    logger.logLine("Inhalt = " + StringUtils.convertToPem(cert));
                    idx++;
                }
                return logger.getLogContentAsStr();
            } else if (webserviceBean.getOpId().equals(OP_VERIFY)) {

                WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                    konnektor,
                    packageName,
                    konnektorServiceBean,
                    konnektorServiceBean.createSoapAction("VerifyCertificate"),
                    logger
                );

                CertificateVerifyWebserviceBean verifyCertificateWebserviceBean = new ObjectMapper().convertValue(serviceBeanMap, CertificateVerifyWebserviceBean.class);

                InputStream targetStream = new ByteArrayInputStream(Base64.decodeBase64(verifyCertificateWebserviceBean.getCertContent()));
                X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X509").generateCertificate(targetStream);

                VerifyCertificate verifyCertificate = new VerifyCertificate();
                if (!verifyCertificate.getClass().getPackageName().equals(packageName)) {
                    throw new IllegalStateException(
                        "certificate webservice not valid "
                        + webserviceBean.getKonnId()
                        + " - "
                        + webserviceBean.getWsId()
                        + " - packagename not equal "
                        + packageName
                        + " - "
                        + verifyCertificate.getClass().getPackageName()
                    );
                }
                verifyCertificate.setX509Certificate(cert.getEncoded());
                verifyCertificate.setContext(contextType);

                timeMetric = metricFactory.timer("CertificateService:verifyCertificate:execute");
                VerifyCertificateResponse verifyCertificateResponse = (VerifyCertificateResponse) webserviceConnector.getSoapResponse(verifyCertificate);
                timeMetric.stopAndPublish();

                logger.logLine("Status = " + verifyCertificateResponse.getStatus().getResult());
                logger.logLine("Verification-Status = " + verifyCertificateResponse.getVerificationStatus().getVerificationResult().value());

                return logger.getLogContentAsStr();
            } else {
                throw new IllegalStateException("unknown opId for the CertificateService and konnektor: " + konnektor.getIp());
            }
        } catch (Exception e) {
            log.error("error on executing the CertificateService for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

    public ReadCardCertificateResponse getCertificate(
        DefaultLogger logger,
        String certRef,
        String cardHandle) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            KonnektorServiceBean konnektorServiceBean = konnektor.extractKonnektorServiceBean(EnumKonnektorServiceBeanType.CertificateService, true);
            String packageName = konnektorServiceBean.createClassPackageName();

            WebserviceConnector webserviceConnector = KonnektorWebserviceUtils.createConnector(
                konnektor,
                packageName,
                konnektorServiceBean,
                konnektorServiceBean.createSoapAction("ReadCardCertificate"),
                logger
            );

            ReadCardCertificate readCardCertificate = new ReadCardCertificate();

            if (!readCardCertificate.getClass().getPackageName().equals(packageName)) {
                throw new IllegalStateException(
                    "certificate webservice not valid "
                    + konnektor.getUuid()
                    + " - "
                    + konnektorServiceBean.getId()
                    + " - packagename not equal "
                    + packageName
                    + " - "
                    + readCardCertificate.getClass().getPackageName()
                );
            }

            ContextType contextType = new ContextType();
            contextType.setMandantId(logger.getDefaultLoggerContext().getMandantId());
            contextType.setClientSystemId(logger.getDefaultLoggerContext().getClientSystemId());
            contextType.setWorkplaceId(logger.getDefaultLoggerContext().getWorkplaceId());

            readCardCertificate.setCardHandle(cardHandle);
            readCardCertificate.setContext(contextType);

            ReadCardCertificate.CertRefList certRefList = new ReadCardCertificate.CertRefList();
            certRefList.getCertRef().add(CertRefEnum.fromValue(certRef));
            readCardCertificate.setCertRefList(certRefList);

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("CertificateService:readCardCertificate:getCertificate");
            ReadCardCertificateResponse readCardCertificateResponse = (ReadCardCertificateResponse) webserviceConnector.getSoapResponse(readCardCertificate);
            timeMetric.stopAndPublish();

            return readCardCertificateResponse;
        } catch (Exception e) {
            log.error("error on loading the certificate for the konnektor: " + konnektor.getIp() + " and the cardHandle: " + cardHandle + " and the certRef: " + certRef);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }
}
