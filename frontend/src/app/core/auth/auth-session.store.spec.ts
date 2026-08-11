import { TestBed } from '@angular/core/testing';

import { LoginResponse } from './auth.models';
import { AuthSessionStore } from './auth-session.store';

describe('AuthSessionStore', () => {
  const response = (overrides: Partial<LoginResponse> = {}): LoginResponse => ({
    accessToken: 'signed-token',
    tokenType: 'Bearer',
    expiresIn: 1800,
    expiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
    userId: 7,
    username: 'operator',
    roles: ['WAREHOUSE'],
    mustChangePassword: false,
    ...overrides,
  });

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [AuthSessionStore] });
  });

  afterEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('stores a valid login only for the current browser session', () => {
    const store = TestBed.inject(AuthSessionStore);

    store.setFromLogin(response());

    expect(store.isAuthenticated()).toBe(true);
    expect(store.accessToken()).toBe('signed-token');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.getItem('invault.auth.session')).toContain('signed-token');
  });

  it('exposes official roles and mandatory password state', () => {
    const store = TestBed.inject(AuthSessionStore);

    store.setFromLogin(response({ roles: ['ADMIN'], mustChangePassword: true }));

    expect(store.hasAnyRole(['ADMIN', 'SUPERVISOR'])).toBe(true);
    expect(store.mustChangePassword()).toBe(true);
  });

  it('rejects expired or malformed authentication responses', () => {
    const store = TestBed.inject(AuthSessionStore);

    expect(() =>
      store.setFromLogin(response({ expiresAt: new Date(Date.now() - 1000).toISOString() })),
    ).toThrow();
    expect(() => store.setFromLogin(response({ roles: [] }))).toThrow();
    expect(store.session()).toBeNull();
  });

  it('clears the token and identity together', () => {
    const store = TestBed.inject(AuthSessionStore);
    store.setFromLogin(response());

    store.clear();

    expect(store.session()).toBeNull();
    expect(sessionStorage.length).toBe(0);
  });
});
