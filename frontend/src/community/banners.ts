export interface Banner {
  id: number; title: string; targetType: 'CLUB' | 'RECRUITMENT' | 'ACTIVITY'; targetId: number;
  targetTitle: string; targetPath: string; imageUrl: string; sortOrder: number; enabled: boolean;
  startsAt: string | null; endsAt: string | null; displayStatus: string; updatedAt: string;
}
export interface BannerTarget { type: Banner['targetType']; id: number; title: string }
export const bannerStatus: Record<string, string> = { OFFLINE: '已下架', VISIBLE: '展示中', SCHEDULED: '等待开始', EXPIRED: '展示已结束', TARGET_UNAVAILABLE: '关联内容已失效' };
export const bannerType = { CLUB: '社团介绍', RECRUITMENT: '社团招新', ACTIVITY: '已发布活动' };
