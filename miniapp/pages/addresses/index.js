const { shareAppMessage, shareTimeline } = require('../../utils/share');
const { request } = require('../../utils/request');

function normalizePhone(value) {
  return String(value || '').replace(/\D/g, '');
}

const MAX_ADDRESSES_PER_CUSTOMER = 5;

// 拆分完整地址为「省市区（区划）」和「详细地址」，供列表分两段展示（参考京东：区划小灰字 + 详细大加粗）
function splitAddress(addressLine) {
  const text = String(addressLine || '').trim();
  // 优先按空格拆分（选点生成 "省市区 详细地址"），无空格再按区划前缀拆分
  const m = text.match(/^(.+?[区县])\s+(.+)$/) || text.match(/^(.+?[区县])(.+)$/);
  if (m && m[1] && m[2]) {
    return { district: m[1].trim(), detail: m[2].trim() };
  }
  return { district: '', detail: text };
}

function buildEmptyForm(customerProfile, isDefault) {
  return {
    id: null,
    contactName: customerProfile.name || '',
    contactPhone: customerProfile.phone || '',
    addressLine: '',
    doorNumber: '',
    isDefault: Boolean(isDefault),
    latitude: null,
    longitude: null
  };
}

function validateAddressForm(form, customerProfile) {
  const contactName = String(customerProfile.name || '').trim();
  const contactPhone = normalizePhone(customerProfile.phone);
  const addressLine = String(form.addressLine || '').trim();

  if (!contactName || !contactPhone) {
    return '请先完善姓名和手机号';
  }
  if (!addressLine) {
    return '请先地图选点定位';
  }
  if (addressLine.length > 120) {
    return '定位地址过长';
  }
  return '';
}

