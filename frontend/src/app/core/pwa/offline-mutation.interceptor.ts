import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { throwError } from 'rxjs';

import { ApiErrorResponse } from '../../shared/models/api-error.model';
import { NetworkStatusService } from './network-status.service';

const READ_ONLY_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export const offlineMutationInterceptor: HttpInterceptorFn = (request, next) => {
  const network = inject(NetworkStatusService);
  if (network.online() || READ_ONLY_METHODS.has(request.method.toUpperCase())) {
    return next(request);
  }

  const error: ApiErrorResponse = {
    status: 503,
    error: 'Offline',
    message:
      'La operación está bloqueada sin conexión para proteger la consistencia del inventario.',
    path: request.url,
    timestamp: new Date().toISOString(),
  };
  return throwError(() => error);
};
