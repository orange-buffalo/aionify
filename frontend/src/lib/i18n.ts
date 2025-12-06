import i18n from "i18next"
import { initReactI18next } from "react-i18next"

// Translation resources
const resources = {
  en: {
    translation: {
      // Login Page
      login: {
        title: "Login",
        welcomeBack: "Welcome back, {{greeting}}!",
        signInPrompt: "Sign in to your account to continue.",
        username: "Username",
        usernamePlaceholder: "Enter your username",
        password: "Password",
        passwordPlaceholder: "Password",
        lostPassword: "Lost password?",
        signIn: "Sign in",
        signingIn: "Signing in...",
        invalidCredentials: "Invalid username or password",
      },
      // Settings Page
      settings: {
        title: "Settings",
        subtitle: "Manage your account settings",
        profile: {
          title: "My Profile",
          subtitle: "View and manage your profile information",
          greeting: "Greeting",
          greetingPlaceholder: "Enter your greeting",
          language: "Language",
          languagePlaceholder: "Select language",
          locale: "Locale",
          localePlaceholder: "Select locale",
          localeExample: "Example: {{example}}",
          save: "Save Profile",
          saving: "Saving...",
          updateSuccess: "Profile updated successfully",
          loading: "Loading profile...",
        },
        changePassword: {
          title: "Change Password",
          subtitle: "Update your password",
          currentPassword: "Current Password",
          currentPasswordPlaceholder: "Enter current password",
          newPassword: "New Password",
          newPasswordPlaceholder: "Enter new password",
          confirmPassword: "Confirm New Password",
          confirmPasswordPlaceholder: "Re-enter new password",
          change: "Change Password",
          changing: "Changing...",
          changeSuccess: "Password changed successfully",
        },
      },
      // Portal Pages
      portal: {
        user: {
          title: "Time Tracking",
          subtitle: "Track your time and manage your tasks",
          timeEntry: {
            title: "Time Entry",
            description: "Log your time",
            comingSoon: "Time entry coming soon...",
          },
          calendar: {
            title: "Calendar",
            description: "View your schedule",
            comingSoon: "Calendar coming soon...",
          },
          reports: {
            title: "Reports",
            description: "View your reports",
            comingSoon: "Reports coming soon...",
          },
        },
        admin: {
          title: "Admin Portal",
          subtitle: "Manage users and system settings",
          users: {
            title: "Users",
            description: "Manage user accounts",
            comingSoon: "User management coming soon...",
          },
          reports: {
            title: "Reports",
            description: "View system reports",
            comingSoon: "Reports coming soon...",
          },
          settings: {
            title: "Settings",
            description: "System configuration",
            comingSoon: "Settings coming soon...",
          },
        },
      },
      // Navigation
      nav: {
        dashboard: "Dashboard",
        settings: "Settings",
        logout: "Logout",
        timeEntry: "Time Entry",
        calendar: "Calendar",
        reports: "Reports",
        users: "Users",
      },
      // Languages
      languages: {
        en: "English",
        uk: "Ukrainian (Українська)",
      },
      // Validation errors
      validation: {
        greetingBlank: "Greeting cannot be blank",
        greetingTooLong: "Greeting cannot exceed 255 characters",
        languageRequired: "Language is required",
        localeRequired: "Locale is required",
        currentPasswordRequired: "Current password is required",
        newPasswordRequired: "New password is required",
        passwordsDoNotMatch: "Passwords do not match",
        currentPasswordIncorrect: "Current password is incorrect",
        passwordTooLong: "Password cannot exceed 50 characters",
      },
      // Common
      common: {
        error: "An error occurred",
      },
      // Error codes for API errors
      errorCodes: {
        INVALID_CREDENTIALS: "Invalid username or password",
        USER_NOT_AUTHENTICATED: "User not authenticated",
        USER_NOT_FOUND: "User not found",
        INVALID_LOCALE: "Invalid locale format",
        GREETING_BLANK: "Greeting cannot be blank",
        GREETING_TOO_LONG: "Greeting cannot exceed 255 characters",
        LANGUAGE_NOT_SUPPORTED: "Language must be either 'en' (English) or 'uk' (Ukrainian)",
        CURRENT_PASSWORD_EMPTY: "Current password cannot be empty",
        NEW_PASSWORD_EMPTY: "New password cannot be empty",
        PASSWORD_TOO_LONG: "Password cannot exceed 50 characters",
        CURRENT_PASSWORD_INCORRECT: "Current password is incorrect",
      },
    },
  },
  uk: {
    translation: {
      // Login Page
      login: {
        title: "Вхід",
        welcomeBack: "З поверненням, {{greeting}}!",
        signInPrompt: "Увійдіть до свого облікового запису, щоб продовжити.",
        username: "Ім'я користувача",
        usernamePlaceholder: "Введіть ім'я користувача",
        password: "Пароль",
        passwordPlaceholder: "Пароль",
        lostPassword: "Забули пароль?",
        signIn: "Увійти",
        signingIn: "Вхід...",
        invalidCredentials: "Невірне ім'я користувача або пароль",
      },
      // Settings Page
      settings: {
        title: "Налаштування",
        subtitle: "Керуйте налаштуваннями облікового запису",
        profile: {
          title: "Мій профіль",
          subtitle: "Перегляд та керування інформацією профілю",
          greeting: "Привітання",
          greetingPlaceholder: "Введіть привітання",
          language: "Мова",
          languagePlaceholder: "Оберіть мову",
          locale: "Локаль",
          localePlaceholder: "Оберіть локаль",
          localeExample: "Приклад: {{example}}",
          save: "Зберегти профіль",
          saving: "Збереження...",
          updateSuccess: "Профіль успішно оновлено",
          loading: "Завантаження профілю...",
        },
        changePassword: {
          title: "Зміна пароля",
          subtitle: "Оновіть свій пароль",
          currentPassword: "Поточний пароль",
          currentPasswordPlaceholder: "Введіть поточний пароль",
          newPassword: "Новий пароль",
          newPasswordPlaceholder: "Введіть новий пароль",
          confirmPassword: "Підтвердіть новий пароль",
          confirmPasswordPlaceholder: "Введіть новий пароль ще раз",
          change: "Змінити пароль",
          changing: "Зміна...",
          changeSuccess: "Пароль успішно змінено",
        },
      },
      // Portal Pages
      portal: {
        user: {
          title: "Облік часу",
          subtitle: "Відстежуйте свій час та керуйте завданнями",
          timeEntry: {
            title: "Облік часу",
            description: "Записуйте свій час",
            comingSoon: "Облік часу скоро з'явиться...",
          },
          calendar: {
            title: "Календар",
            description: "Переглядайте свій розклад",
            comingSoon: "Календар скоро з'явиться...",
          },
          reports: {
            title: "Звіти",
            description: "Переглядайте свої звіти",
            comingSoon: "Звіти скоро з'явиться...",
          },
        },
        admin: {
          title: "Адміністративна панель",
          subtitle: "Керуйте користувачами та налаштуваннями системи",
          users: {
            title: "Користувачі",
            description: "Керування обліковими записами",
            comingSoon: "Керування користувачами скоро з'явиться...",
          },
          reports: {
            title: "Звіти",
            description: "Переглядайте системні звіти",
            comingSoon: "Звіти скоро з'явиться...",
          },
          settings: {
            title: "Налаштування",
            description: "Конфігурація системи",
            comingSoon: "Налаштування скоро з'явиться...",
          },
        },
      },
      // Navigation
      nav: {
        dashboard: "Панель керування",
        settings: "Налаштування",
        logout: "Вийти",
        timeEntry: "Облік часу",
        calendar: "Календар",
        reports: "Звіти",
        users: "Користувачі",
      },
      // Languages
      languages: {
        en: "Англійська (English)",
        uk: "Українська",
      },
      // Validation errors
      validation: {
        greetingBlank: "Привітання не може бути порожнім",
        greetingTooLong: "Привітання не може перевищувати 255 символів",
        languageRequired: "Мова обов'язкова",
        localeRequired: "Локаль обов'язкова",
        currentPasswordRequired: "Поточний пароль обов'язковий",
        newPasswordRequired: "Новий пароль обов'язковий",
        passwordsDoNotMatch: "Паролі не співпадають",
        currentPasswordIncorrect: "Поточний пароль невірний",
        passwordTooLong: "Пароль не може перевищувати 50 символів",
      },
      // Common
      common: {
        error: "Сталася помилка",
      },
      // Error codes for API errors
      errorCodes: {
        INVALID_CREDENTIALS: "Невірне ім'я користувача або пароль",
        USER_NOT_AUTHENTICATED: "Користувач не автентифікований",
        USER_NOT_FOUND: "Користувача не знайдено",
        INVALID_LOCALE: "Невірний формат локалі",
        GREETING_BLANK: "Привітання не може бути порожнім",
        GREETING_TOO_LONG: "Привітання не може перевищувати 255 символів",
        LANGUAGE_NOT_SUPPORTED: "Мова має бути 'en' (англійська) або 'uk' (українська)",
        CURRENT_PASSWORD_EMPTY: "Поточний пароль не може бути порожнім",
        NEW_PASSWORD_EMPTY: "Новий пароль не може бути порожнім",
        PASSWORD_TOO_LONG: "Пароль не може перевищувати 50 символів",
        CURRENT_PASSWORD_INCORRECT: "Поточний пароль невірний",
      },
    },
  },
}

