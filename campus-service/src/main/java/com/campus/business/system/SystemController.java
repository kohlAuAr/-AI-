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
                        new Module("clubs", "社团资料", "SAMPLE", "数据库社团与招新条件查询；社团维护功能待实现"),
                        new Module("activities", "活动管理", "READY", "负责人草稿与确认发布、学生查询；活动编辑、下架及签到待实现"),
                        new Module("knowledge", "资料问答", "READY", "公开资料上传、分块、检索、出处与会话记录"),
                        new Module("identity", "身份与权限", "READY", "Session 登录、BCrypt、CSRF及负责人所属社团校验；注册与完整资料权限待实现"),
                        new Module("recruitment", "招新与社员", "READY", "入社申请、审核、撤回与数据库成员关系；招新条件维护待实现"),
                        new Module("registration", "活动报名", "READY", "账号绑定报名、取消、截止及名额约束、负责人名单；通知待实现"),
                        new Module("recommendation", "兴趣推荐", "PLANNED", "兴趣画像、语义与标签匹配、业务条件过滤"),
                        new Module("assistant", "活动策划", "PLANNED", "业务工具查询、草稿生成与人工确认"),
                        new Module("agent", "ReAct / MCP / DAG", "PLANNED", "核心业务稳定后逐项接入")));
    }

    public record Module(String key, String name, String status, String description) {}
}
