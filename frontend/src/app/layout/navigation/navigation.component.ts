import { ChangeDetectionStrategy, Component, computed, inject, output } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthSessionStore } from '../../core/auth/auth-session.store';
import { Role } from '../../shared/models/role.model';

interface NavigationItem {
  readonly route: string;
  readonly label: string;
  readonly marker: string;
  readonly roles?: readonly Role[];
}

@Component({
  selector: 'app-navigation',
  imports: [MatListModule, RouterLink, RouterLinkActive],
  templateUrl: './navigation.component.html',
  styleUrl: './navigation.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavigationComponent {
  private readonly store = inject(AuthSessionStore);

  readonly navigated = output<void>();

  protected readonly items: readonly NavigationItem[] = [
    { route: '/dashboard', label: 'Dashboard', marker: 'DB' },
    { route: '/products', label: 'Productos', marker: 'PR' },
    { route: '/batches', label: 'Lotes', marker: 'LT' },
    { route: '/stock', label: 'Stock', marker: 'ST' },
    { route: '/movements', label: 'Movimientos', marker: 'MV' },
    { route: '/catalogs', label: 'Catálogos', marker: 'CT' },
    {
      route: '/users',
      label: 'Usuarios',
      marker: 'US',
      roles: ['ADMIN', 'SUPERVISOR'],
    },
    {
      route: '/audit',
      label: 'Auditoría',
      marker: 'AU',
      roles: ['ADMIN', 'SUPERVISOR'],
    },
  ];

  protected readonly visibleItems = computed(() =>
    this.items.filter((item) => !item.roles || this.store.hasAnyRole(item.roles)),
  );
}
