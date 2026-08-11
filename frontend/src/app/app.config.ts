import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners, isDevMode } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { API_CONFIGURATION, apiConfiguration } from './core/configuration/api-configuration';
import { apiErrorInterceptor } from './core/interceptors/api-error.interceptor';
import { authTokenInterceptor } from './core/interceptors/auth-token.interceptor';
import { offlineMutationInterceptor } from './core/pwa/offline-mutation.interceptor';
import { provideServiceWorker } from '@angular/service-worker';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([offlineMutationInterceptor, apiErrorInterceptor, authTokenInterceptor]),
    ),
    { provide: API_CONFIGURATION, useValue: apiConfiguration },
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
