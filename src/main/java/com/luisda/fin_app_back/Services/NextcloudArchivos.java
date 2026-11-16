package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.AlmacenamientoArchivoDTO;
import com.luisda.fin_app_back.DTO.AlmacenamientoPermisoDTO;
import com.luisda.fin_app_back.Model.MAlmacenamientoConexion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * @uso               Operaciones de archivos contra un servidor Nextcloud vía WebDAV
 * @capa              Servicio
 * @responsabilidades Listar (PROPFIND), leer (GET), subir (PUT), organizar (MKCOL/MOVE/COPY),
 *                    papelera (trashbin), lote ZIP y errores coherentes con la capa de almacenamiento
 * @datos             Autenticación Basic con la credencial de aplicación cifrada; nunca en logs ni respuestas
 * @dependencias      RestClient (WebDAV), SAlmacenamientoCifrado, SArchivosPorConexion
 * @usadoPor          CAlmacenamientoArchivos (a través del router)
 */
@Service
public class NextcloudArchivos implements SAlmacenamientoArchivos {

    private static final String NS_DAV = "DAV:";

    private final SAlmacenamientoCifrado cifrado;
    private final RestClient clienteHttp;

    public NextcloudArchivos(SAlmacenamientoCifrado cifrado, RestClient.Builder constructorCliente) {
        this.cifrado = cifrado;
        this.clienteHttp = constructorCliente.build();
    }

    @Override public String proveedor() { return "NEXTCLOUD"; }

    @Override
    public PaginaArchivos listar(MAlmacenamientoConexion conexion, String carpetaId, String vista, String busqueda,
                                 String orden, String pagina) throws GeneralSecurityException {
        boolean papelera = "PAPELERA".equalsIgnoreCase(vista);
        validarVista(vista);
        String carpeta = carpetaId == null || carpetaId.isBlank() || "root".equalsIgnoreCase(carpetaId)
                || "RAIZ".equalsIgnoreCase(carpetaId) ? "" : quitarSlash(decodificarId(carpetaId));
        String ruta = papelera ? rutaPapelera(conexion) : rutaArchivos(conexion) + carpeta;
        List<Element> respuestas = propfind(conexion, ruta, papelera);
        List<AlmacenamientoArchivoDTO> archivos = new ArrayList<>();
        String filtroBusqueda = busqueda == null ? "" : busqueda.toLowerCase(Locale.ROOT);
        for (Element respuesta : respuestas) {
            if (rutaIgual(textoHijo(respuesta, "href"), ruta)) continue; // la propia carpeta consultada
            String href = textoHijo(respuesta, "href");
            if (papelera && href != null && !href.contains("/trashbin/")) continue;
            AlmacenamientoArchivoDTO dto = aDto(respuesta);
            if (!filtroBusqueda.isBlank() && !dto.nombre().toLowerCase(Locale.ROOT).contains(filtroBusqueda)) continue;
            archivos.add(dto);
        }
        ordenar(archivos, orden);
        String idCarpetaActual = papelera ? "" : (carpeta.isBlank() ? "root" : carpeta);
        return new PaginaArchivos(archivos, null, idCarpetaActual);
    }

    @Override
    public AlmacenamientoArchivoDTO metadatos(MAlmacenamientoConexion conexion, String fileId)
            throws GeneralSecurityException {
        return propfindUno(conexion, rutaDeId(conexion, fileId));
    }

    @Override
    public RecursoContenido contenido(MAlmacenamientoConexion conexion, String fileId, boolean descargar)
            throws GeneralSecurityException {
        return flujoArchivo(conexion, fileId);
    }

    @Override
    public RecursoContenido previsualizacion(MAlmacenamientoConexion conexion, String fileId)
            throws GeneralSecurityException {
        return flujoArchivo(conexion, fileId);
    }

