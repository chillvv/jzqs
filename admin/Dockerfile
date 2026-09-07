FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
# 地图选点引擎（可选）：配置高德 key 后使用高德搜索，否则降级到 Leaflet 基础地图
ARG VITE_AMAP_KEY=""
ARG VITE_AMAP_SECURITY_JSCODE=""
ENV VITE_AMAP_KEY=$VITE_AMAP_KEY
ENV VITE_AMAP_SECURITY_JSCODE=$VITE_AMAP_SECURITY_JSCODE
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
