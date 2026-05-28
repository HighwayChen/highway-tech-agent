package com.highway.agent.interview.model;

public final class InterviewEnums {

    private InterviewEnums() {
    }

    public enum ResumeStatus {
        UPLOADED,
        ANALYZING,
        ANALYZED,
        FAILED
    }

    public enum SessionStatus {
        GENERATING,
        READY,
        ANSWERING,
        EVALUATING,
        COMPLETED,
        GENERATE_FAILED,
        EVALUATE_FAILED
    }

    public enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED
    }

    public enum TaskType {
        RESUME_ANALYZE,
        QUESTION_GENERATE,
        SESSION_EVALUATE
    }

    public enum BizType {
        RESUME,
        SESSION
    }
}
