# Aceptación del acceso privado desde tablets

La verificación automatizada no sustituye estas pruebas. Estado inicial: pendientes,
requieren un Windows de prueba, una cuenta/red privada de Tailscale y una tablet real.
No registrar contraseñas, claves de acceso, inventarios privados ni enlaces de login
temporales en los resultados.

- [ ] Instalar con la opción de tablets desmarcada: Tailscale no se instala y el acceso
  local funciona. Repetir seleccionándola y comprobar el MSI oficial firmado.
- [ ] Probar en un equipo con Tailscale ya instalado y vinculado: se conservan su cuenta,
  ajustes, versión y rutas ajenas. Cancelar o fallar su instalación conserva InVault local.
- [ ] Vincular el PC manualmente a la red de prueba y habilitar MagicDNS/HTTPS tras
  revisar los avisos. Comprobar que InVault no muestra éxito antes de responder por HTTPS.
- [ ] Crear una ruta ajena en 443, una sesión de Serve en primer plano y una ruta Funnel
  de prueba: el asistente rechaza cada conflicto y no altera esas configuraciones.
- [ ] Activar desde Mantenimiento con el puerto libre: comprobar el origen exacto,
  el proxy a la interfaz local y la ausencia de Funnel en la configuración resultante.
- [ ] Instalar Tailscale en Android y autorizar la tablet. Abrir InVault por su dirección
  HTTPS, iniciar sesión e instalar la PWA. Verificar stock compartido y actualizaciones
  en tiempo real entre PC y tablet. Verificar rechazo para un dispositivo no autorizado.
- [ ] Confirmar que los puertos de MySQL y Spring Boot no aceptan conexiones desde LAN.
- [ ] Reiniciar Windows sin iniciar sesión: comprobar que InVault y Tailscale permanecen
  disponibles desde la tablet, y que el modo desatendido está activado.
- [ ] Conectar el PC mediante el punto de acceso de un móvil y la tablet mediante otra
  conexión: comprobar que la misma dirección HTTPS y el inventario siguen funcionando.
- [ ] Desconectar internet, suspender el PC o desconectar Tailscale: verificar el error
  de conexión y la protección existente contra operaciones sin conexión.
- [ ] Renombrar el dispositivo o cambiar de red privada: el asistente pide desactivar y
  volver a vincular, conservando el inventario y sin apropiarse de otras rutas.
- [ ] Desactivar acceso conectado y desconectado: el origen se retira y las rutas
  ajenas se conservan; cuando no pueda retirar su ruta, el diagnóstico lo indica.
- [ ] Actualizar una instalación local y otra con acceso privado: conservar empresa,
  datos, cuenta Tailscale, dirección privada y acceso de la tablet.
- [ ] Desinstalar InVault: conservar base y copias, retirar su ruta reconocida y
  conservar el cliente, cuenta y otros servicios de Tailscale.

Anotar versión de Windows/Android/Tailscale, PackageId, fecha, resultado y evidencias
sin secretos. No marcar esta lista completada basándose solo en las pruebas simuladas.
