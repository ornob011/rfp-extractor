package com.dsi.rfp.agent;

import com.dsi.rfp.agent.node.*;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ExtractionGraphTest {

    @Mock
    private ValidateNode validateNode;
    @Mock
    private ClassifyPagesNode classifyPagesNode;
    @Mock
    private ExtractTextNode extractTextNode;
    @Mock
    private SegmentSectionsNode segmentSectionsNode;
    @Mock
    private ExtractTablesNode extractTablesNode;
    @Mock
    private ExtractEntitiesNode extractEntitiesNode;
    @Mock
    private ScoreConfidenceNode scoreConfidenceNode;
    @Mock
    private RepairLoopNode repairLoopNode;
    @Mock
    private RunRulePackNode runRulePackNode;
    @Mock
    private FinalizeNode finalizeNode;
    @Mock
    private ConfidenceRouter confidenceRouter;

    private ExtractionGraph graph;

    @BeforeEach
    void setUp() {
        graph = new ExtractionGraph(
            validateNode,
            classifyPagesNode,
            extractTextNode,
            segmentSectionsNode,
            extractTablesNode,
            extractEntitiesNode,
            scoreConfidenceNode,
            repairLoopNode,
            runRulePackNode,
            finalizeNode,
            confidenceRouter
        );
    }

    @Test
    void shouldCompileWithoutError() throws GraphStateException {
        CompiledGraph<ExtractionState> compiled = graph.compile();

        assertThat(compiled).isNotNull();
    }
}
