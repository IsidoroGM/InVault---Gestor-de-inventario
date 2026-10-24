import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';

import { authErrorMessage } from './auth-error-message';
import { normalizeApiError } from './normalize-api-error';

describe('login rate-limit errors', () => {
  it('keeps the server retry delay when normalizing a 429 response', () => {
    const error = normalizeApiError(
      new HttpErrorResponse({
        status: 429,
        error: { message: 'Too many login attempts.', retryAfterSeconds: 42 },
      }),
      '/api/auth/login',
    );
    expect(error.retryAfterSeconds).toBe(42);
    expect(authErrorMessage(error, 'fallback')).toBe(
      'Se han realizado demasiados intentos de acceso. Espera antes de volver a intentarlo.',
    );
  });

  it('uses the Retry-After header when an intermediary omits the JSON field', () => {
    const error = normalizeApiError(
      new HttpErrorResponse({ status: 429, headers: new HttpHeaders({ 'Retry-After': '19' }) }),
      '/api/auth/login',
    );
    expect(error.retryAfterSeconds).toBe(19);
  });

  it('ignores invalid or unbounded retry delays', () => {
    for (const value of [0, -1, Infinity, NaN, 'unexpected', 3601]) {
      const error = normalizeApiError(
        new HttpErrorResponse({ status: 429, error: { retryAfterSeconds: value } }),
        '/api/auth/login',
      );
      expect(error.retryAfterSeconds).toBeUndefined();
    }
  });
});