    @Override
    public AlmacenamientoArchivoDTO crearCarpeta(MAlmacenamientoConexion conexion, String carpetaId, String nombre)
            throws GeneralSecurityException {
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("Indica el nombre de la carpeta.");
        String padre = carpetaId == null || carpetaId.isBlank() || "root".equalsIgnoreCase(carpetaId) ? "" : quitarSlash(decodificarId(carpetaId));
        String ruta = rutaArchivos(conexion) + (padre.isBlank() ? "" : padre + "/") + UriUtils.encodePathSegment(nombre, StandardCharsets.UTF_8);
        enviar(conexion, HttpMethod.valueOf("MKCOL"), URI.create(urlServidor(conexion) + ruta), null, null);
        return metadatos(conexion, rutaRelativaDe(rutaArchivos(conexion), ruta));
    }

    @Override
    public AlmacenamientoArchivoDTO subir(MAlmacenamientoConexion conexion, String carpetaId, String nombreArchivo,
                                          String mime, byte[] contenido) throws GeneralSecurityException {
        if (contenido == null || contenido.length == 0) throw new IllegalArgumentException("El archivo está vacío.");
        String padre = carpetaId == null || carpetaId.isBlank() || "root".equalsIgnoreCase(carpetaId) ? "" : quitarSlash(decodificarId(carpetaId));
        String ruta = rutaArchivos(conexion) + (padre.isBlank() ? "" : padre + "/")
                + UriUtils.encodePathSegment(nombreSeguro(nombreArchivo), StandardCharsets.UTF_8);
        enviar(conexion, HttpMethod.PUT, URI.create(urlServidor(conexion) + ruta), contenido,
                mime == null || mime.isBlank() ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(mime));
        return metadatos(conexion, rutaRelativaDe(rutaArchivos(conexion), ruta));
    }

    @Override
    public AlmacenamientoArchivoDTO actualizar(MAlmacenamientoConexion conexion, String fileId, String nombre,
                                               String idNuevoPadre, Boolean destacar)
            throws GeneralSecurityException {
        if (destacar != null) throw new IllegalArgumentException("Nextcloud no soporta marcar destacados vía WebDAV.");
        if ((nombre == null || nombre.isBlank()) && (idNuevoPadre == null || idNuevoPadre.isBlank())) {
            throw new IllegalArgumentException("Indica un nombre nuevo o una carpeta destino.");
        }
        String origen = rutaDeId(conexion, fileId);
        String nombreFinal = nombre == null || nombre.isBlank()
                ? nombreDeRuta(fileId) : nombreSeguro(nombre);
        String destinoRelativo = idNuevoPadre == null || idNuevoPadre.isBlank() || "root".equalsIgnoreCase(idNuevoPadre)
                ? "" : quitarSlash(decodificarId(idNuevoPadre)) + "/";
        String destino = rutaArchivos(conexion) + destinoRelativo + UriUtils.encodePathSegment(nombreFinal, StandardCharsets.UTF_8);
        mover(conexion, origen, destino);
        return metadatos(conexion, rutaRelativaDe(rutaArchivos(conexion), destino));
    }

    @Override
    public AlmacenamientoArchivoDTO copiar(MAlmacenamientoConexion conexion, String fileId, String idNuevoPadre)
            throws GeneralSecurityException {
        String origen = rutaDeId(conexion, fileId);
        String destinoRelativo = idNuevoPadre == null || idNuevoPadre.isBlank() || "root".equalsIgnoreCase(idNuevoPadre)
                ? "" : quitarSlash(decodificarId(idNuevoPadre)) + "/";
        String destino = rutaArchivos(conexion) + destinoRelativo + UriUtils.encodePathSegment(
                "copia de " + nombreDeRuta(fileId), StandardCharsets.UTF_8);
        enviar(conexion, HttpMethod.valueOf("COPY"), URI.create(urlServidor(conexion) + origen), null, null,
                Map.of("Destination", urlServidor(conexion) + destino));
        return metadatos(conexion, rutaRelativaDe(rutaArchivos(conexion), destino));
    }

