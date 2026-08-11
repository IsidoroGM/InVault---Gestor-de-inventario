export interface InventoryEvent {
  readonly eventType: 'STOCK_UPDATED';
  readonly occurredAt: string;
  readonly movementId: number;
  readonly productId: number;
  readonly productSku: string;
  readonly batchId: number;
  readonly batchCode: string;
  readonly movementType: 'INBOUND' | 'OUTBOUND' | 'POSITIVE_ADJUSTMENT' | 'NEGATIVE_ADJUSTMENT';
  readonly quantity: number;
  readonly previousBatchQuantity: number;
  readonly newBatchQuantity: number;
}

export type RealtimeState = 'disconnected' | 'connecting' | 'connected' | 'error';
