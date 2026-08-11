import { ApiErrorResponse } from '../../shared/models/api-error.model';

export function authErrorMessage(error: unknown, fallback: string): string {
  if (!isApiError(error)) {
    return fallback;
  }

  if (error.status === 0) {
    return 'No se pudo conectar con el backend. Comprueba que esté iniciado.';
  }
  if (error.status === 401) {
    return 'El usuario o la contraseña no son correctos.';
  }
  return error.message || fallback;
}

function isApiError(error: unknown): error is ApiErrorResponse {
  return (
    typeof error === 'object' &&
    error !== null &&
    'status' in error &&
    typeof error.status === 'number' &&
    'message' in error &&
    typeof error.message === 'string'
  );
}
