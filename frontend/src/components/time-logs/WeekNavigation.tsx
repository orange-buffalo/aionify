import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { formatDuration } from "@/lib/time-utils";

interface WeekNavigationProps {
  weekRange: string;
  weeklyTotal: number;
  locale: string;
  onPreviousWeek: () => void;
  onNextWeek: () => void;
}

export function WeekNavigation({ weekRange, weeklyTotal, locale, onPreviousWeek, onNextWeek }: WeekNavigationProps) {
  const { t } = useTranslation();

  return (
    <Card className="border-none shadow-md mb-6">
      <CardContent className="flex items-center justify-between p-4">
        <Button variant="ghost" onClick={onPreviousWeek} data-testid="previous-week-button" className="text-foreground">
          <ChevronLeft className="h-4 w-4 mr-2" />
          {t("timeLogs.previousWeek")}
        </Button>
        <div className="flex flex-col items-center">
          <h2 className="text-xl font-semibold text-foreground" data-testid="week-range">
            {weekRange}
          </h2>
          <div className="text-sm text-muted-foreground mt-1" data-testid="weekly-total">
            {t("timeLogs.weeklyTotal")}: {formatDuration(weeklyTotal)}
          </div>
        </div>
        <Button variant="ghost" onClick={onNextWeek} data-testid="next-week-button" className="text-foreground">
          {t("timeLogs.nextWeek")}
          <ChevronRight className="h-4 w-4 ml-2" />
        </Button>
      </CardContent>
    </Card>
  );
}
