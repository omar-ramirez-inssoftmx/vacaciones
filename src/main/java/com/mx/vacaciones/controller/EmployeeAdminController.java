package com.mx.vacaciones.controller;

import com.inssoft.people.common.api.PageResponse;
import com.inssoft.people.common.exception.BusinessException;
import com.inssoft.people.employee.dto.EmployeeResponse;
import com.inssoft.people.employee.dto.EmployeeSummaryResponse;
import com.inssoft.people.employee.dto.TerminationRequest;
import com.inssoft.people.employee.entity.Employee;
import com.inssoft.people.employee.repository.EmployeeRepository;
import com.inssoft.people.employee.service.EmployeeService;
import com.inssoft.people.masterdata.entity.CatalogItem;
import com.inssoft.people.masterdata.repository.CatalogItemRepository;
import com.inssoft.people.masterdata.repository.CatalogRepository;
import com.inssoft.people.organization.repository.BranchRepository;
import com.inssoft.people.organization.repository.CompanyRepository;
import com.inssoft.people.organization.repository.DepartmentRepository;
import com.inssoft.people.organization.repository.PositionRepository;
import com.mx.vacaciones.web.EmployeeForm;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin/employees")
@PreAuthorize("@accessGuard.has('EMPLOYEE_VIEW_ALL')")
public class EmployeeAdminController {

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final CatalogRepository catalogRepository;
    private final CatalogItemRepository catalogItemRepository;

    public EmployeeAdminController(
            EmployeeService employeeService,
            EmployeeRepository employeeRepository,
            CompanyRepository companyRepository,
            BranchRepository branchRepository,
            DepartmentRepository departmentRepository,
            PositionRepository positionRepository,
            CatalogRepository catalogRepository,
            CatalogItemRepository catalogItemRepository) {
        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.catalogRepository = catalogRepository;
        this.catalogItemRepository = catalogItemRepository;
    }

    @GetMapping
    public String employees(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        PageResponse<EmployeeSummaryResponse> employees =
                employeeService.list(null, null, null, null, status, null, q, null, Math.max(page, 0), 20);
        model.addAttribute("empleados", employees);
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        return "admin/employees";
    }

    @GetMapping("/new")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_CREATE')")
    public String newEmployee(Model model) {
        EmployeeForm form = new EmployeeForm();
        model.addAttribute("form", form);
        model.addAttribute("editing", false);
        addFormNavigation(model, false, null);
        addFormOptions(model);
        return "admin/employee-form";
    }

    @PostMapping("/new")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_CREATE')")
    public String createEmployee(
            @ModelAttribute("form") EmployeeForm form,
            Model model,
            RedirectAttributes redirect) {
        try {
            EmployeeResponse created = employeeService.create(form.toRequest());
            redirect.addFlashAttribute("ok", "Empleado creado correctamente");
            return "redirect:/admin/employees/" + created.uuid();
        } catch (BusinessException ex) {
            model.addAttribute("form", form);
            model.addAttribute("editing", false);
            model.addAttribute("error", ex.getMessage());
            addFormNavigation(model, false, null);
            addFormOptions(model);
            return "admin/employee-form";
        }
    }

    @GetMapping("/{uuid}")
    public String employee(@PathVariable UUID uuid, Model model) {
        EmployeeResponse employee = employeeService.get(uuid, true);
        model.addAttribute("empleado", employee);
        return "admin/employee-detail";
    }

    @GetMapping("/{uuid}/edit")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_UPDATE')")
    public String editEmployee(@PathVariable UUID uuid, Model model) {
        EmployeeResponse employee = employeeService.get(uuid, true);
        model.addAttribute("form", EmployeeForm.from(employee));
        model.addAttribute("editing", true);
        model.addAttribute("employeeUuid", uuid);
        model.addAttribute("employeeName", employee.fullName());
        addFormNavigation(model, true, uuid);
        addFormOptions(model);
        return "admin/employee-form";
    }

    @PostMapping("/{uuid}/edit")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_UPDATE')")
    public String updateEmployee(
            @PathVariable UUID uuid,
            @ModelAttribute("form") EmployeeForm form,
            Model model,
            RedirectAttributes redirect) {
        try {
            employeeService.update(uuid, form.toRequest());
            redirect.addFlashAttribute("ok", "Empleado actualizado correctamente");
            return "redirect:/admin/employees/" + uuid;
        } catch (BusinessException ex) {
            model.addAttribute("form", form);
            model.addAttribute("editing", true);
            model.addAttribute("employeeUuid", uuid);
            model.addAttribute("error", ex.getMessage());
            addFormNavigation(model, true, uuid);
            addFormOptions(model);
            return "admin/employee-form";
        }
    }

    @PostMapping("/{uuid}/terminate")
    @PreAuthorize("@accessGuard.has('EMPLOYEE_TERMINATE')")
    public String terminate(
            @PathVariable UUID uuid,
            @RequestParam(required = false) LocalDate terminationDate,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirect) {
        try {
            employeeService.terminate(uuid,
                    new TerminationRequest(terminationDate, reason, "BAJA"));
            redirect.addFlashAttribute("ok", "Empleado dado de baja correctamente");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("err", ex.getMessage());
        }
        return "redirect:/admin/employees/" + uuid;
    }

    private void addFormNavigation(Model model, boolean editing, UUID uuid) {
        if (editing && uuid != null) {
            model.addAttribute("formAction", "/admin/employees/" + uuid + "/edit");
            model.addAttribute("backUrl", "/admin/employees/" + uuid);
        } else {
            model.addAttribute("formAction", "/admin/employees/new");
            model.addAttribute("backUrl", "/admin/employees");
        }
    }

    private void addFormOptions(Model model) {
        model.addAttribute("empresas",
                companyRepository.search(null, true, PageRequest.of(0, 100)).getContent());
        model.addAttribute("sucursales",
                branchRepository.search(null, null, true, PageRequest.of(0, 200)).getContent());
        model.addAttribute("departamentos",
                departmentRepository.search(null, null, true, PageRequest.of(0, 200)).getContent());
        model.addAttribute("puestos",
                positionRepository.search(null, null, true, PageRequest.of(0, 200)).getContent());
        model.addAttribute("gerentes", employeeRepository.findByDeletedAtIsNullAndEnabledTrue());
        model.addAttribute("generos", catalogItems("GENDER"));
        model.addAttribute("estadosCiviles", catalogItems("MARITAL_STATUS"));
        model.addAttribute("estatusLaboral", catalogItems("EMPLOYMENT_STATUS"));
        model.addAttribute("modalidades", catalogItems("WORK_MODALITY"));
    }

    private List<CatalogItem> catalogItems(String catalogCode) {
        return catalogRepository.findByCodeAndDeletedAtIsNull(catalogCode)
                .map(catalog -> catalogItemRepository
                        .findByCatalogIdAndDeletedAtIsNullAndEnabledTrueOrderBySortOrderAscNameAsc(catalog.getId()))
                .orElse(List.of());
    }
}
