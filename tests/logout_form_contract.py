"""Static safety check for the native logout route. Run: python tests/logout_form_contract.py"""
from pathlib import Path
root = Path(__file__).resolve().parents[1]
header = (root / 'src/main/resources/templates/fragments/header.html').read_text(encoding='utf-8')
controller = (root / 'src/main/java/vn/edu/ute/milktea/controller/BrowserLogoutController.java').read_text(encoding='utf-8')
api = (root / 'src/main/java/vn/edu/ute/milktea/controller/AuthController.java').read_text(encoding='utf-8')
security = (root / 'src/main/java/vn/edu/ute/milktea/security/SecurityConfig.java').read_text(encoding='utf-8')
assert header.count('th:action="@{/signout}"') == 2
assert header.count('method="post"') >= 2
assert header.count('th:name="${_csrf.parameterName}"') == 2
assert 'logoutAccount()' not in header
assert 'sec:authorize="isAuthenticated()" method="post"' in header
assert '@PostMapping("/signout")' in controller
assert 'authService.logout(actor.getAccountId())' in controller
assert 'csrfTokens.saveToken(null, request, response)' in controller
assert 'expired.setMaxAge(0)' in controller
assert 'expired.setSecure(secureCookie)' in controller
assert 'return "redirect:/login?loggedOut"' in controller
assert '"/signout"' in security and '"/api/v1/auth/**"' in security
assert 'cookie.setSecure(secureCookie)' in api
print('PASS: browser logout form, CSRF, server-side JWT revocation, Secure cookie expiry')
