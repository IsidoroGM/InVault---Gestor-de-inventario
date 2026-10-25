# InVault para Windows

## Instalar

1. Copia `InVaultSetup.exe` a un disco local y ábrelo. Acepta la solicitud de administrador de Windows.
2. Introduce el nombre de tu empresa y elige la carpeta del programa y la carpeta base para los datos.
3. Introduce el correo y una contraseña temporal para el usuario `admin`. Conserva esa contraseña hasta el primer acceso.
4. Revisa el resumen y pulsa **Instalar**. Espera a que el asistente confirme la disponibilidad de la aplicación y la base de datos.
5. Pulsa **Abrir InVault**, entra con `admin` y cambia su contraseña cuando lo solicite la aplicación.

En **Configuración inicial** puedes marcar **Preparar acceso desde tablets con Tailscale**.
Está desmarcado por defecto. El instalador incluye el MSI oficial firmado de Tailscale
y solo lo ejecuta si lo seleccionas; conserva un cliente ya instalado. La aplicación
local se comprueba antes de instalar ese componente opcional. Si este falla o requiere
reiniciar, InVault queda disponible en el PC y puedes reintentar desde Mantenimiento.
Al terminar, el botón **Tablets…** abre la configuración del acceso privado. La
vinculación de la cuenta y HTTPS necesitan internet, aunque la instalación local sea
sin conexión.

Necesitas Windows x64, versión 10 o posterior, y .NET Framework 4.8. El instalador incluye Java 25, MySQL 8.4, Caddy y el requisito Microsoft Visual C++ x64. No requiere conexión durante la instalación. Reserva inicialmente 2 GB para el programa y 3 GB para datos; la base y las copias crecerán con el uso.

Si Visual C++ solicita un reinicio, reinicia y abre de nuevo **el mismo instalador**. Se retomará la identidad de empresa ya registrada. El asistente no reinicia el equipo automáticamente.

La distribución de desarrollo carece de firma de editor. Windows puede mostrar «Editor desconocido». La huella SHA-256 identifica el archivo entregado; no sustituye una firma Authenticode. Para distribución pública, el responsable debe firmarlo con su certificado de editor y conservar la huella del archivo firmado.

## Dónde se guardan las cosas

El resumen muestra las rutas exactas. Por defecto:

| Contenido | Ubicación |
|---|---|
| Programa, Java, MySQL, interfaz y versiones anteriores | `C:\Program Files\InVault` |
| Empresa, configuración y secretos protegidos | `C:\ProgramData\InVault\<identificador>` |
| Archivos persistentes de MySQL | `<carpeta de empresa>\database` |
| Registros técnicos | `<carpeta de empresa>\logs` |
| Copias cifradas | `<carpeta de empresa>\backups` |

Se genera un identificador técnico aleatorio independiente del nombre visible. Renombrar la empresa desde el instalador conserva ese identificador y la misma base de datos. Los secretos se cifran con DPAPI de este equipo y sus archivos solo son accesibles para administradores, SYSTEM y el servicio LocalService. El programa se ejecuta como LocalService; no depende de una sesión abierta.

No muevas la carpeta `database` ni edites el identificador, puertos o registro de instalación manualmente. Esta edición admite una empresa por instalación de Windows. Detecta y mantiene la instalación existente; instalar otro nombre no crea automáticamente otra empresa.

## Abrir y detener

Utiliza **Inicio → InVault → InVault**. Por defecto abre `http://localhost:18740`. Los otros dos puertos son internos y escuchan exclusivamente en este ordenador.

El servicio `InVault-<identificador>` tiene inicio automático retrasado y recuperación tras fallo. Tras reiniciar Windows necesita tiempo para iniciar MySQL y Java. Si el acceso todavía no responde, abre **Mantenimiento de InVault → Iniciar / comprobar** y espera al resultado.

Cerrar el navegador no detiene la aplicación. **Mantenimiento → Detener** para el servicio y conserva todos los datos. Para volver a utilizarlo, pulsa **Iniciar / comprobar**.

## Copias y recuperación

