import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { Tag } from "lucide-react";
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

  // Reset selectedTags when popover opens or currentTags change
  useEffect(() => {
    if (isOpen) {
      setSelectedTags(currentTags);
    }
  }, [isOpen, currentTags]);

  const handleTagsChange = async (newTags: string[]) => {
    if (isSaving) return;
    setSelectedTags(newTags);
    setIsSaving(true);
    try {
      await onSave(newTags);
    } finally {
      setIsSaving(false);
    }
  };

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
      <PopoverContent
        className="dark w-80 p-0"
        align="start"
        collisionPadding={20}
        data-testid={`${testIdPrefix}-popover`}
      >
        <TagListContent
          selectedTags={selectedTags}
          onTagsChange={handleTagsChange}
          disabled={isSaving}
          saving={isSaving}
          testIdPrefix={testIdPrefix}
          isOpen={isOpen}
          onEnterWithEmptyInput={() => setIsOpen(false)}
          onEscape={() => setIsOpen(false)}
        />
      </PopoverContent>
    </Popover>
  );
}
