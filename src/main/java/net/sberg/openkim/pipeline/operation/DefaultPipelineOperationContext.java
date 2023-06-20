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
package net.sberg.openkim.pipeline.operation;

import net.sberg.openkim.log.DefaultLogger;

import java.util.HashMap;
import java.util.Map;

public class DefaultPipelineOperationContext implements IPipelineOperationContext {

    public static final String ENV_KONNEKTOR_ID = "konnId";
    public static final String ENV_WEBSERVICE_ID = "wsId";
    public static final String ENV_OP_ID = "opId";

    private DefaultLogger logger;
    private Map<String, Object> environment = new HashMap<>();

    public DefaultLogger getLogger() {
        return logger;
    }

    public void setLogger(DefaultLogger logger) {
        this.logger = logger;
    }

    public Object getEnvironmentValue(String prefix, String key) {
        return environment.get(prefix+"."+key);
    }
    public boolean hasEnvironmentValue(String prefix, String key) { return environment.containsKey(prefix+"."+key); }
    public void setEnvironmentValue(String prefix, String key, Object val) {
        environment.put(prefix+"."+key, val);
    }
    public void setEnvironmentValues(Map<String, Object> values) {
        environment.putAll(values);
    }
}
