package com.pedeaqui.keycloak.smsotp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * LAC-18 end to end against a REAL Keycloak 26.8.1 (Testcontainers): loads the packaged provider jar and the
 * repository realm ({@code keycloak/delivery-realm.json}), then drives the browser flow of the customer client
 * {@code pede-aqui-web} with Authorization Code + PKCE exactly like a browser (cookies, forms, redirects). The SMS
 * stub runs with reveal-in-dev-mode so the test can read the code from the container log.
 */
class PhoneOtpKeycloakIT {
    private static final boolean DOCKER = DockerClientFactory.instance().isDockerAvailable();
    private static final Path JAR = Paths.get("target", "pede-aqui-keycloak-sms-otp.jar").toAbsolutePath();
    private static final Path REALM = Paths.get("..", "..", "delivery-realm.json").toAbsolutePath().normalize();
    private static final String WEB_REDIRECT = "http://localhost:5173/auth/callback";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.8.1")
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
            .withEnv("KC_SPI_AUTHENTICATOR__PEDE_AQUI_PHONE_OTP__HMAC_SECRET", "it-only-secret-it-only-secret-0123456789")
            .withEnv("KC_SPI_SMS_SENDER__LOG__REVEAL_CODE_IN_DEV_MODE", "true")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/delivery").forPort(8080).withStartupTimeout(Duration.ofMinutes(8)));

    @BeforeAll
    static void start() {
        Assumptions.assumeTrue(DOCKER, "Docker is required for the Keycloak provider test");
        KEYCLOAK.withCopyFileToContainer(MountableFile.forHostPath(JAR), "/opt/keycloak/providers/pede-aqui-keycloak-sms-otp.jar")
                .withCopyFileToContainer(MountableFile.forHostPath(REALM), "/opt/keycloak/data/import/delivery-realm.json");
        KEYCLOAK.start();
    }

    private static String base() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    /** One browser: own cookie jar, never follows redirects (we inspect them). */
    private static final class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        final String verifier = randomUrlSafe(48);

