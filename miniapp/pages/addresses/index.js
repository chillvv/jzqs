const { request } = require('../../utils/request');

Page({
  data: {
    items: [],
    loading: false,
    saving: false,
    showPopup: false,
    selectOrderId: null,
    form: {
      id: null,
      contactName: '',
      contactPhone: '',
      addressLine: '',
      isDefault: true
    }
  },

  onLoad(options) {
    if (options.selectOrderId) {
      this.setData({ selectOrderId: Number(options.selectOrderId) });
      wx.setNavigationBarTitle({ title: '选择配送地址' });
    }
  },

  onShow() {
    this.loadAddresses();
  },

  onPullDownRefresh() {
    this.loadAddresses();
  },

  async loadAddresses() {
    this.setData({ loading: true });
    try {
      const items = await request({
        url: '/api/mobile/customer/addresses'
      });
      // Sort items to put default at the top
      items.sort((a, b) => (b.isDefault ? 1 : 0) - (a.isDefault ? 1 : 0));
      this.setData({ items });
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  showAddPopup() {
    this.setData({ 
      showPopup: true,
      form: {
        id: null,
        contactName: '',
        contactPhone: '',
        addressLine: '',
        isDefault: false
      }
    });
  },

  hideAddPopup() {
    this.setData({ showPopup: false });
  },

  editAddress(e) {
    const id = e.currentTarget.dataset.id;
    const address = this.data.items.find(item => item.id === id);
    if (address) {
      this.setData({
        showPopup: true,
        form: {
          id: address.id,
          contactName: address.contactName,
          contactPhone: address.contactPhone,
          addressLine: address.addressLine,
          isDefault: address.isDefault
        }
      });
    }
  },

  async deleteAddress(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认删除',
      content: '确定要删除这个地址吗？',
      success: async (res) => {
        if (res.confirm) {
          try {
            await request({
              url: `/api/mobile/customer/addresses/${id}`,
              method: 'DELETE'
            });
            wx.showToast({ title: '删除成功', icon: 'success' });
            this.loadAddresses();
          } catch (error) {
            wx.showToast({ title: error.message || '删除失败', icon: 'none' });
          }
        }
      }
    });
  },

  onInputChange(e) {
    const { field } = e.currentTarget.dataset;
    this.setData({
      [`form.${field}`]: e.detail.value
    });
  },

  onDefaultChange(e) {
    this.setData({
      'form.isDefault': e.detail.value
    });
  },

  importWechatAddress() {
    wx.chooseAddress({
      success: (res) => {
        this.setData({
          'form.contactName': res.userName || '',
          'form.contactPhone': res.telNumber || '',
          'form.addressLine': `${res.provinceName || ''}${res.cityName || ''}${res.countyName || ''}${res.detailInfo || ''}`.trim()
        });
        wx.showToast({ title: '已导入微信地址', icon: 'success' });
      },
      fail: () => {
        wx.showToast({ title: '未获取到微信地址', icon: 'none' });
      }
    });
  },

  async saveAddress() {
    if (this.data.saving) {
      return;
    }
    const { id, contactName, contactPhone, addressLine, isDefault } = this.data.form;
    if (!contactName.trim() || !contactPhone.trim() || !addressLine.trim()) {
      wx.showToast({ title: '请完整填写地址信息', icon: 'none' });
      return;
    }
    this.setData({ saving: true });
    try {
      if (id) {
        await request({
          url: `/api/mobile/customer/addresses/${id}`,
          method: 'PUT',
          header: { 'content-type': 'application/json' },
          data: {
            contactName: contactName.trim(),
            contactPhone: contactPhone.trim(),
            addressLine: addressLine.trim(),
            isDefault
          }
        });
      } else {
        await request({
          url: '/api/mobile/customer/addresses',
          method: 'POST',
          header: { 'content-type': 'application/json' },
          data: {
            contactName: contactName.trim(),
            contactPhone: contactPhone.trim(),
            addressLine: addressLine.trim(),
            isDefault
          }
        });
      }
      
      wx.showToast({ title: '地址已保存', icon: 'success' });
      this.setData({
        showPopup: false,
        form: {
          id: null,
          contactName: '',
          contactPhone: '',
          addressLine: '',
          isDefault: false
        }
      });
      this.loadAddresses();
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
    } finally {
      this.setData({ saving: false });
    }
  },

  async setDefault(e) {
    const { id } = e.currentTarget.dataset;
    try {
      await request({
        url: `/api/mobile/customer/addresses/${id}/default`,
        method: 'POST'
      });
      wx.showToast({ title: '默认地址已更新', icon: 'success' });
      this.loadAddresses();
    } catch (error) {
      wx.showToast({ title: error.message || '更新失败', icon: 'none' });
    }
  },

  async selectAddressForOrder(e) {
    const { id } = e.currentTarget.dataset;
    const { selectOrderId } = this.data;
    if (!selectOrderId) {
      return;
    }
    try {
      await request({
        url: `/api/mobile/customer/orders/${selectOrderId}/change-address`,
        method: 'POST',
        header: { 'content-type': 'application/json' },
        data: { addressId: id }
      });
      wx.showToast({ title: '地址已切换', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 500);
    } catch (error) {
      wx.showToast({ title: error.message || '切换失败', icon: 'none' });
    }
  }
});
