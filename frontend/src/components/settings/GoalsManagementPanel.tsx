import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { ChevronDown, Plus, Trash2 } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { TimePicker } from "@/components/ui/time-picker";
import { apiGet, apiPut } from "@/lib/api";
import { useApiExecutor } from "@/hooks/useApiExecutor";

interface TypicalBreak {
  from: string;
  to: string;
}

interface GoalsSettingsResponse {
  dailyGoal: {
    enabled: boolean;
    goalMinutes: number;
    typicalBreaks?: TypicalBreak[];
  };
  weeklyGoal: {
    enabled: boolean;
    goalMinutes: number;
    workingDays: WeekDay[];
  };
}

type WeekDay = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

const weekDays: WeekDay[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

export function GoalsManagementPanel() {
  const { t } = useTranslation();
  const [locale, setLocale] = useState("en-GB");
  const { executeApiCall, apiCallInProgress, formMessage } = useApiExecutor("goals-settings");
  const [dailyGoalEnabled, setDailyGoalEnabled] = useState(false);
  const [dailyGoalHours, setDailyGoalHours] = useState(0);
  const [dailyGoalMinutes, setDailyGoalMinutes] = useState(0);
  const [typicalBreaks, setTypicalBreaks] = useState<TypicalBreak[]>([]);
  const [weeklyGoalEnabled, setWeeklyGoalEnabled] = useState(false);
  const [weeklyGoalHours, setWeeklyGoalHours] = useState(0);
  const [weeklyGoalMinutes, setWeeklyGoalMinutes] = useState(0);
  const [weeklyWorkingDays, setWeeklyWorkingDays] = useState<WeekDay[]>([
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY",
  ]);

  const loadGoalsSettings = async () => {
    const [data, profile] = await Promise.all([
      apiGet<GoalsSettingsResponse>("/api-ui/users/goals-settings"),
      apiGet<{ locale: string }>("/api-ui/users/profile"),
    ]);
    setLocale(profile.locale);
    setDailyGoalEnabled(data.dailyGoal.enabled);
    setDailyGoalHours(Math.floor(data.dailyGoal.goalMinutes / 60));
    setDailyGoalMinutes(data.dailyGoal.goalMinutes % 60);
    setTypicalBreaks(data.dailyGoal.typicalBreaks ?? []);
    setWeeklyGoalEnabled(data.weeklyGoal.enabled);
    setWeeklyGoalHours(Math.floor(data.weeklyGoal.goalMinutes / 60));
    setWeeklyGoalMinutes(data.weeklyGoal.goalMinutes % 60);
    setWeeklyWorkingDays(data.weeklyGoal.workingDays);
  };

  useEffect(() => {
    void executeApiCall(loadGoalsSettings);
  }, [executeApiCall]);

  const handleAddTypicalBreak = () => {
    setTypicalBreaks([...typicalBreaks, { from: "09:00", to: "09:15" }]);
  };

  const handleTypicalBreakChange = (index: number, field: keyof TypicalBreak, value: string) => {
    setTypicalBreaks(
      typicalBreaks.map((entry, entryIndex) => (entryIndex === index ? { ...entry, [field]: value } : entry))
    );
  };

  const handleDeleteTypicalBreak = (index: number) => {
    setTypicalBreaks(typicalBreaks.filter((_, entryIndex) => entryIndex !== index));
  };

  const handleWeeklyWorkingDayChange = (day: WeekDay, checked: boolean) => {
    setWeeklyWorkingDays(
      checked ? [...weeklyWorkingDays, day] : weeklyWorkingDays.filter((workingDay) => workingDay !== day)
    );
  };

  const selectedWeeklyWorkingDaysLabel = weekDays
    .filter((day) => weeklyWorkingDays.includes(day))
    .map((day) => t(`settings.preferences.weekDays.${day}`))
    .join(", ");

  const localTimeToDate = (value: string): Date => {
    const [hours, minutes] = value.split(":").map(Number);
    const date = new Date(2024, 0, 1, 0, 0, 0, 0);
    date.setHours(hours, minutes, 0, 0);
    return date;
  };

  const dateToLocalTime = (value: Date): string => {
    return `${value.getHours().toString().padStart(2, "0")}:${value.getMinutes().toString().padStart(2, "0")}`;
  };

  const handleSave = async () => {
    await executeApiCall(async () => {
      await apiPut("/api-ui/users/goals-settings", {
        dailyGoal: {
          enabled: dailyGoalEnabled,
          goalMinutes: dailyGoalHours * 60 + dailyGoalMinutes,
          typicalBreaks,
        },
        weeklyGoal: {
          enabled: weeklyGoalEnabled,
          goalMinutes: weeklyGoalHours * 60 + weeklyGoalMinutes,
          workingDays: weekDays.filter((day) => weeklyWorkingDays.includes(day)),
        },
      });
      return t("settings.goals.updateSuccess");
    });
  };

  const handleDiscard = async () => {
    await executeApiCall(loadGoalsSettings);
  };

  return (
    <Card className="border-none shadow-md">
      <CardHeader>
        <CardTitle data-testid="goals-title">{t("settings.goals.title")}</CardTitle>
        <CardDescription>{t("settings.goals.subtitle")}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="mb-4">{formMessage}</div>

        <div className="space-y-4">
          <p className="text-sm text-muted-foreground" data-testid="goals-description">
            {t("settings.goals.description")}
          </p>

          <div className="space-y-4 rounded-md border border-input bg-background/30 p-4" data-testid="daily-goal-panel">
            <div className="flex items-start gap-3">
              <Checkbox
                id="daily-goal-enabled"
                checked={dailyGoalEnabled}
                onCheckedChange={(checked) => setDailyGoalEnabled(checked === true)}
                disabled={apiCallInProgress}
                data-testid="daily-goal-toggle"
              />
              <div className="-mt-0.5 space-y-1">
                <Label htmlFor="daily-goal-enabled" className="cursor-pointer leading-none text-foreground">
                  {t("settings.goals.dailyGoal.title")}
                </Label>
                <p className="text-sm text-muted-foreground">{t("settings.goals.dailyGoal.subtitle")}</p>
                <p className="text-sm text-muted-foreground" data-testid="daily-goal-checkbox-description">
                  {t("settings.goals.dailyGoal.description")}
                </p>
              </div>
            </div>

            {dailyGoalEnabled && (
              <div className="space-y-4" data-testid="daily-goal-section">
                <div className="flex flex-wrap items-end gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="daily-goal-hours" className="text-foreground">
                      {t("settings.goals.dailyGoal.goalHours")}
                    </Label>
                    <Input
                      id="daily-goal-hours"
                      type="number"
                      min={0}
                      value={dailyGoalHours}
                      onChange={(e) => setDailyGoalHours(Math.max(0, Number(e.target.value) || 0))}
                      disabled={apiCallInProgress}
                      className="w-20 text-center font-mono text-foreground [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none"
                      data-testid="daily-goal-hours-input"
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="daily-goal-minutes" className="text-foreground">
                      {t("settings.goals.dailyGoal.goalMinutes")}
                    </Label>
                    <Input
                      id="daily-goal-minutes"
                      type="number"
                      min={0}
                      max={59}
                      value={dailyGoalMinutes}
                      onChange={(e) => setDailyGoalMinutes(Math.min(59, Math.max(0, Number(e.target.value) || 0)))}
                      disabled={apiCallInProgress}
                      className="w-20 text-center font-mono text-foreground [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none"
                      data-testid="daily-goal-minutes-input"
                    />
                  </div>
                </div>

                <div className="space-y-3">
                  <div className="flex items-center justify-between gap-4">
                    <div>
                      <h3 className="text-sm font-medium text-foreground">
                        {t("settings.goals.dailyGoal.typicalBreaks.title")}
                      </h3>
                      <p className="text-sm text-muted-foreground">
                        {t("settings.goals.dailyGoal.typicalBreaks.description")}
                      </p>
                    </div>

                    <Button
                      type="button"
                      variant="link"
                      onClick={handleAddTypicalBreak}
                      disabled={apiCallInProgress}
                      className="h-auto p-0 text-teal-600 hover:text-teal-700"
                      data-testid="daily-goal-add-break-button"
                    >
                      <Plus className="mr-1 h-4 w-4" />
                      {t("settings.goals.dailyGoal.typicalBreaks.add")}
                    </Button>
                  </div>

                  <Table data-testid="daily-goal-breaks-table">
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t("settings.goals.dailyGoal.typicalBreaks.table.from")}</TableHead>
                        <TableHead>{t("settings.goals.dailyGoal.typicalBreaks.table.to")}</TableHead>
                        <TableHead className="w-[100px]">
                          {t("settings.goals.dailyGoal.typicalBreaks.table.actions")}
                        </TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {typicalBreaks.length === 0 ? (
                        <TableRow data-testid="daily-goal-breaks-empty-row">
                          <TableCell colSpan={3} className="text-muted-foreground">
                            {t("settings.goals.dailyGoal.typicalBreaks.empty")}
                          </TableCell>
                        </TableRow>
                      ) : (
                        typicalBreaks.map((typicalBreak, index) => (
                          <TableRow
                            key={`${index}-${typicalBreak.from}-${typicalBreak.to}`}
                            data-testid={`daily-goal-break-row-${index}`}
                          >
                            <TableCell>
                              <TimePicker
                                key={`from-${index}-${typicalBreak.from}`}
                                value={localTimeToDate(typicalBreak.from)}
                                onChange={(date) => handleTypicalBreakChange(index, "from", dateToLocalTime(date))}
                                disabled={apiCallInProgress}
                                locale={locale}
                                testIdPrefix={`daily-goal-break-from-${index}`}
                              />
                            </TableCell>
                            <TableCell>
                              <TimePicker
                                key={`to-${index}-${typicalBreak.to}`}
                                value={localTimeToDate(typicalBreak.to)}
                                onChange={(date) => handleTypicalBreakChange(index, "to", dateToLocalTime(date))}
                                disabled={apiCallInProgress}
                                locale={locale}
                                testIdPrefix={`daily-goal-break-to-${index}`}
                              />
                            </TableCell>
                            <TableCell>
                              <Button
                                type="button"
                                variant="ghost"
                                onClick={() => handleDeleteTypicalBreak(index)}
                                disabled={apiCallInProgress}
                                className="text-foreground"
                                data-testid={`daily-goal-break-delete-${index}`}
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </div>
              </div>
            )}
          </div>

          <div
            className="space-y-4 rounded-md border border-input bg-background/30 p-4"
            data-testid="weekly-goal-panel"
          >
            <div className="flex items-start gap-3">
              <Checkbox
                id="weekly-goal-enabled"
                checked={weeklyGoalEnabled}
                onCheckedChange={(checked) => setWeeklyGoalEnabled(checked === true)}
                disabled={apiCallInProgress}
                data-testid="weekly-goal-toggle"
              />
              <div className="-mt-0.5 space-y-1">
                <Label htmlFor="weekly-goal-enabled" className="cursor-pointer leading-none text-foreground">
                  {t("settings.goals.weeklyGoal.title")}
                </Label>
                <p className="text-sm text-muted-foreground">{t("settings.goals.weeklyGoal.subtitle")}</p>
                <p className="text-sm text-muted-foreground" data-testid="weekly-goal-checkbox-description">
                  {t("settings.goals.weeklyGoal.description")}
                </p>
              </div>
            </div>

            {weeklyGoalEnabled && (
              <div className="space-y-4" data-testid="weekly-goal-section">
                <div className="flex flex-wrap items-end gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="weekly-goal-hours" className="text-foreground">
                      {t("settings.goals.weeklyGoal.goalHours")}
                    </Label>
                    <Input
                      id="weekly-goal-hours"
                      type="number"
                      min={0}
                      value={weeklyGoalHours}
                      onChange={(e) => setWeeklyGoalHours(Math.max(0, Number(e.target.value) || 0))}
                      disabled={apiCallInProgress}
                      className="w-20 text-center font-mono text-foreground [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none"
                      data-testid="weekly-goal-hours-input"
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="weekly-goal-minutes" className="text-foreground">
                      {t("settings.goals.weeklyGoal.goalMinutes")}
                    </Label>
                    <Input
                      id="weekly-goal-minutes"
                      type="number"
                      min={0}
                      max={59}
                      value={weeklyGoalMinutes}
                      onChange={(e) => setWeeklyGoalMinutes(Math.min(59, Math.max(0, Number(e.target.value) || 0)))}
                      disabled={apiCallInProgress}
                      className="w-20 text-center font-mono text-foreground [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none"
                      data-testid="weekly-goal-minutes-input"
                    />
                  </div>
                </div>

                {dailyGoalEnabled && (
                  <div className="space-y-3" data-testid="weekly-goal-working-days-section">
                    <div>
                      <h3 className="text-sm font-medium text-foreground">
                        {t("settings.goals.weeklyGoal.workingDays.title")}
                      </h3>
                      <p className="text-sm text-muted-foreground">
                        {t("settings.goals.weeklyGoal.workingDays.description")}
                      </p>
                    </div>

                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          type="button"
                          variant="outline"
                          disabled={apiCallInProgress}
                          className="min-h-10 w-full justify-between text-left font-normal text-foreground sm:w-[360px]"
                          data-testid="weekly-goal-working-days-trigger"
                        >
                          <span className="truncate" data-testid="weekly-goal-working-days-summary">
                            {selectedWeeklyWorkingDaysLabel || t("settings.goals.weeklyGoal.workingDays.placeholder")}
                          </span>
                          <ChevronDown className="ml-2 h-4 w-4 shrink-0 opacity-70" />
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent className="dark w-[var(--radix-dropdown-menu-trigger-width)]" align="start">
                        {weekDays.map((day) => (
                          <DropdownMenuCheckboxItem
                            key={day}
                            checked={weeklyWorkingDays.includes(day)}
                            onCheckedChange={(checked) => handleWeeklyWorkingDayChange(day, checked === true)}
                            onSelect={(event) => event.preventDefault()}
                            data-testid={`weekly-goal-working-day-${day}`}
                          >
                            {t(`settings.preferences.weekDays.${day}`)}
                          </DropdownMenuCheckboxItem>
                        ))}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                )}
              </div>
            )}
          </div>

          <div className="flex flex-wrap gap-3">
            <Button
              onClick={handleSave}
              disabled={apiCallInProgress}
              data-testid="save-goals-button"
              className="bg-teal-600 hover:bg-teal-700"
            >
              {apiCallInProgress ? t("settings.goals.saving") : t("settings.goals.save")}
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={handleDiscard}
              disabled={apiCallInProgress}
              data-testid="discard-goals-button"
            >
              {t("settings.goals.discard")}
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
