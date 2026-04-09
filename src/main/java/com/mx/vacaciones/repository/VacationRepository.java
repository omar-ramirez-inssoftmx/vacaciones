package com.mx.vacaciones.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;

/**
 * Repositorio de acceso a datos para solicitudes de vacaciones.
 *
 * <p>
 * Este repositorio concentra consultas relacionadas con:
 * </p>
 * <ul>
 *     <li>Búsqueda de solicitudes por usuario</li>
 *     <li>Búsqueda por estatus</li>
 *     <li>Validación de traslapes</li>
 *     <li>Consultas para calendario</li>
 *     <li>Métricas para dashboard</li>
 *     <li>Historial filtrado</li>
 * </ul>
 */
public interface VacationRepository extends JpaRepository<VacationRequest, Long> {

    /**
     * Obtiene solicitudes por username.
     *
     * @param username nombre de usuario
     * @return lista de solicitudes del username indicado
     */
    List<VacationRequest> findByUsername(String username);

    /**
     * Obtiene solicitudes por estatus.
     *
     * @param status estatus de la solicitud, por ejemplo PENDING
     * @return lista de solicitudes con ese estatus
     */
    List<VacationRequest> findByStatus(String status);

    /**
     * Obtiene solicitudes asociadas a un usuario.
     *
     * @param user usuario
     * @return lista de solicitudes del usuario
     */
    List<VacationRequest> findByUser(User user);

    /**
     * Obtiene las solicitudes de un usuario ordenadas de más reciente a más antigua.
     *
     * @param username nombre de usuario
     * @return lista de solicitudes ordenadas descendentemente por id
     */
    List<VacationRequest> findByUserUsernameOrderByIdDesc(String username);

    /**
     * Obtiene solicitudes por estatus ordenadas de más reciente a más antigua.
     *
     * @param status estatus de la solicitud
     * @return lista de solicitudes ordenadas descendentemente por id
     */
    List<VacationRequest> findByStatusOrderByIdDesc(String status);

    /**
     * Obtiene solicitudes cuyos estatus estén dentro de una lista,
     * ordenadas de más reciente a más antigua.
     *
     * @param statuses lista de estatus
     * @return lista de solicitudes
     */
    List<VacationRequest> findByStatusInOrderByIdDesc(List<String> statuses);

    /**
     * Cuenta solicitudes por estatus exacto.
     *
     * <p>
     * Este método es útil, por ejemplo, para mostrar en home el badge
     * con la cantidad de solicitudes pendientes por revisar.
     * </p>
     *
     * @param status estatus a contar, por ejemplo PENDING
     * @return total de solicitudes con ese estatus
     */
    long countByStatus(String status);

    /**
     * Valida si existe alguna solicitud que se traslape con el rango indicado
     * para un usuario y con estatus bloqueantes.
     *
     * <p>
     * Se usa para evitar que el usuario capture vacaciones que se crucen
     * con otras ya pendientes o aprobadas.
     * </p>
     *
     * @param userId id del usuario
     * @param startDate fecha inicial del nuevo rango
     * @param endDate fecha final del nuevo rango
     * @param statuses estatus que deben considerarse como bloqueantes
     * @return true si existe traslape
     */
    @Query("""
            SELECT COUNT(v) > 0
            FROM VacationRequest v
            WHERE v.user.id = :userId
              AND v.status IN (:statuses)
              AND v.startDate <= :endDate
              AND v.endDate >= :startDate
            """)
    boolean existsOverlapping(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statuses") List<String> statuses
    );

    /**
     * Obtiene solicitudes para mostrar en el calendario general
     * según una lista de estatus.
     *
     * @param statuses lista de estatus a incluir
     * @return solicitudes ordenadas por fecha de inicio ascendente
     */
    @Query("""
            SELECT v
            FROM VacationRequest v
            WHERE v.status IN (:statuses)
            ORDER BY v.startDate ASC
            """)
    List<VacationRequest> findForCalendar(@Param("statuses") List<String> statuses);

