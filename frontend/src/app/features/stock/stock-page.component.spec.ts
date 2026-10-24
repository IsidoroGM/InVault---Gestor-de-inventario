import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { vi } from 'vitest';

import { AuthSessionStore } from '../../core/auth/auth-session.store';
import { InventoryRealtimeService } from '../../core/realtime/inventory-realtime.service';
import { StockPageComponent } from './stock-page.component';
import { ProductStock } from './stock.models';
import { StockService } from './stock.service';

describe('StockPageComponent synchronization', () => {
  it('reloads after reconnecting and cancels an older stock response', async () => {
    const refreshVersion = signal(0);
    const previous = new Subject<readonly ProductStock[]>();
    const current = new Subject<readonly ProductStock[]>();
    const service = { findAll: vi.fn().mockReturnValueOnce(previous).mockReturnValueOnce(current) };
    await TestBed.configureTestingModule({
      imports: [StockPageComponent],
      providers: [
        provideRouter([]),
        { provide: InventoryRealtimeService, useValue: { refreshVersion } },
        { provide: AuthSessionStore, useValue: { hasAnyRole: () => true } },
        { provide: StockService, useValue: service },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(StockPageComponent);
    fixture.detectChanges();
    expect(service.findAll).toHaveBeenCalledOnce();

    refreshVersion.set(1);
    fixture.detectChanges();
    expect(service.findAll).toHaveBeenCalledTimes(2);
    expect(previous.observed).toBe(false);

    current.next([
      {
        productId: 1,
        sku: 'RAW-001',
        productName: 'Material',
        unitCode: 'KG',
        minimumStock: 10,
        currentStock: 7,
        lowStock: true,
      },
    ]);
    current.complete();
    previous.next([{ currentStock: 99 } as ProductStock]);
    fixture.detectChanges();

    const component = fixture.componentInstance as unknown as {
      stock: Signal<readonly ProductStock[]>;
    };
    expect(component.stock()[0].currentStock).toBe(7);
  });
});
