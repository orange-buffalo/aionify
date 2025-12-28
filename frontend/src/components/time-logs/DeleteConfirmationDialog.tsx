import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
}

export function DeleteConfirmationDialog({
  open,
  onOpenChange,
  entry,
  locale,
  isDeleting,
  onConfirm,
}: DeleteConfirmationDialogProps) {
  const { t } = useTranslation();

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="dark">
        <DialogHeader>
          <DialogTitle className="text-foreground">{t("timeLogs.deleteDialog.title")}</DialogTitle>
          <DialogDescription className="text-foreground">{t("timeLogs.deleteDialog.message")}</DialogDescription>
        </DialogHeader>
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
        <DialogFooter>
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={isDeleting} className="text-foreground">
            {t("timeLogs.deleteDialog.cancel")}
          </Button>
          <Button variant="destructive" onClick={onConfirm} disabled={isDeleting} data-testid="confirm-delete-button">
            {isDeleting ? t("timeLogs.deleting") : t("timeLogs.deleteDialog.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
