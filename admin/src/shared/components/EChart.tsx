import { useEffect, useRef } from "react";
import * as echarts from "echarts/core";
import { LineChart } from "echarts/charts";
import { GridComponent, TooltipComponent, LegendComponent, MarkPointComponent } from "echarts/components";
import { CanvasRenderer } from "echarts/renderers";
import type { EChartsCoreOption } from "echarts/core";

// 按需注册，Vite 打包自动 tree-shake，避免引入整个 echarts（体积可控）
echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, MarkPointComponent, CanvasRenderer]);

type EChartProps = {
  option: EChartsCoreOption;
  height?: number | string;
  ariaLabel?: string;
};

/**
 * ECharts 轻量 React 封装：init / setOption / resize / dispose 全生命周期管理。
 * 在 jsdom 或其它不支持 canvas 的环境（如单测）优雅降级为空容器，不抛错。
 */
export function EChart({ option, height = 300, ariaLabel }: EChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<ReturnType<typeof echarts.init> | null>(null);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) {
      return;
    }
    let chart: ReturnType<typeof echarts.init> | null = null;
    try {
      chart = echarts.init(el);
      chart.setOption(option);
      chartRef.current = chart;
    } catch {
      // 无 canvas 环境（jsdom 等）优雅降级，页面其余内容不受影响
      chartRef.current = null;
    }
    const handleResize = () => chart?.resize();
    window.addEventListener("resize", handleResize);
    return () => {
      window.removeEventListener("resize", handleResize);
      chart?.dispose();
      chartRef.current = null;
    };
    // 仅初始化一次；后续 option 变化走下方的更新 effect
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    chartRef.current?.setOption(option, { notMerge: true });
  }, [option]);

  return <div ref={containerRef} role="img" aria-label={ariaLabel} style={{ width: "100%", height }} />;
}
