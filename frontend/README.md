# InVault Frontend

Cimentación Angular del gestor empresarial de inventario InVault. Esta subfase entrega el shell responsive, las rutas iniciales y la infraestructura común para consumir el backend Spring Boot sin implementar todavía el ciclo completo de autenticación.

## Requisitos

- Node.js compatible con Angular 21: `^20.19.0`, `^22.12.0` o `^24.0.0`.
- npm 11 o una versión compatible con el `package-lock.json`.
- Backend InVault disponible en `http://localhost:8080` para la comprobación de salud.

La versión seleccionada es Angular 21 LTS. Angular 22 requiere Node 24.15 o superior y el entorno de desarrollo usado para esta subfase dispone de Node 24.14.x.

## Instalación y arranque

```powershell
cd frontend
npm ci
npm start
```

La aplicación queda disponible en `http://localhost:4200`. El backend permite ese origen por defecto en desarrollo.

## Comprobaciones

```powershell
npm run build
npm run test:ci
npm run format:check
```

`npm run build` usa la configuración de producción por defecto. Las pruebas se ejecutan con Vitest y sin modo interactivo.

## Configuración por entorno

Los valores se centralizan en `src/environments/` y se exponen a la aplicación mediante `API_CONFIGURATION`.

| Entorno    | REST                    | WebSocket                | Tema               |
| ---------- | ----------------------- | ------------------------ | ------------------ |
| Desarrollo | `http://localhost:8080` | `ws://localhost:8080/ws` | `/topic/inventory` |
| Producción | mismo origen (`''`)     | `/ws`                    | `/topic/inventory` |

La configuración de producción presupone un proxy inverso que publique frontend y backend bajo el mismo origen. Debe ajustarse al despliegue real antes de publicar.

## Arquitectura inicial

```text
src/app
├── core
│   ├── configuration      # URLs y token de configuración
│   ├── error-handling     # Normalización del contrato de error
│   ├── interceptors       # Interceptor HTTP común, todavía sin JWT
│   └── services           # Cliente API y comprobación de health
├── features
│   └── dashboard          # Primera pantalla funcional
├── layout
│   ├── main-layout        # Shell desktop/tablet
│   ├── navigation         # Navegación lateral
│   └── topbar             # Cabecera
└── shared
    ├── components         # Placeholder reutilizable de rutas
    └── models             # Error, página y roles oficiales
```

Las rutas de productos, lotes, stock, movimientos, catálogos, usuarios y auditoría existen como placeholders explícitos. Sus carpetas se crearán al incorporar funcionalidad real para evitar estructura vacía.

## Contratos backend ya representados

- Error estándar: `status`, `error`, `message`, `path`, `timestamp` y `fieldErrors` opcional.
- Página: `content`, `page`, `size`, `totalElements`, `totalPages`, `first` y `last`.
- Roles: `ADMIN`, `SUPERVISOR`, `WAREHOUSE` y `READ_ONLY`.
- Health público: `GET /api/health`, respuesta de texto.
- WebSocket reservado: `/ws` y `/topic/inventory`.

## Límites de esta subfase

- Sin PWA ni modo offline.
- Sin QR ni códigos de barras.
- Sin cliente STOMP todavía.
- Sin almacenamiento de token, guards ni interceptor Bearer.
- Sin edición directa de cantidades de lote; el futuro flujo de stock usará exclusivamente movimientos.

La siguiente subfase debe implementar login, JWT Bearer, logout, guards, manejo de 401/403, cambio obligatorio de contraseña, navegación por roles y cierre de WebSocket por revocación.