    @Override
    public void aPapelera(MAlmacenamientoConexion conexion, String fileId) throws GeneralSecurityException {
        // En Nextcloud, DELETE sobre el archivo lo mueve automáticamente a la papelera del usuario.
        String ruta = rutaDeId(conexion, fileId);
        enviar(conexion, HttpMethod.DELETE, URI.create(urlServidor(conexion) + ruta), null, null);
    }

    @Override
    public void restaurar(MAlmacenamientoConexion conexion, String fileId) throws GeneralSecurityException {
        // fileId es la ruta en la papelera (codificada; contiene el sufijo .d<timestamp>); se devuelve a la raíz de archivos.
        String ruta = decodificarId(fileId);
        String destino = rutaArchivos(conexion) + UriUtils.encodePathSegment(nombreEntradaPapelera(ruta), StandardCharsets.UTF_8);
        enviar(conexion, HttpMethod.valueOf("MOVE"), URI.create(urlServidor(conexion) + quitarSlash(ruta)), null, null,
                Map.of("Destination", urlServidor(conexion) + destino));
    }

    @Override
    public void vaciarPapelera(MAlmacenamientoConexion conexion) throws GeneralSecurityException {
        String ruta = rutaPapelera(conexion);
        if (!"/".equals(ruta)) {
            enviar(conexion, HttpMethod.DELETE, URI.create(urlServidor(conexion) + ruta), null, null);
        }
    }

    @Override
    public List<AlmacenamientoPermisoDTO> listarPermisos(MAlmacenamientoConexion conexion, String fileId) {
        return List.of();
    }

    @Override
    public AlmacenamientoPermisoDTO compartir(MAlmacenamientoConexion conexion, String fileId, String rol,
                                              String tipo, String email) {
        throw new AlmacenamientoProveedorNoSoportado(
                "La compartición vía WebDAV no está disponible en Nextcloud; comparte desde el cliente del servidor.");
    }

    @Override
    public void revocarPermiso(MAlmacenamientoConexion conexion, String fileId, String permisoId) {
        throw new AlmacenamientoProveedorNoSoportado(
                "La compartición vía WebDAV no está disponible en Nextcloud; comparte desde el cliente del servidor.");
    }

