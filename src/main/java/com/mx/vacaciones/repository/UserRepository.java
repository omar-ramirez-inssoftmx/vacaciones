package com.mx.vacaciones.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mx.vacaciones.model.Role;
import com.mx.vacaciones.model.User;

import jakarta.persistence.LockModeType;

/**
 * Repositorio de acceso a datos para la entidad {@link User}.
 *
 * <p>
 * Proporciona métodos de consulta para:
 * </p>
 * <ul>
 *     <li>Búsqueda por username</li>
 *     <li>Validación de existencia por username</li>
 *     <li>Consulta de administradores habilitados</li>
 *     <li>Bloqueo pesimista por id</li>
 *     <li>Búsqueda por correo electrónico</li>
 * </ul>
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca un usuario por su nombre de usuario.
     *
     * @param username nombre de usuario
     * @return usuario encontrado envuelto en Optional
     */
    Optional<User> findByUsername(String username);

    /**
     * Valida si ya existe un usuario con el username indicado.
     *
     * @param username nombre de usuario
     * @return true si existe, false en caso contrario
     */
    boolean existsByUsername(String username);

    /**
     * Obtiene la lista de usuarios habilitados por rol.
     *
     * @param roleAdmin rol a consultar
     * @return lista de usuarios habilitados con el rol indicado
     */
    List<User> findByRoleAndEnabledTrue(Role roleAdmin);

    /**
     * Busca un usuario por id aplicando bloqueo pesimista de escritura.
     *
     * <p>
     * Útil para operaciones críticas donde se requiere evitar concurrencia.
     * </p>
     *
     * @param id identificador del usuario
     * @return usuario encontrado envuelto en Optional
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * Busca un usuario por su correo electrónico.
     *
     * @param name correo electrónico
     * @return usuario encontrado
     */
    User findByEmail(String name);
}