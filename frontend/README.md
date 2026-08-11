# InVault Frontend

Aplicación Angular del gestor empresarial de inventario InVault. Incluye el shell responsive, autenticación JWT, autorización por roles y conexión STOMP con el backend Spring Boot.

## Requisitos

- Node.js compatible con Angular 21: `^20.19.0`, `^22.12.0` o `^24.0.0`.
- npm 11 o una versión compatible con el `package-lock.json`.
- Backend InVault en `http://localhost:8080` para iniciar sesión y consumir datos.

La versión seleccionada es Angular 21 LTS. Angular 22 requiere Node 24.15 o superior y este entorno dispone de Node 24.14.x.

## Instalación y arranque

```powershell
cd frontend
npm ci
npm start
```

La aplicación queda disponible en `http://localhost:4200`. El backend permite ese origen por defecto en desarrollo.

## Comprobaciones

```powershell
npm run format:check
npm run build
npm run test:ci
npm audit --omit=dev
```

`npm run build` usa producción por defecto. Las pruebas se ejecutan con Vitest y sin modo interactivo.

## Configuración por entorno

Los valores se centralizan en `src/environments/` y se inyectan mediante `API_CONFIGURATION`.

| Entorno    | REST                    | WebSocket                | Tema               |
| ---------- | ----------------------- | ------------------------ | ------------------ |
| Desarrollo | `http://localhost:8080` | `ws://localhost:8080/ws` | `/topic/inventory` |
| Producción | mismo origen (`''`)     | `/ws`                    | `/topic/inventory` |

Producción presupone un proxy inverso que publique frontend y backend bajo el mismo origen.

## Autenticación y sesión

1. `POST /api/auth/login` devuelve el JWT, identidad, roles, expiración y `mustChangePassword`.
2. La sesión se conserva en `sessionStorage`, limitada a la pestaña actual. No se utiliza `localStorage`.
3. El interceptor añade `Authorization: Bearer <token>` únicamente a peticiones `/api/` del backend configurado.
4. Un 401 elimina íntegramente la sesión y devuelve al login.
5. Si el backend exige cambio de contraseña, solo se permiten `/change-password` y logout.
6. Cambiar la contraseña o cerrar sesión revoca el token; el frontend lo descarta y exige un nuevo login.

El almacenamiento web no protege un token frente a una vulnerabilidad XSS. Por eso la aplicación evita HTML dinámico, limita el token a `sessionStorage` y nunca lo envía a destinos externos. Una evolución posterior puede adoptar cookies `HttpOnly` si cambia el contrato backend.

## Roles

- `ADMIN`: acceso completo, usuarios y auditoría.
- `SUPERVISOR`: consulta y operación de gestión, usuarios y auditoría.
- `WAREHOUSE`: consulta y movimientos de stock.
- `READ_ONLY`: consulta del inventario.

Los guards protegen las rutas aunque un usuario introduzca manualmente la URL. La navegación oculta las secciones no disponibles, pero el backend sigue siendo la autoridad final.

## Tiempo real

`InventoryRealtimeService` abre STOMP nativo contra `/ws` después del login y envía el JWT en el frame `CONNECT`. Solo se suscribe a `/topic/inventory`; el backend no permite mensajes de escritura desde el navegador.

La conexión se desactiva al cerrar o invalidar la sesión y se reintenta automáticamente ante cortes transitorios. Los eventos admitidos usan `eventType: STOCK_UPDATED` y el contrato inmutable definido por el backend.

## Arquitectura

```text
src/app
├── core
│   ├── auth              # sesión, servicio y guards
│   ├── configuration     # URLs e inyección de configuración
│   ├── error-handling    # normalización y mensajes de autenticación
│   ├── interceptors      # Bearer, 401/403 y error API
│   ├── realtime          # cliente STOMP y contrato de eventos
│   └── services          # cliente REST y health
├── features
│   ├── audit             # filtros, trazabilidad y comparación de cambios
│   ├── auth              # login, contraseña obligatoria y acceso denegado
│   ├── catalogs          # categorías, ubicaciones, unidades y proveedores
│   ├── batches           # lotes, estados y cantidad protegida
│   ├── dashboard         # indicadores, alertas y actividad real
│   ├── movements         # entradas, salidas y ajustes auditables
│   ├── products          # listado, filtros y mantenimiento de productos
│   ├── stock             # saldos agregados y mínimos
│   └── users             # cuentas, roles y ciclo de credenciales
├── layout                # shell, topbar y navegación por rol
└── shared                # componentes y modelos reutilizables
```

El módulo de productos consume la búsqueda paginada del backend y carga categorías, ubicaciones y unidades activas para el formulario. Todos los roles oficiales pueden consultar. Las acciones de alta, edición, reactivación y desactivación lógica solo aparecen para `ADMIN` y `SUPERVISOR`, en consonancia con Spring Security.

La pantalla unificada de catálogos permite consultar y filtrar categorías, ubicaciones, unidades y proveedores. Los roles de gestión pueden crear, editar, reactivar y desactivar registros; no se eliminan datos maestros físicamente.

Lotes ofrece listado paginado, filtro por producto, alta, edición y gestión de estados. La cantidad se muestra siempre como solo lectura y no forma parte de las peticiones de alta o edición.

Movimientos es el único módulo capaz de cambiar cantidades. Registra entradas, salidas y ajustes con los saldos anterior y posterior, responsable, lote, proveedor y motivo. Stock agrega esos saldos por producto y señala automáticamente los mínimos. Lotes, stock, movimientos y dashboard reaccionan a los eventos STOMP `STOCK_UPDATED` y vuelven a consultar al backend como fuente de verdad.

Usuarios separa consulta y administración por rol: `SUPERVISOR` puede consultar y `ADMIN` gestiona cuentas, roles, estado y contraseñas temporales. Cada mutación sensible revoca las sesiones activas del usuario afectado.

Auditoría proporciona consulta paginada por acción, entidad, actor y rango temporal, además de un detalle legible de las instantáneas anterior y posterior. Es un módulo estrictamente de lectura.

## Límites actuales

- Sin PWA ni modo offline.
- Sin QR ni códigos de barras.
- Sin recuperación autónoma de contraseña; la gestiona un administrador.
- Sin edición directa de cantidades de lote; el stock cambia exclusivamente mediante movimientos.
