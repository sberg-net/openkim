package net.sberg.openkim.pipeline;

import net.sberg.openkim.konnektor.KonnektorVzdController;
import net.sberg.openkim.pipeline.operation.IPipelineOperation;
import net.sberg.openkim.pipeline.operation.PipelineOperationLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PipelineService {

    private static final Logger log = LoggerFactory.getLogger(KonnektorVzdController.class);

    @Autowired
    @PipelineOperation
    List<IPipelineOperation> operations;

    public IPipelineOperation getOperation(String key) throws Exception {
        List<IPipelineOperation> result = operations.stream().filter(iPipelineOperation -> iPipelineOperation.getOperationKey().equals(key)).collect(Collectors.toList());
        if (result.size() == 1) {
            return result.get(0);
        }
        if (result.size() > 1) {
            throw new IllegalStateException("more than one operation available: "+key);
        }
        throw new IllegalStateException("no operation available: "+key);
    }

    public Class getOperationClass(String key) throws Exception {
        List<IPipelineOperation> result = operations.stream().filter(iPipelineOperation -> iPipelineOperation.getOperationKey().equals(key)).collect(Collectors.toList());
        if (result.size() == 1) {
            return result.get(0).getClass();
        }
        if (result.size() > 1) {
            throw new IllegalStateException("more than one operation available: "+key);
        }
        throw new IllegalStateException("no operation available: "+key);
    }

    public List<PipelineOperationLabel> getTestableOperations() throws Exception {
        return operations.stream().filter(s -> s.isTestable()).map(s -> s.createLabel()).collect(Collectors.toList());
    }
}
