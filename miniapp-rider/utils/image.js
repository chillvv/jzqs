/**
 * 图片处理工具
 */

function isCancelError(error) {
  return !!(error && /cancel/i.test(error.errMsg || ''));
}

function isPrivacyAgreementError(error) {
  const message = ((error && error.errMsg) || error && error.message || '').toLowerCase();
  return message.includes('privacy agreement') || message.includes('api scope is not declared');
}

function isPrivacyDeniedError(error) {
  const message = ((error && error.errMsg) || error && error.message || '').toLowerCase();
  return message.includes('deny') || message.includes('no permission') || message.includes('jsapi has no permission');
}

function getChooseImageErrorMessage(error) {
  if (isPrivacyAgreementError(error)) {
    return '请先在微信公众平台完善用户隐私保护指引中的照片/相册信息声明';
  }

  if (isPrivacyDeniedError(error)) {
    return '请先完成微信隐私授权，再选择图片';
  }

  return '选择图片失败';
}

function canUseChooseMediaFallback(error) {
  const message = (error && error.errMsg) || '';
  return /not supported|not support|invalid|fail/i.test(message) && !isCancelError(error);
}

function normalizeSourceType(sourceType) {
  if (!Array.isArray(sourceType)) {
    return ['album', 'camera'];
  }

  const uniqueSourceType = sourceType.filter((item, index) => (
    (item === 'album' || item === 'camera') && sourceType.indexOf(item) === index
  ));

  if (
    uniqueSourceType.length === 2 &&
    uniqueSourceType.includes('album') &&
    uniqueSourceType.includes('camera')
  ) {
    return ['album', 'camera'];
  }

  return uniqueSourceType.length > 0 ? uniqueSourceType : ['album', 'camera'];
}

function callWxApi(apiName, options) {
  return new Promise((resolve, reject) => {
    const api = wx && wx[apiName];
    if (typeof api !== 'function') {
      reject(new Error(`${apiName} 不可用`));
      return;
    }

    let settled = false;
    const safeResolve = (value) => {
      if (!settled) {
        settled = true;
        resolve(value);
      }
    };
    const safeReject = (error) => {
      if (!settled) {
        settled = true;
        reject(error);
      }
    };

    try {
      const result = api({
        ...options,
        success: safeResolve,
        fail: safeReject
      });

      if (result && typeof result.then === 'function') {
        result.then(safeResolve).catch(safeReject);
      }
    } catch (error) {
      safeReject(error);
    }
  });
}

async function chooseImageWithChooseMedia({ count, sourceType }) {
  const result = await callWxApi('chooseMedia', {
    count,
    mediaType: ['image'],
    sourceType
  });

  if (Array.isArray(result.tempFiles) && result.tempFiles.length > 0) {
    return result.tempFiles
      .map(file => file && file.tempFilePath)
      .filter(Boolean);
  }

  if (Array.isArray(result.tempFilePaths) && result.tempFilePaths.length > 0) {
    return result.tempFilePaths.filter(Boolean);
  }

  return [];
}

async function chooseImageWithChooseImage({ count, sourceType }) {
  const result = await callWxApi('chooseImage', {
    count,
    sizeType: ['compressed'],
    sourceType
  });

  if (Array.isArray(result.tempFilePaths) && result.tempFilePaths.length > 0) {
    return result.tempFilePaths.filter(Boolean);
  }

  return [];
}

/**
 * 选择图片（拍照或相册）
 * @param {Object} options - 选项
 * @param {number} options.count - 图片数量，默认1
 * @param {Array<string>} options.sourceType - 来源类型，默认['album', 'camera']
 * @returns {Promise<Array<string>>} 图片路径数组
 */
async function chooseImage(options = {}) {
  const {
    count = 1,
    sourceType = ['album', 'camera']
  } = options;
  const normalizedSourceType = normalizeSourceType(sourceType);

  if (typeof wx.requirePrivacyAuthorize === 'function') {
    try {
      await new Promise((resolve, reject) => {
        wx.requirePrivacyAuthorize({
          success: resolve,
          fail: reject
        });
      });
    } catch (privacyError) {
      throw new Error(getChooseImageErrorMessage(privacyError));
    }
  }

  try {
    return await chooseImageWithChooseMedia({ count, sourceType: normalizedSourceType });
  } catch (error) {
    // 用户取消
    if (isCancelError(error)) {
      return [];
    }

    if (typeof wx.chooseImage === 'function' && canUseChooseMediaFallback(error)) {
      try {
        return await chooseImageWithChooseImage({ count, sourceType: normalizedSourceType });
      } catch (fallbackError) {
        if (isCancelError(fallbackError)) {
          return [];
        }
        throw new Error(getChooseImageErrorMessage(fallbackError));
      }
    }

    throw new Error(getChooseImageErrorMessage(error));
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
    const result = await callWxApi('compressImage', {
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
