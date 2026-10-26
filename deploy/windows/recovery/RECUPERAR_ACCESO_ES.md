# Restablecer el acceso de admin

La contraseña anterior no se puede recuperar en texto. Esta herramienta establece una contraseña temporal nueva para la cuenta `admin` de la empresa instalada en el PC servidor.

1. Abre `InVaultRestablecerAcceso.exe` en el PC donde está instalado el servidor y acepta la solicitud de administrador de Windows.
2. Comprueba el nombre de la empresa que muestra la ventana.
3. Introduce dos veces una nueva contraseña temporal de al menos 12 caracteres y pulsa **Restablecer acceso**.
4. Espera a que vuelva a iniciar InVault. Pulsa **Abrir InVault** y entra con el usuario `admin` y la contraseña temporal que acabas de elegir.
5. La aplicación pedirá cambiarla por una contraseña definitiva. Guárdala en tu gestor de contraseñas.

La aplicación se detendrá brevemente para cerrar las sesiones anteriores. Se conserva el inventario y el resto de usuarios. No hace falta reinstalar ni borrar carpetas. No introduzcas la frase de cifrado de las copias: esa frase y la contraseña de la cuenta son distintas.

La herramienta solo sirve para la instalación nativa de Windows y requiere permisos de administrador local. No habilita un acceso remoto sin autenticar ni un enlace de recuperación por correo. Una cuenta `admin` desactivada o sin el rol ADMIN activo se conserva y la recuperación se rechaza.

## Alcance técnico

Se identifica la instalación a través del registro y se exige una base previamente inicializada. Se para el servicio, se inicia únicamente su MySQL local y se ejecuta una transacción: cambio de hash BCrypt, incremento de versión de tokens, obligación de cambiar contraseña, auditoría PASSWORD_RESET y limpieza de los presupuestos de acceso de esa cuenta y de las direcciones de bucle local. Si falla la auditoría se revierte la transacción. Después se para ese MySQL y se reinicia el servicio normal.

Las contraseñas se transfieren al proceso Java por entrada estándar UTF-8. No se incluyen en argumentos, archivos temporales o salida. Las bibliotecas criptográficas y JDBC se obtienen del backend instalado; el usuario no necesita instalar Java ni descargar componentes. Los temporales contienen únicamente el helper y bibliotecas, con los permisos heredados de la carpeta privada de la empresa.

Para construir: `deploy/windows/recovery/build-recovery.ps1` con un JDK 25 en el equipo de desarrollo. El EXE de recuperación se entrega separado del instalador principal y no sustituye sus componentes.

Verificación del 26 de octubre de 2026: 17 comprobaciones sobre una empresa desechable, con MySQL y backend reales. Incluyen conservación del inventario, rechazo de cuentas desactivadas, reversión ante fallo de auditoría, nueva contraseña Unicode válida, revocación de la sesión anterior y cambio obligatorio de contraseña. La empresa real no se modificó durante las pruebas. El recorrido UAC/servicio de la instalación real se realiza cuando su administrador usa esta ventana.
