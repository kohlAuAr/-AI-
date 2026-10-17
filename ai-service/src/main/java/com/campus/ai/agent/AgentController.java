package com.campus.ai.agent;

import com.campus.ai.chat.*;
import com.campus.ai.security.AiActor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
public class AgentController {
    private final AgentService agent;
    public AgentController(AgentService agent) { this.agent = agent; }
    @PostMapping("/internal/assistant")
    public ChatService.Reply ask(@RequestAttribute AiActor actor, @Valid @RequestBody ChatController.Question input) { return agent.ask(actor, input.question().trim(), input.conversationId()); }
}
