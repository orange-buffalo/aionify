import { useState, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { FormMessage } from "@/components/ui/form-message";

/**
 * Hook for managing API call state and execution.
 * Handles loading state, error handling, and success messages automatically.
 *
 * @example
 * ```tsx
 * const { executeApiCall, apiCallInProgress, formMessage } = useApiExecutor();
 *
 * const handleSave = async () => {
 *   await executeApiCall(async () => {
 *     await apiPut("/api/users/profile", { greeting });
 *     return t("profile.updateSuccess");
 *   });
 * };
 *
 * return (
 *   <div>
 *     {formMessage}
 *     <Button onClick={handleSave} disabled={apiCallInProgress}>Save</Button>
 *   </div>
 * );
 * ```
 *
 * @example With factory function for cleaner syntax:
 * ```tsx
 * const { createApiCallExecutor, apiCallInProgress, formMessage } = useApiExecutor();
 *
 * const handleSave = createApiCallExecutor(async () => {
 *   await apiPut("/api/users/profile", { greeting });
 *   return t("profile.updateSuccess");
 * });
 * ```
 */
export function useApiExecutor() {
  const { t } = useTranslation();
  const [apiCallInProgress, setApiCallInProgress] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  /**
   * Executes an API call with automatic state management.
   * @param call - Async function that performs the API call. Can return a string for success message or void.
   */
  const executeApiCall = useCallback(
    async (call: () => Promise<string | void>): Promise<void> => {
      setApiCallInProgress(true);
      setError(null);
      setSuccess(null);

      try {
        const result = await call();
        if (result) {
          setSuccess(result);
        }
      } catch (err: any) {
        const errorCode = err.errorCode;
        if (errorCode) {
          setError(t(`errorCodes.${errorCode}`));
        } else {
          setError(err.message || "An error occurred");
        }
      } finally {
        setApiCallInProgress(false);
      }
    },
    [t]
  );

  /**
   * Creates an API call executor function with the same signature as the original handler.
   * This is syntactic sugar for cleaner code.
   * @param call - Async function that performs the API call
   * @returns A function that executes the API call with state management
   */
  const createApiCallExecutor = useCallback(
    (call: () => Promise<string | void>) => {
      return async () => {
        await executeApiCall(call);
      };
    },
    [executeApiCall]
  );

  /**
   * Clear both error and success messages
   */
  const clearMessages = useCallback(() => {
    setError(null);
    setSuccess(null);
  }, []);

  /**
   * FormMessage component with managed state
   */
  const formMessage = (
    <>
      {error && <FormMessage type="error" message={error} onClose={clearMessages} />}
      {success && <FormMessage type="success" message={success} onClose={clearMessages} />}
    </>
  );

  return {
    executeApiCall,
    createApiCallExecutor,
    apiCallInProgress,
    formMessage,
    clearMessages,
  };
}
