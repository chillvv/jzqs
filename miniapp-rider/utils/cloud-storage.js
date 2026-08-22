/**
 * 微信云开发存储工具
 * 用于上传和管理图片到微信云存储
 */

/**
 * 上传图片到云存储
 * @param {string} filePath - 本地临时文件路径
 * @param {string} cloudPath - 云存储路径（如: rider-receipts/xxx.jpg）
 * @returns {Promise<string>} 云存储文件ID
 */
async function uploadToCloud(filePath, cloudPath) {
  return new Promise((resolve, reject) => {
    wx.cloud.uploadFile({
      cloudPath,
      filePath,
      success: res => {
        resolve(res.fileID);
      },
      fail: err => {
        reject(new Error(err.errMsg || '上传失败'));
      }
    });
  });
}

/**
 * 删除云存储文件
 * @param {string} fileID - 云存储文件ID
 * @returns {Promise<void>}
 */
async function deleteFromCloud(fileID) {
  return new Promise((resolve, reject) => {
    wx.cloud.deleteFile({
      fileList: [fileID],
      success: res => {
        resolve();
      },
      fail: err => {
        reject(new Error(err.errMsg || '删除失败'));
      }
    });
  });
}

/**
 * 获取云存储临时链接（公开读权限下为永久 HTTPS 链接）
 * @param {string} fileID - 云存储文件ID
 * @returns {Promise<string>} HTTPS 访问链接
 */
async function getTempFileURL(fileID) {
  return new Promise((resolve, reject) => {
    wx.cloud.getTempFileURL({
      fileList: [fileID],
      success: res => {
        if (res.fileList && res.fileList.length > 0) {
          const fileInfo = res.fileList[0];
          
          if (fileInfo.status === 0 && fileInfo.tempFileURL) {
            resolve(fileInfo.tempFileURL);
          } else {
            reject(new Error(fileInfo.errMsg || '获取临时链接失败'));
          }
        } else {
          reject(new Error('获取临时链接失败：响应数据为空'));
        }
      },
      fail: err => {
        reject(new Error(err.errMsg || '获取临时链接失败'));
      }
    });
  });
}

/**
 * 清洗路径片段：去掉路径分隔符、控制字符与 ".."，防止路径穿越/越权目录
 */
function sanitizeSegment(value, fallback) {
  const cleaned = String(value == null ? '' : value)
    .replace(/[\/\\\x00-\x1f]/g, '')
    .replace(/\.\./g, '')
    .replace(/^\W+|\W+$/g, '')
    .slice(0, 40);
  return cleaned || fallback;
}

/**
 * 生成云存储路径
 * @param {string} riderName - 骑手姓名
 * @param {string} extension - 文件扩展名（如: jpg）
 * @returns {string} 云存储路径
 */
function generateCloudPath(riderName, extension = 'jpg') {
  const timestamp = Date.now();
  const random = Math.floor(Math.random() * 10000);
  const safeName = sanitizeSegment(riderName, 'rider');
  const safeExt = String(extension || 'jpg').replace(/[^a-zA-Z0-9]/g, '').slice(0, 10) || 'jpg';
  return `rider-receipts/${safeName}-${timestamp}-${random}.${safeExt}`;
}

module.exports = {
  uploadToCloud,
  deleteFromCloud,
  getTempFileURL,
  generateCloudPath
};
