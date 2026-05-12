package com.highway.agent.research.graph;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.highway.agent.research.edge.ResearchEdgeActions;
import com.highway.agent.research.model.ResearchStateKeys;
import com.highway.agent.research.node.ClarifierNode;
import com.highway.agent.research.node.CriticNode;
import com.highway.agent.research.node.ExecutorNode;
import com.highway.agent.research.node.PlanReviewNode;
import com.highway.agent.research.node.PlannerNode;
import com.highway.agent.research.node.QuestionAnalyzerNode;
import com.highway.agent.research.node.ReporterNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepResearchGraph {

    private final QuestionAnalyzerNode questionAnalyzerNode;
    private final ClarifierNode clarifierNode;
    private final PlannerNode plannerNode;
    private final PlanReviewNode planReviewNode;
    private final ExecutorNode executorNode;
    private final CriticNode criticNode;
    private final ReporterNode reporterNode;
    private final ResearchEdgeActions edgeActions;

    public StateGraph buildGraph() throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = KeyStrategy.builder()
                // 原始问题
                .addStrategy(ResearchStateKeys.ORIGINAL_QUESTION, KeyStrategy.REPLACE)
                // 清晰度等级
                .addStrategy(ResearchStateKeys.CLARITY_LEVEL, KeyStrategy.REPLACE)
                // 用户澄清内容
                .addStrategy(ResearchStateKeys.USER_CLARIFICATION, KeyStrategy.REPLACE)
                // 研究计划
                .addStrategy(ResearchStateKeys.RESEARCH_PLAN, KeyStrategy.REPLACE)
                // 当前子问题索引
                .addStrategy(ResearchStateKeys.CURRENT_SUB_QUESTION_INDEX, KeyStrategy.REPLACE)
                // 批评反馈
                .addStrategy(ResearchStateKeys.CRITIQUE_FEEDBACK, KeyStrategy.REPLACE)
                // 迭代次数
                .addStrategy(ResearchStateKeys.ITERATION_COUNT, KeyStrategy.REPLACE)
                // 是否充分
                .addStrategy(ResearchStateKeys.IS_SUFFICIENT, KeyStrategy.REPLACE)
                // 最终报告
                .addStrategy(ResearchStateKeys.FINAL_REPORT, KeyStrategy.REPLACE)
                // 参考文献
                .addStrategy(ResearchStateKeys.REFERENCES, KeyStrategy.REPLACE)
                // 可视化报告数据
                .addStrategy(ResearchStateKeys.VISUAL_REPORT_DATA, KeyStrategy.REPLACE)
                // 澄清问题
                .addStrategy(ResearchStateKeys.CLARIFICATION_QUESTIONS, KeyStrategy.APPEND)
                // 搜索结果
                .addStrategy(ResearchStateKeys.SEARCH_RESULTS, KeyStrategy.APPEND)
                // 提取内容
                .addStrategy(ResearchStateKeys.EXTRACTED_CONTENTS, KeyStrategy.APPEND)
                .build();

        StateGraph graph = new StateGraph("deep-research", keyStrategyFactory);

        // 添加节点
        // 调用LLM 分析问题，输出clarity_level(clear/needs_clarification)和澄清问题列表（clarification_questions）
        graph.addNode("question_analyzer", AsyncNodeAction.node_async(questionAnalyzerNode));
        // 中断节点不执行具体逻辑，暂停图执行，等待用户回答澄清问题，并将恢复resume到user_clarification, 然后回到question_analyzer
        graph.addNode("clarifier", AsyncNodeAction.node_async(clarifierNode));
        graph.addNode("planner", AsyncNodeAction.node_async(plannerNode));
        graph.addNode("plan_review", AsyncNodeAction.node_async(planReviewNode));
        graph.addNode("executor", AsyncNodeAction.node_async(executorNode));
        graph.addNode("critic", AsyncNodeAction.node_async(criticNode));
        graph.addNode("reporter", AsyncNodeAction.node_async(reporterNode));

        // 添加边
        graph.addEdge(START, "question_analyzer");

        // question_analyzer → clarifier 或 planner
        graph.addConditionalEdges("question_analyzer",
                AsyncEdgeAction.edge_async((EdgeAction) edgeActions::afterQuestionAnalyzerEdge),
                Map.of("needs_clarification", "clarifier", "clear", "planner"));

        // clarifier → planner（用户补充信息后直接规划，不再回到 question_analyzer 避免重复追问）
        graph.addEdge("clarifier", "planner");

        // planner → plan_review 或 executor
        graph.addConditionalEdges("planner",
                AsyncEdgeAction.edge_async((EdgeAction) edgeActions::afterPlannerEdge),
                Map.of("needs_confirmation", "plan_review", "auto_proceed", "executor"));

        // plan_review → planner 或 executor
        graph.addConditionalEdges("plan_review",
                AsyncEdgeAction.edge_async((EdgeAction) edgeActions::afterPlanReviewEdge),
                Map.of("adjust", "planner", "confirm", "executor"));

        // executor → critic
        graph.addEdge("executor", "critic");

        // critic → planner 或 reporter
        graph.addConditionalEdges("critic",
                AsyncEdgeAction.edge_async((EdgeAction) edgeActions::afterCriticEdge),
                Map.of("revise", "planner", "sufficient", "reporter"));

        // reporter → END
        graph.addEdge("reporter", END);

        return graph;
    }

    public com.alibaba.cloud.ai.graph.CompiledGraph compile() throws GraphStateException {
        StateGraph graph = buildGraph();

        // 使用 MemorySaver 进行检查点持久化（生产环境可替换为 MysqlSaver）
        SaverConfig saverConfig = SaverConfig.builder()
                .register(MemorySaver.builder().build())
                .build();

        return graph.compile(CompileConfig.builder()
                .saverConfig(saverConfig)
                .interruptAfter("clarifier", "plan_review")
                .build());
    }
}
