import { useEffect, useRef, useState } from "react";
import { Search } from "lucide-react";
import { AMAP_KEY, AMAP_SECURITY_JSCODE, MAP_CONTAINER_HEIGHT, type MapEngineProps } from "./config";

// 高德 JS API 2.0 引擎：中文 POI/地址搜索 + 点击选点。
// 高德坐标天然是 GCJ-02，与小程序选点落库口径一致，无需转换。

declare global {
  interface Window {
    AMap?: any;
    _AMapSecurityConfig?: { securityJsCode: string };
    __amapLoaderPromise?: Promise<any> | null;
  }
}

function loadAmapApi(): Promise<any> {
  if (typeof window === "undefined") {
    return Promise.reject(new Error("no window"));
  }
  if (window.AMap) {
    return Promise.resolve(window.AMap);
  }
  if (!window.__amapLoaderPromise) {
    if (AMAP_SECURITY_JSCODE) {
      window._AMapSecurityConfig = { securityJsCode: AMAP_SECURITY_JSCODE };
    }
    window.__amapLoaderPromise = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(AMAP_KEY)}&plugin=AMap.PlaceSearch,AMap.Geocoder`;
      script.async = true;
      script.onload = () => {
        if (window.AMap) {
          resolve(window.AMap);
        } else {
          reject(new Error("高德地图脚本加载完成但未初始化"));
        }
      };
      script.onerror = () => {
        window.__amapLoaderPromise = null;
        reject(new Error("高德地图脚本加载失败，请检查网络或 VITE_AMAP_KEY 配置"));
      };
      document.head.appendChild(script);
    });
  }
  return window.__amapLoaderPromise;
}

/** 先 POI 搜索，查不到再走地理编码（适合结构化门牌地址） */
async function searchFirstPosition(AMap: any, query: string): Promise<{ lat: number; lng: number } | null> {
  const placeSearch = await Promise.resolve(
    new AMap.PlaceSearch({ city: "武汉", citylimit: false, pageSize: 1 })
  );
  const poiResult = await new Promise<any>((resolve) => {
    placeSearch.search(query, (status: string, result: any) => resolve({ status, result }));
  });
  const poi = poiResult?.result?.poiList?.pois?.[0];
  if (poi?.location) {
    return { lat: Number(poi.location.lat), lng: Number(poi.location.lng) };
  }

  const geocoder = await Promise.resolve(new AMap.Geocoder());
  const geoResult = await new Promise<any>((resolve) => {
    geocoder.getLocation(query, (status: string, result: any) => resolve({ status, result }));
  });
  const location = geoResult?.result?.geocodes?.[0]?.location;
  if (location) {
    return { lat: Number(location.lat), lng: Number(location.lng) };
  }
  return null;
}

export function AmapPicker({ initialGcj, pickedGcj, onPickChange, onAddressResolved, onStatusChange }: MapEngineProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<any>(null);
  const markerRef = useRef<any>(null);
  const geocoderRef = useRef<any>(null);
  const [searchValue, setSearchValue] = useState("");
  const [searching, setSearching] = useState(false);
  const [ready, setReady] = useState(false);

  // 逆地理编码：把选中点解析成"省市区+道路+门牌"文本，供表单自动回填地址
  function resolveAddress(lng: number, lat: number) {
    const AMap = window.AMap;
    if (!AMap) {
      onAddressResolved(null);
      return;
    }
    if (!geocoderRef.current) {
      geocoderRef.current = new AMap.Geocoder();
    }
    geocoderRef.current.getAddress([lng, lat], (status: string, result: any) => {
      if (status === "complete" && result?.regeocode) {
        const address = String(result.regeocode.formattedAddress || "").trim();
        onAddressResolved(address.length > 0 ? address.slice(0, 120) : null);
      } else {
        onAddressResolved(null);
      }
    });
  }

  // 挂载时初始化地图；卸载时销毁
  useEffect(() => {
    let cancelled = false;
    onStatusChange({ level: "info", message: "地图加载中..." });
    loadAmapApi()
      .then((AMap) => {
        if (cancelled || !containerRef.current) {
          return;
        }
        const center = initialGcj ? [initialGcj.lng, initialGcj.lat] : [114.3055, 30.5928]; // 武汉市中心
        // 显式指定栅格瓦片底图（高德 webrd 瓦片，实测可用），
        // 不依赖 JS API 自带的矢量底图 —— 矢量底图受 key 域名白名单/安全密钥校验，
        // 校验不过时地图区域会整体空白（图钉/搜索/版权信息正常但无瓦片）。
        const baseLayer = new AMap.TileLayer({
          tileSize: 256,
          zooms: [3, 20],
          tileUrl:
            "https://webrd01.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=[x]&y=[y]&z=[z]"
        });
        const map = new AMap.Map(containerRef.current, {
          center,
          zoom: initialGcj ? 16 : 12,
          viewMode: "2D",
          layers: [baseLayer]
        });
        map.on("click", (event: any) => {
          const lng = event.lnglat?.getLng?.() ?? event.lnglat?.lng;
          const lat = event.lnglat?.getLat?.() ?? event.lnglat?.lat;
          if (typeof lat === "number" && typeof lng === "number") {
            onPickChange({ lat, lng });
            resolveAddress(lng, lat);
          }
        });
        mapRef.current = map;
        setReady(true);
        onStatusChange(null);
      })
      .catch((err) => {
        if (cancelled) {
          return;
        }
        onStatusChange({
          level: "error",
          message: err instanceof Error ? err.message : "高德地图初始化失败"
        });
      });

    return () => {
      cancelled = true;
      if (mapRef.current) {
        mapRef.current.destroy?.();
        mapRef.current = null;
      }
      markerRef.current = null;
    };
    // 仅挂载时初始化一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 选中点变化 → 同步图钉
  useEffect(() => {
    const AMap = window.AMap;
    const map = mapRef.current;
    if (!AMap || !map) {
      return;
    }
    if (!pickedGcj) {
      if (markerRef.current) {
        map.remove(markerRef.current);
        markerRef.current = null;
      }
      return;
    }
    if (!markerRef.current) {
      markerRef.current = new AMap.Marker({
        position: [pickedGcj.lng, pickedGcj.lat],
        map
      });
    } else {
      markerRef.current.setPosition([pickedGcj.lng, pickedGcj.lat]);
    }
  }, [pickedGcj]);

  async function handleSearch() {
    const query = searchValue.trim();
    const AMap = window.AMap;
    if (!query || searching || !ready || !AMap) {
      return;
    }
    setSearching(true);
    onStatusChange(null);
    try {
      const position = await searchFirstPosition(AMap, query);
      if (!position) {
        onStatusChange({ level: "error", message: "未找到该地址，请直接在地图上点击选点" });
        return;
      }
      mapRef.current?.setZoomAndCenter(16, [position.lng, position.lat]);
      onPickChange(position);
      resolveAddress(position.lng, position.lat);
    } catch (err) {
      onStatusChange({ level: "error", message: err instanceof Error ? err.message : "搜索失败" });
    } finally {
      setSearching(false);
    }
  }

  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", gap: 8 }}>
        <input
          className="form-control"
          value={searchValue}
          onChange={(event) => setSearchValue(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              handleSearch().catch(() => undefined);
            }
          }}
          placeholder="输入地址/小区/大厦名称搜索（如：光谷创意大厦）"
        />
        <button
          type="button"
          className="btn btn-outline"
          disabled={searching || !ready}
          onClick={() => handleSearch().catch(() => undefined)}
        >
          <Search size={14} style={{ marginRight: 4 }} />
          {searching ? "搜索中..." : "搜索"}
        </button>
      </div>
      <div
        ref={containerRef}
        style={{ height: MAP_CONTAINER_HEIGHT, borderRadius: 10, overflow: "hidden", background: "#e2e8f0" }}
      />
    </div>
  );
}
