package com.luisda.fin_app_back.Services;

import com.luisda.fin_app_back.DTO.MuestraCrearDTO;
import com.luisda.fin_app_back.DTO.MuestraListarDTO;
import com.luisda.fin_app_back.DTO.MuestraRespuestaDTO;
import com.luisda.fin_app_back.Model.MMuestra;
import com.luisda.fin_app_back.Repository.IMuestraRepository;
import com.luisda.fin_app_back.Repository.IUsuario;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;

@Service
public class SMuestra {
    private static final Set<String> TIPOS = Set.of("TARJETA", "GRAFICA", "METRICA", "MAPA_CALOR");
    private final IMuestraRepository muestras; private final IUsuario usuarios;
    public SMuestra(IMuestraRepository muestras, IUsuario usuarios) { this.muestras = muestras; this.usuarios = usuarios; }
    public MuestraRespuestaDTO crear(String usuarioId, MuestraCrearDTO dto) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) throw new IllegalStateException("No hay usuario activo.");
        if (dto == null || dto.tipo() == null || !TIPOS.contains(dto.tipo().trim().toUpperCase()) || dto.titulo() == null || dto.titulo().trim().isEmpty() || dto.datos() == null || dto.datos().isBlank()) throw new IllegalArgumentException("Tipo, título y datos son requeridos.");
        String tipo = dto.tipo().trim().toUpperCase(), titulo = dto.titulo().trim();
        if (muestras.existsByUsuarioIdAndTipoAndTitulo(usuarioId, tipo, titulo)) throw new IllegalStateException("Ya existe una muestra con ese tipo y título.");
        MMuestra m = new MMuestra(); m.setUsuarioId(usuarioId); m.setTipo(tipo); m.setTitulo(titulo); m.setDatos(dto.datos());
        return respuesta(muestras.save(m));
    }
    public List<MuestraListarDTO> listar(String usuarioId) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) throw new IllegalStateException("No hay usuario activo.");
        return muestras.findByUsuarioIdOrderByFechaCreacionAsc(usuarioId).stream().map(m -> new MuestraListarDTO(m.getId(), m.getTipo(), m.getTitulo(), m.getFechaCreacion())).toList();
    }
    public MuestraRespuestaDTO buscar(String usuarioId, Long id) {
        if (usuarioId == null || !usuarios.existsById(usuarioId)) throw new IllegalStateException("No hay usuario activo.");
        return muestras.findById(id).filter(m -> m.getUsuarioId().equals(usuarioId)).map(SMuestra::respuesta).orElseThrow(() -> new java.util.NoSuchElementException("Muestra no encontrada."));
    }
    private static MuestraRespuestaDTO respuesta(MMuestra m) { return new MuestraRespuestaDTO(m.getId(), m.getUsuarioId(), m.getTipo(), m.getTitulo(), m.getDatos(), m.getFechaCreacion()); }
}
