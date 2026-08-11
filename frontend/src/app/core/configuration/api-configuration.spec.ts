import { describe, expect, it } from 'vitest';

import { resolveWebSocketUrl } from './api-configuration';

describe('resolveWebSocketUrl', () => {
  it('keeps an absolute WebSocket URL unchanged', () => {
    expect(resolveWebSocketUrl('ws://localhost:8080/ws', 'https://inventory.example')).toBe(
      'ws://localhost:8080/ws',
    );
  });

  it('resolves a relative URL using secure WebSockets on HTTPS', () => {
    expect(resolveWebSocketUrl('/ws', 'https://inventory.example/app')).toBe(
      'wss://inventory.example/ws',
    );
  });

  it('resolves a relative URL using WebSockets on HTTP', () => {
    expect(resolveWebSocketUrl('/ws', 'http://localhost:8080/app')).toBe('ws://localhost:8080/ws');
  });
});
