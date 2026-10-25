# Cómo está construido y cómo se ha reforzado InVault

Estado de desarrollo: 25 de octubre de 2026. El código se prepara y prueba en una
rama de GitHub; la puesta en servicio real necesita un servidor y completar la
aceptación en dispositivos físicos.

La primera candidata se identifica como **0.1.0-rc.1**. El cierre de la fase 1 fija
su código mediante la etiqueta `v0.1.0-rc.1`, después de las comprobaciones y la
integración del PR #15. Las [notas de entrega](RELEASE_CANDIDATE_ES.md) explican
qué incluye, cómo obtenerla y qué falta para ponerla en servicio.

La fase 2 prepara **0.1.0-rc.2**: recorridos completos del navegador, carga limitada,
análisis de código e imágenes y correcciones detectadas por esos controles. La
etiqueta anterior permanece como referencia histórica.

## 1. Cada empresa conserva su propia información

La PWA es la aplicación que se instala desde el navegador. Se conecta al backend,
que aplica las reglas y consulta MySQL. MySQL permanece en el servidor. La base se
comparte entre los trabajadores y dispositivos de **una empresa**.

Para otra empresa se genera otro despliegue: base y volumen independientes,
credenciales distintas, clave de sesión propia y otro dominio. Así, reiniciar,
actualizar o recuperar una empresa no exige mezclar sus datos con los de otra.
El generador está en `deploy/new-company.ps1` y la guía en
[COMPANY_DEPLOYMENTS.md](COMPANY_DEPLOYMENTS.md).

## 2. Un reintento no debe descontar existencias dos veces

Antes de enviar un movimiento, la PWA crea una clave única y guarda temporalmente
esa petición en la sesión de la pestaña. El servidor guarda clave, movimiento,
saldo, auditoría y respuesta dentro de una **transacción**: o se confirman todos,
o no se confirma ninguno.

Si la conexión pierde la respuesta, repetir la misma clave devuelve el resultado
original. Con 10 unidades y una salida de 2, un reintento mantiene el saldo en 8.
Cambiar la cantidad reutilizando la clave se rechaza. Esta garantía también se
comprueba con dos envíos simultáneos y después de reiniciar el backend.
Más detalles en [MOVEMENT_IDEMPOTENCY.md](MOVEMENT_IDEMPOTENCY.md).

## 3. Los intentos de acceso tienen un presupuesto temporal

Antes de comprobar una contraseña, el backend reserva un intento en la base de
esa empresa. Los valores iniciales son 10 intentos por cuenta y 50 por dirección
de origen en ventanas de cinco minutos; cuentan los accesos correctos y fallidos.

Al agotar un límite, responde `429` y comunica los segundos de espera. La PWA
muestra una explicación y desactiva el botón durante ese tiempo. No guarda la
contraseña ni la reenvía al acabar el contador. Reiniciar el servidor o recargar
la página no borra el límite guardado en MySQL.

Para que dos peticiones simultáneas no gasten el mismo intento libre, MySQL
bloquea brevemente la fila del contador. Primero se confirma esa reserva y luego
se comprueba la contraseña; fallar la contraseña no devuelve el intento. El
vencimiento no se alarga por insistir durante la espera. Administración puede
consultar **Acceso limitado temporalmente** en la auditoría.
Guía: [LOGIN_SECURITY.md](LOGIN_SECURITY.md).

## 4. La dirección del cliente debe ser fiable

Las peticiones pasan por Caddy y nginx antes de llegar a Java. Estos proxies
limpian las cabeceras que alguien podría falsificar para aparentar otra dirección.
El backend y MySQL permanecen dentro de las redes privadas del despliegue.

La prueba `deploy/tests/login-security.ps1` levanta una empresa temporal con las
imágenes reales. Cambia cabeceras falsas en cada petición y comprueba que sigue
aplicándose el límite, que llega el tiempo de espera y que reiniciar Java no
permite eludirlo. La prueba usa un puerto local aleatorio y elimina sus recursos
temporales al terminar.

## 5. Las cantidades deben conservar su unidad de medida

Un saldo de 10 kg no puede pasar a significar 10 litros por editar un catálogo.
Al utilizar una unidad en un producto se fija su código, nombre y símbolo. Al
crear el primer lote se fija la unidad del producto, aunque ese lote tenga cero
existencias. Vaciar o consumir los lotes no elimina la protección.

Estas marcas se guardan en MySQL. Una migración prepara también los productos y
unidades que ya existían. La descripción y el estado siguen siendo editables;
para otra medida se crea otra unidad/producto. Las pantallas desactivan los campos
protegidos y el backend comprueba igualmente las peticiones antiguas o manipuladas.

Se han preparado pruebas con dos operaciones simultáneas: crear un producto
mientras se intenta renombrar su unidad, y crear un lote mientras se intenta
cambiar la unidad del producto. Una operación espera a la otra y después aplica
la regla con el estado confirmado. Guía:
[MEASUREMENT_IDENTITY.md](MEASUREMENT_IDENTITY.md).

## 6. Una copia debe poder recuperarse

La herramienta de copias obtiene una instantánea consistente de MySQL y la cifra
con `age`. El servidor que crea copias necesita la clave pública; la clave privada
para recuperarlas se conserva separada. La recuperación comprueba el cifrado y
la identidad de la empresa antes de importar a una base aislada.

La prueba elimina los registros de origen y recupera la copia cifrada. Compara
stock decimal, historial, auditoría, usuarios, texto con acentos, respuestas de
movimientos, límites de acceso y marcas de unidades. También comprueba que una
copia alterada, una clave incorrecta o una empresa distinta sean rechazadas.
Guía: [BACKUPS.md](BACKUPS.md).

