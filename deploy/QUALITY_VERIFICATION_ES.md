# Verificación ampliada de InVault — fase 2

Los controles se ejecutan con fuentes confirmadas, imágenes identificadas y una
empresa temporal. La candidata v0.1.0-rc.1 permanece fijada en su etiqueta; los
cambios de calidad se revisan en una rama posterior.

## Navegador contra la aplicación real

Deployment CI instala Playwright desde el lockfile y ejecuta 24 casos: ocho por
proyecto en Chromium de escritorio, Chromium con perfil Android y WebKit con perfil
iPad. Se sirven el frontend compilado y la API real mediante Caddy y nginx, con MySQL
real. Las API preparan catálogos y cuentas desechables; los formularios y permisos se
comprueban en el navegador. No se simulan respuestas satisfactorias del backend.

Los recorridos cubren cambio de contraseña temporal, revocación y cierre de sesión;
alta de producto y lote; entrada y salida con saldo decimal; acceso a auditoría;
respuesta perdida tras confirmar un movimiento, recarga y reintento con la misma
clave; permisos de los cuatro roles; actualizaciones entre contextos independientes;
recuperación de cambios perdidos y bloqueo de envíos sin conexión.

La pérdida de respuesta se provoca después de enviar la petición real al backend.
Se comprueba después la cantidad y el número de movimientos guardados. Las pruebas
de reconexión esperan a STOMP y comprueban el valor mostrado sin recargar la página.

Estos ensayos detectaron y motivaron dos correcciones: abrir una conexión nueva al
recuperar Internet aunque el navegador conserve el WebSocket anterior, y permitir
que las pulsaciones atraviesen las etiquetas decorativas sobre selectores en WebKit.
Las pruebas utilizan la interacción normal, sin forzar clics sobre elementos tapados.

Los perfiles móviles son emulación. La instalación física, certificados públicos y
actualización del service worker siguen en [PWA_ACCEPTANCE.md](PWA_ACCEPTANCE.md).
El service worker se bloquea en este conjunto para controlar la inyección del fallo
de red. No se atribuye a estas pruebas la aceptación física o de instalación PWA.

## Carga concurrente limitada

Después del navegador, Node ejecuta 400 lecturas y 200 envíos de movimientos de
forma concurrente: 10 trabajadores de lectura y 10 de escritura. Los 200 envíos
representan 50 movimientos únicos, repetidos cuatro veces con su clave original.
Cada movimiento añade 0.125; el saldo final debe ser exactamente 6.25 y el historial
debe contener 50 movimientos. Cada reintento debe conservar la respuesta original.

El informe load.json registra duración, errores y percentil 95 de lectura/escritura.
Los límites de regresión de CI son p95 de 3 segundos para lecturas, 5 para escrituras,
cero errores y duración total de 120 segundos. No representan capacidad comercial
ni un compromiso de servicio: el dimensionamiento necesita medir el servidor real.

El objetivo debe ser HTTP local, con puerto explícito y un identificador de ensayo.
El runner crea un proyecto/volumen propios y solo publica en 127.0.0.1. Sus límites
de login se amplían únicamente en el override temporal para permitir las pruebas;
la política de producción sigue comprobándose en el ensayo de login independiente.
Las variables se restauran y solo se eliminan los recursos del proyecto temporal.

## Análisis de seguridad

Security CI analiza Java compilado y JavaScript/TypeScript con las consultas
security-extended de CodeQL. Conserva SARIF durante 14 días y falla ante resultados
con puntuación de seguridad igual o superior a 7. No requiere activar la subida de
alertas al panel de seguridad; los informes están en los artefactos del workflow.

Deployment CI utiliza Trivy 0.76.1 para analizar las dos imágenes reales. El informe
JSON conserva todas las severidades y los hallazgos sin corrección. El control
bloquea vulnerabilidades HIGH/CRITICAL con versión corregida disponible. Exige
inventario del sistema operativo y, para el backend, dependencias Java del JAR;
un análisis vacío o un formato desconocido no puede dar la imagen por aprobada.

El frontend también conserva la auditoría npm de las dependencias bloqueadas. Las
acciones nuevas de seguridad y conservación de informes se fijan por SHA. No se
añaden exclusiones de vulnerabilidades de dependencias o imágenes. Los doce casos de la política
comprueban rechazos y umbrales con informes de prueba.

El aviso CodeQL sobre CSRF desactivado tiene una revisión de diseño específica en
codeql-reviewed-findings.json. La API exige JWT explícitos y no autentica cookies,
sesiones, formularios ni HTTP Basic. Dos pruebas de la cadena real rechazan
credenciales ambientales, login de otro origen y formularios. La revisión solo se
aplica a esa regla y a la huella SHA-256 del archivo SecurityConfig.java revisado;
si cambia el archivo, el hallazgo vuelve a bloquear. El informe conserva el aviso
y su justificación. Añadir autenticación mediante cookies exige rediseñar CSRF.
Referencia: [CSRF y credenciales del navegador](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html).

Un resultado correcto se refiere al código, imágenes, reglas y base de
vulnerabilidades utilizados en esa ejecución. Se deben revisar los informes
completos, incluidos los hallazgos no bloqueantes y los que aún no tienen arreglo.

El primer análisis de imágenes detectó correcciones pendientes en OpenSSL,
Jackson, Tomcat, Spring MVC y paquetes de Alpine. Se actualiza la plataforma a
Spring Boot 4.1.2 y las bases oficiales de Java/nginx/Node, con actualizaciones de
paquetes del sistema al construir. El informe de cada nueva imagen determina si
las correcciones son suficientes; no se aprueba una imagen por cambiar su etiqueta.

## Ejecución y evidencia

Con Docker, Git, PowerShell 7.3+ y Node 24, desde un checkout limpio:

```powershell
npm --prefix frontend ci
npm --prefix frontend exec -- playwright install chromium webkit
$release = ./deploy/build-release.ps1
./deploy/tests/quality-stack.ps1 -BackendImage $release.BackendImage -FrontendImage $release.FrontendImage -ExpectedRevision $release.Revision -ReportDirectory ./quality-results
```

En Linux, Playwright puede necesitar sus dependencias del sistema; CI las instala
con --with-deps. Ejecutar el runner únicamente para sus empresas desechables. No
se utilizan credenciales ni datos de empresas existentes.

Los artefactos quality-reports contienen JUnit, capturas en caso de fallo,
load.json y los análisis de imágenes. No se guardan trazas, vídeos, estados de
autenticación ni archivos privados de empresa. Los informes locales quality-results
se excluyen de Git y del contexto Docker. Un fallo detiene el control y conserva
la evidencia disponible para corregirlo antes de integrar.

Referencias oficiales: [proyectos de Playwright](https://playwright.dev/docs/test-projects),
[análisis de vulnerabilidades de Trivy](https://trivy.dev/docs/latest/scanner/vulnerability/)
y [modos de compilación de CodeQL](https://docs.github.com/en/code-security/how-tos/find-and-fix-code-vulnerabilities/manage-your-configuration/codeql-for-compiled-languages).