En **Mantenimiento → Crear copia**, escribe una frase de recuperación de al menos 16 caracteres y elige dónde guardar el archivo `.ivbackup`. La copia contiene SQL, identidad de empresa, versión y secretos necesarios para recuperación. Usa AES-256-CBC con HMAC-SHA256 y una clave derivada mediante PBKDF2-SHA256; se verifica antes de confirmarla. Se conserva también una copia en la carpeta privada de la empresa.

Guarda la frase por separado y conserva copias fuera del ordenador. Si pierdes la frase, la copia no se puede descifrar. Esta fase proporciona copias manuales y previas a actualización; no activa un calendario automático ni una réplica externa.

**Extraer copia** autentica todo el archivo y verifica la huella del SQL antes de entregar una recuperación en una carpeta nueva. Esa carpeta contiene secretos en claro y tiene permisos restringidos; trátala como información privada. Nunca se importa SQL automáticamente en la empresa activa.

Para completar una recuperación operativa, un administrador debe preparar un MySQL aislado de la misma versión y una distribución del mismo `PackageId`, crear la base y el usuario indicados, importar `database.sql` y comprobar usuarios, catálogos, stock, movimientos, auditoría e historial Flyway. Debe volver a proteger `secrets.json` con DPAPI en el nuevo equipo y configurar un origen, puertos y servicio coherentes antes de sustituir el servidor. La automatización gráfica de esa importación y la recuperación completa entre equipos quedan fuera de esta primera distribución; la extracción no se presenta como una restauración operativa validada.

Los `.zip.age` del despliegue Docker mantienen su procedimiento de `deploy/BACKUPS.md`. No son intercambiables con `.ivbackup`.

## Actualizar o reinstalar

Ejecuta el nuevo `InVaultSetup.exe`. Detectará la instalación y conservará sus carpetas, identificador, puertos y credenciales. Para otro paquete pedirá una frase y creará una copia cifrada antes de detener la versión anterior y aplicar migraciones. Los componentes anteriores se conservan en `versions`.

No se admite volver a una versión anterior sobre una base migrada. Revertir requiere recuperar una copia en un entorno aislado y verificarla; sustituir únicamente el JAR no revierte una migración. Los cambios de versión de MySQL se bloquean hasta disponer de un procedimiento separado.

Si se interrumpe la instalación, ejecuta otra vez el mismo instalador. Los datos se conservan. Una inicialización de MySQL incompleta con archivos no reconocidos se detiene para revisión; el instalador no borra esos archivos para «empezar de cero».

## Desinstalar

Abre **Configuración de Windows → Aplicaciones → InVault → Desinstalar**, y pulsa **Desinstalar** en el mantenimiento. Se detienen los procesos, se elimina el servicio, los accesos y los archivos del programa identificados por sus manifiestos. Los archivos añadidos o modificados externamente se conservan.

La base, configuración y copias se conservan en la carpeta de empresa. También se conserva la referencia de identidad en el registro para que una reinstalación recupere la empresa correcta. No hay una opción de borrado definitivo de inventario en el asistente.

## Otros ordenadores y tablets

Este paquete instala el servidor de la empresa en este PC. Los demás dispositivos deben conectarse a ese mismo servidor para compartir inventario; no necesitan una base propia. `localhost` en una tablet apunta a la tablet, no al PC servidor.

El acceso inicial está limitado al ordenador anfitrión. Para conectar tablets desde
casa, el almacén u otra conexión, abre **Mantenimiento de InVault → Conectar tablets…**:

1. Pulsa **Instalar Tailscale** si todavía no está instalado. Se usa el MSI oficial
   incluido, verificado por su huella al construir y antes de ejecutarlo. No necesitas
   PowerShell. Se conserva cualquier instalación y cuenta anterior de Tailscale.
2. Pulsa **Abrir Tailscale**, abre su icono junto al reloj e inicia sesión con la
   cuenta/red privada de tu empresa. Cada empresa debe gestionar sus propios
   dispositivos y permisos; el instalador no contiene cuentas ni claves de acceso.
