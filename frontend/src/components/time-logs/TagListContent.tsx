import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Loader } from "@/components/ui/loader";
import { Plus, Loader2 } from "lucide-react";
import { apiGet } from "@/lib/api";

interface TagStat {
  tag: string;
  count: number;
  isLegacy: boolean;
}

interface TagListContentProps {
  selectedTags: string[];
  onTagsChange: (tags: string[]) => void;
  disabled?: boolean;
  saving?: boolean;
  testIdPrefix?: string;
  isOpen: boolean;
  onEnterWithEmptyInput?: () => void;
  onEscape?: () => void;
}

/**
 * Shared component for tag selection UI.
 * Displays available tags with checkboxes and allows adding new tags.
 * Used by both TagSelector and InlineTagsEdit.
 */
export function TagListContent({
  selectedTags,
  onTagsChange,
  disabled = false,
  saving = false,
  testIdPrefix = "tag-list",
  isOpen,
  onEnterWithEmptyInput,
  onEscape,
}: TagListContentProps) {
  const { t } = useTranslation();
  const [availableTags, setAvailableTags] = useState<string[]>([]);
  const [newTagInput, setNewTagInput] = useState("");
  const [loading, setLoading] = useState(false);

  // Load available tags from the API when popover opens
  useEffect(() => {
    async function loadTags() {
      if (!isOpen) return;

      try {
        setLoading(true);
        const response = await apiGet<{ tags: TagStat[] }>("/api-ui/tags/stats");

        // Filter out legacy tags and extract tag names
        const nonLegacyTags = (response.tags || []).filter((stat) => !stat.isLegacy).map((stat) => stat.tag);

        setAvailableTags(nonLegacyTags);
      } catch (err) {
        console.error("Failed to load tags:", err);
        setAvailableTags([]);
      } finally {
        setLoading(false);
      }
    }

    loadTags();
  }, [isOpen]);

  // Reset input when popover opens
  useEffect(() => {
    if (isOpen) {
      setNewTagInput("");
    }
  }, [isOpen]);

  const handleToggleTag = (tag: string) => {
    if (disabled) return;

    if (selectedTags.includes(tag)) {
      onTagsChange(selectedTags.filter((t) => t !== tag));
    } else {
      onTagsChange([...selectedTags, tag]);
    }
  };

  const handleAddNewTag = () => {
    if (disabled) return;

    const trimmedTag = newTagInput.trim();
    if (!trimmedTag) return;

    // Add to selected tags if not already selected
    if (!selectedTags.includes(trimmedTag)) {
      onTagsChange([...selectedTags, trimmedTag]);
    }

    // Add to available tags if not already there
    if (!availableTags.includes(trimmedTag)) {
      setAvailableTags([...availableTags, trimmedTag].sort());
    }

    setNewTagInput("");
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      e.preventDefault();

      if (newTagInput.trim()) {
        // Add the tag if there's text
        handleAddNewTag();
      } else if (onEnterWithEmptyInput) {
        // Call callback if input is empty and callback is provided
        onEnterWithEmptyInput();
      }
    } else if (e.key === "Escape" && onEscape) {
      onEscape();
    }
  };

  return (
    <div className="p-3 space-y-2">
      {/* Add new tag section */}
      <div className="flex gap-2 pb-2 border-b border-border">
        <Input
          value={newTagInput}
          onChange={(e) => setNewTagInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={t("timeLogs.tags.addNewPlaceholder")}
          className="flex-1 text-foreground"
          data-testid={`${testIdPrefix}-new-tag-input`}
          disabled={loading || disabled}
        />
        <Button
          size="icon"
          onClick={handleAddNewTag}
          disabled={!newTagInput.trim() || loading || disabled}
          data-testid={`${testIdPrefix}-add-tag-button`}
          type="button"
          variant="ghost"
        >
          {saving ? (
            <Loader2 className="h-4 w-4 animate-spin" data-testid={`${testIdPrefix}-saving-icon`} />
          ) : (
            <Plus className="h-4 w-4" />
          )}
        </Button>
      </div>

      {/* Tags list */}
      <div className="max-h-64 overflow-y-auto">
        {loading ? (
          <Loader className="py-4" size="sm" testId={`${testIdPrefix}-loading`} />
        ) : availableTags.length === 0 ? (
          <div
            className="text-center py-4 text-sm text-muted-foreground text-foreground"
            data-testid={`${testIdPrefix}-empty`}
          >
            {t("timeLogs.tags.noTags")}
          </div>
        ) : (
          <div className="space-y-1" data-testid={`${testIdPrefix}-list`}>
            {availableTags.map((tag) => (
              <label
                key={tag}
                className="flex items-center gap-2 px-2 py-1.5 rounded-sm hover:bg-accent cursor-pointer"
                data-testid={`${testIdPrefix}-item-${tag}`}
              >
                <Checkbox
                  checked={selectedTags.includes(tag)}
                  onCheckedChange={() => handleToggleTag(tag)}
                  data-testid={`${testIdPrefix}-checkbox-${tag}`}
                  disabled={disabled}
                />
                <span className="text-sm text-foreground flex-1">{tag}</span>
              </label>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
