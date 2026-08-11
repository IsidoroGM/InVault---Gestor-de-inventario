import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_CONFIGURATION } from '../configuration/api-configuration';
import { LoginResponse } from './auth.models';
import { AuthSessionStore } from './auth-session.store';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let store: AuthSessionStore;
  let http: HttpTestingController;

  const loginResponse: LoginResponse = {
    accessToken: 'signed-token',
    tokenType: 'Bearer',
    expiresIn: 1800,
    expiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
    userId: 7,
    username: 'operator',
    roles: ['WAREHOUSE'],
    mustChangePassword: false,
  };

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        AuthSessionStore,
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: API_CONFIGURATION,
          useValue: {
            restBaseUrl: 'http://localhost:8080',
            webSocketUrl: 'ws://localhost:8080/ws',
            inventoryTopic: '/topic/inventory',
          },
        },
      ],
    });

    auth = TestBed.inject(AuthService);
    store = TestBed.inject(AuthSessionStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('logs in and initializes the session from the backend contract', () => {
    auth.login({ username: 'operator', password: 'temporary-password' }).subscribe();

    const request = http.expectOne('http://localhost:8080/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      username: 'operator',
      password: 'temporary-password',
    });
    request.flush(loginResponse);

    expect(store.session()?.username).toBe('operator');
  });

  it('changes the current password and discards the revoked token', () => {
    store.setFromLogin(loginResponse);

    auth
      .changePassword({ currentPassword: 'temporary-password', newPassword: 'new-password-123' })
      .subscribe();

    const request = http.expectOne('http://localhost:8080/api/users/me/password');
    expect(request.request.method).toBe('PUT');
    request.flush(null);

    expect(store.session()).toBeNull();
  });

  it('logs out through the backend and always removes the local session', () => {
    store.setFromLogin(loginResponse);

    auth.logout().subscribe();

    const request = http.expectOne('http://localhost:8080/api/auth/logout');
    expect(request.request.method).toBe('POST');
    request.flush(null);

    expect(store.session()).toBeNull();
  });
});
