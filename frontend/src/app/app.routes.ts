import { Routes } from '@angular/router';

const placeholder = () =>
  import('./shared/components/feature-placeholder/feature-placeholder.component').then(
    (module) => module.FeaturePlaceholderComponent,
  );

export const routes: Routes = [
  {
    path: '',
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
        loadComponent: placeholder,
        data: {
          eyebrow: 'Inventario',
          title: 'Productos',
          description: 'Catálogo de productos, búsqueda y mantenimiento por roles.',
        },
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
        loadComponent: placeholder,
        data: {
          eyebrow: 'Configuración',
          title: 'Catálogos',
          description: 'Unidades, categorías, ubicaciones y proveedores.',
        },
      },
      {
        path: 'users',
        title: 'Usuarios | InVault',
        loadComponent: placeholder,
        data: {
          eyebrow: 'Administración',
          title: 'Usuarios y roles',
          description: 'Gestión de cuentas con los roles oficiales de InVault.',
        },
      },
      {
        path: 'audit',
        title: 'Auditoría | InVault',
        loadComponent: placeholder,
        data: {
          eyebrow: 'Trazabilidad',
          title: 'Auditoría',
          description: 'Consulta paginada de acciones, actores y cambios antes/después.',
        },
      },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: '**', redirectTo: 'dashboard' },
    ],
  },
];
