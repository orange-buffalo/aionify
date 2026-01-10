import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { Tag, Send, Loader2 } from "lucide-react";
import { TagListContent } from "./TagListContent";

interface InlineTagsEditProps {
  currentTags: string[];
  onSave: (newTags: string[]) => Promise<void>;
  testIdPrefix?: string;
}

export function InlineTagsEdit({ currentTags, onSave, testIdPrefix = "inline-tags-edit" }: InlineTagsEditProps) {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  const [selectedTags, setSelectedTags] = useState<string[]>(currentTags);
  const [isSaving, setIsSaving] = useState(false);

  // Reset selectedTags when popover opens
  useEffect(() => {
    if (isOpen) {
      setSelectedTags(currentTags);
    }
  }, [isOpen, currentTags]);

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
        <TagListContent
          selectedTags={selectedTags}
          onTagsChange={setSelectedTags}
          disabled={isSaving}
          testIdPrefix={testIdPrefix}
          isOpen={isOpen}
          onEnterWithEmptyInput={handleSave}
          onEscape={() => setIsOpen(false)}
        />

        {/* Save button */}
        <div className="p-3 pt-0">
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
