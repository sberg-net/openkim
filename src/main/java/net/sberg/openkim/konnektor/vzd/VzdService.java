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
package net.sberg.openkim.konnektor.vzd;

import net.sberg.openkim.common.CommonBuilderFactory;
import net.sberg.openkim.common.ICommonConstants;
import net.sberg.openkim.common.StringUtils;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.common.x509.*;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konnektor.EnumKonnektorAuthMethod;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.error.EnumErrorCode;
import net.sberg.openkim.log.error.MailaddressCertErrorContext;
import net.sberg.openkim.log.error.MailaddressKimVersionErrorContext;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.entry.DefaultAttribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.api.DefaultLdapConnectionFactory;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.Timestamp;
import java.text.MessageFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class VzdService {

    private static final Logger log = LoggerFactory.getLogger(VzdService.class);

    protected static final String LDAP_CN_ATTR = "cn";
    protected static final String LDAP_SN_ATTR = "sn";
    protected static final String LDAP_DISPLAYNAME_ATTR = "displayName";
    protected static final String LDAP_MAIL_ATTR = "mail";
    protected static final String LDAP_GIVENNAME_ATTR = "givenName";
    protected static final String LDAP_KOMLEVERSION_ATTR = "KOM-LE-Version";
    protected static final String LDAP_UID_ATTR = "uid";
    protected static final String LDAP_PERSONALENTRY_ATTR = "personalEntry";
    protected static final String LDAP_CHANGEDATETIME_ATTR = "changeDateTime";
    protected static final String LDAP_COUNTRYCODE_ATTR = "countryCode";
    protected static final String LDAP_DATEFROMAUTHORITY_ATTR = "dataFromAuthority";
    protected static final String LDAP_DOMAINID_ATTR = "domainID";
    protected static final String LDAP_ENTRYTYPE_ATTR = "entryType";
    protected static final String LDAP_l_ATTR = "l";
    protected static final String LDAP_ORGANIZATION_ATTR = "organization";
    protected static final String LDAP_OTHERNAME_ATTR = "otherName";
    protected static final String LDAP_POSTALCODE_ATTR = "postalCode";
    protected static final String LDAP_PROFESSIONOID_ATTR = "professionOID";
    protected static final String LDAP_SPECIALIZATION_ATTR = "specialization";
    protected static final String LDAP_ST_ATTR = "st";
    protected static final String LDAP_STREET_ATTR = "street";
    protected static final String LDAP_TELEMATIKID_ATTR = "telematikID";
    protected static final String LDAP_TITLE_ATTR = "title";
    protected static final String LDAP_CERT_ATTR = "userCertificate;binary";

    private static final String SEARCH_TEMPLATE = "(|(" + LDAP_CN_ATTR + "=*{0}*)(" + LDAP_SN_ATTR + "=*{1}*)(" + LDAP_DISPLAYNAME_ATTR + "=*{2}*)(" + LDAP_MAIL_ATTR + "=*{3}*)(" + LDAP_GIVENNAME_ATTR + "=*{4}*)(" + LDAP_TELEMATIKID_ATTR + "=*{5}*))";
    private static final String SEARCH_MAIL_TEMPLATE = "(" + LDAP_MAIL_ATTR + "={0})";

    @Value("${spring.ldap.base}")
    private String base;

    private VzdResult set(VzdResult vzdResult, String property, Entry entry, CertificateFactory factory) throws Exception {
        if (entry.get(property) != null) {
            DefaultAttribute attribute = (DefaultAttribute) entry.get(property);
            if (property.equals(LDAP_KOMLEVERSION_ATTR) && attribute.get().getString() != null && !attribute.get().getString().trim().isEmpty()) {
                vzdResult.setKomleVersion(attribute.get().getString());
            } else if (property.equals(LDAP_CERT_ATTR)) {
                StringBuilder contentBuilder = new StringBuilder();
                for (Iterator<org.apache.directory.api.ldap.model.entry.Value> iterator = attribute.iterator(); iterator.hasNext(); ) {
                    org.apache.directory.api.ldap.model.entry.Value val = iterator.next();
                    byte[] certBytes = val.getBytes();
                    X509Certificate cert = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
                    contentBuilder.append("*****************************<br/>");
                    contentBuilder.append("Aussteller = ").append(cert.getIssuerDN().getName()).append("<br/>");
                    contentBuilder.append("Inhaber = ").append(cert.getSubjectDN().getName()).append("<br/>");
                    contentBuilder.append("Seriennummer = ").append(cert.getSerialNumber().toString()).append("<br/>");
                    contentBuilder.append("Version = ").append(cert.getVersion()).append("<br/>");
                    contentBuilder.append("Gültig von = ").append(DateTimeFormatter.ofPattern("dd.MM.yyyy hh:mm:ss").format(new Timestamp(cert.getNotBefore().getTime()).toLocalDateTime())).append("<br/>");
                    contentBuilder.append("Gültig bis = ").append(DateTimeFormatter.ofPattern("dd.MM.yyyy hh:mm:ss").format(new Timestamp(cert.getNotAfter().getTime()).toLocalDateTime())).append("<br/>");
                    vzdResult.getCerts().add(cert);
                    vzdResult.getCertBytes().add(certBytes);
                }
                vzdResult.setCertSummary(contentBuilder.toString());
            } else if (property.equals(LDAP_MAIL_ATTR)) {
                vzdResult.setMails(new ArrayList<>());
                for (Iterator<org.apache.directory.api.ldap.model.entry.Value> iterator = attribute.iterator(); iterator.hasNext(); ) {
                    org.apache.directory.api.ldap.model.entry.Value val = iterator.next();
                    vzdResult.getMails().add(val.getString());
                }
            } else {
                PropertyUtils.setProperty(vzdResult, property, attribute.get().getString());
            }
        } else {
            if (property.equals(LDAP_KOMLEVERSION_ATTR)) {
                vzdResult.setKomleVersion("");
            } else if (property.equals(LDAP_MAIL_ATTR)) {
                vzdResult.setMails(new ArrayList<>());
            } else {
                PropertyUtils.setProperty(vzdResult, property, "");
            }
        }
        return vzdResult;
    }

    private LdapConnectionConfig createConfig(Konnektor konnektor) throws Exception {
        LdapConnectionConfig config = new LdapConnectionConfig();
        config.setLdapHost(konnektor.getIp());
        config.setLdapPort(636);
        config.setTimeout(konnektor.getTimeoutInSeconds() * 1000L);
        config.setUseSsl(true);

        //set keystore
        if (konnektor.getKonnektorAuthMethod().equals(EnumKonnektorAuthMethod.CERT)) {
            config.setKeyManagers(new CommonBuilderFactory().createKeyManager(konnektor));
        }

        //set trustore
        File truststoreFile = new File(MessageFormat.format(ICommonConstants.KONNEKTOR_TRUSTORE_JKS, konnektor.getUuid()));
        if (truststoreFile.exists()) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(truststoreFile), ICommonConstants.KONNEKTOR_TRUSTORE_JKS_PWD.toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            TrustManager[] tms = tmf.getTrustManagers();
            config.setTrustManagers(tms);
        }
        return config;
    }

    public List<VzdResult> search(
        DefaultLogger logger,
        String searchValue,
        boolean onlySearchMailAttr,
        boolean resultWithCertificates
    ) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        LdapConnection ldapConnection = null;

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("VzdService:search");

            List<VzdResult> vzdResults = new ArrayList<>();
            CertificateFactory factory = CertificateFactory.getInstance("X.509");

            DefaultLdapConnectionFactory ldapConnectionFactory = new DefaultLdapConnectionFactory(createConfig(konnektor));
            ldapConnection = ldapConnectionFactory.newLdapConnection();
            ldapConnection.bind();

            String searchStr =
                onlySearchMailAttr
                    ? MessageFormat.format(SEARCH_MAIL_TEMPLATE, searchValue)
                    : MessageFormat.format(SEARCH_TEMPLATE, searchValue, searchValue, searchValue, searchValue, searchValue, searchValue);
            EntryCursor cursor = ldapConnection.search(base, searchStr, SearchScope.SUBTREE);
            while (cursor.next()) {
                Entry entry = cursor.get();
                VzdResult vzdResult = new VzdResult();
                try {
                    vzdResult = set(vzdResult, LDAP_CN_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_CHANGEDATETIME_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_COUNTRYCODE_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_DISPLAYNAME_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_DATEFROMAUTHORITY_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_DOMAINID_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_ENTRYTYPE_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_GIVENNAME_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_KOMLEVERSION_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_l_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_MAIL_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_ORGANIZATION_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_OTHERNAME_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_PERSONALENTRY_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_POSTALCODE_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_PROFESSIONOID_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_SN_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_SPECIALIZATION_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_ST_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_STREET_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_TELEMATIKID_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_TITLE_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_UID_ATTR, entry, factory);
                    if (resultWithCertificates) {
                        vzdResult = set(vzdResult, LDAP_CERT_ATTR, entry, factory);
                    }
                } catch (Exception e) {
                    log.error("error on search ldap vzd - handle one entry: " + searchValue, e);
                    vzdResult.setErrorCode(EnumVzdErrorCode.OTHER);
                }
                vzdResults.add(vzdResult);
            }

            if (vzdResults.isEmpty()) {
                VzdResult vzdResult = new VzdResult();
                vzdResult.setErrorCode(EnumVzdErrorCode.NOT_FOUND);
                vzdResults.add(vzdResult);
            }

            timeMetric.stopAndPublish();
            return vzdResults;
        } catch (Exception e) {
            log.error("error on search ldap vzd: " + searchValue, e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        } finally {
            if (ldapConnection != null) {
                ldapConnection.unBind();
                ldapConnection.close();
            }
        }
    }

    public List<X509CertificateResult> loadCerts(
        DefaultLogger logger,
        List<String> addresses,
        boolean senderAddresses,
        boolean rcptAddresses
    ) throws Exception {

        if (!senderAddresses && !rcptAddresses) {
            throw new IllegalStateException("please choose senderAddresses = true or rcptAddresses = true");
        }

        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();
        Konfiguration konfiguration = logger.getDefaultLoggerContext().getKonfiguration();
        MailaddressCertErrorContext mailaddressCertErrorContext = logger.getDefaultLoggerContext().getMailaddressCertErrorContext();
        MailaddressKimVersionErrorContext mailaddressKimVersionErrorContext = logger.getDefaultLoggerContext().getMailaddressKimVersionErrorContext();

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("VzdService:loadCerts");

            List<X509CertificateResult> result = new ArrayList<>();
            for (Iterator<String> iterator = addresses.iterator(); iterator.hasNext(); ) {
                String address = iterator.next();
                X509CertificateResult x509CertificateResult = new X509CertificateResult();
                x509CertificateResult.setMailAddress(address);
                try {
                    List<VzdResult> vzdResults = search(logger, address, true, true);
                    x509CertificateResult.setVzdResults(vzdResults);

                    if (vzdResults.size() == 1 && vzdResults.get(0).getErrorCode().equals(EnumVzdErrorCode.NOT_FOUND)) {
                        x509CertificateResult.setErrorCode(EnumX509ErrorCode.NOT_FOUND);
                        TelematikIdResult telematikIdResult = new TelematikIdResult();
                        telematikIdResult.setErrorCode(EnumX509ErrorCode.NOT_FOUND);
                        x509CertificateResult.setTelematikIdResult(telematikIdResult);
                        if (senderAddresses) {
                            mailaddressCertErrorContext.add(address, EnumErrorCode.CODE_X006, true);
                        } else if (rcptAddresses) {
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

                            if (senderAddresses && StringUtils.isNewVersionHigher(konfiguration.getXkimPtShortVersion(), vzdResult.getKomleVersion())) {
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
                            if (senderAddresses) {
                                mailaddressCertErrorContext.add(address, EnumErrorCode.CODE_X007, true);
                                mailaddressCertErrorContext.add(address, EnumErrorCode.CODE_4003, true);
                            } else if (rcptAddresses) {
                                mailaddressCertErrorContext.add(address, EnumErrorCode.CODE_4005, false);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("error on vzd searching for: " + address + " - konnektor: " + konnektor.getIp(), e);
                    x509CertificateResult.setErrorCode(EnumX509ErrorCode.OTHER);
                    x509CertificateResult.setMailAddress(address);
                    mailaddressCertErrorContext.add(address, EnumErrorCode.CODE_X004, senderAddresses);
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

            timeMetric.stopAndPublish();
            return result;
        } catch (Exception e) {
            log.error("error on search ldap vzd (mailaddresses) for the konnektor " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }
}
