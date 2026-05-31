const test = require('node:test');
const assert = require('node:assert/strict');
const { resolveRiderEntryPage, resolveRiderViewState, canUseWorkbench } = require('../utils/rider-auth-state');

test('keeps unauthenticated rider inside tabs instead of forcing login page', () => {
  assert.equal(resolveRiderEntryPage({
    authReady: true,
    registered: false,
    riderStatus: 'UNAUTHORIZED',
    workbenchEnabled: false
  }), null);
});

test('keeps phone-auth-pending rider inside tabs instead of forcing login page', () => {
  assert.equal(resolveRiderEntryPage({
    authReady: true,
    registered: false,
    needPhoneAuth: true,
    riderStatus: 'UNAUTHORIZED',
    workbenchEnabled: false
  }), null);
});

test('routes unassigned rider to pending page', () => {
  assert.equal(resolveRiderEntryPage({
    authReady: true,
    registered: true,
    riderStatus: 'UNASSIGNED',
    workbenchEnabled: false
  }), '/pages/pending/index');
});

test('routes disabled rider to blocked page', () => {
  assert.equal(resolveRiderEntryPage({
    authReady: true,
    registered: true,
    riderStatus: 'DISABLED',
    workbenchEnabled: false
  }), '/pages/blocked/index');
});

test('allows active rider into workbench', () => {
  assert.equal(resolveRiderEntryPage({
    authReady: true,
    registered: true,
    riderStatus: 'ACTIVE',
    workbenchEnabled: true
  }), null);
  assert.equal(canUseWorkbench({
    authReady: true,
    registered: true,
    riderStatus: 'ACTIVE',
    workbenchEnabled: true
  }), true);
});

test('marks backend-missing rider as not-found and keeps rider on profile flow', () => {
  const state = {
    authReady: true,
    registered: false,
    needPhoneAuth: false,
    riderStatus: 'NOT_FOUND',
    workbenchEnabled: false
  };
  assert.equal(resolveRiderViewState(state), 'not_found');
  assert.equal(resolveRiderEntryPage(state), null);
});
