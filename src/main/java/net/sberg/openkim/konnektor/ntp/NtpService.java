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
package net.sberg.openkim.konnektor.ntp;

import net.sberg.openkim.common.metrics.DefaultMetricFactory;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.pipeline.operation.konnektor.ntp.NtpResult;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import org.apache.james.metrics.api.TimeMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.Date;

@Service
public class NtpService {

    private static final Logger log = LoggerFactory.getLogger(NtpService.class);

    public NtpResult request(DefaultLogger logger, Konnektor konnektor) throws Exception {
        TimeMetric timeMetric = null;
        NTPUDPClient timeClient = null;
        try {

            DefaultMetricFactory metricFactory = new DefaultMetricFactory(logger);
            timeMetric = metricFactory.timer("NtpService:request");

            timeClient = new NTPUDPClient();
            timeClient.setDefaultTimeout(2000);
            InetAddress inetAddress = InetAddress.getByName(konnektor.getIp());

            TimeInfo timeInfo = timeClient.getTime(inetAddress);
            long returnTime = timeInfo.getReturnTime();
            Date konnektorTime = new Date(returnTime);

            timeInfo.computeDetails();
            Date systemTime = new Date(System.currentTimeMillis() + timeInfo.getOffset());

            NtpResult ntpResult = new NtpResult();
            ntpResult.setKonnektorTime(konnektorTime);
            ntpResult.setSystemTime(systemTime);

            timeMetric.stopAndPublish();
            timeClient.close();

            return ntpResult;
        } catch (Exception e) {
            log.error("error on request ntp service: " + konnektor.getIp(), e);
            if (timeMetric != null) {
                timeMetric.stopAndPublish();
            }
            if (timeClient != null && timeClient.isOpen()) {
                timeClient.close();
            }
            throw e;
        }
    }
}
