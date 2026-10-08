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
                        new Module("clubs", "社团资料", "READY", "社团查询、负责人创建与资料维护、招新状态与条件维护"),
                        new Module("activities", "活动管理", "READY", "草稿、编辑、确认发布、取消及报名者通知；负责人开启签到与学生签到"),
                        new Module("knowledge", "资料问答", "READY", "公开资料上传、分块、检索、出处与会话记录"),
                        new Module("identity", "身份与权限", "READY", "学生注册、个人资料与兴趣、Session 登录、BCrypt、CSRF及所属社团权限"),
                        new Module("recruitment", "招新与社员", "READY", "招新条件维护、入社申请、审核、撤回与数据库成员关系"),
                        new Module("registration", "活动报名", "READY", "账号绑定报名、取消、截止及名额约束、负责人名单与站内通知"),
                        new Module("recommendation", "兴趣推荐", "READY", "自由文本兴趣与社团向量排序接口已实现；需配置 Embedding，仅过滤招新开关，不自动判断文字资格或课表冲突"),
                        new Module("assistant", "活动策划", "PLANNED", "业务工具查询、草稿生成与人工确认"),
                        new Module("agent", "ReAct / MCP / DAG", "PLANNED", "核心业务稳定后逐项接入"),
                        new Module("notifications", "站内通知", "READY", "审核、报名及取消通知入库；账号隔离、分页与已读状态"),
                        new Module("finance", "经费台账", "READY", "活动预算、支出防重、作废留痕与社团汇总；不含支付或多级审批"),
                        new Module("favorites", "账号收藏", "READY", "账号绑定收藏、重复收藏幂等；前端收藏页面尚未接入")));
    }

    public record Module(String key, String name, String status, String description) {}
}