// Get saved language or detect browser language
const getSavedLanguage = (): string | null => {
  try {
    return localStorage.getItem("aionify_language")
  } catch {
    return null
  }
}

const detectBrowserLanguage = (): string => {
  const browserLang = navigator.language.split("-")[0] // Get language code without region
  const supportedLanguages = Object.keys(resources)
  return supportedLanguages.includes(browserLang) ? browserLang : "en"
}

const initialLanguage = getSavedLanguage() || detectBrowserLanguage()

i18n
  .use(initReactI18next)
  .init({
    resources,
    lng: initialLanguage,
    fallbackLng: "en",
    interpolation: {
      escapeValue: false, // React already escapes values
    },
  })

// Helper functions for language management
export const saveLanguagePreference = (languageCode: string) => {
  try {
    localStorage.setItem("aionify_language", languageCode)
  } catch (error) {
    console.error("Failed to save language preference:", error)
  }
}

export const loadSavedLanguage = (): string | null => {
  return getSavedLanguage()
}

export const initializeLanguage = async (languageCode: string) => {
  saveLanguagePreference(languageCode)
  await i18n.changeLanguage(languageCode)
}

// Helper function to translate error codes
export const translateErrorCode = (errorCode: string | undefined): string => {
  if (!errorCode) {
    return i18n.t("common.error")
  }
  
  const translationKey = `errorCodes.${errorCode}`
  const translated = i18n.t(translationKey)
  
  // If translation key not found, return the fallback
  return translated === translationKey ? i18n.t("common.error") : translated
}

export default i18n
