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

import net.sberg.openkim.common.FileUtils;
import net.sberg.openkim.common.ICommonConstants;
import org.springframework.stereotype.Service;

import java.io.File;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class LogService {

    private final Map<String, DefaultLogger> cache = new Hashtable<>();

    public DefaultLogger createLogger(DefaultLoggerContext defaultLoggerContext) {
        DefaultLogger defaultLogger = new DefaultLogger();
        defaultLogger.setCreated(LocalDateTime.now());
        defaultLogger.setDefaultLoggerContext(defaultLoggerContext);
        defaultLogger.setId(UUID.randomUUID().toString());
        cache.put(defaultLogger.getId(), defaultLogger);
        return defaultLogger;
    }

    public DefaultLogger getLogger(String id) throws Exception {
        DefaultLogger logger = cache.get(id);
        if (logger == null) {
            throw new IllegalStateException("logger not available for: " + id);
        }
        return logger;
    }

    public DefaultLogger removeLogger(String id) {
        DefaultLogger logger = cache.remove(id);
        if (logger != null) {
            if (logger.getDefaultLoggerContext().isWriteInFile()) {
                logger.writeInFile();
            }
            logger.getDefaultLoggerContext().reset();
        }
        return logger;
    }

    public List<Log> lade(String id, EnumLogTyp logTyp) throws Exception {
        List<Log> result = new ArrayList<>();
        File[] logs = new File(logTyp.equals(EnumLogTyp.POP3) ? ICommonConstants.POP3_LOG_DIR : ICommonConstants.SMTP_LOG_DIR).listFiles();
        if (logs == null) {
            return result;
        }
        for (int i = 0; i < logs.length; i++) {
            Log log = new Log();
            String dbId = logs[i].getName().substring(0, logs[i].getName().indexOf(".log"));
            log.setId(dbId);
            log.setLogTyp(logTyp);
            log.setGeaendert(new Timestamp(logs[i].lastModified()).toLocalDateTime());
            if (dbId.equals(id)) {
                result.add(log);
                break;
            } else if (id == null) {
                result.add(log);
            }
        }
        return result;
    }

    public String ladeInhalt(String id, EnumLogTyp logTyp) throws Exception {
        File[] logs = new File(logTyp.equals(EnumLogTyp.POP3) ? ICommonConstants.POP3_LOG_DIR : ICommonConstants.SMTP_LOG_DIR).listFiles();

        for (int i = 0; i < logs.length; i++) {
            String dbId = logs[i].getName().substring(0, logs[i].getName().indexOf(".log"));
            if (dbId.equals(id)) {
                String content = FileUtils.readFileContent(logs[i].getAbsolutePath());
                return content;
            }
        }
        return "Log konnte mit der Id: " + id + " nicht geladen werden";
    }
}
