package com.campus.business.config;

import com.campus.business.club.*;
import com.campus.business.activity.*;
import com.campus.business.identity.*;
import com.campus.business.membership.*;
import com.campus.business.banner.*;
import org.springframework.core.io.ClassPathResource;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Component
@Profile("demo")
public class DemoData implements CommandLineRunner {
    private final ClubRepository clubs;
    private final ActivityRepository activities;
    private final AccountRepository accounts;
    private final MembershipRepository memberships;
    private final PasswordEncoder passwords;
    private final BannerRepository banners;

    public DemoData(ClubRepository clubs, ActivityRepository activities, AccountRepository accounts, MembershipRepository memberships, PasswordEncoder passwords, BannerRepository banners) {
        this.clubs = clubs;
        this.activities = activities;
        this.accounts = accounts; this.memberships = memberships; this.passwords = passwords;
        this.banners = banners;
    }

    @Override
    @Transactional
    public void run(String... args) throws java.io.IOException {
        Club photo = seedClub("photo", "光影摄影社", "摄影社（示例）", "文化艺术", "用镜头发现校园里的小事，手机也可以成为你的第一台相机。", "摄影,艺术,户外", true, "不限专业，无需自带相机；愿意分享和参与即可。", "周三 18:30 · 双周一次", "学生中心 204");
        Club code = seedClub("code", "程序设计协会", "程序设计社（示例）", "科技实践", "通过入门工作坊、小组项目和技术交流，把想法变成可以运行的作品。", "编程,科技,创作", true, "零基础可加入；每周愿意投入一点学习时间。", "周四 19:00 · 每周一次", "实验楼 B302");
        seedClub("music", "回声音乐社", "", "文化艺术", "从弹唱到草坪音乐会，不要求专业水平，热爱就是起点。", "音乐,艺术,表演", true, "喜欢音乐，可选择器乐、演唱或幕后策划方向。", "周五 18:00 · 每周一次", "艺术楼 106");
        seedClub("hike", "山野户外社", "", "运动户外", "校园慢跑、周末轻徒步与户外知识分享，以安全和适度为原则。", "户外,运动,摄影", true, "遵守活动安全约定，选择适合自己的运动强度。", "周六 09:00 · 双周一次", "东门集合点");
        seedClub("volunteer", "暖阳志愿者协会", "志愿服务社（示例）", "公益服务", "参与校园环保、图书整理与社区服务，关注持续的小行动。", "公益,服务,环保", true, "有责任心，能够按约定参加活动。", "周日 14:00 · 按活动安排", "学生中心 102");
        seedClub("read", "纸间读书会", "", "文化艺术", "主题阅读、书籍交换与小组讨论，带着问题来交流。", "阅读,文化,表达", false, "本轮招新已结束，下轮开放时可再申请。", "周二 18:30 · 双周一次", "图书馆研讨室");
        if (activities.count() == 0) activities.saveAll(List.of(
                new Activity(code.getId(), "Python 入门交流（示例）", "示例教室 A101", LocalDateTime.of(2026, 10, 17, 14, 0), 30),
                new Activity(photo.getId(), "校园摄影分享（示例）", "示例教室 B102", LocalDateTime.of(2026, 10, 18, 15, 0), 25)));
        seedAccount("student", "林同学", "计算机科学 · 2024级", "STUDENT");
        seedAccount("student2", "陈同学", "数字媒体 · 2025级", "STUDENT");
        seedManager("photo_manager", "摄影社负责人", photo.getId());
        seedManager("code_manager", "程序设计协会负责人", code.getId());
        Account admin = seedAccount("platform_admin", "首页管理员", "本地虚构账号", "PLATFORM_ADMIN");
        if (banners.count() == 0) {
            seedBanner("用镜头发现校园", "photo", photo.getId(), BannerController.TargetType.CLUB, 10, admin.getId());
            seedBanner("把你的想法变成作品", "code", code.getId(), BannerController.TargetType.RECRUITMENT, 20, admin.getId());
            seedBanner("在社团里，找到同频的朋友", "music", clubs.findBySlug("music").orElseThrow().getId(), BannerController.TargetType.CLUB, 30, admin.getId());
        }
    }

    private Club seedClub(String slug, String name, String legacyName, String category, String description, String tags,
                          boolean recruiting, String requirements, String schedule, String place) {
        return clubs.findBySlug(slug).orElseGet(() -> {
            // Preserve old record IDs and their activity references; never overwrite existing business records.
            Club club = clubs.findByName(legacyName).filter(c -> c.isDemo() && c.getSlug() == null)
                    .orElseGet(() -> new Club(name, category, description, tags, "示例校区"));
            club.configureRecruitment(slug, recruiting, requirements, schedule, place);
            return clubs.save(club);
        });
    }
    private Account seedAccount(String username, String name, String major, String role) {
        return accounts.findByUsername(username).orElseGet(() -> accounts.save(new Account(username, passwords.encode("CampusDemo123!"), name, major, role)));
    }
    private void seedManager(String username, String name, Long clubId) {
        Account account = seedAccount(username, name, "本地虚构账号", "MANAGER");
        if (!memberships.existsByUserIdAndClubId(account.getId(), clubId)) memberships.save(new Membership(account.getId(), clubId, "MANAGER"));
    }
    private void seedBanner(String title, String illustration, Long targetId, BannerController.TargetType type, int order, Long admin) throws java.io.IOException {
        // Trusted bundled SVG illustrations only; user uploads accept decoded PNG/JPG, never SVG.
        byte[] image;
        try (var input = new ClassPathResource("demo-banners/" + illustration + ".svg").getInputStream()) { image = input.readAllBytes(); }
        Banner banner = new Banner(new BannerController.BannerRequest(title + "（示例）", type, targetId, order, null, null), image, "image/svg+xml", admin);
        banner.setEnabled(true, admin); banners.save(banner);
    }
}
