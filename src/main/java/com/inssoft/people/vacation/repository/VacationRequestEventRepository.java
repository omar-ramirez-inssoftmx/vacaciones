package com.inssoft.people.vacation.repository;

import com.inssoft.people.vacation.entity.VacationRequestEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VacationRequestEventRepository extends JpaRepository<VacationRequestEvent, Long> {

    List<VacationRequestEvent> findByRequest_IdOrderByCreatedAtAsc(Long requestId);
}
