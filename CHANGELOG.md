# Historial de versiones de InVault

## 0.1.0-rc.2 — 2026-10-25

Candidata con verificación ampliada y correcciones de la fase 2. Mantiene la
etiqueta anterior como referencia histórica; para instalaciones nuevas se utiliza
esta revisión y se conservan las imágenes identificadas por su SHA.

- 24 recorridos de navegador contra el stack real: escritorio, Android emulado y
  WebKit con perfil iPad; contraseñas, roles, productos/lotes, movimientos,
  reintentos tras respuesta perdida, sincronización y desconexión.
- Carga limitada: 400 lecturas y 200 envíos concurrentes para 50 movimientos únicos;
  saldo/historial exactos, respuestas idempotentes y métricas p95.
- CodeQL Java/JavaScript, Trivy de imágenes reales y 12 controles de políticas.
  Los informes completos se conservan; CSRF tiene una revisión exacta ligada al
  SHA-256 de su configuración y dos regresiones sobre credenciales del navegador.
- La reconexión recupera inventario incluso si WebKit conserva un socket antiguo;
  las etiquetas decorativas permiten pulsar los selectores.
- Los errores inesperados registran tipo y marcos del servidor con URI saneada,
  sin copiar mensajes de excepción que puedan contener datos enviados.
- Spring Boot 4.1.2: Jackson 2.21.7/3.1.7, Tomcat 11.0.27 y Spring MVC 7.0.10.
  Bases oficiales actualizadas de Java, Node y nginx, con parches del sistema.

La entrega exige los cuatro workflows correctos, incluidos navegador, carga y
políticas de seguridad, antes de integrar y etiquetar. Guía:
[verificación de fase 2](deploy/QUALITY_VERIFICATION_ES.md). Continúan pendientes
el entorno real, aceptación física PWA, operación y piloto.

## 0.1.0-rc.1 — 2026-10-25

Primera candidata del MVP completo. Etiqueta: `v0.1.0-rc.1`.
Backend y frontend declaran la versión `0.1.0-rc.1`.

### Funcionalidad incluida

- Productos, catálogos, proveedores, ubicaciones, lotes, existencias y movimientos.
- Usuarios, cuatro roles, cambio obligatorio de contraseña y auditoría de acciones.
- PWA en español, diseño para tabletas, actualizaciones explícitas y avisos de conexión.
- Actualización de inventario entre dispositivos mediante eventos después del commit.
- Despliegues con base, volumen, credenciales, JWT y dominio propios por empresa.

### Integridad y operación

- Clave obligatoria `Idempotency-Key` para movimientos: un reintento conserva un
  solo efecto y su respuesta, incluso después de reiniciar el servidor.
- Presupuestos de acceso persistentes por cuenta/origen, HTTP 429 y contador en
  la PWA; protección frente a cabeceras de origen falsificadas en los proxies.
- Identidad de unidades usadas y medida de productos con lotes protegidas, aunque
  el saldo sea cero; marcas persistentes y bloqueos de filas bajo concurrencia.
- Copias cifradas con age, recuperación aislada, réplica y supervisión de antigüedad.
- Disponibilidad que comprueba Java y MySQL, y diagnóstico de empresa sin secretos.
- Imágenes construidas desde el código confirmado, con revisión OCI, descriptor y
  overlay Compose. Las copias conservan la revisión de la imagen desplegada.

### Actualización y compatibilidad

- Flyway aplica V1–V6 a la base de cada empresa. Hacer una copia antes de actualizar
  y aplicar el backend/migraciones antes de permitir escrituras desde la nueva PWA.
- Los clientes antiguos que no envíen `Idempotency-Key` deben actualizarse.
- No mantener backends antiguos escribiendo durante la transición de unidades.
- Las imágenes antiguas sin revisión necesitan un `-ReleaseCommit` verificado para
  crear copias. La herramienta ya no toma el checkout como versión instalada.
- Una vuelta atrás de código no revierte la base. Usar el procedimiento de copia y
  recuperación; no improvisar una bajada de versión sobre un esquema migrado.

### Verificación de la candidata

La entrega exige los tres workflows correctos para el código integrado: backend,
frontend y despliegue. La batería incluye 113 pruebas backend (24 en MySQL real),
83 frontend, 34 comprobaciones de diagnóstico, 9 de identidad de versión y 16 de
supervisión de copias, además de los ensayos Docker de recuperación y proxies.

### Pendiente antes de la entrega definitiva

Pruebas completas en navegador, análisis de seguridad ampliado y carga; servidor y
DNS/HTTPS; activación de copias externas/alertas; restauración en el entorno elegido;
aceptación física en Windows, Android e iPad y piloto de uso real.

La base vive en el servidor de cada empresa. La PWA requiere conexión para operar;
no incorpora sincronización de movimientos sin conexión. Esta entrega etiqueta
fuentes; las imágenes se construyen siguiendo [RELEASES.md](deploy/RELEASES.md).

Procedimiento y alcance: [entrega de la candidata](deploy/RELEASE_CANDIDATE_ES.md).
