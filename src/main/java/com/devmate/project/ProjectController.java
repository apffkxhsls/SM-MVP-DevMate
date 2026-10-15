package com.devmate.project;

import com.devmate.matching.MatchStatus;
import com.devmate.matching.dto.RecommendationResult;
import com.devmate.matching.service.RecommendationService;
import com.devmate.member.security.MemberPrincipal;
import com.devmate.project.service.ProjectService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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
     * 모집글 상세와 로그인 회원의 지원·작성자 여부를 전달한다.
     */
    @GetMapping("/projects/{projectId}")
    public String detail(
            @PathVariable("projectId") Long projectId,
            @AuthenticationPrincipal MemberPrincipal principal,
            Model model
    ) {
        if (projectId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "모집글 ID는 양수여야 합니다."
            );
        }

        var project = projectService.getProject(projectId);
        Long memberId = principal.getMemberId();

        model.addAttribute("project", project);
        model.addAttribute(
                "applied",
                projectService.hasApplied(projectId, memberId)
        );
        model.addAttribute(
                "isAuthor",
                memberId.equals(project.authorId())
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
     * 작성자가 모집글을 직접 마감한다.
     * 작성자 ID는 로그인 인증 정보에서 가져온다.
     */
    @PostMapping("/projects/{projectId}/close")
    public String close(
            @PathVariable("projectId") Long projectId,
            @AuthenticationPrincipal MemberPrincipal principal,
            RedirectAttributes redirectAttributes
    ) {
        if (projectId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "모집글 ID는 양수여야 합니다."
            );
        }

        boolean closed = projectService.close(
                projectId,
                principal.getMemberId()
        );

        if (closed) {
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "모집을 마감하고 남은 대기 지원을 거절했습니다."
            );
        } else {
            redirectAttributes.addFlashAttribute(
                    "infoMessage",
                    "이미 마감된 모집글입니다."
            );
        }

        return "redirect:/projects/" + projectId;

    }

    /**
     * 작성자 권한이 없으면 403으로 처리한다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(
            AccessDeniedException exception,
            jakarta.servlet.http.HttpServletResponse response
    ) throws java.io.IOException {
        response.sendError(HttpStatus.FORBIDDEN.value());
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
