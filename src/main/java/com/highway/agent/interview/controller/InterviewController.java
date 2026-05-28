package com.highway.agent.interview.controller;

import com.highway.agent.interview.model.AnswerSaveRequest;
import com.highway.agent.interview.model.QuestionListResponse;
import com.highway.agent.interview.model.ReportResponse;
import com.highway.agent.interview.model.ResumeDetailResponse;
import com.highway.agent.interview.model.ResumeListResponse;
import com.highway.agent.interview.model.ResumeUploadResponse;
import com.highway.agent.interview.model.SessionDetailResponse;
import com.highway.agent.interview.model.SessionStartRequest;
import com.highway.agent.interview.model.SessionSubmitRequest;
import com.highway.agent.interview.service.InterviewResumeService;
import com.highway.agent.interview.service.InterviewSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewResumeService resumeService;
    private final InterviewSessionService sessionService;

    @PostMapping(value = "/resume/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResumeUploadResponse> uploadResume(@RequestPart("file") FilePart file) {
        return resumeService.upload(file);
    }

    @PostMapping("/resume/{id}/analyze")
    public Mono<ResumeDetailResponse> analyzeResume(@PathVariable Long id) {
        return Mono.fromCallable(() -> resumeService.analyze(id))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/resume/{id}")
    public ResumeDetailResponse getResume(@PathVariable Long id) {
        return resumeService.get(id);
    }

    @GetMapping("/resume/list")
    public ResumeListResponse listResumes(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return resumeService.list(page, pageSize);
    }

    @GetMapping("/resume/{id}/report")
    public ReportResponse getResumeReport(@PathVariable Long id) {
        return resumeService.report(id);
    }

    @PostMapping("/session/start")
    public Mono<SessionDetailResponse> startSession(@RequestBody SessionStartRequest request) {
        return Mono.fromCallable(() -> sessionService.start(request.resumeId()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/session/{id}")
    public SessionDetailResponse getSession(@PathVariable Long id) {
        return sessionService.get(id);
    }

    @GetMapping("/session/list")
    public List<SessionDetailResponse> listSessions(
            @RequestParam Long resumeId,
            @RequestParam(defaultValue = "3") int limit) {
        return sessionService.list(resumeId, limit);
    }

    @PostMapping("/session/{id}/answers/save")
    public Mono<SessionDetailResponse> saveAnswers(@PathVariable Long id, @RequestBody AnswerSaveRequest request) {
        return Mono.fromCallable(() -> sessionService.saveAnswers(id, request.answers()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/session/{id}/submit")
    public Mono<SessionDetailResponse> submitSession(@PathVariable Long id, @RequestBody SessionSubmitRequest request) {
        return Mono.fromCallable(() -> sessionService.submit(id, request.answers()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/session/{id}/retry-generate")
    public Mono<SessionDetailResponse> retryGenerate(@PathVariable Long id) {
        return Mono.fromCallable(() -> sessionService.retryGenerate(id))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/session/{id}/retry-evaluate")
    public Mono<SessionDetailResponse> retryEvaluate(@PathVariable Long id) {
        return Mono.fromCallable(() -> sessionService.retryEvaluate(id))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/session/{id}/questions")
    public QuestionListResponse getQuestions(@PathVariable Long id) {
        return sessionService.questionsBySession(id);
    }

    @GetMapping("/session/{id}/report")
    public ReportResponse getSessionReport(@PathVariable Long id) {
        return sessionService.report(id);
    }
}
