package com.inssoft.people.payroll.mapper;

import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.payroll.dto.PayPeriodResponse;
import com.inssoft.people.payroll.dto.PayReceiptResponse;
import com.inssoft.people.payroll.entity.PayPeriod;
import com.inssoft.people.payroll.entity.PayReceipt;

public final class PayrollMapper {

    private PayrollMapper() {
    }

    public static PayPeriodResponse toPeriod(PayPeriod period) {
        return new PayPeriodResponse(
                period.getUuid(),
                period.getPeriodYear(),
                period.getPeriodMonth(),
                period.getPeriodType(),
                period.getStartDate(),
                period.getEndDate(),
                period.getStatus(),
                period.label());
    }

    public static PayReceiptResponse toReceipt(PayReceipt receipt) {
        Employee employee = receipt.getEmployee();
        PayPeriod period = receipt.getPeriod();
        String employeeName = employee == null
                ? null
                : (employee.getFirstName() + " "
                   + (employee.getMiddleName() == null ? "" : employee.getMiddleName() + " ")
                   + employee.getLastName()
                   + (employee.getSecondLastName() == null ? "" : " " + employee.getSecondLastName()))
                        .replaceAll("\\s+", " ").trim();

        return new PayReceiptResponse(
                receipt.getUuid(),
                employee == null ? null : employee.getUuid(),
                employeeName,
                period == null ? null : period.getUuid(),
                period == null ? null : period.label(),
                receipt.getFileName(),
                receipt.getMimeType(),
                receipt.getSizeBytes(),
                receipt.getUploadedBy() == null ? null : receipt.getUploadedBy().getUsername(),
                receipt.getCreatedAt());
    }
}
