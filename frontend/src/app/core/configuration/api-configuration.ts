import { InjectionToken } from '@angular/core';

import { environment } from '../../../environments/environment';

export interface ApiConfiguration {
  readonly restBaseUrl: string;
  readonly webSocketUrl: string;
  readonly inventoryTopic: string;
}

export const API_CONFIGURATION = new InjectionToken<ApiConfiguration>('API_CONFIGURATION');

export const apiConfiguration: ApiConfiguration = {
  restBaseUrl: environment.restBaseUrl,
  webSocketUrl: environment.webSocketUrl,
  inventoryTopic: environment.inventoryTopic,
};
