package com.inssoft.people.common.audit;

public interface AuditService {

    void record(String action, String entity, String entityId, Object newValue);

    void record(String action, String entity, String entityId, Object oldValue, Object newValue);
}