    /**
     * Obtiene solicitudes para calendario dentro de un rango específico.
     *
     * <p>
     * Trae solicitudes que se traslapen con el rango solicitado.
     * </p>
     *
     * @param statuses estatus permitidos
     * @param from fecha inicial del rango consultado
     * @param to fecha final del rango consultado
     * @return solicitudes ordenadas por fecha de inicio ascendente
     */
    @Query("""
            SELECT v
            FROM VacationRequest v
            WHERE v.status IN (:statuses)
              AND v.startDate <= :to
              AND v.endDate >= :from
            ORDER BY v.startDate ASC
            """)
    List<VacationRequest> findForCalendarRange(
            @Param("statuses") List<String> statuses,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /**
     * Obtiene las solicitudes aprobadas que cubren un día específico.
     *
     * <p>
     * Se usa para saber quién está actualmente de vacaciones
     * en una fecha determinada.
     * </p>
     *
     * @param today fecha a consultar
     * @return solicitudes aprobadas activas ese día
     */
    @Query("""
            SELECT vr
            FROM VacationRequest vr
            WHERE vr.status = 'APPROVED'
              AND :today BETWEEN vr.startDate AND vr.endDate
            ORDER BY vr.username ASC
            """)
    List<VacationRequest> findApprovedCoveringDay(@Param("today") LocalDate today);

    /**
     * Cuenta cuántas solicitudes están actualmente pendientes.
     *
     * <p>
     * Similar a countByStatus("PENDING"), pero deja explícita
     * la intención de negocio para dashboard o tarjetas KPI.
     * </p>
     *
     * @return total de solicitudes pendientes
     */
    @Query("""
            SELECT COUNT(vr)
            FROM VacationRequest vr
            WHERE vr.status = 'PENDING'
            """)
    long countPending();

    /**
     * Suma la cantidad de días aprobados que se traslapan con un mes dado.
     *
     * <p>
     * Útil para métricas mensuales del dashboard.
     * </p>
     *
     * @param startOfMonth primer día del mes
     * @param endOfMonth último día del mes
     * @return suma total de días aprobados en el mes
     */
    @Query("""
            SELECT COALESCE(SUM(vr.days), 0)
            FROM VacationRequest vr
            WHERE vr.status = 'APPROVED'
              AND vr.startDate <= :endOfMonth
              AND vr.endDate >= :startOfMonth
            """)
    long sumApprovedDaysOverlappingMonth(
            @Param("startOfMonth") LocalDate startOfMonth,
            @Param("endOfMonth") LocalDate endOfMonth
    );

    /**
     * Obtiene historial filtrado de solicitudes aprobadas o rechazadas.
     *
     * <p>
     * Permite filtrar por:
     * </p>
     * <ul>
     *     <li>Estatus</li>
     *     <li>Texto de búsqueda (username o email)</li>
     *     <li>Rango de fechas</li>
     * </ul>
     *
     * @param status estatus opcional
     * @param q texto de búsqueda opcional
     * @param from fecha inicial opcional
     * @param to fecha final opcional
     * @return lista filtrada de solicitudes históricas
     */
    @Query("""
            SELECT r
            FROM VacationRequest r
            WHERE r.status IN ('APPROVED','REJECTED')
              AND (
                    :status IS NULL OR :status = '' OR
                    UPPER(r.status) = UPPER(:status)
                  )
              AND (
                    :q IS NULL OR :q = '' OR
                    LOWER(r.user.username) LIKE LOWER(CONCAT('%', :q, '%')) OR
                    LOWER(r.user.email) LIKE LOWER(CONCAT('%', :q, '%'))
                  )
              AND (:from IS NULL OR r.startDate >= :from)
              AND (:to IS NULL OR r.endDate <= :to)
            ORDER BY r.id DESC
            """)
    List<VacationRequest> findHistoryFiltered(
            @Param("status") String status,
            @Param("q") String q,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /**
     * Obtiene ranking de usuarios con más días aprobados dentro del mes consultado.
     *
     * <p>
     * Devuelve una lista de arreglos Object[] con:
     * </p>
     * <ul>
     *     <li>posición 0: username</li>
     *     <li>posición 1: suma de días aprobados</li>
     * </ul>
     *
     * @param startOfMonth primer día del mes
     * @param endOfMonth último día del mes
     * @return ranking de usuarios por consumo de días aprobados
     */
    @Query("""
            SELECT vr.user.username, COALESCE(SUM(vr.days), 0)
            FROM VacationRequest vr
            WHERE vr.status = 'APPROVED'
              AND vr.startDate <= :endOfMonth
              AND vr.endDate >= :startOfMonth
            GROUP BY vr.user.username
            ORDER BY SUM(vr.days) DESC
            """)
    List<Object[]> topUsersApprovedDaysOverlappingMonth(
            @Param("startOfMonth") LocalDate startOfMonth,
            @Param("endOfMonth") LocalDate endOfMonth
    );
}