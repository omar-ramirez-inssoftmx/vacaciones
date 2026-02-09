package com.mx.vacaciones.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.mx.vacaciones.model.User;
import com.mx.vacaciones.repository.UserRepository;
import com.mx.vacaciones.service.UserService;
import com.mx.vacaciones.service.VacationPolicyService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

	private final UserService userService;
	private final UserRepository userRepository;
	private final VacationPolicyService policy;

	@GetMapping
	public String usersList(Model model, @RequestParam(required = false) String ok,
			@RequestParam(required = false) String err) {
		model.addAttribute("users", userService.findAll());
		model.addAttribute("ok", ok);
		model.addAttribute("err", err);
		return "admin/users";
	}

	@PostMapping("/create")
	public String createUser(@RequestParam String name, @RequestParam String username, @RequestParam String email, @RequestParam String password,
			@RequestParam int vacationDaysAvailable, @RequestParam String role, @RequestParam LocalDate hireDate) {

		// (opcional) normalizar
		email = email.trim().toLowerCase();

		try {
			userService.createUser(name, username, email, password, vacationDaysAvailable, role, hireDate);
			return "redirect:/admin/users?ok=Usuario%20creado";
		} catch (org.springframework.dao.DataIntegrityViolationException e) {
			// Mensaje corto y seguro (sin acentos si quieres evitar TODO)
			return "redirect:/admin/users?err=EMAIL_DUPLICADO";

		} catch (Exception e) {
			return "redirect:/admin/users?err=ERROR_AL_CREAR";
		}
	}

	@PostMapping("/{id}/update")
	public String updateUser(@PathVariable Long id, @RequestParam String name, @RequestParam String email, @RequestParam String role,
			@RequestParam Integer vacationDaysAvailable, @RequestParam(defaultValue = "false") boolean enabled) {
		try {
			System.out.println("ENABLED RECIBIDO = " + enabled);

			userService.updateUser(id, name, email, role, vacationDaysAvailable, enabled);
			return "redirect:/admin/users?ok=Usuario%20actualizado";
		} catch (Exception e) {
			return "redirect:/admin/users?err=" + encode(e.getMessage());
		}
	}

	@PostMapping("/{id}/reset-password")
	public String resetPassword(@PathVariable Long id, @RequestParam String password) {
		try {
			userService.resetPassword(id, password);
			return "redirect:/admin/users?ok=Password%20actualizado";
		} catch (Exception e) {
			return "redirect:/admin/users?err=" + encode(e.getMessage());
		}
	}

	private String encode(String s) {
		return s == null ? "" : s.replace(" ", "%20");
	}

	@PostMapping("/vacations/recalc")
	@Transactional
	public String recalcAllUsers() {
		LocalDate today = LocalDate.now();
		List<User> users = userRepository.findAll();

		for (User u : users) {
			if (u.getHireDate() == null)
				continue;

			int years = policy.yearsOfService(u.getHireDate(), today);
			int entitled = policy.entitledDaysByYears(years);

			u.setVacationDaysEntitled(entitled);
			u.setVacationDaysAvailable(entitled); // política 1
			userRepository.save(u);
		}
		return "redirect:/admin/users?ok=Recálculo realizado";

	}

}
