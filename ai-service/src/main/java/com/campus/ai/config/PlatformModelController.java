package com.campus.ai.config;

import com.campus.ai.security.AiActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/platform/models")
class PlatformModelController {
    private final PlatformModels models;
    PlatformModelController(PlatformModels models) { this.models = models; }
    private void require(AiActor actor) { if (actor == null || !actor.role().equals("PLATFORM_ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅平台管理员可配置模型"); }
    @GetMapping Map<String, Object> view(@RequestAttribute(required = false) AiActor actor) { require(actor); return models.view(); }
    @PostMapping("/{purpose}/save") Map<String, Object> save(@RequestAttribute(required = false) AiActor actor, @PathVariable PlatformModels.Purpose purpose, @Valid @RequestBody PlatformModels.Input input) { require(actor); return models.save(purpose, input); }
    @PostMapping("/{purpose}/test") Map<String, Object> test(@RequestAttribute(required = false) AiActor actor, @PathVariable PlatformModels.Purpose purpose, @Valid @RequestBody PlatformModels.Revision input) { require(actor); return models.test(purpose, input); }
    @PostMapping("/{purpose}/activate") Map<String, Object> activate(@RequestAttribute(required = false) AiActor actor, @PathVariable PlatformModels.Purpose purpose, @Valid @RequestBody PlatformModels.Revision input) { require(actor); return models.activate(purpose, input, actor.userId()); }
    record Discovery(@NotBlank @Size(max = 400) String baseUrl) {}
    @PostMapping("/discover") Map<String, Object> discover(@RequestAttribute(required = false) AiActor actor, @Valid @RequestBody Discovery input) { require(actor); return models.discover(input.baseUrl()); }
}
