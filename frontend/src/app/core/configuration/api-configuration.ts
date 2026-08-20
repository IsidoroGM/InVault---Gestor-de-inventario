import { InjectionToken } from '@angular/core';

import { environment } from '../../../environments/environment';

export interface ApiConfiguration {
  readonly restBaseUrl: string;
  readonly webSocketUrl: string;
  readonly inventoryTopic: string;
}

export const API_CONFIGURATION = new InjectionToken<ApiConfiguration>('API_CONFIGURATION');

export function resolveWebSocketUrl(
  configuredUrl: string,
  pageUrl = globalThis.location?.href,
): string {
  if (/^wss?:\/\//i.test(configuredUrl) || !pageUrl) {
    return configuredUrl;
  }

  const resolvedUrl = new URL(configuredUrl, pageUrl);
  resolvedUrl.protocol = resolvedUrl.protocol === 'https:' ? 'wss:' : 'ws:';
  return resolvedUrl.toString();
}

export const apiConfiguration: ApiConfiguration = {
  restBaseUrl: environment.restBaseUrl,
  webSocketUrl: resolveWebSocketUrl(environment.webSocketUrl),
  inventoryTopic: environment.inventoryTopic,
};
