export interface Club {
  backendId?: number;
  id: string; name: string; category: string; mark: string; color: string;
  slogan: string; description: string; tags: string[]; members: number;
  schedule: string; place: string; requirements: string; recruiting: boolean;
}
export interface Activity {
  id: string; clubId: string; title: string; category: string; date: string;
  time: string; location: string; capacity: number; enrolled: number;
  description: string; agenda: string[]; status: 'published' | 'draft'; budget: number;
}
export const categories = ['全部', '科技实践', '文化艺术', '运动户外', '公益服务'];
export const clubs: Club[] = [
  { id: 'photo', name: '光影摄影社', category: '文化艺术', mark: '光', color: 'peach', slogan: '把普通的一天，拍成值得记住的一天。', description: '用镜头发现校园里的小事。我们一起进行校园采风、摄影分享和作品展览，手机也可以成为你的第一台相机。', tags: ['摄影', '艺术', '户外'], members: 86, schedule: '周三 18:30 · 双周一次', place: '学生中心 204', requirements: '不限专业，无需自带相机；愿意分享和参与即可。', recruiting: true },
  { id: 'code', name: '程序设计协会', category: '科技实践', mark: '</>', color: 'lavender', slogan: '从第一行代码，到第一个自己的作品。', description: '面向对编程和数字创作感兴趣的同学。通过入门工作坊、小组项目和技术交流，把想法变成可以运行的作品。', tags: ['编程', '科技', '创作'], members: 112, schedule: '周四 19:00 · 每周一次', place: '实验楼 B302', requirements: '零基础可加入；每周愿意投入一点学习时间。', recruiting: true },
  { id: 'music', name: '回声音乐社', category: '文化艺术', mark: '♪', color: 'yellow', slogan: '让喜欢的旋律，在校园里发生。', description: '从弹唱、乐队排练到草坪音乐会，这里有练习的空间，也有互相倾听的人。不要求专业水平，热爱就是起点。', tags: ['音乐', '艺术', '表演'], members: 74, schedule: '周五 18:00 · 每周一次', place: '艺术楼 106', requirements: '喜欢音乐，可选择器乐、演唱或幕后策划方向。', recruiting: true },
  { id: 'hike', name: '山野户外社', category: '运动户外', mark: '山', color: 'mint', slogan: '走出教学楼，去看看更远的风景。', description: '校园慢跑、周末轻徒步与户外知识分享。以安全和适度为原则，和伙伴们一起养成健康、积极的生活节奏。', tags: ['户外', '运动', '摄影'], members: 63, schedule: '周六 09:00 · 双周一次', place: '东门集合点', requirements: '选择适合自己的运动强度，遵守活动安全约定。', recruiting: true },
  { id: 'volunteer', name: '暖阳志愿者协会', category: '公益服务', mark: '暖', color: 'rose', slogan: '把一点点善意，变成看得见的行动。', description: '参与校园环保、图书整理与社区服务。我们关注持续的小行动，提供明确的任务说明与志愿活动安排。', tags: ['公益', '服务', '环保'], members: 138, schedule: '周日 14:00 · 按活动安排', place: '学生中心 102', requirements: '有责任心，能够按约定参加活动。', recruiting: true },
  { id: 'read', name: '纸间读书会', category: '文化艺术', mark: '纸', color: 'blue', slogan: '翻开一本书，也打开另一种生活。', description: '通过主题阅读、书籍交换与小组讨论，遇见不同的观点。无需读完所有书，也可以带着问题来交流。', tags: ['阅读', '文化', '表达'], members: 45, schedule: '周二 18:30 · 双周一次', place: '图书馆研讨室', requirements: '本轮招新已结束，下轮开放时可再申请。', recruiting: false }
];
export const initialActivities: Activity[] = [
  { id: 'campus-photo', clubId: 'photo', title: '秋日校园 · 光影漫游', category: '文化艺术', date: '2026-10-17', time: '15:00', location: '图书馆前广场', capacity: 30, enrolled: 18, description: '带上手机或相机，沿校园的小路收集秋天的光。零基础也可以参与，活动后一起分享最喜欢的一张照片。', agenda: ['15:00 集合与拍摄小技巧分享', '15:20 小组校园采风', '16:30 作品交流与自由分享'], status: 'published', budget: 120 },
  { id: 'code-workshop', clubId: 'code', title: '做出你的第一个个人网页', category: '科技实践', date: '2026-10-18', time: '14:00', location: '实验楼 B302', capacity: 40, enrolled: 27, description: '一次面向零基础的网页创作工作坊。从页面结构到简单交互，在伙伴的帮助下完成自己的小作品。请自带电脑。', agenda: ['14:00 认识网页的组成', '14:30 跟着示例完成个人主页', '16:00 展示与答疑'], status: 'published', budget: 200 },
  { id: 'music-night', clubId: 'music', title: '晚风与歌 · 草坪音乐分享', category: '文化艺术', date: '2026-10-23', time: '18:30', location: '学生中心南侧草坪', capacity: 60, enrolled: 42, description: '在晚风里分享一首歌。可以带来你喜欢的乐器，也可以坐下来听一听。活动安排将以发布通知为准。', agenda: ['18:30 入场与自由交流', '19:00 社员弹唱分享', '20:00 现场点歌与合唱'], status: 'published', budget: 350 },
  { id: 'green-campus', clubId: 'volunteer', title: '让校园更绿一点', category: '公益服务', date: '2026-10-24', time: '09:00', location: '校园东门', capacity: 25, enrolled: 25, description: '一起参与校园环保行动。领取物资后分组完成指定区域清理，在实践中了解垃圾分类与环保知识。', agenda: ['09:00 领取物资与安全说明', '09:20 分组行动', '10:30 回收物资与活动小结'], status: 'published', budget: 180 }
];
