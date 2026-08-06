const DEFAULT_SHARE_TITLE = '简知轻食 · 骑手接单工作台';
const DEFAULT_SHARE_PATH = '/pages/queue/index';
const DEFAULT_SHARE_IMAGE = '/assets/green-intro.jpg';

function shareAppMessage() {
  return {
    title: DEFAULT_SHARE_TITLE,
    path: DEFAULT_SHARE_PATH,
    imageUrl: DEFAULT_SHARE_IMAGE
  };
}

function shareTimeline() {
  return {
    title: DEFAULT_SHARE_TITLE,
    query: '',
    imageUrl: DEFAULT_SHARE_IMAGE
  };
}

module.exports = {
  shareAppMessage,
  shareTimeline,
  DEFAULT_SHARE_TITLE,
  DEFAULT_SHARE_PATH,
  DEFAULT_SHARE_IMAGE
};
