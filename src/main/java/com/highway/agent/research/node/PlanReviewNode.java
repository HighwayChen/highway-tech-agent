package com.highway.agent.research.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.highway.agent.research.model.ResearchStateKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanReviewNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 此节点标记需要确认计划，实际的中断由 interruptAfter("plan_review") 处理
        // 用户回复后通过 resumeResearch 注入 user_clarification（confirm/adjust），然后路由
        log.info("Plan review node: waiting for user confirmation");
        return Map.of();
    }
}
