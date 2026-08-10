/**
 * 小程序运行环境判断。
 * envVersion 取值（微信官方）：
 *   - 'develop' 开发版（开发者工具）
 *   - 'trial'   体验版
 *   - 'release' 正式版（线上发布）
 * 取不到时按最严格处理：当作正式版，避免测试入口泄露到线上。
 */
function getEnvVersion() {
  try {
    return wx.getAccountInfoSync().miniProgram.envVersion;
  } catch (e) {
    return 'release';
  }
}

function isReleaseEnv() {
  return getEnvVersion() === 'release';
}

/** 是否测试环境（开发版 / 体验版），用于决定是否展示内部测试入口 */
function isTestEnv() {
  return !isReleaseEnv();
}

module.exports = {
  getEnvVersion,
  isReleaseEnv,
  isTestEnv
};
