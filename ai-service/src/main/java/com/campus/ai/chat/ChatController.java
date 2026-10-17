package com.campus.ai.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import com.campus.ai.security.AiActor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
public class ChatController {
    private final ChatService chat;

    public ChatController(ChatService chat) { this.chat = chat; }

    @PostMapping("/chat")
    public ChatService.Reply ask(@RequestAttribute AiActor actor, @Valid @RequestBody Question body) {
        return chat.ask(body.question().trim(), body.conversationId(), actor);
    }

    @GetMapping("/conversations/{id}")
    public List<ChatService.TurnView> history(@RequestAttribute AiActor actor, @PathVariable String id) { return chat.history(actor, id); }
    @GetMapping("/conversations")
    public List<ChatService.SessionView> sessions(@RequestAttribute AiActor actor) { return chat.sessions(actor); }

    public record Question(@NotBlank @Size(max = 2000) String question,
                           @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String conversationId) {}
}
