package com.inssoft.people.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditServiceImpl(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entity, String entityId, Object newValue) {
        record(action, entity, entityId, null, newValue);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entity, String entityId, Object oldValue, Object newValue) {
        record(action, entity, entityId, oldValue, newValue, "SUCCESS");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entity, String entityId, Object oldValue, Object newValue, String result) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActorUsername(currentUsername());
            entry.setAction(action);
            entry.setEntity(entity);
            entry.setEntityId(entityId);
            entry.setOldValue(toJson(oldValue));
            entry.setNewValue(toJson(newValue));
            entry.setCorrelationId(MDC.get("requestId"));
            entry.setResult(result == null ? "SUCCESS" : result);

            HttpServletRequest request = currentRequest();
            if (request != null) {
                entry.setIp(request.getRemoteAddr());
                String userAgent = request.getHeader("User-Agent");
                entry.setUserAgent(userAgent != null && userAgent.length() > 500
                        ? userAgent.substring(0, 500)
                        : userAgent);
            }

            repository.save(entry);
        } catch (Exception ex) {
            // La auditoría no debe tumbar la operación de negocio, pero sí debe quedar evidencia.
            log.error("No se pudo registrar auditoría action={} entity={} entityId={}",
                    action, entity, entityId, ex);
        }
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("No se pudo serializar valor de auditoría", ex);
            return String.valueOf(value);
        }
    }
}
