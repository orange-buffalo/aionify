package io.orangebuffalo.aionify

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.orangebuffalo.aionify.domain.User
import io.orangebuffalo.aionify.domain.UserApiAccessTokenRepository
import jakarta.inject.Inject
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.regex.Pattern

/**
 * Tests for the host bridge used by OS-specific wrappers, with a fake host injected into the page
 * the same way native wrappers do it (a script that runs before the app loads).
 */
@MicronautTest(transactional = false)
class HostBridgePlaywrightTest : PlaywrightTestBase() {
    @Inject
    lateinit var testAuthSupport: TestAuthSupport

    @Inject
    lateinit var userApiAccessTokenRepository: UserApiAccessTokenRepository

    private lateinit var regularUser: User

    companion object {
        // Responds to the handshake in a microtask, as microtasks are not affected by the paused test clock
        private val FAKE_HOST_SCRIPT =
            """
            window.__hostMessages = [];
            window.aionifyHost = {
              postMessage: (message) => {
                const parsed = JSON.parse(message);
                window.__hostMessages.push(parsed);
                if (parsed.type === "request" && parsed.method === "host.hello") {
                  Promise.resolve().then(() => window.aionify.receive(JSON.stringify({
                    type: "response",
                    id: parsed.id,
                    result: { protocolVersion: 1, platform: "test" },
                  })));
                }
              },
            };
            """.trimIndent()
    }

    @BeforeEach
    fun setupTestData() {
        regularUser = testUsers.createRegularUser("bridgeUser", "Bridge User")
    }

    @Test
    fun `should not activate bridge when app is not hosted by a wrapper`() {
        page.navigate("/login")

        assertThat(page.locator("[data-testid='username-input']")).isVisible()
        assertEquals(false, page.evaluate("() => 'aionify' in window"))
    }

    @Test
    fun `should perform handshake and publish authenticated user`() {
        installFakeHost()
        loginViaToken("/portal/time-logs", regularUser, testAuthSupport)

        val hello = awaitHostMessage("handshake") { it["method"] == "host.hello" }
        assertEquals(
            mapOf(
                "protocolVersion" to 1,
                "methods" to listOf("app.navigate", "auth.provisionApiToken"),
                "events" to listOf("auth.changed"),
            ),
            hello["params"],
        )

        val authChanged = awaitHostMessage("auth state") { it["event"] == "auth.changed" }
        assertEquals(mapOf("authenticated" to true, "userName" to "bridgeUser"), authChanged["payload"])
    }

    @Test
    fun `should publish auth state change when user logs out`() {
        installFakeHost()
        loginViaToken("/portal/time-logs", regularUser, testAuthSupport)
        awaitAuthState(authenticated = true)

        page.locator("[data-testid='profile-menu-button']").click()
        page.locator("[data-testid='logout-button']").click()

        awaitAuthState(authenticated = false)
        assertEquals(
            listOf(mapOf("authenticated" to true, "userName" to "bridgeUser"), mapOf("authenticated" to false)),
            hostMessages().filter { it["event"] == "auth.changed" }.map { it["payload"] },
        )
    }

    @Test
    fun `should navigate to requested app page`() {
        installFakeHost()
        loginViaToken("/portal/time-logs", regularUser, testAuthSupport)
        awaitAuthState(authenticated = true)

        sendToApp("""{"type":"request","id":"host-1","method":"app.navigate","params":{"path":"/portal/settings"}}""")

        val response = awaitResponse("host-1")
        assertEquals(emptyMap<String, Any>(), response["result"])
        assertThat(page.locator("[data-testid='settings-page']")).isVisible()
    }

    @Test
    fun `should reject navigation outside of the app`() {
        installFakeHost()
        loginViaToken("/portal/time-logs", regularUser, testAuthSupport)
        awaitAuthState(authenticated = true)

        sendToApp("""{"type":"request","id":"host-1","method":"app.navigate","params":{"path":"https://example.com"}}""")

        val response = awaitResponse("host-1")
        assertEquals("INVALID_PARAMS", (response["error"] as Map<*, *>)["code"])
        assertThat(page).hasURL(Pattern.compile(".*/portal/time-logs$"))
    }