    @Override
    public byte[] descargarLote(MAlmacenamientoConexion conexion, List<String> fileIds) throws GeneralSecurityException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        long total = 0;
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (String id : fileIds == null ? List.<String>of() : fileIds) {
                if (id == null || id.isBlank()) continue;
                RecursoContenido recurso = flujoArchivo(conexion, id);
                byte[] bytes = recurso.flujo().readAllBytes();
                total += bytes.length;
                if (total > 512L * 1024 * 1024) throw new IllegalStateException("El lote supera el límite de descarga.");
                zip.putNextEntry(new ZipEntry(nombreSeguro(recurso.nombreArchivo())));
                zip.write(bytes);
                zip.closeEntry();
            }
        } catch (GeneralSecurityException | RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo preparar la descarga en lote.", error);
        }
        return buffer.toByteArray();
    }

    // ---------- Internos WebDAV ----------

    /** Codifica una ruta como id seguro para rutas REST (un solo segmento, sin barras). */
    static String codificarId(String ruta) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(ruta.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodifica ids base64url; los identificadores en claro (p. ej. "root", "a/b.txt") se devuelven tal cual. */
    static String decodificarId(String id) {
        if (id == null) return null;
        if (id.startsWith("/") || id.contains("/") || id.contains("%")) return id;
        if (!id.matches("[A-Za-z0-9_-]+")) return id;
        try {
            String decodificado = new String(Base64.getUrlDecoder().decode(id), StandardCharsets.UTF_8);
            return decodificado.startsWith("/") ? decodificado : id;
        } catch (IllegalArgumentException error) {
            return id;
        }
    }

    /** Resuelve la ruta absoluta del recurso a partir del id (o de una carpeta) de la conexión. */
    private String rutaDeId(MAlmacenamientoConexion conexion, String id) throws GeneralSecurityException {
        String ruta = decodificarId(id);
        if (ruta.startsWith("/remote.php/dav")) {
            // El id ya es una ruta completa (archivo, carpeta o papelera).
            String limpia = quitarSlash(ruta);
            return limpia.startsWith("/") ? limpia : "/" + limpia;
        }
        return rutaArchivos(conexion) + quitarSlash(ruta);
    }

    private RecursoContenido flujoArchivo(MAlmacenamientoConexion conexion, String fileId)
            throws GeneralSecurityException {
        String ruta = decodificarId(fileId);
        boolean enPapelera = ruta.contains("/trashbin/");
        if (enPapelera) ruta = quitarSlash(ruta);
        else if (!ruta.startsWith("/remote.php/dav")) ruta = rutaArchivos(conexion) + quitarSlash(ruta);
        if (!ruta.startsWith("/")) ruta = "/" + ruta;
        byte[] bytes;
        String mime = "application/octet-stream";
        if (!enPapelera) {
            try { mime = valor(metadatos(conexion, fileId).mime(), "application/octet-stream"); }
            catch (RuntimeException ignorada) { }
        }
        bytes = enviarBytes(conexion, URI.create(urlServidor(conexion) + ruta));
        String nombre = nombreDeRuta(quitarSlash(ruta));
        return new RecursoContenido(new ByteArrayInputStream(bytes), bytes.length, mime, nombreSeguro(nombre));
    }

    /** PROPFIND Depth 1 que devuelve todos los <d:response> del documento. */
    private List<Element> propfind(MAlmacenamientoConexion conexion, String ruta, boolean papelera)
            throws GeneralSecurityException {
        String cuerpo = propfindCuerpo();
        String respuesta = texto(conexion, HttpMethod.valueOf("PROPFIND"),
                URI.create(urlServidor(conexion) + ruta), cuerpo, null, Map.of("Depth", "1"));
        return parsearMultistatus(respuesta, ruta);
    }

    private AlmacenamientoArchivoDTO propfindUno(MAlmacenamientoConexion conexion, String ruta)
            throws GeneralSecurityException {
        List<Element> respuestas = propfind(conexion, ruta, false);
        for (Element respuesta : respuestas) {
            if (rutaIgual(textoHijo(respuesta, "href"), ruta)) return aDto(respuesta);
        }
        throw new AlmacenamientoRecursoNoEncontrado("El recurso solicitado no existe.");
    }

    private void mover(MAlmacenamientoConexion conexion, String origen, String destino)
            throws GeneralSecurityException {
        enviar(conexion, HttpMethod.valueOf("MOVE"), URI.create(urlServidor(conexion) + origen), null, null,
                Map.of("Destination", urlServidor(conexion) + destino));
    }

    private String texto(MAlmacenamientoConexion conexion, HttpMethod metodo, URI destino, String cuerpo,
                         MediaType tipo, Map<String, String> cabeceras) throws GeneralSecurityException {
        try {
            String basicAuth = autenticacion(conexion);
            RestClient.RequestBodySpec especificacion = clienteHttp.method(metodo).uri(destino)
                    .headers(h -> {
                        h.setBasicAuth(basicAuth);
                        cabeceras.forEach(h::set);
                    });
            if (cuerpo != null) {
                especificacion = especificacion.contentType(tipo == null ? MediaType.APPLICATION_XML : tipo).body(cuerpo);
            }
            return especificacion.retrieve().body(String.class);
        } catch (HttpStatusCodeException error) {
            throw errorConcreto(error);
        } catch (RestClientException error) {
            throw new IllegalStateException("No se pudo contactar el servidor Nextcloud.", error);
        }
    }

    private void enviar(MAlmacenamientoConexion conexion, HttpMethod metodo, URI destino, byte[] cuerpo,
                        MediaType tipo) throws GeneralSecurityException {
        enviar(conexion, metodo, destino, cuerpo, tipo, Map.of());
    }

    private void enviar(MAlmacenamientoConexion conexion, HttpMethod metodo, URI destino, byte[] cuerpo,
                        MediaType tipo, Map<String, String> cabeceras) throws GeneralSecurityException {
        try {
            String basicAuth = autenticacion(conexion);
            RestClient.RequestBodySpec especificacion = clienteHttp.method(metodo).uri(destino)
                    .headers(h -> {
                        h.setBasicAuth(basicAuth);
                        cabeceras.forEach(h::set);
                    });
            if (cuerpo != null) {
                especificacion = especificacion.contentType(tipo == null ? MediaType.APPLICATION_OCTET_STREAM : tipo)
                        .body(cuerpo);
            }
            especificacion.retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException error) {
            throw errorConcreto(error);
        } catch (RestClientException error) {
            throw new IllegalStateException("No se pudo contactar el servidor Nextcloud.", error);
        }
    }

    private byte[] enviarBytes(MAlmacenamientoConexion conexion, URI destino) throws GeneralSecurityException {
        try {
            String basicAuth = autenticacion(conexion);
            return clienteHttp.method(HttpMethod.GET).uri(destino)
                    .headers(h -> h.setBasicAuth(basicAuth))
                    .retrieve().body(byte[].class);
        } catch (HttpStatusCodeException error) {
            throw errorConcreto(error);
        } catch (RestClientException error) {
            throw new IllegalStateException("No se pudo leer el archivo desde el servidor Nextcloud.", error);
        }
    }

    private RuntimeException errorConcreto(HttpStatusCodeException error) {
        int estado = error.getStatusCode().value();
        if (estado == 401 || estado == 403) {
            return new ConexionRequiereReconexionException(
                    "El servidor Nextcloud rechazó el usuario o la contraseña de aplicación; reconecta la cuenta.");
        }
        if (estado == 404) return new AlmacenamientoRecursoNoEncontrado("El archivo o carpeta no existe en el servidor.");
        if (estado == 409 || estado == 412) {
            return new IllegalStateException("El servidor Nextcloud no pudo completar la operación (carpeta destino ausente).");
        }
        return new IllegalStateException("El servidor Nextcloud rechazó la operación (" + estado + ").");
    }

    /** Valor ya codificado (Base64) para la cabecera Authorization Basic. */
    private String autenticacion(MAlmacenamientoConexion conexion) throws GeneralSecurityException {
        String usuario = valor(conexion.getIdentificadorExterno());
        String credencial = cifrado.descifrar(conexion.getCredencialCifrada());
        return Base64.getEncoder().encodeToString((usuario + ":" + credencial).getBytes(StandardCharsets.UTF_8));
    }

    private String urlServidor(MAlmacenamientoConexion conexion) {
        return quitarSlash(valor(conexion.getUrlServidor()));
    }

    private String rutaArchivos(MAlmacenamientoConexion conexion) {
        return "/remote.php/dav/files/" + UriUtils.encodePathSegment(valor(conexion.getIdentificadorExterno()),
                StandardCharsets.UTF_8) + "/";
    }

    private String rutaPapelera(MAlmacenamientoConexion conexion) {
        return "/remote.php/dav/trashbin/" + UriUtils.encodePathSegment(valor(conexion.getIdentificadorExterno()),
                StandardCharsets.UTF_8) + "/trash";
    }

    private String propfindCuerpo() {
        return "<d:propfind xmlns:d=\"DAV:\"><d:prop>"
                + "<d:displayname/><d:getcontentlength/><d:getlastmodified/><d:getcontenttype/>"
                + "<d:resourcetype/><d:quota-used-bytes/>"
                + "</d:prop></d:propfind>";
    }

    private List<Element> parsearMultistatus(String xml, String rutaConsulta) {
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setNamespaceAware(true);
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            NodeList respuestas = fabrica.newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)))
                    .getElementsByTagNameNS(NS_DAV, "response");
            List<Element> elementos = new ArrayList<>();
            for (int i = 0; i < respuestas.getLength(); i++) elementos.add((Element) respuestas.item(i));
            return elementos;
        } catch (Exception error) {
            throw new IllegalStateException("El servidor Nextcloud devolvió una respuesta no válida.", error);
        }
    }

    private static AlmacenamientoArchivoDTO aDto(Element respuesta) {
        String href = textoHijo(respuesta, "href");
        boolean carpeta = respuesta.getElementsByTagNameNS(NS_DAV, "collection").getLength() > 0;
        boolean papelera = href.contains("/trashbin/");
        String nombre = papelera ? nombreEntradaPapelera(href) : nombreDeRuta(href);
        long tamano = 0;
        String tamanoTexto = textoHijo(respuesta, "getcontentlength");
        if (tamanoTexto != null && !tamanoTexto.isBlank()) {
            try { tamano = Long.parseLong(tamanoTexto.trim()); } catch (NumberFormatException ignorada) { tamano = 0; }
        }
        String id = codificarId(href);
        String padre = papelera ? "" : padreDeHref(href);
        return new AlmacenamientoArchivoDTO(
                id,
                nombre,
                carpeta ? AlmacenamientoArchivoDTO.TIPO_CARPETA : AlmacenamientoArchivoDTO.TIPO_ARCHIVO,
                valor(textoHijo(respuesta, "getcontenttype"), carpeta ? "directorio" : "application/octet-stream"),
                carpeta ? null : tamano,
                valor(textoHijo(respuesta, "getlastmodified"), ""),
                padre.isBlank() ? "root" : codificarId("/" + padre),
                false,
                !papelera,
                false,
                enlaceWeb(href));
    }

    private static String rutaArchivosDeHref(String href) {
        int indice = href.indexOf("/remote.php/dav/files/");
        return indice < 0 ? "" : href.substring(0, indice + "/remote.php/dav/files/".length());
    }

    /** Carpeta contenedora de un href relativa a la raíz de archivos del usuario (sin el segmento del usuario). */
    private static String padreDeHref(String href) {
        int indice = href.indexOf("/remote.php/dav/files/");
        if (indice < 0) return "";
        String dentro = java.net.URLDecoder.decode(
                href.substring(indice + "/remote.php/dav/files/".length()), StandardCharsets.UTF_8);
        int primeraBarra = dentro.indexOf('/');
        if (primeraBarra < 0) return "";
        String resto = dentro.substring(primeraBarra + 1); // descarta el segmento del usuario
        int ultimaBarra = resto.lastIndexOf('/');
        String padre = ultimaBarra <= 0 ? "" : resto.substring(0, ultimaBarra);
        return padre;
    }

    private static String rutaRelativaDe(String baseArchivos, String rutaCompleta) {
        String ruta = rutaCompleta;
        if (ruta.startsWith("http")) {
            try { ruta = new URI(ruta).getPath(); } catch (Exception ignorada) { ruta = rutaCompleta; }
        }
        if (ruta.contains("/remote.php/dav/files/")) {
            ruta = ruta.substring(ruta.indexOf("/remote.php/dav/files/") + "/remote.php/dav/files/".length());
            return ruta.isBlank() ? "root" : quitarSlash(java.net.URLDecoder.decode(ruta, StandardCharsets.UTF_8));
        }
        if (baseArchivos != null && ruta.startsWith(baseArchivos)) {
            ruta = ruta.substring(baseArchivos.length());
        }
        return ruta.isBlank() ? "root" : quitarSlash(java.net.URLDecoder.decode(ruta, StandardCharsets.UTF_8));
    }

    /** Nombre visible de una entrada de papelera: elimina el sufijo .d<timestamp>. */
    static String nombreEntradaPapelera(String hrefOrId) {
        String nombre = nombreDeRuta(hrefOrId);
        int ultimoPuntoD = nombre.lastIndexOf(".d");
        if (ultimoPuntoD > 0 && nombre.substring(ultimoPuntoD + 2).chars().allMatch(Character::isDigit)) {
            nombre = nombre.substring(0, ultimoPuntoD);
        }
        return nombre;
    }

    private static String enlaceWeb(String href) {
        return null;
    }

    private static String nombreDeRuta(String ruta) {
        String rutaLimpia = ruta.split("\\?")[0];
        if (rutaLimpia.endsWith("/")) rutaLimpia = rutaLimpia.substring(0, rutaLimpia.length() - 1);
        int barra = rutaLimpia.lastIndexOf('/');
        String nombre = barra >= 0 ? rutaLimpia.substring(barra + 1) : rutaLimpia;
        return java.net.URLDecoder.decode(nombre, StandardCharsets.UTF_8);
    }

    private static boolean rutaIgual(String href, String rutaConsulta) {
        if (href == null) return false;
        String caminoHref = href.contains("http") ? quitarEsquema(href) : href;
        String caminoConsulta = rutaConsulta;
        try {
            caminoHref = new URI(caminoHref).getPath();
        } catch (Exception ignorada) { }
        try {
            caminoConsulta = new URI(rutaConsulta).getPath();
        } catch (Exception ignorada) { }
        return java.net.URLDecoder.decode(caminoHref, StandardCharsets.UTF_8)
                .equals(java.net.URLDecoder.decode(caminoConsulta, StandardCharsets.UTF_8));
    }

    private static String quitarEsquema(String href) {
        try { return new URI(href).getPath(); } catch (Exception error) { return href; }
    }

    private static void ordenar(List<AlmacenamientoArchivoDTO> archivos, String orden) {
        String criterio = orden == null ? "" : orden.toUpperCase(Locale.ROOT);
        boolean descendente = criterio.endsWith("DESC");
        archivos.sort((a, b) -> {
            int comparacion;
            if (criterio.startsWith("NOMBRE")) {
                comparacion = a.nombre().compareToIgnoreCase(b.nombre());
            } else if (criterio.startsWith("TAMANIO")) {
                comparacion = Long.compare(a.tamanio() == null ? 0 : a.tamanio(), b.tamanio() == null ? 0 : b.tamanio());
            } else {
                comparacion = valor(a.fechaModificacion()).compareTo(valor(b.fechaModificacion()));
            }
            // Las carpetas primero
            if (a.tipo().equals(b.tipo())) return descendente ? -comparacion : comparacion;
            return a.tipo().equals(AlmacenamientoArchivoDTO.TIPO_CARPETA) ? -1 : 1;
        });
    }

    private static void validarVista(String vista) {
        if (vista == null || vista.isBlank()) return;
        String vistaNormalizada = vista.toUpperCase(Locale.ROOT);
        if (List.of("UNIDAD", "PAPELERA").contains(vistaNormalizada)) return;
        throw new IllegalArgumentException(
                "Vista no soportada en Nextcloud: " + vista + ". Usa unidad o papelera.");
    }

    private static String nombreSeguro(String nombre) {
        String limpio = nombre == null ? "archivo" : nombre.replaceAll("[\\r\\n\"\\\\/]", "_").trim();
        return limpio.isBlank() ? "archivo" : limpio;
    }

    private static String quitarSlash(String ruta) {
        String limpia = ruta.trim();
        while (limpia.startsWith("/")) limpia = limpia.substring(1);
        while (limpia.endsWith("/")) limpia = limpia.substring(0, limpia.length() - 1);
        return limpia;
    }

    private static String valor(String texto, String alternativo) {
        return texto == null || texto.isBlank() ? alternativo : texto;
    }

    private static String valor(String texto) { return valor(texto, ""); }

    private static String textoHijo(Element padre, String nombreLocal) {
        NodeList nodos = padre.getElementsByTagNameNS(NS_DAV, nombreLocal);
        if (nodos.getLength() == 0) return null;
        NodeList hijosTexto = nodos.item(0).getChildNodes();
        StringBuilder contenido = new StringBuilder();
        for (int i = 0; i < hijosTexto.getLength(); i++) contenido.append(hijosTexto.item(i).getNodeValue());
        return contenido.toString().trim();
    }
}
