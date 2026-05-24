package com.highway.agent.service;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.interview.graph.InterviewQuestionGraph;
import com.highway.agent.interview.graph.InterviewQuestionState;
import com.highway.agent.memory.InterviewQuestionMapper;
import com.highway.agent.memory.InterviewResumeMapper;
import com.highway.agent.memory.InterviewSessionMapper;
import com.highway.agent.model.InterviewEnums.BizType;
import com.highway.agent.model.InterviewEnums.ResumeStatus;
import com.highway.agent.model.InterviewEnums.SessionStatus;
import com.highway.agent.model.InterviewEnums.TaskType;
import com.highway.agent.model.InterviewQuestion;
import com.highway.agent.model.InterviewResume;
import com.highway.agent.model.InterviewSession;
import com.highway.agent.model.InterviewTask;
import com.highway.agent.model.interview.AnswerSaveRequest;
import com.highway.agent.model.interview.QuestionListResponse;
import com.highway.agent.model.interview.ReportResponse;
import com.highway.agent.model.interview.SessionDetailResponse;
import com.highway.agent.model.interview.agent.GeneratedQuestionResult;
import com.highway.agent.model.interview.agent.EvaluationResult;
import com.highway.agent.model.interview.agent.InterviewPlanResult;
import com.highway.agent.model.interview.agent.ResumeAnalysisResult;
import com.highway.agent.service.interview.EvaluationAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InterviewSessionService {

    private final InterviewResumeMapper resumeMapper;
    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final MinioService minioService;
    private final InterviewTaskService taskService;
    private final InterviewQuestionGraph interviewQuestionGraph;
    private final ObjectMapper objectMapper;
    private final EvaluationAgent evaluationAgent;

    @Transactional
    public SessionDetailResponse start(Long resumeId) {
        InterviewResume resume = requireAnalyzedResume(resumeId);
        InterviewSession session = new InterviewSession();
        session.setResumeId(resumeId);
        session.setStatus(SessionStatus.GENERATING.name());
        sessionMapper.insert(session);

        int retryCount = taskService.nextRetryCount(BizType.SESSION, session.getId(), TaskType.QUESTION_GENERATE);
        InterviewTask task = taskService.createTask(BizType.SESSION, session.getId(), TaskType.QUESTION_GENERATE, retryCount, resume.getFileName());
        taskService.markRunning(task.getId());
        try {
            generateQuestions(session, resume);
            session.setStatus(SessionStatus.READY.name());
            sessionMapper.updateById(session);
            taskService.markSuccess(task.getId(), "已生成 8 道面试题");
        } catch (Exception e) {
            session.setStatus(SessionStatus.GENERATE_FAILED.name());
            session.setFailureReason(e.getMessage());
            sessionMapper.updateById(session);
            taskService.markFailed(task.getId(), e.getMessage());
        }
        return get(session.getId());
    }

    public SessionDetailResponse get(Long sessionId) {
        InterviewSession session = requireSession(sessionId);
        InterviewResume resume = resumeMapper.selectById(session.getResumeId());
        List<InterviewQuestion> questions = questions(sessionId);
        int answeredCount = (int) questions.stream()
                .filter(question -> question.getUserAnswer() != null && !question.getUserAnswer().isBlank())
                .count();
        TaskType latestTaskType = latestTaskType(session.getStatus());
        return new SessionDetailResponse(
                session.getId(),
                session.getResumeId(),
                resume == null ? null : resume.getFileName(),
                session.getStatus(),
                session.getOverallGrade(),
                session.getOverallFeedback(),
                session.getImprovementSuggestions(),
                session.getFailureReason(),
                answeredCount,
                questions.size(),
                latestTaskType == null ? null : taskService.latestTask(BizType.SESSION, sessionId, latestTaskType),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    public List<SessionDetailResponse> list(Long resumeId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        return sessionMapper.selectList(new LambdaQueryWrapper<InterviewSession>()
                        .eq(InterviewSession::getResumeId, resumeId)
                        .orderByDesc(InterviewSession::getCreatedAt)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(session -> get(session.getId()))
                .toList();
    }

    @Transactional
    public SessionDetailResponse saveAnswers(Long sessionId, List<AnswerSaveRequest.AnswerItem> answers) {
        InterviewSession session = requireSession(sessionId);
        if (SessionStatus.COMPLETED.name().equals(session.getStatus()) || SessionStatus.EVALUATING.name().equals(session.getStatus())) {
            throw new IllegalStateException("当前会话不可继续编辑答案");
        }
        if (answers != null) {
            for (AnswerSaveRequest.AnswerItem answer : answers) {
                InterviewQuestion question = questionMapper.selectById(answer.questionId());
                if (question != null && sessionId.equals(question.getSessionId())) {
                    question.setUserAnswer(answer.answer());
                    questionMapper.updateById(question);
                }
            }
        }
        if (SessionStatus.READY.name().equals(session.getStatus())) {
            session.setStatus(SessionStatus.ANSWERING.name());
            sessionMapper.updateById(session);
        }
        return get(sessionId);
    }

    @Transactional
    public SessionDetailResponse submit(Long sessionId, List<AnswerSaveRequest.AnswerItem> answers) {
        InterviewSession session = requireSession(sessionId);
        if (SessionStatus.COMPLETED.name().equals(session.getStatus()) || SessionStatus.EVALUATING.name().equals(session.getStatus())) {
            return get(sessionId);
        }
        saveAnswers(sessionId, answers);
        session = requireSession(sessionId);
        session.setStatus(SessionStatus.EVALUATING.name());
        sessionMapper.updateById(session);

        int retryCount = taskService.nextRetryCount(BizType.SESSION, sessionId, TaskType.SESSION_EVALUATE);
        InterviewTask task = taskService.createTask(BizType.SESSION, sessionId, TaskType.SESSION_EVALUATE, retryCount, "提交面试答案");
        taskService.markRunning(task.getId());
        try {
            evaluateSession(session);
            taskService.markSuccess(task.getId(), "面试评估完成");
        } catch (Exception e) {
            session.setStatus(SessionStatus.EVALUATE_FAILED.name());
            session.setFailureReason(e.getMessage());
            sessionMapper.updateById(session);
            taskService.markFailed(task.getId(), e.getMessage());
        }
        return get(sessionId);
    }

    public SessionDetailResponse retryGenerate(Long sessionId) {
        InterviewSession session = requireSession(sessionId);
        if (!SessionStatus.GENERATE_FAILED.name().equals(session.getStatus())) {
            throw new IllegalStateException("当前会话不需要重新生成题目");
        }
        InterviewResume resume = requireAnalyzedResume(session.getResumeId());
        session.setStatus(SessionStatus.GENERATING.name());
        session.setFailureReason(null);
        sessionMapper.updateById(session);

        int retryCount = taskService.nextRetryCount(BizType.SESSION, sessionId, TaskType.QUESTION_GENERATE);
        InterviewTask task = taskService.createTask(BizType.SESSION, sessionId, TaskType.QUESTION_GENERATE, retryCount, resume.getFileName());
        taskService.markRunning(task.getId());
        try {
            generateQuestions(session, resume);
            session.setStatus(SessionStatus.READY.name());
            sessionMapper.updateById(session);
            taskService.markSuccess(task.getId(), "已重新生成 8 道面试题");
        } catch (Exception e) {
            session.setStatus(SessionStatus.GENERATE_FAILED.name());
            session.setFailureReason(e.getMessage());
            sessionMapper.updateById(session);
            taskService.markFailed(task.getId(), e.getMessage());
        }
        return get(sessionId);
    }

    public SessionDetailResponse retryEvaluate(Long sessionId) {
        InterviewSession session = requireSession(sessionId);
        if (!SessionStatus.EVALUATE_FAILED.name().equals(session.getStatus())) {
            throw new IllegalStateException("当前会话不需要重新评估");
        }
        return submit(sessionId, List.of());
    }

    public QuestionListResponse questionsBySession(Long sessionId) {
        List<InterviewQuestion> questions = questions(sessionId);
        Map<Integer, List<InterviewQuestion>> grouped = questions.stream()
                .collect(Collectors.groupingBy(InterviewQuestion::getRoundNumber));
        List<QuestionListResponse.RoundGroup> rounds = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<InterviewQuestion> roundQuestions = entry.getValue().stream()
                            .sorted(Comparator.comparing(InterviewQuestion::getQuestionNumber))
                            .toList();
                    InterviewQuestion first = roundQuestions.getFirst();
                    List<QuestionListResponse.QuestionItem> items = roundQuestions.stream()
                            .map(question -> new QuestionListResponse.QuestionItem(
                                    question.getId(),
                                    question.getQuestionNumber(),
                                    question.getContent(),
                                    question.getUserAnswer(),
                                    question.getFeedback()
                            ))
                            .toList();
                    return new QuestionListResponse.RoundGroup(first.getRoundNumber(), first.getRoundName(), first.getDifficulty(), items);
                })
                .toList();
        return new QuestionListResponse(sessionId, rounds);
    }

    public ReportResponse report(Long sessionId) {
        InterviewSession session = requireSession(sessionId);
        if (session.getReportHtmlPath() == null || session.getReportHtmlPath().isBlank()) {
            throw new IllegalStateException("面试报告尚未生成");
        }
        return new ReportResponse(sessionId, "html", minioService.getObject(session.getReportHtmlPath()));
    }

    private InterviewResume requireAnalyzedResume(Long resumeId) {
        InterviewResume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new IllegalArgumentException("简历不存在: " + resumeId);
        }
        if (!ResumeStatus.ANALYZED.name().equals(resume.getStatus())) {
            throw new IllegalStateException("简历未完成分析，不能开始面试");
        }
        return resume;
    }

    private InterviewSession requireSession(Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("面试会话不存在: " + sessionId);
        }
        return session;
    }

    private List<InterviewQuestion> questions(Long sessionId) {
        return questionMapper.selectList(new LambdaQueryWrapper<InterviewQuestion>()
                .eq(InterviewQuestion::getSessionId, sessionId)
                .orderByAsc(InterviewQuestion::getRoundNumber)
                .orderByAsc(InterviewQuestion::getQuestionNumber));
    }

    private TaskType latestTaskType(String status) {
        if (SessionStatus.GENERATING.name().equals(status) || SessionStatus.GENERATE_FAILED.name().equals(status)) {
            return TaskType.QUESTION_GENERATE;
        }
        if (SessionStatus.EVALUATING.name().equals(status) || SessionStatus.EVALUATE_FAILED.name().equals(status) || SessionStatus.COMPLETED.name().equals(status)) {
            return TaskType.SESSION_EVALUATE;
        }
        return null;
    }

    private void evaluateSession(InterviewSession session) {
        InterviewResume resume = requireAnalyzedResume(session.getResumeId());
        List<InterviewQuestion> questions = questions(session.getId());
        if (questions.isEmpty()) {
            throw new IllegalStateException("面试题目为空，无法评估");
        }
        ResumeAnalysisResult analysisResult = readJson(InterviewResumeService.analysisResultPath(resume.getId()), ResumeAnalysisResult.class);
        InterviewPlanResult planResult = readJson(resume.getInterviewPlanPath(), InterviewPlanResult.class);
        EvaluationResult result = evaluationAgent.evaluate(analysisResult, planResult, questions);
        Map<Long, String> feedbackByQuestionId = result.questionFeedbacks().stream()
                .collect(Collectors.toMap(EvaluationResult.QuestionFeedback::questionId, EvaluationResult.QuestionFeedback::feedback));
        for (InterviewQuestion question : questions) {
            question.setFeedback(feedbackByQuestionId.get(question.getId()));
            questionMapper.updateById(question);
        }
        session.setStatus(SessionStatus.COMPLETED.name());
        session.setFailureReason(null);
        session.setOverallGrade(result.overallGrade());
        session.setOverallFeedback(result.overallFeedback());
        session.setImprovementSuggestions(String.join("\n", result.improvementSuggestions()));
        session.setReportMdPath("interview/session/" + session.getId() + "/report.md");
        session.setReportHtmlPath("interview/session/" + session.getId() + "/report.html");
        minioService.putObject(session.getReportMdPath(), result.markdownReport());
        minioService.putObject(session.getReportHtmlPath(), result.htmlReport());
        sessionMapper.updateById(session);
    }

    private void generateQuestions(InterviewSession session, InterviewResume resume) throws Exception {
        ResumeAnalysisResult analysisResult = readJson(InterviewResumeService.analysisResultPath(resume.getId()), ResumeAnalysisResult.class);
        InterviewPlanResult planResult = readJson(resume.getInterviewPlanPath(), InterviewPlanResult.class);
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(InterviewQuestionState.RESUME_ID, resume.getId());
        inputs.put(InterviewQuestionState.SESSION_ID, session.getId());
        inputs.put(InterviewQuestionState.RESUME_ANALYSIS_RESULT, analysisResult);
        inputs.put(InterviewQuestionState.INTERVIEW_PLAN_RESULT, planResult);
        OverAllState finalState = interviewQuestionGraph.buildGraph()
                .compile()
                .invoke(inputs)
                .orElseThrow(() -> new IllegalStateException("题目生成工作流未返回结果"));
        List<GeneratedQuestionResult> questions = objectMapper.convertValue(
                finalState.value(InterviewQuestionState.MERGED_QUESTIONS)
                        .orElseThrow(() -> new IllegalStateException("题目生成工作流缺少合并结果")),
                new TypeReference<>() {
                });
        saveGeneratedQuestions(session.getId(), questions);
    }

    private <T> T readJson(String path, Class<T> type) {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("面试生成依赖文件不存在");
        }
        try {
            return objectMapper.readValue(minioService.getObject(path), type);
        } catch (Exception e) {
            throw new IllegalStateException("面试生成依赖文件解析失败: " + path, e);
        }
    }

    private void saveGeneratedQuestions(Long sessionId, List<GeneratedQuestionResult> questions) {
        questionMapper.delete(new LambdaQueryWrapper<InterviewQuestion>().eq(InterviewQuestion::getSessionId, sessionId));
        for (GeneratedQuestionResult generatedQuestion : questions) {
            InterviewQuestion question = new InterviewQuestion();
            question.setSessionId(sessionId);
            question.setRoundNumber(generatedQuestion.roundNumber());
            question.setRoundName(generatedQuestion.roundName());
            question.setDifficulty(generatedQuestion.difficulty());
            question.setQuestionNumber(generatedQuestion.questionNumber());
            question.setContent(generatedQuestion.content());
            question.setScoringPoints(String.join("\n", generatedQuestion.scoringPoints()));
            questionMapper.insert(question);
        }
    }
}