Page({
  onShareAppMessage: shareAppMessage,
  onShareTimeline: shareTimeline,
  data: {
    items: [],
    loading: false,
    saving: false,
    showPopup: false,
    selectOrderId: null,
    currentAddressId: null,
    currentOrderDeliveryAddress: '',
    noOtherAddress: false,
    addressLimitReached: false,
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
      const selectOrderId = Number(options.selectOrderId);
      this.setData({
        selectOrderId,
        currentAddressId: options.currentAddressId ? Number(options.currentAddressId) : null
      });
      wx.setNavigationBarTitle({ title: '选择配送地址' });
      // 主动去订单列表里查这条订单的"地址文本"，再和地址列表逐条比对——
      // 即使后端 list 接口还没把 addressId 字段返回、或者上游入口漏传 currentAddressId，
      // 选择页依然能精准标记哪个是当前订单地址，避免出现"只有一个地址还是当前地址却显示使用这个地址"的自相矛盾。
      this.loadOrderAddressContext(selectOrderId);
    }
    // 从下单页跳转过来补定位：自动打开对应地址的编辑弹窗
    if (options.editId) {
      this.pendingEditId = Number(options.editId);
    }
  },

  async loadOrderAddressContext(orderId) {
    try {
      const response = await request({ url: '/api/mobile/customer/orders' });
      const items = (response && response.items) || [];
      const target = items.find((item) => Number(item.id) === Number(orderId));
      if (!target) {
        return;
      }
      const patch = {};
      if (this.data.currentAddressId == null && target.addressId != null && target.addressId !== '') {
        patch.currentAddressId = Number(target.addressId);
      }
      if (target.deliveryAddress) {
        patch.currentOrderDeliveryAddress = String(target.deliveryAddress);
      }
      if (Object.keys(patch).length > 0) {
        this.setData(patch, () => {
          // 标记变了之后强制重打一次 items 标签，确保 isCurrentOrderAddress 反映最新判定
          if (this.data.items.length > 0) {
            this.refreshCurrentOrderFlag();
          }
        });
      }
    } catch (error) {
      // 拉不到也无妨，已有 currentAddressId / deliveryAddress 文本兜底，不打扰用户
    }
  },

  refreshCurrentOrderFlag() {
    const { currentAddressId, currentOrderDeliveryAddress, items } = this.data;
    let nextItems = items;
    let matches = 0;
    nextItems = items.map((item) => {
      const isCurrentOrderAddress =
        (currentAddressId != null && Number(item.id) === currentAddressId) ||
        (!!currentOrderDeliveryAddress && String(item.addressLine || '').trim() === currentOrderDeliveryAddress.trim());
      if (isCurrentOrderAddress) {
        matches += 1;
      }
      return Object.assign({}, item, { isCurrentOrderAddress });
    });
    const noOtherAddress = this.data.selectOrderId != null
      && nextItems.filter((item) => !item.isCurrentOrderAddress).length === 0;
    if (matches > 0 || noOtherAddress) {
      this.setData({ items: nextItems, noOtherAddress });
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
      const { currentAddressId, currentOrderDeliveryAddress } = this.data;
      const normalizedDeliveryAddress = currentOrderDeliveryAddress ? currentOrderDeliveryAddress.trim() : '';
      items.forEach((item) => {
        const split = splitAddress(item.addressLine);
        item.district = split.district;
        item.detail = split.detail;
        item.doorNumber = item.doorNumber || '';
        const matchesById = currentAddressId != null && Number(item.id) === currentAddressId;
        const matchesByText = !!normalizedDeliveryAddress
          && String(item.addressLine || '').trim() === normalizedDeliveryAddress;
        item.isCurrentOrderAddress = matchesById || matchesByText;
      });
      const noOtherAddress = this.data.selectOrderId != null
        && items.filter((item) => !item.isCurrentOrderAddress).length === 0;
      this.setData({ items, noOtherAddress, addressLimitReached: items.length >= MAX_ADDRESSES_PER_CUSTOMER });
      // 从下单页跳转过来补定位：地址加载完后自动打开对应地址的编辑弹窗
      if (this.pendingEditId) {
        const editId = this.pendingEditId;
        this.pendingEditId = null;
        const target = items.find((item) => Number(item.id) === editId);
        if (target) {
          this.openEditPopup(target);
        }
      }
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  showAddPopup() {
    const { customerProfile, items } = this.data;
    if (items.length >= MAX_ADDRESSES_PER_CUSTOMER) {
      wx.showModal({
        title: '收货地址已达上限',
        content: '每个用户最多保存 ' + MAX_ADDRESSES_PER_CUSTOMER + ' 个地址。请先删除一个不常用的地址，再新增哦~',
        showCancel: false,
        confirmText: '我知道了'
      });
      return;
    }
    this.setData({
      showPopup: true,
      form: buildEmptyForm(customerProfile, items.length === 0)
    });
  },

  hideAddPopup() {
    this.setData({ showPopup: false });
  },

  openEditPopup(address) {
    this.setData({
      showPopup: true,
      form: {
        id: address.id,
        contactName: this.data.customerProfile.name,
        contactPhone: this.data.customerProfile.phone,
        addressLine: address.addressLine || '',
        doorNumber: address.doorNumber || '',
        isDefault: address.isDefault,
        latitude: address.latitude != null ? address.latitude : null,
        longitude: address.longitude != null ? address.longitude : null
      }
    });
  },

  editAddress(e) {
    const id = Number(e.currentTarget.dataset.id);
    const address = this.data.items.find(item => Number(item.id) === id);
    if (address) {
      this.openEditPopup(address);
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

  chooseLocation() {
    const address = String(this.data.form.addressLine || '').trim();
    // 静默复制已填地址，方便在选点页粘贴搜索
    if (address && typeof wx.setClipboardData === 'function') {
      wx.setClipboardData({ data: address });
    }
    // 已有坐标时（编辑地址）把地图初始定位到原坐标，方便微调
    const options = {};
    const currentLat = Number(this.data.form.latitude);
    const currentLng = Number(this.data.form.longitude);
    if (Number.isFinite(currentLat) && Number.isFinite(currentLng) && currentLat !== 0 && currentLng !== 0) {
      options.latitude = currentLat;
      options.longitude = currentLng;
    }
    wx.chooseLocation({
      ...options,
      success: (res) => {
        // 微信 chooseLocation 返回的 name（POI 名）和 address（完整行政区划+道路）
        // 经常冗余：name 含"X区"前缀，address 又有完整"省市区"。
        // 拼成「定位地址（省市区 + POI 名）」存 addressLine，门牌号 doorNumber 由用户另填
        const addressText = res.address || '';
        const nameText = res.name || '';
        let district = '';
        let detail = '';
        if (addressText && nameText) {
          const m = addressText.match(/^(.+?[区县])/);
          district = m ? m[1] : '';
          detail = nameText;
          const districtMatch = district.match(/([\u4e00-\u9fa5]+[区县])$/);
          if (districtMatch && detail.startsWith(districtMatch[1])) {
            detail = detail.slice(districtMatch[1].length).trim();
          }
        } else {
          detail = nameText || addressText;
        }
        const addressLine = [district, detail].filter(Boolean).join(' ');
        this.setData({
          'form.latitude': res.latitude,
          'form.longitude': res.longitude,
          'form.addressLine': addressLine
        });
        wx.showToast({ title: '已标记位置，请补充门牌号', icon: 'success' });
      },
      fail(err) {
        // 用户取消选点（errMsg 含 cancel）不打扰；其他失败（未授权/未声明隐私接口等）给提示
        const msg = err && err.errMsg ? String(err.errMsg) : '';
        if (msg.indexOf('cancel') === -1) {
          wx.showToast({ title: '地图选点失败，请检查位置权限', icon: 'none' });
        }
      }
    });
  },

  async saveAddress() {
    if (this.data.saving) {
      return;
    }
    const { id, addressLine, doorNumber, isDefault, latitude, longitude } = this.data.form;
    const { customerProfile } = this.data;
    const validationError = validateAddressForm(this.data.form, customerProfile);
    if (validationError) {
      wx.showToast({ title: validationError, icon: 'none' });
      return;
    }
    // 软校验：未选点（无坐标）时提醒，避免骑手找不到
    if (latitude == null || longitude == null) {
      const proceed = await new Promise((resolve) => {
        wx.showModal({
          title: '尚未定位',
          content: '该地址还没有地图定位，骑手可能找不到。建议先地图选点。是否仍要保存？',
          confirmText: '仍要保存',
          cancelText: '去选点',
          success: (res) => resolve(!!res.confirm)
        });
      });
      if (!proceed) {
        return;
      }
    }
    const payload = {
      contactName: customerProfile.name.trim(),
      contactPhone: normalizePhone(customerProfile.phone),
      addressLine: addressLine.trim(),
      doorNumber: doorNumber ? doorNumber.trim() : null,
      areaCode: '',
      isDefault,
      latitude: latitude != null ? latitude : null,
      longitude: longitude != null ? longitude : null
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
    const id = Number(e.currentTarget.dataset.id);
    const currentDefault = this.data.items.find(item => item.isDefault);
    if (currentDefault && currentDefault.id === id) {
      return;
    }
    try {
      await request({
        url: `/api/mobile/customer/addresses/${id}/default`,
        method: 'POST'
      });
      wx.showToast({ title: '已设为默认地址', icon: 'success' });
      this.loadAddresses();
    } catch (error) {
      wx.showToast({ title: error.message || '设置失败', icon: 'none' });
    }
  },

  async selectAddressForOrder(e) {
    const { id } = e.currentTarget.dataset;
    const { selectOrderId, currentAddressId } = this.data;
    if (!selectOrderId) {
      return;
    }
    if (currentAddressId != null && Number(id) === currentAddressId) {
      wx.showToast({ title: '已经是当前配送地址', icon: 'none' });
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
