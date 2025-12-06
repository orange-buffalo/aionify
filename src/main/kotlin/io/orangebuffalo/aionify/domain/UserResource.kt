package io.orangebuffalo.aionify.domain

import io.quarkus.security.Authenticated
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import java.util.Locale

@Path("/api/users")
class UserResource(private val userRepository: UserRepository) {

    companion object {
        private val SUPPORTED_LANGUAGES = setOf("en", "uk")
    }

    @GET
    @Path("/profile")
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    fun getProfile(@Context securityContext: SecurityContext): Response {
        val userName = securityContext.userPrincipal?.name
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ProfileErrorResponse("User not authenticated"))
                .build()

        val user = userRepository.findByUserName(userName)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(ProfileErrorResponse("User not found"))
                .build()

        return Response.ok(
            ProfileResponse(
                userName = user.userName,
                greeting = user.greeting,
                languageCode = user.languageCode,
                locale = user.locale.toLanguageTag()
            )
        ).build()
    }

    @PUT
    @Path("/profile")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    fun updateProfile(@Valid request: UpdateProfileRequest, @Context securityContext: SecurityContext): Response {
        val userName = securityContext.userPrincipal?.name
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(ProfileErrorResponse("User not authenticated"))
                .build()

        if (request.languageCode !in SUPPORTED_LANGUAGES) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ProfileErrorResponse("Language must be either 'en' (English) or 'uk' (Ukrainian)"))
                .build()
        }

        val locale = parseLocale(request.locale)
            ?: return Response.status(Response.Status.BAD_REQUEST)
                .entity(ProfileErrorResponse("Invalid locale format"))
                .build()

        userRepository.updateProfile(
            userName = userName,
            greeting = request.greeting,
            languageCode = request.languageCode,
            locale = locale
        )

        return Response.ok(ProfileSuccessResponse("Profile updated successfully")).build()
    }

    private fun parseLocale(localeTag: String): Locale? {
        if (localeTag.isBlank()) return null
        val locale = Locale.forLanguageTag(localeTag)
        // "und" is the BCP 47 language tag for "undetermined" - returned when the input is invalid
        return if (locale.toLanguageTag() == "und") null else locale
    }
}

data class ProfileResponse(
    val userName: String,
    val greeting: String,
    val languageCode: String,
    val locale: String
)

data class UpdateProfileRequest(
    @field:NotBlank(message = "Greeting cannot be blank")
    @field:Size(max = 255, message = "Greeting cannot exceed 255 characters")
    val greeting: String,

    @field:NotBlank(message = "Language code is required")
    val languageCode: String,

    @field:NotBlank(message = "Locale is required")
    val locale: String
)

data class ProfileSuccessResponse(
    val message: String
)

data class ProfileErrorResponse(
    val error: String
)
