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
        lostPasswordDialog: {
          title: "Password Reset",
          message: "To reset your password, please contact your system administrator. They will provide you with a secure password reset link.",
          close: "Close",
        },
        signIn: "Sign in",
        signingIn: "Signing in...",
        invalidCredentials: "Invalid username or password",
        sessionExpired: "Your session has expired, please login again",
        activationSuccess: "Password set successfully! Please login with your new password.",
      },
      // Activation Page
      activation: {
        title: "Set Your Password",
        subtitle: "Enter your new password below",
        validating: "Validating your link...",
        noToken: "No activation token provided",
        invalidToken: "This link is invalid or has expired",
        contactAdmin: "Please contact your system administrator for a new link.",
        welcome: "Welcome, {{greeting}}!",
        password: "Password",
        passwordPlaceholder: "Enter your password",
        confirmPassword: "Confirm Password",
        confirmPasswordPlaceholder: "Re-enter your password",
        setPassword: "Set Password",
        settingPassword: "Setting password...",
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
      // Time Logs Page
      timeLogs: {
        title: "Time Logs",
        subtitle: "Track your time and manage your tasks",
        previousWeek: "Previous Week",
        nextWeek: "Next Week",
        today: "Today",
        yesterday: "Yesterday",
        currentEntry: {
          title: "What are you working on?",
          placeholder: "Enter task title...",
        },
        start: "Start",
        starting: "Starting...",
        stop: "Stop",
        stopping: "Stopping...",
        delete: "Delete",
        deleting: "Deleting...",
        continue: "Continue",
        startedAt: "Started at",
        duration: "Duration",
        totalDuration: "Total",
        inProgress: "in progress",
        noEntries: "No time entries for this week",
        timezoneHint: "Times shown in {{timezone}}",
        deleteDialog: {
          title: "Delete Time Entry",
          message: "Are you sure you want to delete this time entry? This action cannot be undone.",
          confirm: "Delete",
          cancel: "Cancel",
        },
        success: {
          started: "Time entry started",
          stopped: "Time entry stopped",
          deleted: "Time entry deleted",
        },
        errors: {
          titleRequired: "Please enter a task title",
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
            manage: "Manage Users",
            subtitle: "View and manage system users",
            deleteSuccess: "User deleted successfully",
            createUser: "Create User",
            table: {
              username: "Username",
              greeting: "Greeting",
              type: "Type",
              actions: "Actions",
              admin: "Admin",
              regularUser: "Regular User",
              edit: "Edit",
              delete: "Delete",
              noUsers: "No users found",
            },
            deleteConfirm: {
              title: "Delete User",
              message: "Are you sure you want to delete user {{userName}}? This action cannot be undone.",
              confirm: "Delete",
              cancel: "Cancel",
            },
            pagination: {
              showing: "Showing {{start}} to {{end}} of {{total}} users",
              previous: "Previous",
              next: "Next",
              page: "Page {{page}} of {{total}}",
            },
            create: {
              title: "Create User",
              subtitle: "Add a new user to the system",
              back: "Back to Users",
              username: "Username",
              usernamePlaceholder: "Enter username",
              greeting: "Greeting",
              greetingPlaceholder: "Enter user's greeting name",
              userType: "User Type",
              regularUser: "Regular User",
              admin: "Administrator",
              create: "Create User",
              creating: "Creating...",
              createSuccess: "User created successfully! Share the activation link below with the user to allow them to set their password.",
            },
            edit: {
              title: "Edit User",
              subtitle: "Manage user account details",
              back: "Back to Users",
              usernameSection: "Username",
              username: "Username",
              usernamePlaceholder: "Enter username",
              save: "Save Changes",
              saving: "Saving...",
              updateSuccess: "User updated successfully",
              userInfo: "User Information",
              greeting: "Greeting",
              userType: "User Type",
              profileNote: "Note: User profile settings (greeting, language, locale) are managed by the user in their personal settings page.",
              activationSection: "Account Activation",
              activationUrl: "Activation URL",
              activationNote: "Share this URL with the user to allow them to set their password or reset their account.",
              regenerateToken: "Regenerate Activation Token",
              regenerating: "Regenerating...",
              generating: "Generating...",
              tokenRegenerated: "Activation token regenerated successfully",
              noActivationToken: "No valid activation token exists for this user.",
              generateToken: "Generate Activation Token",
              limitations: "Limitations",
              noTypeChange: "User type (admin/regular user) cannot be changed. To change user type, delete the user and create a new account.",
              noPasswordChange: "Passwords for other users cannot be changed. Use the activation token to allow users to set their own password.",
            },
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
        usernameBlank: "Username cannot be blank",
        usernameTooLong: "Username cannot exceed 255 characters",
      },
      // Common
      common: {
        error: "An error occurred",
        loading: "Loading...",
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
        INVALID_TOKEN: "Invalid or expired activation token",
        RATE_LIMIT_EXCEEDED: "Too many attempts. Please try again later.",
        USERNAME_ALREADY_EXISTS: "Username already exists",
        CANNOT_DELETE_SELF: "Cannot delete your own user account",
        INVALID_PAGE: "Page must be non-negative",
        INVALID_SIZE: "Size must be between 1 and 100",
        ACTIVE_ENTRY_EXISTS: "Cannot start a new entry while another is active",
        ENTRY_NOT_FOUND: "Time entry not found",
        ENTRY_ALREADY_STOPPED: "Entry is already stopped",
        INVALID_DATE_FORMAT: "Invalid date format",
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
        lostPasswordDialog: {
          title: "Скидання пароля",
          message: "Щоб скинути пароль, зверніться до системного адміністратора. Він надасть вам безпечне посилання для скидання пароля.",
          close: "Закрити",
        },
        signIn: "Увійти",
        signingIn: "Вхід...",
        invalidCredentials: "Невірне ім'я користувача або пароль",
        sessionExpired: "Ваш сеанс закінчився, будь ласка, увійдіть знову",
        activationSuccess: "Пароль успішно встановлено! Будь ласка, увійдіть з новим паролем.",
      },
      // Activation Page
      activation: {
        title: "Встановіть пароль",
        subtitle: "Введіть новий пароль нижче",
        validating: "Перевірка посилання...",
        noToken: "Токен активації не надано",
        invalidToken: "Це посилання недійсне або закінчилося",
        contactAdmin: "Будь ласка, зверніться до системного адміністратора для отримання нового посилання.",
        welcome: "Ласкаво просимо, {{greeting}}!",
        password: "Пароль",
        passwordPlaceholder: "Введіть пароль",
        confirmPassword: "Підтвердіть пароль",
        confirmPasswordPlaceholder: "Введіть пароль ще раз",
        setPassword: "Встановити пароль",
        settingPassword: "Встановлення пароля...",
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
      // Time Logs Page
      timeLogs: {
        title: "Журнал часу",
        subtitle: "Відстежуйте свій час та керуйте завданнями",
        previousWeek: "Попередній тиждень",
        nextWeek: "Наступний тиждень",
        today: "Сьогодні",
        yesterday: "Вчора",
        currentEntry: {
          title: "Над чим ви працюєте?",
          placeholder: "Введіть назву завдання...",
        },
        start: "Почати",
        starting: "Починаємо...",
        stop: "Зупинити",
        stopping: "Зупиняємо...",
        delete: "Видалити",
        deleting: "Видалення...",
        continue: "Продовжити",
        startedAt: "Почато о",
        duration: "Тривалість",
        totalDuration: "Всього",
        inProgress: "виконується",
        noEntries: "Немає записів часу для цього тижня",
        timezoneHint: "Час показано у {{timezone}}",
        deleteDialog: {
          title: "Видалити запис часу",
          message: "Ви впевнені, що хочете видалити цей запис часу? Цю дію неможливо скасувати.",
          confirm: "Видалити",
          cancel: "Скасувати",
        },
        success: {
          started: "Запис часу почато",
          stopped: "Запис часу зупинено",
          deleted: "Запис часу видалено",
        },
        errors: {
          titleRequired: "Будь ласка, введіть назву завдання",
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
            manage: "Керувати користувачами",
            subtitle: "Перегляд та керування користувачами системи",
            deleteSuccess: "Користувача успішно видалено",
            createUser: "Створити користувача",
            table: {
              username: "Ім'я користувача",
              greeting: "Привітання",
              type: "Тип",
              actions: "Дії",
              admin: "Адміністратор",
              regularUser: "Звичайний користувач",
              edit: "Редагувати",
              delete: "Видалити",
              noUsers: "Користувачів не знайдено",
            },
            deleteConfirm: {
              title: "Видалити користувача",
              message: "Ви впевнені, що хочете видалити користувача {{userName}}? Цю дію неможливо скасувати.",
              confirm: "Видалити",
              cancel: "Скасувати",
            },
            pagination: {
              showing: "Показано {{start}} - {{end}} з {{total}} користувачів",
              previous: "Попередня",
              next: "Наступна",
              page: "Сторінка {{page}} з {{total}}",
            },
            create: {
              title: "Створити користувача",
              subtitle: "Додайте нового користувача до системи",
              back: "Назад до користувачів",
              username: "Ім'я користувача",
              usernamePlaceholder: "Введіть ім'я користувача",
              greeting: "Привітання",
              greetingPlaceholder: "Введіть привітання користувача",
              userType: "Тип користувача",
              regularUser: "Звичайний користувач",
              admin: "Адміністратор",
              create: "Створити користувача",
              creating: "Створення...",
              createSuccess: "Користувача успішно створено! Поділіться посиланням для активації нижче з користувачем, щоб дозволити йому встановити пароль.",
            },
            edit: {
              title: "Редагувати користувача",
              subtitle: "Керування обліковим записом",
              back: "Назад до користувачів",
              usernameSection: "Ім'я користувача",
              username: "Ім'я користувача",
              usernamePlaceholder: "Введіть ім'я користувача",
              save: "Зберегти зміни",
              saving: "Збереження...",
              updateSuccess: "Користувача успішно оновлено",
              userInfo: "Інформація користувача",
              greeting: "Привітання",
              userType: "Тип користувача",
              profileNote: "Примітка: Налаштування профілю (привітання, мова, локаль) керуються користувачем на сторінці особистих налаштувань.",
              activationSection: "Активація облікового запису",
              activationUrl: "URL активації",
              activationNote: "Поділіться цим URL з користувачем, щоб дозволити йому встановити пароль або скинути обліковий запис.",
              regenerateToken: "Згенерувати токен активації знову",
              regenerating: "Генерація...",
              generating: "Генерація...",
              tokenRegenerated: "Токен активації успішно згенеровано знову",
              noActivationToken: "Для цього користувача не існує дійсного токена активації.",
              generateToken: "Згенерувати токен активації",
              limitations: "Обмеження",
              noTypeChange: "Тип користувача (адміністратор/звичайний користувач) не може бути змінений. Щоб змінити тип користувача, видаліть користувача та створіть новий обліковий запис.",
              noPasswordChange: "Паролі інших користувачів не можуть бути змінені. Використовуйте токен активації, щоб дозволити користувачам встановити власний пароль.",
            },
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
        usernameBlank: "Ім'я користувача не може бути порожнім",
        usernameTooLong: "Ім'я користувача не може перевищувати 255 символів",
      },
      // Common
      common: {
        error: "Сталася помилка",
        loading: "Завантаження...",
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
        INVALID_TOKEN: "Недійсний або застарілий токен активації",
        RATE_LIMIT_EXCEEDED: "Забагато спроб. Будь ласка, спробуйте пізніше.",
        USERNAME_ALREADY_EXISTS: "Ім'я користувача вже існує",
        CANNOT_DELETE_SELF: "Неможливо видалити власний обліковий запис",
        INVALID_PAGE: "Сторінка має бути невід'ємною",
        INVALID_SIZE: "Розмір має бути між 1 і 100",
        ACTIVE_ENTRY_EXISTS: "Неможливо почати новий запис, поки інший активний",
        ENTRY_NOT_FOUND: "Запис часу не знайдено",
        ENTRY_ALREADY_STOPPED: "Запис вже зупинено",
        INVALID_DATE_FORMAT: "Невірний формат дати",
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
