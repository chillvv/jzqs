import { useCallback, useEffect, useRef, useState } from "react";
import {
  fetchDispatchAreaBindings,
  fetchDispatchManagedRiders,
  fetchDispatchOverview,
  fetchDispatchPendingItems,
  fetchDispatchRiderProgress
} from "../../shared/api/http";
import type {
  DispatchAreaBindingResponse,
  DispatchManagedRiderResponse,
  DispatchOverviewResponse,
  DispatchPendingItemResponse,
  DispatchRiderProgressResponse
} from "../../shared/api/types";
import { useAdminRealtime } from "../../shared/realtime/adminRealtime";
import {
  normalizeDispatchAreaBindings,
  normalizeDispatchOverview,
  type DispatchMealPeriod
} from "./dispatchCenterLayout.helpers";

const DISPATCH_EVENT_PREFIX = "dispatch.";
const PROGRESS_POLLING_MS = 8000;

type DispatchScope = {
  mealPeriod: DispatchMealPeriod;
  serveDate: string;
};

type LoadersShape = Record<string, (scope: DispatchScope) => Promise<unknown>>;

type LoaderValue<TLoader> = TLoader extends (...args: never[]) => Promise<infer TValue> ? TValue : never;

type LoaderValues<TLoaders extends LoadersShape> = {
  [TKey in keyof TLoaders]: LoaderValue<TLoaders[TKey]>;
};

type PartialLoaderValues<TLoaders extends LoadersShape> = Partial<LoaderValues<TLoaders>>;

type ReloadOptions = {
  silent?: boolean;
  auto?: boolean;
};

type WarningBuilderArgs<TLoaders extends LoadersShape> = {
  failedKeys: Array<keyof TLoaders>;
  hasSuccessfulData: boolean;
};

type LiveResourceOptions<TLoaders extends LoadersShape, TData> = {
  scope: DispatchScope;
  loaders: TLoaders;
  initialData: TData;
  mergeData: (previous: TData, next: PartialLoaderValues<TLoaders>) => TData;
  autoRefreshBlocked?: boolean;
  pollingMs?: number;
  buildWarning?: (args: WarningBuilderArgs<TLoaders>) => string;
};

type LiveResourceState<TData> = {
  data: TData;
  loading: boolean;
  warning: string;
  loadError: unknown;
  reload: (options?: ReloadOptions) => Promise<void>;
};

type HomeDispatchData = {
  overview: DispatchOverviewResponse;
  areaBindings: DispatchAreaBindingResponse[];
  pendingItems: DispatchPendingItemResponse[];
};

type AreasDispatchData = {
  bindings: DispatchAreaBindingResponse[];
  riders: DispatchManagedRiderResponse[];
};

type ProgressDispatchData = {
  areaBindings: DispatchAreaBindingResponse[];
  riderProgress: DispatchRiderProgressResponse[];
};

const homeLoaders = {
  overview: ({ mealPeriod, serveDate }: DispatchScope) => fetchDispatchOverview(mealPeriod, serveDate),
  areaBindings: ({ mealPeriod, serveDate }: DispatchScope) => fetchDispatchAreaBindings(mealPeriod, serveDate),
  pendingItems: ({ mealPeriod, serveDate }: DispatchScope) => fetchDispatchPendingItems(mealPeriod, serveDate)
};

const areasLoaders = {
  bindings: ({ mealPeriod, serveDate }: DispatchScope) => fetchDispatchAreaBindings(mealPeriod, serveDate),
  riders: () => fetchDispatchManagedRiders()
};

const progressLoaders = {
  areaBindings: ({ mealPeriod, serveDate }: DispatchScope) => fetchDispatchAreaBindings(mealPeriod, serveDate),
  riderProgress: ({ mealPeriod, serveDate }: DispatchScope) => fetchDispatchRiderProgress(mealPeriod, serveDate)
};

