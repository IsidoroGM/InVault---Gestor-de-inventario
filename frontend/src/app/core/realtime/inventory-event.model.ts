export interface InventoryEvent {
  readonly eventType: 'STOCK_UPDATED' | 'BATCH_CHANGED';
  readonly occurredAt: string;
  readonly movementId: number | null;
  readonly productId: number;
  readonly productSku: string;
  readonly batchId: number;
  readonly batchCode: string;
  readonly movementType:
    'INBOUND' | 'OUTBOUND' | 'POSITIVE_ADJUSTMENT' | 'NEGATIVE_ADJUSTMENT' | null;
  readonly quantity: number | null;
  readonly previousBatchQuantity: number | null;
  readonly newBatchQuantity: number;
}

export type RealtimeState = 'disconnected' | 'connecting' | 'connected' | 'error';
