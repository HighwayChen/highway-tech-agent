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
public class ClarifierNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 此节点主要作用是标记需要澄清，实际的中断由 interruptAfter("clarifier") 处理
        // 用户回复后通过 resumeResearch 注入 user_clarification，然后回到 question_analyzer
        log.info("Clarifier node: waiting for user clarification");
        return Map.of();
    }
}
