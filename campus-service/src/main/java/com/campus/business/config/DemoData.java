package com.campus.business.config;

import com.campus.business.club.*;
import com.campus.business.activity.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
public class DemoData implements CommandLineRunner {
    private final ClubRepository clubs;
    private final ActivityRepository activities;

    public DemoData(ClubRepository clubs, ActivityRepository activities) {
        this.clubs = clubs;
        this.activities = activities;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (clubs.count() > 0) return;
        List<Club> saved = clubs.saveAll(List.of(
                new Club("程序设计社（示例）", "科技实践", "面向编程新手开展 Python 学习与小项目交流。", "Python,编程,科普", "示例校区"),
                new Club("摄影社（示例）", "文化艺术", "围绕校园摄影、构图与影像记录开展交流。", "摄影,构图,影像", "示例校区"),
                new Club("志愿服务社（示例）", "公益服务", "组织公益交流与志愿服务经验分享。", "公益,志愿服务", "示例校区")));
        activities.saveAll(List.of(
                new Activity(saved.get(0).getId(), "Python 入门交流（示例）", "示例教室 A101", LocalDateTime.of(2026, 10, 17, 14, 0), 30),
                new Activity(saved.get(1).getId(), "校园摄影分享（示例）", "示例教室 B102", LocalDateTime.of(2026, 10, 18, 15, 0), 25)));
    }
}
