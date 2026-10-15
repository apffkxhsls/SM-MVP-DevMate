package com.devmate.profile.dto;

import com.devmate.profile.Profile;

import java.util.List;

public record ApplicantProfileView(
        String name,
        List<String> skills,
        List<Integer> availableSlots
) {

    public static ApplicantProfileView from(Profile profile) {
        return new ApplicantProfileView(
                profile.getMember().getName(),
                profile.getSkills().stream()
                        .map(skill -> skill.name())
                        .sorted()
                        .toList(),
                profile.getAvailableSlots().stream()
                        .sorted()
                        .toList()
        );
    }
}
