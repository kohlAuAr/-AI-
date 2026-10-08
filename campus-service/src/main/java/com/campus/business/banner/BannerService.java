package com.campus.business.banner;

import com.campus.business.activity.*;
import com.campus.business.club.*;
import com.campus.business.identity.*;
import java.io.*;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static com.campus.business.banner.BannerController.TargetType;

@Service
@Transactional(readOnly = true)
public class BannerService {
    private final BannerRepository banners;
    private final ClubRepository clubs;
    private final ActivityRepository activities;
    private final IdentityService identity;
    public BannerService(BannerRepository banners, ClubRepository clubs, ActivityRepository activities, IdentityService identity) {
        this.banners = banners; this.clubs = clubs; this.activities = activities; this.identity = identity;
    }
    public List<BannerView> published() {
        return banners.findAllByOrderBySortOrderAscIdAsc().stream().filter(b -> status(b).equals("VISIBLE")).map(b -> view(b, false)).toList();
    }
    public List<BannerView> managed(Principal user) {
        admin(user); return banners.findAllByOrderBySortOrderAscIdAsc().stream().map(b -> view(b, true)).toList();
    }
    public List<Target> targets(Principal user) {
        admin(user);
        List<Target> result = new ArrayList<>();
        for (Club club : clubs.findAllByOrderByIdAsc()) {
            result.add(new Target("CLUB", club.getId(), club.getName()));
            if (club.isRecruiting()) result.add(new Target("RECRUITMENT", club.getId(), club.getName()));
        }
        activities.findByStatusOrderByStartTimeAsc("PUBLISHED").stream().filter(a -> a.getStartTime().isAfter(LocalDateTime.now()))
                .forEach(a -> result.add(new Target("ACTIVITY", a.getId(), a.getTitle())));
        return result;
    }
    @Transactional
    public BannerView create(Principal user, BannerController.BannerRequest body, MultipartFile file) {
        Account actor = admin(user); validate(body); Image image = upload(file);
        return view(banners.save(new Banner(body, image.bytes(), image.type(), actor.getId())), true);
    }
    @Transactional
    public BannerView update(Principal user, Long id, BannerController.BannerRequest body, MultipartFile file) {
        Account actor = admin(user); Banner banner = find(id); validate(body);
        if (file != null) { Image image = upload(file); banner.replaceImage(image.bytes(), image.type()); }
        banner.update(body, actor.getId()); return view(banner, true);
    }
    @Transactional
    public BannerView visibility(Principal user, Long id, boolean enabled) {
        Account actor = admin(user); Banner banner = find(id);
        if (enabled && (!target(banner.getTargetType(), banner.getTargetId()).valid() || (banner.getEndsAt() != null && !banner.getEndsAt().isAfter(LocalDateTime.now()))))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "关联内容已失效或展示已结束，请先编辑海报");
        banner.setEnabled(enabled, actor.getId()); return view(banner, true);
    }
    public Banner publicImage(Long id) {
        Banner banner = find(id);
        if (!status(banner).equals("VISIBLE")) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "海报未展示");
        return banner;
    }
    public Banner preview(Principal user, Long id) { admin(user); return find(id); }
    private Account admin(Principal principal) {
        Account user = identity.current(principal);
        if (!user.getRole().equals("PLATFORM_ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅平台管理员可管理首页内容");
        return user;
    }
    private Banner find(Long id) { return banners.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "海报不存在")); }
    private void validate(BannerController.BannerRequest body) {
        if (body.startsAt() != null && body.endsAt() != null && !body.endsAt().isAfter(body.startsAt()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "展示结束时间必须晚于开始时间");
        if (!target(body.targetType().name(), body.targetId()).valid())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择有效社团、开放招新或尚未开始的已发布活动");
    }
    private Destination target(String type, Long id) {
        if (type.equals(TargetType.ACTIVITY.name())) {
            Activity activity = activities.findById(id).orElse(null);
            boolean valid = activity != null && "PUBLISHED".equals(activity.getStatus()) && activity.getStartTime().isAfter(LocalDateTime.now());
            return new Destination(valid, activity == null ? "活动不存在" : activity.getTitle(), "/activities/" + id);
        }
        Club club = clubs.findById(id).orElse(null);
        return new Destination(club != null && (!type.equals(TargetType.RECRUITMENT.name()) || club.isRecruiting()),
                club == null ? "社团不存在" : club.getName(), "/clubs/" + (club == null || club.getSlug() == null ? id : club.getSlug()));
    }
    private String status(Banner banner) {
        if (!banner.isEnabled()) return "OFFLINE";
        if (!target(banner.getTargetType(), banner.getTargetId()).valid()) return "TARGET_UNAVAILABLE";
        LocalDateTime now = LocalDateTime.now();
        if (banner.getEndsAt() != null && !banner.getEndsAt().isAfter(now)) return "EXPIRED";
        if (banner.getStartsAt() != null && banner.getStartsAt().isAfter(now)) return "SCHEDULED";
        return "VISIBLE";
    }
    private BannerView view(Banner banner, boolean managed) {
        Destination target = target(banner.getTargetType(), banner.getTargetId());
        String imageUrl = (managed ? "/api/platform/banners/" : "/api/banners/") + banner.getId() + "/image";
        return new BannerView(banner.getId(), banner.getTitle(), banner.getTargetType(), banner.getTargetId(), target.title(), target.path(),
                imageUrl, banner.getSortOrder(), banner.isEnabled(), banner.getStartsAt(), banner.getEndsAt(), status(banner), banner.getUpdatedAt());
    }
    private Image upload(MultipartFile file) {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择海报图片");
        if (file.getSize() > 2 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "海报文件不能超过 2MB");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Not an image");
            var reader = readers.next();
            try {
                reader.setInput(input); String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!List.of("png", "jpeg", "jpg").contains(format) || reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096) throw new IOException("Unsupported image");
                var output = new ByteArrayOutputStream();
                ImageIO.write(reader.read(0), format, output); // Decode and re-encode; do not trust extension, MIME, or embedded metadata.
                if (output.size() > 2 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "处理后的海报超过 2MB，请压缩图片");
                return new Image(output.toByteArray(), format.equals("png") ? "image/png" : "image/jpeg");
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持可读取的 PNG/JPG 图片，宽高各不超过 4096 像素");
        }
    }
    private record Image(byte[] bytes, String type) {}
    private record Destination(boolean valid, String title, String path) {}
    public record Target(String type, Long id, String title) {}
    public record BannerView(Long id, String title, String targetType, Long targetId, String targetTitle, String targetPath,
                             String imageUrl, int sortOrder, boolean enabled, LocalDateTime startsAt, LocalDateTime endsAt,
                             String displayStatus, LocalDateTime updatedAt) {}
}
