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
package net.sberg.openkim.pipeline.operation.konnektor.vzd;

import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.*;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.log.error.MailaddressCertErrorContext;
import net.sberg.openkim.log.error.MailaddressKimVersionErrorContext;
import net.sberg.openkim.pipeline.PipelineOperation;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@PipelineOperation
@Component
public class LoadVzdCertsOperation implements IPipelineOperation  {

    private static final Logger log = LoggerFactory.getLogger(LoadVzdCertsOperation.class);
    public static final String NAME = "LoadVzdCerts";

    public static final String ENV_ADDRESSES = "addresses";
    public static final String ENV_VZD_CERTS = "vzdCerts";
    public static final String ENV_VZD_SEARCH_BASE = "vzdSearchBase";
    public static final String ENV_LOAD_SENDER_ADRESSES = "loadSenderAddresses";
    public static final String ENV_LOAD_RCPT_ADRESSES = "loadRcptAddresses";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer() {
        throw new IllegalStateException("not implemented");
    }

    @Override
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer) {
        TimeMetric timeMetric = null;

        DefaultLogger logger = defaultPipelineOperationContext.getLogger();
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            List<String> addresses = (List<String>) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_ADDRESSES);
            addresses = addresses.stream().distinct().collect(Collectors.toList());

            String searchBase = (String) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_VZD_SEARCH_BASE);
            boolean loadSenderAddresses = (boolean) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_LOAD_SENDER_ADRESSES);
            boolean loadRcptAddresses = (boolean) defaultPipelineOperationContext.getEnvironmentValue(NAME, ENV_LOAD_RCPT_ADRESSES);

            if (!loadSenderAddresses && !loadRcptAddresses) {
                failConsumer.accept(defaultPipelineOperationContext, new IllegalStateException("please choose senderAddresses = true or rcptAddresses = true"));
            }
            else {
                Konfiguration konfiguration = logger.getDefaultLoggerContext().getKonfiguration();
                MailaddressCertErrorContext mailaddressCertErrorContext = logger.getDefaultLoggerContext().getMailaddressCertErrorContext();
                MailaddressKimVersionErrorContext mailaddressKimVersionErrorContext = logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext();

                DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
                timeMetric = metricFactory.timer(NAME);

                List<X509CertificateResult> result = new ArrayList<>();
                for (Iterator<String> iterator = addresses.iterator(); iterator.hasNext(); ) {
                    String address = iterator.next();
                    X509CertificateResult x509CertificateResult = new X509CertificateResult();
                    x509CertificateResult.setMailAddress(address.toLowerCase());
                    try {
                        List<VzdResult> vzdResults = VzdUtils.search(logger, searchBase, address, true, true);
                        x509CertificateResult.setVzdResults(vzdResults);

                        if (vzdResults.size() == 1 && vzdResults.get(0).getErrorCode().equals(EnumVzdErrorCode.NOT_FOUND)) {
                            x509CertificateResult.setErrorCode(EnumX509ErrorCode.NOT_FOUND);
                            TelematikIdResult telematikIdResult = new TelematikIdResult();
                            telematikIdResult.setErrorCode(EnumX509ErrorCode.NOT_FOUND);
                            x509CertificateResult.setTelematikIdResult(telematikIdResult);
                            if (loadSenderAddresses) {
                                mailaddressCertErrorContext.add(address, EnumErrorCode.CODE_X006, true);
                            } else if (loadRcptAddresses) {
                                mailaddressCertErrorContext.add(address, EnumErrorCode.CODE_X005, false);
                            }
                        } else {
                            List<byte[]> certs = new ArrayList<>();
                            for (Iterator<VzdResult> vzdResultIterator = vzdResults.iterator(); vzdResultIterator.hasNext(); ) {
                                VzdResult vzdResult = vzdResultIterator.next();
                                if (!vzdResult.getErrorCode().equals(EnumVzdErrorCode.OK)) {
                                    x509CertificateResult.setErrorCode(EnumX509ErrorCode.OTHER);
                                    break;
                                }
                                certs.addAll(vzdResult.getCertBytes());

                                if (loadSenderAddresses && StringUtils.isNewVersionHigher(konfiguration.getXkimPtShortVersion().getInnerVersion(), vzdResult.getMailResults().get(address).getVersion().getInnerVersion())) {
                                    mailaddressKimVersionErrorContext.add(x509CertificateResult, EnumErrorCode.CODE_X008, true);
                                }
                            }

                            TelematikIdResult telematikIdResult = new TelematikIdResult();
                            for (Iterator<byte[]> certsIter = certs.iterator(); certsIter.hasNext(); ) {
                                byte[] cert = certsIter.next();
                                telematikIdResult = X509CertificateUtils.extractTelematikId(cert, telematikIdResult);
                            }
                            x509CertificateResult.setTelematikIdResult(telematikIdResult);
                            x509CertificateResult.setCerts(certs);

                            if (!konnektor.isEccEncryptionAvailable()
                                    && x509CertificateResult.getErrorCode().equals(EnumX509ErrorCode.OK)
                                    && telematikIdResult.getErrorCode().equals(EnumX509ErrorCode.OK)
                            ) {
                                x509CertificateResult = CMSUtils.filterRsaCerts(x509CertificateResult);
                            }

                            if (telematikIdResult.getErrorCode().equals(EnumX509ErrorCode.MORE_THAN_ONE_TELEMATIKID)) {
                                if (loadSenderAddresses) {
                                    mailaddressCertErrorContext.add(address, EnumErrorCode.CODE_X007, true);
                                    mailaddressCertErrorContext.add(address, EnumErrorCode.CODE_4003, true);
                                } else if (loadRcptAddresses) {
                                    mailaddressCertErrorContext.add(address, EnumErrorCode.CODE_4005, false);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("error on vzd searching for: " + address + " - konnektor: " + konnektor.getIp(), e);
                        x509CertificateResult.setErrorCode(EnumX509ErrorCode.OTHER);
                        x509CertificateResult.setMailAddress(address.toLowerCase());
                        mailaddressCertErrorContext.add(address, EnumErrorCode.CODE_X004, loadSenderAddresses);
                    }
                    result.add(x509CertificateResult);
                }

                //check result
                for (Iterator<X509CertificateResult> iterator = result.iterator(); iterator.hasNext(); ) {
                    X509CertificateResult x509CertificateResult = iterator.next();

                    if (!x509CertificateResult.getErrorCode().equals(EnumX509ErrorCode.OK)
                            || !x509CertificateResult.getTelematikIdResult().getErrorCode().equals(EnumX509ErrorCode.OK)
                    ) {
                        log.error("error on loading certs for address: " + x509CertificateResult.getMailAddress());
                        throw new IllegalStateException("error on loading certs for address: " + x509CertificateResult.getMailAddress());
                    }
                    log.info(
                        "cert result found for: " + x509CertificateResult.getMailAddress()
                            + " - "
                            + x509CertificateResult.getCerts().size()
                            + " certs found - "
                            + x509CertificateResult.getRsaCerts().size()
                            + " rsa certs found - telematik id result - "
                            + x509CertificateResult.getTelematikIdResult().getTelematikId()
                            + " "
                            + x509CertificateResult.getTelematikIdResult().getErrorCode()
                    );
                    logger.logLine(
                        "cert result found for: " + x509CertificateResult.getMailAddress()
                            + " - "
                            + x509CertificateResult.getCerts().size()
                            + " certs found - "
                            + x509CertificateResult.getRsaCerts().size()
                            + " rsa certs found - telematik id result - "
                            + x509CertificateResult.getTelematikIdResult().getTelematikId()
                            + " "
                            + x509CertificateResult.getTelematikIdResult().getErrorCode()
                    );
                }
                if (result.isEmpty()) {
                    log.error("error on loading certs - no x509 certificate result: " + konnektor.getIp() + " - " + String.join(",", addresses));
                    throw new IllegalStateException("error on loading certs - no x509 certificate result: " + konnektor.getIp() + " - " + String.join(",", addresses));
                }

                defaultPipelineOperationContext.setEnvironmentValue(NAME, ENV_VZD_CERTS, result);
                timeMetric.stopAndPublish();

                okConsumer.accept(defaultPipelineOperationContext);
            }
        } catch (Exception e) {
            log.error("error on executing the LoadVzdCertsOperation for the konnektor: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            failConsumer.accept(defaultPipelineOperationContext, e);
        }
    }
}
