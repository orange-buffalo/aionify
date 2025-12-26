import { useState, useEffect } from "react"
import { useTranslation } from "react-i18next"
import { PortalLayout } from "@/components/layout/PortalLayout"
import { Card } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { FormMessage } from "@/components/ui/form-message"
import { apiGet } from "@/lib/api"

interface TagStat {
  tag: string
  count: number
}

interface TagStatsResponse {
  tags: TagStat[]
}

export function SettingsPage() {
  const { t } = useTranslation()
  const [tags, setTags] = useState<TagStat[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadTags = async () => {
    setLoading(true)
    setError(null)
    
    try {
      const data = await apiGet<TagStatsResponse>("/api/tags/stats")
      // Defensive check: Handle edge case where API might return empty object
      // This can happen if serialization fails or in certain error scenarios
      // Better to show empty list than crash with "Cannot read properties of undefined"
      if (data && data.tags && Array.isArray(data.tags)) {
        setTags(data.tags)
      } else {
        setTags([])
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "An error occurred")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadTags()
  }, [])

  return (
    <PortalLayout testId="settings-page">
      <div className="p-8">
        <div className="max-w-4xl mx-auto">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground" data-testid="settings-title">{t("settings.title")}</h1>
            <p className="text-muted-foreground">{t("settings.subtitle")}</p>
          </div>

          <Card className="border-none shadow-md">
            <div className="p-6">
              <h2 className="text-2xl font-semibold text-foreground mb-2">{t("settings.tags.title")}</h2>
              <p className="text-sm text-muted-foreground mb-6">{t("settings.tags.subtitle")}</p>

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
                      <TableHead data-testid="tags-header-tag">{t("settings.tags.table.tag")}</TableHead>
                      <TableHead className="w-[100px]" data-testid="tags-header-count">{t("settings.tags.table.count")}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {tags.map((tagStat) => (
                      <TableRow key={tagStat.tag} data-testid={`tag-row-${tagStat.tag}`}>
                        <TableCell data-testid={`tag-name-${tagStat.tag}`}>
                          {tagStat.tag}
                        </TableCell>
                        <TableCell data-testid={`tag-count-${tagStat.tag}`}>
                          {tagStat.count}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </div>
          </Card>
        </div>
      </div>
    </PortalLayout>
  )
}
