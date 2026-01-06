import { useTranslation } from "react-i18next";
import { FormMessage } from "@/components/ui/form-message";
import { WeekNavigation } from "./WeekNavigation";
import { DayGroup } from "./DayGroup";
import { DeleteConfirmationDialog } from "./DeleteConfirmationDialog";
import { useStoppedEntries } from "@/hooks/useStoppedEntries";

interface StoppedEntriesListProps {
  weekStart: Date;
  locale: string;
  startOfWeek: number;
  userLocale: string | null;
  weekRange: string;
  onPreviousWeek: () => void;
  onNextWeek: () => void;
}

/**
 * Self-contained stopped entries list that manages its own state.
 * Only re-renders when stopped entries state changes.
 */
export function StoppedEntriesList({
  weekStart,
  locale,
  startOfWeek,
  userLocale,
  weekRange,
  onPreviousWeek,
  onNextWeek,
}: StoppedEntriesListProps) {
  const { t } = useTranslation();
  const {
    dayGroups,
    weeklyTotal,
    loading,
    editingEntryId,
    isSaving,
    deleteDialogOpen,
    entryToDelete,
    isDeleting,
    error,
    handleContinue,
    handleDeleteClick,
    handleDelete,
    handleEditEntry,
    handleCancelEditEntry,
    handleSaveStoppedEntry,
    handleSaveGroupEdit,
    setDeleteDialogOpen,
  } = useStoppedEntries(weekStart, userLocale);

  return (
    <>
      {error && <FormMessage type="error" message={error} testId="time-logs-error" />}

      {/* Week Navigation */}
      <WeekNavigation
        weekRange={weekRange}
        weeklyTotal={weeklyTotal}
        locale={locale}
        onPreviousWeek={onPreviousWeek}
        onNextWeek={onNextWeek}
      />

      {/* Time Entries List */}
      {loading ? (
        <div className="text-center py-8 text-foreground">{t("common.loading")}</div>
      ) : dayGroups.length === 0 ? (
        <div className="text-center py-8 text-muted-foreground" data-testid="no-entries">
          {t("timeLogs.noEntries")}
        </div>
      ) : (
        <div className="space-y-6">
          {dayGroups.map((group) => (
            <DayGroup
              key={group.date}
              group={group}
              locale={locale}
              startOfWeek={startOfWeek}
              editingEntryId={editingEntryId}
              isSaving={isSaving}
              onContinue={handleContinue}
              onDelete={handleDeleteClick}
              onEdit={handleEditEntry}
              onSaveEdit={handleSaveStoppedEntry}
              onCancelEdit={handleCancelEditEntry}
              onSaveGroupEdit={handleSaveGroupEdit}
            />
          ))}
        </div>
      )}

      <DeleteConfirmationDialog
        open={deleteDialogOpen}
        onOpenChange={setDeleteDialogOpen}
        entry={entryToDelete}
        locale={locale}
        isDeleting={isDeleting}
        onConfirm={handleDelete}
      />
    </>
  );
}
