const { submitAftersaleApplication } = require("../../utils/aftersale");
const { buildAftersaleNotice } = require("../../utils/customer-order-flow");

const REASON_OPTIONS = [
  { value: "USER_TEMP_CHANGE", label: "临时有事" },
  { value: "DELIVERY_EXCEPTION", label: "配送异常" },
  { value: "MEAL_QUALITY", label: "餐品问题" }
];

Page({
  data: {
    orderId: "",
    type: "REFUND",
    reasonCode: "USER_TEMP_CHANGE",
    reasonText: "临时有事",
    remark: "",
    reasonOptions: REASON_OPTIONS,
    noticeText: buildAftersaleNotice("REFUND"),
    submitting: false
  },

  onLoad(options) {
    this.setData({
      orderId: options.orderId || ""
    });
  },

  onTypeChange(e) {
    const type = e.detail.value;
    this.setData({
      type,
      noticeText: buildAftersaleNotice(type)
    });
  },

  onReasonChange(e) {
    const reasonCode = e.detail.value;
    const matched = REASON_OPTIONS.find((item) => item.value === reasonCode);
    this.setData({
      reasonCode,
      reasonText: matched ? matched.label : ""
    });
  },

  onRemarkInput(e) {
    this.setData({ remark: e.detail.value });
  },

  submit() {
    const { orderId, type, reasonCode, reasonText, remark, submitting } = this.data;
    if (!orderId || submitting) {
      return;
    }
    this.setData({ submitting: true });
    return submitAftersaleApplication(orderId, {
      type,
      reasonCode,
      reasonText,
      remark
    }).then(() => {
      wx.showModal({
        title: "已提交申请",
        content: buildAftersaleNotice(type),
        showCancel: false,
        success: () => wx.navigateBack()
      });
    }).catch((error) => {
      wx.showToast({
        title: error.message || "提交失败",
        icon: "none"
      });
    }).finally(() => {
      this.setData({ submitting: false });
    });
  }
});
