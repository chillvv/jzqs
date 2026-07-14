export const SETTINGS_SECTION = {
  BASIC: "basic",
  AI_DISPATCH: "ai-dispatch",
  MAINTENANCE: "maintenance"
} as const;

export type SettingsSection = (typeof SETTINGS_SECTION)[keyof typeof SETTINGS_SECTION];

export const DEFAULT_SETTINGS_SECTION: SettingsSection = SETTINGS_SECTION.BASIC;

export const SETTINGS_SECTION_VALUES = Object.values(SETTINGS_SECTION);

export const SETTINGS_SECTION_META: Record<SettingsSection, { label: string; description: string }> = {
  [SETTINGS_SECTION.BASIC]: {
    label: "基础设置",
    description: "锁定公告、轮播图、餐包提醒"
  },
  [SETTINGS_SECTION.AI_DISPATCH]: {
    label: "AI 智能调度",
    description: "全局模型配置、路线排版控制台、实验室与区域长期记忆"
  },
  [SETTINGS_SECTION.MAINTENANCE]: {
    label: "系统维护",
    description: "清理规则、执行入口、维护日志"
  }
};

export function isSettingsSection(value: string | undefined): value is SettingsSection {
  return SETTINGS_SECTION_VALUES.includes(value as SettingsSection);
}

export function buildSettingsSectionPath(section: SettingsSection) {
  return `/settings/${section}`;
}
