# 首页轮播视觉资源

2026-10-19：首页使用全幅图片、渐变遮罩与 HTML 标题，底部集中展示页码、切换和暂停控件。桌面横幅与手机竖幅分别排版；图片响应式裁切，重要主体宜放在中间偏右，文字不要烘焙在图片里。

三个示例背景由内置 imagegen 生成，属于虚构校园场景，不是真实学校或社团照片。原 PNG 留在生成目录，使用 Sharp 等比缩至 1536×864 并编码为 JPEG（质量 86）；没有对图片追加文字。资源位于 `campus-service/src/main/resources/demo-banners/`：

- `photo-editorial.jpg`：校园摄影，约 199 KB。
- `code-editorial.jpg`：编程工作坊，约 141 KB。
- `music-editorial.jpg`：草坪弹唱，约 147 KB。

新数据库仅在 demo 海报表为空时创建它们。已有数据库不自动迁移；开发者可显式运行 `node scripts/refresh-demo-banner-artwork.mjs`，使用本地 demo 管理员通过原有 Session/CSRF 上传接口替换。脚本只匹配字节完全一致的原始内置 SVG，先把原图和元数据备份至忽略目录 `.run/banner-artwork-backup-*/`；保持原有标题、排序、关联目标、展示时间与上下架状态，跳过其他图片。重复运行不会再次覆盖 JPEG。该脚本不是正式环境管理员配置或并发版本锁。

## 本次验证

- 前端 `npm run build`、`test:business`、`test:prototype` 通过；原型 228 个断言、29 条渲染路径。构建仍提示现有 JS 主包超过 500 KB，没有在本次视觉修改中拆包。
- 校园服务 `mvn -pl campus-service -DskipTests=false test`：81 项，失败 0、错误 0，2 项可选真实 Redis 测试跳过。新增测试验证三个初始 JPEG 可解码且为 1536×864。
- 本机旧示例 1/2/3 通过管理 API 仅替换图片，原 SVG 与元数据已备份；重跑脚本更新 0 张。校园服务打包、关闭数据库备份并重启后，`check-banners.mjs restored` 验证原上传 fixture 4 的 PNG 与下架状态仍保留。
- 实际浏览器检查图片加载、6 秒自动切换、暂停保持、方向键和关联社团跳转；375×812 与桌面视口检查无页面横向溢出。截图在忽略目录 `.run/banner-redesign-20261019/`。没有宣称真实手机触摸、系统减少动态效果设置或完整可访问性认证通过。

## 生成提示词

### photo

Use case: photorealistic-natural. Asset type: a sophisticated campus photography-club website banner background, landscape approximately 16:9. Original editorial photograph, NOT a UI mockup. A sunlit Chinese university campus walkway with leafy green trees and warm stone architecture; on the right third an East Asian university student aged about 21 seen from behind/three-quarter back raising a small mirrorless camera to photograph the campus, natural relaxed pose, realistic hands and equipment, quiet university life, no staged crowd. Layered foliage and dappled late-afternoon light, forest green and warm golden highlights, tasteful analog documentary photography with fine grain and real texture. The left half has darker softly blurred foliage and deep olive shadows suitable for placing white Chinese headline text later in HTML. Keep the camera/student at right but inset 12% from edges so center cropping for a phone still works. Soft contrast with rich dark greens, no artificial neon. No text, no lettering, no logos, no watermarks, no typography, no collage, no flat vector illustration, no 3D render.

### code

Use case: photorealistic-natural. Asset type: university programming-club website banner background, wide landscape approximately 16:9. An original cinematic editorial photograph of a real-looking student maker workspace at blue hour: on the right half a laptop on a wooden desk, a small robotics prototype with tasteful components and a softly glowing adjustable desk lamp; distant East Asian university-age students are softly blurred in background, casual authentic workshop atmosphere. Screen shows only indistinct colored code lines, absolutely no legible text or brand. Muted indigo and charcoal shadows, warm amber task light, photographic texture and fine film grain, refined campus publication aesthetic. Left half mostly dark out-of-focus workshop and soft ambient shapes to support white headline overlay later, not a big empty colored rectangle. Objects inset from frame edges, important subject at center-right visible under responsive cropping. No titles, no typography, no watermarks, no logos, no glossy 3D render, no sci-fi neon, no collage, no UI.

### music

Use case: photorealistic-natural. Asset type: campus music-club website banner background, wide landscape approximately 16:9. Original candid editorial photograph of a small Chinese university outdoor acoustic session at dusk. On the center-right a university-age East Asian student is seated playing an acoustic guitar naturally, medium-wide shot, three other students listening softly blurred further back on grass, subtle string lights in background, beautiful soft amber bokeh. Authentic modest campus gathering, no giant concert stage, no crowd spectacle. Rich charcoal-purple dusk shadows with warm honey light on wooden guitar and hands, gentle fine-grain analog photograph, realistic anatomy and instruments. Keep left half dark and soft with an unoccupied part of lawn / softly blurred trees so HTML white title can sit there, scene remains natural rather than a flat gradient. Important subjects away from outer edges for responsive crop. No text, no letters, no watermark, no logos, no posters, no vector art, no 3D, no UI.
