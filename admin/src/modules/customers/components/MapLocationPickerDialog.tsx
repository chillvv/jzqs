import { useEffect, useMemo, useState } from "react";
import { X } from "lucide-react";
import { isAmapConfigured, PickedPoint } from "./mapPicker/config";
import { AmapPicker } from "./mapPicker/AmapPicker";
import { LeafletPicker } from "./mapPicker/LeafletPicker";

interface MapLocationPickerDialogProps {
  open: boolean;
  /** 当前表单里的坐标（GCJ-02 字符串，可能为空/非法） */
  initialLatitude: string;
  initialLongitude: string;
  onClose: () => void;
  /** 确认回调：返回 GCJ-02 坐标字符串 + 逆地理编码识别出的地址文本（可能为 null） */
  onConfirm: (latitude: string, longitude: string, resolvedAddress: string | null) => void;
}

function parseGcj(raw: string): number | null {
  const value = Number(raw);
  return Number.isFinite(value) && value !== 0 ? value : null;
}

function formatPoint(point: PickedPoint): string {
  return `${point.lat.toFixed(6)}, ${point.lng.toFixed(6)}`;
}

export function MapLocationPickerDialog({
  open,
  initialLatitude,
  initialLongitude,
  onClose,
  onConfirm
}: MapLocationPickerDialogProps) {
  const [picked, setPicked] = useState<PickedPoint | null>(null);
  const [resolvedAddress, setResolvedAddress] = useState<string | null>(null);
  const [status, setStatus] = useState<{ level: "info" | "error"; message: string } | null>(null);
  const useAmap = useMemo(() => isAmapConfigured(), []);

  // 每次打开：从表单坐标恢复初始选中点
  useEffect(() => {
    if (!open) {
      return;
    }
    const lat = parseGcj(initialLatitude);
    const lng = parseGcj(initialLongitude);
    setPicked(lat !== null && lng !== null ? { lat, lng } : null);
    setResolvedAddress(null);
    setStatus(null);
  }, [open, initialLatitude, initialLongitude]);

  if (!open) {
    return null;
  }

  const initialGcj = (() => {
    const lat = parseGcj(initialLatitude);
    const lng = parseGcj(initialLongitude);
    return lat !== null && lng !== null ? { lat, lng } : null;
  })();

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: 680, width: "94%" }}>
        <div className="modal-header">
          <span>地图选点</span>
          <span className="modal-close" onClick={onClose}><X size={20} /></span>
        </div>
        <div className="modal-body" style={{ display: "grid", gap: 12 }}>
          {useAmap ? (
            <AmapPicker
              initialGcj={initialGcj}
              pickedGcj={picked}
              onPickChange={setPicked}
              onAddressResolved={setResolvedAddress}
              onStatusChange={setStatus}
            />
          ) : (
            <LeafletPicker
              initialGcj={initialGcj}
              pickedGcj={picked}
              onPickChange={setPicked}
              onAddressResolved={setResolvedAddress}
              onStatusChange={setStatus}
            />
          )}

          {resolvedAddress && (
            <div style={{ fontSize: 13, color: "var(--text-main)", background: "rgba(239,246,255,0.6)", padding: "8px 10px", borderRadius: 8 }}>
              识别地址：{resolvedAddress}
            </div>
          )}
          {status?.level === "info" && (
            <div style={{ color: "var(--text-sub)", fontSize: 13 }}>{status.message}</div>
          )}
          {status?.level === "error" && (
            <div style={{ color: "#e11d48", fontSize: 13 }}>{status.message}</div>
          )}
          {!useAmap && (
            <div style={{ color: "var(--text-sub)", fontSize: 12 }}>
              当前为基础地图（搜索能力有限，推荐直接点击地图选点）。
              配置 VITE_AMAP_KEY / VITE_AMAP_SECURITY_JSCODE 后重建前端可启用高德搜索。
            </div>
          )}

          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              gap: 10,
              flexWrap: "wrap"
            }}
          >
            <div style={{ fontSize: 13, color: "var(--text-main)" }}>
              {picked ? `已选坐标：${formatPoint(picked)}` : "在地图上点击任意位置即可选点"}
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              <button type="button" className="btn btn-outline" onClick={onClose}>取消</button>
              <button
                type="button"
                className="btn btn-primary"
                disabled={!picked}
                onClick={() => {
                  if (!picked) {
                    return;
                  }
                  onConfirm(picked.lat.toFixed(6), picked.lng.toFixed(6), resolvedAddress);
                }}
              >
                使用该坐标
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
