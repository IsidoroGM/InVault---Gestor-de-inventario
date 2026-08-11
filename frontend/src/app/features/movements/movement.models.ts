import { Batch, BatchProductOption } from '../batches/batch.models';

export type MovementType = 'INBOUND' | 'OUTBOUND' | 'POSITIVE_ADJUSTMENT' | 'NEGATIVE_ADJUSTMENT';

export interface StockMovement {
  readonly id: number;
  readonly productId: number;
  readonly productSku: string;
  readonly productName: string;
  readonly batchId: number;
  readonly batchCode: string;
  readonly userId: number;
  readonly username: string;
  readonly supplierId: number | null;
  readonly supplierName: string | null;
  readonly movementType: MovementType;
  readonly quantity: number;
  readonly previousBatchQuantity: number;
  readonly newBatchQuantity: number;
  readonly reason: string | null;
  readonly movementDate: string;
}

export interface StockMovementRequest {
  readonly productId: number;
  readonly batchId: number;
  readonly supplierId: number | null;
  readonly movementType: MovementType;
  readonly quantity: number;
  readonly reason: string | null;
}

export interface MovementSearchCriteria {
  readonly productId: number | null;
  readonly movementType: MovementType | null;
  readonly page: number;
  readonly size: number;
}

export interface MovementSupplierOption {
  readonly id: number;
  readonly name: string;
  readonly active: boolean;
}

export interface MovementFormOptions {
  readonly products: readonly BatchProductOption[];
  readonly suppliers: readonly MovementSupplierOption[];
}

export type MovementBatchOption = Batch;

export const MOVEMENT_TYPE_LABELS: Readonly<Record<MovementType, string>> = {
  INBOUND: 'Entrada',
  OUTBOUND: 'Salida',
  POSITIVE_ADJUSTMENT: 'Ajuste positivo',
  NEGATIVE_ADJUSTMENT: 'Ajuste negativo',
};

export const MOVEMENT_TYPES = Object.keys(MOVEMENT_TYPE_LABELS) as readonly MovementType[];

export function movementDirection(type: MovementType): 'positive' | 'negative' {
  return type === 'INBOUND' || type === 'POSITIVE_ADJUSTMENT' ? 'positive' : 'negative';
}
