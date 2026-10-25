import { ApiErrorResponse } from '../../shared/models/api-error.model';

export function productErrorMessage(error: unknown, fallback: string): string {
  if (isApiError(error)) {
    if (error.status === 0) {
      return 'No se pudo conectar con el backend.';
    }
    if (error.status === 403) {
      return 'Tu rol no permite modificar productos.';
    }
    if (
      error.message ===
      'A product with batches cannot change its unit of measurement. Create a new product.'
    ) {
      return 'El producto ya tiene lotes y debe conservar su unidad de medida. Crea un producto nuevo para otra medida.';
    }
    return error.message || fallback;
  }
  return fallback;
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
