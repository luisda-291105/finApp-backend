"""Genera un JWT de sesión para probar la API desde Swagger UI o curl.

Uso:
    python docs/generar_token_swagger.py <idUsuario> [horas_validez]

La clave se toma de la variable de entorno FINAPP_JWT_SECRET o, si no existe,
del archivo .env del repositorio (mismo formato que el backend: Base64 de 256 bits).

El token reproduce exactamente los claims que emite JwtTokenService:
    sub, jti, iat, exp, expiraEn (ms), actividad (ms)
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import sys
import time
import uuid
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
RUTAS_DOTENV = [RAIZ / ".env", RAIZ.parent / ".env"]
EXPIRACION_ABSOLUTA_HORAS = 8


def leer_secreto() -> str:
    secreto = os.environ.get("FINAPP_JWT_SECRET", "").strip()
    if secreto:
        return secreto
    for ruta in RUTAS_DOTENV:
        if not ruta.is_file():
            continue
        for linea in ruta.read_text(encoding="utf-8", errors="ignore").splitlines():
            limpia = linea.strip()
            if not limpia or limpia.startswith("#") or "=" not in limpia:
                continue
            clave, valor = limpia.split("=", 1)
            if clave.strip() == "FINAPP_JWT_SECRET":
                valor = valor.strip().strip('"').strip("'")
                if valor:
                    return valor
    raise SystemExit("No se encontro FINAPP_JWT_SECRET (ni en el entorno ni en .env).")


def base64url(datos: bytes) -> str:
    return base64.urlsafe_b64encode(datos).rstrip(b"=").decode("ascii")


def firmar(claims: dict, secreto: str) -> str:
    clave = base64.b64decode(secreto)
    cabecera = {"alg": "HS256", "typ": "JWT"}
    partes = [
        base64url(json.dumps(cabecera, separators=(",", ":")).encode("utf-8")),
        base64url(json.dumps(claims, separators=(",", ":")).encode("utf-8")),
    ]
    firma = hmac.new(clave, ".".join(partes).encode("ascii"), hashlib.sha256).digest()
    partes.append(base64url(firma))
    return ".".join(partes)


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit("Uso: python docs/generar_token_swagger.py <idUsuario> [horas_validez]")
    id_usuario = sys.argv[1].strip()
    horas = int(sys.argv[2]) if len(sys.argv) > 2 else EXPIRACION_ABSOLUTA_HORAS
    ahora_ms = int(time.time() * 1000)
    expira_en_ms = ahora_ms + horas * 3600 * 1000
    claims = {
        "sub": id_usuario,
        "jti": str(uuid.uuid4()),
        "iat": int(time.time()),
        "exp": int(expira_en_ms / 1000),
        "expiraEn": expira_en_ms,
        "actividad": ahora_ms,
    }
    print(firmar(claims, leer_secreto()))


if __name__ == "__main__":
    main()
