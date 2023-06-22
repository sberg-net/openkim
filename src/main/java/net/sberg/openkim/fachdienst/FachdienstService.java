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
package net.sberg.openkim.fachdienst;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.gematik.kim.kas.ApiClient;
import de.gematik.kim.kas.api.AttachmentsApi;
import net.sberg.openkim.konfiguration.EnumTIEnvironment;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.PipelineService;
import net.sberg.openkim.pipeline.operation.DefaultPipelineOperationContext;
import net.sberg.openkim.pipeline.operation.konnektor.dns.DnsFqdnRequestOperation;
import net.sberg.openkim.pipeline.operation.konnektor.dns.DnsRequestOperation;
import net.sberg.openkim.pipeline.operation.konnektor.dns.DnsResult;
import net.sberg.openkim.pipeline.operation.konnektor.dns.DnsResultContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.xbill.DNS.Type;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FachdienstService {

    private static final Logger log = LoggerFactory.getLogger(FachdienstService.class);

    private static final String ACCMGR_PTR_SEARCH = "_accmgr._tcp.kim.telematik";
    private static final String KAS_PTR_SEARCH = "_kas._tcp.kim.telematik";

    private TreeMap<EnumFachdienst, FachdienstDescr> descrMap;

    @Autowired
    private PipelineService pipelineService;

    @PostConstruct
    public void init() throws Exception {
        descrMap = new ObjectMapper().readValue(getClass().getResourceAsStream("/fachdienst_descriptors.json"), new TypeReference<TreeMap<EnumFachdienst, FachdienstDescr>>() {
        });
    }

    public List<Fachdienst> create(DefaultLogger logger) throws Exception {

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        List<Fachdienst> result = new ArrayList<>();
        EnumFachdienst[] typen = EnumFachdienst.values();
        for (int i = 0; i < typen.length; i++) {

            logger.logLine("create beginning for konnektor: " + konnektor.getIp() + " and fachdienst: " + typen[i].name());
            log.info("create beginning for konnektor: " + konnektor.getIp() + " and fachdienst: " + typen[i].name());

            Fachdienst fachdienst = new Fachdienst();

            //fqdn request
            try {
                fachdienst.setTyp(typen[i]);
                fachdienst = fqdnRequest(logger, fachdienst, konnektor.getTiEnvironment());
            } catch (Exception e) {
                log.error("error on creating fachdienst (fqdnRequest): " + typen[i].name() + " for konnektor: " + konnektor.getIp(), e);
                logger.logLine("error on creating fachdienst (fqdnRequest): " + typen[i].name() + " for konnektor: " + konnektor.getIp());
                fachdienst.setErrorOnCreating(true);
            }

            //a-type request
            try {
                if (konnektor.getTiEnvironment().equals(EnumTIEnvironment.PU)) {
                    String ipAddress = aRequest(logger, konnektor, fachdienst.getTyp().getDomain());
                    fachdienst.setSmtpDomain(fachdienst.getTyp().getDomain());
                    fachdienst.setPop3Domain(fachdienst.getTyp().getDomain());
                    if (ipAddress == null) {
                        fachdienst.setErrorOnCreating(true);
                    } else {
                        fachdienst.setSmtpIpAddress(ipAddress);
                        fachdienst.setPop3IpAddress(ipAddress);
                    }
                } else {
                    fachdienst = create(logger, fachdienst, EnumFachdienstDomainDescrId.SMTP, konnektor.getTiEnvironment());
                    fachdienst = create(logger, fachdienst, EnumFachdienstDomainDescrId.POP3, konnektor.getTiEnvironment());
                }
            } catch (Exception e) {
                log.error("error on creating fachdienst (aRequest): " + typen[i].name() + " for konnektor: " + konnektor.getIp(), e);
                logger.logLine("error on creating fachdienst (aRequest): " + typen[i].name() + " for konnektor: " + konnektor.getIp());
                fachdienst.setErrorOnCreating(true);
            }

            result.add(fachdienst);

            logger.logLine("create finishing for konnektor: " + konnektor.getIp() + " and fachdienst: " + typen[i].name());
            log.info("create finishing for konnektor: " + konnektor.getIp() + " and fachdienst: " + typen[i].name());
        }

        return result;
    }

    private void buildKasApi(Fachdienst fachdienst, Konfiguration konfiguration) throws Exception {
        HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        httpRequestFactory.setConnectionRequestTimeout(konfiguration.getFachdienstKasTimeOutInSeconds() * 1000);
        httpRequestFactory.setConnectTimeout(konfiguration.getFachdienstKasTimeOutInSeconds() * 1000);
        RestTemplate restTemplate = new RestTemplate(httpRequestFactory);

        ApiClient apiClient = new ApiClient(restTemplate);
        apiClient.setBasePath("https://" + fachdienst.getKasIpAddress() + ":" + fachdienst.getKasPort() + fachdienst.getKasContextPath() + "/attachments/v2.2");
        AttachmentsApi attachmentsApi = new AttachmentsApi();
        attachmentsApi.setApiClient(apiClient);
        fachdienst.setAttachmentsApi(attachmentsApi);
    }

    private String aRequest(DefaultLogger logger, Konnektor konnektor, String domain) throws Exception {

        AtomicInteger failedCounter = new AtomicInteger();
        DnsRequestOperation dnsRequestOperation = (DnsRequestOperation) pipelineService.getOperation(DnsRequestOperation.BUILTIN_VENDOR+"."+DnsRequestOperation.NAME);
        DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
        defaultPipelineOperationContext.setEnvironmentValue(DnsRequestOperation.NAME, DnsRequestOperation.ENV_DOMAIN, domain);
        defaultPipelineOperationContext.setEnvironmentValue(DnsRequestOperation.NAME, DnsRequestOperation.ENV_RECORD_TYPE, Type.string(Type.A));

        dnsRequestOperation.execute(
            defaultPipelineOperationContext,
            context -> {
                log.info("dns request finished for: " + domain);
            },
            (context, e) -> {
                log.error("dns request failed for: " + domain, e);
                failedCounter.incrementAndGet();
            }
        );

        List<DnsResult> result = null;
        DnsResultContainer dnsResultContainer = (DnsResultContainer) defaultPipelineOperationContext.getEnvironmentValue(DnsRequestOperation.NAME, DnsRequestOperation.ENV_DNS_RESULT);
        if (failedCounter.get() > 0 || dnsResultContainer == null || dnsResultContainer.isError()) {
            throw new IllegalStateException("ip-address for domain " + domain + " not found");
        }
        if (!dnsResultContainer.isError()) {
            result = dnsResultContainer.getResult();
        }

        if (result.isEmpty()) {
            log.info("empty result -> error on aRequest " + domain + " for konnektor: " + konnektor.getIp());
            logger.logLine("empty result -> error on aRequest " + domain + " for konnektor: " + konnektor.getIp());
        } else {
            DnsResult dnsResult = result.get(0);
            return dnsResult.getAddress();
        }

        return null;
    }

    private Fachdienst fqdnRequest(DefaultLogger logger, Konnektor konnektor, Fachdienst fachdienst, String domain) throws Exception {

        AtomicInteger failedCounter = new AtomicInteger();

        DnsFqdnRequestOperation dnsFqdnRequestOperation = (DnsFqdnRequestOperation) pipelineService.getOperation(DnsFqdnRequestOperation.BUILTIN_VENDOR+"."+DnsFqdnRequestOperation.NAME);
        DefaultPipelineOperationContext defaultPipelineOperationContext = new DefaultPipelineOperationContext(logger);
        defaultPipelineOperationContext.setEnvironmentValue(DnsFqdnRequestOperation.NAME, DnsFqdnRequestOperation.ENV_DOMAIN, domain);
        defaultPipelineOperationContext.setEnvironmentValue(DnsFqdnRequestOperation.NAME, DnsFqdnRequestOperation.ENV_PTR_DOMAIN_SUFFIX, fachdienst.getTyp().getDomainSuffix());

        dnsFqdnRequestOperation.execute(
            defaultPipelineOperationContext,
            context -> {
                log.info("dns request finished for: " + domain);
            },
            (context, e) -> {
                log.error("dns request failed for: " + domain, e);
                failedCounter.incrementAndGet();
            }
        );

        DnsResultContainer dnsResultContainer = (DnsResultContainer) defaultPipelineOperationContext.getEnvironmentValue(DnsFqdnRequestOperation.NAME, DnsFqdnRequestOperation.ENV_DNS_RESULT);
        if (failedCounter.get() > 0 || dnsResultContainer == null || dnsResultContainer.isError()) {
            fachdienst.setErrorOnCreating(true);
            return fachdienst;
        }

        List<DnsResult> result = dnsResultContainer.getResult();
        if (result.isEmpty()) {
            log.info("empty result -> error on fqdnRequest " + domain + " for konnektor: " + konnektor.getIp() + " and fachdienst: " + fachdienst.getTyp().getDomain());
            logger.logLine("empty result -> error on fqdnRequest " + domain + " for konnektor: " + konnektor.getIp() + " and fachdienst: " + fachdienst.getTyp().getDomain());
        } else if (result.size() != 1) {
            log.info("more than one result -> error on fqdnRequest " + domain + " for konnektor: " + konnektor.getIp() + " and fachdienst: " + fachdienst.getTyp().getDomain());
            logger.logLine("more than one result -> error on fqdnRequest " + domain + " for konnektor: " + konnektor.getIp() + " and fachdienst: " + fachdienst.getTyp().getDomain());
        } else {
            DnsResult dnsResult = result.get(0);

            if (domain.equals(KAS_PTR_SEARCH)) {
                fachdienst.setKasDomain(dnsResult.getAddress().split(":")[0]);
                fachdienst.setKasPort(dnsResult.getAddress().split(":")[1]);
                fachdienst.setKasContextPath("");

                String ipAddress = aRequest(logger, konnektor, fachdienst.getKasDomain());
                if (ipAddress == null) {
                    fachdienst.setErrorOnCreating(true);
                } else {
                    fachdienst.setKasIpAddress(ipAddress);
                    buildKasApi(fachdienst, logger.getDefaultLoggerContext().getKonfiguration());
                    fachdienst.setKasInitialized(true);
                }
            } else {
                fachdienst.setAccmgrDomain(dnsResult.getAddress().split(":")[0]);
                fachdienst.setAccmgrPort(dnsResult.getAddress().split(":")[1]);
                fachdienst.setAccmgrContextPath("");

                String ipAddress = aRequest(logger, konnektor, fachdienst.getAccmgrDomain());
                if (ipAddress == null) {
                    fachdienst.setErrorOnCreating(true);
                } else {
                    fachdienst.setAccmgrIpAddress(ipAddress);
                    fachdienst.setAccmgrInitialized(true);
                }
            }
        }
        return fachdienst;
    }

    private Fachdienst fqdnRequest(DefaultLogger logger, Fachdienst fachdienst, EnumTIEnvironment tiEnvironment) throws Exception {
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {
            if (tiEnvironment.equals(EnumTIEnvironment.PU)) {
                fachdienst = fqdnRequest(logger, konnektor, fachdienst, ACCMGR_PTR_SEARCH);
                fachdienst = fqdnRequest(logger, konnektor, fachdienst, KAS_PTR_SEARCH);
            } else {
                fachdienst = create(logger, fachdienst, EnumFachdienstDomainDescrId.KAS, tiEnvironment);
                fachdienst = create(logger, fachdienst, EnumFachdienstDomainDescrId.AM, tiEnvironment);
            }
            return fachdienst;
        } catch (Exception e) {
            log.error("error on fqdnRequest for konnektor: " + konnektor.getIp() + " and fachdienst: " + fachdienst.getTyp().getDomain(), e);
            throw e;
        }
    }

    private Fachdienst create(DefaultLogger logger, Fachdienst fachdienst, EnumFachdienstDomainDescrId fachdienstDomainDescrId, EnumTIEnvironment tiEnvironment) throws Exception {
        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();
        Konfiguration konfiguration = logger.getDefaultLoggerContext().getKonfiguration();

        List<FachdienstDomainDescr> descr = descrMap.get(fachdienst.getTyp()).extractDomainDescr(fachdienstDomainDescrId, tiEnvironment);
        if (descr.isEmpty()) {
            fachdienst.setErrorOnCreating(true);
            logger.logLine("no descr available for " + fachdienst.getTyp() + " - " + fachdienstDomainDescrId + " - " + tiEnvironment);
        } else {
            logger.logLine("descr available for " + fachdienst.getTyp() + " - " + fachdienstDomainDescrId + " - " + tiEnvironment);
            for (Iterator<FachdienstDomainDescr> iterator = descr.iterator(); iterator.hasNext(); ) {
                FachdienstDomainDescr fachdienstDomainDescr = iterator.next();

                if (fachdienstDomainDescrId.equals(EnumFachdienstDomainDescrId.AM)) {
                    fachdienst.setAccmgrDomain(fachdienstDomainDescr.getDomain());
                    fachdienst.setAccmgrPort(fachdienstDomainDescr.getPort());
                    fachdienst.setAccmgrContextPath(fachdienstDomainDescr.getContextpath());
                    String ipAddress = aRequest(logger, konnektor, fachdienstDomainDescr.getDomain());
                    if (ipAddress == null) {
                        fachdienst.setErrorOnCreating(true);
                    } else {
                        fachdienst.setAccmgrInitialized(true);
                        fachdienst.setAccmgrIpAddress(ipAddress);
                        break;
                    }
                } else if (fachdienstDomainDescrId.equals(EnumFachdienstDomainDescrId.KAS)) {
                    fachdienst.setKasDomain(fachdienstDomainDescr.getDomain());
                    fachdienst.setKasPort(fachdienstDomainDescr.getPort());
                    fachdienst.setKasContextPath(fachdienstDomainDescr.getContextpath());
                    String ipAddress = aRequest(logger, konnektor, fachdienstDomainDescr.getDomain());
                    if (ipAddress == null) {
                        fachdienst.setErrorOnCreating(true);
                    } else {
                        fachdienst.setKasInitialized(true);
                        fachdienst.setKasIpAddress(ipAddress);
                        buildKasApi(fachdienst, konfiguration);
                        break;
                    }
                } else if (fachdienstDomainDescrId.equals(EnumFachdienstDomainDescrId.SMTP)) {
                    fachdienst.setSmtpDomain(fachdienstDomainDescr.getDomain());
                    String ipAddress = aRequest(logger, konnektor, fachdienstDomainDescr.getDomain());
                    if (ipAddress == null) {
                        fachdienst.setErrorOnCreating(true);
                    } else {
                        fachdienst.setSmtpIpAddress(ipAddress);
                        break;
                    }
                } else if (fachdienstDomainDescrId.equals(EnumFachdienstDomainDescrId.POP3)) {
                    fachdienst.setPop3Domain(fachdienstDomainDescr.getDomain());
                    String ipAddress = aRequest(logger, konnektor, fachdienstDomainDescr.getDomain());
                    if (ipAddress == null) {
                        fachdienst.setErrorOnCreating(true);
                    } else {
                        fachdienst.setPop3IpAddress(ipAddress);
                        break;
                    }
                }
            }
        }
        return fachdienst;
    }
}
