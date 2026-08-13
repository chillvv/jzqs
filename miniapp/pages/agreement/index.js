const { AGREEMENTS } = require('../../utils/agreements');

const FALLBACK = {
  title: '协议',
  updatedAt: '',
  intro: '',
  sections: []
};

Page({
  data: {
    title: FALLBACK.title,
    updatedAt: '',
    intro: '',
    sections: []
  },

  onLoad(options = {}) {
    const type = String(options.type || 'user');
    const doc = AGREEMENTS[type] || AGREEMENTS.user || FALLBACK;
    this.setData({
      title: doc.title || FALLBACK.title,
      updatedAt: doc.updatedAt || '',
      intro: doc.intro || '',
      sections: doc.sections || []
    });
    if (doc.title) {
      wx.setNavigationBarTitle({ title: doc.title });
    }
  },

  goBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack();
    } else {
      wx.switchTab({ url: '/pages/home/index' });
    }
  }
});
