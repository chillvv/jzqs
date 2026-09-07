import { useEffect, useRef, useState } from "react";
import { Search } from "lucide-react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { gcj02ToWgs84, wgs84ToGcj02 } from "../../../../shared/utils/coordinates";
import { MAP_CONTAINER_HEIGHT, type MapEngineProps } from "./config";

// Leaflet + OpenStreetMap 免 key 基础引擎（高德未配置 key 时的降级方案）。
// 瓦片/搜索是 WGS-84：展示前把 GCJ-02 转 WGS-84，选中后转回 GCJ-02 落库。

const DEFAULT_CENTER = { lat: 30.5928, lng: 114.3055 }; // 武汉市中心

const PIN_HTML =
  '<div style="width:16px;height:16px;border-radius:50% 50% 50% 0;transform:rotate(-45deg);' +
  'background:#e11d48;border:2px solid #ffffff;box-shadow:0 1px 4px rgba(0,0,0,0.45);margin:0 auto"></div>';

const pinIcon = L.divIcon({
  className: "map-location-picker__pin",
  html: PIN_HTML,
  iconSize: [20, 20],
  iconAnchor: [10, 20]
});

interface NominatimResult {
  lat: string;
  lon: string;
}

async function searchAddress(query: string): Promise<NominatimResult[]> {
  const url =
    "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&accept-language=zh-CN&q=" +
    encodeURIComponent(query);
  const response = await fetch(url, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error("搜索服务不可用");
  }
  const data = (await response.json()) as NominatimResult[];
  if (!Array.isArray(data) || data.length === 0) {
    throw new Error("未找到该地址，请直接在地图上点击选点");
  }
  return data;
}

export function LeafletPicker({ initialGcj, pickedGcj, onPickChange, onAddressResolved, onStatusChange }: MapEngineProps) {
  async function resolveAddress(lat: number, lng: number) {
    try {
      const response = await fetch(
        `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lng}&accept-language=zh-CN`,
        { headers: { Accept: "application/json" } }
      );
      if (!response.ok) {
        onAddressResolved(null);
        return;
      }
      const data = (await response.json()) as { display_name?: string };
      const address = String(data?.display_name || "").trim();
      onAddressResolved(address.length > 0 ? address.slice(0, 120) : null);
    } catch {
      onAddressResolved(null);
    }
  }
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const markerRef = useRef<L.Marker | null>(null);
  const [searchValue, setSearchValue] = useState("");
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    let cancelled = false;
    onStatusChange({ level: "info", message: "地图加载中..." });

    const timer = window.setTimeout(() => {
      if (cancelled || !containerRef.current) {
        return;
      }
      try {
        const centerWgs = initialGcj ? gcj02ToWgs84(initialGcj.lat, initialGcj.lng) : DEFAULT_CENTER;
        const map = L.map(containerRef.current, {
          center: [centerWgs.lat, centerWgs.lng],
          zoom: initialGcj ? 16 : 12,
          zoomControl: true
        });
        L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
          maxZoom: 19,
          attribution: "&copy; OpenStreetMap"
        }).addTo(map);
        map.on("click", (event: L.LeafletMouseEvent) => {
          // 点击得到 WGS-84，转 GCJ-02 再上报，保持落库口径一致
          const gcj = wgs84ToGcj02(event.latlng.lat, event.latlng.lng);
          onPickChange(gcj);
          resolveAddress(event.latlng.lat, event.latlng.lng).catch(() => onAddressResolved(null));
        });
        mapRef.current = map;
        onStatusChange(null);
      } catch (err) {
        onStatusChange({
          level: "error",
          message: err instanceof Error ? err.message : "地图初始化失败"
        });
      }
    }, 50);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
      if (mapRef.current) {
        mapRef.current.remove();
        mapRef.current = null;
      }
      markerRef.current = null;
    };
    // 仅挂载时初始化一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 选中点变化 → 同步图钉（GCJ-02 转 WGS-84 对齐瓦片）
  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }
    if (!pickedGcj) {
      if (markerRef.current) {
        map.removeLayer(markerRef.current);
        markerRef.current = null;
      }
      return;
    }
    const wgs = gcj02ToWgs84(pickedGcj.lat, pickedGcj.lng);
    if (!markerRef.current) {
      markerRef.current = L.marker([wgs.lat, wgs.lng], { icon: pinIcon, keyboard: false }).addTo(map);
    } else {
      markerRef.current.setLatLng([wgs.lat, wgs.lng]);
    }
  }, [pickedGcj]);

  async function handleSearch() {
    const query = searchValue.trim();
    if (!query || searching) {
      return;
    }
    setSearching(true);
    onStatusChange(null);
    try {
      const results = await searchAddress(query);
      const lat = Number(results[0].lat);
      const lng = Number(results[0].lon);
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
        throw new Error("搜索结果无效");
      }
      // 搜索结果是 WGS-84，转 GCJ-02 上报
      const gcj = wgs84ToGcj02(lat, lng);
      mapRef.current?.flyTo([lat, lng], 16);
      onPickChange(gcj);
      resolveAddress(lat, lng).catch(() => onAddressResolved(null));
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
          placeholder="输入地址关键词搜索（基础地图搜索能力有限，推荐直接点击地图选点）"
        />
        <button
          type="button"
          className="btn btn-outline"
          disabled={searching}
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
