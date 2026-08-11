import { ApiErrorResponse } from '../models/api-error.model';

export function apiErrorMessage(
  error: unknown,
  fallback: string,
  forbiddenMessage = 'Tu rol no permite realizar esta operación.',
): string {
  if (!isApiError(error)) {
    return fallback;
  }
  if (error.status === 0) {
    return 'No se pudo conectar con el backend.';
  }
  if (error.status === 403) {
    return forbiddenMessage;
  }
  return error.message || fallback;
}

export function apiFieldErrors(error: unknown): Readonly<Record<string, string>> {
  return isApiError(error) ? (error.fieldErrors ?? {}) : {};
}

function isApiError(error: unknown): error is ApiErrorResponse {
  return (
    typeof error === 'object' &&
    error !== null &&
    'status' in error &&
    typeof error.status === 'number'
  );
}
