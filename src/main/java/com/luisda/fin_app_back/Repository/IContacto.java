package com.luisda.fin_app_back.Repository;
import com.luisda.fin_app_back.Model.MContacto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;
import java.util.Optional;



/**
 * @uso               Persiste y consulta contactos
 * @capa              Repositorio
 * @responsabilidades CRUD y búsquedas por usuario, nombre, teléfono y correo
 * @datos             Recibe y devuelve entidades MContacto
 * @dependencias      MContacto, Spring Data JPA
 * @usadoPor          SContacto
 */

public interface IContacto extends JpaRepository<MContacto ,String> {
    List<MContacto> findByUsuario_IdUsuario(String idUsuario);
    List<MContacto> findTop25ByUsuario_IdUsuarioAndNombreContainingIgnoreCaseOrderByNombreAsc(String idUsuario, String nombre);
    Optional<MContacto> findByIdContactoAndUsuario_IdUsuario(String idContacto, String idUsuario);
    Optional<MContacto> findByOrigenAndUsuario_IdUsuario(String origen, String idUsuario);
    // consultas
    @Query("SELECT c FROM MContacto c WHERE c.nombre = :nombre")
    List<MContacto> buscarPorNombre(
            @Param("nombre") String nombreContacto
    );

    @Query("SELECT c FROM MContacto c WHERE c.notas = :nota")
    List<MContacto> buscarPorNota(
            @Param("nota") String notaContacto
    );

    @Query("SELECT c FROM MContacto c WHERE c.telefono = :telefono")
    List<MContacto> buscarPorTelefono(
            @Param("telefono") String telefono
    );

}
