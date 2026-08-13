// 引导蒙层（交互版）：
// - interactive=true 时蒙层不拦截点击（pointer-events:none），用户可真实点击页面按钮（配合演示沙盒）。
// - 每步「懒定位」：切到该步时才查询元素当前位置，适配视图切换（如 菜单→结算→成功弹窗）。
// - 数据驱动：页面通过 setData 设 gTrigger/gSteps/gAccent 就能触发起动，不依赖 selectComponent 时序。
Component({
  properties: {
    gTrigger: { type: Number, value: 0 },
    gSteps: { type: Array, value: [] },
    gAccent: { type: String, value: '#639922' }
  },

  observers: {
    // 同时监听 trigger 和 steps —— 确保两样都到了才启动
    'gTrigger, gSteps' (trigger, steps) {
      if (!trigger || !steps || !steps.length) return;
      if (trigger === this._lastTrigger) return;
      this._lastTrigger = trigger;
      this._safeStart(trigger, steps);
    }
  },

  data: {
    visible: false,
    current: 0,
    total: 0,
    steps: [],
    step: null,
    accent: '#639922',
    hole: { left: 0, top: 0, width: 0, height: 0 },
    maskRects: [],
    tip: { left: 0, top: 0, width: 300 },
    placement: 'bottom',
    arrowLeft: 150,
    isLast: false,
    centered: false,
    interactive: false
  },

  lifetimes: {
    ready() {
      // 安全网：如果 10 秒内引导还没启动但 demo 开着 → 自动推进 flow，防止用户困死
      this._safetyTimer = setTimeout(() => {
        if (!this.data.visible) {
          const pages = getCurrentPages();
          const page = pages[pages.length - 1];
          const cb = (page && page.__guideCBs) || {};
          if (typeof cb.onDone === 'function') {
            try { cb.onDone(); } catch (e) {}
          }
        }
      }, 10000);
    },
    detached() {
      if (this._safetyTimer) { clearTimeout(this._safetyTimer); this._safetyTimer = null; }
    }
  },

  methods: {
    _safeStart(trigger, steps) {
      try {
        const pages = getCurrentPages();
        const page = pages[pages.length - 1];
        const cb = (page && page.__guideCBs) || {};
        this.start(steps, this.properties.gAccent, {
          pageCtx: page || null,
          interactive: cb.interactive || false,
          onSkip: cb.onSkip || null,
          onDone: cb.onDone || null
        });
      } catch (e) {
        // 极端兜底：start 失败也不能困住用户，直接推进到下一步
        const cb = (page && page.__guideCBs) || {};
        if (typeof cb.onDone === 'function') {
          try { cb.onDone(); } catch (e2) {}
        }
      }
    },
    start(steps, accent, opts) {
      if (!steps || !steps.length) {
        return;
      }
      opts = opts || {};
      this._pageCtx = opts.pageCtx || null;
      this._interactive = !!opts.interactive;
      this._onSkip = (typeof opts.onSkip === 'function') ? opts.onSkip : null;
      this._onDone = (typeof opts.onDone === 'function') ? opts.onDone : null;
      this._scrollAttempted = {};
      this.setData({
        visible: true,
        steps,
        current: 0,
        total: steps.length,
        accent: accent || '#639922',
        interactive: this._interactive
      });
      this._layout();
    },

    hide() {
      this.setData({ visible: false });
    },

    // 懒定位：只查当前这一步的元素位置（selector + fallbacks 中第一个可见的）
    _queryRect(step) {
      return new Promise((resolve) => {
        const selectors = [];
        if (step.selector) selectors.push(step.selector);
        (step.fallbacks || []).forEach((s) => selectors.push(s));
        if (!selectors.length) {
          resolve(null);
          return;
        }
        const q = (this._pageCtx && typeof this._pageCtx.createSelectorQuery === 'function')
          ? this._pageCtx.createSelectorQuery()
          : this.createSelectorQuery();
        selectors.forEach((s) => q.select(s).boundingClientRect());
        q.exec((res) => {
          const hit = (res || []).find((r) => r && r.width > 0 && r.height > 0);
          resolve(hit
            ? { top: hit.top, left: hit.left, width: hit.width, height: hit.height }
            : null);
        });
      });
    },

    async _layout() {
      const steps = this.data.steps;
      const step = steps[this.data.current] || null;
      if (!step) {
        return;
      }
      // 需要高亮的步骤：切到该步时实时查询位置；查不到（元素未渲染/视图未切换）→ 居中说明兜底
      if (step.selector) {
        const rect = await this._queryRect(step);
        if (rect) {
          // 元素在可视区外 → 先滚到可见位置再重新查询
          const sys = (wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync());
          const H = sys.windowHeight || sys.screenHeight;
          const stepIdx = this.data.current;
          const outOfView = rect.top > H * 0.75 || rect.bottom < 0;
          if (outOfView && !this._scrollAttempted[stepIdx]) {
            this._scrollAttempted[stepIdx] = true;
            await this._scrollToView(step, rect);
            // 重新查询滚动后的位置
            const rect2 = await this._queryRect(step);
            if (rect2) {
              this._layoutHole(step, rect2);
              return;
            }
          }
          this._layoutHole(step, rect);
          return;
        }
      }
      this._layoutCentered(step);
    },

    // 将目标元素滚到可视区域内
    _scrollToView(step, rect) {
      return new Promise((resolve) => {
        if (step.scrollViewSelector) {
          // 在 scroll-view 内：使用 scrollIntoView
          try {
            wx.createSelectorQuery()
              .select(step.scrollViewSelector)
              .node()
              .exec((res) => {
                if (res[0] && res[0].node && typeof res[0].node.scrollIntoView === 'function') {
                  res[0].node.scrollIntoView(step.selector);
                }
                setTimeout(resolve, 200);
              });
          } catch (e) {
            setTimeout(resolve, 100);
          }
        } else {
          // 普通页面滚动
          wx.pageScrollTo({
            scrollTop: Math.max(0, rect.top - 60),
            duration: 0
          });
          setTimeout(resolve, 200);
        }
      });
    },

    _layoutCentered(step) {
      const sys = (wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync());
      const W = sys.windowWidth || sys.screenWidth;
      const H = sys.windowHeight || sys.screenHeight;
      const tipW = Math.min(W - 48, 320);
      const tipH = 176;
      const tipLeft = (W - tipW) / 2;
      const tipTop = (H - tipH) / 2;
      this.setData({
        step,
        centered: true,
        hole: { left: -9999, top: -9999, width: 0, height: 0 },
        maskRects: [{ left: 0, top: 0, width: W, height: H }],
        tip: { left: tipLeft, top: tipTop, width: tipW },
        placement: 'center',
        arrowLeft: tipW / 2,
        isLast: this.data.current === this.data.steps.length - 1
      });
    },

    _layoutHole(step, rect) {
      const sys = (wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync());
      const W = sys.windowWidth || sys.screenWidth;
      const H = sys.windowHeight || sys.screenHeight;
      const pad = 8;
      const hole = {
        left: Math.max(0, rect.left - pad),
        top: Math.max(0, rect.top - pad),
        width: rect.width + pad * 2,
        height: rect.height + pad * 2
      };
      const maskRects = [
        { left: 0, top: 0, width: W, height: hole.top },
        {
          left: 0,
          top: hole.top + hole.height,
          width: W,
          height: Math.max(0, H - (hole.top + hole.height))
        },
        { left: 0, top: hole.top, width: hole.left, height: hole.height },
        {
          left: hole.left + hole.width,
          top: hole.top,
          width: Math.max(0, W - (hole.left + hole.width)),
          height: hole.height
        }
      ];
      const tipW = Math.min(W - 32, 300);
      let placement = rect.top > H / 2 ? 'top' : 'bottom';
      let tipLeft = rect.left + rect.width / 2 - tipW / 2;
      tipLeft = Math.max(16, Math.min(tipLeft, W - tipW - 16));
      const tipH = 132;
      let tipTop = placement === 'top'
        ? hole.top - tipH - 14
        : hole.top + hole.height + 14;
      if (placement === 'top' && tipTop < 8) {
        tipTop = hole.top + hole.height + 14;
        placement = 'bottom';
      }
      if (placement === 'bottom' && tipTop + tipH > H - 8) {
        tipTop = hole.top - tipH - 14;
        placement = 'top';
      }
      tipTop = Math.max(8, tipTop);
      const arrowLeft = rect.left + rect.width / 2 - tipLeft;
      this.setData({
        step,
        centered: false,
        hole,
        maskRects,
        tip: { left: tipLeft, top: tipTop, width: tipW },
        placement,
        arrowLeft,
        isLast: this.data.current === this.data.steps.length - 1
      });
    },

    onMaskTap() {
      this.next();
    },

    next() {
      if (this.data.current >= this.data.steps.length - 1) {
        if (this._onDone) {
          try { this._onDone(); } catch (e) {}
        }
        this._emit('done');
      } else {
        this.setData({ current: this.data.current + 1 });
        this._layout();
      }
    },

    onSkip() {
      if (this._onSkip) {
        try { this._onSkip(); } catch (e) {}
      }
      this._emit('skip');
    },

    _emit(evt) {
      this.setData({ visible: false });
      this.triggerEvent(evt, {});
    },

    noop() {}
  }
});
