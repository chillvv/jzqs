const { request } = require('../../utils/request');

function normalizePhone(value) {
  return String(value || '').replace(/\D/g, '');
}

function buildEmptyForm(customerProfile, isDefault) {
  return {
    id: null,
    contactName: customerProfile.name || '',
    contactPhone: customerProfile.phone || '',
    addressLine: '',
    isDefault: Boolean(isDefault)
  };
}

function validateAddressForm(form, customerProfile) {
  const contactName = String(customerProfile.name || '').trim();
  const contactPhone = normalizePhone(customerProfile.phone);
  const addressLine = String(form.addressLine || '').trim();

  if (!contactName || !contactPhone) {
    return '请先完善姓名和手机号';
  }
  if (addressLine.length < 4) {
    return '详细地址至少 4 个字';
  }
  if (addressLine.length > 120) {
    return '详细地址不能超过120个字';
  }
  return '';
}

Page({
  data: {
    items: [],
    loading: false,
    saving: false,
    showPopup: false,
    selectOrderId: null,
    statusBarHeight: 0,
    navBarHeight: 44,
    customerProfile: {
      name: '',
      phone: ''
    },
    form: buildEmptyForm({ name: '', phone: '' }, true)
  },

  onLoad(options) {
    const app = getApp();
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight
    });
    if (options.selectOrderId) {
      this.setData({ selectOrderId: Number(options.selectOrderId) });
      wx.setNavigationBarTitle({ title: '选择配送地址' });
    }
  },

  onShow() {
    Promise.all([
      this.loadCustomerProfile(),
      this.loadAddresses()
    ]).catch(() => {});
  },

  onPullDownRefresh() {
    Promise.all([
      this.loadCustomerProfile(),
      this.loadAddresses()
    ]).catch(() => {});
  },

  async loadCustomerProfile() {
    try {
      const home = await request({
        url: '/api/mobile/customer/home'
      });
      const customerProfile = {
        name: String(home && home.name ? home.name : '').trim(),
        phone: normalizePhone(home && home.phone ? home.phone : '')
      };
      const nextForm = {
        ...this.data.form,
        contactName: customerProfile.name,
        contactPhone: customerProfile.phone
      };
      this.setData({
        customerProfile,
        form: nextForm
      });
    } catch (error) {
      wx.showToast({ title: error.message || '用户信息加载失败', icon: 'none' });
    }
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
    const { customerProfile, items } = this.data;
    this.setData({
      showPopup: true,
      form: buildEmptyForm(customerProfile, items.length === 0)
    });
  },

  hideAddPopup() {
    this.setData({ showPopup: false });
  },

  editAddress(e) {
    const id = Number(e.currentTarget.dataset.id);
    const address = this.data.items.find(item => Number(item.id) === id);
    if (address) {
      this.setData({
        showPopup: true,
        form: {
          id: address.id,
          contactName: this.data.customerProfile.name,
          contactPhone: this.data.customerProfile.phone,
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
    const { id, addressLine, isDefault } = this.data.form;
    const { customerProfile } = this.data;
    const validationError = validateAddressForm(this.data.form, customerProfile);
    if (validationError) {
      wx.showToast({ title: validationError, icon: 'none' });
      return;
    }
    const payload = {
      contactName: customerProfile.name.trim(),
      contactPhone: normalizePhone(customerProfile.phone),
      addressLine: addressLine.trim(),
      areaCode: '',
      isDefault
    };
    this.setData({ saving: true });
    try {
      if (id) {
        await request({
          url: `/api/mobile/customer/addresses/${id}`,
          method: 'PUT',
          header: { 'content-type': 'application/json' },
          data: payload
        });
      } else {
        await request({
          url: '/api/mobile/customer/addresses',
          method: 'POST',
          header: { 'content-type': 'application/json' },
          data: payload
        });
      }

      wx.showToast({ title: '地址已保存', icon: 'success' });
      this.setData({
        showPopup: false,
        form: buildEmptyForm(customerProfile, false)
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
