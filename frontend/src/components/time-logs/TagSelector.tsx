import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Tag, Plus } from "lucide-react";
import { apiGet } from "@/lib/api";
import { cn } from "@/lib/utils";

interface TagSelectorProps {
  selectedTags: string[];
  onTagsChange: (tags: string[]) => void;
  disabled?: boolean;
  testIdPrefix?: string;
}

interface TagStat {
  tag: string;
  count: number;
  isLegacy: boolean;
}

/**
 * Reusable component for selecting tags on time log entries.
 * Displays available tags with checkboxes and allows adding new tags.
 */
export function TagSelector({
  selectedTags,
  onTagsChange,
  disabled = false,
  testIdPrefix = "tag-selector",
}: TagSelectorProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [availableTags, setAvailableTags] = useState<string[]>([]);
  const [newTagInput, setNewTagInput] = useState("");
  const [loading, setLoading] = useState(false);

  // Load available tags from the API
  useEffect(() => {
    async function loadTags() {
      if (!open) return; // Only load when popover is opened

      try {
        setLoading(true);
        const response = await apiGet<{ tags: TagStat[] }>("/api/tags/stats");

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
  }, [open]);

  const handleToggleTag = (tag: string) => {
    if (selectedTags.includes(tag)) {
      onTagsChange(selectedTags.filter((t) => t !== tag));
    } else {
      onTagsChange([...selectedTags, tag]);
    }
  };

  const handleAddNewTag = () => {
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
      handleAddNewTag();
    }
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          size="icon"
          className={cn(
            "rounded-full h-10 w-10",
            selectedTags.length > 0 && "bg-teal-600 hover:bg-teal-700 text-white border-teal-600"
          )}
          disabled={disabled}
          data-testid={`${testIdPrefix}-button`}
          type="button"
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
              disabled={loading}
            />
            <Button
              size="icon"
              onClick={handleAddNewTag}
              disabled={!newTagInput.trim() || loading}
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
              <div className="text-center py-4 text-sm text-muted-foreground" data-testid={`${testIdPrefix}-loading`}>
                {t("common.loading")}
              </div>
            ) : availableTags.length === 0 ? (
              <div className="text-center py-4 text-sm text-muted-foreground" data-testid={`${testIdPrefix}-empty`}>
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
                    />
                    <span className="text-sm text-foreground flex-1">{tag}</span>
                  </label>
                ))}
              </div>
            )}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
