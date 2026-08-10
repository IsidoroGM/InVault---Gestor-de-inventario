import { HttpErrorResponse } from '@angular/common/http';

import { ApiErrorResponse, FieldErrors } from '../../shared/models/api-error.model';

interface ErrorPayload {
  readonly status?: unknown;
  readonly error?: unknown;
  readonly message?: unknown;
  readonly path?: unknown;
  readonly timestamp?: unknown;
  readonly fieldErrors?: unknown;
}

export function normalizeApiError(error: unknown, requestUrl: string): ApiErrorResponse {
  if (error instanceof HttpErrorResponse) {
    const payload = isObject(error.error) ? (error.error as ErrorPayload) : undefined;

    return {
      status: asNumber(payload?.status, error.status),
      error: asString(payload?.error, error.status === 0 ? 'Connection Error' : error.statusText),
      message: asString(
        payload?.message,
        error.status === 0
          ? 'No se pudo conectar con el backend.'
          : 'La solicitud no pudo completarse.',
      ),
      path: asString(payload?.path, requestUrl),
      timestamp: asString(payload?.timestamp, new Date().toISOString()),
      fieldErrors: asFieldErrors(payload?.fieldErrors),
    };
  }

  return {
    status: 0,
    error: 'Client Error',
    message: error instanceof Error ? error.message : 'Se produjo un error inesperado.',
    path: requestUrl,
    timestamp: new Date().toISOString(),
  };
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function asString(value: unknown, fallback: string): string {
  return typeof value === 'string' && value.length > 0 ? value : fallback;
}

function asNumber(value: unknown, fallback: number): number {
  return typeof value === 'number' ? value : fallback;
}

function asFieldErrors(value: unknown): FieldErrors | undefined {
  if (!isObject(value)) {
    return undefined;
  }

  const entries = Object.entries(value).filter(
    (entry): entry is [string, string] => typeof entry[1] === 'string',
  );
  return entries.length > 0 ? Object.fromEntries(entries) : undefined;
}
