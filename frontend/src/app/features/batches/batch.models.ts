export type BatchStatus = 'AVAILABLE' | 'BLOCKED' | 'CONSUMED' | 'INACTIVE';

export interface Batch {
  readonly id: number;
  readonly productId: number;
  readonly productSku: string;
  readonly productName: string;
  readonly batchCode: string;
  readonly quantity: number;
  readonly status: BatchStatus;
  readonly notes: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface BatchRequest {
  readonly productId: number;
  readonly batchCode: string;
  readonly status: BatchStatus;
  readonly notes: string | null;
}

export interface BatchProductOption {
  readonly id: number;
  readonly sku: string;
  readonly name: string;
  readonly unitSymbol: string;
}

export interface BatchSearchCriteria {
  readonly productId: number | null;
  readonly page: number;
  readonly size: number;
}

export const BATCH_STATUS_LABELS: Readonly<Record<BatchStatus, string>> = {
  AVAILABLE: 'Disponible',
  BLOCKED: 'Bloqueado',
  CONSUMED: 'Consumido',
  INACTIVE: 'Inactivo',
};
