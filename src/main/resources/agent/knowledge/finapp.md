# FinApp: guía del asistente

FinApp es una aplicación web para organizar información personal y financiera. El asistente de chat responde sobre el uso documentado de la aplicación y puede consultar fuentes públicas de Internet cuando la pregunta requiere información actual. No puede consultar los registros de una cuenta ni realizar operaciones.

## Navegación del dashboard

El dashboard incluye Inicio, Transacciones, Tarjetas, Bolsillos, Contactos, Correos y Configuración. La navegación lateral permite cambiar de sección. En móvil, las opciones adicionales aparecen en el menú lateral.

## Tarjetas

La sección Tarjetas permite registrar y consultar tarjetas del usuario activo. El formulario solicita titular, número de tarjeta, vencimiento MM/AA, CVV y entidad financiera. El número debe tener entre 14 y 19 dígitos y el CVV exactamente 3. El vencimiento debe ser el mes actual o futuro. El detalle visual enmascara el número completo y no muestra el CVV.

## Contactos y bolsillos

Contactos permite registrar y ver contactos asociados al usuario activo. Bolsillos organiza grupos y sus fuentes asociadas. El detalle del grupo muestra fuentes vinculadas y permite gestionar la información disponible en la interfaz. Chat e invitaciones de grupos que se indiquen como demostración no representan comunicaciones enviadas a otras personas.

## Correos

La sección Correos puede consultar mensajes de Gmail después de autorizar la conexión. La consulta usa el permiso de solo lectura. Los mensajes de Gmail no forman parte del contexto del agente de chat y no se envían a NVIDIA ni al proveedor de búsqueda web.

## Configuración y apariencia

Configuración permite revisar el perfil y gestionar fuentes registradas. El switch de Apariencia cambia entre tema claro y oscuro y conserva la preferencia en el navegador.

## Privacidad del asistente

No escribas nombres completos, correos, números de tarjeta, CVV, contraseñas u otros datos privados en el chat. Los mensajes se envían al modelo configurado en el backend; si se necesita información web, la consulta también se envía al proveedor de búsqueda. El asistente no lee datos personales almacenados por FinApp y no ejecuta acciones financieras.
