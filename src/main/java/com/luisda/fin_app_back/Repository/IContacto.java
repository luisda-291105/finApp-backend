package com.luisda.fin_app_back.Repository;
import com.luisda.fin_app_back.Model.MContacto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
@Repository
public interface IContacto extends JpaRepository<MContacto ,String> {
    List<MContacto> buscarPorNombre (String nombreContacto)throws Exception;
    List<MContacto> buscarPorNota  (String notaContacto)throws Exception;
    List<MContacto> buscarPorTelefono(String telefono)throws Exception;
    void delete(Optional<MContacto> usuarioEncontrado);
}
