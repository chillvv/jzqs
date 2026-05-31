const taskService = require('../../services/task.service');

Page({
  data: {
    riderName: '',
    imagePath: '',
    logs: []
  },

  onLoad() {
    const app = getApp();
    this.setData({
      riderName: app.getActiveRiderName() || '测试骑手'
    });
    this.addLog('页面加载完成');
    this.checkAuth();
  },

  addLog(message) {
    const logs = this.data.logs;
    const time = new Date().toLocaleTimeString();
    logs.unshift(`[${time}] ${message}`);
    this.setData({ logs: logs.slice(0, 20) });
    console.log(message);
  },

  checkAuth() {
    const app = getApp();
    const token = wx.getStorageSync('auth_token');
    this.addLog(`认证状态: ${app.globalData.riderAuthReady ? '已就绪' : '未就绪'}`);
    this.addLog(`Token: ${token ? '有' : '无'}`);
    this.addLog(`骑手: ${app.globalData.riderName || '未登录'}`);
  },

  onRiderNameInput(e) {
    this.setData({ riderName: e.detail.value });
  },

  // 选择图片
  async chooseImage() {
    try {
      const res = await wx.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera']
      });
      
      const imagePath = res.tempFilePaths[0];
      this.setData({ imagePath });
      this.addLog(`图片已选择: ${imagePath}`);
    } catch (error) {
      this.addLog(`选择图片失败: ${error.message}`);
    }
  },

  // 测试上传
  async testUpload() {
    const { riderName, imagePath } = this.data;
    
    if (!imagePath) {
      this.addLog('请先选择图片');
      wx.showToast({ title: '请先选择图片', icon: 'none' });
      return;
    }
    
    if (!riderName) {
      this.addLog('请输入骑手姓名');
      wx.showToast({ title: '请输入骑手姓名', icon: 'none' });
      return;
    }
    
    this.addLog(`开始上传: ${riderName}`);
    
    try {
      const result = await taskService.uploadReceipt(riderName, imagePath);
      this.addLog(`上传成功: ${JSON.stringify(result)}`);
      wx.showToast({ title: '上传成功', icon: 'success' });
    } catch (error) {
      this.addLog(`上传失败: ${error.message}`);
      wx.showToast({ title: error.message || '上传失败', icon: 'none' });
    }
  },

  // 测试直接调用 wx.uploadFile
  async testDirectUpload() {
    const { riderName, imagePath } = this.data;
    
    if (!imagePath) {
      this.addLog('请先选择图片');
      return;
    }
    
    const app = getApp();
    const token = wx.getStorageSync('auth_token');
    
    this.addLog(`直接上传测试`);
    this.addLog(`URL: ${app.globalData.apiBaseUrl}/api/mobile/rider/uploads/receipt`);
    this.addLog(`Token: ${token ? '有' : '无'}`);
    this.addLog(`RiderName: ${riderName}`);
    
    try {
      const res = await new Promise((resolve, reject) => {
        wx.uploadFile({
          url: `${app.globalData.apiBaseUrl}/api/mobile/rider/uploads/receipt`,
          filePath: imagePath,
          name: 'file',
          formData: {
            riderName: riderName,
            token: token
          },
          header: {
            Authorization: `Bearer ${token}`
          },
          success: resolve,
          fail: reject
        });
      });
      
      this.addLog(`响应状态: ${res.statusCode}`);
      this.addLog(`响应数据: ${res.data}`);
      
      const body = JSON.parse(res.data);
      if (body.code === 'OK') {
        this.addLog(`上传成功: ${JSON.stringify(body.data)}`);
        wx.showToast({ title: '上传成功', icon: 'success' });
      } else {
        this.addLog(`上传失败: ${body.message}`);
        wx.showToast({ title: body.message, icon: 'none' });
      }
    } catch (error) {
      this.addLog(`上传失败: ${error.message || JSON.stringify(error)}`);
      wx.showToast({ title: '上传失败', icon: 'none' });
    }
  },

  clearLogs() {
    this.setData({ logs: [] });
  }
});
