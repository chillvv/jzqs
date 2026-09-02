/**
 * 地址拆分工具（与顾客端 miniapp/pages/addresses/index.js 的 splitAddress 对齐）。
 * 把完整地址拆成「省市区（区划）」和「详细地址」，供骑手端分两段展示：
 * 省市区小灰字 + 详细地址大字突出（参考京东）。
 * 同时兼容历史数据里逗号/顿号分隔的省市区（"湖北省,武汉市,洪山区"）。
 */
function splitAddress(addressLine) {
  const text = String(addressLine || '').trim();
  if (!text) {
    return { district: '', detail: '' };
  }
  // 逗号/顿号统一为空格，便于拆分；也满足「地址不要用逗号隔开」的要求
  const cleaned = text.replace(/[,，、]/g, ' ');
  // 优先按空格拆分（"省市区 详细地址"），无空格再按区划前缀拆分（"省市区详细地址"）
  let m = cleaned.match(/^(.+?[区县])\s+(.+)$/) || cleaned.match(/^(.+?[区县])(.+)$/);
  if (m && m[1] && m[2]) {
    // 省市区内部空格压缩掉（"湖北省 武汉市 洪山区" → "湖北省武汉市洪山区"）
    const district = m[1].replace(/\s+/g, '').trim();
    const detail = m[2].replace(/\s+/g, ' ').trim();
    return { district, detail };
  }
  return { district: '', detail: cleaned.replace(/\s+/g, ' ').trim() };
}

module.exports = { splitAddress };
