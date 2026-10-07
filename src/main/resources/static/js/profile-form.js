/* ===== 에러 표시 유틸리티 =====
    project-form.js와 동일한 패턴.
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


/* ===== 필드 참조 ===== */

const roleSelect          = document.getElementById('role');
const goalSelect          = document.getElementById('goal');
const weeklyHoursInput    = document.getElementById('weeklyHours');
const regionInput         = document.getElementById('region');
const portfolioUrlInput   = document.getElementById('portfolioUrl');
const introductionInput   = document.getElementById('introduction');
const skillsError         = document.getElementById('skillsError');
const availableSlotsError = document.getElementById('availableSlotsError');


/* ===== 필드별 검증 함수 ===== */

function validateRole() {
    if (!roleSelect.value) return showError(roleSelect, '희망 역할을 선택해주세요.'), false;
    clearError(roleSelect);
    return true;
}

/* 보유 기술: 체크박스 그룹이라 showError 대신 전용 요소 사용 */
function validateSkills() {
    const count = document.querySelectorAll('input[name="skills"]:checked').length;
    if (count === 0) {
        skillsError.textContent = '보유 기술을 1개 이상 선택해주세요.';
        return false;
    }
    skillsError.textContent = '';
    return true;
}

function validateWeeklyHours() {
    if (!weeklyHoursInput.value) return showError(weeklyHoursInput, '주당 투자 가능 시간을 입력해주세요.'), false;
    clearError(weeklyHoursInput);
    return true;
}

/* 활동 가능 시간대: 체크박스가 동적 생성이라 showError 대신 전용 요소 사용 */
function validateAvailableSlots() {
    const count = document.querySelectorAll('input[name="availableSlots"]:checked').length;
    if (count === 0) {
        availableSlotsError.textContent = '활동 가능 시간대를 1개 이상 선택해주세요.';
        return false;
    }
    availableSlotsError.textContent = '';
    return true;
}

function validateGoal() {
    if (!goalSelect.value) return showError(goalSelect, '희망 목표를 선택해주세요.'), false;
    clearError(goalSelect);
    return true;
}

/* 선택 항목: 값이 있을 때만 길이 검사 */
function validateRegion() {
    if (regionInput.value.trim().length > 100) {
        showError(regionInput, '지역은 100자 이하로 입력해주세요.');
        return false;
    }
    clearError(regionInput);
    return true;
}

function validatePortfolioUrl() {
    const value = portfolioUrlInput.value.trim();
    if (!value) { clearError(portfolioUrlInput); return true; }
    if (value.length > 2000) return showError(portfolioUrlInput, '포트폴리오 링크는 2000자 이하로 입력해주세요.'), false;
    if (!/^https?:\/\/\S+$/i.test(value)) return showError(portfolioUrlInput, 'http:// 또는 https://로 시작하는 올바른 주소를 입력해주세요.'), false;
    clearError(portfolioUrlInput);
    return true;
}

function validateIntroduction() {
    if (introductionInput.value.length > 1000) {
        showError(introductionInput, '자기소개는 1000자 이하로 입력해주세요.');
        return false;
    }
    clearError(introductionInput);
    return true;
}


/* ===== blur 시 실시간 검사 ===== */
roleSelect.addEventListener('blur', validateRole);
weeklyHoursInput.addEventListener('blur', validateWeeklyHours);
goalSelect.addEventListener('blur', validateGoal);
regionInput.addEventListener('blur', validateRegion);
portfolioUrlInput.addEventListener('blur', validatePortfolioUrl);
introductionInput.addEventListener('blur', validateIntroduction);

/* 기술을 1개 이상 선택하면 에러를 즉시 지운다 */
document.querySelectorAll('input[name="skills"]').forEach(input => {
    input.addEventListener('change', () => {
        if (document.querySelectorAll('input[name="skills"]:checked').length > 0) {
            skillsError.textContent = '';
        }
    });
});

/* 시간대를 1칸 이상 선택하면 에러를 즉시 지운다 */
document.getElementById('timeTableBody').addEventListener('change', () => {
    if (document.querySelectorAll('input[name="availableSlots"]:checked').length > 0) {
        availableSlotsError.textContent = '';
    }
});


/* ===== 제출 시 전체 검증 =====
    배열 리터럴 안에서 함수를 먼저 모두 호출한 뒤 every(Boolean)로 판정하므로
    첫 번째 실패에서 멈추지 않고 모든 필드의 오류를 한 번에 표시한다. */
const profileForm = document.querySelector('.profile-form');
if (profileForm) {
    profileForm.addEventListener('submit', (e) => {
        const ok = [
            validateRole(),
            validateSkills(),
            validateWeeklyHours(),
            validateAvailableSlots(),
            validateGoal(),
            validateRegion(),
            validatePortfolioUrl(),
            validateIntroduction(),
        ].every(Boolean);
        if (!ok) e.preventDefault();
    });
}
