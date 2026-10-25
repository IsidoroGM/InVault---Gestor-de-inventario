import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { AuthSessionStore } from '../auth/auth-session.store';
import { AuthSession } from '../auth/auth.models';
import { API_CONFIGURATION } from '../configuration/api-configuration';
import { NetworkStatusService } from '../pwa/network-status.service';
import { InventoryRealtimeService } from './inventory-realtime.service';

interface ClientOptions {
  connectHeaders: { Authorization: string };
  onConnect(): void;
  onWebSocketClose(): void;
  onWebSocketError(): void;
  onStompError(): void;
}

interface FakeClient {
  options: ClientOptions;
  activate: ReturnType<typeof vi.fn>;
  deactivate: ReturnType<typeof vi.fn>;
  subscribe: ReturnType<typeof vi.fn>;
  receive?: (message: { body: string }) => void;
}

const stomp = vi.hoisted(() => ({ clients: [] as FakeClient[] }));

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    readonly activate = vi.fn();
    readonly deactivate = vi.fn(() => Promise.resolve());
    receive?: (message: { body: string }) => void;
    readonly subscribe = vi.fn(
      (_destination: string, callback: (message: { body: string }) => void) => {
        this.receive = callback;
        return { id: 'inventory', unsubscribe: vi.fn() };
      },
    );

    constructor(readonly options: ClientOptions) {
      stomp.clients.push(this);
    }
  },
}));

describe('InventoryRealtimeService', () => {
  const session = signal<AuthSession | null>(null);
  const offline = signal(false);
  const login = (): AuthSession => ({
    accessToken: 'token-a',
    tokenType: 'Bearer',
    expiresIn: 1800,
    expiresAt: new Date(Date.now() + 1800000).toISOString(),
    userId: 1,
    username: 'operator',
    roles: ['WAREHOUSE'],
    mustChangePassword: false,
  });

  beforeEach(() => {
    stomp.clients.length = 0;
    offline.set(false);
    session.set(login());
    TestBed.configureTestingModule({
      providers: [
        { provide: NetworkStatusService, useValue: { online: () => !offline(), offline } },
        {
          provide: AuthSessionStore,
          useValue: { session, hasValidSession: () => session() !== null },
        },
        {
          provide: API_CONFIGURATION,
          useValue: { webSocketUrl: 'ws://localhost/ws', inventoryTopic: '/topic/inventory' },
        },
      ],
    });
  });

  function start(): InventoryRealtimeService {
    const service = TestBed.inject(InventoryRealtimeService);
    TestBed.tick();
    return service;
  }

  it('authenticates and requests a REST refresh after subscribing', () => {
    const service = start();
    const client = stomp.clients[0];
    expect(client.options.connectHeaders.Authorization).toBe('Bearer token-a');
    client.options.onConnect();
    expect(client.subscribe).toHaveBeenCalledWith('/topic/inventory', expect.any(Function));
    expect(service.state()).toBe('connected');
    expect(service.refreshVersion()).toBe(1);
  });

  it('refreshes after a reconnect even when no new movement event arrives', () => {
    const service = start();
    const client = stomp.clients[0];
    client.options.onConnect();
    client.options.onWebSocketClose();
    expect(service.state()).toBe('disconnected');
    client.options.onConnect();
    expect(service.refreshVersion()).toBe(2);
    expect(service.lastEvent()).toBeNull();
  });

  it('replaces a seemingly connected socket after an offline interval', () => {
    const service = start();
    const stale = stomp.clients[0];
    stale.options.onConnect();
    offline.set(true);
    TestBed.tick();
    expect(stale.deactivate).toHaveBeenCalled();
    expect(service.state()).toBe('disconnected');
    stale.options.onConnect();
    expect(service.refreshVersion()).toBe(1);
    offline.set(false);
    TestBed.tick();
    const current = stomp.clients[1];
    expect(current.activate).toHaveBeenCalled();
    current.options.onConnect();
    expect(service.refreshVersion()).toBe(2);
    expect(service.state()).toBe('connected');
  });

  it('invalidates inventory for batch changes that have no movement identifier', () => {
    const service = start();
    const client = stomp.clients[0];
    client.options.onConnect();
    client.receive?.({
      body: JSON.stringify({ eventType: 'BATCH_CHANGED', movementId: null, batchId: 7 }),
    });
    expect(service.refreshVersion()).toBe(2);
    expect(service.lastEvent()?.eventType).toBe('BATCH_CHANGED');
  });

  it('invalidates inventory for stock movements', () => {
    const service = start();
    const client = stomp.clients[0];
    client.options.onConnect();
    client.receive?.({ body: JSON.stringify({ eventType: 'STOCK_UPDATED', movementId: 5 }) });
    expect(service.refreshVersion()).toBe(2);
  });

  it('ignores callbacks and messages from the previous session', () => {
    const service = start();
    const previous = stomp.clients[0];
    previous.options.onConnect();
    session.set({ ...login(), accessToken: 'token-b', userId: 2 });
    TestBed.tick();
    const current = stomp.clients[1];
    current.options.onConnect();

    previous.options.onConnect();
    previous.options.onWebSocketClose();
    previous.options.onStompError();
    previous.options.onWebSocketError();
    previous.receive?.({ body: JSON.stringify({ eventType: 'STOCK_UPDATED', movementId: 9 }) });

    expect(previous.deactivate).toHaveBeenCalled();
    expect(service.state()).toBe('connected');
    expect(service.refreshVersion()).toBe(2);
    expect(service.lastEvent()).toBeNull();
  });

  it('disconnects and clears the last event on logout', () => {
    const service = start();
    const client = stomp.clients[0];
    client.options.onConnect();
    client.receive?.({ body: JSON.stringify({ eventType: 'STOCK_UPDATED', movementId: 5 }) });
    session.set(null);
    TestBed.tick();
    expect(client.deactivate).toHaveBeenCalled();
    expect(service.lastEvent()).toBeNull();
    expect(service.state()).toBe('disconnected');
  });

  it('does not open a connection before the mandatory password change', () => {
    session.set({ ...login(), mustChangePassword: true });
    start();
    expect(stomp.clients).toHaveLength(0);
  });

  it('reports malformed messages without requesting another data load', () => {
    const service = start();
    const client = stomp.clients[0];
    client.options.onConnect();
    client.receive?.({ body: '{invalid' });
    expect(service.state()).toBe('error');
    expect(service.refreshVersion()).toBe(1);
  });
});
