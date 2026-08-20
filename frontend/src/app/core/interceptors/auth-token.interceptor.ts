import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { API_CONFIGURATION } from '../configuration/api-configuration';
import { AuthSessionStore } from '../auth/auth-session.store';

export const authTokenInterceptor: HttpInterceptorFn = (request, next) => {
  const configuration = inject(API_CONFIGURATION);
  const store = inject(AuthSessionStore);
  const router = inject(Router);
  const token = store.accessToken();
  const apiRoot = `${configuration.restBaseUrl}/api/`;
  const isApiRequest = request.url.startsWith(apiRoot);

  const authenticatedRequest =
    token && isApiRequest
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request;

  return next(authenticatedRequest).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && token && error.status === 401) {
        store.clear();
        void router.navigate(['/login'], {
          queryParams: { sessionExpired: '1' },
          replaceUrl: true,
        });
      } else if (
        error instanceof HttpErrorResponse &&
        error.status === 403 &&
        store.mustChangePassword()
      ) {
        void router.navigate(['/change-password'], { replaceUrl: true });
      }

      return throwError(() => error);
    }),
  );
};
