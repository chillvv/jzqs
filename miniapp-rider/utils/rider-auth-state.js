function canUseWorkbench(state) {
  return Boolean(state && state.authReady && state.registered && state.workbenchEnabled && state.riderStatus === 'ACTIVE');
}

function resolveRiderViewState(state) {
  if (!state || !state.authReady) {
    return 'checking';
  }
  if (state.riderStatus === 'NOT_FOUND') {
    return 'not_found';
  }
  if (!state.registered || state.needPhoneAuth || state.riderStatus === 'UNAUTHORIZED') {
    return 'guest';
  }
  if (state.riderStatus === 'DISABLED') {
    return 'blocked';
  }
  if (!state.workbenchEnabled || state.riderStatus === 'UNASSIGNED') {
    return 'pending';
  }
  return 'active';
}

function resolveRiderEntryPage(state) {
  const viewState = resolveRiderViewState(state);
  if (viewState === 'blocked') {
    return '/pages/blocked/index';
  }
  if (viewState === 'pending') {
    return '/pages/pending/index';
  }
  return null;
}

module.exports = {
  canUseWorkbench,
  resolveRiderViewState,
  resolveRiderEntryPage
};
