# Mensajería privada por Telegram

El chat de cada cuenta de FinApp se vincula a una conversación privada con el bot. La clave del bot nunca se entrega al frontend.

## Configuración

El backend lee estas variables de `finApp-backend/.env`:

- `TELEGRAM_BOT_TOKEN`: token privado creado por BotFather.
- `NOMBRE_BOT_TELEGRAM`: nombre de usuario del bot, con o sin `@`.
- `TELEGRAM_POLL_TIMEOUT_SECONDS`: opcional; espera de long polling, por defecto 5 segundos.
- `TELEGRAM_POLL_DELAY_MS`: opcional; pausa entre consultas, por defecto 1000 ms.

`NOMBRE_BOT_TELEGRAM` debe ser el username público usado en `https://t.me/<username>`, no el nombre visible del bot.

## Vincular una cuenta

1. Abre **Configuración → Mensajería por Telegram** en FinApp y elige **Vincular Telegram**.
2. Abre el enlace generado y pulsa **Iniciar** en la conversación privada del bot.
3. El backend valida el código de un solo uso y guarda el chat privado en la cuenta autenticada.
4. El asistente personal responde los mensajes entrantes de texto y el historial queda disponible desde **Grupos → Chat grupal**.

Los códigos expiran a los 10 minutos y se invalidan al vincularse. Si se reinicia el backend antes de completar el vínculo, genera otro enlace.

## Persistencia y recepción

Aplica `sql/067_mensajeria_telegram.sql` y `sql/068_respuestas_asistente_telegram.sql` en instalaciones que no ejecuten el esquema JPA con `ddl-auto=update`. La tabla `telegram_mensaje` guarda el historial por usuario y rol; el identificador privado queda en `usuario.telegram_chat_id`.

La recepción usa el método `getUpdates` de la Bot API. Telegram no permite usar `getUpdates` mientras haya un webhook activo para el mismo bot; elimina ese webhook en la configuración del bot si la pantalla informa que no llegan actualizaciones. [Referencia oficial de `getUpdates`](https://core.telegram.org/bots/api#getupdates). El enlace de vinculación usa el parámetro `start` de los enlaces profundos de Telegram. [Referencia oficial de enlaces profundos](https://core.telegram.org/bots/features#deep-linking).
