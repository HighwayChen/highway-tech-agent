package com.highway.agent.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.highway.agent.interview.model.InterviewQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {
}
