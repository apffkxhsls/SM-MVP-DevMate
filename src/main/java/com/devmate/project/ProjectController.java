package com.devmate.project;

import com.devmate.matching.MatchStatus;
import com.devmate.matching.dto.RecommendationResult;
import com.devmate.matching.service.RecommendationService;
import com.devmate.member.security.MemberPrincipal;
import com.devmate.project.service.ProjectService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProjectController {

    private final ProjectService projectService;
    private final RecommendationService recommendationService;

    public ProjectController(
            ProjectService projectService,
            RecommendationService recommendationService
    ) {
        this.projectService = projectService;
        this.recommendationService = recommendationService;
    }

    /**
     * 빈 모집글 입력 폼을 전달한다.
     */
    @GetMapping("/projects/new")
    public String createForm(Model model) {
        model.addAttribute("projectForm", new ProjectForm());
        return "project/form";
    }

    /**
     * 로그인한 회원을 작성자로 모집글을 등록한다.
     */
    @PostMapping("/projects")
    public String create(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @ModelAttribute("projectForm") ProjectForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "project/form";
        }

        Long projectId = projectService.create(
                principal.getMemberId(),
                form
        );

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "모집글이 등록되었습니다."
        );

        return "redirect:/projects/" + projectId;
    }

    /**
     * 모집글 상세 정보를 조회한다.
     */
    @GetMapping("/projects/{projectId}")
    public String detail(
            @PathVariable Long projectId,
            Model model
    ) {
        if (projectId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "모집글 ID는 양수여야 합니다."
            );
        }

        model.addAttribute(
                "project",
                projectService.getProject(projectId)
        );

        return "project/detail";
    }

    /**
     * 로그인한 회원이 작성한 모집글만 조회한다.
     */
    @GetMapping("/my/projects")
    public String myProjects(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        validatePage(page);

        model.addAttribute(
                "projectPage",
                projectService.getMyProjects(principal.getMemberId(), page)
        );

        return "project/my-list";
    }

    /**
     * 로그인한 회원의 조건에 맞춰 모집글을 조회한다.
     */
    @GetMapping("/projects")
    public String list(
            @AuthenticationPrincipal MemberPrincipal memberPrincipal,
            @RequestParam(defaultValue = "PASS") MatchStatus status,
            @RequestParam(defaultValue = "0") int page,
            Model model
    ) {
        validatePage(page);

        RecommendationResult result =
                recommendationService.getRecommendations(
                        memberPrincipal.getMemberId(),
                        status,
                        page
                );

        model.addAttribute("projectPage", result.projectPage());
        model.addAttribute("selectedStatus", result.selectedStatus());
        model.addAttribute("profileRequired", result.profileRequired());

        return "project/list";
    }

    /**
     * 이 컨트롤러에서 조회 대상이 없으면 404로 처리한다.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public void handleNotFound(
            EntityNotFoundException exception,
            jakarta.servlet.http.HttpServletResponse response
    ) throws java.io.IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    private void validatePage(int page) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "페이지 번호는 0 이상이어야 합니다."
            );
        }
    }
}
