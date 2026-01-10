import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Tag, Plus, Send, Loader2 } from "lucide-react";
import { apiGet } from "@/lib/api";

interface InlineTagsEditProps {
  currentTags: string[];
  onSave: (newTags: string[]) => Promise<void>;
  testIdPrefix?: string;
}

interface TagStat {
  tag: string;
  count: number;
  isLegacy: boolean;
}

export function InlineTagsEdit({ currentTags, onSave, testIdPrefix = "inline-tags-edit" }: InlineTagsEditProps) {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  const [selectedTags, setSelectedTags] = useState<string[]>(currentTags);
  const [availableTags, setAvailableTags] = useState<string[]>([]);
  const [newTagInput, setNewTagInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

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

  // Reset selectedTags when popover opens
  useEffect(() => {
    if (isOpen) {
      setSelectedTags(currentTags);
      setNewTagInput("");
    }
  }, [isOpen, currentTags]);

  const handleToggleTag = (tag: string) => {
    if (isSaving) return;

    if (selectedTags.includes(tag)) {
      setSelectedTags(selectedTags.filter((t) => t !== tag));
    } else {
      setSelectedTags([...selectedTags, tag]);
    }
  };

  const handleAddNewTag = () => {
    if (isSaving) return;

    const trimmedTag = newTagInput.trim();
    if (!trimmedTag) return;

    // Add to selected tags if not already selected
    if (!selectedTags.includes(trimmedTag)) {
      setSelectedTags([...selectedTags, trimmedTag]);
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

      // If there's text in the input, add the tag
      if (newTagInput.trim()) {
        handleAddNewTag();
      } else {
        // Otherwise, save if there are changes
        handleSave();
      }
    } else if (e.key === "Escape") {
      setIsOpen(false);
    }
  };

  const handleSave = async () => {
    if (isSaving) return;

    setIsSaving(true);
    try {
      await onSave(selectedTags);
      setIsOpen(false);
    } finally {
      setIsSaving(false);
    }
  };

  // Check if tags have changed
  const tagsChanged = () => {
    const currentSet = new Set(currentTags);
    const selectedSet = new Set(selectedTags);

    if (currentSet.size !== selectedSet.size) return true;

    for (const tag of currentTags) {
      if (!selectedSet.has(tag)) return true;
    }

    return false;
  };

  const hasChanges = tagsChanged();

  return (
    <Popover open={isOpen} onOpenChange={setIsOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          className="text-foreground"
          data-testid={`${testIdPrefix}-button`}
          title={t("timeLogs.edit")}
        >
          <Tag className="h-4 w-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="dark w-80 p-0" align="start" data-testid={`${testIdPrefix}-popover`}>
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
              disabled={loading || isSaving}
            />
            <Button
              size="icon"
              onClick={handleAddNewTag}
              disabled={!newTagInput.trim() || loading || isSaving}
              data-testid={`${testIdPrefix}-add-tag-button`}
              type="button"
              variant="ghost"
            >
              <Plus className="h-4 w-4" />
            </Button>
          </div>

          {/* Tags list */}
          <div className="max-h-64 overflow-y-auto">
            {loading ? (
              <div
                className="text-center py-4 text-sm text-muted-foreground text-foreground"
                data-testid={`${testIdPrefix}-loading`}
              >
                {t("common.loading")}
              </div>
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
                      disabled={isSaving}
                    />
                    <span className="text-sm text-foreground flex-1">{tag}</span>
                  </label>
                ))}
              </div>
            )}
          </div>

          {/* Save button */}
          <div className="pt-2 border-t border-border">
            <Button
              onClick={handleSave}
              disabled={!hasChanges || isSaving}
              className="w-full bg-teal-600 hover:bg-teal-700"
              data-testid={`${testIdPrefix}-save-button`}
            >
              {isSaving ? (
                <Loader2 className="h-4 w-4 animate-spin" data-testid={`${testIdPrefix}-loading-icon`} />
              ) : (
                <>
                  <Send className="h-4 w-4 mr-2" data-testid={`${testIdPrefix}-send-icon`} />
                  {t("timeLogs.currentEntry.save")}
                </>
              )}
            </Button>
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
