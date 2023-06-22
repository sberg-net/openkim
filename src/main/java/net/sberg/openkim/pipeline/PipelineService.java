package net.sberg.openkim.pipeline;

import jakarta.annotation.PostConstruct;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.PipelineOperationLabel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PipelineService {

    private Map<String, IPipelineOperation> operationMap = new HashMap<>();

    @Value("${pipeline.scanPackages}")
    private String scanPackages;

    @PostConstruct
    public void init() throws Exception {
        operationMap = new PipelineOperationScanner().createOperationMap(Arrays.asList(scanPackages.split(",")));
    }

    public IPipelineOperation getOperation(String key) throws Exception {
        if (operationMap.containsKey(key)) {
            IPipelineOperation pipelineOperation = operationMap.get(key).getClass().getDeclaredConstructor().newInstance();
            pipelineOperation.initialize(this);
            return pipelineOperation;
        }
        throw new IllegalStateException("no operation available: "+key);
    }

    public Class getOperationClass(String key) throws Exception {
        if (operationMap.containsKey(key)) {
            return operationMap.get(key).getClass();
        }
        throw new IllegalStateException("no operation available: "+key);
    }

    public List<PipelineOperationLabel> getTestableOperations() throws Exception {
        return operationMap.keySet().stream().filter(s -> operationMap.get(s).isTestable()).map(s -> operationMap.get(s).createLabel()).collect(Collectors.toList());
    }
}
