import { Routes } from '@angular/router';

import { authGuard, guestGuard, passwordPolicyGuard, roleGuard } from './core/auth/auth.guards';

const placeholder = () =>
  import('./shared/components/feature-placeholder/feature-placeholder.component').then(
    (module) => module.FeaturePlaceholderComponent,
  );

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
        loadComponent: placeholder,
        data: {
          eyebrow: 'Inventario',
          title: 'Lotes',
          description:
            'Consulta de lotes y sus estados. La cantidad nunca se editará directamente.',
        },
      },
      {
        path: 'stock',
        title: 'Stock | InVault',
        loadComponent: placeholder,
        data: {
          eyebrow: 'Existencias',
          title: 'Resumen de stock',
          description: 'Saldos agregados por producto y alertas de stock mínimo.',
        },
      },
      {
        path: 'movements',
        title: 'Movimientos | InVault',
        loadComponent: placeholder,
        data: {
          eyebrow: 'Operación',
          title: 'Movimientos',
          description: 'Entradas, salidas y ajustes auditables sobre lotes.',
        },
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
        loadComponent: placeholder,
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
        loadComponent: placeholder,
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
