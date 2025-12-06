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
        },
        admin: {
          title: "Admin Portal",
          subtitle: "Manage users and system settings",
        },
      },
      // Navigation
      nav: {
        dashboard: "Dashboard",
        settings: "Settings",
        logout: "Logout",
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
        },
        admin: {
          title: "Адміністративна панель",
          subtitle: "Керуйте користувачами та налаштуваннями системи",
        },
      },
      // Navigation
      nav: {
        dashboard: "Панель керування",
        settings: "Налаштування",
        logout: "Вийти",
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
  const supportedLanguages = ["en", "uk"]
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

export default i18n
