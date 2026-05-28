package com.highway.agent.chat.memory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.highway.agent.chat.model.ChatMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT * FROM chat_message WHERE conversation_id = #{conversationId} ORDER BY created_at ASC LIMIT #{lastN}")
    List<ChatMessage> selectLastN(@Param("conversationId") String conversationId, @Param("lastN") int lastN);

    @Delete("DELETE FROM chat_message WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(@Param("conversationId") String conversationId);

    @Select("SELECT conversation_id FROM chat_message GROUP BY conversation_id ORDER BY MAX(created_at) DESC")
    List<String> selectConversationIds();
}
