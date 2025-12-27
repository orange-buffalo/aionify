import { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { PortalLayout } from "@/components/layout/PortalLayout";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { FormMessage } from "@/components/ui/form-message";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { apiGet, apiPost, apiRequest } from "@/lib/api";
import { MoreVertical, Archive, ArchiveRestore } from "lucide-react";

interface TagStat {
  tag: string;
  count: number;
  isLegacy: boolean;
}

interface TagStatsResponse {
  tags: TagStat[];
}

export function SettingsPage() {
  const { t } = useTranslation();
  const [tags, setTags] = useState<TagStat[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);

  const loadTags = async () => {
    setLoading(true);
    setError(null);

    try {
      const data = await apiGet<TagStatsResponse>("/api/tags/stats");
      // Defensive check: Handle edge case where API might return empty object
      // This can happen if serialization fails or in certain error scenarios
      // Better to show empty list than crash with "Cannot read properties of undefined"
      if (data && data.tags && Array.isArray(data.tags)) {
        setTags(data.tags);
      } else {
        setTags([]);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "An error occurred");
    } finally {
      setLoading(false);
    }
  };

  const handleMarkAsLegacy = async (tag: string) => {
    setActionInProgress(tag);
    setError(null);

    try {
      await apiPost("/api/tags/legacy", { tag });
      await loadTags();
    } catch (err) {
      setError(err instanceof Error ? err.message : "An error occurred");
    } finally {
      setActionInProgress(null);
    }
  };

  const handleRemoveFromLegacy = async (tag: string) => {
    setActionInProgress(tag);
    setError(null);

    try {
      await apiRequest("/api/tags/legacy", {
        method: "DELETE",
        body: JSON.stringify({ tag }),
      });
      await loadTags();
    } catch (err) {
      setError(err instanceof Error ? err.message : "An error occurred");
    } finally {
      setActionInProgress(null);
    }
  };

  useEffect(() => {
    loadTags();
  }, []);

  return (
    <PortalLayout testId="settings-page">
      <div className="p-8">
        <div className="max-w-4xl mx-auto">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground" data-testid="settings-title">
              {t("settings.title")}
            </h1>
            <p className="text-muted-foreground">{t("settings.subtitle")}</p>
          </div>

          <Card className="border-none shadow-md">
            <CardHeader>
              <CardTitle data-testid="tags-title">{t("settings.tags.title")}</CardTitle>
              <CardDescription>{t("settings.tags.subtitle")}</CardDescription>
            </CardHeader>
            <CardContent>
              {error && (
                <div className="mb-4">
                  <FormMessage type="error" message={error} testId="tags-error" />
                </div>
              )}

              {loading ? (
                <div className="text-center py-8 text-foreground" data-testid="tags-loading">
                  {t("common.loading")}
                </div>
              ) : tags.length === 0 ? (
                <div className="text-center py-8 text-muted-foreground" data-testid="tags-empty">
                  {t("settings.tags.noTags")}
                </div>
              ) : (
                <Table data-testid="tags-table">
                  <TableHeader>
                    <TableRow>
                      <TableHead data-testid="tags-header-tag">
                        {t("settings.tags.table.tag")}
                      </TableHead>
                      <TableHead className="w-[100px]" data-testid="tags-header-count">
                        {t("settings.tags.table.count")}
                      </TableHead>
                      <TableHead className="w-auto" data-testid="tags-header-legacy">
                        <div className="flex items-center gap-2">
                          <span>{t("settings.tags.table.isLegacy")}</span>
                          <Popover>
                            <PopoverTrigger asChild>
                              <button
                                className="text-muted-foreground hover:text-foreground"
                                data-testid="tags-legacy-tooltip-trigger"
                              >
                                <svg
                                  xmlns="http://www.w3.org/2000/svg"
                                  viewBox="0 0 20 20"
                                  fill="currentColor"
                                  className="w-4 h-4"
                                >
                                  <path
                                    fillRule="evenodd"
                                    d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zM8.94 6.94a.75.75 0 11-1.061-1.061 3 3 0 112.871 5.026v.345a.75.75 0 01-1.5 0v-.5c0-.72.57-1.172 1.081-1.287A1.5 1.5 0 108.94 6.94zM10 15a1 1 0 100-2 1 1 0 000 2z"
                                    clipRule="evenodd"
                                  />
                                </svg>
                              </button>
                            </PopoverTrigger>
                            <PopoverContent
                              className="dark text-sm"
                              data-testid="tags-legacy-tooltip"
                            >
                              {t("settings.tags.table.isLegacyTooltip")}
                            </PopoverContent>
                          </Popover>
                        </div>
                      </TableHead>
                      <TableHead className="w-[100px]" data-testid="tags-header-actions">
                        Actions
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {tags.map((tagStat) => (
                      <TableRow key={tagStat.tag} data-testid={`tag-row-${tagStat.tag}`}>
                        <TableCell data-testid={`tag-name-${tagStat.tag}`}>{tagStat.tag}</TableCell>
                        <TableCell data-testid={`tag-count-${tagStat.tag}`}>
                          {tagStat.count}
                        </TableCell>
                        <TableCell data-testid={`tag-legacy-${tagStat.tag}`}>
                          {tagStat.isLegacy && (
                            <span className="text-foreground">{t("settings.tags.yes")}</span>
                          )}
                        </TableCell>
                        <TableCell data-testid={`tag-actions-${tagStat.tag}`}>
                          <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                              <Button
                                variant="ghost"
                                size="sm"
                                disabled={actionInProgress === tagStat.tag}
                                data-testid={`tag-actions-menu-${tagStat.tag}`}
                              >
                                <MoreVertical className="h-4 w-4" />
                              </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end" className="dark">
                              {tagStat.isLegacy ? (
                                <DropdownMenuItem
                                  onClick={() => handleRemoveFromLegacy(tagStat.tag)}
                                  data-testid={`tag-unmark-legacy-${tagStat.tag}`}
                                >
                                  <ArchiveRestore className="h-4 w-4 mr-2" />
                                  {t("settings.tags.actions.removeFromLegacy")}
                                </DropdownMenuItem>
                              ) : (
                                <DropdownMenuItem
                                  onClick={() => handleMarkAsLegacy(tagStat.tag)}
                                  data-testid={`tag-mark-legacy-${tagStat.tag}`}
                                >
                                  <Archive className="h-4 w-4 mr-2" />
                                  {t("settings.tags.actions.markAsLegacy")}
                                </DropdownMenuItem>
                              )}
                            </DropdownMenuContent>
                          </DropdownMenu>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </div>
      </div>
    </PortalLayout>
  );
}
