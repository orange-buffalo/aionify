import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ChevronLeft, ChevronRight } from "lucide-react";

interface WeekNavigationProps {
  weekRange: string;
  onPreviousWeek: () => void;
  onNextWeek: () => void;
}

export function WeekNavigation({ weekRange, onPreviousWeek, onNextWeek }: WeekNavigationProps) {
  const { t } = useTranslation();

  return (
    <Card className="border-none shadow-md mb-6">
      <CardContent className="flex items-center justify-between p-4">
        <Button variant="ghost" onClick={onPreviousWeek} data-testid="previous-week-button" className="text-foreground">
          <ChevronLeft className="h-4 w-4 mr-2" />
          {t("timeLogs.previousWeek")}
        </Button>
        <h2 className="text-xl font-semibold text-foreground" data-testid="week-range">
          {weekRange}
        </h2>
        <Button variant="ghost" onClick={onNextWeek} data-testid="next-week-button" className="text-foreground">
          {t("timeLogs.nextWeek")}
          <ChevronRight className="h-4 w-4 ml-2" />
        </Button>
      </CardContent>
    </Card>
  );
}
