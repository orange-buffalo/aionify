import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Tag } from "lucide-react";
import { cn } from "@/lib/utils";
import { TagListContent } from "./TagListContent";

interface TagSelectorProps {
  selectedTags: string[];
  onTagsChange: (tags: string[]) => void;
  disabled?: boolean;
  testIdPrefix?: string;
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
  const [open, setOpen] = useState(false);

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
        <TagListContent
          selectedTags={selectedTags}
          onTagsChange={onTagsChange}
          disabled={disabled}
          testIdPrefix={testIdPrefix}
          isOpen={open}
        />
      </PopoverContent>
    </Popover>
  );
}
