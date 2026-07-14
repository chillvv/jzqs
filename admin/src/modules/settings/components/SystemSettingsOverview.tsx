import React from "react";
import { Image as ImageIcon, Megaphone } from "lucide-react";
import type { OperationSettingsResponse } from "../../../shared/api/types";
import {
  countBannerImages,
  handleAdminImageFallback,
  resolveAdminMediaUrl,
  type BannerConfigItem
} from "../systemSettingsPage.helpers";

interface SystemSettingsOverviewProps {
  settings: OperationSettingsResponse;
  customerHints: {
    popupHint: string;
    reminderHint: string;
    bannerHint: string;
  };
  bannerConfigs: BannerConfigItem[];
  enabledBannerCount: number;
  onOpenPopup: () => void;
  onOpenPackageReminder: () => void;
  onOpenBanner: () => void;
  onPreviewBanner: (imageUrl: string) => void;
}

export function SystemSettingsOverview({
  settings,
  customerHints,
  bannerConfigs,
  enabledBannerCount,
  onOpenPopup,
  onOpenPackageReminder,
  onOpenBanner,
  onPreviewBanner
}: SystemSettingsOverviewProps) {
  return (
    <>
      <div className="stat-row">
        <div className="stat-card">
          <div className="stat-title">锁定公告</div>
          <div className="stat-val">
            <span className={settings.popupAnnouncementEnabled ? "tag tag-red" : "tag tag-gray"}>
              {settings.popupAnnouncementEnabled ? "已锁定" : "未启用"}
            </span>
          </div>
          <div className="stat-footer">
            {settings.popupAnnouncementEnabled
              ? settings.holidayNoticeTitle || "用户当前只能查看公告"
              : "用户可正常使用小程序"}
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-title">轮播图</div>
          <div className="stat-val">{countBannerImages(settings.bannerImages)} <span>张</span></div>
          <div className="stat-footer">启用中 {enabledBannerCount} 张</div>
        </div>
        <div className="stat-card">
          <div className="stat-title">餐包提醒</div>
          <div className="stat-val">{settings.packageExpiryReminderDays} <span>天</span></div>
          <div className="stat-footer">
            低餐量阈值 {settings.packageLowBalanceThreshold} 餐 · 订阅
            {settings.deliverySubscribeEnabled ? ` 午餐 ${settings.deliverySubscribeLunchTime} / 晚餐 ${settings.deliverySubscribeDinnerTime}` : " 已关闭"}
          </div>
        </div>
      </div>

      <div className="settings-cards">
        <div className="settings-card settings-card--highlight">
          <div className="settings-card__title">
            <Megaphone size={18} /> 锁定公告
          </div>
          <div className="settings-card__body">
            <span className={`tag ${settings.popupAnnouncementEnabled ? "tag-red" : "tag-gray"}`}>
              {settings.popupAnnouncementEnabled ? "用户仅可查看公告" : "当前未锁定"}
            </span>
            <div className="settings-card__detail">
              {settings.holidayNoticeTitle || "未设置锁定公告主文"}
            </div>
            <div className="settings-card__detail settings-card__detail--sub">
              {settings.holidayNoticeDesc || "未设置锁定公告摘要"}
            </div>
            <span className="settings-card__hint">{customerHints.popupHint}</span>
          </div>
          <div className="settings-card__actions">
            <button className="btn btn-outline" style={{ width: "100%" }} onClick={onOpenPopup}>
              配置锁定公告
            </button>
          </div>
        </div>

        <div className="settings-card">
          <div className="settings-card__title">餐包提醒</div>
          <div className="settings-card__body">
            <div className="settings-card__detail">
              到期前 {settings.packageExpiryReminderDays} 天提醒用户和商家
            </div>
            <div className="settings-card__detail settings-card__detail--sub">
              剩余餐数小于等于 {settings.packageLowBalanceThreshold} 餐时标记为餐数不足
            </div>
            <div className="settings-card__detail settings-card__detail--sub">
              上线提醒弹窗{settings.mealReminderPopupEnabled ? "已开启" : "已关闭"}，订阅通知{settings.deliverySubscribeEnabled ? `午餐 ${settings.deliverySubscribeLunchTime}、晚餐 ${settings.deliverySubscribeDinnerTime} 按设置时间扫描发送` : "已关闭"}
            </div>
            <span className="settings-card__hint">{customerHints.reminderHint}</span>
          </div>
          <div className="settings-card__actions">
            <button className="btn btn-outline" style={{ width: "100%" }} onClick={onOpenPackageReminder}>
              配置餐包提醒
            </button>
          </div>
        </div>

        <div className="settings-card">
          <div className="settings-card__title">
            <ImageIcon size={18} /> 首页轮播图
          </div>
          <div className="settings-card__body">
            <div className="banner-preview-grid">
              {bannerConfigs.slice(0, 3).map((item, index) => (
                <div key={`${item.imageUrl}-${index}`} className="banner-preview-card">
                  <img
                    src={resolveAdminMediaUrl(item.imageUrl)}
                    className="banner-preview-thumb"
                    alt={`轮播图 ${index + 1}`}
                    onError={handleAdminImageFallback}
                    onClick={() => onPreviewBanner(item.imageUrl)}
                  />
                  <div className="banner-preview-card__meta">
                    <div className="banner-preview-card__title">{`轮播图 ${index + 1}`}</div>
                    <div className="banner-preview-card__hint">{item.enabled ? "启用中 · 点击图片查看大图" : "已停用 · 点击图片查看大图"}</div>
                  </div>
                </div>
              ))}
            </div>
            <div className="settings-card__detail">
              启用中 {enabledBannerCount} 张，轮播间隔 {Math.max(1, settings.bannerIntervalSeconds || 3)} 秒
            </div>
            <div className="settings-card__detail settings-card__detail--sub">
              首页只展示图片，用户点击后仅预览大图。
            </div>
            <span className="settings-card__hint">
              {customerHints.bannerHint}
            </span>
          </div>
          <div className="settings-card__actions">
            <button className="btn btn-outline" style={{ width: "100%" }} onClick={onOpenBanner}>
              管理轮播图
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
