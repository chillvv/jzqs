import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";
import "antd/dist/reset.css";
import "./index.css";

// 注意：react-beautiful-dnd v13 在 React 18 StrictMode 下有已知 bug 导致拖拽失效
// 暂时移除 StrictMode，等升级到 @hello-pangea/dnd 后可恢复
ReactDOM.createRoot(document.getElementById("root")!).render(
  <App />
);
