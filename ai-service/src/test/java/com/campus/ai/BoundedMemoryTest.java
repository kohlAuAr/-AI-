package com.campus.ai;

import com.campus.ai.chat.*;
import com.campus.ai.knowledge.KnowledgeService;
import com.campus.ai.security.AiActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BoundedMemoryTest {
    @Test void shortContextReadsOnlyLastSixAndRetainsChronologicalOrderAndOwnership() {
        var turns = mock(ConversationRepository.class); var owners = mock(AiConversationRepository.class);
        var memory = mock(SessionMemory.class); var knowledge = mock(KnowledgeService.class);
        var actor = new AiActor(12, "STUDENT", Set.of(), Set.of()); String id = UUID.randomUUID().toString();
        when(owners.existsById(id)).thenReturn(true); when(owners.findById(id)).thenReturn(Optional.of(new AiConversation(id, 12L)));
        List<ConversationTurn> last = new ArrayList<>();
        for (int i = 9; i >= 4; i--) last.add(new ConversationTurn(id, "问题" + i, "答案" + i, "[]", "LOCAL", "KEYWORD"));
        when(turns.findTop6ByConversationIdOrderByIdDesc(id)).thenReturn(last);
        when(memory.recent(anyString(), eq(id), anyList())).thenAnswer(call -> call.getArgument(2));
        var chat = new ChatService(null, knowledge, null, null, turns, owners, memory, new ObjectMapper());
        var messages = chat.recent(actor, id);
        assertThat(messages).hasSize(12); assertThat(messages.get(0).get("content")).isEqualTo("问题4");
        assertThat(messages.get(11).get("content")).isEqualTo("答案9");
        verify(turns, never()).findByConversationIdOrderByIdAsc(anyString());
        assertThatThrownBy(() -> chat.recent(new AiActor(13, "STUDENT", Set.of(), Set.of()), id)).hasMessageContaining("不属于当前账号");
    }
}
