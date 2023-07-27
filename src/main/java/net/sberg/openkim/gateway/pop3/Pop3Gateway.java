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
package net.sberg.openkim.gateway.pop3;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.sberg.openkim.common.EnumMailConnectionSecurity;
import net.sberg.openkim.common.ICommonConstants;
import net.sberg.openkim.gateway.GatewayNettyServer;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konfiguration.KonfigurationService;
import net.sberg.openkim.log.LogService;
import net.sberg.openkim.pipeline.PipelineService;
import org.apache.james.protocols.api.ClientAuth;
import org.apache.james.protocols.api.Protocol;
import org.apache.james.protocols.api.handler.WiringException;
import org.apache.james.protocols.netty.Encryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;

@Service
public class Pop3Gateway {

    private static final Logger log = LoggerFactory.getLogger(Pop3Gateway.class);

    private GatewayNettyServer server;

    @Autowired
    private LogService logService;
    @Autowired
    private PipelineService pipelineService;
    @Autowired
    private KonfigurationService konfigurationService;
    @Value("${gatewaykeystore.password}")
    private String keyStorePwd;

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
                log.info("***POP3 Gateway is stopped***");
            }
        } catch (Exception e) {
            log.error("error on destroying the pop3 gateway", e);
        }
    }

    private void start() throws Exception {
        try {
            Konfiguration konfiguration = konfigurationService.getKonfiguration();
            if (konfiguration == null) {
                return;
            }

            if (log.isInfoEnabled()) {
                log.info("***POP3 Gateway activated***");
            }

            server = new GatewayNettyServer.Factory()
                .protocol(createProtocol(konfiguration)).secure(buildSSLContext(konfiguration))
                .build();
            server.setTimeout(konfiguration.getPop3GatewayIdleTimeoutInSeconds());
            server.setListenAddresses(new InetSocketAddress(konfiguration.getGatewayHost(), Integer.parseInt(konfiguration.getPop3GatewayPort())));
            server.bind();
            startSucces = true;

            if (log.isInfoEnabled()) {
                log.info("***POP3 Gateway is started***");
            }
        } catch (Exception e) {
            log.error("error on starting the pop3 gateway", e);
        }
    }

    private Encryption buildSSLContext(Konfiguration konfiguration) throws Exception {
        Encryption encryption = null;
        if (!konfiguration.getPop3GatewayConnectionSec().equals(EnumMailConnectionSecurity.NONE)) {
            FileInputStream fis = null;
            try {
                KeyStore ks = KeyStore.getInstance("PKCS12", "BC");
                fis = new FileInputStream(new File(ICommonConstants.BASE_DIR+ICommonConstants.OPENKIM_SERVER_KEYSTORE_FILENAME));
                ks.load(fis, keyStorePwd.toCharArray());

                // Set up key manager factory to use our key store
                KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX", "BCJSSE");
                kmf.init(ks, keyStorePwd.toCharArray());

                // Initialize the SSLContext to work with our key managers.
                SSLContext context = SSLContext.getInstance("TLS", "BCJSSE");
                context.init(kmf.getKeyManagers(), null, null);
                if (konfiguration.getPop3GatewayConnectionSec().equals(EnumMailConnectionSecurity.STARTTLS)) {
                    encryption = Encryption.createStartTls(context, null, null, ClientAuth.NONE);
                }
                else {
                    encryption = Encryption.createTls(context, null, null, ClientAuth.NONE);
                }

            }
            catch (Exception e) {
                log.error("error on starting the pop3 gateway - bulding ssl context", e);
            }
            finally {
                if (fis != null) {
                    fis.close();
                }
                return encryption;
            }
        }
        return encryption;
    }

    protected Protocol createProtocol(Konfiguration konfiguration) throws WiringException {
        Pop3GatewayProtocolHandlerChain chain = new Pop3GatewayProtocolHandlerChain(pipelineService);
        chain.wireExtensibleHandlers();
        return new Pop3GatewayProtocol(chain, new Pop3GatewayConfiguration(konfiguration, logService));
    }

    public void restart() throws Exception {
        destroy();
        start();
    }
}
