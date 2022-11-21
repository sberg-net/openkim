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
package net.sberg.openkim.log;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;

@Data
public class DefaultLogger {

    private static final Logger log = LoggerFactory.getLogger(DefaultLogger.class);

    private String id;
    private LocalDateTime created;
    private LocalDateTime used;
    private DefaultLoggerContext defaultLoggerContext;
    private StringBuilder logContent = new StringBuilder();
    private int depth;

    public void logLine(String line) {
        if (defaultLoggerContext.isHtmlMode()) {
            logContent.append((depth > 0) ? "<br/><span style=\"white-space:nowrap;\" class=\"ml-" + depth + "\">" : "<br/><span style=\"white-space:nowrap;\">");
        } else {
            logContent.append("\r\n");
            for (int i = 0; i < depth; i++) {
                logContent.append(" ");
            }
        }
        logContent.append(line);
        if (defaultLoggerContext.isHtmlMode()) {
            logContent.append("</span>");
        }
        setUsed(LocalDateTime.now());
    }

    public void handleDepth(int d) {
        depth = depth + d;
    }

    public String getLogContentAsStr() {
        return logContent.toString();
    }

    public void writeInFile() {
        try {
            if (logContent.length() > 0 && defaultLoggerContext.isWriteInFile()) {
                PrintWriter printWriter = null;
                File file = new File(defaultLoggerContext.getFileName());
                try {
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    printWriter = new PrintWriter(new FileOutputStream(defaultLoggerContext.getFileName(), true));
                    printWriter.write(logContent.toString());
                } catch (IOException e) {
                    log.error("error on writing logfile: " + defaultLoggerContext.getFileName(), e);
                } finally {
                    if (printWriter != null) {
                        printWriter.flush();
                        printWriter.close();
                    }
                }
            }
        } catch (Exception e) {
            log.error("error on writing logfile: " + defaultLoggerContext.getFileName(), e);
        }
    }

    public void parseUsername(String user) throws Exception {
        //ais-werkstatt.kvbb@tm.kim.telematik#mail.tm.kim.telematik:995#839999800#ALBIS#1#userId#konnektorId
        getDefaultLoggerContext().setMailServerUsername(user.split("#")[0]);
        String[] hostPort = user.split("#")[1].split(":");

        getDefaultLoggerContext().setMailServerHost(hostPort[0]);
        getDefaultLoggerContext().setMailServerPort(hostPort[1]);

        getDefaultLoggerContext().setMandantId(user.split("#")[2]);
        getDefaultLoggerContext().setClientSystemId(user.split("#")[3]);
        getDefaultLoggerContext().setWorkplaceId(user.split("#")[4]);

        try {
            getDefaultLoggerContext().setUserId(user.split("#")[5]);
        } catch (Exception e) {
        }

        try {
            getDefaultLoggerContext().setKonnektorId(user.split("#")[6]);
        } catch (Exception e) {
        }
    }
}
