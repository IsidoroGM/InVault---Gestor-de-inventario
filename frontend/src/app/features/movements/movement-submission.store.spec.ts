import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { AuthSessionStore } from '../../core/auth/auth-session.store';
import { StockMovementRequest } from './movement.models';
import { MovementSubmissionStore } from './movement-submission.store';

describe('MovementSubmissionStore', () => {
  const owner = signal<{ userId: number } | null>({ userId: 7 });
  const request: StockMovementRequest = {
    productId: 2,
    batchId: 3,
    movementType: 'INBOUND',
    quantity: 4,
    supplierId: null,
    reason: 'Recepción',
  };

  beforeEach(() => {
    sessionStorage.clear();
    owner.set({ userId: 7 });
    TestBed.configureTestingModule({
      providers: [{ provide: AuthSessionStore, useValue: { session: owner } }],
    });
  });
  afterEach(() => sessionStorage.clear());
  const store = () => TestBed.runInInjectionContext(() => new MovementSubmissionStore());

  it('retains the same key and payload when a dialog or application is recreated', () => {
    const original = store().begin(request);
    expect(store().current()).toEqual(original);
    expect(original.key).toMatch(/^[\da-f-]{36}$/i);
    expect(() => store().begin({ ...request, quantity: 9 })).toThrow();
  });

  it('separates pending submissions by user and restores them after logging back in', () => {
    const first = store().begin(request);
    owner.set({ userId: 8 });
    expect(store().current()).toBeNull();
    const second = store().begin(request);
    expect(second.key).not.toBe(first.key);
    owner.set(null);
    expect(store().current()).toBeNull();
    owner.set({ userId: 7 });
    expect(store().current()).toEqual(first);
  });

  it('clears only the matching completed submission', () => {
    const original = store().begin(request);
    store().complete({ ...original, key: crypto.randomUUID() });
    expect(store().current()).toEqual(original);
    store().complete(original);
    expect(store().current()).toBeNull();
  });

  it('does not silently replace corrupted pending data with a new movement', () => {
    store().begin(request);
    sessionStorage.setItem('invault.movement.pending.v1.7', '{broken');
    expect(store().hasPending()).toBe(true);
    expect(() => store().begin(request)).toThrow();
    expect(() => store().current()).toThrow();
  });
});
