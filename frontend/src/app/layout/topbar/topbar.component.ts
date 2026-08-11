import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';

import { AuthSessionStore } from '../../core/auth/auth-session.store';
import { InventoryRealtimeService } from '../../core/realtime/inventory-realtime.service';
import { Role } from '../../shared/models/role.model';

@Component({
  selector: 'app-topbar',
  imports: [MatButtonModule, MatToolbarModule],
  templateUrl: './topbar.component.html',
  styleUrl: './topbar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopbarComponent {
  private readonly store = inject(AuthSessionStore);

  readonly compact = input(false);
  readonly menuToggle = output<void>();
  readonly logoutRequested = output<void>();

  protected readonly session = this.store.session;
  protected readonly realtime = inject(InventoryRealtimeService);

  protected initials(username: string): string {
    return username.slice(0, 2).toUpperCase();
  }

  protected roleLabel(role: Role | undefined): string {
    const labels: Record<Role, string> = {
      ADMIN: 'Administración',
      SUPERVISOR: 'Supervisión',
      WAREHOUSE: 'Almacén',
      READ_ONLY: 'Solo lectura',
    };
    return role ? labels[role] : 'Sin rol';
  }
}
