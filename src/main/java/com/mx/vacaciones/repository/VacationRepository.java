package com.mx.vacaciones.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mx.vacaciones.model.User;
import com.mx.vacaciones.model.VacationRequest;

public interface VacationRepository extends JpaRepository<VacationRequest, Long> {

	List<VacationRequest> findByUsername(String username);

	List<VacationRequest> findByStatus(String status);

	List<VacationRequest> findByUser(User user);

	List<VacationRequest> findByUserUsernameOrderByIdDesc(String username);

	List<VacationRequest> findByStatusOrderByIdDesc(String status);

	List<VacationRequest> findByStatusInOrderByIdDesc(List<String> statuses);

	@Query("""
			  SELECT COUNT(v) > 0
			  FROM VacationRequest v
			  WHERE v.user.id = :userId
			    AND v.status IN (:statuses)
			    AND v.startDate <= :endDate
			    AND v.endDate >= :startDate
			""")
	boolean existsOverlapping(@Param("userId") Long userId, @Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate, @Param("statuses") List<String> statuses);

	@Query("""
			  SELECT v
			  FROM VacationRequest v
			  WHERE v.status IN (:statuses)
			  ORDER BY v.startDate ASC
			""")
	List<VacationRequest> findForCalendar(@Param("statuses") List<String> statuses);

	@Query("""
			  SELECT v
			  FROM VacationRequest v
			  WHERE v.status IN (:statuses)
			    AND v.startDate <= :to
			    AND v.endDate >= :from
			  ORDER BY v.startDate ASC
			""")
	List<VacationRequest> findForCalendarRange(@Param("statuses") List<String> statuses, @Param("from") LocalDate from,
			@Param("to") LocalDate to);

	@Query("""
			  select vr from VacationRequest vr
			  where vr.status = 'APPROVED'
			    and :today between vr.startDate and vr.endDate
			  order by vr.username asc
			""")
	List<VacationRequest> findApprovedCoveringDay(@Param("today") LocalDate today);

	@Query("""
			  select count(vr) from VacationRequest vr
			  where vr.status = 'PENDIENTE'
			""")
	long countPending();

	@Query("""
			  select coalesce(sum(vr.days), 0) from VacationRequest vr
			  where vr.status = 'APPROVED'
			    and vr.startDate <= :endOfMonth
			    and vr.endDate >= :startOfMonth
			""")
	long sumApprovedDaysOverlappingMonth(@Param("startOfMonth") LocalDate startOfMonth,
			@Param("endOfMonth") LocalDate endOfMonth);

	@Query("""
			  select vr.username, coalesce(sum(vr.days), 0)
			  from VacationRequest vr
			  where vr.status = 'APPROVED'
			    and vr.startDate <= :endOfMonth
			    and vr.endDate >= :startOfMonth
			  group by vr.username
			  order by coalesce(sum(vr.days), 0) desc
			""")
	List<Object[]> topUsersApprovedDaysOverlappingMonth(@Param("startOfMonth") LocalDate startOfMonth,
			@Param("endOfMonth") LocalDate endOfMonth);

}