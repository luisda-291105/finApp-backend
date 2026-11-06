# FinApp: guía del asistente

FinApp es una aplicación web para organizar información personal y financiera. El asistente de chat responde sobre el uso documentado de la aplicación, puede consultar los datos del usuario en sesión (contactos, grupos, correos, tarjetas, bolsillos contables y movimientos), y puede buscar información pública en Internet cuando la pregunta lo requiere. No puede modificar datos ni ejecutar operaciones financieras.

## Consultas de datos del usuario

Cuando el usuario está en sesión, el asistente puede responder preguntas simples sobre sus datos registrados:
- **Contactos**: "¿qué contactos tengo?", "muéstrame mis contactos", "¿cuántos contactos hay?"
- **Grupos**: "¿cuáles son mis grupos?", "¿qué grupos están activos?", "¿qué grupos están inactivos?"
- **Bolsillos contables**: "¿cuánto he gastado este mes?", "¿qué categoría concentra más gastos?", "compara mis ingresos y gastos por mes".
- **Correos**: "¿qué correos están activos?", "¿cuántas cuentas de correo tengo?", "¿qué cuentas Gmail tengo conectadas?"
- **Tarjetas**: "¿cuántas tarjetas tengo registradas?", "¿qué tarjetas hay?"
- **Finanzas**: puede comparar movimientos registrados por tipo, bolsillo, categoría y mes, y describir movimientos recientes. Los totales representan sumas de movimientos; no equivalen al saldo actual.

Estas consultas son de solo lectura. El asistente no puede crear, editar ni eliminar registros.

## Navegación del dashboard

El dashboard incluye Inicio, Transacciones, Tarjetas, Bolsillos, Contactos, Correos y Configuración. La navegación lateral permite cambiar de sección. En móvil, las opciones adicionales aparecen en el menú lateral.

## Tarjetas

La sección Tarjetas permite crear, consultar, editar y eliminar tarjetas del usuario activo. Solicita titular, número, vencimiento MM/AA y entidad financiera; no solicita ni almacena CVV. El número debe tener entre 14 y 19 dígitos y el vencimiento debe ser el mes actual o futuro. El backend conserva solo los últimos cuatro dígitos enmascarados; el detalle nunca muestra el PAN completo.

## Contactos y bolsillos

Contactos permite registrar y ver contactos asociados al usuario activo. Bolsillos organiza grupos y sus fuentes asociadas. El detalle del grupo muestra fuentes vinculadas y permite gestionar la información disponible en la interfaz. Chat e invitaciones de grupos que se indiquen como demostración no representan comunicaciones enviadas a otras personas.

## Correos

La sección Correos puede consultar mensajes de Gmail después de autorizar la conexión. La consulta usa el permiso de solo lectura. Los mensajes de Gmail no forman parte del contexto del agente de chat y no se envían a NVIDIA ni al proveedor de búsqueda web.

## Configuración y apariencia

Configuración permite revisar el perfil y gestionar fuentes registradas. El switch de Apariencia cambia entre tema claro y oscuro y conserva la preferencia en el navegador.

## Privacidad del asistente

No escribas nombres completos, correos, números de tarjeta, CVV, contraseñas u otros datos privados en el chat. Los mensajes se envían al modelo configurado en el backend; si se necesita información web, la consulta también se envía al proveedor de búsqueda. El asistente solo lee los datos básicos del usuario en sesión (contactos, grupos, correos y tarjetas) para responder consultas directas, y no ejecuta acciones financieras.
