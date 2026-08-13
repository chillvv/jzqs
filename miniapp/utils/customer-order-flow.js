function buildWalletHint({ walletDelta }) {
  if (walletDelta < 0) {
    return `本次预订已扣减 ${Math.abs(walletDelta)} 餐`;
  }
  return `本次售后已退回 ${walletDelta} 餐`;
}

function buildAftersaleNotice(type) {
  if (type === 'REFUND') {
    return '退款申请提交后，商家会核对订单与配送情况，并在处理完成后同步到订单状态和餐次流水';
  }
  return '售后申请提交后，商家会尽快处理，并同步结果到订单页';
}

module.exports = {
  buildWalletHint,
  buildAftersaleNotice
};
