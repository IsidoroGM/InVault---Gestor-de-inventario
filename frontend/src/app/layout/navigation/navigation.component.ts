import { ChangeDetectionStrategy, Component, output } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { RouterLink, RouterLinkActive } from '@angular/router';

interface NavigationItem {
  readonly route: string;
  readonly label: string;
  readonly marker: string;
}

@Component({
  selector: 'app-navigation',
  imports: [MatListModule, RouterLink, RouterLinkActive],
  templateUrl: './navigation.component.html',
  styleUrl: './navigation.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavigationComponent {
  readonly navigated = output<void>();

  protected readonly items: readonly NavigationItem[] = [
    { route: '/dashboard', label: 'Dashboard', marker: 'DB' },
    { route: '/products', label: 'Productos', marker: 'PR' },
    { route: '/batches', label: 'Lotes', marker: 'LT' },
    { route: '/stock', label: 'Stock', marker: 'ST' },
    { route: '/movements', label: 'Movimientos', marker: 'MV' },
    { route: '/catalogs', label: 'Catálogos', marker: 'CT' },
    { route: '/users', label: 'Usuarios', marker: 'US' },
    { route: '/audit', label: 'Auditoría', marker: 'AU' },
  ];
}
