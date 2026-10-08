package com.campus.business.banner;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class BannerController {
    private final BannerService banners;
    public BannerController(BannerService banners) { this.banners = banners; }
    @GetMapping("/banners")
    public List<BannerService.BannerView> published() { return banners.published(); }
    @GetMapping("/platform/banners")
    public List<BannerService.BannerView> managed(Principal user) { return banners.managed(user); }
    @GetMapping("/platform/banner-targets")
    public List<BannerService.Target> targets(Principal user) { return banners.targets(user); }
    @PostMapping(value = "/platform/banners", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BannerService.BannerView create(Principal user, @Valid @RequestPart("metadata") BannerRequest body, @RequestPart("image") MultipartFile image) {
        return banners.create(user, body, image);
    }
    @PutMapping(value = "/platform/banners/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BannerService.BannerView update(Principal user, @PathVariable Long id, @Valid @RequestPart("metadata") BannerRequest body, @RequestPart(value = "image", required = false) MultipartFile image) {
        return banners.update(user, id, body, image);
    }
    @PostMapping("/platform/banners/{id}/visibility")
    public BannerService.BannerView visibility(Principal user, @PathVariable Long id, @Valid @RequestBody Visibility body) {
        return banners.visibility(user, id, body.enabled());
    }
    @GetMapping("/banners/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) { return imageResponse(banners.publicImage(id)); }
    @GetMapping("/platform/banners/{id}/image")
    public ResponseEntity<byte[]> preview(Principal user, @PathVariable Long id) { return imageResponse(banners.preview(user, id)); }
    private ResponseEntity<byte[]> imageResponse(Banner banner) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(banner.getContentType())).body(banner.getImage());
    }
    public enum TargetType { CLUB, RECRUITMENT, ACTIVITY }
    public record BannerRequest(@NotBlank @Size(max = 80) String title, @NotNull TargetType targetType,
                                @NotNull @Positive Long targetId, @Min(0) @Max(999) int sortOrder,
                                LocalDateTime startsAt, LocalDateTime endsAt) {}
    public record Visibility(@NotNull Boolean enabled) {}
}