function useDispatchLiveResource<TLoaders extends LoadersShape, TData>({
  scope,
  loaders,
  initialData,
  mergeData,
  autoRefreshBlocked = false,
  pollingMs,
  buildWarning
}: LiveResourceOptions<TLoaders, TData>): LiveResourceState<TData> {
  const { mealPeriod, serveDate } = scope;
  const [data, setData] = useState(initialData);
  const [loading, setLoading] = useState(true);
  const [warning, setWarning] = useState("");
  const [loadError, setLoadError] = useState<unknown>(null);
  const latestRequestIdRef = useRef(0);
  const activeReloadRef = useRef(Promise.resolve());
  const isRunningRef = useRef(false);
  const hasSuccessfulDataRef = useRef(false);
  const autoRefreshBlockedRef = useRef(autoRefreshBlocked);
  const queuedAutoReloadRef = useRef(false);
  const mountedRef = useRef(true);

  useEffect(() => {
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const performReload = useCallback(async ({ silent = false }: ReloadOptions = {}) => {
    const currentScope: DispatchScope = { mealPeriod, serveDate };
    const requestId = latestRequestIdRef.current + 1;
    latestRequestIdRef.current = requestId;
    isRunningRef.current = true;
    if (!silent) {
      setLoading(true);
    }
    const loaderKeys = Object.keys(loaders) as Array<keyof TLoaders>;
    const results = await Promise.allSettled(loaderKeys.map((key) => loaders[key](currentScope)));
    if (!mountedRef.current || requestId !== latestRequestIdRef.current) {
      return;
    }

    let firstError: unknown = null;
    const failedKeys: Array<keyof TLoaders> = [];
    const nextValues: PartialLoaderValues<TLoaders> = {};
    let hasSuccess = false;

    results.forEach((result, index) => {
      const key = loaderKeys[index];
      if (result.status === "fulfilled") {
        nextValues[key] = result.value as LoaderValues<TLoaders>[typeof key];
        hasSuccess = true;
        return;
      }
      failedKeys.push(key);
      firstError = firstError ?? result.reason;
    });

    if (hasSuccess) {
      setData((previous) => mergeData(previous, nextValues));
      hasSuccessfulDataRef.current = true;
      setLoadError(null);
    } else if (!silent) {
      setLoadError(firstError);
    }

    setWarning(buildWarning ? buildWarning({ failedKeys, hasSuccessfulData: hasSuccessfulDataRef.current }) : "");
    setLoading(false);

    if (!hasSuccess && firstError) {
      throw firstError;
    }
  }, [buildWarning, loaders, mealPeriod, mergeData, serveDate]);

  const reload = useCallback((options: ReloadOptions = {}) => {
    activeReloadRef.current = activeReloadRef.current
      .catch(() => undefined)
      .then(() => performReload(options))
      .finally(() => {
        isRunningRef.current = false;
        if (queuedAutoReloadRef.current && !autoRefreshBlockedRef.current) {
          queuedAutoReloadRef.current = false;
          void reload({ silent: true, auto: true }).catch(() => undefined);
        }
      });
    return activeReloadRef.current;
  }, [performReload]);

  useEffect(() => {
    autoRefreshBlockedRef.current = autoRefreshBlocked;
    if (!autoRefreshBlocked && queuedAutoReloadRef.current && !isRunningRef.current) {
      queuedAutoReloadRef.current = false;
      void reload({ silent: true, auto: true }).catch(() => undefined);
    }
  }, [autoRefreshBlocked, reload]);

  const triggerAutoReload = useCallback(() => {
    if (autoRefreshBlockedRef.current || isRunningRef.current) {
      queuedAutoReloadRef.current = true;
      return;
    }
    void reload({ silent: true, auto: true }).catch(() => undefined);
  }, [reload]);

  useEffect(() => {
    void reload().catch(() => undefined);
  }, [reload]);

  useEffect(() => {
    return useAdminRealtime((message) => {
      if (!message.eventType || !message.eventType.startsWith(DISPATCH_EVENT_PREFIX)) {
        return;
      }
      triggerAutoReload();
    });
  }, [triggerAutoReload]);

  useEffect(() => {
    if (!pollingMs) {
      return undefined;
    }
    const timer = window.setInterval(() => {
      triggerAutoReload();
    }, pollingMs);
    return () => window.clearInterval(timer);
  }, [pollingMs, triggerAutoReload]);

  return { data, loading, warning, loadError, reload };
}

function mergeHomeData(previous: HomeDispatchData, next: PartialLoaderValues<typeof homeLoaders>): HomeDispatchData {
  return {
    overview: next.overview ? normalizeDispatchOverview(next.overview) : previous.overview,
    areaBindings: next.areaBindings ? normalizeDispatchAreaBindings(next.areaBindings) : previous.areaBindings,
    pendingItems: next.pendingItems ?? previous.pendingItems
  };
}

function buildHomeWarning({ failedKeys, hasSuccessfulData }: WarningBuilderArgs<typeof homeLoaders>) {
  if (failedKeys.length === 0) {
    return "";
  }
  const labels = failedKeys.map((key) => {
    switch (key) {
      case "overview":
        return "概览";
      case "areaBindings":
        return "区域";
      case "pendingItems":
        return "待分配订单";
      default:
        return String(key);
    }
  });
  if (hasSuccessfulData) {
    return `部分分单数据刷新失败：${labels.join("、")}。页面当前展示的是最近一次成功获取的数据。`;
  }
  return `分单数据加载失败：${labels.join("、")}。`;
}

function mergeAreasData(previous: AreasDispatchData, next: PartialLoaderValues<typeof areasLoaders>): AreasDispatchData {
  return {
    bindings: next.bindings ? normalizeDispatchAreaBindings(next.bindings) : previous.bindings,
    riders: next.riders ?? previous.riders
  };
}

function mergeProgressData(previous: ProgressDispatchData, next: PartialLoaderValues<typeof progressLoaders>): ProgressDispatchData {
  return {
    areaBindings: next.areaBindings ? normalizeDispatchAreaBindings(next.areaBindings) : previous.areaBindings,
    riderProgress: next.riderProgress ?? previous.riderProgress
  };
}

export function useDispatchHomeLiveData(scope: DispatchScope) {
  const { data, loading, warning, loadError, reload } = useDispatchLiveResource({
    scope,
    loaders: homeLoaders,
    initialData: {
      overview: normalizeDispatchOverview({}),
      areaBindings: [],
      pendingItems: []
    },
    mergeData: mergeHomeData,
    buildWarning: buildHomeWarning
  });
  return {
    overview: data.overview,
    areaBindings: data.areaBindings,
    pendingItems: data.pendingItems,
    loading,
    warning,
    loadError,
    reload
  };
}

export function useDispatchAreasLiveData(scope: DispatchScope, options?: { autoRefreshBlocked?: boolean }) {
  const { data, loadError, reload } = useDispatchLiveResource({
    scope,
    loaders: areasLoaders,
    initialData: {
      bindings: [],
      riders: []
    },
    mergeData: mergeAreasData,
    autoRefreshBlocked: options?.autoRefreshBlocked ?? false
  });
  return {
    bindings: data.bindings,
    riders: data.riders,
    loadError,
    reload
  };
}

export function useDispatchProgressLiveData(scope: DispatchScope) {
  const { data, loadError } = useDispatchLiveResource({
    scope,
    loaders: progressLoaders,
    initialData: {
      areaBindings: [],
      riderProgress: []
    },
    mergeData: mergeProgressData,
    pollingMs: PROGRESS_POLLING_MS
  });
  return {
    areaBindings: data.areaBindings,
    riderProgress: data.riderProgress,
    loadError
  };
}
