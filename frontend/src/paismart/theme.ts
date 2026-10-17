// Adapted from PaiSmart/frontend/src/theme/settings.ts (Apache-2.0; see THIRD-PARTY-NOTICES.md).
import type { GlobalThemeOverrides } from 'naive-ui';
export const theme = { primary: '#646cff', background: 'rgb(247, 250, 252)', surface: '#fff', text: '#1f1f1f', headerHeight: 56, siderWidth: 180, collapsedWidth: 64 };
export const themeOverrides: GlobalThemeOverrides = {
  common: { primaryColor: theme.primary, primaryColorHover: '#8288ff', primaryColorPressed: '#5057d9', primaryColorSuppl: '#646cff', bodyColor: theme.background, cardColor: theme.surface, textColorBase: theme.text, borderRadius: '8px', fontFamily: 'v-sans, system-ui, -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif' },
  Menu: { itemHeight: '44px', borderRadius: '8px', itemColorActive: '#efefff', itemColorActiveHover: '#e8e8ff', itemTextColorActive: '#555cd6', itemIconColorActive: '#555cd6' }
};
