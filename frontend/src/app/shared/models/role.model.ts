export const OFFICIAL_ROLES = ['ADMIN', 'SUPERVISOR', 'WAREHOUSE', 'READ_ONLY'] as const;

export type Role = (typeof OFFICIAL_ROLES)[number];
