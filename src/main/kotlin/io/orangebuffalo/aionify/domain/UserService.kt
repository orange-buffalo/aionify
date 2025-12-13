package io.orangebuffalo.aionify.domain

import jakarta.inject.Singleton

@Singleton
class UserService(private val userRepository: UserRepository) {
    
    fun findAllPaginated(page: Int, size: Int): PagedUsers {
        val offset = page * size
        val users = userRepository.findAllPaginated(offset, size)
        val total = userRepository.countAll()
        
        return PagedUsers(
            users = users,
            total = total,
            page = page,
            size = size
        )
    }
    
    fun updateProfile(userName: String, greeting: String, languageCode: String, locale: java.util.Locale) {
        userRepository.updateProfile(userName, greeting, languageCode, locale.toLanguageTag())
    }
}
