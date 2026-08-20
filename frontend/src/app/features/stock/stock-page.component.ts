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

import { AuthSessionStore } from '../../core/auth/auth-session.store';
import { InventoryRealtimeService } from '../../core/realtime/inventory-realtime.service';
import { apiErrorMessage } from '../../shared/utils/api-error-message';
import { ProductStock } from './stock.models';
import { StockService } from './stock.service';

@Component({
  selector: 'app-stock-page',
  imports: [MatButtonModule, MatProgressSpinnerModule, RouterLink],
  templateUrl: './stock-page.component.html',
  styleUrl: './stock-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockPageComponent implements OnInit {
  private readonly realtime = inject(InventoryRealtimeService);
  private readonly service = inject(StockService);
  private readonly sessionStore = inject(AuthSessionStore);
  private lastMovementId: number | null = null;

  protected readonly stock = signal<readonly ProductStock[]>([]);
  protected readonly query = signal('');
  protected readonly onlyLowStock = signal(false);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly canMoveStock = computed(() =>
    this.sessionStore.hasAnyRole(['ADMIN', 'SUPERVISOR', 'WAREHOUSE']),
  );
  protected readonly lowStockCount = computed(
    () => this.stock().filter((item) => item.lowStock).length,
  );
  protected readonly productsWithStock = computed(
    () => this.stock().filter((item) => Number(item.currentStock) > 0).length,
  );
  protected readonly filtered = computed(() => {
    const query = this.query().trim().toLocaleLowerCase('es');
    return this.stock().filter(
      (item) =>
        (!this.onlyLowStock() || item.lowStock) &&
        (!query ||
          item.sku.toLocaleLowerCase('es').includes(query) ||
          item.productName.toLocaleLowerCase('es').includes(query)),
    );
  });

  constructor() {
    effect(() => {
      const event = this.realtime.lastEvent();
      if (event && event.movementId !== this.lastMovementId) {
        this.lastMovementId = event.movementId;
        this.loadStock();
      }
    });
  }

  ngOnInit(): void {
    this.loadStock();
  }
  protected updateQuery(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }
  protected toggleLowStock(): void {
    this.onlyLowStock.update((value) => !value);
  }
  protected reload(): void {
    this.loadStock();
  }

  private loadStock(): void {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.service
      .findAll()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (stock) => this.stock.set(stock),
        error: (error: unknown) => {
          this.stock.set([]);
          this.errorMessage.set(apiErrorMessage(error, 'No se pudo cargar el resumen de stock.'));
        },
      });
  }
}