        HttpResponse<String> get(String url) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String url, Map<String, String> form) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(encode(form))).build(), HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> start(String clientId, String redirectUri, String loginHint) throws Exception {
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            Map<String, String> q = new LinkedHashMap<>();
            q.put("client_id", clientId);
            q.put("redirect_uri", redirectUri);
            q.put("response_type", "code");
            q.put("scope", "openid");
            q.put("state", randomUrlSafe(12));
            q.put("code_challenge", challenge);
            q.put("code_challenge_method", "S256");
            if (loginHint != null) {
                q.put("login_hint", loginHint);
            }
            HttpResponse<String> response = get(base() + "/realms/delivery/protocol/openid-connect/auth?" + encode(q));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            return response;
        }

        /** Submits the page's form with {@code values} (other inputs keep their value); action = submit button. */
        HttpResponse<String> submit(HttpResponse<String> page, String formSelector, String buttonAction,
                                    Map<String, String> values) throws Exception {
            Document doc = Jsoup.parse(page.body(), page.uri().toString());
            Element form = doc.selectFirst(formSelector);
            assertThat(form).as("form %s in %s", formSelector, page.body()).isNotNull();
            Map<String, String> data = new LinkedHashMap<>();
            for (Element input : form.select("input[name]")) {
                if (!"checkbox".equals(input.attr("type"))) {
                    data.put(input.attr("name"), input.val());
                }
            }
            data.putAll(values);
            if (buttonAction != null) {
                data.put("action", buttonAction);
            }
            return post(form.absUrl("action"), data);
        }

        Map<String, String> exchange(String location, String redirectUri) throws Exception {
            Matcher m = Pattern.compile("[?&]code=([^&]+)").matcher(location);
            assertThat(m.find()).as(location).isTrue();
            Map<String, String> form = Map.of("grant_type", "authorization_code", "code", m.group(1),
                    "redirect_uri", redirectUri, "client_id", "pede-aqui-web", "code_verifier", verifier);
            HttpResponse<String> token = post(base() + "/realms/delivery/protocol/openid-connect/token", form);
            assertThat(token.statusCode()).as(token.body()).isEqualTo(200);
            String accessToken = jsonString(token.body(), "access_token");
            String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]), StandardCharsets.UTF_8);
            return Map.of("payload", payload);
        }
    }

    private static String encode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String randomUrlSafe(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String jsonString(String json, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertThat(m.find()).as("%s in %s", field, json).isTrue();
        return m.group(1);
    }

    /** Latest code the SMS stub logged for the masked number (waits for the log line). */
    private static String codeFor(String maskedPhone, int expectedCount) throws InterruptedException {
        Pattern p = Pattern.compile("SMS \\(stub, not sent\\) to " + Pattern.quote(maskedPhone) + ": .*? (\\d{6})\\.");
        for (int i = 0; i < 50; i++) {
            Matcher m = p.matcher(KEYCLOAK.getLogs());
            String last = null;
            int count = 0;
            while (m.find()) {
                last = m.group(1);
                count++;
            }
            if (count >= expectedCount) {
                return last;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("No SMS logged for " + maskedPhone);
    }

    private static long smsCount(String maskedPhone) {
        return KEYCLOAK.getLogs().lines().filter(l -> l.contains("SMS (stub, not sent) to " + maskedPhone)).count();
    }

    @Test
    void providerLoadsWithConfiguredSecret() {
        assertThat(KEYCLOAK.getLogs()).contains("pede-aqui-phone-otp: HMAC secret source=config");
    }

    @Test
    void newCustomerLogsInWithPhoneAndGetsPhoneClaimsAndCustomerRole() throws Exception {
        Browser browser = new Browser();
        HttpResponse<String> phonePage = browser.start("pede-aqui-web", WEB_REDIRECT, null);
        assertThat(phonePage.body()).contains("kc-phone-otp-phone-form");

        HttpResponse<String> codePage = browser.submit(phonePage, "#kc-phone-otp-phone-form", "send", Map.of("phone", "84 123 4567"));
        assertThat(codePage.statusCode()).isEqualTo(200);
        assertThat(codePage.body()).contains("kc-phone-otp-code-form").contains("+25884*****67").doesNotContain("841234567");

        String code = codeFor("+25884*****67", 1);
        HttpResponse<String> next = browser.submit(codePage, "#kc-phone-otp-code-form", "verify", Map.of("code", code));
        if (next.statusCode() == 200) {
            // First login of a phone-only customer: Keycloak asks for the required profile fields (name).
            assertThat(next.body()).contains("firstName");
            next = browser.submit(next, "#kc-update-profile-form", null, Map.of("firstName", "Ana", "lastName", "Mussa"));
        }
        assertThat(next.statusCode()).as(next.body()).isEqualTo(302);
        String location = next.headers().firstValue("Location").orElseThrow();
        assertThat(location).startsWith(WEB_REDIRECT);

        String payload = browser.exchange(location, WEB_REDIRECT).get("payload");
        assertThat(payload).contains("\"phone_number\":\"+258841234567\"")
                .contains("\"phone_number_verified\":true")
                .contains("\"CUSTOMER\"")
                .contains("\"azp\":\"pede-aqui-web\"")
                .containsPattern("\"preferred_username\":\"phone-[0-9a-f-]{36}\"")
                .doesNotContain("tenant_id");

        // Second login with the same number reuses the account (no new user, no profile page).
        Browser again = new Browser();
        HttpResponse<String> phone2 = again.start("pede-aqui-web", WEB_REDIRECT, "+258841234567");
        assertThat(Jsoup.parse(phone2.body()).selectFirst("#phone").val()).isEqualTo("+258841234567");
        Thread.sleep(61_000); // resend cooldown of the first login
        HttpResponse<String> code2Page = again.submit(phone2, "#kc-phone-otp-phone-form", "send", Map.of());
        String code2 = codeFor("+25884*****67", 2);
        HttpResponse<String> done = again.submit(code2Page, "#kc-phone-otp-code-form", "verify", Map.of("code", code2));
        assertThat(done.statusCode()).as(done.body()).isEqualTo(302);
        String sub1 = jsonString(payload, "sub");
        String sub2 = jsonString(again.exchange(done.headers().firstValue("Location").orElseThrow(), WEB_REDIRECT).get("payload"), "sub");
        assertThat(sub2).isEqualTo(sub1);

        assertThat(KEYCLOAK.getLogs()).doesNotContain("+258841234567").doesNotContain(" 841234567");
    }

    @Test
    void fiveWrongCodesLockThePhoneEvenForTheRightCode() throws Exception {
        Browser browser = new Browser();
        HttpResponse<String> page = browser.submit(browser.start("pede-aqui-web", WEB_REDIRECT, null),
                "#kc-phone-otp-phone-form", "send", Map.of("phone", "+258 82 765 4321"));
        String code = codeFor("+25882*****21", 1);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int left = 4; left >= 1; left--) {
            page = browser.submit(page, "#kc-phone-otp-code-form", "verify", Map.of("code", wrong));
            assertThat(page.statusCode()).isEqualTo(200);
            assertThat(page.body()).contains("Wrong code. " + left + " attempts left.");
        }
        page = browser.submit(page, "#kc-phone-otp-code-form", "verify", Map.of("code", wrong));
        assertThat(page.body()).contains("temporarily locked").contains("kc-phone-otp-phone-form");

        // The challenge is gone and a new code cannot be requested while locked.
        HttpResponse<String> retry = browser.submit(page, "#kc-phone-otp-phone-form", "send", Map.of("phone", "827654321"));
        assertThat(retry.body()).contains("temporarily locked");
        assertThat(smsCount("+25882*****21")).isEqualTo(1);
    }

    @Test
    void resendWithinCooldownIsRefused() throws Exception {
        Browser browser = new Browser();
        HttpResponse<String> codePage = browser.submit(browser.start("pede-aqui-web", WEB_REDIRECT, null),
                "#kc-phone-otp-phone-form", "send", Map.of("phone", "85 000 1122"));
        codeFor("+25885*****22", 1);

        HttpResponse<String> resend = browser.submit(codePage, "#kc-phone-otp-code-form", "resend", Map.of());
        assertThat(resend.body()).containsPattern("Wait \\d+ seconds before requesting another code");
        assertThat(smsCount("+25885*****22")).isEqualTo(1);
    }

    @Test
    void invalidNumberIsRejectedWithoutSms() throws Exception {
        Browser browser = new Browser();
        HttpResponse<String> page = browser.submit(browser.start("pede-aqui-web", WEB_REDIRECT, null),
                "#kc-phone-otp-phone-form", "send", Map.of("phone", "21 123 456"));
        assertThat(page.body()).contains("Invalid number").contains("kc-phone-otp-phone-form");
    }

    @Test
    void privilegedUserCannotLogInByPhone() throws Exception {
        String adminToken = adminToken();
        String userId = createUser(adminToken, "ops-with-phone", "+258861112233");
        grantRealmRole(adminToken, userId, "CUSTOMER");
        grantRealmRole(adminToken, userId, "OPS");

        Browser browser = new Browser();
        HttpResponse<String> codePage = browser.submit(browser.start("pede-aqui-web", WEB_REDIRECT, null),
                "#kc-phone-otp-phone-form", "send", Map.of("phone", "86 111 2233"));
        String code = codeFor("+25886*****33", 1);
        HttpResponse<String> result = browser.submit(codePage, "#kc-phone-otp-code-form", "verify", Map.of("code", code));

        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.body()).contains("This number cannot be used to sign in");
        assertThat(result.headers().firstValue("Location")).isEmpty();
    }

    @Test
    void usePasswordAndEmailLoginHintFallThroughToThePasswordForm() throws Exception {
        Browser browser = new Browser();
        HttpResponse<String> passwordPage = browser.submit(browser.start("pede-aqui-web", WEB_REDIRECT, null),
                "#kc-phone-otp-phone-form", "password", Map.of());
        assertThat(passwordPage.body()).contains("kc-form-login").contains("name=\"password\"");

        HttpResponse<String> hinted = new Browser().start("pede-aqui-web", WEB_REDIRECT, "someone@example.com");
        assertThat(hinted.body()).contains("kc-form-login").doesNotContain("kc-phone-otp-phone-form");
    }

    @Test
    void backofficeClientKeepsThePasswordFlowWithoutPhoneStep() throws Exception {
        HttpResponse<String> page = new Browser().start("delivery-app", "http://localhost:3000/api/auth/callback/keycloak", null);
        assertThat(page.body()).contains("kc-form-login").doesNotContain("kc-phone-otp-phone-form");
    }

    private static String adminToken() throws Exception {
        HttpResponse<String> response = new Browser().post(base() + "/realms/master/protocol/openid-connect/token",
                Map.of("grant_type", "password", "client_id", "admin-cli", "username", "admin", "password", "admin"));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return jsonString(response.body(), "access_token");
    }

    private static String createUser(String token, String username, String phone) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"enabled\":true,\"firstName\":\"Ops\",\"lastName\":\"Phone\","
                + "\"email\":\"" + username + "@example.com\",\"attributes\":{\"phone_e164\":[\"" + phone + "\"]}}";
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create(base() + "/admin/realms/delivery/users"))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        String location = response.headers().firstValue("Location").orElseThrow();
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private static void grantRealmRole(String token, String userId, String role) throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> roleRep = http.send(HttpRequest.newBuilder(URI.create(base() + "/admin/realms/delivery/roles/" + role))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(roleRep.statusCode()).isEqualTo(200);
        HttpResponse<String> grant = http.send(HttpRequest.newBuilder(
                        URI.create(base() + "/admin/realms/delivery/users/" + userId + "/role-mappings/realm"))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("[" + roleRep.body() + "]")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(grant.statusCode()).as(grant.body()).isEqualTo(204);
    }
}
