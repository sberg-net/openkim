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

import net.sberg.openkim.pipeline.PipelineService;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface IPipelineOperation {

    public static final String BUILTIN_VENDOR = "OpenKIM";

    public static final String ENV_EXCEPTION = "exception";

    public String getName();
    public Consumer<DefaultPipelineOperationContext> getDefaultOkConsumer();
    public void execute(DefaultPipelineOperationContext defaultPipelineOperationContext, Consumer<DefaultPipelineOperationContext> okConsumer, BiConsumer<DefaultPipelineOperationContext, Exception> failConsumer);

    public default String getOperationKey() {
        return getVendor()+"."+getName();
    }
    public default String getVendor() {
        return BUILTIN_VENDOR;
    }
    public default BiConsumer<DefaultPipelineOperationContext, Exception> getDefaultFailConsumer() {
        return (context, e) -> {
            context.setEnvironmentValue(getName(), ENV_EXCEPTION, e);
        };
    }
    public default boolean hasError(DefaultPipelineOperationContext defaultPipelineOperationContext, String... prefixes) {
        for (int i = 0; i < prefixes.length; i++) {
            if (defaultPipelineOperationContext.hasEnvironmentValue(prefixes[i], ENV_EXCEPTION)) {
                return true;
            }
        }
        return false;
    }
    public default void initialize(PipelineService pipelineService) throws Exception {}
}
