const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const mapServicePath = path.join(__dirname, '..', 'services', 'map.service.js');

function loadFreshModules() {
  delete require.cache[require.resolve(mapServicePath)];
  return require(mapServicePath);
}

function mockWx() {
  const state = {
    openedLocations: [],
    clipboardData: [],
    toasts: []
  };
  global.wx = {
    showToast(options) {
      state.toasts.push(options);
    },
    setClipboardData(options) {
      state.clipboardData.push(options.data);
    },
    openLocation(options) {
      state.openedLocations.push(options);
    }
  };
  return state;
}

test('navigate with order coordinates opens map directly at that location', () => {
  const state = mockWx();
  const mapService = loadFreshModules();

  mapService.navigate({
    customerName: '王五',
    deliveryAddress: '湖南省长沙市天心区书院路 5 号',
    latitude: 28.19,
    longitude: 112.98
  });

  assert.equal(state.openedLocations.length, 1);
  assert.equal(state.openedLocations[0].latitude, 28.19);
  assert.equal(state.openedLocations[0].longitude, 112.98);
  assert.equal(state.openedLocations[0].name, '王五');
  assert.equal(state.openedLocations[0].address, '湖南省长沙市天心区书院路 5 号');
  assert.equal(state.clipboardData.length, 0);
});

test('navigate without coordinates copies address and shows fallback toast', () => {
  const state = mockWx();
  const mapService = loadFreshModules();

  mapService.navigate({
    customerName: '王五',
    deliveryAddress: '湖南省长沙市天心区书院路 5 号'
  });

  assert.equal(state.openedLocations.length, 0);
  assert.deepEqual(state.clipboardData, ['湖南省长沙市天心区书院路 5 号']);
  assert.ok(state.toasts.some((t) => String(t.title).includes('该地址暂无定位')));
});

test('navigate with no address shows toast and does nothing', () => {
  const state = mockWx();
  const mapService = loadFreshModules();

  mapService.navigate(null);

  assert.equal(state.openedLocations.length, 0);
  assert.equal(state.clipboardData.length, 0);
  assert.ok(state.toasts.some((t) => String(t.title).includes('暂无地址信息')));
});
