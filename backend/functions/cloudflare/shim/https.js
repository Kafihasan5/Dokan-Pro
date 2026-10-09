/** firebase-functions/v2/https HttpsError, with the same code → HTTP status mapping. */
const CODES = {
  ok: [200, 'OK'],
  cancelled: [499, 'CANCELLED'],
  unknown: [500, 'UNKNOWN'],
  'invalid-argument': [400, 'INVALID_ARGUMENT'],
  'deadline-exceeded': [504, 'DEADLINE_EXCEEDED'],
  'not-found': [404, 'NOT_FOUND'],
  'already-exists': [409, 'ALREADY_EXISTS'],
  'permission-denied': [403, 'PERMISSION_DENIED'],
  'resource-exhausted': [429, 'RESOURCE_EXHAUSTED'],
  'failed-precondition': [400, 'FAILED_PRECONDITION'],
  aborted: [409, 'ABORTED'],
  'out-of-range': [400, 'OUT_OF_RANGE'],
  unimplemented: [501, 'UNIMPLEMENTED'],
  internal: [500, 'INTERNAL'],
  unavailable: [503, 'UNAVAILABLE'],
  'data-loss': [500, 'DATA_LOSS'],
  unauthenticated: [401, 'UNAUTHENTICATED'],
};

export class HttpsError extends Error {
  constructor(code, message, details) {
    super(message);
    this.code = CODES[code] ? code : 'internal';
    this.details = details;
    const [status, canonicalName] = CODES[this.code];
    this.httpErrorCode = { status, canonicalName };
  }
}
