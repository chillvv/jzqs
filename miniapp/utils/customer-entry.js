function buildHomeValueProps() {
  return ['明日预订', '营养搭配', '商家自配', '会员餐次'];
}

function buildProfileGuestCard() {
  return {
    title: '确认你的会员手机号',
    description: '确认后即可同步餐次、地址、预订记录和配送服务。',
    primaryAction: '微信一键确认',
    secondaryAction: '手动输入手机号'
  };
}

function buildInlineAuthSheet(source) {
  if (source === 'order') {
    return {
      title: '先确认会员手机号',
      description: '确认后继续当前预订，不会丢失你已选择的餐食。',
      primaryAction: '微信一键确认',
      secondaryAction: '手动输入手机号'
    };
  }
  return buildProfileGuestCard();
}

module.exports = {
  buildHomeValueProps,
  buildProfileGuestCard,
  buildInlineAuthSheet
};
