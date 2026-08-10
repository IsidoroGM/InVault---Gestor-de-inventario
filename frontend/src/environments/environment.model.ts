export interface AppEnvironment {
  readonly production: boolean;
  readonly restBaseUrl: string;
  readonly webSocketUrl: string;
  readonly inventoryTopic: string;
}
