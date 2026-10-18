package com.inssoft.people.payroll.service;

import com.inssoft.people.common.api.ErrorCode;
import com.inssoft.people.common.api.PageRequestFactory;
import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ConflictException;
import com.inssoft.people.common.exception.ResourceNotFoundException;
import com.inssoft.people.common.security.AuthorizationService;
import com.inssoft.people.common.util.CodeNormalizer;
import com.inssoft.people.documents.storage.FileStorageService;
import com.inssoft.people.documents.storage.StoredFile;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.organization.entity.Company;
import com.inssoft.people.payroll.dto.PayPeriodRequest;
import com.inssoft.people.payroll.dto.PayPeriodResponse;
import com.inssoft.people.payroll.dto.PayReceiptResponse;
import com.inssoft.people.payroll.entity.PayPeriod;
import com.inssoft.people.payroll.entity.PayReceipt;
import com.inssoft.people.payroll.mapper.PayrollMapper;
import com.inssoft.people.payroll.repository.PayPeriodRepository;
import com.inssoft.people.payroll.repository.PayReceiptRepository;
import com.mx.vacaciones.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PayrollService {

    private static final long MAX_PDF_BYTES = 10L * 1024 * 1024;
    private static final String PDF_MIME = "application/pdf";

    private final PayPeriodRepository periodRepository;
    private final PayReceiptRepository receiptRepository;
    private final EmployeeRepository employeeRepository;
    private final FileStorageService storageService;
    private final AuditService auditService;
    private final AuthorizationService authorizationService;

    public PayrollService(
            PayPeriodRepository periodRepository,
            PayReceiptRepository receiptRepository,
            EmployeeRepository employeeRepository,
            FileStorageService storageService,
            AuditService auditService,
            AuthorizationService authorizationService) {
        this.periodRepository = periodRepository;
        this.receiptRepository = receiptRepository;
        this.employeeRepository = employeeRepository;
        this.storageService = storageService;
        this.auditService = auditService;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<PayPeriodResponse> periods(Company company) {
        return periodRepository
                .findByCompany_IdAndDeletedAtIsNullOrderByPeriodYearDescPeriodMonthDesc(company.getId())
                .stream()
                .map(PayrollMapper::toPeriod)
                .toList();
    }

    @Transactional
    public PayPeriodResponse createPeriod(Company company, PayPeriodRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException("La fecha final no puede ser anterior a la inicial");
        }
        String type = normalizeType(request.periodType());
        if (periodRepository.existsByCompany_IdAndPeriodYearAndPeriodMonthAndPeriodTypeAndDeletedAtIsNull(
                company.getId(), request.year(), request.month(), type)) {
            throw new ConflictException("Ya existe un periodo de nómina para ese año, mes y tipo");
        }

        PayPeriod period = new PayPeriod();
        period.setCompany(company);
        period.setPeriodYear(request.year());
        period.setPeriodMonth(request.month());
        period.setPeriodType(type);
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setStatus("OPEN");

        PayPeriod saved = periodRepository.save(period);
        PayPeriodResponse response = PayrollMapper.toPeriod(saved);
        auditService.record("PAYROLL_PERIOD_CREATE", "PayPeriod", saved.getUuid().toString(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<PayReceiptResponse> listSelf(String username, int page, int size) {
        Employee employee = findEmployeeByUsername(username);
        Page<PayReceipt> receipts = receiptRepository
                .findByEmployee_IdAndDeletedAtIsNull(
                        employee.getId(), PageRequestFactory.of(page, size,
                                Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(receipts, PayrollMapper::toReceipt);
    }

    @Transactional(readOnly = true)
    public PageResponse<PayReceiptResponse> search(
            Company company,
            UUID employeeUuid,
            UUID periodUuid,
            String q,
            int page,
            int size) {
        Page<PayReceipt> receipts = receiptRepository.search(
                company.getId(),
                employeeUuid,
                periodUuid,
                CodeNormalizer.trimToNull(q),
                PageRequestFactory.of(page, size,
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(receipts, PayrollMapper::toReceipt);
    }

    @Transactional(readOnly = true)
    public Download download(UUID uuid, User actor, Company company, boolean canViewAll) {
        PayReceipt receipt = findReceipt(uuid);
        authorize(receipt, actor, company, canViewAll);

        try (InputStream input = storageService.open(receipt.getStorageKey())) {
            byte[] content = input.readAllBytes();
            auditService.record("PAYROLL_DOWNLOAD", "PayReceipt",
                    receipt.getUuid().toString(), PayrollMapper.toReceipt(receipt));
            return new Download(content, receipt.getFileName(), receipt.getMimeType(), receipt.getSizeBytes());
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer el recibo: " + ex.getMessage());
        }
    }

    @Transactional
    public PayReceiptResponse upload(
            Employee employee,
            UUID periodUuid,
            MultipartFile file,
            User uploader) {

        if (file == null || file.isEmpty()) {
            throw new BusinessException("El recibo es obligatorio");
        }
        if (file.getSize() > MAX_PDF_BYTES) {
            throw new BusinessException("El recibo excede el tamaño máximo de 10 MB");
        }

        PayPeriod period = periodRepository.findByUuidAndDeletedAtIsNull(periodUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Periodo de nómina", periodUuid));
        if (!period.getCompany().getId().equals(employee.getCompany().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "El periodo no pertenece a la empresa del empleado");
        }
        if (!"OPEN".equals(period.getStatus())) {
            throw new BusinessException("El periodo de nómina está cerrado");
        }
        if (receiptRepository.existsByEmployee_IdAndPeriod_IdAndDeletedAtIsNull(
                employee.getId(), period.getId())) {
            throw new ConflictException("El empleado ya tiene un recibo para este periodo");
        }

        String fileName = sanitizeFileName(file.getOriginalFilename());
        String mimeType = file.getContentType();
        if (mimeType == null || !PDF_MIME.equalsIgnoreCase(mimeType)) {
            throw new BusinessException("El recibo debe ser un archivo PDF");
        }

        StoredFile stored;
        try {
            stored = storageService.store(file.getInputStream(), fileName, PDF_MIME);
        } catch (IOException ex) {
            throw new BusinessException("No se pudo leer el archivo: " + ex.getMessage());
        }

        try {
            PayReceipt receipt = new PayReceipt();
            receipt.setCompany(employee.getCompany());
            receipt.setEmployee(employee);
            receipt.setPeriod(period);
            receipt.setFileName(fileName);
            receipt.setMimeType(PDF_MIME);
            receipt.setSizeBytes(stored.sizeBytes());
            receipt.setSha256(stored.sha256());
            receipt.setStorageProvider(stored.provider());
            receipt.setStorageKey(stored.storageKey());
            receipt.setUploadedBy(uploader);
            receipt.setEnabled(true);

            PayReceipt saved = receiptRepository.save(receipt);
            PayReceiptResponse response = PayrollMapper.toReceipt(saved);
            auditService.record("PAYROLL_UPLOAD", "PayReceipt", saved.getUuid().toString(), response);
            return response;
        } catch (RuntimeException ex) {
            storageService.delete(stored.storageKey());
            throw ex;
        }
    }

    @Transactional
    public void delete(UUID uuid, User actor, Company company) {
        PayReceipt receipt = findReceipt(uuid);
        authorize(receipt, actor, company, true);
        PayReceiptResponse previous = PayrollMapper.toReceipt(receipt);
        receipt.setDeletedAt(java.time.Instant.now());
        receipt.setEnabled(false);
        receiptRepository.save(receipt);
        storageService.delete(receipt.getStorageKey());
        auditService.record("PAYROLL_DELETE", "PayReceipt", uuid.toString(), previous, null);
    }

    private void authorize(PayReceipt receipt, User actor, Company company, boolean canViewAll) {
        if (company != null && !receipt.getCompany().getId().equals(company.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "No puedes consultar recibos de otra empresa");
        }
        if (canViewAll || authorizationService.hasPermission("PAYROLL_MANAGE")) {
            return;
        }
        if (receipt.getEmployee() != null
                && authorizationService.canViewPayroll(actor, receipt.getEmployee())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "No puedes consultar este recibo");
    }

    private PayReceipt findReceipt(UUID uuid) {
        return receiptRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Recibo de nómina", uuid));
    }

    private Employee findEmployeeByUsername(String username) {
        return employeeRepository.findByUser_UsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe un empleado vinculado al usuario " + username));
    }

    private String normalizeType(String periodType) {
        String value = periodType == null ? "" : periodType.trim().toUpperCase(Locale.ROOT);
        if (!value.equals("MONTHLY") && !value.equals("BIWEEKLY") && !value.equals("EXTRA")) {
            throw new BusinessException("El tipo de periodo debe ser MONTHLY, BIWEEKLY o EXTRA");
        }
        return value;
    }

    private String sanitizeFileName(String original) {
        String name = original == null || original.isBlank() ? "recibo.pdf" : original.trim();
        name = name.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\r\\n\\t]", "_");
        if (name.length() > 255) {
            name = name.substring(name.length() - 255);
        }
        return name;
    }

    public record Download(byte[] content, String fileName, String mimeType, long sizeBytes) {
    }
}
