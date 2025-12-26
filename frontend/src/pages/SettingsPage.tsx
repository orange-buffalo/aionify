import { useState, useEffect } from "react"
import { useTranslation } from "react-i18next"
import { PortalLayout } from "@/components/layout/PortalLayout"
import { Card, CardContent } from "@/components/ui/card"
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

  useEffect(() => {
    const loadTags = async () => {
      setLoading(true)
      setError(null)
      
      try {
        const data = await apiGet<TagStatsResponse>("/api/tags/stats")
        setTags(data.tags)
      } catch (err) {
        setError(err instanceof Error ? err.message : "An error occurred")
      } finally {
        setLoading(false)
      }
    }

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
            <Card className="border-none shadow-md" data-testid="tags-empty-card">
              <CardContent className="pt-6">
                <div className="text-center py-8 text-muted-foreground" data-testid="tags-empty">
                  {t("settings.tags.noTags")}
                </div>
              </CardContent>
            </Card>
          ) : (
            <Card className="border-none shadow-md" data-testid="tags-table-container">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("settings.tags.table.tag")}</TableHead>
                    <TableHead className="w-[100px]">{t("settings.tags.table.count")}</TableHead>
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
            </Card>
          )}
        </div>
      </div>
    </PortalLayout>
  )
}
