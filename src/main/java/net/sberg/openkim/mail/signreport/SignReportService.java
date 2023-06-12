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
package net.sberg.openkim.mail.signreport;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import oasis.names.tc.dss_x._1_0.profiles.verificationreport.schema_.*;
import org.apache.james.metrics.api.TimeMetric;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBElement;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class SignReportService {

    private static final Logger log = LoggerFactory.getLogger(SignReportService.class);

    @Autowired
    @Qualifier("signaturpruefbericht")
    private JasperReport signaturpruefbericht;

    public File execute(DefaultLogger logger, VerificationReportType verificationReportType) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("SignReportService:execute");

            ObjectMapper objectMapper = new ObjectMapper();

            VerifyResult verifyResult = new VerifyResult();
            verifyResult.setVerificationTime(StringUtils.convertToStr(verificationReportType.getVerificationTimeInfo().getVerificationTime()));

            IndividualReportType individualReportType = verificationReportType.getIndividualReport().get(0);

            SignedObjectIdentifierType signedObjectIdentifierType = individualReportType.getSignedObjectIdentifier();
            verifyResult.setSigningTime(StringUtils.convertToStr(signedObjectIdentifierType.getSignedProperties().getSignedSignatureProperties().getSigningTime()));

            for (Iterator iterator = individualReportType.getDetails().getAny().iterator(); iterator.hasNext(); ) {
                Object obj = iterator.next();
                Class clazz = null;
                Object value = null;
                if (obj instanceof LinkedHashMap) {
                    clazz = Class.forName((String) ((Map) obj).get("declaredType"));
                    value = ((Map) obj).get("value");
                } else if (obj instanceof JAXBElement) {
                    JAXBElement jaxbElement = (JAXBElement) obj;
                    clazz = jaxbElement.getDeclaredType();
                    value = jaxbElement.getValue();
                }
                if (clazz.equals(DetailedSignatureReportType.class)) {
                    DetailedSignatureReportType detailedSignatureReportType = objectMapper.convertValue(value, DetailedSignatureReportType.class);

                    verifyResult.setFormatResult(detailedSignatureReportType.getFormatOK().getResultMajor());
                    if (!verifyResult.getFormatResult().equals(VerifyResult.VALID)) {
                        logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4112);
                        logger.logLine("Fehler: " + EnumErrorCode.CODE_4112 + " - " + EnumErrorCode.CODE_4112.getHrText());
                        timeMetric.stopAndPublish();
                        return null;
                    }

                    verifyResult.setSignResult(detailedSignatureReportType.getSignatureOK().getSigMathOK().getResultMajor());
                    if (!verifyResult.getSignResult().equals(VerifyResult.VALID)) {
                        logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4115);
                        logger.logLine("Fehler: " + EnumErrorCode.CODE_4115 + " - " + EnumErrorCode.CODE_4115.getHrText());
                        timeMetric.stopAndPublish();
                        return null;
                    }

                    verifyResult.setSignAlg(detailedSignatureReportType.getSignatureOK().getSignatureAlgorithm().getAlgorithm());

                    CertificatePathValidityType certificatePathValidityType = detailedSignatureReportType.getCertificatePathValidity();
                    verifyResult.setPathValiditySummary(certificatePathValidityType.getPathValiditySummary().getResultMajor());

                    if (!verifyResult.getPathValiditySummary().equals(VerifyResult.VALID)) {
                        logger.getDefaultLoggerContext().getMailSignVerifyErrorContext().getErrorCodes().add(EnumErrorCode.CODE_4206);
                        logger.logLine("Fehler: " + EnumErrorCode.CODE_4206 + " - " + EnumErrorCode.CODE_4206.getHrText());
                        timeMetric.stopAndPublish();
                        return null;
                    }

                    verifyResult.setIssuer(certificatePathValidityType.getCertificateIdentifier().getX509IssuerName());
                    verifyResult.setSerialNumber(certificatePathValidityType.getCertificateIdentifier().getX509SerialNumber().toString());

                    for (Iterator<CertificateValidityType> iterator1 = certificatePathValidityType.getPathValidityDetail().getCertificateValidity().iterator(); iterator1.hasNext(); ) {
                        CertificateValidityType certificateValidityType = iterator1.next();

                        VerifyCertResult verifyCertResult = new VerifyCertResult();

                        if (certificateValidityType.getExtensionsOK() != null) {
                            verifyCertResult.setExtensionResult(certificateValidityType.getExtensionsOK().getResultMajor());
                        }

                        if (certificateValidityType.getChainingOK() != null) {
                            verifyCertResult.setChainingResult(certificateValidityType.getChainingOK().getResultMajor());
                        }

                        if (certificateValidityType.getValidityPeriodOK() != null) {
                            verifyCertResult.setValidityPeriodResult(certificateValidityType.getValidityPeriodOK().getResultMajor());
                        }

                        if (certificateValidityType.getSignatureOK() != null && certificateValidityType.getSignatureOK().getSigMathOK() != null) {
                            verifyCertResult.setSignResult(certificateValidityType.getSignatureOK().getSigMathOK().getResultMajor());
                        }

                        if (certificateValidityType.getSignatureOK() != null && certificateValidityType.getSignatureOK().getSignatureAlgorithm() != null) {
                            verifyCertResult.setSignAlg(certificateValidityType.getSignatureOK().getSignatureAlgorithm().getAlgorithm());
                        }

                        Certificate cert = new org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory().engineGenerateCertificate(new ByteArrayInputStream(certificateValidityType.getCertificateValue()));
                        verifyCertResult.setCertificate((X509Certificate) cert);

                        //oscp result
                        if (certificateValidityType.getCertificateStatus() != null
                            &&
                            certificateValidityType.getCertificateStatus().getRevocationEvidence() != null
                            &&
                            certificateValidityType.getCertificateStatus().getRevocationEvidence().getOCSPValidity() != null
                            &&
                            certificateValidityType.getCertificateStatus().getRevocationEvidence().getOCSPValidity().getOCSPValue() != null) {

                            VerifyOCSPResult verifyOCSPResult = new VerifyOCSPResult();
                            verifyCertResult.setVerifyOCSPResult(verifyOCSPResult);

                            OCSPResp oresponse = new OCSPResp(certificateValidityType.getCertificateStatus().getRevocationEvidence().getOCSPValidity().getOCSPValue());
                            if (oresponse.getStatus() == 0) {
                                BasicOCSPResp basicOCSPResp = (BasicOCSPResp) oresponse.getResponseObject();
                                SingleResp[] singleResps = basicOCSPResp.getResponses();
                                if (singleResps[0].getCertStatus() instanceof UnknownStatus) {
                                    verifyOCSPResult.setUnknown(true);
                                }
                                if (singleResps[0].getCertStatus() instanceof RevokedStatus) {
                                    verifyOCSPResult.setRevoked(true);
                                    RevokedStatus revokedStatus = (RevokedStatus) singleResps[0].getCertStatus();
                                    verifyOCSPResult.setRevokeTime(new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(revokedStatus.getRevocationTime().getTime())));
                                }

                                verifyOCSPResult.setProduceTime(new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date(basicOCSPResp.getProducedAt().getTime())));
                                X509CertificateHolder[] certs = basicOCSPResp.getCerts();
                                if (certs != null && certs.length > 0 && basicOCSPResp.getSignature() != null && basicOCSPResp.getSignature().length > 0) {
                                    for (int i = 0; i < certs.length; ++i) {
                                        X509CertificateHolder x509CertificateHolder = certs[i];
                                        cert = new org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory().engineGenerateCertificate(new ByteArrayInputStream(x509CertificateHolder.getEncoded()));
                                        verifyOCSPResult.getCerts().add((X509Certificate) cert);
                                    }
                                }
                            }
                        }
                        verifyResult.getCertResults().add(verifyCertResult);
                    }
                }
            }

            Map<String, Object> params = new HashMap<String, Object>();
            params.put("result", verifyResult);
            params.put("datasource", new JRBeanCollectionDataSource(List.of(new Object())));

            JasperPrint jasperPrint = JasperFillManager.fillReport(signaturpruefbericht, params, new JREmptyDataSource());
            File f = new File(System.getProperty("java.io.tmpdir") + File.separator + "Signaturprüfbericht" + System.currentTimeMillis() + ".pdf");
            JasperExportManager.exportReportToPdfFile(jasperPrint, f.getAbsolutePath());

            timeMetric.stopAndPublish();

            return f;
        } catch (Exception e) {
            log.error("error on creating sign report for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

}