También se ha preparado una comprobación de copias por empresa: detecta que
falte una copia, que sea demasiado antigua, que parezca truncada o que no coincida
con su réplica. Utiliza la fecha del nombre generado por la herramienta; copiar
un archivo antiguo no lo convierte en una copia reciente. Devuelve un resultado
JSON y un código de salida para la supervisión del servidor. La plantilla de
comprobación horaria todavía debe activarse en el alojamiento elegido y conectarse
a sus alertas. Esta comprobación no descifra ni sustituye una restauración de
ensayo. Guía: [BACKUP_MONITORING.md](BACKUP_MONITORING.md).

## 7. Cómo se comprueba y publica cada avance

Se ejecutan pruebas de Java, pruebas contra MySQL 8.4 real, pruebas de Angular,
revisión de formato y compilación de la PWA. Después se guarda un commit y se
sube a la rama del PR. GitHub repite sus comprobaciones del backend, frontend y
despliegue con contenedores.

Un resultado correcto permite revisar el cambio con evidencia. No significa que
la empresa ya esté usando esa versión: desplegarla y probar Windows, Android e
iPad son pasos posteriores. La lista está en [PWA_ACCEPTANCE.md](PWA_ACCEPTANCE.md).

## 8. Comprobar que el servicio puede trabajar

Se añadió una comprobación de disponibilidad que pregunta tanto al estado de Java
como a MySQL. Devuelve `UP` con HTTP 200 cuando puede atender peticiones y `DOWN`
con HTTP 503 cuando no puede hacerlo. El resultado público no contiene detalles
de la base ni credenciales. Java puede seguir vivo durante una caída de MySQL;
eso ya no se presenta como servicio disponible en el panel.

La PWA utiliza esta comprobación, espera como máximo ocho segundos y descarta
respuestas anteriores si se inicia otra consulta. Se prueba perder el acceso a
la base y recuperarlo, incluyendo detener el MySQL temporal detrás de los proxies,
sin reiniciar Java. Guía: [READINESS.md](READINESS.md).

## 9. Diagnosticar una empresa antes y después del arranque

El comando `deploy/check-company.ps1` lee su configuración y comprueba que proyecto,
base, credenciales, orígenes, redes y volumen correspondan a esa empresa. Antes
del primer arranque se puede usar solo para validar la configuración.

Con la empresa en marcha comprueba además sus cuatro contenedores, detecta ajustes
de base/JWT que no coincidan con el archivo y hace una consulta nueva de
disponibilidad. Puede incluir las comprobaciones de copias y réplica. Devuelve
JSON y un código de salida para que el mantenimiento pueda automatizarse.
No inicia servicios ni modifica existencias y su informe omite claves y contraseñas.
DNS, certificados y pruebas de dispositivos siguen requiriendo aceptación aparte.
Guía: [COMPANY_DIAGNOSTICS.md](COMPANY_DIAGNOSTICS.md).

## 10. Preparar una versión identificable para cada instalación

El nuevo constructor toma un commit confirmado y una carpeta sin cambios pendientes.
Construye las imágenes de Java y la PWA, y les añade ese identificador. Guarda un
descriptor con sus huellas y un archivo Compose que selecciona esa versión.
Las empresas pueden utilizar el mismo código con bases y configuraciones propias.

El diagnóstico puede comprobar que Java y la PWA ejecutan la revisión esperada.
Las copias obtienen ahora la revisión de la imagen desplegada, aunque se haya
descargado código más nuevo en la carpeta. Para instalaciones antiguas sin esa
marca hay que indicar su versión comprobada; una versión que contradiga la imagen
se rechaza. Así, la recuperación conserva una referencia fiel de la aplicación
que estaba atendiendo a la empresa. Guía: [RELEASES.md](RELEASES.md).

## 11. Comprobar la aplicación completa y sus dependencias

El navegador maneja los formularios reales con MySQL detrás de los proxies. Se
comprueba registrar cantidades decimales, recuperar un envío cuya respuesta se
perdió, aplicar permisos y recuperar el inventario después de desconectarse.
Los ensayos detectaron un socket que WebKit podía conservar y una etiqueta que
tapaba un selector; se corrigieron ambos comportamientos y se conservaron pruebas.
La hoja CSS se carga directamente: el cargador de CSS crítico generado por Angular
usaba un evento inline bloqueado por la política CSP de producción.

La carga repite 50 movimientos cuatro veces mientras se hacen 400 consultas. Debe
haber 50 movimientos y un saldo de 6.25, con cero errores y tiempos dentro de los
límites de CI. CodeQL revisa código y Trivy las imágenes: los hallazgos condujeron
a actualizar Spring Boot y paquetes del sistema. Los registros de errores también
excluyen rutas y mensajes de excepción para impedir líneas falsificadas y evitar
copiar datos enviados por el usuario.

Los informes conservan lo observado y las revisiones justificadas. Estos ensayos
no sustituyen las pruebas físicas ni el dimensionamiento del servidor. Procedimiento:
[QUALITY_VERIFICATION_ES.md](QUALITY_VERIFICATION_ES.md).

## Lo que queda para una puesta en marcha real

1. Fijar el servidor, dominio y almacenamiento externo de copias para cada empresa.
2. Configurar DNS/HTTPS y activar las copias periódicas y su supervisión.
3. Aplicar primero el backend y sus migraciones, y después publicar la PWA.
4. Hacer una recuperación de ensayo en ese entorno y completar la matriz física.
5. Completar el piloto y publicar la versión definitiva después de la aceptación
   de esta candidata. Cada candidata se integra y etiqueta después de verificar
   los controles del commit correspondiente.

La PWA necesita conexión para consultar datos actuales y registrar cambios. No se
ha implementado una base independiente en cada dispositivo ni sincronización de
movimientos realizados sin conexión.
