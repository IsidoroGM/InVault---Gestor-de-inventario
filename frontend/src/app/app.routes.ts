import { Routes } from '@angular/router';

import { authGuard, guestGuard, passwordPolicyGuard, roleGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Iniciar sesión | InVault',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/login-page/login-page.component').then(
        (module) => module.LoginPageComponent,
      ),
  },
  {
    path: 'change-password',
    title: 'Cambiar contraseña | InVault',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/change-password-page/change-password-page.component').then(
        (module) => module.ChangePasswordPageComponent,
      ),
  },
  {
    path: '',
    canActivate: [authGuard, passwordPolicyGuard],
    canActivateChild: [authGuard, passwordPolicyGuard],
    loadComponent: () =>
      import('./layout/main-layout/main-layout.component').then(
        (module) => module.MainLayoutComponent,
      ),
    children: [
      {
        path: 'dashboard',
        title: 'Dashboard | InVault',
        loadComponent: () =>
          import('./features/dashboard/dashboard-page.component').then(
            (module) => module.DashboardPageComponent,
          ),
      },
      {
        path: 'products',
        title: 'Productos | InVault',
        loadComponent: () =>
          import('./features/products/products-page.component').then(
            (module) => module.ProductsPageComponent,
          ),
      },
      {
        path: 'batches',
        title: 'Lotes | InVault',
        loadComponent: () =>
          import('./features/batches/batches-page.component').then(
            (module) => module.BatchesPageComponent,
          ),
      },
      {
        path: 'stock',
        title: 'Stock | InVault',
        loadComponent: () =>
          import('./features/stock/stock-page.component').then(
            (module) => module.StockPageComponent,
          ),
      },
      {
        path: 'movements',
        title: 'Movimientos | InVault',
        loadComponent: () =>
          import('./features/movements/movements-page.component').then(
            (module) => module.MovementsPageComponent,
          ),
      },
      {
        path: 'catalogs',
        title: 'Catálogos | InVault',
        loadComponent: () =>
          import('./features/catalogs/catalogs-page.component').then(
            (module) => module.CatalogsPageComponent,
          ),
      },
      {
        path: 'users',
        title: 'Usuarios | InVault',
        canActivate: [roleGuard],
        data: {
          roles: ['ADMIN', 'SUPERVISOR'],
          eyebrow: 'Administración',
          title: 'Usuarios y roles',
          description: 'Gestión de cuentas con los roles oficiales de InVault.',
        },
        loadComponent: () =>
          import('./features/users/users-page.component').then(
            (module) => module.UsersPageComponent,
          ),
      },
      {
        path: 'audit',
        title: 'Auditoría | InVault',
        canActivate: [roleGuard],
        data: {
          roles: ['ADMIN', 'SUPERVISOR'],
          eyebrow: 'Trazabilidad',
          title: 'Auditoría',
          description: 'Consulta paginada de acciones, actores y cambios antes/después.',
        },
        loadComponent: () =>
          import('./features/audit/audit-page.component').then(
            (module) => module.AuditPageComponent,
          ),
      },
      {
        path: 'forbidden',
        title: 'Acceso restringido | InVault',
        loadComponent: () =>
          import('./features/auth/access-denied-page/access-denied-page.component').then(
            (module) => module.AccessDeniedPageComponent,
          ),
      },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: '**', redirectTo: 'dashboard' },
    ],
  },
];
