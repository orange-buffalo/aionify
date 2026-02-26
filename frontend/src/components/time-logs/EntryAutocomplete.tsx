import { useState, useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { Input } from "@/components/ui/input";
import { Loader } from "@/components/ui/loader";
import { apiGet } from "@/lib/api";
import { cn } from "@/lib/utils";
import { formatDateTime } from "@/lib/date-format";

interface AutocompleteEntry {
  title: string;
  tags: string[];
  lastStartTime: string;
}

interface EntryAutocompleteProps {
  value: string;
  onChange: (value: string) => void;
  onSelect: (entry: AutocompleteEntry) => void;
  onKeyDown?: (e: React.KeyboardEvent<HTMLInputElement>) => void;
  placeholder?: string;
  disabled?: boolean;
  testId?: string;
  locale: string;
}

/**
 * Autocomplete input for time log entry titles.
 * Shows suggestions based on previous entries and allows selecting them to copy title and tags.
 */
export function EntryAutocomplete({
  value,
  onChange,
  onSelect,
  onKeyDown,
  placeholder,
  disabled = false,
  testId = "new-entry-input",
  locale,
}: EntryAutocompleteProps) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [suggestions, setSuggestions] = useState<AutocompleteEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const debounceTimerRef = useRef<NodeJS.Timeout | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const selectedEntryTitleRef = useRef<string | null>(null);

  // Debounced search function
  useEffect(() => {
    if (value === selectedEntryTitleRef.current) {
      // If the current value matches the selected entry title, do not fetch suggestions
      return;
    }
    selectedEntryTitleRef.current = null;

    const query = value.trim();

    // Clear suggestions if input is empty
    if (!query) {
      setSuggestions([]);
      setOpen(false);
      return;
    }

    // Clear any existing timer
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }

    // Set new timer
    debounceTimerRef.current = setTimeout(async () => {
      try {
        setLoading(true);
        const response = await apiGet<{ entries: AutocompleteEntry[] }>(
          `/api-ui/time-log-entries/autocomplete?query=${encodeURIComponent(query)}`
        );

        setSuggestions(response.entries || []);
        setOpen((response.entries || []).length > 0 || query.length > 0);
        setHighlightedIndex(-1);
      } catch (err) {
        console.error("Failed to fetch autocomplete suggestions:", err);
        setSuggestions([]);
        setOpen(false);
      } finally {
        setLoading(false);
      }
    }, 300); // 300ms debounce delay

    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, [value]);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange(e.target.value);
  };

  const handleSelectEntry = (entry: AutocompleteEntry) => {
    onSelect(entry);
    setOpen(false);
    setSuggestions([]);
    setHighlightedIndex(-1);
    selectedEntryTitleRef.current = entry.title;
    // Switch focus to the start button
    const startButton = document.querySelector('[data-testid="start-button"]') as HTMLElement;
    if (startButton) {
      startButton.focus();
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    // Handle Escape key to close popover (works for both results and no-results)
    if (e.key === "Escape" && (open || showNoResults)) {
      e.preventDefault();
      setOpen(false);
      setHighlightedIndex(-1);
      return;
    }

    // If no suggestions, pass through to parent handler
    if (!open || suggestions.length === 0) {
      onKeyDown?.(e);
      return;
    }

    if (e.key === "ArrowDown") {
      e.preventDefault();
      setHighlightedIndex((prev) => (prev < suggestions.length - 1 ? prev + 1 : 0));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setHighlightedIndex((prev) => (prev > 0 ? prev - 1 : suggestions.length - 1));
    } else if (e.key === "Enter" && highlightedIndex >= 0) {
      e.preventDefault();
      handleSelectEntry(suggestions[highlightedIndex]);
    } else {
      onKeyDown?.(e);
    }
  };

  // Show no results message if search was performed but no results
  const showNoResults = !loading && value.trim() && suggestions.length === 0 && open;

  return (
    <div className="relative flex-1">
      <Input
        ref={inputRef}
        value={value}
        onChange={handleInputChange}
        onKeyDown={handleKeyDown}
        onFocus={() => {
          // Re-open if there are results and input has value
          if (value.trim() && (suggestions.length > 0 || showNoResults)) {
            setOpen(true);
          }
        }}
        onBlur={() => {
          // Close the popover when input loses focus
          // Use a small delay to allow click events on suggestions to fire first
          setTimeout(() => {
            setOpen(false);
            setHighlightedIndex(-1);
          }, 200);
        }}
        placeholder={placeholder}
        disabled={disabled}
        className="text-foreground"
        data-testid={testId}
      />
      {(open || showNoResults) && (
        <div
          className="absolute z-50 w-full mt-1 rounded-md border border-input bg-popover p-2 text-popover-foreground shadow-md"
          data-testid="autocomplete-popover"
        >
          {loading ? (
            <Loader className="py-2" size="sm" />
          ) : showNoResults ? (
            <div className="text-center py-2 text-sm text-muted-foreground" data-testid="autocomplete-no-results">
              {t("timeLogs.autocomplete.noResults")}
            </div>
          ) : (
            <div className="space-y-1">
              {suggestions.map((entry, index) => (
                <button
                  key={index}
                  type="button"
                  onMouseDown={(e) => {
                    // Prevent blur event on input when clicking suggestion
                    e.preventDefault();
                  }}
                  onClick={() => handleSelectEntry(entry)}
                  className={cn(
                    "w-full text-left px-3 py-2 rounded-sm hover:bg-accent cursor-pointer transition-colors",
                    highlightedIndex === index && "bg-accent"
                  )}
                  data-testid={`autocomplete-item-${index}`}
                  data-highlighted={highlightedIndex === index ? "true" : "false"}
                >
                  <div className="font-medium text-foreground text-sm">{entry.title}</div>
                  <div className="text-xs text-muted-foreground mt-0.5" data-testid="autocomplete-last-started">
                    {t("timeLogs.autocomplete.lastStarted", { time: formatDateTime(entry.lastStartTime, locale) })}
                  </div>
                  {entry.tags && entry.tags.length > 0 && (
                    <div className="text-xs text-muted-foreground mt-0.5" data-testid="autocomplete-tags">
                      {t("timeLogs.autocomplete.tags", { tags: entry.tags.join(", ") })}
                    </div>
                  )}
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
