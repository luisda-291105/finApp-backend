package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.MuestraCrearDTO;
import com.luisda.fin_app_back.DTO.MuestraListarDTO;
import com.luisda.fin_app_back.DTO.MuestraRespuestaDTO;
import com.luisda.fin_app_back.Model.MMuestra;
import com.luisda.fin_app_back.Repository.IMuestraRepository;
import com.luisda.fin_app_back.Repository.IUsuario;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.NoSuchElementException;

@Service
public class SMuestra {
    private static final Set<String> TIPOS = Set.of("TARJETA", "GRAFICA", "METRICA", "MAPA_CALOR");
    private final IMuestraRepository muestras; private final IUsuario usuarios;
    private final ObjectMapper objectMapper = new ObjectMapper();
    public SMuestra(IMuestraRepository muestras, IUsuario usuarios) {
        this.muestras = muestras;
        this.usuarios = usuarios;
    }
    public MuestraRespuestaDTO crear(String usuarioId, MuestraCrearDTO dto) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) throw new IllegalStateException("No hay usuario activo.");
        validarDto(dto);
        String tipo = normalizarTipo(dto.tipo()), titulo = dto.titulo().trim();
        if (muestras.existsByUsuarioIdAndTipoAndTitulo(usuarioId, tipo, titulo)) throw new IllegalStateException("Ya existe una muestra con ese tipo y título.");
        MMuestra m = new MMuestra(); m.setUsuarioId(usuarioId); m.setTipo(tipo); m.setTitulo(titulo); m.setDatos(dto.datos());
        return respuesta(muestras.save(m));
    }
    public List<MuestraListarDTO> listar(String usuarioId) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) throw new IllegalStateException("No hay usuario activo.");
        return muestras.findByUsuarioIdOrderByFechaCreacionAsc(usuarioId).stream().map(m -> new MuestraListarDTO(m.getId(), m.getTipo(), m.getTitulo(), m.getDatos(), m.getFechaCreacion())).toList();
    }
    @Transactional
    public void reordenar(String usuarioId, List<Long> ids) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) throw new IllegalStateException("No hay usuario activo.");
        if (ids == null || ids.stream().anyMatch(id -> id == null) || new HashSet<>(ids).size() != ids.size())
            throw new IllegalArgumentException("La lista de muestras es inválida.");
        List<MMuestra> delUsuario = muestras.findByUsuarioId(usuarioId);
        Set<Long> idsUsuario = delUsuario.stream().map(MMuestra::getId).collect(java.util.stream.Collectors.toSet());
        if (ids.size() != delUsuario.size() || !idsUsuario.equals(new HashSet<>(ids)))
            throw new IllegalArgumentException("La lista debe incluir todas las muestras del usuario.");

        var porId = delUsuario.stream().collect(java.util.stream.Collectors.toMap(MMuestra::getId, muestra -> muestra));
        for (int indice = 0; indice < ids.size(); indice++) {
            MMuestra muestra = porId.get(ids.get(indice));
            try {
                var datos = objectMapper.readTree(muestra.getDatos());
                if (datos == null || !datos.isObject())
                    throw new IllegalStateException("Los datos guardados de una muestra no tienen un formato válido.");
                ((ObjectNode) datos).put("ordenDashboard", indice);
                muestra.setDatos(objectMapper.writeValueAsString(datos));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("No se pudo actualizar el orden porque una muestra tiene datos inválidos.", e);
            }
        }
        muestras.saveAll(delUsuario);
    }
    public MuestraRespuestaDTO buscar(String usuarioId, Long id) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) throw new IllegalStateException("No hay usuario activo.");
        return muestras.findById(id).filter(m -> m.getUsuarioId().equals(usuarioId)).map(SMuestra::respuesta).orElseThrow(() -> new java.util.NoSuchElementException("Muestra no encontrada."));
    }
    public MuestraRespuestaDTO actualizar(String usuarioId, Long id, MuestraCrearDTO dto) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) throw new IllegalStateException("No hay usuario activo.");
        validarDto(dto);
        MMuestra muestra = muestras.findById(id).filter(m -> m.getUsuarioId().equals(usuarioId))
                .orElseThrow(() -> new NoSuchElementException("Muestra no encontrada."));
        String tipo = normalizarTipo(dto.tipo()), titulo = dto.titulo().trim();
        if (muestras.existsByUsuarioIdAndTipoAndTituloAndIdNot(usuarioId, tipo, titulo, id))
            throw new IllegalStateException("Ya existe una muestra con ese tipo y título.");
        muestra.setTipo(tipo); muestra.setTitulo(titulo); muestra.setDatos(dto.datos());
        return respuesta(muestras.save(muestra));
    }
    public void eliminar(String usuarioId, Long id) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) throw new IllegalStateException("No hay usuario activo.");
        MMuestra muestra = muestras.findById(id).filter(m -> m.getUsuarioId().equals(usuarioId))
                .orElseThrow(() -> new NoSuchElementException("Muestra no encontrada."));
        muestras.delete(muestra);
    }
    private static void validarDto(MuestraCrearDTO dto) {
        if (dto == null || dto.tipo() == null || !TIPOS.contains(normalizarTipo(dto.tipo()))
                || dto.titulo() == null || dto.titulo().trim().isEmpty()
                || dto.datos() == null || dto.datos().isBlank())
            throw new IllegalArgumentException("Tipo, título y datos son requeridos.");
    }
    private static String normalizarTipo(String tipo) { return tipo == null ? "" : tipo.trim().toUpperCase(); }
    private static MuestraRespuestaDTO respuesta(MMuestra m) { return new MuestraRespuestaDTO(m.getId(), m.getUsuarioId(), m.getTipo(), m.getTitulo(), m.getDatos(), m.getFechaCreacion()); }
}
