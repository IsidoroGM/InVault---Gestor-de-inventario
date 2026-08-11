import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';

import { authGuard, passwordPolicyGuard, roleGuard } from './auth.guards';
import { LoginResponse } from './auth.models';
import { AuthSessionStore } from './auth-session.store';

describe('authentication guards', () => {
  let store: AuthSessionStore;
  let router: Router;

  const loginResponse = (mustChangePassword = false): LoginResponse => ({
    accessToken: 'signed-token',
    tokenType: 'Bearer',
    expiresIn: 1800,
    expiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
    userId: 7,
    username: 'operator',
    roles: ['WAREHOUSE'],
    mustChangePassword,
  });

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), AuthSessionStore],
    });
    store = TestBed.inject(AuthSessionStore);
    router = TestBed.inject(Router);
  });

  afterEach(() => sessionStorage.clear());

  it('sends anonymous users to login and retains the intended URL', () => {
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/products' } as RouterStateSnapshot),
    );

    expect(router.serializeUrl(result as UrlTree)).toBe('/login?returnUrl=%2Fproducts');
  });

  it('forces the mandatory password flow before entering the shell', () => {
    store.setFromLogin(loginResponse(true));

    const result = TestBed.runInInjectionContext(() =>
      passwordPolicyGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );

    expect(router.serializeUrl(result as UrlTree)).toBe('/change-password');
  });

  it('blocks routes outside the active role', () => {
    store.setFromLogin(loginResponse());

    const result = TestBed.runInInjectionContext(() =>
      roleGuard(
        { data: { roles: ['ADMIN', 'SUPERVISOR'] } } as unknown as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
      ),
    );

    expect(router.serializeUrl(result as UrlTree)).toBe('/forbidden');
  });
});
