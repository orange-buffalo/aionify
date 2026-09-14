package io.orangebuffalo.aionify.domain

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Put
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.serde.annotation.Serdeable
import io.swagger.v3.oas.annotations.Hidden
import jakarta.transaction.Transactional
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.security.Principal
import java.util.Locale

@Controller("/api-ui/users")
@Secured(SecurityRule.IS_AUTHENTICATED)
@Transactional
@Hidden
open class UserResource(
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val userSettingsRepository: UserSettingsRepository,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(UserResource::class.java)

    @Get("/profile")
    open fun getProfile(currentUser: UserWithId): HttpResponse<*> {
        log.trace("Returning profile for user: {}", currentUser.user.userName)

        val settings = userSettingsRepository.findByUserId(currentUser.id).orElse(null)
        val startOfWeek = settings?.startOfWeek?.name ?: "MONDAY"

        return HttpResponse.ok(
            ProfileResponse(
                userName = currentUser.user.userName,
                greeting = currentUser.user.greeting,
                locale = currentUser.user.localeTag,
                startOfWeek = startOfWeek,
            ),
        )
    }

    @Put("/profile")
    open fun updateProfile(
        @Valid @Body request: UpdateProfileRequest,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        val locale = parseLocale(request.locale)
        if (locale == null) {
            log.debug("Update profile failed: invalid locale: {}", request.locale)
            return HttpResponse.badRequest(
                ProfileErrorResponse("Invalid locale format", "INVALID_LOCALE"),
            )
        }

        userService.updateProfile(
            userName = currentUser.user.userName,
            greeting = request.greeting,
            locale = locale,
        )

        return HttpResponse.ok(ProfileSuccessResponse("Profile updated successfully"))
    }

    @Put("/settings")
    open fun updateSettings(
        @Valid @Body request: UpdateSettingsRequest,
        currentUser: UserWithId,
    ): HttpResponse<*> {
        val weekDay =
            try {
                WeekDay.valueOf(request.startOfWeek)
            } catch (e: IllegalArgumentException) {
                log.debug("Update settings failed: invalid start of week: {}", request.startOfWeek)
                return HttpResponse.badRequest(
                    SettingsErrorResponse("Invalid start of week", "INVALID_START_OF_WEEK"),
                )
            }

        val settings = userSettingsRepository.findByUserId(currentUser.id).orElse(null)
        if (settings == null) {
            // Create new settings if they don't exist
            userSettingsRepository.save(UserSettings.create(userId = currentUser.id, startOfWeek = weekDay))
        } else {
            // Update existing settings
            userSettingsRepository.update(settings.copy(startOfWeek = weekDay))
        }

        return HttpResponse.ok(SettingsSuccessResponse("Settings updated successfully"))
    }

    private fun parseLocale(localeTag: String): Locale? {
        if (localeTag.isBlank()) return null
        val locale = Locale.forLanguageTag(localeTag)
        // "und" is the BCP 47 language tag for "undetermined" - returned when the input is invalid
        return if (locale.toLanguageTag() == "und") null else locale
    }
}

@Serdeable
@Introspected
data class ProfileResponse(
    val userName: String,
    val greeting: String,
    val locale: String,
    val startOfWeek: String,
)

@Serdeable
@Introspected
data class UpdateProfileRequest(
    @field:NotBlank(message = "Greeting cannot be blank")
    @field:Size(max = 255, message = "Greeting cannot exceed 255 characters")
    val greeting: String,
    @field:NotBlank(message = "Locale is required")
    val locale: String,
)

@Serdeable
@Introspected
data class ProfileSuccessResponse(
    val message: String,
)

@Serdeable
@Introspected
data class ProfileErrorResponse(
    val error: String,
    val errorCode: String,
)

@Serdeable
@Introspected
data class UpdateSettingsRequest(
    @field:NotBlank(message = "Start of week is required")
    val startOfWeek: String,
)

@Serdeable
@Introspected
data class SettingsSuccessResponse(
    val message: String,
)

@Serdeable
@Introspected
data class SettingsErrorResponse(
    val error: String,
    val errorCode: String,
)
