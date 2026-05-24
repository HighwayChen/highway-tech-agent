package com.highway.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.highway.agent.memory.InterviewTaskMapper;
import com.highway.agent.model.InterviewEnums.BizType;
import com.highway.agent.model.InterviewEnums.TaskStatus;
import com.highway.agent.model.InterviewEnums.TaskType;
import com.highway.agent.model.InterviewTask;
import com.highway.agent.model.interview.TaskSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InterviewTaskService {

    private final InterviewTaskMapper taskMapper;

    public InterviewTask createTask(BizType bizType, Long bizId, TaskType taskType, int retryCount, String inputSummary) {
        InterviewTask task = new InterviewTask();
        task.setBizType(bizType.name());
        task.setBizId(bizId);
        task.setTaskType(taskType.name());
        task.setStatus(TaskStatus.PENDING.name());
        task.setRetryCount(retryCount);
        task.setInputSummary(inputSummary);
        taskMapper.insert(task);
        return task;
    }

    public void markRunning(Long taskId) {
        InterviewTask task = taskMapper.selectById(taskId);
        task.setStatus(TaskStatus.RUNNING.name());
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    public void markSuccess(Long taskId, String outputSummary) {
        InterviewTask task = taskMapper.selectById(taskId);
        task.setStatus(TaskStatus.SUCCESS.name());
        task.setOutputSummary(outputSummary);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    public void markFailed(Long taskId, String failureReason) {
        InterviewTask task = taskMapper.selectById(taskId);
        task.setStatus(TaskStatus.FAILED.name());
        task.setFailureReason(failureReason);
        task.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    public TaskSummaryResponse latestTask(BizType bizType, Long bizId, TaskType taskType) {
        InterviewTask task = taskMapper.selectOne(new LambdaQueryWrapper<InterviewTask>()
                .eq(InterviewTask::getBizType, bizType.name())
                .eq(InterviewTask::getBizId, bizId)
                .eq(InterviewTask::getTaskType, taskType.name())
                .orderByDesc(InterviewTask::getCreatedAt)
                .last("LIMIT 1"));
        return toSummary(task);
    }

    public int nextRetryCount(BizType bizType, Long bizId, TaskType taskType) {
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<InterviewTask>()
                .eq(InterviewTask::getBizType, bizType.name())
                .eq(InterviewTask::getBizId, bizId)
                .eq(InterviewTask::getTaskType, taskType.name()));
        return count.intValue();
    }

    private TaskSummaryResponse toSummary(InterviewTask task) {
        if (task == null) {
            return null;
        }
        return new TaskSummaryResponse(
                task.getId(),
                task.getTaskType(),
                task.getStatus(),
                task.getRetryCount(),
                task.getFailureReason(),
                task.getInputSummary(),
                task.getOutputSummary(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getCreatedAt()
        );
    }
}
