package net.sberg.openkim.pipeline;

import jakarta.annotation.PostConstruct;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

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
            return operationMap.get(key).getClass().getDeclaredConstructor().newInstance();
        }
        throw new IllegalStateException("no operation available: "+key);
    }

    public List<IPipelineOperation> getOperations(List<String> keys) throws Exception {
        List<IPipelineOperation> operations = new ArrayList<>();
        for (Iterator<String> iterator = keys.iterator(); iterator.hasNext(); ) {
            String key = iterator.next();
            operations.add(getOperation(key));
        }
        return operations;
    }
}
