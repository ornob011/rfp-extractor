package com.dsi.rfp.agent;

import com.dsi.rfp.adapter.persistence.ExtractionStateCheckpointRepository;
import com.dsi.rfp.agent.checkpoint.CheckpointingNodeAction;
import com.dsi.rfp.agent.node.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractionGraph {

    private final ValidateNode validateNode;
    private final ClassifyPagesNode classifyPagesNode;
    private final ExtractTextNode extractTextNode;
    private final SegmentSectionsNode segmentSectionsNode;
    private final ExtractTablesNode extractTablesNode;
    private final ExtractEntitiesNode extractEntitiesNode;
    private final ScoreConfidenceNode scoreConfidenceNode;
    private final RepairLoopNode repairLoopNode;
    private final RunRulePackNode runRulePackNode;
    private final FinalizeNode finalizeNode;
    private final RepairRouter repairRouter;
    private final ExtractionStateCheckpointRepository checkpointRepository;

    public CompiledGraph<ExtractionState> compile() throws GraphStateException {
        StateGraph<ExtractionState> graph = new StateGraph<>(ExtractionState::new);

        registerNodes(graph);
        registerEdges(graph);

        log.info(
            "event=graph.compiled component=ExtractionGraph nodes=10"
        );

        return graph.compile();
    }

    private void registerNodes(
        StateGraph<ExtractionState> graph
    ) throws GraphStateException {
        graph.addNode(NodeId.VALIDATE.code(), async(validateNode));
        graph.addNode(NodeId.CLASSIFY_PAGES.code(), async(classifyPagesNode));
        graph.addNode(NodeId.EXTRACT_TEXT.code(), async(extractTextNode));
        graph.addNode(NodeId.SEGMENT_SECTIONS.code(), async(segmentSectionsNode));
        graph.addNode(NodeId.EXTRACT_TABLES.code(), async(extractTablesNode));
        graph.addNode(NodeId.EXTRACT_ENTITIES.code(), async(extractEntitiesNode));
        graph.addNode(NodeId.SCORE_CONFIDENCE.code(), async(scoreConfidenceNode));
        graph.addNode(NodeId.REPAIR_LOOP.code(), async(repairLoopNode));
        graph.addNode(NodeId.RUN_RULE_PACK.code(), async(runRulePackNode));
        graph.addNode(NodeId.FINALIZE.code(), async(finalizeNode));
    }

    private void registerEdges(
        StateGraph<ExtractionState> graph
    ) throws GraphStateException {
        graph.addEdge(START, NodeId.VALIDATE.code());
        graph.addEdge(NodeId.VALIDATE.code(), NodeId.CLASSIFY_PAGES.code());
        graph.addEdge(NodeId.CLASSIFY_PAGES.code(), NodeId.EXTRACT_TEXT.code());
        graph.addEdge(NodeId.EXTRACT_TEXT.code(), NodeId.SEGMENT_SECTIONS.code());
        graph.addEdge(NodeId.SEGMENT_SECTIONS.code(), NodeId.EXTRACT_TABLES.code());
        graph.addEdge(NodeId.EXTRACT_TABLES.code(), NodeId.EXTRACT_ENTITIES.code());
        graph.addEdge(NodeId.EXTRACT_ENTITIES.code(), NodeId.SCORE_CONFIDENCE.code());

        graph.addConditionalEdges(
            NodeId.SCORE_CONFIDENCE.code(),
            AsyncEdgeAction.edge_async(this::routeAfterScoring),
            routeMap()
        );

        graph.addConditionalEdges(
            NodeId.REPAIR_LOOP.code(),
            AsyncEdgeAction.edge_async(this::routeAfterRepair),
            routeMap()
        );

        graph.addEdge(NodeId.RUN_RULE_PACK.code(), NodeId.FINALIZE.code());
        graph.addEdge(NodeId.FINALIZE.code(), END);
    }

    private java.util.Map<String, String> routeMap() {
        return java.util.Map.of(
            NodeId.REPAIR_LOOP.code(), NodeId.REPAIR_LOOP.code(),
            NodeId.RUN_RULE_PACK.code(), NodeId.RUN_RULE_PACK.code()
        );
    }

    private String routeAfterScoring(ExtractionState state) {
        return routeFor(state);
    }

    private String routeAfterRepair(ExtractionState state) {
        return routeFor(state);
    }

    private String routeFor(ExtractionState state) {
        if (repairRouter.shouldRepair(state)) {
            return NodeId.REPAIR_LOOP.code();
        }

        return NodeId.RUN_RULE_PACK.code();
    }

    private AsyncNodeAction<ExtractionState> async(
        NodeAction<ExtractionState> action
    ) {
        return AsyncNodeAction.node_async(
            new CheckpointingNodeAction(
                action,
                checkpointRepository
            )
        );
    }

    private enum NodeId {
        VALIDATE("VALIDATE"),
        CLASSIFY_PAGES("CLASSIFY_PAGES"),
        EXTRACT_TEXT("EXTRACT_TEXT"),
        SEGMENT_SECTIONS("SEGMENT_SECTIONS"),
        EXTRACT_TABLES("EXTRACT_TABLES"),
        EXTRACT_ENTITIES("EXTRACT_ENTITIES"),
        SCORE_CONFIDENCE("SCORE_CONFIDENCE"),
        REPAIR_LOOP("REPAIR_LOOP"),
        RUN_RULE_PACK("RUN_RULE_PACK"),
        FINALIZE("FINALIZE");

        private final String code;

        NodeId(String code) {
            this.code = code;
        }

        private String code() {
            return code;
        }
    }
}
