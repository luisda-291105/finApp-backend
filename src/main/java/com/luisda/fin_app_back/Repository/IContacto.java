package com.luisda.fin_app_back.Repository;
import com.luisda.fin_app_back.Model.MContacto;
import com.luisda.fin_app_back.Model.MUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;



/**
 * @uso               Persiste y consulta contactos
 * @capa              Repositorio
 * @responsabilidades CRUD y búsquedas por usuario, nombre, teléfono y correo
 * @datos             Recibe y devuelve entidades MContacto
 * @dependencias      MContacto, Spring Data JPA
 * @usadoPor          SContacto
 */
@Repository
public interface IContacto extends JpaRepository<MContacto ,String> {
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
