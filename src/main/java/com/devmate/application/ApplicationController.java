package com.devmate.application;

import com.devmate.application.exception.ApplicationConflictException;
import com.devmate.application.service.ApplicationService;
import com.devmate.member.security.MemberPrincipal;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 로그인한 회원이 모집글에 지원한다.
     */
    @PostMapping("/projects/{projectId}/applications")
    public String apply(
            @PathVariable("projectId") Long projectId,
            @AuthenticationPrincipal MemberPrincipal principal,
            RedirectAttributes redirectAttributes
    ) {
        validateId(projectId);

        try {
            applicationService.apply(
                    projectId,
                    principal.getMemberId()
            );
        } catch (ApplicationConflictException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );

            return "redirect:/projects/" + projectId;
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "프로젝트 지원이 완료되었습니다."
        );

        return "redirect:/my/applications";
    }

    /**
     * 로그인한 회원 본인의 지원 목록을 조회한다.
     */
    @GetMapping("/my/applications")
    public String myApplications(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model
    ) {
        validatePage(page);

        model.addAttribute(
                "applicationPage",
                applicationService.getMyApplications(
                        principal.getMemberId(),
                        page
                )
        );

        return "application/my-list";
    }

    /**
     * 모집글 작성자가 지원자 목록을 조회한다.
     * 작성자 권한은 서비스에서 검사한다.
     */
    @GetMapping("/projects/{projectId}/applications")
    public String applicants(
            @PathVariable("projectId") Long projectId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "page", defaultValue = "0") int page,
            Model model
    ) {
        validateId(projectId);
        validatePage(page);

        model.addAttribute(
                "applicationPage",
                applicationService.getProjectApplications(
                        projectId,
                        principal.getMemberId(),
                        page
                )
        );
        model.addAttribute("projectId", projectId);

        return "application/applicants";
    }

    /**
     * 모집글 작성자가 지원자를 수락한다.
     */
    @PostMapping("/applications/{applicationId}/accept")
    public String accept(
            @PathVariable("applicationId") Long applicationId,
            @AuthenticationPrincipal MemberPrincipal principal,
            RedirectAttributes redirectAttributes
    ) {
        validateId(applicationId);

        Long authorId = principal.getMemberId();
        Long projectId = applicationService.getProjectIdForAuthor(
                applicationId,
                authorId
        );

        try {
            applicationService.accept(applicationId, authorId);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "지원자를 수락하고 모집을 마감했습니다."
            );
        } catch (ApplicationConflictException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/projects/" + projectId + "/applications";
    }

    /**
     * 모집글 작성자가 대기 중인 지원을 거절한다.
     */
    @PostMapping("/applications/{applicationId}/reject")
    public String reject(
            @PathVariable("applicationId") Long applicationId,
            @AuthenticationPrincipal MemberPrincipal principal,
            RedirectAttributes redirectAttributes
    ) {
        validateId(applicationId);

        Long authorId = principal.getMemberId();
        Long projectId = applicationService.getProjectIdForAuthor(
                applicationId,
                authorId
        );

        try {
            applicationService.reject(applicationId, authorId);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "지원을 거절했습니다."
            );
        } catch (ApplicationConflictException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    exception.getMessage()
            );
        }

        return "redirect:/projects/" + projectId + "/applications";
    }

    /**
     * 조회 대상이 없으면 404로 처리한다.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public void handleNotFound(
            EntityNotFoundException exception,
            HttpServletResponse response
    ) throws IOException {
        response.sendError(HttpStatus.NOT_FOUND.value());
    }

    /**
     * 모집글 작성자 권한이 없으면 403으로 처리한다.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(
            AccessDeniedException exception,
            HttpServletResponse response
    ) throws IOException {
        response.sendError(HttpStatus.FORBIDDEN.value());
    }

    private void validateId(Long id) {
        if (id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ID는 양수여야 합니다."
            );
        }
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