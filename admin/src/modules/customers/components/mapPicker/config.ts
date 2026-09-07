// 地图选点引擎配置：
//   - 配置了高德 key（VITE_AMAP_KEY，可选安全密钥 VITE_AMAP_SECURITY_JSCODE）时
//     使用高德 JS API（中文 POI 搜索体验最好）；
//   - 未配置时自动降级到 Leaflet + OpenStreetMap 免 key 基础方案。
// 两个引擎的落库口径一致：GCJ-02（与小程序 wx.chooseLocation / 骑手端 wx.openLocation 相同）。
// 引擎内部自行处理展示坐标系（Leaflet/OSM 为 WGS-84，需要互转；高德原生 GCJ-02 无需转换）。

export const AMAP_KEY = ((import.meta.env.VITE_AMAP_KEY as string | undefined) ?? "").trim();
export const AMAP_SECURITY_JSCODE =
  ((import.meta.env.VITE_AMAP_SECURITY_JSCODE as string | undefined) ?? "").trim();

export function isAmapConfigured(): boolean {
  return AMAP_KEY.length > 0;
}

/** GCJ-02 坐标点（落库/展示口径） */
export interface PickedPoint {
  lat: number;
  lng: number;
}

export type MapEngineStatus = { level: "info" | "error"; message: string } | null;

/** 地图容器高度（px） */
export const MAP_CONTAINER_HEIGHT = 360;

export interface MapEngineProps {
  /** 打开弹窗时的初始坐标（GCJ-02），无则 null */
  initialGcj: PickedPoint | null;
  /** 当前选中点（GCJ-02，受控） */
  pickedGcj: PickedPoint | null;
  /** 用户点击地图/搜索选中 */
  onPickChange: (picked: PickedPoint | null) => void;
  /** 选中点的逆地理编码结果（格式化地址文本），失败为 null */
  onAddressResolved: (address: string | null) => void;
  /** 引擎内部状态提示（加载中/错误等） */
  onStatusChange: (status: MapEngineStatus) => void;
}
