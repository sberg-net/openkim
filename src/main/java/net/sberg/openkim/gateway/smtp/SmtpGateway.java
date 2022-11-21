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
package net.sberg.openkim.gateway.smtp;

import net.sberg.openkim.common.mail.MailService;
import net.sberg.openkim.gateway.GatewayNettyServer;
import net.sberg.openkim.gateway.smtp.hook.SmtpGatewayMailHook;
import net.sberg.openkim.gateway.smtp.hook.SmtpGatewayQuitHook;
import net.sberg.openkim.kas.KasService;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konfiguration.KonfigurationService;
import net.sberg.openkim.konfiguration.konnektor.dns.DnsService;
import net.sberg.openkim.konfiguration.konnektor.vzd.VzdService;
import net.sberg.openkim.log.LogService;
import org.apache.james.protocols.api.Encryption;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.handler.WiringException;
import org.jboss.netty.util.HashedWheelTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.Arrays;

@Service
public class SmtpGateway {

    private static final Logger log = LoggerFactory.getLogger(SmtpGateway.class);

    private HashedWheelTimer hashedWheelTimer;
    private GatewayNettyServer server;
    @Autowired
    private KonfigurationService konfigurationService;
    @Autowired
    private LogService logService;
    @Autowired
    private KasService kasService;
    @Autowired
    private VzdService vzdService;
    @Autowired
    private DnsService dnsService;
    @Autowired
    private MailService mailService;

    private boolean startSucces = false;

    public boolean isStartSucces() {
        return startSucces;
    }

    @PostConstruct
    protected void init() throws Exception {
        start();
    }

    @PreDestroy
    protected void destroy() throws Exception {
        try {
            if (server != null) {
                server.unbind();
            }
            startSucces = false;
            if (log.isInfoEnabled()) {
                log.info("***SMTP Gateway is stopped***");
            }
        } catch (Exception e) {
            log.error("error on destroying the smtp gateway", e);
        }
    }

    private void start() throws Exception {
        try {
            Konfiguration konfiguration = konfigurationService.getKonfiguration();
            if (konfiguration == null) {
                return;
            }

            if (log.isInfoEnabled()) {
                log.info("***SMTP Gateway activated***");
            }

            if (hashedWheelTimer == null) {
                hashedWheelTimer = new HashedWheelTimer();
            }

            server = new GatewayNettyServer.Factory(hashedWheelTimer)
                .protocol(createProtocol(konfiguration))
                .secure(buildSSLContext(konfiguration))
                .build();
            server.setTimeout(konfiguration.getSmtpGatewayIdleTimeoutInSeconds());
            server.setListenAddresses(new InetSocketAddress(konfiguration.getGatewayHost(), Integer.parseInt(konfiguration.getSmtpGatewayPort())));
            server.bind();
            startSucces = true;

            if (log.isInfoEnabled()) {
                log.info("***SMTP Gateway is started***");
            }
        } catch (Exception e) {
            log.error("error on starting the smtp gateway", e);
        }
    }

    private Encryption buildSSLContext(Konfiguration konfiguration) throws Exception {
        Encryption encryption = null;
        return encryption;
    }

    protected Protocol createProtocol(Konfiguration konfiguration) throws WiringException {
        SmtpGatewayProtocolHandlerChain chain = new SmtpGatewayProtocolHandlerChain(true, vzdService, dnsService);
        chain.addAll(0, Arrays.asList(new SmtpGatewayMailHook(kasService, mailService, vzdService), new SmtpGatewayQuitHook()));
        chain.wireExtensibleHandlers();
        return new SmtpGatewayProtocol(chain, new SmtpGatewayConfiguration(konfiguration, logService));
    }

    public void restart() throws Exception {
        destroy();
        start();
    }
}
