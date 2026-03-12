package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.exception.EntityMetadataContractException;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.TableExtractionStrategy;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class TableExtractionConfig {

    private static final String CONFIG_PATH = "metadata/table-extraction-v1.yml";

    private final ConfigDocument config;

    public TableExtractionConfig() {
        config = loadConfig();
    }

    public int latticeMinLinesForTable() {
        return config.lattice().minLinesForTable();
    }

    public List<TableExtractionStrategy> extractionOrder() {
        return config.extractorOrder()
                     .stream()
                     .map(TableExtractionStrategy::fromString)
                     .toList();
    }

    public int latticeMaxCells() {
        return config.lattice().maxCells();
    }

    public float latticeLineTolerance() {
        return config.lattice().lineTolerance();
    }

    public float streamYProximityTolerance() {
        return config.stream().yProximityTolerance();
    }

    public double streamColumnGapRatio() {
        return config.stream().columnGapRatio();
    }

    public double streamConfidence() {
        return config.stream().confidence();
    }

    public int streamMinColumns() {
        return config.stream().minColumns();
    }

    public int streamMinRows() {
        return config.stream().minRows();
    }

    public int continuationRequiredSignals() {
        return config.continuation().requiredSignals();
    }

    public int continuationHeaderDistanceThreshold() {
        return config.continuation().headerDistanceThreshold();
    }

    public java.util.List<String> continuationFooterKeywords() {
        return config.continuation().footerKeywords();
    }

    public String vlmMethod() {
        return config.methods().vlm();
    }

    public String latticeMethod() {
        return config.methods().lattice();
    }

    public String streamMethod() {
        return config.methods().stream();
    }

    public Resource scannedPromptResource() {
        return new ClassPathResource(config.scanned().promptResourcePath());
    }

    public String scannedPromptTemplate() {
        Resource resource = scannedPromptResource();

        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load scanned table prompt: %s", resource),
                exception
            );
        }
    }

    public Resource scannedSystemPromptResource() {
        return new ClassPathResource(config.scanned().systemPromptResourcePath());
    }

    public int scannedMaxPageTextLength() {
        return config.scanned().maxPageTextLength();
    }

    public double scannedConfidenceFactor() {
        return config.scanned().confidenceFactor();
    }

    public String scannedLlmMethod() {
        return config.scanned().llmMethod();
    }

    public TableExtractionStrategy latticeStrategy() {
        return TableExtractionStrategy.fromString(config.methods().lattice());
    }

    public TableExtractionStrategy streamStrategy() {
        return TableExtractionStrategy.fromString(config.methods().stream());
    }

    private ConfigDocument loadConfig() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        try (InputStream input = new ClassPathResource(CONFIG_PATH).getInputStream()) {
            return mapper.readValue(input, ConfigDocument.class);
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to load table extraction config: %s", CONFIG_PATH),
                exception
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConfigDocument(
        Integer version,
        List<String> extractorOrder,
        Methods methods,
        Lattice lattice,
        Stream stream,
        Continuation continuation,
        Scanned scanned
    ) {

        private ConfigDocument {
            if (version == null) {
                throw new EntityMetadataContractException("Table extraction config must define version");
            }

            if (methods == null) {
                throw new EntityMetadataContractException("Table extraction config must define methods");
            }

            if (extractorOrder == null || extractorOrder.isEmpty()) {
                throw new EntityMetadataContractException("Table extraction config must define extractorOrder");
            }

            if (lattice == null) {
                throw new EntityMetadataContractException("Table extraction config must define lattice");
            }

            if (stream == null) {
                throw new EntityMetadataContractException("Table extraction config must define stream");
            }

            if (continuation == null) {
                throw new EntityMetadataContractException("Table extraction config must define continuation");
            }

            if (scanned == null) {
                throw new EntityMetadataContractException("Table extraction config must define scanned");
            }
        }
    }

    private record Methods(
        String vlm,
        String lattice,
        String stream
    ) {
    }

    private record Lattice(
        int minLinesForTable,
        int maxCells,
        float lineTolerance
    ) {
    }

    private record Stream(
        float yProximityTolerance,
        double columnGapRatio,
        double confidence,
        int minColumns,
        int minRows
    ) {
    }

    private record Continuation(
        int requiredSignals,
        int headerDistanceThreshold,
        java.util.List<String> footerKeywords
    ) {
    }

    private record Scanned(
        String promptResourcePath,
        String systemPromptResourcePath,
        int maxPageTextLength,
        double confidenceFactor,
        String llmMethod
    ) {

        private Scanned {
            if (promptResourcePath == null) {
                throw new EntityMetadataContractException("Table extraction config scanned section must define promptResourcePath");
            }

            if (systemPromptResourcePath == null) {
                throw new EntityMetadataContractException("Table extraction config scanned section must define systemPromptResourcePath");
            }

            if (llmMethod == null) {
                throw new EntityMetadataContractException("Table extraction config scanned section must define llmMethod");
            }
        }
    }
}
