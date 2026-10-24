import { effect, inject, Injectable, signal } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';

import { AuthSessionStore } from '../auth/auth-session.store';
import { API_CONFIGURATION } from '../configuration/api-configuration';
import { InventoryEvent, RealtimeState } from './inventory-event.model';

@Injectable({ providedIn: 'root' })
export class InventoryRealtimeService {
  private readonly store = inject(AuthSessionStore);
  private readonly configuration = inject(API_CONFIGURATION);
  private client: Client | null = null;

  private readonly stateSignal = signal<RealtimeState>('disconnected');
  private readonly lastEventSignal = signal<InventoryEvent | null>(null);
  private readonly refreshVersionSignal = signal(0);

  readonly state = this.stateSignal.asReadonly();
  readonly lastEvent = this.lastEventSignal.asReadonly();
  readonly refreshVersion = this.refreshVersionSignal.asReadonly();

  constructor() {
    effect((onCleanup) => {
      const session = this.store.session();
      if (session && this.store.hasValidSession() && !session.mustChangePassword) {
        this.connect(session.accessToken);
      } else {
        this.disconnect();
      }

      onCleanup(() => this.disconnect());
    });
  }

  private connect(accessToken: string): void {
    this.disconnect();
    this.stateSignal.set('connecting');

    const client = new Client({
      brokerURL: this.configuration.webSocketUrl,
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      debug: () => undefined,
      onConnect: () => {
        if (this.client !== client) {
          return;
        }
        client.subscribe(this.configuration.inventoryTopic, (message) => {
          if (this.client === client) {
            this.receive(message);
          }
        });
        this.stateSignal.set('connected');
        // A connection can have missed events. Recover the authoritative state through REST.
        this.refreshVersionSignal.update((version) => version + 1);
      },
      onStompError: () => {
        if (this.client === client) {
          this.stateSignal.set('error');
        }
      },
      onWebSocketError: () => {
        if (this.client === client) {
          this.stateSignal.set('error');
        }
      },
      onWebSocketClose: () => {
        if (this.client === client) {
          this.stateSignal.set('disconnected');
        }
      },
    });

    this.client = client;
    client.activate();
  }

  private disconnect(): void {
    const client = this.client;
    this.client = null;
    this.lastEventSignal.set(null);
    if (client) {
      void client.deactivate();
    }
    this.stateSignal.set('disconnected');
  }

  private receive(message: IMessage): void {
    try {
      const event = JSON.parse(message.body) as InventoryEvent | null;
      if (event?.eventType === 'STOCK_UPDATED' || event?.eventType === 'BATCH_CHANGED') {
        this.lastEventSignal.set(event);
        this.refreshVersionSignal.update((version) => version + 1);
      }
    } catch {
      this.stateSignal.set('error');
    }
  }
}
