package com.inssoft.people.payroll.service;

import com.inssoft.people.common.audit.AuditService;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.common.exception.ConflictException;
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
import com.inssoft.people.payroll.repository.PayPeriodRepository;
import com.inssoft.people.payroll.repository.PayReceiptRepository;
import com.mx.vacaciones.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock
    private PayPeriodRepository periodRepository;

    @Mock
    private PayReceiptRepository receiptRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private FileStorageService storageService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private PayrollService service;

    @Test
    void createsPeriod() {
        Company company = company(1L);
        when(periodRepository.existsByCompany_IdAndPeriodYearAndPeriodMonthAndPeriodTypeAndDeletedAtIsNull(
                1L, 2026, 10, "MONTHLY")).thenReturn(false);
        when(periodRepository.save(any(PayPeriod.class))).thenAnswer(invocation -> {
            PayPeriod period = invocation.getArgument(0);
            period.setId(1L);
            period.setUuid(UUID.randomUUID());
            period.setCreatedAt(Instant.now());
            return period;
        });

        PayPeriodResponse response = service.createPeriod(company, new PayPeriodRequest(
                2026, 10, "monthly", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        assertThat(response.periodType()).isEqualTo("MONTHLY");
        assertThat(response.status()).isEqualTo("OPEN");
        verify(auditService).record(eq("PAYROLL_PERIOD_CREATE"), eq("PayPeriod"), any(), any());
    }

    @Test
    void rejectsDuplicatePeriod() {
        when(periodRepository.existsByCompany_IdAndPeriodYearAndPeriodMonthAndPeriodTypeAndDeletedAtIsNull(
                1L, 2026, 10, "MONTHLY")).thenReturn(true);

        assertThatThrownBy(() -> service.createPeriod(company(1L), new PayPeriodRequest(
                2026, 10, "MONTHLY", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void uploadsPdfReceipt() {
        Company company = company(1L);
        Employee employee = employee(company, user(5L, "colaborador"));
        PayPeriod period = period(company);
        User uploader = user(2L, "admin");
        MockMultipartFile file = new MockMultipartFile(
                "file", "recibo.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8));

        when(periodRepository.findByUuidAndDeletedAtIsNull(period.getUuid())).thenReturn(Optional.of(period));
        when(receiptRepository.existsByEmployee_IdAndPeriod_IdAndDeletedAtIsNull(1L, 1L)).thenReturn(false);
        when(storageService.store(any(), anyString(), eq("application/pdf")))
                .thenReturn(new StoredFile(UUID.randomUUID(), "DATABASE", 3, "a".repeat(64)));
        when(receiptRepository.save(any(PayReceipt.class))).thenAnswer(invocation -> {
            PayReceipt receipt = invocation.getArgument(0);
            receipt.setId(1L);
            receipt.setUuid(UUID.randomUUID());
            receipt.setCreatedAt(Instant.now());
            return receipt;
        });

        PayReceiptResponse response = service.upload(employee, period.getUuid(), file, uploader);

        assertThat(response.fileName()).isEqualTo("recibo.pdf");
        assertThat(response.employeeName()).contains("Colaborador");
        verify(auditService).record(eq("PAYROLL_UPLOAD"), eq("PayReceipt"), any(), any());
    }

    @Test
    void rejectsNonPdfReceipt() {
        Company company = company(1L);
        Employee employee = employee(company, user(5L, "colaborador"));
        PayPeriod period = period(company);
        MockMultipartFile file = new MockMultipartFile(
                "file", "recibo.txt", "text/plain", "texto".getBytes(StandardCharsets.UTF_8));

        when(periodRepository.findByUuidAndDeletedAtIsNull(period.getUuid())).thenReturn(Optional.of(period));

        assertThatThrownBy(() -> service.upload(employee, period.getUuid(), file, user(2L, "admin")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void downloadsOwnReceipt() {
        Company company = company(1L);
        User actor = user(5L, "colaborador");
        Employee employee = employee(company, actor);
        PayPeriod period = period(company);
        PayReceipt receipt = receipt(company, employee, period);
        when(receiptRepository.findByUuidAndDeletedAtIsNull(receipt.getUuid())).thenReturn(Optional.of(receipt));
        when(storageService.open(receipt.getStorageKey()))
                .thenReturn(new ByteArrayInputStream("pdf".getBytes(StandardCharsets.UTF_8)));

        PayrollService.Download download = service.download(receipt.getUuid(), actor, company, false);

        assertThat(new String(download.content(), StandardCharsets.UTF_8)).isEqualTo("pdf");
        assertThat(download.fileName()).isEqualTo("recibo.pdf");
    }

    @Test
    void rejectsReceiptFromAnotherEmployee() {
        Company company = company(1L);
        Employee employee = employee(company, user(5L, "colaborador"));
        PayReceipt receipt = receipt(company, employee, period(company));
        when(receiptRepository.findByUuidAndDeletedAtIsNull(receipt.getUuid())).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.download(receipt.getUuid(), user(9L, "otro"), company, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No puedes consultar");
    }

    private Company company(Long id) {
        Company company = new Company();
        company.setId(id);
        company.setUuid(UUID.randomUUID());
        return company;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("hash");
        return user;
    }

    private Employee employee(Company company, User user) {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setUuid(UUID.randomUUID());
        employee.setCompany(company);
        employee.setUser(user);
        employee.setFirstName("Colaborador");
        employee.setLastName("Prueba");
        return employee;
    }

    private PayPeriod period(Company company) {
        PayPeriod period = new PayPeriod();
        period.setId(1L);
        period.setUuid(UUID.randomUUID());
        period.setCompany(company);
        period.setPeriodYear(2026);
        period.setPeriodMonth(10);
        period.setPeriodType("MONTHLY");
        period.setStartDate(LocalDate.of(2026, 10, 1));
        period.setEndDate(LocalDate.of(2026, 10, 31));
        period.setStatus("OPEN");
        return period;
    }

    private PayReceipt receipt(Company company, Employee employee, PayPeriod period) {
        PayReceipt receipt = new PayReceipt();
        receipt.setId(1L);
        receipt.setUuid(UUID.randomUUID());
        receipt.setCompany(company);
        receipt.setEmployee(employee);
        receipt.setPeriod(period);
        receipt.setFileName("recibo.pdf");
        receipt.setMimeType("application/pdf");
        receipt.setSizeBytes(3);
        receipt.setSha256("a".repeat(64));
        receipt.setStorageKey(UUID.randomUUID());
        receipt.setCreatedAt(Instant.now());
        return receipt;
    }
}
