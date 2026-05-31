// 云开发测试页面
Page({
  data: {
    logs: []
  },

  onLoad() {
    this.addLog('页面加载');
    this.testCloudInit();
  },

  addLog(msg) {
    const logs = this.data.logs;
    logs.push(`[${new Date().toLocaleTimeString()}] ${msg}`);
    this.setData({ logs });
    console.log(msg);
  },

  testCloudInit() {
    this.addLog('--- 测试云开发初始化 ---');
    
    if (typeof wx.cloud === 'undefined') {
      this.addLog('❌ wx.cloud 未定义');
      return;
    }
    
    this.addLog('✅ wx.cloud 已定义');
    
    const app = getApp();
    this.addLog(`云环境ID: ${app.globalData.cloudEnvId}`);
  },

  async testUpload() {
    this.addLog('--- 测试上传图片 ---');
    
    try {
      // 选择图片
      const res = await wx.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera']
      });
      
      const filePath = res.tempFilePaths[0];
      this.addLog(`选择图片: ${filePath}`);
      
      // 上传到云存储
      const cloudPath = `test/${Date.now()}.jpg`;
      this.addLog(`开始上传到: ${cloudPath}`);
      
      const uploadRes = await wx.cloud.uploadFile({
        cloudPath,
        filePath
      });
      
      this.addLog(`✅ 上传成功`);
      this.addLog(`fileID: ${uploadRes.fileID}`);
      
      // 获取临时链接
      this.addLog('获取临时链接...');
      const tempRes = await wx.cloud.getTempFileURL({
        fileList: [uploadRes.fileID]
      });
      
      if (tempRes.fileList && tempRes.fileList.length > 0) {
        const tempUrl = tempRes.fileList[0].tempFileURL;
        this.addLog(`✅ 临时链接: ${tempUrl}`);
        
        if (tempUrl.startsWith('https://')) {
          this.addLog('✅ 链接格式正确（HTTPS）');
        } else {
          this.addLog(`❌ 链接格式错误: ${tempUrl}`);
        }
      }
      
    } catch (error) {
      this.addLog(`❌ 错误: ${error.message || error.errMsg}`);
      console.error(error);
    }
  },

  clearLogs() {
    this.setData({ logs: [] });
  }
});
