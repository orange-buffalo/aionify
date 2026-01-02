# User Administration Guide

This guide explains how to manage users in Aionify.

## Default Admin User

On first startup, if no admin user exists in the database, Aionify automatically creates a default admin user:

- **Username**: `sudo`
- **Password**: Generated randomly (100+ characters)

The generated password is printed to the application output (stdout) on startup:

```
============================================================
DEFAULT ADMIN CREATED
Username: sudo
Password: <random-generated-password>
Please change this password after first login!
============================================================
```

**Important**: 
- Check the container logs immediately after first startup to retrieve the password
- The password is only displayed once and is not stored anywhere
- Change the password after first login for security

To view the password in Docker:
```bash
docker logs aionify 2>&1 | grep -A5 "DEFAULT ADMIN CREATED"
```

If you missed the password, see [Troubleshooting](#cannot-find-default-admin-password) below.

## User Onboarding Flow

Aionify uses a secure activation token system for user onboarding. Here's how to add a new user to the system:

### 1. Create User Account

As an administrator:

1. Navigate to **Admin Portal** → **Users**
2. Click the **Create User** button
3. Fill in the user details:
   - **Username**: Unique identifier for the user (required)
   - **Greeting**: Display name shown in the UI (required)
   - **User Type**: Select either "Regular User" or "Administrator"
4. Click **Create User**

When you create a user, the system automatically:
- Generates a secure random password (100+ characters) that cannot be used until activated
- Sets the default language to English (users can change this in their settings)
- Creates an activation token valid for 10 days (240 hours)

### 2. Share Activation Link

After creating the user, you'll be redirected to the user's edit page where you'll see:

- A **success message** confirming the user was created
- The **activation URL** in a read-only field

**Share this activation URL with the new user** via a secure channel (email, messaging, etc.). This link allows them to:
- Set their own password
- Activate their account
- Log in to the system

Example activation URL:
```
https://aionify.example.com/activate?token=abc123xyz789...
```

### 3. User Activation

The new user should:

1. Click the activation link you provided
2. Enter and confirm their desired password
3. Click **Set Password**
4. Log in with their username and new password

**Important**: 
- Activation tokens expire after 10 days
- Tokens can only be used once
- If a token expires or is lost, you can regenerate it (see below)

### 4. Regenerating Activation Tokens

If a user loses their activation link or the token expires:

1. Navigate to **Admin Portal** → **Users**
2. Click the **Edit** button for that user
3. Scroll to the **Account Activation** section
4. Click **Regenerate Activation Token**
5. Share the new activation URL with the user

The old token will be invalidated and a new one created with a fresh 10-day expiration.

## Managing Users

### Editing Users

Administrators can edit limited user properties:

1. Navigate to **Admin Portal** → **Users**
2. Click the **Edit** button for a user
3. Update the **Username** field if needed
4. Click **Save Changes**

**Limitations**:
- User type (admin/regular user) cannot be changed after creation
- Passwords cannot be changed by administrators (use activation tokens)
- Profile settings (greeting, language, locale) are managed by users in their personal settings

### Deleting Users

To delete a user:

1. Navigate to **Admin Portal** → **Users**
2. Click the **Actions** menu (⋮) for the user
3. Select **Delete**
4. Confirm the deletion

**Important**: 
- You cannot delete your own admin account
- User deletion is permanent and cannot be undone
- Consider the impact on related data before deleting users

## User Roles

Aionify supports two user types:

### Regular User
- Access to personal portal features
- Can view and manage their own time entries
- Can update their own profile settings
- Cannot access admin features

### Administrator
- All regular user permissions
- Can create, edit, and delete users
- Can view all users in the system
- Can regenerate activation tokens
- Access to admin portal and system settings

## Password Management

### User Self-Service

Users can change their own passwords:

1. Navigate to **Settings** (gear icon in navigation)
2. Scroll to **Change Password** section
3. Enter current password
4. Enter and confirm new password
5. Click **Change Password**

### Password Reset by Admin

Administrators cannot directly reset user passwords. Instead:

1. Go to the user's edit page
2. Click **Regenerate Activation Token** in the Account Activation section
3. Share the new activation URL with the user
4. The user can set a new password using the activation link

This approach ensures:
- Only the user knows their password
- Secure password reset process
- Audit trail of password resets

## Security Best Practices

1. **Use Strong Passwords**: Encourage users to choose strong, unique passwords
2. **Change Default Admin Password**: Always change the default admin password after first login
3. **Secure Activation Links**: Share activation URLs through secure channels
4. **Monitor Token Expiration**: Regenerate tokens promptly if they expire
5. **Regular Access Review**: Periodically review user accounts and remove inactive users
6. **Limit Admin Accounts**: Only grant admin privileges to trusted users who need them

## Troubleshooting

### Cannot Find Default Admin Password

If you missed the default admin password during first startup:

1. Stop the application
2. Delete the admin user from the database:
   ```sql
   DELETE FROM app_user WHERE user_name = 'sudo';
   ```
3. Restart the application to regenerate a new admin user with a new password

### User Cannot Activate Account

Common issues:

1. **Token Expired**: Activation tokens are valid for 10 days. Regenerate a new token.
2. **Token Already Used**: Each token can only be used once. Generate a new token if needed.
3. **Invalid Token**: Verify the complete URL was copied. Generate a new token if the link is broken.

### Username Already Exists

Usernames must be unique. If you try to create a user with an existing username:

1. Check if the user already exists in the Users list
2. Choose a different username
3. If the user account is no longer needed, delete it first

### User Type Changes

User types (admin/regular user) cannot be changed after creation. To change a user's type:

1. Note the user's current information
2. Delete the existing user account
3. Create a new account with the desired user type
4. Share the activation link with the user

**Important**: This will require the user to set up their account again with a new password.

## API Access

Users can generate API tokens for programmatic access to Aionify. See the [Public API Guide](./public-api.md) for details on:

- How to generate and manage API tokens
- Available API endpoints
- Authentication and security
- Browser integrations for GitHub and Jira

API tokens provide full access to a user's account, so users should:
- Keep tokens secure and never share them
- Rotate tokens regularly
- Delete unused tokens
- Use HTTPS only when making API requests

See [Browser Integrations Guide](./browser-integrations.md) for Tampermonkey scripts that enable time tracking from GitHub and Jira web pages.
