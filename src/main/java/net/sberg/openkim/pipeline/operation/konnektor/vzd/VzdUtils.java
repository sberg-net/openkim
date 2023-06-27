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

import net.sberg.openkim.common.CommonBuilderFactory;
import net.sberg.openkim.common.ICommonConstants;
import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.EnumKonnektorAuthMethod;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.entry.DefaultAttribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.api.DefaultLdapConnectionFactory;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapConnectionConfig;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class VzdUtils {

    private static final Logger log = LoggerFactory.getLogger(VzdUtils.class);

    private static final String LDAP_CN_ATTR = "cn";
    private static final String LDAP_SN_ATTR = "sn";
    private static final String LDAP_DISPLAYNAME_ATTR = "displayName";
    private static final String LDAP_MAIL_ATTR = "mail";
    private static final String LDAP_KOMLEDATA_ATTR = "komLeData";
    private static final String LDAP_GIVENNAME_ATTR = "givenName";
    private static final String LDAP_UID_ATTR = "uid";
    private static final String LDAP_PERSONALENTRY_ATTR = "personalEntry";
    private static final String LDAP_CHANGEDATETIME_ATTR = "changeDateTime";
    private static final String LDAP_COUNTRYCODE_ATTR = "countryCode";
    private static final String LDAP_DATEFROMAUTHORITY_ATTR = "dataFromAuthority";
    private static final String LDAP_DOMAINID_ATTR = "domainID";
    private static final String LDAP_ENTRYTYPE_ATTR = "entryType";
    private static final String LDAP_l_ATTR = "l";
    private static final String LDAP_ORGANIZATION_ATTR = "organization";
    private static final String LDAP_OTHERNAME_ATTR = "otherName";
    private static final String LDAP_POSTALCODE_ATTR = "postalCode";
    private static final String LDAP_PROFESSIONOID_ATTR = "professionOID";
    private static final String LDAP_SPECIALIZATION_ATTR = "specialization";
    private static final String LDAP_ST_ATTR = "st";
    private static final String LDAP_STREET_ATTR = "street";
    private static final String LDAP_TELEMATIKID_ATTR = "telematikID";
    private static final String LDAP_TITLE_ATTR = "title";
    private static final String LDAP_CERT_ATTR = "userCertificate;binary";

    private static final String SEARCH_TEMPLATE = "(|(" + LDAP_CN_ATTR + "=*{0}*)(" + LDAP_SN_ATTR + "=*{1}*)(" + LDAP_DISPLAYNAME_ATTR + "=*{2}*)(" + LDAP_MAIL_ATTR + "=*{3}*)(" + LDAP_GIVENNAME_ATTR + "=*{4}*)(" + LDAP_TELEMATIKID_ATTR + "=*{5}*))";
    private static final String SEARCH_MAIL_TEMPLATE = "(" + LDAP_MAIL_ATTR + "={0})";

    private static final VzdResult set(VzdResult vzdResult, String property, Entry entry, CertificateFactory factory) throws Exception {
        if (entry.get(property) != null) {
            DefaultAttribute attribute = (DefaultAttribute) entry.get(property);
            if (property.equals(LDAP_KOMLEDATA_ATTR)) {
                vzdResult.setMailResults(new HashMap<>());
                for (Iterator<org.apache.directory.api.ldap.model.entry.Value> iterator = attribute.iterator(); iterator.hasNext(); ) {
                    org.apache.directory.api.ldap.model.entry.Value val = iterator.next();
                    String valStr = val.getString();
                    //1.5+,Hansekrone@akquinet.kim.telematik
                    String[] arr = valStr.split(",");
                    VzdMailResult mailResult = new VzdMailResult();
                    mailResult.setMailAddress(arr[1].toLowerCase());
                    mailResult.setVersion(EnumKomLeVersion.get(arr[0]));
                    vzdResult.getMailResults().put(mailResult.getMailAddress(), mailResult);
                }
            }
            else if (property.equals(LDAP_CERT_ATTR)) {
                StringBuilder contentBuilder = new StringBuilder();
                for (Iterator<Value> iterator = attribute.iterator(); iterator.hasNext(); ) {
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
            } else {
                PropertyUtils.setProperty(vzdResult, property, attribute.get().getString());
            }
        } else {
            if (property.equals(LDAP_KOMLEDATA_ATTR)) {
                vzdResult.setMailResults(new HashMap<>());
            } else if (property.equals(LDAP_CERT_ATTR)) {
                vzdResult.setCertBytes(new ArrayList<>());
                vzdResult.setCertSummary("");
            } else {
                PropertyUtils.setProperty(vzdResult, property, "");
            }
        }
        return vzdResult;
    }

    private static final LdapConnectionConfig createConfig(Konnektor konnektor) throws Exception {
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

    protected static final List<VzdResult> search(
            DefaultLogger logger,
            String base,
            String searchValue,
            boolean onlySearchMailAttr,
            boolean resultWithCertificates
    ) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        LdapConnection ldapConnection = null;

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("Vzd:search");

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
                    vzdResult = set(vzdResult, LDAP_KOMLEDATA_ATTR, entry, factory);
                    vzdResult = set(vzdResult, LDAP_l_ATTR, entry, factory);
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
}
