package com.campus.business.system;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class SystemController {
    @GetMapping("/api/system")
    public Map<String, Object> overview() {
        return Map.of(
                "name", "校园社团平台 · 开发骨架",
                "security", "LOCAL_DEMO_ONLY",
                "modules", List.of(
                        new Module("clubs", "社团资料", "SAMPLE", "已有示例列表与详情接口；维护功能待实现"),
                        new Module("activities", "活动管理", "SAMPLE", "已有示例列表；发布、取消与报名待实现"),
                        new Module("knowledge", "资料问答", "READY", "公开资料上传、分块、检索、出处与会话记录"),
                        new Module("identity", "身份与权限", "PLANNED", "登录、JWT、所属社团权限校验"),
                        new Module("recruitment", "招新与社员", "PLANNED", "申请、审核、撤回与成员关系"),
                        new Module("registration", "活动报名", "PLANNED", "报名、取消、名额约束与通知"),
                        new Module("recommendation", "兴趣推荐", "PLANNED", "兴趣画像、语义与标签匹配、业务条件过滤"),
                        new Module("assistant", "活动策划", "PLANNED", "业务工具查询、草稿生成与人工确认"),
                        new Module("agent", "ReAct / MCP / DAG", "PLANNED", "核心业务稳定后逐项接入")));
    }

    public record Module(String key, String name, String status, String description) {}
}
