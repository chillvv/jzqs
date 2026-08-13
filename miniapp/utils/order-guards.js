function getCheckoutMealLimitMessage({ totalQty, remainingMeals }) {
  if (remainingMeals <= 0) {
    return '当前剩余餐数为 0，请联系专属客服办理套餐后再下单';
  }
  if (totalQty > remainingMeals) {
    return '剩余餐次不足，请调整餐食数量后再结算';
  }
  return '';
}

// ============================================================
// 夜间停止下单（后厨/系统结算休整）
// 每天 23:30 起至次日 08:00 止，系统进入结算休整，不接受新订单。
// 即便次日菜单已发布、可下单，该时段内也只展示提示、不开放下单入口。
// 时间按用户本地时间判定（前端友好拦截，后端下单接口仍需二次校验）。
// ============================================================
const NIGHT_ORDER_START_HOUR = 23;
const NIGHT_ORDER_START_MINUTE = 30; // 23:30 起
const NIGHT_ORDER_END_HOUR = 8; // 次日 08:00 恢复

function isNightOrderClosed(now) {
  const date = now instanceof Date ? now : new Date();
  const minutes = date.getHours() * 60 + date.getMinutes();
  const start = NIGHT_ORDER_START_HOUR * 60 + NIGHT_ORDER_START_MINUTE; // 1410
  const end = NIGHT_ORDER_END_HOUR * 60; // 480
  // 窗口跨越午夜：[start, 1440) ∪ [0, end)
  return minutes >= start || minutes < end;
}

function getNightCloseNotice() {
  return {
    title: '系统夜间结算中',
    content:
      '为了给您更准时的配送，系统每晚 23:30 起进入结算休整，暂无法下单' +
      '别急，明早 08:00 一切就绪，更多好菜等您～',
    desc:
      '为了给您更准时的配送，系统每晚 23:30 起进入结算休整，暂无法下单' +
      '别急，明早 08:00 一切就绪，更多好菜等您～',
    buttonText: '23:30 后暂停下单'
  };
}

// 送餐当天联系客服的最后时限：过了这个点，餐已经出餐上路，客服也拦不下来。
const SUPPORT_REFUND_CUTOFF_HOUR = 9;

function parseDateTime(value) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function parseServeDay(serveDate) {
  const parsed = new Date(`${serveDate}T00:00:00`);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function startOfDay(date) {
  const copy = new Date(date);
  copy.setHours(0, 0, 0, 0);
  return copy;
}

/**
 * 自助秒退款（取消预订）：只要送餐日还没到（即"前一天"及更早）即可操作，
 * 送餐当天不可秒退、需联系商家协商。
 * 判定标准与「换地址」保持一致（serveDate.isAfter(today)），不再复用下单截止 23:00。
 * 状态上只要「还没送到」都放行 —— 后台已分配骑手（DISPATCHING）不应该堵住用户退款。
 */
function canCancelMiniappOrder({ status, serveDate, now }) {
  if (!isUndeliveredOrderStatus(status)) {
    return false;
  }
  if (!serveDate || !now) {
    return false;
  }
  const current = parseDateTime(now);
  const serveDay = parseServeDay(serveDate);
  if (!current || !serveDay) {
    return false;
  }
  const today = startOfDay(current);
  // serveDay 在"今天之后"= 送餐日还没到 = 前一天及更早，可秒退；当天/过期则不可。
  return serveDay.getTime() > today.getTime();
}

function isUndeliveredOrderStatus(status) {
  return status === 'PENDING_DISPATCH' || status === 'DISPATCHING' || status === 'DISPATCHED';
}

/**
 * 未送达、但已不可自助秒退的订单，给一个"联系商家协商 / 申请售后"的出口，避免用户卡住没有任何按钮。
 * 返回 '' 表示仍可自助（还能秒退或订单已完结）；
 * 'SAME_DAY' 送餐当天，引导联系商家协商（委婉、以商家反馈为准）；
 * 'AFTER_CUTOFF' 送餐日已过，引导签收后走售后。
 */
function resolveSupportRefundStage({ status, serveDate, now }) {
  if (!isUndeliveredOrderStatus(status) || !serveDate || !now) {
    return '';
  }
  if (canCancelMiniappOrder({ status, serveDate, now })) {
    return '';
  }
  const current = parseDateTime(now);
  const serveDay = parseServeDay(serveDate);
  if (!current || !serveDay) {
    return '';
  }
  const today = startOfDay(current);
  // 送餐当天：当天不可秒退，引导联系商家协商
  if (serveDay.getTime() === today.getTime()) {
    return 'SAME_DAY';
  }
  // 送餐日已经过去：早就该送到了，走售后而不是退款
  if (serveDay.getTime() < today.getTime()) {
    return 'AFTER_CUTOFF';
  }
  // 理论上不会到这（canCancel 已覆盖所有未来日期），保险归为可协商
  return 'SAME_DAY';
}

function buildSupportRefundNotice(stage) {
  if (stage === 'SAME_DAY') {
    return {
      title: '送餐当天需联系商家',
      content: '这单今天就要配送啦，系统暂时没法直接取消。您可以先和商家协商下，看能不能调整，能不能处理以商家反馈为准'
    };
  }
  return {
    title: '建议送达后申请售后',
    content: '这单可能已出餐或配送中，建议先签收。如有问题，可在送达后申请售后处理'
  };
}

module.exports = {
  SUPPORT_REFUND_CUTOFF_HOUR,
  getCheckoutMealLimitMessage,
  canCancelMiniappOrder,
  isUndeliveredOrderStatus,
  resolveSupportRefundStage,
  buildSupportRefundNotice,
  isNightOrderClosed,
  getNightCloseNotice
};
