/* ===== 에러 표시 유틸리티 =====
    auth.js와 동일한 패턴.
    input의 부모 요소(.form-field) 안에서 .field-error를 찾아 메시지를 넣거나,
    없으면 새로 만들어서 input 바로 뒤에 삽입한다. */

function showError(input, message) {
    let errorEl = input.parentElement.querySelector('.field-error');
    if (!errorEl) {
        errorEl = document.createElement('p');
        errorEl.className = 'field-error';
        input.after(errorEl);
    }
    errorEl.textContent = message;
    input.setAttribute('aria-invalid', 'true');
}

function clearError(input) {
    const errorEl = input.parentElement.querySelector('.field-error');
    if (errorEl) errorEl.textContent = '';
    input.removeAttribute('aria-invalid');
}


/* ===== 1. 필수·우대 기술 중복 선택 방지 ===== */
const requiredSkills  = document.querySelectorAll('input[name="requiredSkills"]');
const preferredSkills = document.querySelectorAll('input[name="preferredSkills"]');

function updateSkillOptions() {
    const selectedRequired = new Set(
        [...requiredSkills].filter(input => input.checked).map(input => input.value)
    );
    const selectedPreferred = new Set(
        [...preferredSkills].filter(input => input.checked).map(input => input.value)
    );

    // 우대에서 선택한 기술은 필수에서 선택 불가
    requiredSkills.forEach(input => { input.disabled = selectedPreferred.has(input.value); });
    // 필수에서 선택한 기술은 우대에서 선택 불가
    preferredSkills.forEach(input => { input.disabled = selectedRequired.has(input.value); });
}

[...requiredSkills, ...preferredSkills].forEach(input => {
    input.addEventListener('change', updateSkillOptions);
});

updateSkillOptions();


/* ===== 2. 대면 선택 시에만 지역 입력 =====
    required 속성은 JS 검증(validateRegion)으로 처리하므로 여기서는 설정하지 않는다. */
const meetingType = document.getElementById('meetingType');
const regionField = document.getElementById('regionField');
const regionInput = document.getElementById('region');

function updateRegionField() {
    const isOffline = meetingType.value === 'OFFLINE';
    regionField.hidden  = !isOffline;
    regionInput.disabled = !isOffline;
}

meetingType.addEventListener('change', updateRegionField);
updateRegionField();


/* ===== 3. 필수 항목 검증 ===== */

const titleInput           = document.getElementById('title');
const descriptionInput     = document.getElementById('description');
const roleSelect           = document.getElementById('role');
const requiredWeeklyHoursInput = document.getElementById('requiredWeeklyHours');
const minimumCommonHoursInput  = document.getElementById('minimumCommonHours');
const desiredCommonHoursInput  = document.getElementById('desiredCommonHours');
const goalSelect           = document.getElementById('goal');
const deadlineInput        = document.getElementById('deadline');
const availableSlotsError  = document.getElementById('availableSlotsError');

function validateTitle() {
    const value = titleInput.value.trim();
    if (!value) return showError(titleInput, '프로젝트 제목을 입력해주세요.'), false;
    if (value.length > 100) return showError(titleInput, '제목은 100자 이하로 입력해주세요.'), false;
    clearError(titleInput);
    return true;
}

function validateDescription() {
    const value = descriptionInput.value.trim();
    if (!value) return showError(descriptionInput, '상세 설명을 입력해주세요.'), false;
    if (value.length > 5000) return showError(descriptionInput, '상세 설명은 5000자 이하로 입력해주세요.'), false;
    clearError(descriptionInput);
    return true;
}

function validateRole() {
    if (!roleSelect.value) return showError(roleSelect, '모집 역할을 선택해주세요.'), false;
    clearError(roleSelect);
    return true;
}

/* 시간 숫자 입력 공통 검증: 비어있는지만 확인 (범위 초과는 서버에서 처리) */
function validateHoursInput(input, label) {
    if (!input.value) return showError(input, `${label}을 입력해주세요.`), false;
    clearError(input);
    return true;
}

/* 팀 활동 가능 시간대: 체크박스가 동적 생성이라 showError 대신 전용 요소 사용 */
function validateAvailableSlots() {
    const count = document.querySelectorAll('input[name="availableSlots"]:checked').length;
    if (count === 0) {
        availableSlotsError.textContent = '팀 활동 가능 시간대를 선택해주세요.';
        return false;
    }
    availableSlotsError.textContent = '';
    return true;
}