3. Pulsa **Configurar HTTPS**. En el panel DNS de Tailscale activa MagicDNS y los
   certificados HTTPS. Lee los avisos del proveedor: el nombre del certificado
   del servidor se registra públicamente, aunque el acceso siga siendo privado.
   Revisa los permisos de tu red privada para limitar el acceso a los dispositivos
   de tu empresa. Esta autorización la completa su administrador.
4. Pulsa **Activar acceso**. Se configura Tailscale Serve, se reinicia InVault para
   aplicar la dirección permitida y se espera la respuesta HTTPS, dando tiempo a
   la emisión inicial del certificado. La operación
   habilita el modo desatendido de Tailscale para mantener conectado el PC tras
   cerrar sesión en Windows. El nombre y la identidad del PC quedan registrados.
   Se conserva el acceso local y el mismo inventario. La activación reinicia el
   servicio: espera a que termine antes de continuar trabajando.
5. Copia la dirección `https://<pc>.<red>.ts.net`. En cada tablet instala **Tailscale**
   desde Google Play, vincúlala a la red de tu empresa o autoriza su acceso y conecta
   la VPN. Abre la dirección en Chrome, entra con un usuario de InVault e instala
   la aplicación web desde el menú del navegador. El usuario de InVault y la cuenta
   de Tailscale son accesos diferentes. La PWA no sustituye al cliente VPN de Android.
6. Usa **Comprobar conexión** y prueba desde la tablet el inicio de sesión, un
   movimiento de stock y su actualización en el PC. Repite la prueba después de
   reiniciar Windows y al compartir conexión desde un móvil.

La dirección se mantiene al cambiar de wifi, siempre que no cambies el nombre del
PC, su identidad o el nombre de la red privada en Tailscale. No necesitas reservar
la IP del router para este modo de acceso. El PC debe permanecer encendido, sin
suspensión, con InVault y Tailscale funcionando. Los dispositivos necesitan
comunicación a través de Tailscale; planifica conexión a internet en ambos.
Las credenciales de los dispositivos pueden caducar y requerir iniciar sesión
otra vez. El asistente no desactiva su caducidad ni cambia permisos de la red.
Si hay otra VPN activa, comprueba su compatibilidad con Tailscale.

**Desactivar acceso** retira únicamente la ruta reconocida de InVault y su origen
permitido. Si Tailscale está desconectado o la ruta fue modificada, se retira el
origen y se indica que la ruta requiere revisión. No se resetean otras rutas, ni
se cierra la cuenta, ni se desinstala el cliente Tailscale. Se conserva el modo
desatendido del cliente: puedes gestionarlo en sus preferencias. Desinstalar InVault
retira también su acceso registrado antes de detener el servicio y conserva la
configuración y los datos de la empresa.

Si cambias de cuenta/red privada o renombras el PC, desactiva el acceso anterior,
vuelve a activarlo y actualiza el acceso instalado en las tablets. Una ventana
de mantenimiento antigua debe cerrarse y abrirse de nuevo si otra operación ha
cambiado la configuración.

El asistente usa **Serve privado** y rechaza una ruta existente en el puerto 443
que no reconozca como propia, o una configuración pública Funnel. Las conexiones
locales de Caddy, Spring Boot y MySQL siguen escuchando en el PC. Los despliegues
HTTPS con otras topologías permanecen documentados en
`deploy/COMPANY_DEPLOYMENTS.md` y `deploy/PWA_ACCEPTANCE.md`.

## Resolver incidencias

- **Puerto ocupado:** elige otros puertos en una instalación inicial. En una instalación existente identifica la aplicación que ocupa los puertos registrados.
- **Permiso insuficiente:** abre el instalador como administrador y elige carpetas locales accesibles. No se admiten carpetas de red o con enlaces/redirecciones.
- **Sin espacio:** libera espacio en los discos indicados y reintenta con el mismo instalador.
- **No inicia:** consulta la carpeta `logs`; no compartas configuraciones ni copias que contengan secretos.
- **Reinicio pendiente:** termina las actualizaciones de Windows y vuelve a ejecutar el instalador.

El instalador solo anuncia éxito después de comprobar readiness del backend con MySQL y la entrega de la interfaz a través del servidor local.
