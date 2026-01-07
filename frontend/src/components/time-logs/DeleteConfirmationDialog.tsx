import { useTranslation } from "react-i18next";
import { ConfirmationDialog } from "@/components/ui/confirmation-dialog";
import { formatTime } from "@/lib/date-format";
import { calculateDuration, formatDuration } from "@/lib/time-utils";
import type { TimeEntry } from "./types";

interface DeleteConfirmationDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  entry: TimeEntry | null;
  locale: string;
  isDeleting: boolean;
  onConfirm: () => Promise<void>;
  formMessage?: React.ReactNode;
}

export function DeleteConfirmationDialog({
  open,
  onOpenChange,
  entry,
  locale,
  isDeleting,
  onConfirm,
  formMessage,
}: DeleteConfirmationDialogProps) {
  const { t } = useTranslation();

  return (
    <ConfirmationDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("timeLogs.deleteDialog.title")}
      description={t("timeLogs.deleteDialog.message")}
      confirmLabel={isDeleting ? t("timeLogs.deleting") : t("timeLogs.deleteDialog.confirm")}
      cancelLabel={t("timeLogs.deleteDialog.cancel")}
      onConfirm={onConfirm}
      isConfirming={isDeleting}
      confirmTestId="confirm-delete-button"
    >
      {formMessage}
      {entry && (
        <div className="mt-2 p-2 bg-muted rounded text-foreground">
          <div className="font-semibold">{entry.title}</div>
          <div className="text-sm">
            {formatTime(entry.startTime, locale)} -{" "}
            {entry.endTime ? formatTime(entry.endTime, locale) : t("timeLogs.inProgress")}
          </div>
          <div className="text-sm">
            {t("timeLogs.duration")}: {formatDuration(calculateDuration(entry.startTime, entry.endTime))}
          </div>
        </div>
      )}
    </ConfirmationDialog>
  );
}
