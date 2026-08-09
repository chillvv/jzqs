// 引导蒙层（交互版）：
// - interactive=true 时蒙层不拦截点击（pointer-events:none），用户可真实点击页面按钮（配合演示沙盒）。
// - 每步「懒定位」：切到该步时才查询元素当前位置，适配视图切换（如 菜单→结算→成功弹窗）。
Component({
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

  methods: {
    start(steps, accent, opts) {
      if (!steps || !steps.length) {
        return;
      }
      opts = opts || {};
      this._pageCtx = opts.pageCtx || null;
      this._interactive = !!opts.interactive;
      this._onSkip = (typeof opts.onSkip === 'function') ? opts.onSkip : null;
      this._onDone = (typeof opts.onDone === 'function') ? opts.onDone : null;
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
          this._layoutHole(step, rect);
          return;
        }
      }
      this._layoutCentered(step);
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
