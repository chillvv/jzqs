/**
 * 图片处理工具
 */

/**
 * 选择图片（拍照或相册）
 * @param {Object} options - 选项
 * @param {number} options.count - 图片数量，默认1
 * @param {Array<string>} options.sourceType - 来源类型，默认['camera', 'album']
 * @returns {Promise<Array<string>>} 图片路径数组
 */
async function chooseImage(options = {}) {
  const {
    count = 1,
    sourceType = ['camera', 'album']
  } = options;

  try {
    const result = await wx.chooseMedia({
      count,
      mediaType: ['image'],
      sourceType
    });

    return result.tempFiles.map(file => file.tempFilePath);
  } catch (error) {
    // 用户取消
    if (error && /cancel/i.test(error.errMsg || '')) {
      return [];
    }
    throw new Error('选择图片失败');
  }
}

/**
 * 压缩图片
 * @param {string} src - 图片路径
 * @param {number} quality - 压缩质量（0-100），默认80
 * @returns {Promise<string>} 压缩后的图片路径
 */
async function compressImage(src, quality = 80) {
  try {
    const result = await wx.compressImage({
      src,
      quality
    });
    return result.tempFilePath;
  } catch (error) {
    console.warn('[图片压缩] 失败，使用原图', error);
    return src;
  }
}

/**
 * 预览图片
 * @param {Array<string>} urls - 图片URL数组
 * @param {number} current - 当前显示图片索引，默认0
 */
function previewImage(urls, current = 0) {
  if (!urls || urls.length === 0) {
    return;
  }

  wx.previewImage({
    urls: Array.isArray(urls) ? urls : [urls],
    current: Array.isArray(urls) ? urls[current] : urls
  });
}

/**
 * 选择并压缩图片
 * @param {Object} options - 选项
 * @returns {Promise<Array<string>>} 压缩后的图片路径数组
 */
async function chooseAndCompressImage(options = {}) {
  const paths = await chooseImage(options);
  
  if (paths.length === 0) {
    return [];
  }

  // 并行压缩所有图片
  const compressPromises = paths.map(path => compressImage(path, options.quality));
  return await Promise.all(compressPromises);
}

/**
 * 获取图片信息
 * @param {string} src - 图片路径
 * @returns {Promise<Object>} 图片信息
 */
async function getImageInfo(src) {
  return new Promise((resolve, reject) => {
    wx.getImageInfo({
      src,
      success: resolve,
      fail: reject
    });
  });
}

module.exports = {
  chooseImage,
  compressImage,
  previewImage,
  chooseAndCompressImage,
  getImageInfo
};
