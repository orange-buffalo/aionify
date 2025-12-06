package io.orangebuffalo.aionify.domain

import io.quarkus.security.Authenticated
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import jakarta.ws.rs.*
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.SecurityContext
import java.util.Locale

@Path("/api/users")
class UserResource(private val userRepository: UserRepository) {

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

        // Validate language code
        if (request.languageCode !in listOf("en", "uk")) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ProfileErrorResponse("Language must be either 'en' (English) or 'uk' (Ukrainian)"))
                .build()
        }

        // Validate and parse locale
        val locale = try {
            Locale.forLanguageTag(request.locale).also {
                if (it.toLanguageTag() == "und" || request.locale.isBlank()) {
                    throw IllegalArgumentException("Invalid locale")
                }
            }
        } catch (e: Exception) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ProfileErrorResponse("Invalid locale format"))
                .build()
        }

        userRepository.updateProfile(
            userName = userName,
            greeting = request.greeting,
            languageCode = request.languageCode,
            locale = locale
        )

        return Response.ok(ProfileSuccessResponse("Profile updated successfully")).build()
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
