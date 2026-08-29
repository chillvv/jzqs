import path from "path"
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          // echarts 拆独立 chunk：体积大且极少变更，浏览器可长期缓存，业务代码更新不重复下载
          echarts: ["echarts/core", "echarts/charts", "echarts/components", "echarts/renderers"]
        }
      }
    }
  },
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8081",
        changeOrigin: true
      },
      "/uploads": {
        target: "http://localhost:8081",
        changeOrigin: true
      },
      "/ws": {
        target: "ws://localhost:8081",
        changeOrigin: true,
        ws: true
      }
    }
  },
  test: {
    include: ["src/**/*.test.{ts,tsx}"],
    exclude: ["scripts/**"]
  }
});
