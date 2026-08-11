import { ApiErrorResponse } from '../../shared/models/api-error.model';

export function catalogErrorMessage(error: unknown, fallback: string): string {
  if (!isApiError(error)) {
    return fallback;
  }
  if (error.status === 0) {
    return 'No se pudo conectar con el backend.';
  }
  if (error.status === 403) {
    return 'Tu rol no permite modificar catálogos.';
  }
  return error.message || fallback;
}

export function catalogFieldErrors(error: unknown): Readonly<Record<string, string>> {
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
