import { inject } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
} from '@angular/router';

import { Role } from '../../shared/models/role.model';
import { AuthSessionStore } from './auth-session.store';

export const authGuard: CanActivateFn = (_route, state: RouterStateSnapshot) => {
  const store = inject(AuthSessionStore);
  const router = inject(Router);

  return store.hasValidSession()
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export const guestGuard: CanActivateFn = () => {
  const store = inject(AuthSessionStore);
  const router = inject(Router);

  if (!store.hasValidSession()) {
    return true;
  }

  return router.createUrlTree([store.mustChangePassword() ? '/change-password' : '/dashboard']);
};

export const passwordPolicyGuard: CanActivateFn = () => {
  const store = inject(AuthSessionStore);
  const router = inject(Router);
  return store.mustChangePassword() ? router.createUrlTree(['/change-password']) : true;
};

export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const store = inject(AuthSessionStore);
  const router = inject(Router);
  const roles = (route.data['roles'] as readonly Role[] | undefined) ?? [];

  return roles.length === 0 || store.hasAnyRole(roles)
    ? true
    : router.createUrlTree(['/forbidden']);
};
