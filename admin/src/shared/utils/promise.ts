export function isPromiseFulfilledResult<T>(
  result: PromiseSettledResult<T>
): result is PromiseFulfilledResult<T> {
  return result.status === "fulfilled";
}

export function isPromiseRejectedResult<T>(
  result: PromiseSettledResult<T>
): result is PromiseRejectedResult {
  return result.status === "rejected";
}

export function getRejectedReason<T>(result: PromiseSettledResult<T>): unknown {
  return isPromiseRejectedResult(result) ? result.reason : undefined;
}
