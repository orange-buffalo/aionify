import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { TagSelector } from "./TagSelector";

interface EditGroupedEntryFormProps {
  title: string;
  tags: string[];
  isSaving: boolean;
  onTitleChange: (title: string) => void;
  onTagsChange: (tags: string[]) => void;
  onSave: () => void;
  onCancel: () => void;
}

export function EditGroupedEntryForm({
  title,
  tags,
  isSaving,
  onTitleChange,
  onTagsChange,
  onSave,
  onCancel,
}: EditGroupedEntryFormProps) {
  const { t } = useTranslation();

  return (
    <div className="space-y-4 p-3 border border-border rounded-md" data-testid="edit-grouped-entry-form">
      <div>
        <Label htmlFor="edit-grouped-entry-title" className="text-foreground">
          {t("timeLogs.currentEntry.titleLabel")}
        </Label>
        <div className="mt-2 flex items-center gap-2">
          <Input
            id="edit-grouped-entry-title"
            value={title}
            onChange={(e) => onTitleChange(e.target.value)}
            className="flex-1 text-foreground"
            data-testid="edit-grouped-entry-title-input"
            disabled={isSaving}
          />
          <TagSelector
            selectedTags={tags}
            onTagsChange={onTagsChange}
            disabled={isSaving}
            testIdPrefix="edit-grouped-entry-tags"
          />
        </div>
      </div>
      <div className="flex items-center gap-2">
        <Button
          onClick={onSave}
          disabled={!title.trim() || isSaving}
          data-testid="save-edit-grouped-entry-button"
          className="bg-teal-600 hover:bg-teal-700"
        >
          {t("timeLogs.currentEntry.save")}
        </Button>
        <Button
          variant="ghost"
          onClick={onCancel}
          disabled={isSaving}
          data-testid="cancel-edit-grouped-entry-button"
          className="text-foreground"
        >
          {t("timeLogs.currentEntry.cancel")}
        </Button>
      </div>
    </div>
  );
}
