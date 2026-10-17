package com.campus.ai;

import com.campus.ai.agent.*;
import com.campus.ai.chat.ChatService;
import com.campus.ai.client.ModelClient;
import com.campus.ai.config.AiSettings;
import com.campus.ai.knowledge.HybridSearchService;
import com.campus.ai.security.AiActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Scripted model outputs prove orchestration boundaries, not real-model answer quality. */
class AgentServiceTest {
    ModelClient models; BusinessToolClient tools; ChatService chat; AgentService agent;
    ObjectMapper json = new ObjectMapper(); AiActor actor = new AiActor(42, "STUDENT", Set.of(), Set.of());
    AiSettings settings(AiSettings.Mode mode) { return new AiSettings(mode, false, new AiSettings.Provider("http://fixture", "fixture", ""), new AiSettings.Provider("http://fixture", "fixture", ""), AiSettings.EmbeddingProvider.NONE); }
    @BeforeEach void setup() {
        models = mock(ModelClient.class); tools = mock(BusinessToolClient.class); chat = mock(ChatService.class);
        when(chat.conversation(actor, null)).thenReturn("1e9167e0-74ba-4548-b950-60ffd3c23470");
        when(chat.boundedHistory(any(), anyString())).thenReturn(List.of());
        when(chat.save(any(), anyString(), anyString(), anyString(), anyList(), anyString(), anyList())).thenAnswer(call ->
                new ChatService.Reply(call.getArgument(1), call.getArgument(3), call.getArgument(4), "OLLAMA", call.getArgument(5), Instant.now(), call.getArgument(6)));
        agent = new AgentService(models, settings(AiSettings.Mode.OLLAMA), tools, mock(HybridSearchService.class), chat, json);
    }
    @Test void modelChoosesRealReadToolsAndObservesResultsBeforeFinishing() throws Exception {
        when(models.chat(anyList())).thenReturn("{\"action\":\"search_clubs\",\"query\":\"摄影\"}", "{\"action\":\"get_club\",\"id\":10}", "{\"action\":\"finish\",\"answer\":\"光影摄影社 id=10 招新中\"}");
        when(tools.call(actor, "search_clubs", "摄影", null)).thenReturn(json.readTree("{\"items\":[{\"id\":10}]}"));
        when(tools.call(actor, "get_club", "", 10L)).thenReturn(json.readTree("{\"id\":10,\"name\":\"光影摄影社\",\"recruiting\":true}"));
        var result = agent.ask(actor, "帮我了解摄影社", null);
        assertThat(result.retrieval()).isEqualTo("REACT_TOOLS"); assertThat(result.steps()).hasSize(2);
        assertThat(result.steps().get(1).observation()).contains("光影摄影社");
        verify(models, times(3)).chat(argThat(messages -> messages.get(0).get("role").equals("system")));
        verify(tools).call(actor, "get_club", "", 10L);
    }
    @Test void finishWithoutEvidenceAndWriteToolsAreRejected() {
        when(models.chat(anyList())).thenReturn("{\"action\":\"finish\",\"answer\":\"已报名\"}");
        assertThatThrownBy(() -> agent.ask(actor, "报名", null)).hasMessageContaining("未先查询依据");
        when(models.chat(anyList())).thenReturn("{\"action\":\"sign_up_activity\",\"id\":10}");
        assertThatThrownBy(() -> agent.ask(actor, "报名", null)).hasMessageContaining("只读工具范围");
        verifyNoInteractions(tools); verify(chat, never()).save(any(), any(), any(), any(), any(), any(), any());
    }
    @Test void loopAndMalformedOutputsFailWithoutInventingATurn() throws Exception {
        when(models.chat(anyList())).thenReturn("{\"action\":\"search_clubs\"}");
        when(tools.call(actor, "search_clubs", "", null)).thenReturn(json.readTree("{\"items\":[]}"));
        assertThatThrownBy(() -> agent.ask(actor, "社团", null)).hasMessageContaining("步数上限");
        verify(tools, times(3)).call(actor, "search_clubs", "", null);
        when(models.chat(anyList())).thenReturn("没有 JSON");
        assertThatThrownBy(() -> agent.ask(actor, "社团", null)).hasMessageContaining("结构化结果");
        verify(chat, never()).save(any(), any(), any(), any(), any(), any(), any());
    }
    @Test void localModeIsExplicitFixedQueryNotReAct() throws Exception {
        agent = new AgentService(models, settings(AiSettings.Mode.LOCAL), tools, mock(HybridSearchService.class), chat, json);
        when(tools.call(actor, "my_registrations", "", null)).thenReturn(json.readTree("{\"items\":[]}"));
        var reply = agent.ask(actor, "我报名了什么", null);
        assertThat(reply.retrieval()).isEqualTo("FIXED_TOOLS"); assertThat(reply.answer()).contains("未启用 ReAct");
        verifyNoInteractions(models);
    }
    @Test void planningOnlyReturnsTextAndRequiresOwnedManager() {
        var plans = new PlanningController(models, settings(AiSettings.Mode.OLLAMA), agent);
        var input = new PlanningController.Input(10L, "光影摄影社", "学习摄影", "新手交流");
        assertThatThrownBy(() -> plans.plan(actor, input)).hasMessageContaining("所属负责人");
        var manager = new AiActor(7, "MANAGER", Set.of(10L), Set.of(10L));
        when(models.chat(anyList())).thenReturn("{\"title\":\"构图交流\",\"description\":\"作品分享；时间地点待负责人确认\"}");
        var plan = plans.plan(manager, input);
        assertThat(UUID.fromString(plan.requestId())).isNotNull(); assertThat(plan.title()).isEqualTo("构图交流");
        verifyNoInteractions(tools); verify(chat, never()).save(any(), any(), any(), any(), any(), any(), any());
    }
}
