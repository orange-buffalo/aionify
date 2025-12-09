package io.orangebuffalo.aionify.domain

import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Put
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.security.Principal
import java.util.Locale

@Controller("/api/users")
@Secured(SecurityRule.IS_AUTHENTICATED)
open class UserResource(
    private val userRepository: UserRepository,
    private val userService: UserService
) {

    companion object {
        private val SUPPORTED_LANGUAGES = setOf("en", "uk")
    }

    @Get("/profile")
    open fun getProfile(principal: Principal?): HttpResponse<*> {
        val userName = principal?.name
            ?: return HttpResponse.unauthorized<ProfileErrorResponse>()
                .body(ProfileErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        val user = userRepository.findByUserName(userName).orElse(null)
            ?: return HttpResponse.notFound<ProfileErrorResponse>()
                .body(ProfileErrorResponse("User not found", "USER_NOT_FOUND"))

        return HttpResponse.ok(
            ProfileResponse(
                userName = user.userName,
                greeting = user.greeting,
                languageCode = user.languageCode,
                locale = user.localeTag
            )
        )
    }

    @Put("/profile")
    open fun updateProfile(@Valid @Body request: UpdateProfileRequest, principal: Principal?): HttpResponse<*> {
        val userName = principal?.name
            ?: return HttpResponse.unauthorized<ProfileErrorResponse>()
                .body(ProfileErrorResponse("User not authenticated", "USER_NOT_AUTHENTICATED"))

        if (request.languageCode !in SUPPORTED_LANGUAGES) {
            return HttpResponse.badRequest(
                ProfileErrorResponse("Language must be either 'en' (English) or 'uk' (Ukrainian)", "LANGUAGE_NOT_SUPPORTED")
            )
        }

        val locale = parseLocale(request.locale)
            ?: return HttpResponse.badRequest(
                ProfileErrorResponse("Invalid locale format", "INVALID_LOCALE")
            )

        userService.updateProfile(
            userName = userName,
            greeting = request.greeting,
            languageCode = request.languageCode,
            locale = locale
        )

        return HttpResponse.ok(ProfileSuccessResponse("Profile updated successfully"))
    }

    private fun parseLocale(localeTag: String): Locale? {
        if (localeTag.isBlank()) return null
        val locale = Locale.forLanguageTag(localeTag)
        // "und" is the BCP 47 language tag for "undetermined" - returned when the input is invalid
        return if (locale.toLanguageTag() == "und") null else locale
    }
}

@Introspected
data class ProfileResponse(
    val userName: String,
    val greeting: String,
    val languageCode: String,
    val locale: String
)

@Introspected
data class UpdateProfileRequest(
    @field:NotBlank(message = "Greeting cannot be blank")
    @field:Size(max = 255, message = "Greeting cannot exceed 255 characters")
    val greeting: String,

    @field:NotBlank(message = "Language code is required")
    val languageCode: String,

    @field:NotBlank(message = "Locale is required")
    val locale: String
)

@Introspected
data class ProfileSuccessResponse(
    val message: String
)

@Introspected
data class ProfileErrorResponse(
    val error: String,
    val errorCode: String
)
