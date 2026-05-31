/**
 * 订单详情页面
 * 提供单个订单的所有操作：导航、电话、送达确认、异常上报等
 */

const taskService = require('../../services/task.service');
const mapService = require('../../services/map.service');
const imageUtil = require('../../utils/image');
const { formatCurrentDateTime, getMealPeriodLabel } = require('../../utils/formatter');
const { EXCEPTION_TYPE_OPTIONS } = require('../../utils/constants');

Page({
  data: {
    statusBarHeight: 0,
    loading: true,
    order: null,
    deliverySectionExpanded: false,
    receiptTempFilePath: '',
    receiptNote: '',
    submitting: false,
    deferring: false,
    isEditingReceipt: false, // 是否正在编辑已提交的回执
    originalReceiptUrl: '' // 原始回执URL
  },

  /**
   * 页面加载
   */
  async onLoad(options) {
    const app = getApp();
    this.setData({ statusBarHeight: app.globalData.statusBarHeight });
    
    const { batchItemId, mealSlotOrderId, expandDelivery } = options;

    if (!batchItemId || !mealSlotOrderId) {
      wx.showToast({ title: '参数错误', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 1500);
      return;
    }

    // 如果传入 expandDelivery 参数，自动展开送达操作区
    if (expandDelivery === '1') {
      this.setData({ deliverySectionExpanded: true });
    }

    await this.loadOrderDetail(Number(batchItemId), Number(mealSlotOrderId));
  },

  /**
   * 加载订单详情
   */
  async loadOrderDetail(batchItemId, mealSlotOrderId) {
    const app = getApp();
    const riderName = app.getActiveRiderName();

    if (!riderName) {
      wx.showToast({ title: '骑手信息未就绪', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 1500);
      return;
    }

    this.setData({ loading: true });

    try {
      // 直接查单个订单详情，不依赖全量队列数据
      const order = await taskService.getOrderDetail(riderName, batchItemId);

      if (!order) {
        wx.showToast({ title: '订单不存在', icon: 'none' });
        setTimeout(() => wx.navigateBack(), 1500);
        return;
      }

      // 规范化订单数据
      const normalizedOrder = {
        ...order,
        mealLabel: getMealPeriodLabel(order.mealPeriod)
      };

      let isEditingReceipt = false;
      let originalReceiptUrl = '';
      let receiptTempFilePath = '';
      let receiptNote = '';
      
      if (normalizedOrder.itemStatus === 'DELIVERED') {
        originalReceiptUrl = normalizedOrder.receiptUrl || '';
        receiptTempFilePath = normalizedOrder.receiptUrl || '';
        receiptNote = normalizedOrder.receiptNote || '';
      }

      this.setData({
        order: normalizedOrder,
        loading: false,
        isEditingReceipt,
        originalReceiptUrl,
        receiptTempFilePath,
        receiptNote
      });

    } catch (error) {
      console.error('[订单详情] 加载失败', error);
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 1500);
    }
  },

  /**
   * 展开/收起送达操作区
   */
  toggleDeliverySection() {
    this.setData({
      deliverySectionExpanded: !this.data.deliverySectionExpanded
    });
  },

  /**
   * 导航
   */
  handleNavigate() {
    const { order } = this.data;
    if (!order) return;

    mapService.navigate(order);
  },

  /**
   * 拨打电话
   */
  handleCall() {
    const { order } = this.data;
    if (!order || !order.customerPhone) {
      wx.showToast({ title: '电话号码不可用', icon: 'none' });
      return;
    }

    wx.makePhoneCall({
      phoneNumber: order.customerPhone,
      fail: (error) => {
        console.error('[拨打电话] 失败', error);
        wx.showToast({ title: '拨打失败', icon: 'none' });
      }
    });
  },

  /**
   * 复制地址
   */
  handleCopyAddress() {
    const { order } = this.data;
    if (!order || !order.deliveryAddress) {
      wx.showToast({ title: '地址不可用', icon: 'none' });
      return;
    }

    wx.setClipboardData({
      data: order.deliveryAddress,
      success: () => {
        wx.showToast({
          title: '地址已复制',
          icon: 'success',
          duration: 2000
        });
      }
    });
  },

  /**
   * 选择照片
   */
  async choosePhoto() {
    try {
      const paths = await imageUtil.chooseAndCompressImage({
        count: 1,
        sourceType: ['camera', 'album'],
        quality: 80
      });

      if (paths.length > 0) {
        this.setData({
          receiptTempFilePath: paths[0]
        });
        wx.showToast({ title: '照片已选择', icon: 'success' });
      }
    } catch (error) {
      console.error('[选择照片] 失败', error);
      wx.showToast({ title: error.message || '选择照片失败', icon: 'none' });
    }
  },

  /**
   * 预览照片
   */
  previewPhoto() {
    const { receiptTempFilePath, order, isEditingReceipt } = this.data;
    
    // 如果是已送达且未编辑模式，预览原有照片
    let imageUrl = receiptTempFilePath;
    if (!isEditingReceipt && order && order.itemStatus === 'DELIVERED' && order.receiptUrl) {
      imageUrl = order.receiptUrl;
    }
    
    if (!imageUrl) return;

    imageUtil.previewImage([imageUrl], 0);
  },

  /**
   * 删除照片（撤回编辑）
   */
  removePhoto() {
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这张照片吗？',
      success: (res) => {
        if (res.confirm) {
          this.setData({
            receiptTempFilePath: ''
          });
          wx.showToast({
            title: '照片已删除',
            icon: 'success'
          });
        }
      }
    });
  },

  /**
   * 进入编辑回执模式
   */
  enterEditReceiptMode() {
    const { order } = this.data;
    if (!order || order.itemStatus !== 'DELIVERED') {
      wx.showToast({ title: '该订单未送达', icon: 'none' });
      return;
    }

    // 进入编辑模式，保留原有的照片和备注
    this.setData({
      isEditingReceipt: true,
      receiptTempFilePath: order.receiptUrl || '', // 保留原有照片URL
      receiptNote: order.receiptNote || '' // 保留原有备注
    });

    wx.showToast({
      title: '可以重新拍照或修改',
      icon: 'none',
      duration: 1500
    });
  },

  /**
   * 取消编辑回执
   */
  cancelEditReceipt() {
    const { order } = this.data;
    
    // 退出编辑模式，恢复原有数据
    this.setData({
      isEditingReceipt: false,
      receiptTempFilePath: order.receiptUrl || '', // 恢复原有照片
      receiptNote: order.receiptNote || '' // 恢复原有备注
    });

    wx.showToast({
      title: '已取消编辑',
      icon: 'none',
      duration: 1500
    });
  },

  /**
   * 删除已提交的回执照片
   */
  async deleteSubmittedReceipt() {
    const { order } = this.data;
    if (!order || order.itemStatus !== 'DELIVERED') {
      wx.showToast({ title: '该订单未送达', icon: 'none' });
      return;
    }

    // 确认对话框
    const confirmResult = await new Promise(resolve => {
      wx.showModal({
        title: '删除照片',
        content: '确定要删除已提交的送达照片吗？删除后用户将无法查看照片。',
        confirmText: '确定删除',
        confirmColor: '#ff4444',
        cancelText: '取消',
        success: res => resolve(res.confirm),
        fail: () => resolve(false)
      });
    });

    if (!confirmResult) return;

    const app = getApp();
    const riderName = app.getActiveRiderName();

    wx.showLoading({ title: '删除中...', mask: true });

    try {
      await taskService.deleteReceiptImage(riderName, order.mealSlotOrderId, order.receiptUrl);

      wx.hideLoading();
      wx.showToast({
        title: '照片已删除',
        icon: 'success',
        duration: 2000
      });

      // 重新加载订单详情
      setTimeout(() => {
        this.loadOrderDetail(order.batchItemId, order.mealSlotOrderId);
      }, 1000);

    } catch (error) {
      console.error('[删除照片] 失败', error);
      wx.hideLoading();
      wx.showToast({ title: error.message || '删除失败', icon: 'none' });
    }
  },

  /**
   * 回执说明输入
   */
  onReceiptNoteInput(e) {
    this.setData({
      receiptNote: e.detail.value
    });
  },

  goBack() {
    wx.navigateBack();
  },

  /**
   * 提交送达回执
   */
  async handleSubmitReceipt() {
    const { order, receiptTempFilePath, receiptNote, submitting, isEditingReceipt } = this.data;

    if (submitting) return;

    if (!order) {
      wx.showToast({ title: '订单信息不存在', icon: 'none' });
      return;
    }

    // 验证：照片和说明至少要有一个
    const hasPhoto = receiptTempFilePath && receiptTempFilePath.trim();
    const hasNote = receiptNote && receiptNote.trim();
    
    if (!hasPhoto && !hasNote) {
      wx.showToast({ 
        title: '请至少上传照片或填写说明', 
        icon: 'none',
        duration: 2000
      });
      return;
    }

    const app = getApp();
    const riderName = app.getActiveRiderName();

    this.setData({ submitting: true });
    wx.showLoading({ title: isEditingReceipt ? '更新中...' : '提交中...', mask: true });

    try {
      let receiptFileKey = '';
      
      // 1. 上传图片 (如果选择了图片)
      if (receiptTempFilePath && !receiptTempFilePath.startsWith('https://') && !receiptTempFilePath.startsWith('cloud://')) {
        console.log('[提交回执] 开始上传图片', receiptTempFilePath);
        const uploadResult = await taskService.uploadReceipt(riderName, receiptTempFilePath);
        receiptFileKey = uploadResult.fileKey;
        console.log('[提交回执] 上传成功，fileKey:', receiptFileKey);
        
        // 验证上传结果
        if (!receiptFileKey || !receiptFileKey.startsWith('https://')) {
          throw new Error('图片上传失败，请重试');
        }
      } else if (receiptTempFilePath && (receiptTempFilePath.startsWith('https://') || receiptTempFilePath.startsWith('cloud://'))) {
        // 如果是原有的HTTPS URL或云存储fileID，直接使用
        receiptFileKey = receiptTempFilePath;
        console.log('[提交回执] 使用已有URL:', receiptFileKey);
      } else {
        // 没有选择图片，只使用文字说明
        receiptFileKey = '';
        console.log('[提交回执] 只使用文字说明');
      }

      const deliveredAt = formatCurrentDateTime();

      // 2. 根据是否编辑模式调用不同的API
      if (isEditingReceipt) {
        await taskService.updateReceipt(
          riderName,
          order.mealSlotOrderId,
          receiptFileKey,
          receiptNote || '',
          deliveredAt
        );
      } else {
        await taskService.submitReceipt(
          riderName,
          order.mealSlotOrderId,
          receiptFileKey,
          receiptNote || '',
          deliveredAt
        );
      }

      wx.hideLoading();
      wx.showToast({
        title: isEditingReceipt ? '更新成功' : '送达成功',
        icon: 'success',
        duration: 1200
      });

      // 短提示后立即返回，避免人为等待造成卡顿感
      setTimeout(() => {
        wx.navigateBack();
      }, 120);

    } catch (error) {
      console.error('[提交回执] 失败', error);
      wx.hideLoading();
      wx.showToast({ title: error.message || '提交失败', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  },

  /**
   * 撤回送达（允许骑手修改误触的送达状态）
   */
  async handleUndoDelivery() {
    const { order } = this.data;

    if (!order || order.itemStatus !== 'DELIVERED') {
      wx.showToast({ title: '该订单未送达', icon: 'none' });
      return;
    }

    // 确认对话框
    const confirmResult = await new Promise(resolve => {
      wx.showModal({
        title: '撤回送达',
        content: '确定要撤回这单的送达状态吗？撤回后需要重新确认送达。',
        confirmText: '确定撤回',
        cancelText: '取消',
        success: res => resolve(res.confirm),
        fail: () => resolve(false)
      });
    });

    if (!confirmResult) return;

    const app = getApp();
    const riderName = app.getActiveRiderName();

    wx.showLoading({ title: '处理中...', mask: true });

    try {
      await taskService.undoDelivery(riderName, order.mealSlotOrderId);
      
      wx.hideLoading();
      wx.showToast({
        title: '已撤回送达',
        icon: 'success',
        duration: 2000
      });

      // 重新加载订单详情
      setTimeout(() => {
        this.loadOrderDetail(order.batchItemId, order.mealSlotOrderId);
      }, 1000);

    } catch (error) {
      console.error('[撤回送达] 失败', error);
      wx.hideLoading();
      wx.showToast({ title: error.message || '撤回失败', icon: 'none' });
    }
  },

  /**
   * 稍后送这单
   */
  async handleDefer() {
    const { order, deferring } = this.data;

    if (deferring) return;

    if (!order) {
      wx.showToast({ title: '订单信息不存在', icon: 'none' });
      return;
    }

    // 确认对话框
    const confirmResult = await new Promise(resolve => {
      wx.showModal({
        title: '稍后送',
        content: '确定将这单改为稍后送吗？',
        success: res => resolve(res.confirm),
        fail: () => resolve(false)
      });
    });

    if (!confirmResult) return;

    const app = getApp();
    const riderName = app.getActiveRiderName();

    this.setData({ deferring: true });

    try {
      await taskService.deferQueueItem(riderName, order.batchItemId);

      wx.showToast({
        title: '已改为稍后送',
        icon: 'success',
        duration: 2000
      });

      // 延迟返回队列页面
      setTimeout(() => {
        wx.navigateBack();
      }, 2000);

    } catch (error) {
      console.error('[稍后送] 失败', error);
      wx.showToast({ title: error.message || '操作失败', icon: 'none' });
    } finally {
      this.setData({ deferring: false });
    }
  },

  /**
   * 上报异常
   */
  async handleReportException() {
    const { order } = this.data;

    if (!order) {
      wx.showToast({ title: '订单信息不存在', icon: 'none' });
      return;
    }

    // 1. 选择异常类型
    const exceptionType = await this.selectExceptionType();
    if (!exceptionType) return;

    // 2. 输入异常说明
    const exceptionNote = await this.inputExceptionNote();
    if (!exceptionNote) return;

    // 3. 提交异常
    await this.submitException(exceptionType, exceptionNote);
  },

  reportException() {
    return this.handleReportException();
  },

  /**
   * 选择异常类型
   */
  selectExceptionType() {
    return new Promise(resolve => {
      wx.showActionSheet({
        itemList: EXCEPTION_TYPE_OPTIONS.map(opt => opt.label),
        success: (res) => {
          const selected = EXCEPTION_TYPE_OPTIONS[res.tapIndex];
          resolve(selected.value);
        },
        fail: () => resolve(null)
      });
    });
  },

  /**
   * 输入异常说明
   */
  inputExceptionNote() {
    return new Promise(resolve => {
      wx.showModal({
        title: '异常说明',
        content: '请输入异常详细说明',
        editable: true,
        placeholderText: '请描述具体情况...',
        success: (res) => {
          if (res.confirm && res.content && res.content.trim()) {
            resolve(res.content.trim());
          } else {
            resolve(null);
          }
        },
        fail: () => resolve(null)
      });
    });
  },

  /**
   * 提交异常
   */
  async submitException(exceptionType, exceptionNote) {
    const { order } = this.data;
    const app = getApp();
    const riderName = app.getActiveRiderName();

    wx.showLoading({ title: '提交中...', mask: true });

    try {
      await taskService.reportException(
        riderName,
        order.mealSlotOrderId,
        exceptionType,
        exceptionNote,
        [] // 暂不支持上传异常图片
      );

      wx.hideLoading();
      wx.showToast({
        title: '异常已上报',
        icon: 'success',
        duration: 2000
      });

      // 延迟返回队列页面
      setTimeout(() => {
        wx.navigateBack();
      }, 2000);

    } catch (error) {
      console.error('[上报异常] 失败', error);
      wx.hideLoading();
      wx.showToast({ title: error.message || '上报失败', icon: 'none' });
    }
  }
});
