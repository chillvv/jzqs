// WGS-84 与 GCJ-02（火星坐标）互转。
// 背景说明：
//   - 小程序 wx.chooseLocation / wx.openLocation、高德与腾讯地图使用的都是 GCJ-02；
//   - OpenStreetMap 等国际地图瓦片与 Nominatim 搜索返回的是 WGS-84；
//   - 顾客端地图选点落库的是 GCJ-02，因此后台地图选点在确认时必须把
//     WGS-84 转成 GCJ-02 再存库，否则骑手端 wx.openLocation 会有数百米偏移。

const PI = Math.PI;
const GCJ_A = 6378245.0;
const GCJ_EE = 0.00669342162296594323;

/** 坐标是否在中国大陆之外（海外坐标不做偏移转换，原样返回） */
export function outOfChina(lat: number, lng: number): boolean {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
}

function transformLat(x: number, y: number): number {
  let ret =
    -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0;
  ret += ((20.0 * Math.sin(y * PI) + 40.0 * Math.sin((y / 3.0) * PI)) * 2.0) / 3.0;
  ret += ((160.0 * Math.sin((y / 12.0) * PI) + 320.0 * Math.sin((y * PI) / 30.0)) * 2.0) / 3.0;
  return ret;
}

function transformLng(x: number, y: number): number {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0;
  ret += ((20.0 * Math.sin(x * PI) + 40.0 * Math.sin((x / 3.0) * PI)) * 2.0) / 3.0;
  ret += ((150.0 * Math.sin((x / 12.0) * PI) + 300.0 * Math.sin((x / 30.0) * PI)) * 2.0) / 3.0;
  return ret;
}

export interface LatLng {
  lat: number;
  lng: number;
}

/** WGS-84 → GCJ-02 */
export function wgs84ToGcj02(lat: number, lng: number): LatLng {
  if (outOfChina(lat, lng)) {
    return { lat, lng };
  }
  let dLat = transformLat(lng - 105.0, lat - 35.0);
  let dLng = transformLng(lng - 105.0, lat - 35.0);
  const radLat = (lat / 180.0) * PI;
  let magic = Math.sin(radLat);
  magic = 1 - GCJ_EE * magic * magic;
  const sqrtMagic = Math.sqrt(magic);
  dLat = (dLat * 180.0) / (((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic)) * PI);
  dLng = (dLng * 180.0) / ((GCJ_A / sqrtMagic) * Math.cos(radLat) * PI);
  return { lat: lat + dLat, lng: lng + dLng };
}

/** GCJ-02 → WGS-84（迭代逼近，误差 < 1m，足够骑手导航使用） */
export function gcj02ToWgs84(lat: number, lng: number): LatLng {
  if (outOfChina(lat, lng)) {
    return { lat, lng };
  }
  let wgsLat = lat;
  let wgsLng = lng;
  for (let i = 0; i < 3; i++) {
    const gcj = wgs84ToGcj02(wgsLat, wgsLng);
    wgsLat += lat - gcj.lat;
    wgsLng += lng - gcj.lng;
  }
  return { lat: wgsLat, lng: wgsLng };
}
