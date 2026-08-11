import { AppEnvironment } from './environment.model';

export const environment: AppEnvironment = {
  production: false,
  restBaseUrl: 'http://localhost:8080',
  webSocketUrl: 'ws://localhost:8080/ws',
  inventoryTopic: '/topic/inventory',
};
