import { StockMovement } from '../movements/movement.models';
import { ProductStock } from '../stock/stock.models';

export interface DashboardData {
  readonly activeProductCount: number;
  readonly lowStockProductCount: number;
  readonly availableBatchCount: number;
  readonly lowStockProducts: readonly ProductStock[];
  readonly recentMovements: readonly StockMovement[];
}
