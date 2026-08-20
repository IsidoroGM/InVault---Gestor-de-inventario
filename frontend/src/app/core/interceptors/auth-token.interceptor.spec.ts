import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { AuthSessionStore } from '../auth/auth-session.store';
import { API_CONFIGURATION } from '../configuration/api-configuration';
import { authTokenInterceptor } from './auth-token.interceptor';

describe('authTokenInterceptor', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let store: AuthSessionStore;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: { navigate: vi.fn().mockResolvedValue(true) } },
        provideHttpClient(withInterceptors([authTokenInterceptor])),
        provideHttpClientTesting(),
        AuthSessionStore,
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

    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    store = TestBed.inject(AuthSessionStore);
    store.setFromLogin({
      accessToken: 'signed-token',
      tokenType: 'Bearer',
      expiresIn: 1800,
      expiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
      userId: 7,
      username: 'operator',
      roles: ['WAREHOUSE'],
      mustChangePassword: false,
    });
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('adds the bearer token to configured backend API requests', () => {
    client.get('http://localhost:8080/api/products').subscribe();

    const request = http.expectOne('http://localhost:8080/api/products');
    expect(request.request.headers.get('Authorization')).toBe('Bearer signed-token');
    request.flush([]);
  });

  it('never sends the token to an external origin', () => {
    client.get('https://example.com/api/products').subscribe();

    const request = http.expectOne('https://example.com/api/products');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush([]);
  });

  it('discards the complete session after an authenticated 401', () => {
    client.get('http://localhost:8080/api/products').subscribe({ error: () => undefined });

    const request = http.expectOne('http://localhost:8080/api/products');
    request.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(store.session()).toBeNull();
  });
});