function validateGoal() {
    if (!goalSelect.value) return showError(goalSelect, '프로젝트 목표를 선택해주세요.'), false;
    clearError(goalSelect);
    return true;
}

function validateMeetingType() {
    if (!meetingType.value) return showError(meetingType, '협업 방식을 선택해주세요.'), false;
    clearError(meetingType);
    return true;
}

/* 지역: 대면 선택 시에만 검사 */
function validateRegion() {
    if (meetingType.value === 'OFFLINE' && !regionInput.value.trim()) {
        showError(regionInput, '대면 프로젝트는 진행 지역을 입력해주세요.');
        return false;
    }
    clearError(regionInput);
    return true;
}

function validateDeadline() {
    if (!deadlineInput.value) return showError(deadlineInput, '모집 마감 일시를 입력해주세요.'), false;
    clearError(deadlineInput);
    return true;
}

/* blur 시 실시간 검사 */
titleInput.addEventListener('blur', validateTitle);
descriptionInput.addEventListener('blur', validateDescription);
roleSelect.addEventListener('blur', validateRole);
requiredWeeklyHoursInput.addEventListener('blur', () => validateHoursInput(requiredWeeklyHoursInput, '요구 주당 시간'));
minimumCommonHoursInput.addEventListener('blur',  () => validateHoursInput(minimumCommonHoursInput,  '최소 공통 시간'));
desiredCommonHoursInput.addEventListener('blur',  () => validateHoursInput(desiredCommonHoursInput,  '희망 공통 시간'));
goalSelect.addEventListener('blur', validateGoal);
meetingType.addEventListener('blur', validateMeetingType);
regionInput.addEventListener('blur', validateRegion);
deadlineInput.addEventListener('blur', validateDeadline);

/* 시간대를 1칸 이상 선택하면 에러를 즉시 지운다 */
document.getElementById('timeTableBody').addEventListener('change', () => {
    if (document.querySelectorAll('input[name="availableSlots"]:checked').length > 0) {
        availableSlotsError.textContent = '';
    }
});


/* ===== 4. 시간 조건 검증 (최소 공통 ≤ 희망 공통 ≤ 요구 주당) ===== */
const hoursOrderError = document.getElementById('hoursOrderError');

function validateTimeOrder() {
    // 세 필드 모두 입력된 경우에만 조건 검사 (빈 값은 validateHoursInput에서 처리)
    if (!minimumCommonHoursInput.value || !desiredCommonHoursInput.value || !requiredWeeklyHoursInput.value) {
        hoursOrderError.textContent = '';
        return true;
    }

    const min     = Number(minimumCommonHoursInput.value);
    const desired = Number(desiredCommonHoursInput.value);
    const weekly  = Number(requiredWeeklyHoursInput.value);

    if (min <= desired && desired <= weekly) {
        hoursOrderError.textContent = '';
        return true;
    }

    hoursOrderError.textContent = '최소 공통 시간 ≤ 희망 공통 시간 ≤ 요구 주당 시간이어야 합니다.';
    return false;
}

/* blur 시 실시간 검사 */
[requiredWeeklyHoursInput, minimumCommonHoursInput, desiredCommonHoursInput].forEach(input => {
    input.addEventListener('blur', validateTimeOrder);
});


/* ===== 제출 시 전체 검증 =====
    배열 리터럴 안에서 함수를 먼저 모두 호출한 뒤 every(Boolean)로 판정하므로
    첫 번째 실패에서 멈추지 않고 모든 필드의 오류를 한 번에 표시한다. */
const projectForm = document.querySelector('.project-form');
if (projectForm) {
    projectForm.addEventListener('submit', (e) => {
        const ok = [
            validateTitle(),
            validateDescription(),
            validateRole(),
            validateHoursInput(requiredWeeklyHoursInput, '요구 주당 시간'),
            validateHoursInput(minimumCommonHoursInput,  '최소 공통 시간'),
            validateHoursInput(desiredCommonHoursInput,  '희망 공통 시간'),
            validateTimeOrder(),
            validateAvailableSlots(),
            validateGoal(),
            validateMeetingType(),
            validateRegion(),
            validateDeadline(),
        ].every(Boolean);
        if (!ok) e.preventDefault();
    });
}
