const assert = require('node:assert/strict');
const path = require('node:path');

const imageUtilPath = path.join(__dirname, '..', 'miniapp-rider', 'utils', 'image.js');
const imageUtil = require(imageUtilPath);

async function testFallsBackToChooseImageWhenChooseMediaUnavailable() {
  let chooseImageCalled = false;

  global.wx = {
    chooseMedia() {
      return Promise.reject({
        errMsg: 'chooseMedia:fail not supported'
      });
    },
    chooseImage(options) {
      chooseImageCalled = true;
      assert.deepEqual(options.sourceType, ['album', 'camera']);
      assert.equal(options.count, 1);
      return Promise.resolve({
        tempFilePaths: ['mock-image.jpg']
      });
    }
  };

  const paths = await imageUtil.chooseImage({
    count: 1,
    sourceType: ['camera', 'album']
  });

  assert.equal(chooseImageCalled, true, 'chooseMedia 不可用时应回退到 chooseImage');
  assert.deepEqual(paths, ['mock-image.jpg']);
}

async function testReturnsEmptyArrayWhenUserCancels() {
  global.wx = {
    chooseMedia() {
      return Promise.reject({
        errMsg: 'chooseMedia:fail cancel'
      });
    }
  };

  const paths = await imageUtil.chooseImage();
  assert.deepEqual(paths, []);
}

async function testResolvesWhenChooseMediaUsesCallbacks() {
  global.wx = {
    chooseMedia(options) {
      setTimeout(() => {
        options.success({
          tempFiles: [{ tempFilePath: 'callback-media.jpg' }]
        });
      }, 0);
    }
  };

  const paths = await imageUtil.chooseImage({
    count: 1,
    sourceType: ['camera']
  });

  assert.deepEqual(paths, ['callback-media.jpg']);
}

async function testFallsBackWhenChooseImageUsesCallbacks() {
  let chooseImageCalled = false;

  global.wx = {
    chooseMedia(options) {
      setTimeout(() => {
        options.fail({
          errMsg: 'chooseMedia:fail not supported'
        });
      }, 0);
    },
    chooseImage(options) {
      chooseImageCalled = true;
      setTimeout(() => {
        options.success({
          tempFilePaths: ['callback-fallback.jpg']
        });
      }, 0);
    }
  };

  const paths = await imageUtil.chooseImage({
    count: 1,
    sourceType: ['album']
  });

  assert.equal(chooseImageCalled, true);
  assert.deepEqual(paths, ['callback-fallback.jpg']);
}

async function testPrefersAlbumBeforeCameraWhenBothSourcesProvided() {
  global.wx = {
    chooseMedia(options) {
      assert.deepEqual(
        options.sourceType,
        ['album', 'camera'],
        '同时允许相册和拍照时应优先展示相册'
      );
      return Promise.resolve({
        tempFiles: [{ tempFilePath: 'ordered-source.jpg' }]
      });
    }
  };

  const paths = await imageUtil.chooseImage({
    count: 1,
    sourceType: ['camera', 'album']
  });

  assert.deepEqual(paths, ['ordered-source.jpg']);
}

async function testThrowsHelpfulMessageWhenPrivacyAgreementMissing() {
  global.wx = {
    chooseMedia() {
      return Promise.reject({
        errMsg: 'chooseMedia:fail api scope is not declared in the privacy agreement'
      });
    },
    chooseImage() {
      return Promise.reject({
        errMsg: 'chooseImage:fail api scope is not declared in the privacy agreement'
      });
    }
  };

  await assert.rejects(
    () => imageUtil.chooseImage({
      count: 1,
      sourceType: ['album', 'camera']
    }),
    /隐私保护指引|隐私声明/
  );
}

async function main() {
  await testFallsBackToChooseImageWhenChooseMediaUnavailable();
  await testReturnsEmptyArrayWhenUserCancels();
  await testResolvesWhenChooseMediaUsesCallbacks();
  await testFallsBackWhenChooseImageUsesCallbacks();
  await testPrefersAlbumBeforeCameraWhenBothSourcesProvided();
  await testThrowsHelpfulMessageWhenPrivacyAgreementMissing();
  console.log('PASS: 骑手小程序图片选择兼容逻辑正常');
}

main().finally(() => {
  delete global.wx;
});
