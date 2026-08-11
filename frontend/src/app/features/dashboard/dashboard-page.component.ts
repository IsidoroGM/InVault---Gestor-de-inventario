import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { InventoryRealtimeService } from '../../core/realtime/inventory-realtime.service';
import { AuthSessionStore } from '../../core/auth/auth-session.store';
import { BackendHealthService } from '../../core/services/backend-health.service';
import { apiErrorMessage } from '../../shared/utils/api-error-message';
import {
  MovementType,
  MOVEMENT_TYPE_LABELS,
  movementDirection,
} from '../movements/movement.models';
import { DashboardData } from './dashboard.models';
import { DashboardService } from './dashboard.service';

@Component({
  selector: 'app-dashboard-page',
  imports: [DatePipe, MatButtonModule, MatProgressSpinnerModule, RouterLink],
  templateUrl: './dashboard-page.component.html',
  styleUrl: './dashboard-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPageComponent implements OnInit {
  protected readonly health = inject(BackendHealthService);
  protected readonly realtime = inject(InventoryRealtimeService);
  private readonly service = inject(DashboardService);
  private readonly sessionStore = inject(AuthSessionStore);
  private lastMovementId: number | null = null;

  protected readonly data = signal<DashboardData | null>(null);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly typeLabels = MOVEMENT_TYPE_LABELS;
  protected readonly canMoveStock = computed(() =>
    this.sessionStore.hasAnyRole(['ADMIN', 'SUPERVISOR', 'WAREHOUSE']),
  );

  constructor() {
    effect(() => {
      const event = this.realtime.lastEvent();
      if (event && event.movementId !== this.lastMovementId) {
        this.lastMovementId = event.movementId;
        this.loadDashboard();
      }
    });
  }

  ngOnInit(): void {
    this.health.check();
    this.loadDashboard();
  }

  protected typeLabel(type: MovementType): string {
    return this.typeLabels[type];
  }
  protected direction(type: MovementType): 'positive' | 'negative' {
    return movementDirection(type);
  }
  protected reload(): void {
    this.health.check();
    this.loadDashboard();
  }

  private loadDashboard(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service
      .load()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (data) => this.data.set(data),
        error: (error: unknown) =>
          this.errorMessage.set(apiErrorMessage(error, 'No se pudo cargar el dashboard.')),
      });
  }
}
