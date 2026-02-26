import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { Send, Loader2 } from "lucide-react";
import { EntryAutocomplete } from "./EntryAutocomplete";

interface InlineTitleEditProps {
  currentTitle: string;
  onSave: (newTitle: string) => Promise<void>;
  onAutocompleteSelect: (title: string, tags: string[]) => Promise<void>;
  locale: string;
  testIdPrefix?: string;
}

export function InlineTitleEdit({
  currentTitle,
  onSave,
  onAutocompleteSelect,
  locale,
  testIdPrefix = "inline-title-edit",
}: InlineTitleEditProps) {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  const [title, setTitle] = useState(currentTitle);
  const [isSaving, setIsSaving] = useState(false);

  // Reset title when popover opens
  useEffect(() => {
    if (isOpen) {
      setTitle(currentTitle);
    }
  }, [isOpen, currentTitle]);

  const handleSave = async () => {
    if (!title.trim() || title.length > 1000 || isSaving) return;

    setIsSaving(true);
    try {
      await onSave(title.trim());
      setIsOpen(false);
    } finally {
      setIsSaving(false);
    }
  };

  const handleAutocompleteSelect = async (entry: { title: string; tags: string[] }) => {
    if (isSaving) return;

    setIsSaving(true);
    try {
      await onAutocompleteSelect(entry.title, entry.tags);
      setIsOpen(false);
    } finally {
      setIsSaving(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      e.preventDefault();
      handleSave();
    } else if (e.key === "Escape") {
      setIsOpen(false);
    }
  };

  const isInvalid = !title.trim() || title.length > 1000;

  return (
    <Popover open={isOpen} onOpenChange={setIsOpen}>
      <PopoverTrigger asChild>
        <span className="font-medium text-foreground cursor-pointer" data-testid={`${testIdPrefix}-trigger`}>
          {currentTitle}
        </span>
      </PopoverTrigger>
      <PopoverContent
        className="dark w-auto min-w-[300px] p-2"
        align="start"
        side="bottom"
        data-testid={`${testIdPrefix}-popover`}
      >
        <div className="flex items-center gap-2">
          <EntryAutocomplete
            value={title}
            onChange={setTitle}
            onSelect={handleAutocompleteSelect}
            onKeyDown={handleKeyDown}
            disabled={isSaving}
            testId={`${testIdPrefix}-input`}
            locale={locale}
            placeholder={t("timeLogs.currentEntry.placeholder")}
          />
          <Button
            onClick={handleSave}
            disabled={isInvalid || isSaving}
            className="bg-teal-600 hover:bg-teal-700"
            size="sm"
            data-testid={`${testIdPrefix}-save-button`}
          >
            {isSaving ? (
              <Loader2 className="h-4 w-4 animate-spin" data-testid={`${testIdPrefix}-loading-icon`} />
            ) : (
              <Send className="h-4 w-4" data-testid={`${testIdPrefix}-send-icon`} />
            )}
          </Button>
        </div>
      </PopoverContent>
    </Popover>
  );
}
