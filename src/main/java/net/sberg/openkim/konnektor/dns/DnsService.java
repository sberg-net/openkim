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
package net.sberg.openkim.konnektor.dns;

import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class DnsService {

    private static final Logger log = LoggerFactory.getLogger(DnsService.class);

    public DnsResultContainer request(DefaultLogger logger, String domain, String recordType) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        DnsResultContainer dnsResultContainer = new DnsResultContainer();

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("DnsService:request");
            logger.logLine("request for: " + konnektor.getIp() + " - " + domain + " - " + recordType);

            Name name = new Name(domain);
            Lookup lookup = new Lookup(name, Type.value(recordType), DClass.IN);
            Resolver resolver = new ExtendedResolver(new String[]{konnektor.getIp()});
            resolver.setTimeout(Duration.ofSeconds(konnektor.getTimeoutInSeconds()));
            lookup.setResolver(resolver);
            Record[] records = lookup.run();

            dnsResultContainer.setError(
                lookup.getErrorString() != null
                && !lookup.getErrorString().equals("successful")
                && !lookup.getErrorString().trim().isEmpty()
            );

            List<DnsResult> result = new ArrayList<>();

            if (records == null || records.length == 0) {
                logger.logLine("no results");
            } else {
                for (int i = 0; i < records.length; i++) {
                    DnsResult dnsResult = new DnsResult();

                    if (Type.value(recordType) == Type.A) {
                        dnsResult.setAddress(((ARecord) records[i]).getAddress().getHostAddress());
                    } else if (Type.value(recordType) == Type.PTR) {
                        dnsResult.setAddress(((PTRRecord) records[i]).getTarget().toString().substring(0, ((PTRRecord) records[i]).getTarget().toString().length() - 1));
                    } else if (Type.value(recordType) == Type.SRV) {
                        dnsResult.setAddress(((SRVRecord) records[i]).getTarget().toString().substring(0, ((SRVRecord) records[i]).getTarget().toString().length() - 1) + ":" + ((SRVRecord) records[i]).getPort());
                    }

                    dnsResult.setTtl(records[i].getTTL());
                    dnsResult.setName(records[i].getName().toString());
                    dnsResult.setType(records[i].getType());

                    logger.logLine(dnsResult.toString());

                    result.add(dnsResult);
                }
            }

            timeMetric.stopAndPublish();

            dnsResultContainer.setResult(result);
            return dnsResultContainer;
        } catch (Exception e) {
            log.error("error on request dns service: " + konnektor.getIp() + " - " + domain + " - " + recordType, e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            dnsResultContainer.setError(true);
            return dnsResultContainer;
        }
    }

    public DnsResultContainer fqdnRequest(DefaultLogger logger, String domain, String ptrDomainSuffix) throws Exception {
        TimeMetric timeMetric = null;

        Konnektor konnektor = logger.getDefaultLoggerContext().getKonnektor();

        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("DnsService:request");
            logger.logLine("fqdnRequest for: " + konnektor.getIp() + " - " + domain);

            List<DnsResult> srvResults = new ArrayList<>();

            DnsResultContainer dnsResultContainer = request(logger, domain, Type.string(Type.PTR));
            if (dnsResultContainer.isError()) {
                timeMetric.stopAndPublish();
                return dnsResultContainer;
            }
            List<DnsResult> ptrResults = dnsResultContainer.getResult();

            for (Iterator<DnsResult> iterator = ptrResults.iterator(); iterator.hasNext(); ) {
                DnsResult ptrResult = iterator.next();
                if (ptrResult.getAddress().toLowerCase().endsWith(ptrDomainSuffix.toLowerCase())) {
                    dnsResultContainer = request(logger, ptrResult.getAddress(), Type.string(Type.SRV));
                    if (dnsResultContainer.isError()) {
                        timeMetric.stopAndPublish();
                        return dnsResultContainer;
                    }
                    srvResults.addAll(dnsResultContainer.getResult());
                }
            }

            timeMetric.stopAndPublish();

            DnsResultContainer resultContainer = new DnsResultContainer();
            resultContainer.setResult(srvResults);
            return resultContainer;
        } catch (Exception e) {
            log.error("error on fqdnRequest: " + konnektor.getIp() + " - " + domain, e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            throw e;
        }
    }

}
