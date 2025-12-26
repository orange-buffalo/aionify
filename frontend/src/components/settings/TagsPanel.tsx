import { useState, useEffect } from "react"
import { useTranslation } from "react-i18next"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { FormMessage } from "@/components/ui/form-message"
import { Tag } from "lucide-react"
import { apiGet } from "@/lib/api"

interface TagStat {
  tag: string
  count: number
}

interface TagStatsResponse {
  tags: TagStat[]
}

export function TagsPanel() {
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
    <Card data-testid="tags-panel">
      <CardHeader>
        <div className="flex items-center gap-2">
          <Tag className="h-5 w-5" />
          <CardTitle className="text-foreground">{t("settings.tags.title")}</CardTitle>
        </div>
        <CardDescription className="text-foreground">{t("settings.tags.subtitle")}</CardDescription>
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
                <TableHead className="text-foreground">{t("settings.tags.table.tag")}</TableHead>
                <TableHead className="text-foreground w-[100px]">{t("settings.tags.table.count")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tags.map((tagStat) => (
                <TableRow key={tagStat.tag} data-testid={`tag-row-${tagStat.tag}`}>
                  <TableCell className="text-foreground" data-testid={`tag-name-${tagStat.tag}`}>
                    {tagStat.tag}
                  </TableCell>
                  <TableCell className="text-foreground" data-testid={`tag-count-${tagStat.tag}`}>
                    {tagStat.count}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}