    @Test
    fun `should provision API token after user consent`() {
        val baseTime = setBaseTime("2024-03-16", "03:30")
        installFakeHost()
        loginViaToken("/portal/time-logs", regularUser, testAuthSupport)
        awaitAuthState(authenticated = true)

        sendToApp(
            """{"type":"request","id":"host-1","method":"auth.provisionApiToken","params":{"name":"Aionify for macOS"}}""",
        )

        val dialog = page.locator("[data-testid='host-token-consent-dialog']")
        assertThat(dialog).containsText("Allow app access?")
        assertThat(dialog).containsText("requests access to your account as \"Aionify for macOS\"")
        captureUiReviewScreenshot("token-consent-dialog")

        page.locator("[data-testid='host-token-consent-approve-button']").click()

        val result = awaitResponse("host-1")["result"] as Map<*, *>
        assertThat(dialog).not().isVisible()

        val tokens =
            testDatabaseSupport.inTransaction {
                userApiAccessTokenRepository.findAllByUserId(requireNotNull(regularUser.id))
            }
        assertEquals(1, tokens.size)
        assertEquals("Aionify for macOS", tokens[0].name)
        assertEquals(baseTime, tokens[0].createdAt)
        assertEquals(mapOf("name" to "Aionify for macOS", "token" to tokens[0].token), result)
    }

    @Test
    fun `should not provision API token when user denies access`() {
        installFakeHost()
        loginViaToken("/portal/time-logs", regularUser, testAuthSupport)
        awaitAuthState(authenticated = true)

        sendToApp(
            """{"type":"request","id":"host-1","method":"auth.provisionApiToken","params":{"name":"Aionify for macOS"}}""",
        )
        val dialog = page.locator("[data-testid='host-token-consent-dialog']")
        assertThat(dialog).isVisible()

        page.locator("[data-testid='host-token-consent-reject-button']").click()

        val response = awaitResponse("host-1")
        assertEquals("USER_REJECTED", (response["error"] as Map<*, *>)["code"])
        assertThat(dialog).not().isVisible()
        testDatabaseSupport.inTransaction {
            assertEquals(emptyList<Any>(), userApiAccessTokenRepository.findAllByUserId(requireNotNull(regularUser.id)))
        }
    }

    @Test
    fun `should not provision API token when user is not logged in`() {
        installFakeHost()
        page.navigate("/login")
        awaitAuthState(authenticated = false)

        sendToApp(
            """{"type":"request","id":"host-1","method":"auth.provisionApiToken","params":{"name":"Aionify for macOS"}}""",
        )

        val response = awaitResponse("host-1")
        assertEquals("NOT_AUTHENTICATED", (response["error"] as Map<*, *>)["code"])
        assertThat(page.locator("[data-testid='host-token-consent-dialog']")).not().isVisible()
    }

    private fun installFakeHost() {
        page.addInitScript(FAKE_HOST_SCRIPT)
    }

    private fun sendToApp(message: String) {
        page.evaluate("(message) => window.aionify.receive(message)", message)
    }

    @Suppress("UNCHECKED_CAST")
    private fun hostMessages(): List<Map<String, Any?>> = page.evaluate("() => window.__hostMessages ?? []") as List<Map<String, Any?>>

    /**
     * Waits until the fake host receives a matching message. Polls in the test thread, as Playwright is not thread-safe.
     */
    private fun awaitHostMessage(
        description: String,
        predicate: (Map<String, Any?>) -> Boolean,
    ): Map<String, Any?> {
        var message: Map<String, Any?>? = null
        await()
            .alias("Host message: $description")
            .pollInSameThread()
            .ignoreExceptions()
            .atMost(Duration.ofSeconds(10))
            .until {
                message = hostMessages().firstOrNull(predicate)
                message != null
            }
        return requireNotNull(message)
    }

    private fun awaitResponse(requestId: String) =
        awaitHostMessage("response to $requestId") { it["type"] == "response" && it["id"] == requestId }

    private fun awaitAuthState(authenticated: Boolean) =
        awaitHostMessage("auth state authenticated=$authenticated") {
            it["event"] == "auth.changed" && (it["payload"] as Map<*, *>)["authenticated"] == authenticated
        }
}
