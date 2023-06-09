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
package net.sberg.openkim.gateway.pop3;

import net.sberg.openkim.common.ICommonConstants;
import net.sberg.openkim.konfiguration.Konfiguration;
import net.sberg.openkim.konnektor.Konnektor;
import net.sberg.openkim.log.DefaultLogger;
import net.sberg.openkim.log.DefaultLoggerContext;
import net.sberg.openkim.log.LogService;
import org.apache.james.protocols.api.ProtocolTransport;
import org.apache.james.protocols.pop3.POP3SessionImpl;

import javax.mail.Folder;
import javax.mail.Store;
import java.io.File;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Pop3GatewaySession extends POP3SessionImpl {

    private static final DateTimeFormatter dtFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final List<Integer> delMsgs = new ArrayList<>();

    private Store pop3ClientStore;
    private Folder pop3ClientFolder;
    private EnumPop3GatewayState gatewayState = EnumPop3GatewayState.UNKNOWN;

    private final DefaultLogger logger;
    private String id;

    public Pop3GatewaySession(
        ProtocolTransport transport,
        Pop3GatewayConfiguration config
    ) {
        super(transport, config);
        setHandlerState(AUTHENTICATION_READY);

        DefaultLoggerContext defaultLoggerContext = new DefaultLoggerContext();
        LogService logService = ((Pop3GatewayConfiguration) getConfiguration()).getLogService();
        Konfiguration konfiguration = ((Pop3GatewayConfiguration) getConfiguration()).getKonfiguration();
        Konnektor konnektor = null;
        if (konfiguration.getKonnektoren().size() > 0) {
            konnektor = konfiguration.getKonnektoren().get(0);
        }

        if (konnektor != null) {
            logger = logService.createLogger(
                defaultLoggerContext
                    .buildKonnektor(konnektor)
                    .buildKonfiguration(konfiguration)
                    .buildHtmlMode(true)
                    .buildWriteInFile(konfiguration.isWriteSmtpCmdLogFile())
                    .buildFileName(MessageFormat.format(ICommonConstants.POP3_LOG_FILENAME, getSessionID()))
            );
        } else {
            logger = logService.createLogger(
                defaultLoggerContext
                    .buildHtmlMode(true)
                    .buildKonfiguration(konfiguration)
                    .buildWriteInFile(konfiguration.isWriteSmtpCmdLogFile())
                    .buildFileName(MessageFormat.format(ICommonConstants.POP3_LOG_FILENAME, getSessionID()))
            );
        }


        if (!new File(ICommonConstants.POP3_LOG_DIR).exists()) {
            new File(ICommonConstants.POP3_LOG_DIR).mkdirs();
        }
    }

    @Override
    public String getSessionID() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        return id;
    }

    public int getPop3ClientIdleTimeoutInSeconds() {
        return ((Pop3GatewayConfiguration) getConfiguration()).getKonfiguration().getPop3ClientIdleTimeoutInSeconds();
    }

    public List<Integer> getDelMsgs() {
        return delMsgs;
    }

    public void setPop3ClientFolder(Folder pop3ClientFolder) {
        this.pop3ClientFolder = pop3ClientFolder;
    }

    public Folder getPop3ClientFolder() {
        return pop3ClientFolder;
    }

    public void setPop3ClientStore(Store pop3ClientStore) {
        this.pop3ClientStore = pop3ClientStore;
    }

    public Store getPop3ClientStore() {
        return pop3ClientStore;
    }

    public EnumPop3GatewayState getGatewayState() {
        return gatewayState;
    }

    public void setGatewayState(EnumPop3GatewayState gatewayState) {
        this.gatewayState = gatewayState;
    }

    public void log(String content) {
        StringBuilder logContent = new StringBuilder();
        logContent.append(dtFormatter.format(LocalDateTime.now()));
        if (((Pop3GatewayConfiguration) getConfiguration()).getKonfiguration().isLogPersonalInformations() && logger.getDefaultLoggerContext().getMailServerUsername() != null) {
            logContent.append(" ");
            logContent.append(logger.getDefaultLoggerContext().getMailServerUsername());
        }
        if (logger.getDefaultLoggerContext().getMailServerHost() != null) {
            logContent.append(" ");
            logContent.append(logger.getDefaultLoggerContext().getMailServerHost());
        }
        if (logger.getDefaultLoggerContext().getMailServerPort() != null) {
            logContent.append(" ");
            logContent.append(logger.getDefaultLoggerContext().getMailServerPort());
        }
        logContent.append(" ");
        logContent.append(content);
        logger.logLine(logContent.toString());
    }

    public DefaultLogger getLogger() {
        return logger;
    }

    public void cleanup() {
        ((Pop3GatewayConfiguration) getConfiguration()).getLogService().removeLogger(logger.getId());
    }
}
